import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Route, Routes } from 'react-router'
import { renderWithProviders, stubFetch } from '../test/render'
import { BrowsePage } from './BrowsePage'

describe('BrowsePage', () => {
  it('lists products with their allergens in words', async () => {
    stubFetch({
      '/api/v1/products': {
        status: 200,
        body: {
          items: [
            {
              id: 'a',
              sku: 'CB-1',
              name: 'Cashew Butter',
              priceCents: 899,
              currency: 'USD',
              active: true,
              allergenVersion: 1,
              allergenDataAvailable: true,
              allergens: [{ allergenId: 'TREE_NUT.CASHEW', displayName: 'Cashew', matchType: 'CONTAINS' }],
            },
            {
              id: 'b',
              sku: 'MG-1',
              name: 'Mystery Granola',
              priceCents: 499,
              currency: 'USD',
              active: true,
              allergenDataAvailable: false,
              allergens: [],
            },
          ],
          page: 0,
          size: 20,
          total: 2,
        },
      },
    })
    renderWithProviders(
      <Routes>
        <Route path="/" element={<BrowsePage />} />
      </Routes>,
    )
    expect(await screen.findByRole('link', { name: 'Cashew Butter' })).toBeInTheDocument()
    expect(screen.getByText('$8.99')).toBeInTheDocument()
    expect(screen.getByText('Contains:').parentElement).toHaveTextContent('Cashew')
    expect(screen.getByText('Unable to verify allergens.')).toBeInTheDocument()
  })
})
