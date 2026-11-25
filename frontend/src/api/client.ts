import type { components } from './schema'

/** Error body of the API (RFC 9457); `errors` lists fields for 400 (validation) and 422 (business rules). */
export type ProblemDetail = components['schemas']['ProblemDetail']

/** An error answer of the API (application/problem+json, RFC 9457). */
export class ApiError extends Error {
  readonly status: number
  readonly problem?: ProblemDetail

  constructor(status: number, problem?: ProblemDetail) {
    super(problem?.detail ?? problem?.title ?? `HTTP ${status}`)
    this.status = status
    this.problem = problem
  }
}

/**
 * Calls the API through the same origin (the dev server proxies /api to the backend). The session lives in HttpOnly
 * cookies that the browser sends by itself; the page never sees the token. A Bearer token is only for scripts.
 */
export async function api<T>(path: string, init: RequestInit = {}, token?: string): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json, application/problem+json')
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(path, { credentials: 'same-origin', ...init, headers })
  if (!response.ok) {
    const problem = response.headers.get('Content-Type')?.includes('json')
      ? ((await response.json()) as ProblemDetail)
      : undefined
    throw new ApiError(response.status, problem)
  }
  return (response.status === 204 ? undefined : await response.json()) as T
}
