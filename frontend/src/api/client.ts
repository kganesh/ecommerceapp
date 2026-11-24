import createClient, { type Middleware } from 'openapi-fetch'
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

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

export function readCookie(name: string): string | undefined {
  if (typeof document === 'undefined') return undefined
  const prefix = `${name}=`
  const match = document.cookie.split('; ').find((part) => part.startsWith(prefix))
  return match === undefined ? undefined : decodeURIComponent(match.slice(prefix.length))
}

/** Echoes the XSRF-TOKEN cookie on state-changing requests (Spring Security cookie CSRF). */
export const csrfMiddleware: Middleware = {
  onRequest({ request }) {
    if (!SAFE_METHODS.has(request.method.toUpperCase())) {
      const token = readCookie('XSRF-TOKEN')
      if (token !== undefined) request.headers.set('X-XSRF-TOKEN', token)
    }
    return request
  },
}
api.use(csrfMiddleware)

export type Schemas = components['schemas']
export type Allergen = Schemas['Allergen']
export type Product = Schemas['Product']
export type ProductPage = Schemas['ProductPage']
export type ProductAllergen = Product['allergens'][number]
export type MatchType = ProductAllergen['matchType']
export type AllergenEntry = Schemas['AllergenEntry']
export type AuditEntry = Schemas['AuditEntry']
export type AppMeta = Schemas['AppMeta']

/** An RFC 9457 problem response from the backend. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string | undefined

  constructor(status: number, detail: string | undefined, code: string | undefined) {
    super(detail ?? `Request failed with HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

/** Turns an openapi-fetch result into data or a thrown {@link ApiError}. */
export function unwrap<T>(result: { data?: T; error?: unknown; response: Response }): T {
  if (result.data !== undefined) return result.data
  const problem = (result.error ?? {}) as { detail?: unknown; code?: unknown }
  throw new ApiError(
    result.response.status,
    typeof problem.detail === 'string' ? problem.detail : undefined,
    typeof problem.code === 'string' ? problem.code : undefined,
  )
}

export async function fetchMeta(): Promise<AppMeta> {
  return unwrap(await api.GET('/api/v1/meta'))
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
