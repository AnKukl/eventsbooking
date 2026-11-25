import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from './client'
import { mockApi } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

describe('api client', () => {
  it('renews an expired session once and repeats the request', async () => {
    let first = true
    const calls = mockApi({
      'GET /api/me': () => (first ? ((first = false), { status: 401 }) : { body: { id: 1 } }),
      'POST /api/auth/refresh': { body: { expiresIn: 900 } },
    })

    await expect(api('/api/me')).resolves.toEqual({ id: 1 })
    expect(calls.map((call) => `${call.method} ${call.path}`)).toEqual(['GET /api/me', 'POST /api/auth/refresh', 'GET /api/me'])
  })

  it('gives up with the 401 when the refresh fails too', async () => {
    mockApi({ 'GET /api/me': { status: 401 }, 'POST /api/auth/refresh': { status: 401 } })

    await expect(api('/api/me')).rejects.toMatchObject({ status: 401 })
  })

  it('parallel 401s share one refresh', async () => {
    let refreshed = false
    const calls = mockApi({
      'GET /api/me': () => (refreshed ? { body: {} } : { status: 401 }),
      'GET /api/bookings/my': () => (refreshed ? { body: {} } : { status: 401 }),
      'POST /api/auth/refresh': () => ((refreshed = true), { body: { expiresIn: 900 } }),
    })

    await Promise.all([api('/api/me'), api('/api/bookings/my')])
    expect(calls.filter((call) => call.path === '/api/auth/refresh')).toHaveLength(1)
  })

  it('a login failure is not retried through refresh', async () => {
    const calls = mockApi({ 'POST /api/auth/login': { status: 401, body: { status: 401 } } })

    await expect(api('/api/auth/login', { method: 'POST', body: '{}' })).rejects.toBeInstanceOf(ApiError)
    expect(calls).toHaveLength(1)
  })
})
