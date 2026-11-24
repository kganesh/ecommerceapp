package com.kganesh.ecommerce.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings under {@code app.*}.
 *
 * @param publicBaseUrl origin the browser uses for the SPA, e.g. http://localhost:5173;
 *                      login and logout redirects return here
 * @param oidc          identity-provider settings not covered by Spring's OAuth2 client
 */
@ConfigurationProperties("app")
public record AppProperties(String publicBaseUrl, Oidc oidc) {

    /**
     * @param endSessionUri RP-initiated logout endpoint, as the browser reaches it
     * @param rolesClaim    ID-token claim that lists the user's roles
     */
    public record Oidc(String endSessionUri, String rolesClaim) {
    }
}
