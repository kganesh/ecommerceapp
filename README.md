# ecommerceapp

Shopping app for parents of children with nut allergies. Parents keep an avoid
list per child, and the app warns them when a product they add to the cart, or
check out, matches any child's avoid list.

**Spec (single source of truth):**
[Nut-Allergy Safe Shopping App — Central Spec](https://claude.ai/artifact/ECSj6d1thfSZdhCCzS6uwL).
Requirements, decisions, API contract and milestones live there; change the
spec before or alongside the code.

## Status

| Milestone | Scope | State |
| --- | --- | --- |
| M0 Foundations | Repo, CI, local Docker stack, skeletons | Done (PR #1) |
| M1 Catalog and taxonomy | Versioned allergens, admin catalog, RBAC | In review (PR #2) |
| M2 Identity and profiles | Cognito + BFF, child profiles, avoid lists | Not started |
| M3 Allergen check and cart | Warning contract, fail-safe, audit | Not started |
| M4 Checkout and orders | Payments, checkout re-check, order history | Not started |
| M5 Production readiness | AWS via Terraform, alerts, load and restore tests | Not started |

## Repo layout

```
backend/    Java 25, Spring Boot 4, Spring Data, Flyway
frontend/   React + TypeScript + Vite
docs/       openapi.json (API contract, checked against the running backend)
local/      Local stand-ins: Keycloak realm, Toxiproxy config, seed SQL
infra/      Terraform for AWS (M5)
```

## Run it in GitHub Codespaces (no local installs)

On GitHub: **Code → Codespaces → Create codespace on `<branch>`**. The dev
container (`.devcontainer/`) brings Java 25, Maven, Node 22 and Docker, warms
the dependency caches, and runs `make up` on every start. Then:

```bash
make app          # backend + frontend; the Ports tab opens the frontend (5173)
make check-local  # in a second terminal
make e2e
```

Ports are forwarded at `https://<codespace>-<port>.app.github.dev`: 5173
frontend, 8080 backend, 3000 Grafana, 8180 Keycloak, 8474 Toxiproxy. The
codespace needs a 4-core machine; **stop it when you're done** so it doesn't use
up your monthly allowance (it also auto-stops after 30 minutes idle by default).

## Prerequisites (running on your own machine)

- Docker with Compose v2
- JDK 25 (Eclipse Temurin) and Maven 3.9+
- Node 22+

## Run it locally

```bash
make up          # Postgres, Redis, Keycloak, Grafana/OTel, Toxiproxy
make app         # backend on :8080 (local profile, OTel agent) + frontend on :5173
make seed        # local seed data (after the backend has started once)
make check-local # M0 gate: health, API and frontend respond
```

Open http://localhost:5173. The page calls the backend through the Vite proxy
and shows its name, version and health.

| URL | What |
| --- | --- |
| http://localhost:5173 | Frontend (Vite dev server) |
| http://localhost:8080/actuator/health | Backend health |
| http://localhost:8080/swagger-ui.html | API docs |
| http://localhost:3000 | Grafana (admin/admin) — traces in Explore > Tempo |
| http://localhost:8180 | Keycloak (admin/admin), realm `ecommerce` |
| http://localhost:8474 | Toxiproxy admin API |

### Local stand-ins for AWS

| AWS | Local |
| --- | --- |
| ECS Fargate | `make backend`, or the same image via `make up-app` |
| S3 + CloudFront | Vite dev server |
| RDS PostgreSQL | `postgres:17-alpine` |
| ElastiCache | `redis:7.4-alpine` |
| Cognito | Keycloak, realm imported from `local/keycloak` |
| Managed Prometheus / Grafana | `grafana/otel-lgtm` |

Test users (local Keycloak only): `parent1` / `parent1`, `parent2` / `parent2`,
`admin1` / `admin1`.

### Fault injection

Run the backend with `SPRING_PROFILES_ACTIVE=local,chaos` to route Postgres and
Redis through Toxiproxy, then add toxics through its API, for example:

```bash
curl -s -X POST localhost:8474/proxies/redis/toxics \
  -d '{"name":"down","type":"timeout","attributes":{"timeout":0}}'
```

## Tests

```bash
make test        # backend: unit + Testcontainers; frontend: typecheck, Vitest, build
make e2e         # Playwright against the running local stack
make openapi     # regenerate docs/openapi.json and the TypeScript client types
```

The API contract in `docs/openapi.json` is checked by `OpenApiContractTest`:
if the backend's generated spec drifts from the file, the build fails. After an
intended API change, run `make openapi` and commit both the contract and the
regenerated `frontend/src/api/schema.d.ts`.
