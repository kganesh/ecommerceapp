package com.kganesh.ecommerce.identity;

import com.jayway.jsonpath.JsonPath;
import com.kganesh.ecommerce.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M2: backend-for-frontend login, server-side sessions, roles (FR-1, D11). */
class IdentityTest extends IntegrationTest {

    @Autowired
    StringRedisTemplate redis;

    @Value("${local.server.port}")
    int port;

    static RequestPostProcessor shopper(String subject) {
        return oidcLogin()
                .idToken(token -> token.subject(subject)
                        .claim("preferred_username", "parent-" + subject.substring(0, 8))
                        .claim("email", subject + "@example.test"))
                .authorities(new SimpleGrantedAuthority("ROLE_SHOPPER"));
    }

    @Test
    void anonymousMeSaysNotSignedInAndWhereToLogIn() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.loginUrl").value("/oauth2/authorization/idp"));
    }

    @Test
    void signedInMeShowsIdentityAndRolesAndProvisionsTheUser() throws Exception {
        String subject = UUID.randomUUID().toString();
        mvc.perform(get("/api/v1/me").with(shopper(subject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.roles[0]").value("SHOPPER"))
                .andExpect(jsonPath("$.email").value(subject + "@example.test"));

        long users = jdbc.sql("SELECT count(*) FROM app_user WHERE oidc_subject = :s")
                .param("s", subject).query(Long.class).single();
        assertThat(users).isEqualTo(1);

        // A second visit reuses the same row.
        mvc.perform(get("/api/v1/me").with(shopper(subject))).andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT count(*) FROM app_user WHERE oidc_subject = :s")
                .param("s", subject).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void loginStartsAuthorizationCodeWithPkceAndReturnsToTheSpa() throws Exception {
        String location = mvc.perform(get("/oauth2/authorization/idp"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        String decoded = URLDecoder.decode(location, StandardCharsets.UTF_8);
        assertThat(decoded)
                .startsWith("http://localhost:8180/realms/ecommerce/protocol/openid-connect/auth?")
                .contains("response_type=code")
                .contains("code_challenge_method=S256")
                .contains("code_challenge=")
                .contains("redirect_uri=http://localhost:5173/login/oauth2/code/idp");
    }

    @Test
    void sessionLivesInRedisAndBrowserGetsOnlySessionAndCsrfCookies() throws Exception {
        HttpResponse<String> response = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
                .send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/oauth2/authorization/idp")).build(),
                        HttpResponse.BodyHandlers.ofString());

        List<String> cookies = response.headers().allValues("set-cookie");
        assertThat(cookies).anySatisfy(c -> assertThat(c).startsWith("SESSION=").containsIgnoringCase("HttpOnly")
                .containsIgnoringCase("SameSite=Lax"));
        assertThat(cookies).allSatisfy(c -> assertThat(c).matches("^(SESSION|XSRF-TOKEN)=.*"));

        // The cookie carries only the (Base64-encoded) session id; the session itself is in Redis.
        String cookieValue = cookies.stream().filter(c -> c.startsWith("SESSION=")).findFirst().orElseThrow()
                .substring("SESSION=".length()).split(";", 2)[0];
        String sessionId = new String(Base64.getDecoder().decode(cookieValue), StandardCharsets.UTF_8);
        assertThat(redis.keys("*" + sessionId + "*")).isNotEmpty();
    }

    @Test
    void logoutEndsSessionAndReturnsIdentityProviderLogoutUrl() throws Exception {
        String subject = UUID.randomUUID().toString();
        String body = mvc.perform(post("/logout").with(shopper(subject)).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String redirect = JsonPath.read(body, "$.redirectUrl");
        assertThat(redirect)
                .startsWith("http://localhost:8180/realms/ecommerce/protocol/openid-connect/logout?")
                .contains("id_token_hint=")
                .contains("post_logout_redirect_uri=")
                // Cognito's logout endpoint (M5) needs these two.
                .contains("client_id=ecommerce-bff")
                .contains("logout_uri=");
    }

    @Test
    void logoutWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/logout").with(shopper(UUID.randomUUID().toString())))
                .andExpect(status().isForbidden());
    }
}
