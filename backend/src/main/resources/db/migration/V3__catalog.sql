-- M1: catalog with versioned, immutable allergen data (spec: Data model, FR-4, FR-14).
-- Every warning and acknowledgment (M3) points at an exact allergen version,
-- so versions are append-only: the database rejects UPDATE and DELETE on them.

CREATE TABLE product (
    id                       UUID         PRIMARY KEY,
    sku                      VARCHAR(64)  NOT NULL UNIQUE,
    name                     VARCHAR(200) NOT NULL,
    description              TEXT,
    price_cents              BIGINT       NOT NULL CHECK (price_cents >= 0),
    currency                 CHAR(3)      NOT NULL DEFAULT 'USD',
    active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    -- NULL means no allergen data has been entered yet. The allergen check (M3)
    -- must treat that as "unable to verify", never as "safe" (FR-11).
    current_allergen_version INT,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX product_name_idx ON product (lower(name));

CREATE TABLE product_allergen_version (
    product_id UUID         NOT NULL REFERENCES product (id),
    version    INT          NOT NULL CHECK (version >= 1),
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, version)
);

CREATE TABLE product_allergen (
    product_id  UUID        NOT NULL,
    version     INT         NOT NULL,
    allergen_id VARCHAR(64) NOT NULL REFERENCES allergen (id),
    match_type  VARCHAR(16) NOT NULL CHECK (match_type IN ('CONTAINS', 'MAY_CONTAIN')),
    PRIMARY KEY (product_id, version, allergen_id),
    FOREIGN KEY (product_id, version) REFERENCES product_allergen_version (product_id, version)
);

-- The current pointer must name a version that exists for this product.
ALTER TABLE product
    ADD CONSTRAINT product_current_allergen_version_fk
    FOREIGN KEY (id, current_allergen_version)
    REFERENCES product_allergen_version (product_id, version)
    DEFERRABLE INITIALLY DEFERRED;

CREATE FUNCTION reject_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'table % is append-only; % is not allowed', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER product_allergen_version_immutable
    BEFORE UPDATE OR DELETE ON product_allergen_version
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

CREATE TRIGGER product_allergen_immutable
    BEFORE UPDATE OR DELETE ON product_allergen
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

-- Append-only record of every catalog edit (spec: catalog_audit).
CREATE TABLE catalog_audit (
    id           BIGSERIAL    PRIMARY KEY,
    actor        VARCHAR(255) NOT NULL,
    product_id   UUID         NOT NULL REFERENCES product (id),
    action       VARCHAR(32)  NOT NULL CHECK (action IN ('PRODUCT_CREATED', 'PRODUCT_UPDATED', 'ALLERGENS_CHANGED')),
    from_version INT,
    to_version   INT,
    at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX catalog_audit_product_idx ON catalog_audit (product_id, at);

CREATE TRIGGER catalog_audit_append_only
    BEFORE UPDATE OR DELETE ON catalog_audit
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();
