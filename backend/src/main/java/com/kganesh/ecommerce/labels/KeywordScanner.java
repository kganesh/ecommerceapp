package com.kganesh.ecommerce.labels;

import com.kganesh.ecommerce.taxonomy.Allergen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic backstop for the model (FR-18): finds taxonomy names and label terms in
 * text the model read. It over-reports on purpose ("oat milk" suggests Milk); the admin
 * removes what does not apply. It never under-reports a term it knows.
 */
final class KeywordScanner {

    private record Term(String allergenId, Pattern pattern) {
    }

    private final List<Term> terms = new ArrayList<>();

    KeywordScanner(List<Allergen> taxonomy) {
        for (Allergen allergen : taxonomy) {
            List<String> words = new ArrayList<>(allergen.synonyms());
            words.add(allergen.displayName());
            for (String word : words) {
                Pattern pattern = compile(word);
                if (pattern != null) {
                    terms.add(new Term(allergen.id(), pattern));
                }
            }
        }
    }

    /** Allergen id to the first matching words in the text, in taxonomy order. */
    Map<String, String> scan(String text) {
        Map<String, String> hits = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return hits;
        }
        for (Term term : terms) {
            if (hits.containsKey(term.allergenId())) {
                continue;
            }
            Matcher matcher = term.pattern().matcher(text);
            if (matcher.find()) {
                hits.put(term.allergenId(), matcher.group());
            }
        }
        return hits;
    }

    /**
     * Whole words only, any case, with an optional plural: "Tree nuts" matches "tree nut",
     * "TREE NUTS" and "tree-nuts"; "nut" does not match "nutmeg", "peanut" or "coconut".
     */
    static Pattern compile(String word) {
        String stem = word.trim().toLowerCase();
        if (stem.length() > 3 && stem.endsWith("s") && !stem.endsWith("ss")) {
            stem = stem.substring(0, stem.length() - 1);
        }
        if (stem.isEmpty()) {
            return null;
        }
        String body = String.join("[\\s\\-]+",
                java.util.Arrays.stream(stem.split("[\\s\\-]+")).map(Pattern::quote).toList());
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + body + "(?:e?s)?(?![\\p{L}\\p{N}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
