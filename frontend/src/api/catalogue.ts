import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from './client'
import type { EventPage } from './events'

/** Filters of GET /api/events; `city` may list spellings separated by commas ("Sopot,Сопот"). */
export type CatalogueFilter = { q?: string; from?: string; to?: string; maxPrice?: number; city?: string; size?: number }

export function catalogueUrl(filter: CatalogueFilter): string {
  const params = new URLSearchParams({ sort: 'startsAt', page: '0', size: String(filter.size ?? 20) })
  if (filter.q) params.set('q', filter.q)
  if (filter.from) params.set('from', filter.from)
  if (filter.to) params.set('to', filter.to)
  if (filter.maxPrice !== undefined) params.set('maxPrice', String(filter.maxPrice))
  if (filter.city) params.set('city', filter.city)
  return `/api/events?${params}`
}

/** The public catalogue narrowed by the filter; the previous result stays on screen while the next one loads. */
export function useCatalogue(filter: CatalogueFilter, enabled = true) {
  return useQuery({
    queryKey: ['events', 'catalogue', filter],
    queryFn: () => api<EventPage>(catalogueUrl(filter)),
    placeholderData: keepPreviousData,
    enabled,
  })
}
