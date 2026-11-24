package com.kganesh.ecommerce.catalog;

import com.jayway.jsonpath.JsonPath;
import com.kganesh.ecommerce.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR-4, FR-14 and RBAC. M1 exit gate: an admin edit creates a new version and an audit row. */
class CatalogTest extends IntegrationTest {

    private static final RequestPostProcessor ADMIN = user("admin1").roles("CATALOG_ADMIN");
    private static final RequestPostProcessor SHOPPER = user("parent1").roles("SHOPPER");

    private static String sku() {
        return "TEST-" + UUID.randomUUID();
    }

    private String createProduct(String sku, String name, String allergensJson) throws Exception {
        String allergens = allergensJson == null ? "" : ", \"allergens\": " + allergensJson;
        String body = """
                {"sku": "%s", "name": "%s", "priceCents": 499%s}
                """.formatted(sku, name, allergens);
        String response = mvc.perform(post("/api/v1/admin/products").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    @Test
    void anonymousCannotUseAdminApi() throws Exception {
        mvc.perform(post("/api/v1/admin/products").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shopperCannotUseAdminApi() throws Exception {
        mvc.perform(get("/api/v1/admin/products").with(SHOPPER)).andExpect(status().isForbidden());
    }

    @Test
    void adminWriteWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/v1/admin/products").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createWithAllergensStartsAtVersionOne() throws Exception {
        String id = createProduct(sku(), "Cashew Butter", """
                [{"allergenId": "TREE_NUT.CASHEW", "matchType": "CONTAINS"},
                 {"allergenId": "PEANUT", "matchType": "MAY_CONTAIN"}]
                """);

        mvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allergenVersion").value(1))
                .andExpect(jsonPath("$.allergenDataAvailable").value(true))
                .andExpect(jsonPath("$.allergens", hasSize(2)))
                .andExpect(jsonPath("$.allergens[?(@.allergenId == 'TREE_NUT.CASHEW')].matchType").value("CONTAINS"))
                .andExpect(jsonPath("$.allergens[?(@.allergenId == 'PEANUT')].matchType").value("MAY_CONTAIN"));
    }

    @Test
    void productWithoutAllergenDataIsFlaggedNotSafe() throws Exception {
        String id = createProduct(sku(), "Mystery Granola", null);
        mvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(jsonPath("$.allergenDataAvailable").value(false))
                .andExpect(jsonPath("$.allergenVersion").doesNotExist())
                .andExpect(jsonPath("$.allergens", hasSize(0)));
    }

    @Test
    void verifiedNutFreeProductHasAVersionWithNoAllergens() throws Exception {
        String id = createProduct(sku(), "Plain Rice Cakes", "[]");
        mvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(jsonPath("$.allergenDataAvailable").value(true))
                .andExpect(jsonPath("$.allergenVersion").value(1))
                .andExpect(jsonPath("$.allergens", hasSize(0)));
    }

    @Test
    void changingAllergensCreatesNewVersionKeepsOldOneAndAudits() throws Exception {
        String id = createProduct(sku(), "Trail Mix", """
                [{"allergenId": "TREE_NUT.ALMOND", "matchType": "CONTAINS"}]
                """);

        mvc.perform(put("/api/v1/admin/products/{id}/allergens", id).with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"allergens": [{"allergenId": "TREE_NUT.ALMOND", "matchType": "CONTAINS"},
                                               {"allergenId": "PEANUT", "matchType": "CONTAINS"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allergenVersion").value(2))
                .andExpect(jsonPath("$.allergens", hasSize(2)));

        long versionOneRows = jdbc.sql("SELECT count(*) FROM product_allergen WHERE product_id = :id AND version = 1")
                .param("id", UUID.fromString(id)).query(Long.class).single();
        assertThat(versionOneRows).isEqualTo(1);

        mvc.perform(get("/api/v1/admin/products/{id}/audit", id).with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].action").value("PRODUCT_CREATED"))
                .andExpect(jsonPath("$[1].action").value("ALLERGENS_CHANGED"))
                .andExpect(jsonPath("$[1].toVersion").value(1))
                .andExpect(jsonPath("$[2].action").value("ALLERGENS_CHANGED"))
                .andExpect(jsonPath("$[2].fromVersion").value(1))
                .andExpect(jsonPath("$[2].toVersion").value(2))
                .andExpect(jsonPath("$[2].actor").value("admin1"));
    }

    @Test
    void allergenVersionsAndAuditAreImmutableInTheDatabase() throws Exception {
        String id = createProduct(sku(), "Pecan Pie", """
                [{"allergenId": "TREE_NUT.PECAN", "matchType": "CONTAINS"}]
                """);
        UUID productId = UUID.fromString(id);

        assertThatThrownBy(() -> jdbc.sql("UPDATE product_allergen SET match_type = 'MAY_CONTAIN' WHERE product_id = :id")
                .param("id", productId).update()).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM product_allergen WHERE product_id = :id")
                .param("id", productId).update()).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM catalog_audit WHERE product_id = :id")
                .param("id", productId).update()).isInstanceOf(DataAccessException.class);
    }

    @Test
    void unknownAllergenIsRejected() throws Exception {
        mvc.perform(post("/api/v1/admin/products").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku": "%s", "name": "Bad", "priceCents": 1,
                                 "allergens": [{"allergenId": "TREE_NUT.COCONUTT", "matchType": "CONTAINS"}]}
                                """.formatted(sku())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_ALLERGEN"));
    }

    @Test
    void sameAllergenTwiceIsRejected() throws Exception {
        String id = createProduct(sku(), "Peanut Brittle", "[]");
        mvc.perform(put("/api/v1/admin/products/{id}/allergens", id).with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"allergens": [{"allergenId": "PEANUT", "matchType": "CONTAINS"},
                                               {"allergenId": "PEANUT", "matchType": "MAY_CONTAIN"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ALLERGEN"));
    }

    @Test
    void duplicateSkuIsAConflict() throws Exception {
        String sku = sku();
        createProduct(sku, "First", "[]");
        mvc.perform(post("/api/v1/admin/products").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku": "%s", "name": "Second", "priceCents": 1}
                                """.formatted(sku)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SKU"));
    }

    @Test
    void publicSearchFindsActiveProductsAndHidesInactiveOnes() throws Exception {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        String activeId = createProduct(sku(), "Hazelnut Spread " + marker, """
                [{"allergenId": "TREE_NUT.HAZELNUT", "matchType": "CONTAINS"}]
                """);
        String inactiveId = createProduct(sku(), "Old Hazelnut Spread " + marker, "[]");
        mvc.perform(put("/api/v1/admin/products/{id}", inactiveId).with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Old Hazelnut Spread %s", "priceCents": 499, "active": false}
                                """.formatted(marker)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/products").param("q", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(activeId))
                .andExpect(jsonPath("$.items[0].allergens[0].displayName").value("Hazelnut"));

        mvc.perform(get("/api/v1/products/{id}", inactiveId)).andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/admin/products").with(ADMIN).param("q", marker))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void searchTreatsLikeWildcardsLiterally() throws Exception {
        mvc.perform(get("/api/v1/products").param("q", "%_%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }
}
