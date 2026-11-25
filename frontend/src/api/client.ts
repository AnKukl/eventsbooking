import type { components } from './schema'

/** Error body of the API (RFC 9457); `errors` lists fields for 400 (validation) and 422 (business rules). */
export type ProblemDetail = components['schemas']['ProblemDetail']

/** An error answer of the API (application/problem+json, RFC 9457). */
export class ApiError extends Error {
  readonly status: number
  readonly problem?: ProblemDetail
  /** Seconds from Retry-After (429, 503), if the server sent it. */
  readonly retryAfter?: number

  constructor(status: number, problem?: ProblemDetail, retryAfter?: number) {
    super(problem?.detail ?? problem?.title ?? `HTTP ${status}`)
    this.status = status
    this.problem = problem
    this.retryAfter = retryAfter
  }
}

const AUTH_PATHS = /^\/api\/auth\//

/** One refresh at a time: parallel 401s wait for the same POST /api/auth/refresh. */
let refreshing: Promise<boolean> | null = null

function refreshSession(): Promise<boolean> {
  refreshing ??= fetch('/api/auth/refresh', { method: 'POST', credentials: 'same-origin' })
    .then((response) => response.ok)
    .catch(() => false)
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

async function send(path: string, init: RequestInit): Promise<Response> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json, application/problem+json')
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  return fetch(path, { credentials: 'same-origin', ...init, headers })
}

/**
 * Calls the API through the same origin (the dev server proxies /api to the backend). The session lives in HttpOnly
 * cookies that the browser sends by itself; the page never sees the token. An expired access token (401) is renewed
 * once with the refresh cookie and the request is repeated; if that fails too, the 401 reaches the caller.
 */
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response = await send(path, init)
  if (response.status === 401 && !AUTH_PATHS.test(path) && (await refreshSession())) {
    response = await send(path, init)
  }
  if (!response.ok) {
    const problem = response.headers.get('Content-Type')?.includes('json')
      ? ((await response.json()) as ProblemDetail)
      : undefined
    const retryAfter = Number(response.headers.get('Retry-After')) || undefined
    throw new ApiError(response.status, problem, retryAfter)
  }
  const text = response.status === 204 ? '' : await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/** JSON body helper. */
export function json(body: unknown): RequestInit {
  return { body: JSON.stringify(body) }
}
