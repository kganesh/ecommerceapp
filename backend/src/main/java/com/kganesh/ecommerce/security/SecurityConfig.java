package com.kganesh.ecommerce.security;

import com.kganesh.ecommerce.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Access rules and login (spec: Security, privacy and compliance; D11).
 *
 * <ul>
 *   <li>Backend-for-frontend: the browser signs in through {@code /oauth2/authorization/idp}
 *       (Authorization Code + PKCE). Tokens stay in the server session, which lives in Redis
 *       (Spring Session); the browser only holds an HttpOnly, SameSite session cookie.</li>
 *   <li>Browsing the catalog and the taxonomy is public; {@code /api/v1/me} tells the SPA
 *       who is signed in.</li>
 *   <li>{@code /api/v1/profiles/**} requires SHOPPER; {@code /api/v1/admin/**} requires CATALOG_ADMIN.</li>
 *   <li>Cookie sessions, so every state-changing request needs the CSRF header.</li>
 *   <li>API calls without a session get 401 JSON, never a login redirect; the SPA starts login itself.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    static final String LOGIN_PATH = "/oauth2/authorization/idp";

    @Bean
    OAuth2AuthorizedClientRepository authorizedClientRepository() {
        // Keep access and refresh tokens in the (Redis-backed) session, not in instance memory.
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, AppProperties app,
                                    ClientRegistrationRepository registrations,
                                    OAuth2AuthorizedClientRepository authorizedClients) throws Exception {
        DefaultOAuth2AuthorizationRequestResolver authorizationRequests =
                new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        // PKCE for our confidential client too.
        authorizationRequests.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

        String home = app.publicBaseUrl() + "/";

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/meta", "/api/v1/me").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/allergens").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole(Roles.CATALOG_ADMIN)
                        .requestMatchers("/api/v1/profiles", "/api/v1/profiles/**").hasRole(Roles.SHOPPER)
                        .requestMatchers("/api/v1/carts/**", "/api/v1/orders", "/api/v1/orders/**").hasRole(Roles.SHOPPER)
                        .anyRequest().authenticated())
                .oauth2Login(login -> login
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(authorizationRequests))
                        .authorizedClientRepository(authorizedClients)
                        .userInfoEndpoint(userInfo -> userInfo
                                .userAuthoritiesMapper(new RolesClaimAuthoritiesMapper(app.oidc().rolesClaim())))
                        .defaultSuccessUrl(home, true)
                        .failureUrl(home + "?login=failed"))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(new OidcLogoutHandler(app.oidc().endSessionUri(), home)))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }
}
