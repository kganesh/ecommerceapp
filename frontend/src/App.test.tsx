import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import App from './App'

function renderApp() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <App />
    </QueryClientProvider>,
  )
}

function stubFetch(routes: Record<string, { status: number; body: unknown }>) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      const path = new URL(url, 'http://localhost').pathname
      const route = routes[path]
      if (!route) return new Response('not found', { status: 404 })
      return new Response(JSON.stringify(route.body), {
        status: route.status,
        headers: { 'Content-Type': 'application/json' },
      })
    }),
  )
}

describe('App shell', () => {
  it('shows the backend as connected when meta and health respond', async () => {
    stubFetch({
      '/api/v1/meta': { status: 200, body: { name: 'ecommerceapp', version: '0.1.0-SNAPSHOT' } },
      '/actuator/health': { status: 200, body: { status: 'UP' } },
    })
    renderApp()
    expect(await screen.findByText(/Backend connected: ecommerceapp 0.1.0-SNAPSHOT/)).toBeInTheDocument()
  })

  it('shows a text warning, not colour alone, when the backend is down', async () => {
    stubFetch({
      '/api/v1/meta': { status: 503, body: {} },
      '/actuator/health': { status: 503, body: {} },
    })
    renderApp()
    expect(await screen.findByText(/Backend unavailable/)).toBeInTheDocument()
  })
})
