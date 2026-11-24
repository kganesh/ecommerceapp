package com.kganesh.ecommerce.taxonomy;

import com.kganesh.ecommerce.IntegrationTest;
import com.kganesh.ecommerce.common.ApiExceptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M1 exit gate: taxonomy expansion is unit-tested. */
class TaxonomyTest extends IntegrationTest {

    private static final Set<String> TREE_NUTS = Set.of(
            "TREE_NUT", "TREE_NUT.ALMOND", "TREE_NUT.BRAZIL_NUT", "TREE_NUT.CASHEW", "TREE_NUT.HAZELNUT",
            "TREE_NUT.MACADAMIA", "TREE_NUT.PECAN", "TREE_NUT.PINE_NUT", "TREE_NUT.PISTACHIO", "TREE_NUT.WALNUT");

    @Autowired
    TaxonomyService taxonomy;

    @Test
    void avoidingTreeNutCoversEveryTreeNutButNotPeanut() {
        assertThat(taxonomy.expand(List.of("TREE_NUT"))).containsExactlyInAnyOrderElementsOf(TREE_NUTS);
    }

    @Test
    void peanutIsItsOwnRootAndNotATreeNut() {
        assertThat(taxonomy.expand(List.of("PEANUT"))).containsExactly("PEANUT");
    }

    @Test
    void aLeafCoversOnlyItself() {
        assertThat(taxonomy.expand(List.of("TREE_NUT.CASHEW"))).containsExactly("TREE_NUT.CASHEW");
    }

    @Test
    void overlappingEntriesAreDeduplicated() {
        assertThat(taxonomy.expand(List.of("TREE_NUT", "TREE_NUT.CASHEW", "PEANUT")))
                .hasSize(TREE_NUTS.size() + 1)
                .contains("PEANUT", "TREE_NUT.CASHEW");
    }

    @Test
    void unknownIdsAreRejectedNeverIgnored() {
        assertThatThrownBy(() -> taxonomy.expand(List.of("TREE_NUT", "COCONUT_TYPO")))
                .isInstanceOf(ApiExceptions.BadRequest.class)
                .hasMessageContaining("COCONUT_TYPO");
    }

    @Test
    void closureHasSelfRowForEveryAllergen() {
        long allergens = jdbc.sql("SELECT count(*) FROM allergen").query(Long.class).single();
        long selfRows = jdbc.sql("SELECT count(*) FROM allergen_closure WHERE depth = 0").query(Long.class).single();
        assertThat(selfRows).isEqualTo(allergens);
    }

    @Test
    void taxonomyIsPublic() throws Exception {
        mvc.perform(get("/api/v1/allergens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'TREE_NUT.CASHEW')].parentId").value("TREE_NUT"))
                .andExpect(jsonPath("$[?(@.id == 'PEANUT')].synonyms[*]").value(org.hamcrest.Matchers.hasItem("arachis oil")));
    }
}
