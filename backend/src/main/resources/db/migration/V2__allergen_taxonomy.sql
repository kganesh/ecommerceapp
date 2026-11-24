-- M1: allergen taxonomy (spec: Glossary and allergen taxonomy).
-- Matching is always by allergen ID, never by text. Synonyms help admins tag
-- products correctly; they are not used for matching.

CREATE TABLE allergen (
    id           VARCHAR(64)  PRIMARY KEY,
    parent_id    VARCHAR(64)  REFERENCES allergen (id),
    display_name VARCHAR(128) NOT NULL,
    synonyms     TEXT[]       NOT NULL DEFAULT '{}',
    sort_order   INT          NOT NULL DEFAULT 0,
    CONSTRAINT allergen_id_format CHECK (id ~ '^[A-Z][A-Z_]*(\.[A-Z][A-Z_]*)*$'),
    CONSTRAINT allergen_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

-- Closure table: every (ancestor, descendant) pair, including each node with
-- itself at depth 0. Avoiding TREE_NUT therefore matches TREE_NUT.CASHEW.
CREATE TABLE allergen_closure (
    ancestor_id   VARCHAR(64) NOT NULL REFERENCES allergen (id),
    descendant_id VARCHAR(64) NOT NULL REFERENCES allergen (id),
    depth         INT         NOT NULL CHECK (depth >= 0),
    PRIMARY KEY (ancestor_id, descendant_id)
);
CREATE INDEX allergen_closure_descendant_idx ON allergen_closure (descendant_id);

-- Rebuilds the closure from the parent links. Every migration that changes the
-- taxonomy must call this at the end.
CREATE FUNCTION refresh_allergen_closure() RETURNS void LANGUAGE sql AS $$
    DELETE FROM allergen_closure;
    INSERT INTO allergen_closure (ancestor_id, descendant_id, depth)
    WITH RECURSIVE walk (ancestor_id, descendant_id, depth) AS (
        SELECT id, id, 0 FROM allergen
        UNION ALL
        SELECT w.ancestor_id, a.id, w.depth + 1
        FROM walk w
        JOIN allergen a ON a.parent_id = w.descendant_id
    )
    SELECT ancestor_id, descendant_id, depth FROM walk;
$$;

-- Phase 1 taxonomy: the US major food allergens, with tree nuts in detail.
-- Peanut is a legume and its own root, never a tree nut.
INSERT INTO allergen (id, parent_id, display_name, synonyms, sort_order) VALUES
    ('PEANUT',               NULL,       'Peanut',     '{"groundnut","arachis oil","arachis","monkey nut","beer nuts"}', 10),
    ('TREE_NUT',             NULL,       'Tree nuts',  '{}', 20),
    ('TREE_NUT.ALMOND',      'TREE_NUT', 'Almond',     '{"marzipan","almond paste","amaretti"}', 21),
    ('TREE_NUT.BRAZIL_NUT',  'TREE_NUT', 'Brazil nut', '{}', 22),
    ('TREE_NUT.CASHEW',      'TREE_NUT', 'Cashew',     '{}', 23),
    ('TREE_NUT.HAZELNUT',    'TREE_NUT', 'Hazelnut',   '{"filbert","gianduja","praline"}', 24),
    ('TREE_NUT.MACADAMIA',   'TREE_NUT', 'Macadamia',  '{}', 25),
    ('TREE_NUT.PECAN',       'TREE_NUT', 'Pecan',      '{}', 26),
    ('TREE_NUT.PINE_NUT',    'TREE_NUT', 'Pine nut',   '{"pignoli","pinon"}', 27),
    ('TREE_NUT.PISTACHIO',   'TREE_NUT', 'Pistachio',  '{}', 28),
    ('TREE_NUT.WALNUT',      'TREE_NUT', 'Walnut',     '{}', 29),
    ('MILK',                 NULL,       'Milk',       '{"casein","whey","lactose"}', 40),
    ('EGG',                  NULL,       'Egg',        '{"albumin","ovalbumin"}', 50),
    ('FISH',                 NULL,       'Fish',       '{}', 60),
    ('CRUSTACEAN_SHELLFISH', NULL,       'Crustacean shellfish', '{"shrimp","crab","lobster"}', 70),
    ('WHEAT',                NULL,       'Wheat',      '{"semolina","spelt","durum"}', 80),
    ('SOY',                  NULL,       'Soy',        '{"soya","edamame","tofu"}', 90),
    ('SESAME',               NULL,       'Sesame',     '{"tahini","benne"}', 100);

SELECT refresh_allergen_closure();
