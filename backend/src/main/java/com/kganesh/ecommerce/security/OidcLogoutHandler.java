package com.kganesh.ecommerce.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Ends the server session (already done by Spring's logout filter), then tells the
 * SPA where to send the browser to end the identity-provider session too
 * (OIDC RP-initiated logout). The SPA calls {@code POST /logout} with fetch, so a
 * JSON body works better than a cross-origin redirect.
 */
final class OidcLogoutHandler implements LogoutSuccessHandler {

    private final String endSessionUri;
    private final String postLogoutRedirectUri;
    private final String clientId;

    OidcLogoutHandler(String endSessionUri, String postLogoutRedirectUri, String clientId) {
        this.endSessionUri = endSessionUri;
        this.postLogoutRedirectUri = postLogoutRedirectUri;
        this.clientId = clientId;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException {
        String redirectUrl = postLogoutRedirectUri;
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser user) {
            redirectUrl = UriComponentsBuilder.fromUriString(endSessionUri)
                    .queryParam("id_token_hint", user.getIdToken().getTokenValue())
                    .queryParam("post_logout_redirect_uri", postLogoutRedirectUri)
                    // Cognito's /logout ignores the two standard parameters above and needs these.
                    .queryParam("client_id", clientId)
                    .queryParam("logout_uri", postLogoutRedirectUri)
                    .encode()
                    .toUriString();
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"redirectUrl\":\"" + redirectUrl.replace("\"", "%22") + "\"}");
    }
}
