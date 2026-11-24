import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { renderWithProviders, stubFetch } from '../test/render'
import { AddToCart } from './AddToCart'
import { SafetyNotice } from './SafetyNotice'

const shopper = { authenticated: true, username: 'parent1', roles: ['SHOPPER'], loginUrl: '/oauth2/authorization/idp' }

const warning = {
  code: 'ALLERGEN_WARNING',
  productId: 'p1',
  productName: 'Nut Mix',
  allergenVersion: 1,
  unverified: false,
  matches: [
    { profileId: 'c1', profileLabel: 'Child 1', allergenId: 'TREE_NUT.CASHEW', allergenName: 'Cashew', matchType: 'CONTAINS' },
    { profileId: 'c2', profileLabel: 'Child 2', allergenId: 'PEANUT', allergenName: 'Peanut', matchType: 'MAY_CONTAIN' },
  ],
  affectedProfiles: [
    { id: 'c1', label: 'Child 1' },
    { id: 'c2', label: 'Child 2' },
  ],
  ackToken: 'token-1',
  ackExpiresAt: '2026-11-24T20:00:00Z',
}

const emptyCart = { lines: [], totalCents: 0, currency: 'USD', needsAcknowledgment: false }

async function bodies(fetchMock: ReturnType<typeof stubFetch>) {
  const posts = fetchMock.mock.calls
    .map(([input]) => input as Request)
    .filter((r) => r.method === 'POST' && r.url.endsWith('/api/v1/carts/me/items'))
  return Promise.all(posts.map((r) => r.clone().json()))
}

describe('AddToCart (FR-7, FR-10)', () => {
  it('shows every child and match in words, and adds nothing until the parent confirms', async () => {
    const fetchMock = stubFetch({
      '/api/v1/me': { status: 200, body: shopper },
      'POST /api/v1/carts/me/items': { status: 409, body: warning },
    })
    renderWithProviders(<AddToCart productId="p1" />)
    await userEvent.click(await screen.findByRole('button', { name: 'Add to cart' }))

    const panel = await screen.findByRole('alertdialog', { name: /Allergen warning: Nut Mix/ })
    expect(panel).toHaveTextContent('Child 1: contains Cashew')
    expect(panel).toHaveTextContent('Child 2: may contain Peanut')
    expect(panel).toHaveTextContent('It has not been added to your cart.')

    await userEvent.click(screen.getByRole('button', { name: "Don't add" }))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(await bodies(fetchMock)).toEqual([{ productId: 'p1', qty: 1 }])
  })

  it('confirming resends with the ack token', async () => {
    let calls = 0
    const fetchMock = stubFetch({ '/api/v1/me': { status: 200, body: shopper } })
    const original = fetchMock.getMockImplementation()!
    fetchMock.mockImplementation(async (input, init) => {
      const request = input as Request
      if (request.method === 'POST' && request.url.endsWith('/api/v1/carts/me/items')) {
        calls += 1
        const body = calls === 1 ? warning : emptyCart
        return new Response(JSON.stringify(body), {
          status: calls === 1 ? 409 : 201,
          headers: { 'Content-Type': 'application/json' },
        })
      }
      return original(input, init)
    })
    renderWithProviders(<AddToCart productId="p1" />)
    await userEvent.click(await screen.findByRole('button', { name: 'Add to cart' }))
    await userEvent.click(await screen.findByRole('button', { name: 'I understand, add anyway' }))

    expect(await screen.findByText(/Added\./)).toBeInTheDocument()
    expect(await bodies(fetchMock)).toEqual([
      { productId: 'p1', qty: 1 },
      { productId: 'p1', qty: 1, ackToken: 'token-1' },
    ])
  })

  it('says nothing was added when the check is unavailable (FR-11)', async () => {
    stubFetch({
      '/api/v1/me': { status: 200, body: shopper },
      'POST /api/v1/carts/me/items': {
        status: 503,
        body: { status: 503, code: 'ALLERGEN_CHECK_UNAVAILABLE', detail: 'Unable to verify allergens right now.' },
      },
    })
    renderWithProviders(<AddToCart productId="p1" />)
    await userEvent.click(await screen.findByRole('button', { name: 'Add to cart' }))
    expect(await screen.findByRole('alert')).toHaveTextContent("We couldn't check allergens right now, so nothing was added.")
  })

  it('explains an unverified product instead of listing matches', async () => {
    stubFetch({
      '/api/v1/me': { status: 200, body: shopper },
      'POST /api/v1/carts/me/items': {
        status: 409,
        body: { ...warning, unverified: true, allergenVersion: undefined, matches: [] },
      },
    })
    renderWithProviders(<AddToCart productId="p1" />)
    await userEvent.click(await screen.findByRole('button', { name: 'Add to cart' }))
    const panel = await screen.findByRole('alertdialog', { name: /Unable to verify allergens: Nut Mix/ })
    expect(panel).toHaveTextContent("we can't check it for Child 1, Child 2")
  })
})

describe('SafetyNotice (FR-5)', () => {
  it('never calls a product safe', () => {
    renderWithProviders(<SafetyNotice safety={{ status: 'CLEAR', matches: [], affectedProfiles: [] }} />)
    expect(screen.getByText("No match with your children's avoid lists.")).toBeInTheDocument()
    expect(screen.queryByText(/safe/i)).not.toBeInTheDocument()
  })

  it('treats an unavailable check as unverified', () => {
    renderWithProviders(<SafetyNotice safety={{ status: 'UNAVAILABLE', matches: [], affectedProfiles: [] }} />)
    expect(screen.getByRole('note')).toHaveTextContent("Couldn't check allergens right now. Treat as unverified.")
  })
})
