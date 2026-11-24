#!/usr/bin/env bash
# Runs once when the codespace is created: warm the caches so `make app`
# and `make test` start fast. `make up` runs on every start (postStartCommand).
set -euo pipefail

echo "== Frontend dependencies"
(cd frontend && npm ci --no-audit --no-fund)

echo "== Backend dependencies (offline cache)"
(cd backend && mvn -B -q -ntp dependency:go-offline)

echo "== OpenTelemetry Java agent"
make .tools/opentelemetry-javaagent.jar

echo "== Done. Next: 'make app', then open the forwarded port 5173."
