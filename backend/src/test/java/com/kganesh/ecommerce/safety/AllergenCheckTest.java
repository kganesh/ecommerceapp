package com.kganesh.ecommerce.safety;

import com.kganesh.ecommerce.IntegrationTest;
import com.kganesh.ecommerce.ShopFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static com.kganesh.ecommerce.ShopFixtures.json;
import static com.kganesh.ecommerce.ShopFixtures.newShopper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M3 exit gate: the matching matrix (descendants, ancestors, MAY_CONTAIN, multiple profiles,
 * unverified products) and the acknowledgment rules (single use, stale tokens, FR-9 re-flag).
 */
class AllergenCheckTest extends IntegrationTest {

    private ShopFixtures shop;

    @BeforeEach
    void setUp() {
        shop = new ShopFixtures(mvc);
    }

    private void expectCartEmpty(RequestPostProcessor shopper) throws Exception {
        mvc.perform(get("/api/v1/carts/me").with(shopper))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(0)));
    }

    // ---- Matching matrix -------------------------------------------------------------

    @Test
    void avoidingTreeNutsWarnsOnCashewAndAddsNothing() throws Exception {
        RequestPostProcessor parent = newShopper();
        String child = shop.profile(parent, "Child 1", "TREE_NUT");
        String product = shop.product("Cashew Butter", "[{\"allergenId\": \"TREE_NUT.CASHEW\", \"matchType\": \"CONTAINS\"}]");

        shop.addToCart(parent, product, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALLERGEN_WARNING"))
                .andExpect(jsonPath("$.unverified").value(false))
                .andExpect(jsonPath("$.allergenVersion").value(1))
                .andExpect(jsonPath("$.matches", hasSize(1)))
                .andExpect(jsonPath("$.matches[0].profileId").value(child))
                .andExpect(jsonPath("$.matches[0].profileLabel").value("Child 1"))
                .andExpect(jsonPath("$.matches[0].allergenName").value("Cashew"))
                .andExpect(jsonPath("$.matches[0].matchType").value("CONTAINS"))
                .andExpect(jsonPath("$.ackToken").exists())
                .andExpect(jsonPath("$.ackExpiresAt").exists());
        expectCartEmpty(parent);
    }

    @Test
    void avoidingCashewWarnsOnAGenericTreeNutLabel() throws Exception {
        RequestPostProcessor parent = newShopper();
        shop.profile(parent, "Child 1", "TREE_NUT.CASHEW");
        String product = shop.product("Mixed Nut Cookies", "[{\"allergenId\": \"TREE_NUT\", \"matchType\": \"MAY_CONTAIN\"}]");

        shop.addToCart(parent, product, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.matches[0].allergenId").value("TREE_NUT"))
                .andExpect(jsonPath("$.matches[0].matchType").value("MAY_CONTAIN"));
    }

    @Test
    void mayContainCountsAsAMatch() throws Exception {
        RequestPostProcessor parent = newShopper();
        shop.profile(parent, "Child 1", "PEANUT");
        String product = shop.product("Chocolate Chip Cookies", "[{\"allergenId\": \"PEANUT\", \"matchType\": \"MAY_CONTAIN\"}]");
        shop.addToCart(parent, product, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.matches[0].matchType").value("MAY_CONTAIN"));
    }

    @Test
    void peanutAvoidanceDoesNotMatchTreeNuts() throws Exception {
        RequestPostProcessor parent = newShopper();
        shop.profile(parent, "Child 1", "PEANUT");
        String product = shop.product("Almond Bar", "[{\"allergenId\": \"TREE_NUT.ALMOND\", \"matchType\": \"CONTAINS\"}]");
        shop.addToCart(parent, product, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].status").value("CLEAR"));
    }

    @Test
    void everyChildIsNamedWhenSeveralMatch() throws Exception {
        RequestPostProcessor parent = newShopper();
        shop.profile(parent, "Child 1", "TREE_NUT");
        shop.profile(parent, "Child 2", "PEANUT");
        shop.profile(parent, "Child 3", "SESAME");
        String product = shop.product("Nut Mix", """
                [{"allergenId": "TREE_NUT.CASHEW", "matchType": "CONTAINS"},
                 {"allergenId": "PEANUT", "matchType": "MAY_CONTAIN"}]
                """);
        shop.addToCart(parent, product, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.matches", hasSize(2)))
                .andExpect(jsonPath("$.matches[0].profileLabel").value("Child 1"))
                .andExpect(jsonPath("$.matches[1].profileLabel").value("Child 2"))
                .andExpect(jsonPath("$.matches[1].matchType").value("MAY_CONTAIN"))
                .andExpect(jsonPath("$.affectedProfiles", hasSize(2)));
    }

    @Test
    void productWithoutAllergenDataIsUnverifiedAndNeedsAcknowledgment() throws Exception {
        RequestPostProcessor parent = newShopper();
        shop.profile(parent, "Child 1", "TREE_NUT");
        shop.profile(parent, "Child 2");
        String product = shop.product("Trail Mix Surprise", null);
        shop.addToCart(parent, product, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.unverified").value(true))
                .andExpect(jsonPath("$.allergenVersion").doesNotExist())
                .andExpect(jsonPath("$.matches", hasSize(0)))
                .andExpect(jsonPath("$.affectedProfiles", hasSize(1)))
                .andExpect(jsonPath("$.affectedProfiles[0].label").value("Child 1"));
        expectCartEmpty(parent);
    }

    @Test
    void withNoAvoidListsThereIsNothingToWarnAbout() throws Exception {
        RequestPostProcessor parent = newShopper();
        String product = shop.product("Trail Mix Surprise", null);
        shop.addToCart(parent, product, null).andExpect(status().isCreated());
    }

    @Test
    void deletedProfilesNoLongerTriggerWarnings() throws Exception {
        RequestPostProcessor parent = newShopper();
        String child = shop.profile(parent, "Child 1", "SESAME");
        String product = shop.product("Tahini", "[{\"allergenId\": \"SESAME\", \"matchType\": \"CONTAINS\"}]");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/profiles/{id}", child).with(parent)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNoContent());
        shop.addToCart(parent, product, null).andExpect(status().isCreated());
    }

    // ---- Acknowledgment -------------------------------------------------------------

    @Test
    void acknowledgingAddsTheItemAndIsAudited() throws Exception {
        RequestPostProcessor parent = newShopper();
        shop.profile(parent, "Child 1", "TREE_NUT");
        String product = shop.product("Pecan Pie", "[{\"allergenId\": \"TREE_NUT.PECAN\", \"matchType\": \"CONTAINS\"}]");

        String token = json(shop.addToCart(parent, product, null).andExpect(status().isConflict()), "$.ackToken");
        shop.addToCart(parent, product, token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.lines[0].matches[0].allergenName").value("Pecan"))
                .andExpect(jsonPath("$.needsAcknowledgment").value(false));

        long acks = jdbc.sql("""
                        SELECT count(*) FROM allergen_ack k JOIN allergen_warning w ON w.id = k.warning_id
                        WHERE w.token = :token AND w.product_id = :product AND w.context = 'ADD_TO_CART'
                        """)
                .param("token", UUID.fromString(token)).param("product", UUID.fromString(product))
                .query(Long.class).single();
        assertThat(acks).isEqualTo(1);
    }

    @Test
    void aTokenWorksOnlyOnce() throws Exception {
        RequestPostProcessor parent = newShopper();
        shop.profile(parent, "Child 1", "PEANUT");
        String product = shop.product("Peanut Butter", "[{\"allergenId\": \"PEANUT\", \"matchType\": \"CONTAINS\"}]");
        String token = json(shop.addToCart(parent, product, null), "$.ackToken");
        shop.addToCart(parent, product, token).andExpect(status().isCreated());

        String second = json(shop.addToCart(parent, product, token).andExpect(status().isConflict()), "$.ackToken");
        assertThat(second).isNotEqualTo(token);
    }

    @Test
    void aTokenIsVoidAfterTheAvoidListChanges() throws Exception {
        RequestPostProcessor parent = newShopper();
        String child = shop.profile(parent, "Child 1", "PEANUT");
        String product = shop.product("Satay Sauce", """
                [{"allergenId": "PEANUT", "matchType": "CONTAINS"}, {"allergenId": "SOY", "matchType": "CONTAINS"}]
                """);
        String token = json(shop.addToCart(parent, product, null), "$.ackToken");

        shop.setAvoidList(parent, child, "PEANUT", "SOY");
        shop.addToCart(parent, product, token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.matches", hasSize(2)));
        expectCartEmpty(parent);
    }

    @Test
    void aTokenIsVoidAfterTheProductAllergensChange() throws Exception {
        RequestPostProcessor parent = newShopper();
        shop.profile(parent, "Child 1", "TREE_NUT");
        String product = shop.product("Hazelnut Spread", "[{\"allergenId\": \"TREE_NUT.HAZELNUT\", \"matchType\": \"MAY_CONTAIN\"}]");
        String token = json(shop.addToCart(parent, product, null), "$.ackToken");

        shop.setProductAllergens(product, "[{\"allergenId\": \"TREE_NUT.HAZELNUT\", \"matchType\": \"CONTAINS\"}]");
        shop.addToCart(parent, product, token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.allergenVersion").value(2))
                .andExpect(jsonPath("$.matches[0].matchType").value("CONTAINS"));
    }

    @Test
    void anotherParentsTokenIsNotAccepted() throws Exception {
        RequestPostProcessor parentA = newShopper();
        RequestPostProcessor parentB = newShopper();
        shop.profile(parentA, "Child 1", "PEANUT");
        shop.profile(parentB, "Child 1", "PEANUT");
        String product = shop.product("Peanut Brittle", "[{\"allergenId\": \"PEANUT\", \"matchType\": \"CONTAINS\"}]");
        String tokenA = json(shop.addToCart(parentA, product, null), "$.ackToken");
        shop.addToCart(parentB, product, tokenA).andExpect(status().isConflict());
        expectCartEmpty(parentB);
    }

    @Test
    void acknowledgedLineIsReflaggedWhenTheProductChanges() throws Exception {
        RequestPostProcessor parent = newShopper();
        shop.profile(parent, "Child 1", "TREE_NUT");
        String product = shop.product("Walnut Brownies", "[{\"allergenId\": \"TREE_NUT.WALNUT\", \"matchType\": \"MAY_CONTAIN\"}]");
        String token = json(shop.addToCart(parent, product, null), "$.ackToken");
        shop.addToCart(parent, product, token).andExpect(status().isCreated());

        shop.setProductAllergens(product, "[{\"allergenId\": \"TREE_NUT.WALNUT\", \"matchType\": \"CONTAINS\"}]");
        mvc.perform(get("/api/v1/carts/me").with(parent))
                .andExpect(jsonPath("$.lines[0].status").value("NEEDS_ACKNOWLEDGMENT"))
                .andExpect(jsonPath("$.lines[0].matches[0].matchType").value("CONTAINS"))
                .andExpect(jsonPath("$.needsAcknowledgment").value(true));
    }

    @Test
    void clearLineIsFlaggedWhenAChildsAvoidListGrows() throws Exception {
        RequestPostProcessor parent = newShopper();
        String child = shop.profile(parent, "Child 1", "PEANUT");
        String product = shop.product("Sesame Crackers", "[{\"allergenId\": \"SESAME\", \"matchType\": \"CONTAINS\"}]");
        shop.addToCart(parent, product, null).andExpect(status().isCreated());

        shop.setAvoidList(parent, child, "PEANUT", "SESAME");
        mvc.perform(get("/api/v1/carts/me").with(parent))
                .andExpect(jsonPath("$.lines[0].status").value("NEEDS_ACKNOWLEDGMENT"))
                .andExpect(jsonPath("$.needsAcknowledgment").value(true));
    }

    @Test
    void warningsAndAcknowledgmentsAreAppendOnly() throws Exception {
        RequestPostProcessor parent = newShopper();
        shop.profile(parent, "Child 1", "PEANUT");
        String product = shop.product("Peanut Clusters", "[{\"allergenId\": \"PEANUT\", \"matchType\": \"CONTAINS\"}]");
        String token = json(shop.addToCart(parent, product, null), "$.ackToken");
        shop.addToCart(parent, product, token).andExpect(status().isCreated());

        UUID t = UUID.fromString(token);
        assertThatThrownBy(() -> jdbc.sql("UPDATE allergen_warning SET unverified = TRUE WHERE token = :t")
                .param("t", t).update()).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM allergen_ack WHERE warning_id IN (SELECT id FROM allergen_warning WHERE token = :t)")
                .param("t", t).update()).isInstanceOf(DataAccessException.class);
    }

    // ---- Browse (FR-5) and access ---------------------------------------------------

    @Test
    void browseShowsEachShopperTheirMatchesAndCanHideThem() throws Exception {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        RequestPostProcessor parent = newShopper();
        shop.profile(parent, "Child 1", "TREE_NUT");
        String risky = shop.product("Cashew Snack " + marker, "[{\"allergenId\": \"TREE_NUT.CASHEW\", \"matchType\": \"CONTAINS\"}]");
        String unknown = shop.product("Mystery Snack " + marker, null);
        String clear = shop.product("Rice Snack " + marker, "[]");

        ResultActions all = mvc.perform(get("/api/v1/products").param("q", marker).with(parent))
                .andExpect(jsonPath("$.total").value(3));
        all.andExpect(jsonPath("$.items[?(@.id == '" + risky + "')].safety.status").value("WARNING"))
                .andExpect(jsonPath("$.items[?(@.id == '" + unknown + "')].safety.status").value("UNVERIFIED"))
                .andExpect(jsonPath("$.items[?(@.id == '" + clear + "')].safety.status").value("CLEAR"));

        mvc.perform(get("/api/v1/products").param("q", marker).param("hideUnsafe", "true").with(parent))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(clear));

        mvc.perform(get("/api/v1/products").param("q", marker))
                .andExpect(jsonPath("$.items[0].safety").doesNotExist());
    }

    @Test
    void inactiveOrUnknownProductsCannotBeAdded() throws Exception {
        RequestPostProcessor parent = newShopper();
        shop.addToCart(parent, UUID.randomUUID().toString(), null).andExpect(status().isNotFound());
    }

    @Test
    void cartIsForShoppersOnly() throws Exception {
        mvc.perform(get("/api/v1/carts/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/carts/me").with(ShopFixtures.ADMIN)).andExpect(status().isForbidden());
    }
}
