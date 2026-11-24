package com.kganesh.ecommerce.profile;

import com.jayway.jsonpath.JsonPath;
import com.kganesh.ecommerce.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR-2, FR-3. M2 exit gate: an avoid-list change is visible on the next read. */
class ProfileTest extends IntegrationTest {

    private static RequestPostProcessor user(String subject, String role) {
        return oidcLogin()
                .idToken(token -> token.subject(subject).claim("preferred_username", "u-" + subject.substring(0, 8)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static RequestPostProcessor newShopper() {
        return user(UUID.randomUUID().toString(), "SHOPPER");
    }

    private String createProfile(RequestPostProcessor who, String label) throws Exception {
        String body = mvc.perform(post("/api/v1/profiles").with(who).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\": \"" + label + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void parentManagesSeveralProfiles() throws Exception {
        RequestPostProcessor parent = newShopper();
        String child1 = createProfile(parent, "Child 1");
        createProfile(parent, "Child 2");

        mvc.perform(get("/api/v1/profiles").with(parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].label").value("Child 1"))
                .andExpect(jsonPath("$[0].avoidList", hasSize(0)));

        mvc.perform(patch("/api/v1/profiles/{id}", child1).with(parent).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\": \"  Eldest  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Eldest"));

        mvc.perform(delete("/api/v1/profiles/{id}", child1).with(parent).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/profiles").with(parent))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].label").value("Child 2"));
    }

    @Test
    void avoidingTreeNutsCoversEveryTreeNutAndTheChangeIsVisibleOnNextRead() throws Exception {
        RequestPostProcessor parent = newShopper();
        String id = createProfile(parent, "Child 1");

        mvc.perform(put("/api/v1/profiles/{id}/avoid-list", id).with(parent).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allergenIds\": [\"TREE_NUT\", \"PEANUT\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avoidList", hasSize(2)))
                .andExpect(jsonPath("$.coveredAllergenIds", hasItems("TREE_NUT.CASHEW", "TREE_NUT.ALMOND", "PEANUT")))
                .andExpect(jsonPath("$.coveredAllergenIds", not(hasItem("SESAME"))))
                .andExpect(jsonPath("$.avoidListVersion").value(1));

        mvc.perform(get("/api/v1/profiles").with(parent))
                .andExpect(jsonPath("$[0].avoidList", hasItems("PEANUT", "TREE_NUT")))
                .andExpect(jsonPath("$[0].avoidListVersion").value(1));
    }

    @Test
    void avoidListVersionOnlyChangesWhenTheListChanges() throws Exception {
        RequestPostProcessor parent = newShopper();
        String id = createProfile(parent, "Child 1");
        String body = "{\"allergenIds\": [\"PEANUT\"]}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/api/v1/profiles/{id}/avoid-list", id).with(parent).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.avoidListVersion").value(1));
        }
        mvc.perform(put("/api/v1/profiles/{id}/avoid-list", id).with(parent).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allergenIds\": []}"))
                .andExpect(jsonPath("$.avoidListVersion").value(2))
                .andExpect(jsonPath("$.avoidList", hasSize(0)));
    }

    @Test
    void unknownAllergenIsRejectedNotDropped() throws Exception {
        RequestPostProcessor parent = newShopper();
        String id = createProfile(parent, "Child 1");
        mvc.perform(put("/api/v1/profiles/{id}/avoid-list", id).with(parent).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allergenIds\": [\"PEANUT\", \"PEANUTS\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_ALLERGEN"));
        mvc.perform(get("/api/v1/profiles").with(parent))
                .andExpect(jsonPath("$[0].avoidList", hasSize(0)));
    }

    @Test
    void anotherParentsProfileIsNotFound() throws Exception {
        String id = createProfile(newShopper(), "Child 1");
        RequestPostProcessor stranger = newShopper();
        mvc.perform(put("/api/v1/profiles/{id}/avoid-list", id).with(stranger).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allergenIds\": []}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/profiles/{id}", id).with(stranger).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyShoppersHaveProfiles() throws Exception {
        mvc.perform(get("/api/v1/profiles")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/profiles").with(user(UUID.randomUUID().toString(), "CATALOG_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void labelIsValidated() throws Exception {
        RequestPostProcessor parent = newShopper();
        mvc.perform(post("/api/v1/profiles").with(parent).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\": \"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/profiles").with(parent).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\": \"" + "x".repeat(41) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atMostTenProfilesPerAccount() throws Exception {
        RequestPostProcessor parent = newShopper();
        for (int i = 1; i <= 10; i++) {
            createProfile(parent, "Child " + i);
        }
        mvc.perform(post("/api/v1/profiles").with(parent).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\": \"Child 11\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOO_MANY_PROFILES"));
    }
}
