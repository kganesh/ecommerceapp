import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'
import { renderWithProviders, stubFetch } from './test/render'

const emptyPage = { status: 200, body: { items: [], page: 0, size: 20, total: 0 } }

describe('App shell', () => {
  it('shows the backend as connected when meta and health respond', async () => {
    stubFetch({
      '/api/v1/meta': { status: 200, body: { name: 'ecommerceapp', version: '0.1.0-SNAPSHOT' } },
      '/actuator/health': { status: 200, body: { status: 'UP' } },
      '/api/v1/products': emptyPage,
    })
    renderWithProviders(<App />)
    expect(await screen.findByText(/Backend connected: ecommerceapp 0.1.0-SNAPSHOT/)).toBeInTheDocument()
  })

  it('shows a text warning, not colour alone, when the backend is down', async () => {
    stubFetch({
      '/api/v1/meta': { status: 503, body: {} },
      '/actuator/health': { status: 503, body: {} },
      '/api/v1/products': { status: 503, body: {} },
    })
    renderWithProviders(<App />)
    expect(await screen.findByText(/Backend unavailable/)).toBeInTheDocument()
  })

  it('always shows the read-the-label disclaimer (FR-13)', async () => {
    stubFetch({ '/api/v1/products': emptyPage })
    renderWithProviders(<App />)
    expect(await screen.findByText(/Always read the label on the package/)).toBeInTheDocument()
  })
})
