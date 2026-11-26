import { useQuery } from '@tanstack/react-query'
import { api } from './client'
import type { components } from './schema'

export type EventItem = components['schemas']['EventResponse']
export type EventPage = components['schemas']['PagedModelEventResponse']

/** Midnight of the current local day: "from today" includes today's evening events that already started. */
export function startOfToday(now: Date = new Date()): Date {
  return new Date(now.getFullYear(), now.getMonth(), now.getDate())
}

/** The public catalogue from today on, nearest first (the home page tiles). */
export function useEventsFromToday(size = 20) {
  const from = startOfToday().toISOString()
  return useQuery({
    queryKey: ['events', 'from', from, size],
    queryFn: () => api<EventPage>(`/api/events?from=${encodeURIComponent(from)}&sort=startsAt&page=0&size=${size}`),
  })
}

/** Events of the coming seven days (the "В эту неделю" strip and its "ещё N" counter). */
export function useEventsThisWeek(size = 12) {
  const now = new Date()
  const from = startOfToday(now)
  const to = new Date(from.getTime() + 7 * 86_400_000)
  return useQuery({
    queryKey: ['events', 'week', from.toISOString(), size],
    queryFn: () =>
      api<EventPage>(
        `/api/events?from=${encodeURIComponent(from.toISOString())}&to=${encodeURIComponent(to.toISOString())}` +
          `&sort=startsAt&page=0&size=${size}`,
      ),
  })
}
