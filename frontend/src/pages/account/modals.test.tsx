import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CancelBookingModal, LogoutModal, refundOf } from './modals'
import { mockApi, renderAt } from '../../test/render'

afterEach(() => vi.unstubAllGlobals())

const inDays = (days: number) => new Date(Date.now() + days * 86_400_000).toISOString()
const EVENT = { id: 5, title: 'Свет на мраморе', startsAt: inDays(5), price: 45, capacity: 20, bookedCount: 6, organizerId: 1 }
const CONFIRMED = { id: 77, eventId: 5, seats: 2, status: 'CONFIRMED' as const }

describe('refund rule (same as the server)', () => {
  it('a confirmed booking far enough ahead is refunded in full', () => {
    expect(refundOf(CONFIRMED, EVENT, 24)).toBe(90)
  })

  it('a late cancellation or an unpaid hold brings nothing back', () => {
    expect(refundOf(CONFIRMED, { ...EVENT, startsAt: new Date(Date.now() + 3_600_000).toISOString() }, 24)).toBe(0)
    expect(refundOf({ ...CONFIRMED, status: 'HOLD' }, EVENT, 24)).toBe(0)
  })
})

describe('CancelBookingModal (P-modals)', () => {
  it('shows the refund of 90 PLN and when it arrives, and sends the chosen reason', async () => {
    const calls = mockApi({ 'POST /api/bookings/77/cancel': { body: { ...CONFIRMED, status: 'REFUND_PENDING' } } })
    const onClose = vi.fn()
    renderAt('/', <CancelBookingModal booking={CONFIRMED} event={EVENT} noticeHours={24} onClose={onClose} />)

    const dialog = screen.getByRole('dialog', { name: 'Отменить бронь?' })
    expect(within(dialog).getByText('Возврат на карту').nextSibling).toHaveTextContent('90 PLN')
    expect(dialog).toHaveTextContent('Деньги вернутся за 3–5 рабочих дней')
    await userEvent.selectOptions(within(dialog).getByRole('combobox', { name: 'Причина (необязательно)' }), 'Нашёл другое событие')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Отменить' }))

    await waitFor(() => expect(calls).toContainEqual({ method: 'POST', path: '/api/bookings/77/cancel', body: { reason: 'FOUND_ALTERNATIVE' } }))
    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(screen.getByRole('status')).toHaveTextContent('Бронь отменена, вернём 90 PLN')
  })

  it('a late cancellation says that no money comes back', () => {
    mockApi({})
    const soon = { ...EVENT, startsAt: new Date(Date.now() + 3_600_000).toISOString() }
    renderAt('/', <CancelBookingModal booking={CONFIRMED} event={soon} noticeHours={24} onClose={() => {}} />)

    expect(screen.getByRole('dialog')).toHaveTextContent('До начала меньше 24 часов: деньги не возвращаются.')
    expect(screen.queryByText('Возврат на карту')).not.toBeInTheDocument()
  })

  it('Esc closes the window without cancelling', async () => {
    const calls = mockApi({})
    const onClose = vi.fn()
    renderAt('/', <CancelBookingModal booking={CONFIRMED} event={EVENT} noticeHours={24} onClose={onClose} />)

    await userEvent.keyboard('{Escape}')

    expect(onClose).toHaveBeenCalled()
    expect(calls).toEqual([])
  })
})

describe('LogoutModal (P-modals)', () => {
  it('"Выйти" logs out and goes home; "Остаться" just closes', async () => {
    const calls = mockApi({ 'POST /api/auth/logout': { status: 204 } })
    const onClose = vi.fn()
    renderAt('/account', <LogoutModal onClose={onClose} />)

    await userEvent.click(screen.getByRole('button', { name: 'Остаться' }))
    expect(onClose).toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Выйти' }))

    await waitFor(() => expect(calls).toContainEqual({ method: 'POST', path: '/api/auth/logout', body: undefined }))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/))
  })
})
