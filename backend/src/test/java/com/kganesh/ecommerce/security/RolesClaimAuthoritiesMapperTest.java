package com.kganesh.ecommerce.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RolesClaimAuthoritiesMapperTest {

    private static OidcUserAuthority withClaims(Map<String, Object> claims) {
        Map<String, Object> all = new HashMap<>(claims);
        all.put("sub", "user-1");
        OidcIdToken token = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), all);
        return new OidcUserAuthority(token);
    }

    private static List<String> map(String claim, Map<String, Object> claims) {
        return new RolesClaimAuthoritiesMapper(claim).mapAuthorities(List.of(withClaims(claims))).stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .sorted()
                .toList();
    }

    @Test
    void mapsKnownRolesFromTheClaim() {
        assertThat(map("roles", Map.of("roles", List.of("SHOPPER", "catalog_admin"))))
                .containsExactly("ROLE_CATALOG_ADMIN", "ROLE_SHOPPER");
    }

    @Test
    void ignoresUnknownGroupsSoTheyCannotWidenAccess() {
        assertThat(map("roles", Map.of("roles", List.of("offline_access", "ADMIN", "default-roles-ecommerce"))))
                .isEmpty();
    }

    @Test
    void readsAConfigurableClaimSuchAsCognitoGroups() {
        assertThat(map("cognito:groups", Map.of("cognito:groups", List.of("SHOPPER"))))
                .containsExactly("ROLE_SHOPPER");
    }

    @Test
    void noClaimMeansNoApplicationRoles() {
        assertThat(map("roles", Map.of())).isEmpty();
    }
}
