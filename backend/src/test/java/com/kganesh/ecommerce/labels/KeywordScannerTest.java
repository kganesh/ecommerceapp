package com.kganesh.ecommerce.labels;

import com.kganesh.ecommerce.taxonomy.Allergen;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordScannerTest {

    static final List<Allergen> TAXONOMY = List.of(
            new Allergen("PEANUT", null, "Peanut", List.of("groundnut", "arachis oil")),
            new Allergen("TREE_NUT", null, "Tree nuts", List.of("nut")),
            new Allergen("TREE_NUT.ALMOND", "TREE_NUT", "Almond", List.of("marzipan")),
            new Allergen("TREE_NUT.CASHEW", "TREE_NUT", "Cashew", List.of()),
            new Allergen("TREE_NUT.HAZELNUT", "TREE_NUT", "Hazelnut", List.of("filbert")),
            new Allergen("MILK", null, "Milk", List.of("whey")),
            new Allergen("WHEAT", null, "Wheat", List.of()));

    private final KeywordScanner scanner = new KeywordScanner(TAXONOMY);

    @Test
    void findsNamesSynonymsAndPluralsInAnyCase() {
        assertThat(scanner.scan("Sugar, CASHEWS, roasted Peanuts, whey powder, Marzipan").keySet())
                .containsExactlyInAnyOrder("PEANUT", "TREE_NUT.CASHEW", "MILK", "TREE_NUT.ALMOND");
    }

    @Test
    void reportsTheWordsItMatched() {
        assertThat(scanner.scan("may contain FILBERTS")).containsEntry("TREE_NUT.HAZELNUT", "FILBERTS");
    }

    @Test
    void matchesWholeWordsOnly() {
        assertThat(scanner.scan("nutmeg, coconut, buckwheat, peanut-free")).containsOnlyKeys("PEANUT");
    }

    @Test
    void genericNutsAndTreeNutsMeanTreeNuts() {
        assertThat(scanner.scan("May contain nuts")).containsOnlyKeys("TREE_NUT");
        assertThat(scanner.scan("made in a facility with tree-nuts")).containsOnlyKeys("TREE_NUT");
    }

    @Test
    void multiWordTermsTolerateLineBreaks() {
        assertThat(scanner.scan("refined arachis\noil")).containsOnlyKeys("PEANUT");
    }

    @Test
    void emptyTextFindsNothing() {
        assertThat(scanner.scan(null)).isEmpty();
        assertThat(scanner.scan("  ")).isEmpty();
    }
}
