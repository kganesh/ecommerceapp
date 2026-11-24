import createClient from 'openapi-fetch'
import type { components, paths } from './schema'

/**
 * Typed API client generated from docs/openapi.json (`npm run gen:api`).
 * Same-origin: the Vite proxy (dev) and CloudFront (prod) route /api to the backend.
 */
export const api = createClient<paths>({
  baseUrl: typeof window === 'undefined' ? 'http://localhost' : window.location.origin,
  credentials: 'same-origin',
  // Resolve fetch per call (not at module load) so tests and instrumentation can wrap it.
  fetch: (request) => globalThis.fetch(request),
})

export type AppMeta = components['schemas']['AppMeta']

export async function fetchMeta(): Promise<AppMeta> {
  const result = await api.GET('/api/v1/meta')
  if (result.data === undefined) {
    throw new Error(`GET /api/v1/meta failed with HTTP ${result.response.status}`)
  }
  return result.data
}

/** Actuator health is operational, not part of the product API contract. */
export async function fetchHealth(): Promise<string> {
  const response = await fetch('/actuator/health', { credentials: 'same-origin' })
  if (!response.ok) {
    throw new Error(`GET /actuator/health failed with HTTP ${response.status}`)
  }
  const body = (await response.json()) as { status?: string }
  return body.status ?? 'UNKNOWN'
}
