# Local developer workflow (spec: "Local testing" > "Workflow").
# Requires: Docker with Compose v2, JDK 25, Maven 3.9+, Node 22+.

SHELL := /bin/bash
COMPOSE := docker compose
OTEL_AGENT := .tools/opentelemetry-javaagent.jar
OTEL_AGENT_URL := https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar
INFRA_SERVICES := postgres redis keycloak otel-lgtm toxiproxy

# In GitHub Codespaces the browser reaches forwarded ports at
# https://<codespace>-<port>.<forwarding domain>; login redirects must use those URLs.
ifeq ($(CODESPACES),true)
  CS_URL = https://$(CODESPACE_NAME)-$(1).$(GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN)
  export APP_PUBLIC_BASE_URL := $(call CS_URL,5173)
  export OIDC_BROWSER_BASE := $(call CS_URL,8180)
endif

.DEFAULT_GOAL := help
.PHONY: help up down reset app backend frontend seed test test-backend test-frontend e2e openapi image image-web up-app up-prod e2e-prod load-test aws-deploy check-local

help: ## List targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

up: ## Start Postgres, Redis, Keycloak, OTel stack and Toxiproxy
	$(COMPOSE) up -d --wait $(INFRA_SERVICES)
	@echo "Grafana:  http://localhost:3000 (admin/admin)"
	@echo "Keycloak: http://localhost:8180 (admin/admin; realm 'ecommerce')"

down: ## Stop the local stack (keeps data)
	$(COMPOSE) --profile app --profile prod down

reset: ## Stop the stack and delete all local data
	$(COMPOSE) --profile app --profile prod down -v

app: ## Run backend (local profile) and frontend dev server together
	$(MAKE) -j2 backend frontend

$(OTEL_AGENT):
	@mkdir -p .tools
	curl -fsSL -o $(OTEL_AGENT) $(OTEL_AGENT_URL)

backend: $(OTEL_AGENT) ## Run the backend with the OTel agent, 100% trace sampling
	cd backend && \
	OTEL_SERVICE_NAME=ecommerceapp-backend \
	OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318 \
	OTEL_TRACES_SAMPLER=always_on \
	mvn -q spring-boot:run \
	  -Dspring-boot.run.profiles=local \
	  -Dspring-boot.run.jvmArguments="-javaagent:../$(OTEL_AGENT)"

frontend: ## Run the Vite dev server (proxies /api to :8080)
	cd frontend && npm install --no-audit --no-fund && npm run dev

seed: ## Load local seed data (local only, never in prod)
	@if [ "$$($(COMPOSE) exec -T postgres psql -U ecommerce -d ecommerce -tAc "SELECT to_regclass('public.product') IS NOT NULL")" != "t" ]; then \
	  echo "seed: database tables don't exist yet. Start the backend first ('make app'), wait for"; \
	  echo "      'Started EcommerceApplication' (Flyway creates the tables on startup), then run 'make seed' again."; \
	  exit 1; \
	fi
	@for f in local/seed/*.sql; do \
	  echo "seed: $$f"; \
	  $(COMPOSE) exec -T postgres psql -U ecommerce -d ecommerce -v ON_ERROR_STOP=1 -q < $$f || exit 1; \
	done

test: test-backend test-frontend ## Unit + Testcontainers integration tests

test-backend:
	cd backend && mvn -B verify

test-frontend:
	cd frontend && npm ci --no-audit --no-fund && npm run typecheck && npm test && npm run build

e2e: ## Playwright suite against the running local stack
	cd frontend && npx playwright install --with-deps chromium && npm run e2e

openapi: ## Regenerate docs/openapi.json from the backend and the TS client types
	cd backend && mvn -B -q test -Dtest=OpenApiContractTest -Dopenapi.update=true -Dsurefire.failIfNoSpecifiedTests=false
	cd frontend && npm run gen:api

image: ## Build the backend container image (same image that goes to ECR)
	cd backend && mvn -B spring-boot:build-image -DskipTests \
	  -Dspring-boot.build-image.imageName=ecommerceapp-backend:local

image-web: ## Build the SPA image (nginx) for the prod-like stack
	docker build -t ecommerceapp-web:local frontend

up-app: image ## Run the backend image inside Compose
	$(COMPOSE) --profile app up -d --wait

up-prod: image image-web ## M5: production images behind a prod-like web tier on http://localhost:8081
	$(COMPOSE) --profile prod up -d --wait
	@echo "App: http://localhost:8081  (then 'make seed' once the backend has started)"

e2e-prod: ## Playwright suite (incl. accessibility scan) against the prod-like stack
	cd frontend && npx playwright install --with-deps chromium && E2E_BASE_URL=http://localhost:8081 npm run e2e

load-test: ## M5: k6 at 10 req/s against the prod-like stack (needs Docker)
	docker run --rm -i --network host -e BASE_URL=http://localhost:8081 -v "$(CURDIR)/load:/load:ro" \
	  grafana/k6:latest run /load/shop.js

aws-deploy: ## M5: first deploy to AWS (Terraform + image + SPA); see infra/README.md
	./infra/deploy.sh

check-local: ## M0 gate: verify the local stack answers
	@curl -fsS http://localhost:8080/actuator/health | grep -q '"UP"' && echo "backend health: UP"
	@curl -fsS http://localhost:8080/api/v1/meta && echo
	@curl -fsS -o /dev/null http://localhost:5173 && echo "frontend: serving on :5173"
