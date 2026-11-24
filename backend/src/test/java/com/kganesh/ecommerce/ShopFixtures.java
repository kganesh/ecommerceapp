package com.kganesh.ecommerce;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Builds catalog, shopper and profile state through the public APIs, as a real client would. */
public final class ShopFixtures {

    public static final RequestPostProcessor ADMIN = user("admin1").roles("CATALOG_ADMIN");

    private final MockMvc mvc;

    public ShopFixtures(MockMvc mvc) {
        this.mvc = mvc;
    }

    /** A new shopper with a fresh identity-provider subject. */
    public static RequestPostProcessor newShopper() {
        return shopper(UUID.randomUUID().toString());
    }

    /** A shopper with a known identity-provider subject. */
    public static RequestPostProcessor shopper(String subject) {
        return oidcLogin()
                .idToken(token -> token.subject(subject).claim("preferred_username", "p-" + subject.substring(0, 8)))
                .authorities(new SimpleGrantedAuthority("ROLE_SHOPPER"));
    }

    /** Creates an active product. {@code allergensJson} null means "no allergen data". */
    public String product(String name, String allergensJson) throws Exception {
        String allergens = allergensJson == null ? "" : ", \"allergens\": " + allergensJson;
        String body = """
                {"sku": "T-%s", "name": "%s", "priceCents": 250%s}
                """.formatted(UUID.randomUUID(), name, allergens);
        String response = mvc.perform(post("/api/v1/admin/products").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    public void setProductAllergens(String productId, String allergensJson) throws Exception {
        mvc.perform(put("/api/v1/admin/products/{id}/allergens", productId).with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allergens\": " + allergensJson + "}"))
                .andExpect(status().isOk());
    }

    /** Creates a profile for the shopper with the given avoid list; returns its id. */
    public String profile(RequestPostProcessor shopper, String label, String... avoid) throws Exception {
        String response = mvc.perform(post("/api/v1/profiles").with(shopper).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\": \"" + label + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(response, "$.id");
        setAvoidList(shopper, id, avoid);
        return id;
    }

    public void setAvoidList(RequestPostProcessor shopper, String profileId, String... avoid) throws Exception {
        String ids = String.join(", ", java.util.Arrays.stream(avoid).map(a -> "\"" + a + "\"").toList());
        mvc.perform(put("/api/v1/profiles/{id}/avoid-list", profileId).with(shopper).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allergenIds\": [" + ids + "]}"))
                .andExpect(status().isOk());
    }

    public ResultActions addToCart(RequestPostProcessor shopper, String productId, String ackToken) throws Exception {
        String token = ackToken == null ? "" : ", \"ackToken\": \"" + ackToken + "\"";
        return mvc.perform(post("/api/v1/carts/me/items").with(shopper).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\": \"" + productId + "\", \"qty\": 1" + token + "}"));
    }

    public ResultActions checkout(RequestPostProcessor shopper, String idempotencyKey, String paymentToken,
                                  String ackToken) throws Exception {
        String token = ackToken == null ? "" : ", \"ackToken\": \"" + ackToken + "\"";
        var request = post("/api/v1/carts/me/checkout").with(shopper).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentToken\": \"" + paymentToken + "\"" + token + "}");
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request);
    }

    public static String newKey() {
        return "chk-" + UUID.randomUUID();
    }

    public static String json(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path).toString();
    }
}
