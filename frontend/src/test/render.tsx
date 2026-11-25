import type { ReactElement } from 'react'
import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { vi } from 'vitest'

/** Shows where the app navigated to. */
export function Location() {
  const location = useLocation()
  return <div data-testid="location">{location.pathname + location.search}</div>
}

/** Renders a screen with a fresh query cache at the given route; other routes show their path. */
export function renderAt(path: string, element: ReactElement, entry: string | { pathname: string; state?: unknown } = path) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path={path} element={element} />
          <Route path="*" element={<Location />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

type Reply = { status?: number; body?: unknown; headers?: Record<string, string> }

/** Replaces fetch: each handler answers "METHOD /path" (query string ignored); everything else is 404. */
export function mockApi(handlers: Record<string, Reply | ((init?: RequestInit) => Reply)>) {
  const calls: { method: string; path: string; body?: unknown }[] = []
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), 'http://localhost')
    const method = init?.method ?? 'GET'
    calls.push({ method, path: url.pathname + url.search, body: init?.body ? JSON.parse(String(init.body)) : undefined })
    const handler = handlers[`${method} ${url.pathname}`]
    const reply = typeof handler === 'function' ? handler(init) : (handler ?? { status: 404 })
    const status = reply.status ?? 200
    const json = reply.body !== undefined
    return new Response(json ? JSON.stringify(reply.body) : null, {
      status,
      headers: { ...(json ? { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' } : {}), ...reply.headers },
    })
  })
  vi.stubGlobal('fetch', fetchMock)
  return calls
}
