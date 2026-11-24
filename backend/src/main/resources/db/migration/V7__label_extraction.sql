-- M4.5: AI label reading (spec: AI assist, FR-16 to FR-20, D19).
-- An extraction records what the model and the keyword backstop suggested for one
-- label photo. Suggestions never change the catalog by themselves: an admin reviews
-- them and saves an allergen version, and the audit row links back here.
-- The photo itself is not stored, only its SHA-256.

CREATE TABLE label_extraction (
    id                    UUID         PRIMARY KEY,
    actor                 VARCHAR(255) NOT NULL,
    model_id              VARCHAR(200) NOT NULL,
    image_sha256          CHAR(64)     NOT NULL,
    media_type            VARCHAR(32)  NOT NULL,
    image_bytes           INT          NOT NULL CHECK (image_bytes > 0),
    readable              BOOLEAN      NOT NULL,
    ingredients_text      TEXT,
    contains_statement    TEXT,
    may_contain_statement TEXT,
    unmatched_terms       TEXT[]       NOT NULL DEFAULT '{}',
    notes                 TEXT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX label_extraction_actor_idx ON label_extraction (actor, created_at);

CREATE TABLE label_extraction_item (
    extraction_id UUID         NOT NULL REFERENCES label_extraction (id),
    allergen_id   VARCHAR(64)  NOT NULL REFERENCES allergen (id),
    match_type    VARCHAR(16)  NOT NULL CHECK (match_type IN ('CONTAINS', 'MAY_CONTAIN')),
    source        VARCHAR(32)  NOT NULL CHECK (source IN ('MODEL', 'KEYWORD_SCAN', 'MODEL_AND_KEYWORD_SCAN')),
    evidence      TEXT,
    PRIMARY KEY (extraction_id, allergen_id)
);

CREATE TRIGGER label_extraction_append_only
    BEFORE UPDATE OR DELETE ON label_extraction
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

CREATE TRIGGER label_extraction_item_append_only
    BEFORE UPDATE OR DELETE ON label_extraction_item
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

-- Which extraction, if any, an allergen save was based on (FR-19).
ALTER TABLE catalog_audit
    ADD COLUMN label_extraction_id UUID REFERENCES label_extraction (id);

-- Label terms the keyword backstop (FR-18) needs that the M1 taxonomy lacked.
-- A generic "nuts" on a label is treated as tree nuts: the admin reviews it.
UPDATE allergen SET synonyms = synonyms || '{nut}'              WHERE id = 'TREE_NUT';
UPDATE allergen SET synonyms = synonyms || '{soybean}'          WHERE id = 'SOY';
UPDATE allergen SET synonyms = synonyms || '{prawn,crayfish}'   WHERE id = 'CRUSTACEAN_SHELLFISH';
SELECT refresh_allergen_closure();
