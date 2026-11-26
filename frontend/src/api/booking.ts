import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, json } from './client'
import type { EventItem } from './events'
import type { components } from './schema'

export type Booking = components['schemas']['BookingResponse']
export type WaitlistChannel = 'EMAIL' | 'PUSH'
export type BookingSettings = { maxSeatsPerBooking: number; holdDurationSeconds: number; cancellationNoticeHours: number }

/** The rules the form follows: they come from the server configuration (GET /api/settings/booking). */
export function useBookingSettings() {
  return useQuery({
    queryKey: ['settings', 'booking'],
    queryFn: () => api<BookingSettings>('/api/settings/booking'),
    staleTime: 60 * 60_000,
  })
}

/** One public event; the key lives under "events", so the optimistic heart and seat updates reach it too. */
export function useEvent(id: number) {
  return useQuery({ queryKey: ['events', 'one', id], queryFn: () => api<EventItem>(`/api/events/${id}`) })
}

/** HOLD seats. The Idempotency-Key makes a repeated click (or a retried request) return the same booking. */
export function useHold() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ eventId, seats, key }: { eventId: number; seats: number; key: string }) =>
      api<Booking>('/api/bookings', { method: 'POST', headers: { 'Idempotency-Key': key }, ...json({ eventId, seats }) }),
    onSuccess: () => client.invalidateQueries({ queryKey: ['events'] }),
  })
}

/** HOLD → CONFIRMED; this is where a payment would happen (the payment gateway is a stub for now). */
export function useConfirm() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (bookingId: number) => api<Booking>(`/api/bookings/${bookingId}/confirm`, { method: 'POST' }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['events'] })
      void client.invalidateQueries({ queryKey: ['bookings'] })
    },
  })
}

export function useJoinWaitlist() {
  return useMutation({
    mutationFn: ({ eventId, channel }: { eventId: number; channel: WaitlistChannel }) =>
      api<unknown>(`/api/events/${eventId}/waitlist`, { method: 'POST', ...json({ channel }) }),
  })
}
