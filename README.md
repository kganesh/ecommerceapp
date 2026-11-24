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
| M2 Identity and profiles | Cognito + BFF, child profiles, avoid lists | In review (PR #3) |
| M3 Allergen check and cart | Warning contract, fail-safe, audit | In review (PR #4) |
| M4 Checkout and orders | Payments, checkout re-check, order history | In review (PR #5) |
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

## Share a running codespace

`make share` makes the app (5173) and sign-in (8180) ports public and prints the link to send.
Use `make share SHARE_PORT=8081` for the prod-like stack. Anyone with the link can use the test
accounts, and the Keycloak admin page becomes public too, so change its `admin/admin` password first.

## Prod-like stack and AWS (M5)

`make up-prod` builds the production images and runs them behind an nginx web tier that routes
like CloudFront, at http://localhost:8081 (sign in as parent1/parent1 or admin1/admin1). Then:

- `make seed` once the backend is up
- `make e2e-prod`: Playwright suite, including the accessibility scan (`e2e/a11y.spec.ts`)
- `make load-test`: k6 at 10 requests/second (`load/shop.js`); fails if p95 > 500 ms (proposed NFR-3 target)

CI runs all three on every pull request. To deploy to AWS, see [infra/README.md](infra/README.md).

## AI label reading (optional, M4.5)

Admins can photograph a label in the product editor; Claude on Amazon Bedrock suggests
allergens, a keyword check backs it up, and the admin reviews and saves. It is off by default
and the control stays hidden.

To turn it on (locally or in Codespaces):

1. In the AWS account, enable access to Claude Sonnet 5.5 in Amazon Bedrock for a US region.
2. Give the backend AWS credentials: Codespaces secrets `AWS_ACCESS_KEY_ID`,
   `AWS_SECRET_ACCESS_KEY` and `AWS_REGION` (e.g. `us-east-1`), or any standard AWS credential source.
3. Start the backend with `AI_PROVIDER=bedrock` (optionally `AI_MODEL_ID`, default
   `us.anthropic.claude-sonnet-5-5`).

Before using it in production, run it against a set of real labels with known answers (NFR-12).

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
