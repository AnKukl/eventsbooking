import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BookingPage, mmss } from './BookingPage'
import { mockApi, renderAt } from '../../test/render'

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

const EVENT = {
  id: 5, title: 'Свет на мраморе', startsAt: new Date(Date.now() + 3 * 86_400_000).toISOString(), capacity: 20,
  bookedCount: 6, organizerId: 1, seatStatus: 'AVAILABLE', isFavorite: false, venue: 'Национальный музей', price: 45,
}
const SETTINGS = { maxSeatsPerBooking: 4, holdDurationSeconds: 600, cancellationNoticeHours: 24 }
const ME = { body: { id: 7, email: 'anna@example.com', role: 'USER' } }

function api(extra = {}, event = EVENT) {
  return mockApi({
    'GET /api/me': ME,
    'GET /api/events/5': { body: event },
    'GET /api/events/5/preview': { body: { id: 5, audienceFit: [], audienceNotFit: [], media: [] } },
    'GET /api/settings/booking': { body: SETTINGS },
    ...extra,
  })
}

const open = () => renderAt('/events/:id/book', <BookingPage />, '/events/5/book')
const plus = () => screen.getByRole('button', { name: 'Больше мест' })

describe('BookingPage (I-booking)', () => {
  it('the counter stops at the server limit for one booking', async () => {
    api()
    open()

    await screen.findByRole('heading', { name: 'Свет на мраморе' })
    for (let i = 0; i < 6; i++) if (!plus().hasAttribute('disabled')) await userEvent.click(plus())

    expect(within(screen.getByRole('group', { name: 'Количество билетов' })).getByText('4')).toBeInTheDocument()
    expect(plus()).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Перейти к оплате · 180 PLN' })).toBeInTheDocument()
  })

  it('and at the seats that are left when fewer remain', async () => {
    api({}, { ...EVENT, bookedCount: 18 })
    open()

    await screen.findByRole('heading', { name: 'Свет на мраморе' })
    await userEvent.click(plus())

    expect(plus()).toBeDisabled()
    expect(screen.getByText('Не больше 2 места в одной брони')).toBeInTheDocument()
  })

  it('holds the seats with one Idempotency-Key and shows the hold timer, then confirms', async () => {
    const expires = new Date(Date.now() + 10 * 60_000).toISOString()
    const calls = api({
      'POST /api/bookings': { status: 201, body: { id: 77, eventId: 5, seats: 2, status: 'HOLD', holdExpiresAt: expires } },
      'POST /api/bookings/77/confirm': { body: { id: 77, eventId: 5, seats: 2, status: 'CONFIRMED' } },
    })
    open()

    await screen.findByRole('heading', { name: 'Свет на мраморе' })
    await userEvent.click(plus())
    await userEvent.click(screen.getByRole('button', { name: 'Перейти к оплате · 90 PLN' }))

    const hold = calls.find((call) => call.method === 'POST' && call.path === '/api/bookings')!
    expect(hold.body).toEqual({ eventId: 5, seats: 2 })
    expect(hold.headers?.['Idempotency-Key']).toMatch(/^[0-9a-f-]{36}$/)
    expect(await screen.findByRole('timer', { name: 'Места удержаны' })).toHaveTextContent(/0?9:5\d|10:00/)

    await userEvent.click(screen.getByRole('button', { name: 'Подтвердить и оплатить · 90 PLN' }))
    expect(await screen.findByText('Места ваши')).toBeInTheDocument()
  })

  it('the timer runs out: "Время удержания истекло" and a fresh hold with a new key', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const expires = new Date(Date.now() + 3_000).toISOString()
    const calls = api({
      'POST /api/bookings': { status: 201, body: { id: 77, eventId: 5, seats: 1, status: 'HOLD', holdExpiresAt: expires } },
    })
    open()
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })

    await user.click(await screen.findByRole('button', { name: 'Перейти к оплате · 45 PLN' }))
    expect(await screen.findByRole('timer')).toBeInTheDocument()
    await act(async () => { vi.advanceTimersByTime(4_000) })

    expect(screen.getByRole('alert')).toHaveTextContent('Время удержания истекло')
    await user.click(screen.getByRole('button', { name: 'Удержать места заново' }))
    await user.click(screen.getByRole('button', { name: 'Перейти к оплате · 45 PLN' }))

    const keys = calls.filter((call) => call.path === '/api/bookings').map((call) => call.headers?.['Idempotency-Key'])
    expect(keys).toHaveLength(2)
    expect(keys[0]).not.toBe(keys[1])
  })

  it('409 "no seats" opens the waitlist window, which joins with the chosen channel', async () => {
    let full = false
    const calls = mockApi({
      'GET /api/me': ME,
      'GET /api/events/5': () => ({ body: full ? { ...EVENT, bookedCount: 20, seatStatus: 'SOLD_OUT' } : EVENT }),
      'GET /api/events/5/preview': { body: { id: 5, media: [] } },
      'GET /api/settings/booking': { body: SETTINGS },
      'POST /api/bookings': () => {
        full = true
        return { status: 409, body: { status: 409, title: 'Event is full', code: 'EVENT_IS_FULL' } }
      },
      'POST /api/events/5/waitlist': { status: 201, body: { position: 1 } },
    })
    open()

    await userEvent.click(await screen.findByRole('button', { name: 'Перейти к оплате · 45 PLN' }))
    const dialog = await screen.findByRole('dialog', { name: 'Сообщим, когда появятся места' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Push' }))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Сообщить' }))

    await waitFor(() => expect(calls).toContainEqual({ method: 'POST', path: '/api/events/5/waitlist', body: { channel: 'PUSH' } }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('a guest is sent to sign in first', async () => {
    mockApi({ 'GET /api/me': { status: 401 }, 'POST /api/auth/refresh': { status: 401 }, 'GET /api/events/5': { body: EVENT },
      'GET /api/settings/booking': { body: SETTINGS } })
    open()

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login?returnTo=%2Fevents%2F5%2Fbook'))
  })

  it('formats the countdown', () => {
    expect(mmss(582_000)).toBe('09:42')
    expect(mmss(0)).toBe('00:00')
  })
})
