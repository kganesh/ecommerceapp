package com.kganesh.ecommerce.identity;

import com.kganesh.ecommerce.common.ApiExceptions;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Resolves the signed-in shopper to an {@code app_user} row, creating it on first use
 * (just-in-time provisioning from the identity provider's subject claim).
 */
@Service
public class CurrentUserService {

    private final JdbcClient jdbc;

    CurrentUserService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The app user id for an OIDC sign-in; inserts or refreshes the row atomically. */
    @Transactional
    public UUID userId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser user)) {
            throw new NotSignedIn();
        }
        return jdbc.sql("""
                        INSERT INTO app_user (id, oidc_subject, email)
                        VALUES (:id, :subject, :email)
                        ON CONFLICT (oidc_subject)
                        DO UPDATE SET email = EXCLUDED.email, last_seen_at = now()
                        RETURNING id
                        """)
                .param("id", UUID.randomUUID())
                .param("subject", user.getSubject())
                .param("email", user.getEmail())
                .query(UUID.class)
                .single();
    }

    /** Signed in, but not with the identity provider (e.g. a test principal). */
    public static final class NotSignedIn extends ApiExceptions.ApiException {
        NotSignedIn() {
            super(HttpStatus.UNAUTHORIZED, "NOT_SIGNED_IN", "Sign in with the identity provider to continue");
        }
    }
}
