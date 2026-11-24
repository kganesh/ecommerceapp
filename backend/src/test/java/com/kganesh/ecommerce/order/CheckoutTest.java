package com.kganesh.ecommerce.order;

import com.kganesh.ecommerce.IntegrationTest;
import com.kganesh.ecommerce.ShopFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static com.kganesh.ecommerce.ShopFixtures.json;
import static com.kganesh.ecommerce.ShopFixtures.newKey;
import static com.kganesh.ecommerce.ShopFixtures.newShopper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-8, FR-9, FR-15, NFR-11. M4 exit gate: an end-to-end purchase, including a product
 * edited after acknowledgment.
 */
class CheckoutTest extends IntegrationTest {

    private static final String APPROVE = "tok_test_approve";
    private static final String DECLINE = "tok_test_decline";

    private ShopFixtures shop;

    @BeforeEach
    void setUp() {
        shop = new ShopFixtures(mvc);
    }

    private void expectCartLines(RequestPostProcessor shopper, int count) throws Exception {
        mvc.perform(get("/api/v1/carts/me").with(shopper)).andExpect(jsonPath("$.lines", hasSize(count)));
    }

    @Test
    void clearCartChecksOutAndAppearsInOrderHistory() throws Exception {
        RequestPostProcessor parent = newShopper();
        shop.profile(parent, "Child 1", "PEANUT");
        String product = shop.product("Rice Cakes", "[]");
        shop.addToCart(parent, product, null).andExpect(status().isCreated());

        String orderId = json(shop.checkout(parent, newKey(), APPROVE, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.totalCents").value(250))
                .andExpect(jsonPath("$.paymentReference").exists())
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].allergenVersion").value(1))
                .andExpect(jsonPath("$.lines[0].acknowledgedWarning").doesNotExist()), "$.id");

        expectCartLines(parent, 0);
        mvc.perform(get("/api/v1/orders").with(parent))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(orderId));
    }

    @Test
    void retryWithTheSameKeyReturnsTheSameOrderAndChargesOnce() throws Exception {
        RequestPostProcessor parent = newShopper();
        String product = shop.product("Oat Bars", "[]");
        shop.addToCart(parent, product, null);
        String key = newKey();

        String first = json(shop.checkout(parent, key, APPROVE, null).andExpect(status().isCreated()), "$.id");
        String second = json(shop.checkout(parent, key, APPROVE, null).andExpect(status().isOk()), "$.id");
        assertThat(second).isEqualTo(first);

        long orders = jdbc.sql("SELECT count(*) FROM customer_order WHERE idempotency_key = :key")
                .param("key", key).query(Long.class).single();
        assertThat(orders).isEqualTo(1);
    }

    @Test
    void anAddToCartAcknowledgmentStillValidNeedsNoReconfirmationAndIsRecordedOnTheOrder() throws Exception {
        RequestPostProcessor parent = newShopper();
        shop.profile(parent, "Child 1", "TREE_NUT");
        String product = shop.product("Cashew Butter", "[{\"allergenId\": \"TREE_NUT.CASHEW\", \"matchType\": \"CONTAINS\"}]");
        String token = json(shop.addToCart(parent, product, null), "$.ackToken");
        shop.addToCart(parent, product, token).andExpect(status().isCreated());

        String orderId = json(shop.checkout(parent, newKey(), APPROVE, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].acknowledgedWarning.context").value("ADD_TO_CART"))
                .andExpect(jsonPath("$.lines[0].acknowledgedWarning.matches[0].allergenName").value("Cashew"))
                .andExpect(jsonPath("$.lines[0].acknowledgedWarning.matches[0].profileLabel").value("Child 1"))
                .andExpect(jsonPath("$.lines[0].acknowledgedWarning.acknowledgedAt").exists()), "$.id");

        mvc.perform(get("/api/v1/orders/{id}", orderId).with(parent))
                .andExpect(jsonPath("$.lines[0].acknowledgedWarning.matches[0].matchType").value("CONTAINS"));
    }

    @Test
    void productEditedAfterAcknowledgmentMustBeReconfirmedAtCheckout() throws Exception {
        RequestPostProcessor parent = newShopper();
        shop.profile(parent, "Child 1", "TREE_NUT");
        String risky = shop.product("Granola", "[{\"allergenId\": \"TREE_NUT.ALMOND\", \"matchType\": \"MAY_CONTAIN\"}]");
        String plain = shop.product("Plain Crackers", "[]");
        String token = json(shop.addToCart(parent, risky, null), "$.ackToken");
        shop.addToCart(parent, risky, token).andExpect(status().isCreated());
        shop.addToCart(parent, plain, null).andExpect(status().isCreated());

        // The admin edits the label after the parent acknowledged it.
        shop.setProductAllergens(risky, "[{\"allergenId\": \"TREE_NUT.ALMOND\", \"matchType\": \"CONTAINS\"}]");

        String key = newKey();
        String checkoutToken = json(shop.checkout(parent, key, APPROVE, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHECKOUT_ALLERGEN_WARNING"))
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].productId").value(risky))
                .andExpect(jsonPath("$.lines[0].allergenVersion").value(2))
                .andExpect(jsonPath("$.lines[0].matches[0].matchType").value("CONTAINS")), "$.ackToken");

        assertThat(jdbc.sql("SELECT count(*) FROM customer_order WHERE idempotency_key = :key")
                .param("key", key).query(Long.class).single()).isZero();
        expectCartLines(parent, 2);

        shop.checkout(parent, key, APPROVE, checkoutToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines", hasSize(2)))
                .andExpect(jsonPath("$.lines[0].allergenVersion").value(2))
                .andExpect(jsonPath("$.lines[0].acknowledgedWarning.context").value("CHECKOUT"))
                .andExpect(jsonPath("$.lines[1].acknowledgedWarning").doesNotExist());
    }

    @Test
    void aCheckoutTokenIsVoidIfAnAvoidListChangesBeforeItIsUsed() throws Exception {
        RequestPostProcessor parent = newShopper();
        String child = shop.profile(parent, "Child 1", "PEANUT");
        String product = shop.product("Trail Mix", null);
        // No allergen data: unverified, needs acknowledging.
        String addToken = json(shop.addToCart(parent, product, null), "$.ackToken");
        shop.addToCart(parent, product, addToken).andExpect(status().isCreated());
        shop.setAvoidList(parent, child, "PEANUT", "SESAME");

        String key = newKey();
        String token = json(shop.checkout(parent, key, APPROVE, null).andExpect(status().isConflict()), "$.ackToken");
        shop.setAvoidList(parent, child, "PEANUT", "SESAME", "SOY");
        shop.checkout(parent, key, APPROVE, token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.lines[0].unverified").value(true));
    }

    @Test
    void declinedPaymentPlacesNoOrderAndKeepsTheCart() throws Exception {
        RequestPostProcessor parent = newShopper();
        String product = shop.product("Apple Chips", "[]");
        shop.addToCart(parent, product, null);

        shop.checkout(parent, newKey(), DECLINE, null)
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_DECLINED"));
        expectCartLines(parent, 1);
        mvc.perform(get("/api/v1/orders").with(parent)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void unknownPaymentTokensAreDeclinedNeverCharged() throws Exception {
        RequestPostProcessor parent = newShopper();
        shop.addToCart(parent, shop.product("Pretzels", "[]"), null);
        shop.checkout(parent, newKey(), "4242424242424242", null)
                .andExpect(status().isPaymentRequired());
    }

    @Test
    void emptyCartAndMissingKeyAreRejected() throws Exception {
        RequestPostProcessor parent = newShopper();
        shop.checkout(parent, newKey(), APPROVE, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));
        shop.addToCart(parent, shop.product("Popcorn", "[]"), null);
        shop.checkout(parent, null, APPROVE, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void discontinuedProductBlocksCheckout() throws Exception {
        RequestPostProcessor parent = newShopper();
        String product = shop.product("Seasonal Cookies", "[]");
        shop.addToCart(parent, product, null);
        mvc.perform(put("/api/v1/admin/products/{id}", product).with(ShopFixtures.ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Seasonal Cookies\", \"priceCents\": 250, \"active\": false}"))
                .andExpect(status().isOk());
        shop.checkout(parent, newKey(), APPROVE, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"));
    }

    @Test
    void ordersArePrivateAndAppendOnly() throws Exception {
        RequestPostProcessor parent = newShopper();
        shop.addToCart(parent, shop.product("Cheese Puffs", "[]"), null);
        String orderId = json(shop.checkout(parent, newKey(), APPROVE, null), "$.id");

        mvc.perform(get("/api/v1/orders/{id}", orderId).with(newShopper())).andExpect(status().isNotFound());
        assertThatThrownBy(() -> jdbc.sql("UPDATE customer_order SET total_cents = 0 WHERE id = :id")
                .param("id", UUID.fromString(orderId)).update()).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM order_line WHERE order_id = :id")
                .param("id", UUID.fromString(orderId)).update()).isInstanceOf(DataAccessException.class);
    }
}
