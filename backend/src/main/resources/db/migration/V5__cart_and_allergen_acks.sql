-- M3: cart, allergen warnings and acknowledgments (spec: FR-6, FR-7, FR-10 to FR-12).

CREATE TABLE cart (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL UNIQUE REFERENCES app_user (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cart_item (
    cart_id                 UUID        NOT NULL REFERENCES cart (id),
    product_id              UUID        NOT NULL REFERENCES product (id),
    qty                     INT         NOT NULL CHECK (qty BETWEEN 1 AND 99),
    -- The product's allergen version when the line was added (NULL: no data then).
    allergen_version_at_add INT,
    added_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (cart_id, product_id)
);

-- A warning shown to a parent (FR-12). One row per product per showing; rows that
-- share a token were shown together and are acknowledged together.
-- The fingerprint binds the warning to the product's allergen version and to every
-- profile's avoid-list version at that moment: if either changes, the warning and any
-- acknowledgment of it no longer apply (FR-9).
CREATE TABLE allergen_warning (
    id               UUID        PRIMARY KEY,
    token            UUID        NOT NULL,
    user_id          UUID        NOT NULL REFERENCES app_user (id),
    product_id       UUID        NOT NULL REFERENCES product (id),
    allergen_version INT,
    fingerprint      CHAR(64)    NOT NULL,
    context          VARCHAR(16) NOT NULL CHECK (context IN ('ADD_TO_CART', 'CHECKOUT')),
    -- TRUE when the product has no allergen data: "unable to verify" (FR-11).
    unverified       BOOLEAN     NOT NULL,
    shown_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ NOT NULL
);
CREATE INDEX allergen_warning_token_idx ON allergen_warning (token);
CREATE INDEX allergen_warning_user_product_idx ON allergen_warning (user_id, product_id, fingerprint);

CREATE TABLE allergen_warning_match (
    warning_id  UUID        NOT NULL REFERENCES allergen_warning (id),
    profile_id  UUID        NOT NULL REFERENCES child_profile (id),
    allergen_id VARCHAR(64) NOT NULL REFERENCES allergen (id),
    match_type  VARCHAR(16) NOT NULL CHECK (match_type IN ('CONTAINS', 'MAY_CONTAIN')),
    PRIMARY KEY (warning_id, profile_id, allergen_id)
);

-- A parent's explicit acknowledgment of a warning (FR-7, FR-12). Single use: one per warning.
CREATE TABLE allergen_ack (
    id         UUID        PRIMARY KEY,
    warning_id UUID        NOT NULL UNIQUE REFERENCES allergen_warning (id),
    user_id    UUID        NOT NULL REFERENCES app_user (id),
    acked_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER allergen_warning_append_only
    BEFORE UPDATE OR DELETE ON allergen_warning
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();
CREATE TRIGGER allergen_warning_match_append_only
    BEFORE UPDATE OR DELETE ON allergen_warning_match
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();
CREATE TRIGGER allergen_ack_append_only
    BEFORE UPDATE OR DELETE ON allergen_ack
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();
