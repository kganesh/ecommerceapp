// M5 load test (spec: Local testing > M5; NFR-1 to NFR-3): 10 requests/second for the
// configured duration against the prod-like stack, with the seed data loaded.
// Mix: visitors browsing, plus a signed-in parent whose allergen check runs on every request.
//
//   make up-prod && make seed && make load-test
//
// The p95 threshold is the proposed NFR-3 target, still awaiting sign-off.
import http from 'k6/http'
import { check, fail } from 'k6'

const BASE = __ENV.BASE_URL || 'http://localhost:8081'
const P95_MS = Number(__ENV.P95_MS || 500)
const STIR_FRY = '00000000-0000-4000-8000-000000000003' // contains peanut: parent1's Child 2 avoids it

export const options = {
  scenarios: {
    shop: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 10),
      timeUnit: '1s',
      duration: __ENV.DURATION || '2m',
      preAllocatedVUs: 10,
      maxVUs: 30,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{flow:browse}': [`p(95)<${P95_MS}`],
    'http_req_duration{flow:allergen-check}': [`p(95)<${P95_MS}`],
    checks: ['rate>0.99'],
  },
}

/** Signs a VU in as parent1 through the identity provider's login form (Keycloak locally). */
function signIn() {
  const login = http.get(`${BASE}/oauth2/authorization/idp`, { tags: { flow: 'login' } })
  const match = login.body && login.body.match(/<form[^>]*id="kc-form-login"[^>]*action="([^"]+)"/)
  if (!match) fail(`login form not found (HTTP ${login.status})`)
  const action = match[1].replace(/&amp;/g, '&')
  const done = http.post(action, { username: 'parent1', password: 'parent1', credentialId: '' }, { tags: { flow: 'login' } })
  if (done.status !== 200) fail(`sign-in failed (HTTP ${done.status})`)
  const me = http.get(`${BASE}/api/v1/me`, { tags: { flow: 'login' } })
  if (!me.json('authenticated')) fail('not signed in after login')
}

function csrfHeader() {
  const cookies = http.cookieJar().cookiesForURL(BASE)
  const token = cookies['XSRF-TOKEN'] && cookies['XSRF-TOKEN'][0]
  return token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {}
}

let signedIn = false

export default function () {
  const roll = Math.random()
  if (roll < 0.6) {
    // Visitor: browse and open a product.
    const page = http.get(`${BASE}/api/v1/products?page=0&size=20`, { tags: { flow: 'browse' } })
    check(page, { 'browse 200': (r) => r.status === 200 })
    const product = http.get(`${BASE}/api/v1/products/${STIR_FRY}`, { tags: { flow: 'browse' } })
    check(product, { 'product 200': (r) => r.status === 200 })
    return
  }
  if (!signedIn) {
    signIn()
    signedIn = true
  }
  if (roll < 0.85) {
    // Browse with "hide unsafe": the allergen check runs against both children.
    const res = http.get(`${BASE}/api/v1/products?page=0&size=20&hideUnsafe=true`, { tags: { flow: 'allergen-check' } })
    check(res, { 'safe browse 200': (r) => r.status === 200 })
  } else {
    // Add a product Child 2 must avoid: the API warns (409) and adds nothing.
    const res = http.post(`${BASE}/api/v1/carts/me/items`, JSON.stringify({ productId: STIR_FRY, qty: 1 }), {
      headers: { 'Content-Type': 'application/json', ...csrfHeader() },
      tags: { flow: 'allergen-check' },
      responseCallback: http.expectedStatuses(409),
    })
    check(res, { 'add warns 409': (r) => r.status === 409 && r.json('code') === 'ALLERGEN_WARNING' })
  }
}
