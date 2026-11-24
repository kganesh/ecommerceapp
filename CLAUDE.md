# Working in this repo

- The spec is the single source of truth:
  https://claude.ai/artifact/ECSj6d1thfSZdhCCzS6uwL. Requirements are numbered
  FR-x / NFR-x; reference them in commits and PRs. If code needs to differ from
  the spec, change the spec first.
- Safety rule: the allergen check fails toward warning. Never add a code path
  that skips or silently passes the check on error, timeout or missing data.
- API changes: update the controller, run `make openapi`, commit
  `docs/openapi.json` and `frontend/src/api/schema.d.ts` together.
- Postgres is the system of record. Redis is a cache and session store only;
  nothing safety-relevant may exist only in Redis.
- Schema changes go through Flyway migrations in
  `backend/src/main/resources/db/migration`; never edit an applied migration.
- Local seed data lives in `local/seed` and must never ship on the classpath.
- Commits are authored as `kganesh <kganesh@users.noreply.github.com>`.
