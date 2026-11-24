import { afterEach, describe, expect, it } from 'vitest'
import { asTree } from './catalog'
import { ApiError, csrfMiddleware, unwrap, type Allergen } from './client'

function runCsrf(method: string): Request {
  const request = new Request('http://localhost/api/v1/admin/products', { method })
  const onRequest = csrfMiddleware.onRequest!
  // The middleware only reads `request`; other fields are unused.
  return onRequest({ request } as Parameters<typeof onRequest>[0]) as Request
}

describe('csrfMiddleware', () => {
  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('echoes the XSRF-TOKEN cookie on state-changing requests', () => {
    document.cookie = 'XSRF-TOKEN=abc%20123; path=/'
    expect(runCsrf('POST').headers.get('X-XSRF-TOKEN')).toBe('abc 123')
    expect(runCsrf('PUT').headers.get('X-XSRF-TOKEN')).toBe('abc 123')
  })

  it('does not add the header to safe requests', () => {
    document.cookie = 'XSRF-TOKEN=abc; path=/'
    expect(runCsrf('GET').headers.get('X-XSRF-TOKEN')).toBeNull()
  })
})

describe('unwrap', () => {
  it('returns data', () => {
    expect(unwrap({ data: 42, response: new Response(null, { status: 200 }) })).toBe(42)
  })

  it('throws an ApiError carrying the problem code', () => {
    const call = () =>
      unwrap({
        error: { detail: 'Unknown allergen ids: [X]', code: 'UNKNOWN_ALLERGEN' },
        response: new Response(null, { status: 400 }),
      })
    expect(call).toThrow(ApiError)
    try {
      call()
    } catch (e) {
      expect((e as ApiError).status).toBe(400)
      expect((e as ApiError).code).toBe('UNKNOWN_ALLERGEN')
    }
  })
})

describe('asTree', () => {
  it('puts each root before its children', () => {
    const allergens: Allergen[] = [
      { id: 'PEANUT', displayName: 'Peanut', synonyms: [] },
      { id: 'TREE_NUT', displayName: 'Tree nuts', synonyms: [] },
      { id: 'TREE_NUT.CASHEW', parentId: 'TREE_NUT', displayName: 'Cashew', synonyms: [] },
    ]
    expect(asTree(allergens).map(({ node, depth }) => `${depth}:${node.id}`)).toEqual([
      '0:PEANUT',
      '0:TREE_NUT',
      '1:TREE_NUT.CASHEW',
    ])
  })
})
