import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../../api/client'
import type { EventItem } from '../../api/events'
import { type Booking, type CancellationReason, useCancelBooking } from '../../api/booking'
import { useLogout } from '../../api/session'
import { Modal, modalStyles } from '../../components/modal/Modal'
import { useToast } from '../../components/toast/Toast'

/** "Выйти из аккаунта?" (design/pages/P-modals). Logging out clears every cached answer and goes home. */
export function LogoutModal({ onClose }: { onClose: () => void }) {
  const logout = useLogout()
  const navigate = useNavigate()
  return (
    <Modal title="Выйти из аккаунта?" narrow onClose={onClose}>
      <span className={modalStyles.text}>Брони и избранное сохранятся. Войдёте снова с этого же email.</span>
      <div className={modalStyles.actions}>
        <button type="button" className={modalStyles.secondary} onClick={onClose}>Остаться</button>
        <button
          type="button"
          className={modalStyles.danger}
          disabled={logout.isPending}
          onClick={() => logout.mutate(undefined, { onSettled: () => navigate('/', { replace: true }) })}
        >
          Выйти
        </button>
      </div>
    </Modal>
  )
}

const REASONS: { value: CancellationReason; label: string }[] = [
  { value: 'CHANGED_PLANS', label: 'Изменились планы' },
  { value: 'SCHEDULE_CONFLICT', label: 'Не подходит время' },
  { value: 'FOUND_ALTERNATIVE', label: 'Нашёл другое событие' },
  { value: 'TOO_EXPENSIVE', label: 'Дорого' },
  { value: 'OTHER', label: 'Другое' },
]

/**
 * Whether cancelling now brings money back: the same rule as the server (a confirmed booking at least
 * `noticeHours` before the start is refunded; a hold has not been paid, a late cancellation is not refunded).
 */
export function refundOf(booking: Booking, event: EventItem, noticeHours: number, now: Date = new Date()): number {
  if (booking.status !== 'CONFIRMED' || event.price === undefined || event.price === null) return 0
  const deadline = new Date(event.startsAt!).getTime() - noticeHours * 3_600_000
  return now.getTime() <= deadline ? Number(event.price) * (booking.seats ?? 1) : 0
}

function pln(amount: number): string {
  return `${amount.toLocaleString('ru-RU')} PLN`
}

/** "Отменить бронь?" (design/pages/P-modals): what is cancelled, what comes back and when, an optional reason. */
export function CancelBookingModal({ booking, event, noticeHours, onClose }: {
  booking: Booking
  event: EventItem
  noticeHours: number
  onClose: () => void
}) {
  const [reason, setReason] = useState<CancellationReason>('CHANGED_PLANS')
  const cancel = useCancelBooking()
  const toast = useToast()
  const seats = booking.seats ?? 1
  const paid = event.price === undefined || event.price === null ? null : Number(event.price) * seats
  const refund = refundOf(booking, event, noticeHours)

  function submit() {
    cancel.mutate(
      { bookingId: booking.id!, reason },
      {
        onSuccess: (result) => {
          toast.show({ message: result.status === 'REFUND_PENDING' ? `Бронь отменена, вернём ${pln(refund)}` : 'Бронь отменена' })
          onClose()
        },
      },
    )
  }

  return (
    <Modal title="Отменить бронь?" onClose={onClose}>
      <div className={modalStyles.summary}>
        <div className={modalStyles.summaryRow}>
          <span>{event.title} · {seats} {seats === 1 ? 'место' : seats < 5 ? 'места' : 'мест'}</span>
          <span>{paid === null ? '' : pln(paid)}</span>
        </div>
        {refund > 0 ? (
          <>
            <div className={`${modalStyles.summaryRow} ${modalStyles.refund}`}>
              <span>Возврат на карту</span>
              <span>{pln(refund)}</span>
            </div>
            <span className={modalStyles.small}>Деньги вернутся за 3–5 рабочих дней. Места снова появятся в продаже.</span>
          </>
        ) : (
          <span className={modalStyles.small}>
            {booking.status === 'HOLD'
              ? 'Бронь ещё не оплачена, списаний не было. Места снова появятся в продаже.'
              : `До начала меньше ${noticeHours} часов: деньги не возвращаются. Места снова появятся в продаже.`}
          </span>
        )}
      </div>
      <label className={modalStyles.field}>
        <span className={modalStyles.label}>Причина (необязательно)</span>
        <select className={modalStyles.input} value={reason} onChange={(e) => setReason(e.target.value as CancellationReason)}>
          {REASONS.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
        </select>
      </label>
      {cancel.isError && (
        <span className={modalStyles.error} role="alert">
          {cancel.error instanceof ApiError && cancel.error.status === 409 ? 'Эта бронь уже отменена.' : 'Не удалось отменить. Попробуйте ещё раз.'}
        </span>
      )}
      <div className={modalStyles.actions}>
        <button type="button" className={modalStyles.secondary} onClick={onClose}>Оставить бронь</button>
        <button type="button" className={modalStyles.danger} disabled={cancel.isPending} onClick={submit}>Отменить</button>
      </div>
    </Modal>
  )
}
