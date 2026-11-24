import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError, unwrap, type Schemas } from './client'

export type Cart = Schemas['Cart']
export type CartLine = Schemas['CartLine']
export type AllergenWarning = Schemas['AllergenWarning']
export type SafetyMatch = Schemas['SafetyMatch']
export type ProductSafety = Schemas['ProductSafety']

export const cartKeys = { cart: ['cart'] as const }

export function useCart(enabled = true) {
  return useQuery({
    queryKey: cartKeys.cart,
    queryFn: async () => unwrap(await api.GET('/api/v1/carts/me')),
    enabled,
    retry: false,
  })
}

export type AddOutcome = { kind: 'added'; cart: Cart } | { kind: 'warning'; warning: AllergenWarning }

/**
 * FR-7. A 409 is not an error: it is the allergen warning, and nothing was added.
 * A 503 (ALLERGEN_CHECK_UNAVAILABLE) throws, and nothing was added either.
 */
export async function addToCart(productId: string, qty: number, ackToken?: string): Promise<AddOutcome> {
  const result = await api.POST('/api/v1/carts/me/items', { body: { productId, qty, ackToken } })
  if (result.response.status === 409 && result.error) {
    return { kind: 'warning', warning: result.error as AllergenWarning }
  }
  return { kind: 'added', cart: unwrap(result) }
}

export function useAddToCart() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: { productId: string; qty?: number; ackToken?: string }) =>
      addToCart(input.productId, input.qty ?? 1, input.ackToken),
    onSuccess: (outcome) => {
      if (outcome.kind === 'added') client.setQueryData(cartKeys.cart, outcome.cart)
    },
  })
}

export function useUpdateCartLine() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: async ({ productId, qty }: { productId: string; qty: number }) =>
      unwrap(await api.PATCH('/api/v1/carts/me/items/{productId}', { params: { path: { productId } }, body: { qty } })),
    onSuccess: (cart) => client.setQueryData(cartKeys.cart, cart),
  })
}

export function useRemoveCartLine() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: async (productId: string) =>
      unwrap(await api.DELETE('/api/v1/carts/me/items/{productId}', { params: { path: { productId } } })),
    onSuccess: (cart) => client.setQueryData(cartKeys.cart, cart),
  })
}

export function isCheckUnavailable(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'ALLERGEN_CHECK_UNAVAILABLE'
}
