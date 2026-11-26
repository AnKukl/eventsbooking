import { type QueryClient, useQuery } from '@tanstack/react-query'
import { api } from './client'
import type { components } from './schema'

export type Preview = components['schemas']['EventPreviewResponse']

const key = (id: number) => ['preview', id] as const
const load = (id: number) => () => api<Preview>(`/api/events/${id}/preview`)

/** The server caches the preview for five minutes (Cache-Control); the query cache keeps it as long. */
const STALE = 5 * 60_000

export function usePreview(id: number, enabled: boolean) {
  return useQuery({ queryKey: key(id), queryFn: load(id), enabled, staleTime: STALE })
}

/** Starts loading as soon as the pointer is on the card, so the preview is ready when it opens two seconds later. */
export function prefetchPreview(client: QueryClient, id: number) {
  return client.prefetchQuery({ queryKey: key(id), queryFn: load(id), staleTime: STALE })
}
