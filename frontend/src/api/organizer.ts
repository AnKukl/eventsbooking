import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, json } from './client'
import type { components } from './schema'

export type OrganizerEvent = components['schemas']['OrganizerEventResponse']
export type OrganizerEventUpdate = components['schemas']['OrganizerEventUpdateRequest']
export type OrganizerBooking = components['schemas']['OrganizerBookingResponse']
export type Analytics = components['schemas']['AnalyticsResponse']
type Page<T> = { content?: T[]; page?: { totalElements?: number } }

export function useMyEvents(status?: 'DRAFT') {
  return useQuery({
    queryKey: ['organizer', 'events', status ?? 'all'],
    queryFn: () => api<Page<OrganizerEvent>>(`/api/organizer/events?page=0&size=50${status ? `&status=${status}` : ''}`),
  })
}

export function useAnalytics(range: string) {
  return useQuery({ queryKey: ['organizer', 'analytics', range], queryFn: () => api<Analytics>(`/api/organizer/analytics?range=${range}`) })
}

export function useOrganizerEvent(id: number) {
  return useQuery({ queryKey: ['organizer', 'event', id], queryFn: () => api<OrganizerEvent>(`/api/organizer/events/${id}`) })
}

export function useEventBookings(id: number) {
  return useQuery({
    queryKey: ['organizer', 'bookings', id],
    queryFn: () => api<Page<OrganizerBooking>>(`/api/organizer/events/${id}/bookings?page=0&size=100`),
  })
}

/** A new draft needs only a title, a start and a capacity; the rest is filled in the editor. */
export function useCreateDraft() {
  return useMutation({
    mutationFn: (draft: { title: string; startsAt: string; capacity: number }) =>
      api<OrganizerEvent>('/api/organizer/events', { method: 'POST', ...json(draft) }),
  })
}

/** PATCH with the version the form was loaded with; an older one is refused with 409 (somebody saved in between). */
export function useSaveEvent(id: number) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (update: OrganizerEventUpdate) => api<OrganizerEvent>(`/api/organizer/events/${id}`, { method: 'PATCH', ...json(update) }),
    onSuccess: (saved) => {
      client.setQueryData(['organizer', 'event', id], saved)
      void client.invalidateQueries({ queryKey: ['organizer', 'events'] })
    },
  })
}

export function usePublish(id: number) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => api<OrganizerEvent>(`/api/organizer/events/${id}/publish`, { method: 'PUT' }),
    onSuccess: (published) => {
      client.setQueryData(['organizer', 'event', id], published)
      void client.invalidateQueries({ queryKey: ['organizer', 'events'] })
      void client.invalidateQueries({ queryKey: ['events'] })
    },
  })
}

export function useUploadPhotos(id: number) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (files: File[]) => {
      const body = new FormData()
      files.forEach((file) => body.append('files', file))
      return api<{ url: string }[]>(`/api/organizer/events/${id}/media`, { method: 'POST', body })
    },
    onSuccess: () => client.invalidateQueries({ queryKey: ['organizer', 'event', id] }),
  })
}
