import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, json } from './client'
import type { components } from './schema'

export type MyBooking = components['schemas']['MyBookingResponse']
export type Notification = components['schemas']['NotificationResponse']
export type Preference = components['schemas']['NotificationPreference']
export type ProfileUpdate = components['schemas']['ProfileUpdateRequest']
type Page<T> = { content?: T[]; page?: { totalElements?: number } }

export function useMyBookings() {
  return useQuery({ queryKey: ['bookings', 'my'], queryFn: () => api<Page<MyBooking>>('/api/bookings/my?page=0&size=50') })
}

export function useNotifications() {
  return useQuery({ queryKey: ['notifications', 'feed'], queryFn: () => api<Page<Notification>>('/api/me/notifications?page=0&size=50') })
}

export function useUnreadCount(enabled: boolean) {
  return useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => api<{ count: number }>('/api/me/notifications/unread-count'),
    enabled,
  })
}

/** Marks everything read (opening the feed, or the button); the counter in the menu follows. */
export function useMarkAllRead() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => api<void>('/api/me/notifications/read', { method: 'POST' }),
    onSuccess: () => client.setQueryData(['notifications', 'unread'], { count: 0 }),
  })
}

export function usePreferences() {
  return useQuery({
    queryKey: ['notifications', 'preferences'],
    queryFn: () => api<{ preferences?: Preference[] }>('/api/me/notifications/preferences'),
  })
}

export function useSavePreferences() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (preferences: Preference[]) =>
      api<{ preferences?: Preference[] }>('/api/me/notifications/preferences', { method: 'PUT', ...json({ preferences }) }),
    onSuccess: (data) => client.setQueryData(['notifications', 'preferences'], data),
  })
}

export function useSaveProfile() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (update: ProfileUpdate) => api<unknown>('/api/me', { method: 'PATCH', ...json(update) }),
    onSuccess: (data) => client.setQueryData(['me'], data),
  })
}

export function useChangeEmail() {
  return useMutation({
    mutationFn: (newEmail: string) => api<void>('/api/me/email-change', { method: 'POST', ...json({ newEmail }) }),
  })
}
