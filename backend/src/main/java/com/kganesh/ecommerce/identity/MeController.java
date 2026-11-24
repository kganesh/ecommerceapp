package com.kganesh.ecommerce.identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@RestController
@RequestMapping(path = "/api/v1/me", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "session", description = "Who is signed in")
class MeController {

    static final String LOGIN_URL = "/oauth2/authorization/idp";

    @Schema(description = "The current browser session. Tokens are never exposed; only identity and roles.")
    record Me(
            @Schema(requiredMode = REQUIRED) boolean authenticated,
            @Schema(description = "Present when signed in") String username,
            String email,
            @Schema(requiredMode = REQUIRED, description = "Application roles: SHOPPER, CATALOG_ADMIN") List<String> roles,
            @Schema(requiredMode = REQUIRED, description = "Navigate the browser here to sign in") String loginUrl) {
    }

    private final CurrentUserService users;

    MeController(CurrentUserService users) {
        this.users = users;
    }

    @GetMapping
    @Operation(operationId = "getMe", summary = "The signed-in user, or authenticated=false")
    Me me(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof OidcUser user)) {
            return new Me(false, null, null, List.of(), LOGIN_URL);
        }
        users.userId(authentication);
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .sorted()
                .toList();
        String username = user.getPreferredUsername() != null ? user.getPreferredUsername() : user.getSubject();
        return new Me(true, username, user.getEmail(), roles, LOGIN_URL);
    }
}
