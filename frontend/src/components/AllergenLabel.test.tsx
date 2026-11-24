import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Product } from '../api/client'
import { formatPrice } from '../lib/format'
import { AllergenLabel } from './AllergenLabel'

const base: Product = {
  id: '00000000-0000-0000-0000-000000000001',
  sku: 'SKU-1',
  name: 'Test product',
  priceCents: 499,
  currency: 'USD',
  active: true,
  allergenVersion: 1,
  allergenDataAvailable: true,
  allergens: [],
}

describe('AllergenLabel', () => {
  it('never presents a product without allergen data as safe (FR-11)', () => {
    render(<AllergenLabel product={{ ...base, allergenVersion: undefined, allergenDataAvailable: false }} />)
    expect(screen.getByText('Unable to verify allergens.')).toBeInTheDocument()
    expect(screen.queryByText(/No listed allergens/)).not.toBeInTheDocument()
  })

  it('says so in words when the label lists no allergens', () => {
    render(<AllergenLabel product={base} />)
    expect(screen.getByText('No listed allergens.')).toBeInTheDocument()
  })

  it('separates contains from may-contain, in text', () => {
    render(
      <AllergenLabel
        product={{
          ...base,
          allergens: [
            { allergenId: 'TREE_NUT.CASHEW', displayName: 'Cashew', matchType: 'CONTAINS' },
            { allergenId: 'PEANUT', displayName: 'Peanut', matchType: 'MAY_CONTAIN' },
          ],
        }}
      />,
    )
    expect(screen.getByText('Contains:').parentElement).toHaveTextContent('Contains: Cashew')
    expect(screen.getByText('May contain:').parentElement).toHaveTextContent('May contain: Peanut')
  })

  it('formats prices from cents', () => {
    expect(formatPrice(499, 'USD')).toBe('$4.99')
  })
})
