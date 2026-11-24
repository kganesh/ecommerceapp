package com.kganesh.ecommerce.labels;

import com.kganesh.ecommerce.catalog.CatalogDtos.MatchType;
import com.kganesh.ecommerce.common.ApiExceptions;
import com.kganesh.ecommerce.labels.LabelDtos.LabelExtraction;
import com.kganesh.ecommerce.labels.LabelDtos.LabelReadingStatus;
import com.kganesh.ecommerce.labels.LabelDtos.SuggestedAllergen;
import com.kganesh.ecommerce.labels.LabelDtos.SuggestionSource;
import com.kganesh.ecommerce.labels.LabelReader.ImageType;
import com.kganesh.ecommerce.labels.LabelReader.LabelImage;
import com.kganesh.ecommerce.labels.LabelReader.LabelReading;
import com.kganesh.ecommerce.labels.LabelReader.ModelAllergen;
import com.kganesh.ecommerce.taxonomy.Allergen;
import com.kganesh.ecommerce.taxonomy.TaxonomyService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns a label photo into reviewed-before-saving allergen suggestions (FR-16 to FR-19).
 * The model's answer is validated against the taxonomy and backed up by a keyword scan;
 * nothing here writes to the catalog.
 */
@Service
public class LabelExtractionService {

    /** 3.5 MiB: the client resizes photos well under this (FR-16). */
    static final int MAX_IMAGE_BYTES = 3_670_016;

    private final LabelReader reader;
    private final TaxonomyService taxonomy;
    private final LabelExtractionRepository repository;
    private final TransactionTemplate transactions;
    private final MeterRegistry meters;

    LabelExtractionService(LabelReader reader, TaxonomyService taxonomy, LabelExtractionRepository repository,
                           TransactionTemplate transactions, MeterRegistry meters) {
        this.reader = reader;
        this.taxonomy = taxonomy;
        this.repository = repository;
        this.transactions = transactions;
        this.meters = meters;
    }

    public LabelReadingStatus status() {
        return new LabelReadingStatus(reader.enabled(), reader.modelId());
    }

    /** Reads the photo (outside any transaction: the model call can take seconds), then records the result. */
    public LabelExtraction extract(byte[] image, String actor) {
        if (!reader.enabled()) {
            throw LabelReaderUnavailable.disabled();
        }
        if (image == null || image.length == 0) {
            throw new ApiExceptions.BadRequest("EMPTY_IMAGE", "Choose a photo of the label");
        }
        if (image.length > MAX_IMAGE_BYTES) {
            throw new ImageTooLarge();
        }
        ImageType type = ImageType.sniff(image);
        if (type == null) {
            throw new ApiExceptions.BadRequest("UNSUPPORTED_IMAGE", "Use a JPEG, PNG or WebP photo");
        }

        List<Allergen> allergens = taxonomy.listAll();
        LabelReading reading;
        try {
            reading = reader.read(new LabelImage(image, type), allergens);
        } catch (LabelReaderUnavailable e) {
            meters.counter("label_extractions", "outcome", "unavailable").increment();
            throw e;
        }

        LabelExtraction extraction = merge(UUID.randomUUID(), reader.modelId(), reading, allergens);
        transactions.executeWithoutResult(tx ->
                repository.insert(extraction, actor, sha256(image), type.mediaType(), image.length));
        meters.counter("label_extractions", "outcome", extraction.readable() ? "read" : "unreadable").increment();
        return extraction;
    }

    /**
     * Combines the model's answer with the keyword backstop. Ids the taxonomy does not know are
     * never suggested; they become unmatched terms. A keyword hit in the ingredients or a
     * "Contains" statement upgrades a suggestion to CONTAINS.
     */
    static LabelExtraction merge(UUID id, String model, LabelReading reading, List<Allergen> taxonomy) {
        Map<String, Allergen> byId = taxonomy.stream()
                .collect(Collectors.toMap(Allergen::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<String, Draft> drafts = new LinkedHashMap<>();
        LinkedHashSet<String> unmatched = new LinkedHashSet<>(reading.unmatchedTerms() == null ? List.of() : reading.unmatchedTerms());

        for (ModelAllergen suggestion : reading.allergens() == null ? List.<ModelAllergen>of() : reading.allergens()) {
            if (!byId.containsKey(suggestion.allergenId())) {
                unmatched.add(suggestion.evidence() != null ? suggestion.evidence() : suggestion.allergenId());
                continue;
            }
            Draft existing = drafts.get(suggestion.allergenId());
            if (existing == null) {
                drafts.put(suggestion.allergenId(),
                        new Draft(suggestion.matchType(), SuggestionSource.MODEL, suggestion.evidence()));
            } else if (suggestion.matchType() == MatchType.CONTAINS) {
                existing.matchType = MatchType.CONTAINS;
            }
        }

        KeywordScanner scanner = new KeywordScanner(taxonomy);
        Map<String, String> contains = scanner.scan(join(reading.ingredientsText(), reading.containsStatement()));
        Map<String, String> mayContain = scanner.scan(reading.mayContainStatement());
        contains.forEach((allergenId, words) -> found(drafts, allergenId, words, MatchType.CONTAINS));
        mayContain.forEach((allergenId, words) -> found(drafts, allergenId, words, MatchType.MAY_CONTAIN));

        List<SuggestedAllergen> suggestions = new ArrayList<>();
        for (Allergen allergen : byId.values()) {
            Draft draft = drafts.get(allergen.id());
            if (draft != null) {
                suggestions.add(new SuggestedAllergen(allergen.id(), allergen.displayName(), draft.matchType,
                        draft.source, draft.evidence));
            }
        }
        return new LabelExtraction(id, model, reading.readable(), reading.ingredientsText(),
                reading.containsStatement(), reading.mayContainStatement(), suggestions,
                List.copyOf(unmatched), reading.notes());
    }

    private static void found(Map<String, Draft> drafts, String allergenId, String words, MatchType matchType) {
        Draft draft = drafts.get(allergenId);
        if (draft == null) {
            drafts.put(allergenId, new Draft(matchType, SuggestionSource.KEYWORD_SCAN, words));
            return;
        }
        if (draft.source == SuggestionSource.MODEL) {
            draft.source = SuggestionSource.MODEL_AND_KEYWORD_SCAN;
        }
        if (matchType == MatchType.CONTAINS) {
            draft.matchType = MatchType.CONTAINS;
        }
    }

    private static String join(String a, String b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : a + "\n" + b;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class Draft {
        MatchType matchType;
        SuggestionSource source;
        final String evidence;

        Draft(MatchType matchType, SuggestionSource source, String evidence) {
            this.matchType = matchType;
            this.source = source;
            this.evidence = evidence;
        }
    }

    static final class ImageTooLarge extends ApiExceptions.ApiException {
        ImageTooLarge() {
            super(HttpStatus.CONTENT_TOO_LARGE, "IMAGE_TOO_LARGE", "The photo is larger than 3.5 MB");
        }
    }
}
