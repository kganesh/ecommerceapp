# docs

- **Spec (source of truth):** https://claude.ai/artifact/ECSj6d1thfSZdhCCzS6uwL
- **`openapi.json`:** the API contract. `OpenApiContractTest` fails the build if
  the backend drifts from it, and the frontend's types in
  `frontend/src/api/schema.d.ts` are generated from it. Regenerate both with
  `make openapi`.
