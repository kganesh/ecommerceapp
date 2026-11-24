package com.kganesh.ecommerce.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Turns the identity provider's roles claim into Spring authorities: {@code SHOPPER}
 * becomes {@code ROLE_SHOPPER}. Only known application roles are granted, so an
 * unrelated group in the identity provider can never widen access.
 */
final class RolesClaimAuthoritiesMapper implements GrantedAuthoritiesMapper {

    private static final Set<String> KNOWN_ROLES = Set.of(Roles.SHOPPER, Roles.CATALOG_ADMIN);

    private final String rolesClaim;

    RolesClaimAuthoritiesMapper(String rolesClaim) {
        this.rolesClaim = rolesClaim;
    }

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<GrantedAuthority> mapped = new LinkedHashSet<>(authorities);
        for (GrantedAuthority authority : authorities) {
            if (authority instanceof OidcUserAuthority oidc) {
                Object claim = oidc.getIdToken().getClaims().get(rolesClaim);
                if (claim == null && oidc.getUserInfo() != null) {
                    claim = oidc.getUserInfo().getClaims().get(rolesClaim);
                }
                if (claim instanceof Collection<?> roles) {
                    for (Object role : roles) {
                        String name = String.valueOf(role).toUpperCase(Locale.ROOT);
                        if (KNOWN_ROLES.contains(name)) {
                            mapped.add(new SimpleGrantedAuthority("ROLE_" + name));
                        }
                    }
                }
            }
        }
        return mapped;
    }
}
