import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { renderWithProviders, stubFetch } from '../test/render'
import { CheckoutPage } from './CheckoutPage'

const shopper = { authenticated: true, username: 'parent1', roles: ['SHOPPER'], loginUrl: '/oauth2/authorization/idp' }
const product = {
  id: 'p1', sku: 'S', name: 'Granola', priceCents: 300, currency: 'USD', active: true,
  allergenVersion: 2, allergenDataAvailable: true, allergens: [],
}
const cart = {
  lines: [{
    product, qty: 2, lineTotalCents: 600, status: 'NEEDS_ACKNOWLEDGMENT', unverified: false, available: true,
    matches: [{ profileId: 'c1', profileLabel: 'Child 1', allergenId: 'TREE_NUT.ALMOND', allergenName: 'Almond', matchType: 'CONTAINS' }],
    affectedProfiles: [{ id: 'c1', label: 'Child 1' }],
  }],
  totalCents: 600, currency: 'USD', needsAcknowledgment: true,
}
const warning = {
  code: 'CHECKOUT_ALLERGEN_WARNING',
  lines: [{ productId: 'p1', productName: 'Granola', allergenVersion: 2, unverified: false, matches: cart.lines[0]!.matches, affectedProfiles: cart.lines[0]!.affectedProfiles }],
  ackToken: 'chk-token',
  ackExpiresAt: '2026-11-24T21:00:00Z',
}
const order = {
  id: 'o1', status: 'PLACED', placedAt: '2026-11-24T20:00:00Z', totalCents: 600, currency: 'USD',
  paymentReference: 'test_ch_1', lines: [],
}

function render() {
  return renderWithProviders(
    <Routes>
      <Route path="/checkout" element={<CheckoutPage />} />
      <Route path="/orders/:id" element={<p>Order page</p>} />
    </Routes>,
    { route: '/checkout' },
  )
}

function checkoutCalls(fetchMock: ReturnType<typeof stubFetch>) {
  return fetchMock.mock.calls
    .map(([input]) => input as Request)
    .filter((r) => r.method === 'POST' && r.url.endsWith('/api/v1/carts/me/checkout'))
}

describe('CheckoutPage (FR-8)', () => {
  it('asks for confirmation, then resends with the token and the same Idempotency-Key', async () => {
    const fetchMock = stubFetch({
      '/api/v1/me': { status: 200, body: shopper },
      '/api/v1/carts/me': { status: 200, body: cart },
    })
    const original = fetchMock.getMockImplementation()!
    let attempt = 0
    fetchMock.mockImplementation(async (input, init) => {
      const request = input as Request
      if (request.method === 'POST' && request.url.endsWith('/checkout')) {
        attempt += 1
        return new Response(JSON.stringify(attempt === 1 ? warning : order), {
          status: attempt === 1 ? 409 : 201,
          headers: { 'Content-Type': 'application/json' },
        })
      }
      return original(input, init)
    })

    render()
    await userEvent.click(await screen.findByRole('button', { name: 'Place order' }))
    const panel = await screen.findByRole('alertdialog', { name: /Please confirm before we place your order/ })
    expect(panel).toHaveTextContent('Child 1: contains Almond')
    expect(panel).toHaveTextContent('Nothing has been charged.')

    await userEvent.click(screen.getByRole('button', { name: 'I understand, place the order' }))
    expect(await screen.findByText('Order page')).toBeInTheDocument()

    const calls = checkoutCalls(fetchMock)
    expect(calls).toHaveLength(2)
    const [first, second] = calls
    expect(first!.headers.get('Idempotency-Key')).toMatch(/^web-/)
    expect(second!.headers.get('Idempotency-Key')).toBe(first!.headers.get('Idempotency-Key'))
    expect(await first!.clone().json()).toEqual({ paymentToken: 'tok_test_approve' })
    expect(await second!.clone().json()).toEqual({ paymentToken: 'tok_test_approve', ackToken: 'chk-token' })
  })

  it('says a declined payment placed no order', async () => {
    stubFetch({
      '/api/v1/me': { status: 200, body: shopper },
      '/api/v1/carts/me': { status: 200, body: { ...cart, needsAcknowledgment: false } },
      'POST /api/v1/carts/me/checkout': {
        status: 402,
        body: { status: 402, code: 'PAYMENT_DECLINED', detail: 'Payment was declined (card_declined).' },
      },
    })
    render()
    await userEvent.click(await screen.findByLabelText('Test card (declines)'))
    await userEvent.click(screen.getByRole('button', { name: 'Place order' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Your payment was declined. No order was placed')
  })

  it('uses a fresh key per checkout page visit', () => {
    const uuid = vi.spyOn(crypto, 'randomUUID')
    stubFetch({ '/api/v1/me': { status: 200, body: shopper }, '/api/v1/carts/me': { status: 200, body: cart } })
    render()
    expect(uuid).toHaveBeenCalledTimes(1)
  })
})
