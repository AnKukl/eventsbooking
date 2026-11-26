import { type APIRequestContext, expect, request } from '@playwright/test'

export const PASSWORD = 'e2e-Password-123'
const INVITE = process.env.E2E_INVITE_CODE ?? 'e2e-invite-code'

export function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`
}

/** A smallest valid PNG: the backend checks the signature of uploaded pictures. */
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+P+/HgAFhAJ/wlseKgAAAABJRU5ErkJggg==',
  'base64',
)

/**
 * A published event to test with, made through the API by a fresh organizer (registered with the invite code).
 * The Origin header is what a browser would send: the backend's CSRF check needs it with the session cookie.
 */
export async function publishedEvent(baseURL: string, title: string, options: { price?: number; hoursAhead?: number } = {}) {
  const api: APIRequestContext = await request.newContext({ baseURL, extraHTTPHeaders: { Origin: baseURL } })
  const email = uniqueEmail('e2e-org')
  expect((await api.post('/api/auth/register', { data: { email, password: PASSWORD, inviteCode: INVITE } })).status()).toBe(201)
  expect((await api.post('/api/auth/login', { data: { email, password: PASSWORD } })).ok()).toBeTruthy()
  const startsAt = new Date(Date.now() + (options.hoursAhead ?? 72) * 3_600_000).toISOString()
  const created = await api.post('/api/organizer/events', {
    data: { title, startsAt, capacity: 20, venue: 'Grand Cafe, Sopot', price: options.price ?? 45,
      shortDescription: 'Проверочное событие e2e', latitude: 54.4441, longitude: 18.5701 },
  })
  expect(created.status()).toBe(201)
  const { id } = await created.json()
  const photo = await api.post(`/api/organizer/events/${id}/media`, {
    multipart: { files: { name: 'cover.png', mimeType: 'image/png', buffer: PNG } },
  })
  expect(photo.status()).toBe(201)
  expect((await api.put(`/api/organizer/events/${id}/publish`)).ok()).toBeTruthy()
  await api.dispose()
  return id as number
}
