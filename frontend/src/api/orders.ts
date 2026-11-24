import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { cartKeys } from './cart'
import { api, ApiError, unwrap, type Schemas } from './client'

export type Order = Schemas['Order']
export type OrderLine = Schemas['OrderLine']
export type CheckoutWarning = Schemas['CheckoutWarning']

export const orderKeys = { all: ['orders'] as const, one: (id: string) => ['orders', id] as const }

export function useOrders() {
  return useQuery({ queryKey: orderKeys.all, queryFn: async () => unwrap(await api.GET('/api/v1/orders')) })
}

export function useOrder(id: string) {
  return useQuery({
    queryKey: orderKeys.one(id),
    queryFn: async () => unwrap(await api.GET('/api/v1/orders/{id}', { params: { path: { id } } })),
  })
}

export type CheckoutOutcome =
  | { kind: 'placed'; order: Order }
  | { kind: 'warning'; warning: CheckoutWarning }

/**
 * FR-8. Every attempt of one checkout reuses the same Idempotency-Key, so a retry or a
 * double-click can never place two orders. A 409 with CHECKOUT_ALLERGEN_WARNING is the
 * allergen confirmation; any other non-2xx throws an ApiError (402 declined, 409
 * product unavailable, 503 check unavailable).
 */
export async function checkout(idempotencyKey: string, paymentToken: string, ackToken?: string): Promise<CheckoutOutcome> {
  const result = await api.POST('/api/v1/carts/me/checkout', {
    params: { header: { 'Idempotency-Key': idempotencyKey } },
    body: { paymentToken, ackToken },
  })
  const error = result.error as { code?: string } | undefined
  if (result.response.status === 409 && error?.code === 'CHECKOUT_ALLERGEN_WARNING') {
    return { kind: 'warning', warning: result.error as CheckoutWarning }
  }
  return { kind: 'placed', order: unwrap(result) }
}

export function useCheckout() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: { idempotencyKey: string; paymentToken: string; ackToken?: string }) =>
      checkout(input.idempotencyKey, input.paymentToken, input.ackToken),
    onSuccess: (outcome) => {
      if (outcome.kind === 'placed') {
        void client.invalidateQueries({ queryKey: cartKeys.cart })
        void client.invalidateQueries({ queryKey: orderKeys.all })
      }
    },
  })
}

export function errorCode(error: unknown): string | undefined {
  return error instanceof ApiError ? error.code : undefined
}
