import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type { components } from './schema'

export type EventItem = components['schemas']['EventResponse']
type Page = components['schemas']['PagedModelEventResponse']

/** The public catalogue, nearest first. */
export function useUpcomingEvents(size = 20) {
  return useQuery({
    queryKey: ['events', 'upcoming', size],
    queryFn: () => api<Page>(`/api/events?page=0&size=${size}&sort=startsAt`),
  })
}

/** ♡ on a tile: add or remove; every cached event list is refreshed afterwards. */
export function useToggleFavorite() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, favorite }: { id: number; favorite: boolean }) =>
      api<void>(`/api/favorites/${id}`, { method: favorite ? 'POST' : 'DELETE' }),
    onSuccess: () => client.invalidateQueries({ queryKey: ['events'] }),
  })
}
