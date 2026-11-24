# Local seed data

`make seed` applies every `*.sql` file in this folder, in name order, to the
local Postgres container. It runs after the app has started once, so Flyway has
already created the schema.

Seed data is **local only**. It is never on the application classpath and never
runs in any AWS environment.

Planned content (spec, "Local testing" > "Seed data"):

- Allergen taxonomy (M1)
- Products: contains cashew, may contain peanut, peanut oil (synonym case),
  almond (descendant case), nut-free, and one with no allergen data
  (fail-safe case) (M1)
- Child profiles for `parent1`: Child 1 avoids `TREE_NUT`, Child 2 avoids
  `PEANUT` (M2)

Keycloak users (`parent1`, `parent2`, `admin1`) are seeded from
`local/keycloak/ecommerce-realm.json`, not from here.
