package com.kganesh.ecommerce.security;

/** Application roles, issued by the identity provider (Keycloak locally, Cognito on AWS). */
public final class Roles {

    /** Parent or guardian who shops and manages child profiles. */
    public static final String SHOPPER = "SHOPPER";

    /** Enters and edits product and allergen data (FR-14). */
    public static final String CATALOG_ADMIN = "CATALOG_ADMIN";

    private Roles() {
    }
}
