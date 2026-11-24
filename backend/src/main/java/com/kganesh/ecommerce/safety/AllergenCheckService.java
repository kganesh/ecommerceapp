package com.kganesh.ecommerce.safety;

import com.kganesh.ecommerce.common.ApiExceptions;
import com.kganesh.ecommerce.safety.AllergenCheckRepository.MatchRow;
import com.kganesh.ecommerce.safety.AllergenCheckRepository.ProductState;
import com.kganesh.ecommerce.safety.AllergenCheckRepository.ProfileState;
import com.kganesh.ecommerce.safety.SafetyDtos.AffectedProfile;
import com.kganesh.ecommerce.safety.SafetyDtos.ProductSafety;
import com.kganesh.ecommerce.safety.SafetyDtos.SafetyMatch;
import com.kganesh.ecommerce.safety.SafetyDtos.SafetyStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The allergen check (spec: Architecture > Allergen check). Safety rule: it fails toward
 * warning. A product with no allergen data is UNVERIFIED, and if the check cannot run it
 * throws {@link Unavailable} rather than returning anything that could read as clear.
 */
@Service
public class AllergenCheckService {

    private static final Logger log = LoggerFactory.getLogger(AllergenCheckService.class);

    /** The check result for one product, bound to the exact data it was computed from. */
    public record Assessment(
            UUID productId,
            String productName,
            Integer allergenVersion,
            boolean active,
            SafetyStatus status,
            List<SafetyMatch> matches,
            List<AffectedProfile> affectedProfiles,
            String fingerprint) {

        public boolean needsAcknowledgment() {
            return status != SafetyStatus.CLEAR;
        }

        public ProductSafety toSafety() {
            return new ProductSafety(status, matches, affectedProfiles);
        }
    }

    /** The check could not run. Callers must not proceed as if the product were clear. */
    public static final class Unavailable extends ApiExceptions.ApiException {
        Unavailable() {
            super(HttpStatus.SERVICE_UNAVAILABLE, "ALLERGEN_CHECK_UNAVAILABLE",
                    "Unable to verify allergens right now. Nothing was changed; please try again.");
        }
    }

    private final AllergenCheckRepository repository;
    private final MeterRegistry meters;
    private final Timer timer;

    AllergenCheckService(AllergenCheckRepository repository, MeterRegistry meters) {
        this.repository = repository;
        this.meters = meters;
        this.timer = Timer.builder("allergen.check.latency").publishPercentiles(0.95).register(meters);
    }

    /**
     * Checks products against every avoid list of the parent's active profiles.
     * Products that don't exist are absent from the result.
     *
     * @throws Unavailable if the check cannot complete
     */
    @Transactional(readOnly = true)
    public Map<UUID, Assessment> assess(UUID userId, Collection<UUID> productIds) {
        Timer.Sample sample = Timer.start(meters);
        try {
            return compute(userId, productIds);
        } catch (RuntimeException e) {
            meters.counter("allergen.check.failures").increment();
            log.error("Allergen check failed for user {} and {} product(s); failing toward warning",
                    userId, productIds.size(), e);
            throw new Unavailable();
        } finally {
            sample.stop(timer);
        }
    }

    /** For display only (browse): a failed check shows as UNAVAILABLE on every product, never as clear. */
    public Map<UUID, ProductSafety> assessForDisplay(UUID userId, Collection<UUID> productIds) {
        Map<UUID, ProductSafety> result = new LinkedHashMap<>();
        try {
            assess(userId, productIds).forEach((id, assessment) -> result.put(id, assessment.toSafety()));
        } catch (Unavailable e) {
            productIds.forEach(id -> result.put(id, ProductSafety.unavailable()));
        }
        return result;
    }

    private Map<UUID, Assessment> compute(UUID userId, Collection<UUID> productIds) {
        Map<UUID, ProductState> products = repository.products(productIds);
        List<ProfileState> profiles = repository.profiles(userId);
        Map<UUID, List<MatchRow>> matchesByProduct = repository.matches(userId, products.keySet()).stream()
                .collect(Collectors.groupingBy(MatchRow::productId, LinkedHashMap::new, Collectors.toList()));

        List<AffectedProfile> withAvoidLists = profiles.stream()
                .filter(ProfileState::hasAvoidList)
                .map(p -> new AffectedProfile(p.id(), p.label()))
                .toList();

        Map<UUID, Assessment> result = new LinkedHashMap<>();
        for (ProductState product : products.values()) {
            List<SafetyMatch> matches = matchesByProduct.getOrDefault(product.id(), List.of()).stream()
                    .map(m -> new SafetyMatch(m.profileId(), m.profileLabel(), m.allergenId(), m.allergenName(), m.matchType()))
                    .toList();

            SafetyStatus status;
            List<AffectedProfile> affected;
            if (withAvoidLists.isEmpty()) {
                // No child has anything to avoid: nothing to warn about, even without allergen data.
                status = SafetyStatus.CLEAR;
                affected = List.of();
            } else if (product.allergenVersion() == null) {
                status = SafetyStatus.UNVERIFIED;
                affected = withAvoidLists;
            } else if (!matches.isEmpty()) {
                status = SafetyStatus.WARNING;
                affected = distinctProfiles(matches);
            } else {
                status = SafetyStatus.CLEAR;
                affected = List.of();
            }
            result.put(product.id(), new Assessment(product.id(), product.name(), product.allergenVersion(),
                    product.active(), status, matches, affected, fingerprint(product, profiles)));
        }
        return result;
    }

    private static List<AffectedProfile> distinctProfiles(List<SafetyMatch> matches) {
        Map<UUID, AffectedProfile> seen = new LinkedHashMap<>();
        for (SafetyMatch m : matches) {
            seen.putIfAbsent(m.profileId(), new AffectedProfile(m.profileId(), m.profileLabel()));
        }
        return new ArrayList<>(seen.values());
    }

    /**
     * Binds a result to the product's allergen version and every active profile's avoid-list
     * version. Any change to either (FR-9), or a profile added or deleted, changes it.
     */
    static String fingerprint(ProductState product, List<ProfileState> profiles) {
        StringBuilder input = new StringBuilder()
                .append(product.id()).append('|')
                .append(product.allergenVersion() == null ? "none" : product.allergenVersion());
        profiles.stream()
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .forEach(p -> input.append('|').append(p.id()).append(':').append(p.avoidListVersion()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
