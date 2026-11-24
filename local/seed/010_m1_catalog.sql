-- M1 seed: one product per allergen case in the spec ("Local testing" > "Seed data").
-- Local only. Safe to re-run: every insert skips rows that already exist.
BEGIN;

INSERT INTO product (id, sku, name, description, price_cents) VALUES
    ('00000000-0000-4000-8000-000000000001', 'SEED-CASHEW-BUTTER', 'Cashew Butter',
     'Contains cashew (descendant of tree nuts).', 899),
    ('00000000-0000-4000-8000-000000000002', 'SEED-CHOC-CHIP', 'Chocolate Chip Cookies',
     'Label: may contain peanuts.', 449),
    ('00000000-0000-4000-8000-000000000003', 'SEED-STIR-FRY', 'Stir-Fry Sauce',
     'Label lists "arachis oil" (synonym case: peanut).', 599),
    ('00000000-0000-4000-8000-000000000004', 'SEED-ALMOND-BAR', 'Almond Granola Bars',
     'Contains almond (descendant of tree nuts).', 529),
    ('00000000-0000-4000-8000-000000000005', 'SEED-RICE-CAKES', 'Plain Rice Cakes',
     'Verified: no listed allergens.', 299),
    ('00000000-0000-4000-8000-000000000006', 'SEED-TRAIL-MIX', 'Trail Mix Surprise',
     'No allergen data entered yet (fail-safe case).', 699)
ON CONFLICT (id) DO NOTHING;

INSERT INTO product_allergen_version (product_id, version, created_by) VALUES
    ('00000000-0000-4000-8000-000000000001', 1, 'seed'),
    ('00000000-0000-4000-8000-000000000002', 1, 'seed'),
    ('00000000-0000-4000-8000-000000000003', 1, 'seed'),
    ('00000000-0000-4000-8000-000000000004', 1, 'seed'),
    ('00000000-0000-4000-8000-000000000005', 1, 'seed')
ON CONFLICT DO NOTHING;

INSERT INTO product_allergen (product_id, version, allergen_id, match_type) VALUES
    ('00000000-0000-4000-8000-000000000001', 1, 'TREE_NUT.CASHEW', 'CONTAINS'),
    ('00000000-0000-4000-8000-000000000002', 1, 'PEANUT',          'MAY_CONTAIN'),
    ('00000000-0000-4000-8000-000000000002', 1, 'WHEAT',           'CONTAINS'),
    ('00000000-0000-4000-8000-000000000002', 1, 'MILK',            'CONTAINS'),
    ('00000000-0000-4000-8000-000000000002', 1, 'EGG',             'CONTAINS'),
    ('00000000-0000-4000-8000-000000000003', 1, 'PEANUT',          'CONTAINS'),
    ('00000000-0000-4000-8000-000000000003', 1, 'SOY',             'CONTAINS'),
    ('00000000-0000-4000-8000-000000000004', 1, 'TREE_NUT.ALMOND', 'CONTAINS'),
    ('00000000-0000-4000-8000-000000000004', 1, 'WHEAT',           'CONTAINS')
ON CONFLICT DO NOTHING;

UPDATE product SET current_allergen_version = 1
WHERE id IN ('00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000002',
             '00000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000004',
             '00000000-0000-4000-8000-000000000005')
  AND current_allergen_version IS NULL;

INSERT INTO catalog_audit (actor, product_id, action, from_version, to_version)
SELECT 'seed', p.id, 'PRODUCT_CREATED', NULL, NULL
FROM product p
WHERE p.sku LIKE 'SEED-%'
  AND NOT EXISTS (SELECT 1 FROM catalog_audit a WHERE a.product_id = p.id);

COMMIT;
