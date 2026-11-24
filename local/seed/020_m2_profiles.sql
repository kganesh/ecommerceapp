-- M2 seed: parent1's two children (spec: "Local testing" > "Seed data").
-- User ids match the fixed Keycloak user ids in local/keycloak/ecommerce-realm.json,
-- so signing in as parent1 finds these profiles. Local only; safe to re-run.
BEGIN;

INSERT INTO app_user (id, oidc_subject, email) VALUES
    ('11111111-1111-4111-8111-111111111111', '11111111-1111-4111-8111-111111111111', 'parent1@example.test')
ON CONFLICT (oidc_subject) DO NOTHING;

INSERT INTO child_profile (id, user_id, label, avoid_list_version) VALUES
    ('aaaaaaaa-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Child 1', 1),
    ('aaaaaaaa-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'Child 2', 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO avoid_list_entry (profile_id, allergen_id) VALUES
    ('aaaaaaaa-0000-4000-8000-000000000001', 'TREE_NUT'),
    ('aaaaaaaa-0000-4000-8000-000000000002', 'PEANUT')
ON CONFLICT DO NOTHING;

COMMIT;
