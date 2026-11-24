-- M4: orders (spec: FR-8, FR-15, NFR-11). "order" is reserved in SQL, hence customer_order.
-- Orders are a record of what was sold: append-only, like the allergen audit.

CREATE TABLE customer_order (
    id                UUID         PRIMARY KEY,
    user_id           UUID         NOT NULL REFERENCES app_user (id),
    -- Client-supplied Idempotency-Key: a retried checkout returns the same order.
    idempotency_key   VARCHAR(64)  NOT NULL,
    status            VARCHAR(16)  NOT NULL CHECK (status IN ('PLACED')),
    total_cents       BIGINT       NOT NULL CHECK (total_cents >= 0),
    currency          CHAR(3)      NOT NULL,
    payment_provider  VARCHAR(32)  NOT NULL,
    -- Provider's charge reference only; card data never reaches our servers (NFR-11).
    payment_reference VARCHAR(128) NOT NULL,
    placed_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (user_id, idempotency_key)
);
CREATE INDEX customer_order_user_idx ON customer_order (user_id, placed_at DESC);

CREATE TABLE order_line (
    order_id                UUID         NOT NULL REFERENCES customer_order (id),
    line_no                 INT          NOT NULL,
    product_id              UUID         NOT NULL REFERENCES product (id),
    product_name            VARCHAR(200) NOT NULL,
    qty                     INT          NOT NULL CHECK (qty BETWEEN 1 AND 99),
    unit_price_cents        BIGINT       NOT NULL CHECK (unit_price_cents >= 0),
    -- The allergen version sold (NULL: no allergen data at the time).
    allergen_version        INT,
    -- The warning the parent acknowledged for this line, if one applied (FR-15).
    acknowledged_warning_id UUID         REFERENCES allergen_warning (id),
    PRIMARY KEY (order_id, line_no)
);

CREATE TRIGGER customer_order_append_only
    BEFORE UPDATE OR DELETE ON customer_order
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();
CREATE TRIGGER order_line_append_only
    BEFORE UPDATE OR DELETE ON order_line
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();
