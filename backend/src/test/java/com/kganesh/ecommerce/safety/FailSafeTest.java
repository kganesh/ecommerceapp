package com.kganesh.ecommerce.safety;

import com.kganesh.ecommerce.IntegrationTest;
import com.kganesh.ecommerce.ShopFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static com.kganesh.ecommerce.ShopFixtures.newShopper;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR-11: when the allergen check cannot run, nothing is added and nothing reads as clear. */
class FailSafeTest extends IntegrationTest {

    @MockitoBean
    AllergenCheckRepository brokenCheck;

    private ShopFixtures shop;

    @BeforeEach
    void setUp() {
        shop = new ShopFixtures(mvc);
        when(brokenCheck.products(any())).thenThrow(new DataAccessResourceFailureException("database unreachable"));
    }

    @Test
    void addToCartReturns503AndAddsNothing() throws Exception {
        RequestPostProcessor parent = newShopper();
        String product = shop.product("Rice Cakes", "[]");

        shop.addToCart(parent, product, null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ALLERGEN_CHECK_UNAVAILABLE"));

        long lines = jdbc.sql("""
                        SELECT count(*) FROM cart_item i JOIN cart c ON c.id = i.cart_id
                        WHERE i.product_id = :product
                        """)
                .param("product", UUID.fromString(product)).query(Long.class).single();
        org.assertj.core.api.Assertions.assertThat(lines).isZero();
    }

    @Test
    void browseShowsUnavailableNeverClear() throws Exception {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        shop.product("Plain Crackers " + marker, "[]");
        mvc.perform(get("/api/v1/products").param("q", marker).with(newShopper()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].safety.status").value("UNAVAILABLE"));
    }

    @Test
    void checkoutChargesNothingAndPlacesNoOrderWhenTheCheckCannotRun() throws Exception {
        String subject = UUID.randomUUID().toString();
        RequestPostProcessor parent = ShopFixtures.shopper(subject);
        String product = shop.product("Corn Chips", "[]");
        // Put the item in the cart directly: adding through the API is (correctly) refused here.
        mvc.perform(get("/api/v1/me").with(parent)).andExpect(status().isOk());
        UUID userId = jdbc.sql("SELECT id FROM app_user WHERE oidc_subject = :s").param("s", subject)
                .query(UUID.class).single();
        UUID cartId = UUID.randomUUID();
        jdbc.sql("INSERT INTO cart (id, user_id) VALUES (:id, :userId)").param("id", cartId).param("userId", userId).update();
        jdbc.sql("INSERT INTO cart_item (cart_id, product_id, qty, allergen_version_at_add) VALUES (:c, :p, 1, 1)")
                .param("c", cartId).param("p", UUID.fromString(product)).update();

        shop.checkout(parent, ShopFixtures.newKey(), "tok_test_approve", null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ALLERGEN_CHECK_UNAVAILABLE"));
        long orders = jdbc.sql("SELECT count(*) FROM customer_order WHERE user_id = :u").param("u", userId)
                .query(Long.class).single();
        org.assertj.core.api.Assertions.assertThat(orders).isZero();
    }

    @Test
    void cartReadFailsRatherThanShowingStaleStatuses() throws Exception {
        mvc.perform(get("/api/v1/carts/me").with(newShopper()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ALLERGEN_CHECK_UNAVAILABLE"));
    }
}
