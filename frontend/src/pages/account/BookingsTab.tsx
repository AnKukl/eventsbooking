import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { type MyBooking, useMyBookings } from '../../api/account'
import { useBookingSettings, useConfirm } from '../../api/booking'
import { useMe } from '../../api/session'
import type { EventItem } from '../../api/events'
import { Modal, modalStyles } from '../../components/modal/Modal'
import { seatsText, when } from '../home/format'
import { CancelBookingModal } from './modals'
import styles from './Tabs.module.css'

const ACTIVE = new Set(['HOLD', 'CONFIRMED'])

function startOf(booking: MyBooking): number {
  return booking.event?.startsAt ? new Date(booking.event.startsAt).getTime() : 0
}

/** "через 2 часа 50 минут", "через 3 дня" */
export function inTime(ms: number): string {
  const minutes = Math.max(0, Math.round(ms / 60_000))
  if (minutes < 60) return `через ${minutes} мин`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `через ${hours} ч ${minutes % 60} мин`
  const days = Math.floor(hours / 24)
  return `через ${days} ${days === 1 ? 'день' : days < 5 ? 'дня' : 'дней'}`
}

function statusText(booking: MyBooking, now: number): string {
  switch (booking.status) {
    case 'HOLD': return booking.holdExpiresAt && new Date(booking.holdExpiresAt).getTime() > now ? 'Ждёт оплаты' : 'Удержание истекло'
    case 'CONFIRMED': return booking.event?.price !== undefined && Number(booking.event?.price) === 0 ? 'Бесплатно' : 'Оплачено'
    case 'REFUND_PENDING': return 'Возврат оформляется'
    default: return 'Отменена'
  }
}

/** The event of a booking in the shape the shared helpers expect. */
function asEvent(booking: MyBooking): EventItem {
  return { id: booking.eventId, title: booking.event?.title, startsAt: booking.event?.startsAt, venue: booking.event?.venue,
    price: booking.event?.price, latitude: booking.event?.latitude, longitude: booking.event?.longitude }
}

/** "Мои брони" (design/pages/F-account): the next event first, then the upcoming and the past bookings. */
export function BookingsTab() {
  const me = useMe()
  const bookings = useMyBookings()
  const settings = useBookingSettings()
  const confirm = useConfirm()
  const [now, setNow] = useState(() => Date.now())
  const [cancelling, setCancelling] = useState<MyBooking | null>(null)
  const [qr, setQr] = useState(false)

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 30_000)
    return () => clearInterval(timer)
  }, [])

  if (bookings.isPending) return <p className={styles.status}>Загружаем брони…</p>
  if (bookings.isError) {
    return (
      <div className={styles.status} role="alert">
        Не удалось загрузить брони.{' '}
        <button type="button" className={styles.primary} onClick={() => void bookings.refetch()}>Повторить</button>
      </div>
    )
  }

  const all = bookings.data.content ?? []
  const upcoming = all.filter((b) => ACTIVE.has(b.status!) && startOf(b) > now).sort((a, b) => startOf(a) - startOf(b))
  const past = all.filter((b) => !upcoming.includes(b))
  const next = upcoming.find((b) => b.status === 'CONFIRMED') ?? upcoming[0]
  const name = me.data?.name?.trim()
  const notice = settings.data?.cancellationNoticeHours ?? 24
  const route = next?.event?.latitude !== undefined && next?.event?.latitude !== null
    ? `https://www.openstreetmap.org/directions?route=;${next.event.latitude},${next.event.longitude}` : null

  return (
    <section>
      <div className={styles.head}>
        <h1 className={styles.title}>{name ? `Привет, ${name}` : 'Мои брони'}</h1>
        {next && <span className={styles.aside}>Следующее событие {inTime(startOf(next) - now)}</span>}
      </div>

      {next && (
        <div className={styles.next}>
          <div className={`${styles.card} ${styles.nextCard}`}>
            <span className={styles.when}>{when(next.event!.startsAt!)} · {seatsText(next.seats ?? 1)}</span>
            <Link to={`/events/${next.eventId}`} className={styles.nextTitle}>{next.event?.title}</Link>
            {next.event?.venue && <span className={styles.muted}>{next.event.venue}</span>}
            <div className={styles.buttons}>
              <button type="button" className={styles.primary} onClick={() => setQr(true)}>Показать QR</button>
              {route
                ? <a className={styles.outline} href={route} target="_blank" rel="noopener noreferrer">Как добраться</a>
                : <Link className={styles.outline} to={`/map?event=${next.eventId}`}>Как добраться</Link>}
            </div>
          </div>
          <div className={`${styles.card} ${styles.ticket}`}>
            <div className={styles.qr}>Билет с QR-кодом скоро появится здесь</div>
            <span className={styles.muted}>Пока покажите бронь в кабинете</span>
          </div>
        </div>
      )}

      <div className={styles.card}>
        <span className={styles.listLabel}>Предстоящие</span>
        {upcoming.length === 0 && (
          <p className={styles.empty}>Броней пока нет. <Link to="/">Выбрать событие</Link></p>
        )}
        {upcoming.map((booking) => (
          <div key={booking.id} className={styles.row}>
            <span>
              <Link to={`/events/${booking.eventId}`} className={styles.rowTitle}>{booking.event?.title ?? `Событие ${booking.eventId}`}</Link>
              <br />
              <span className={styles.rowMeta}>{booking.event?.startsAt ? when(booking.event.startsAt) : ''} · {seatsText(booking.seats ?? 1)}</span>
            </span>
            <span className={styles.rowMeta}>{statusText(booking, now)}</span>
            <span className={styles.actions}>
              {booking.status === 'HOLD' && booking.holdExpiresAt && new Date(booking.holdExpiresAt).getTime() > now && (
                <button type="button" className={styles.confirm} disabled={confirm.isPending}
                  onClick={() => confirm.mutate(booking.id!, { onSuccess: () => void bookings.refetch() })}>
                  Оплатить
                </button>
              )}
              <button type="button" className={styles.cancel} onClick={() => setCancelling(booking)}>Отменить</button>
            </span>
          </div>
        ))}

        {past.length > 0 && (
          <>
            <span className={styles.listLabel} style={{ marginTop: 18 }}>Прошедшие и отменённые</span>
            {past.map((booking) => (
              <div key={booking.id} className={styles.row}>
                <span>
                  <span className={styles.rowTitle}>{booking.event?.title ?? `Событие ${booking.eventId}`}</span>
                  <br />
                  <span className={styles.rowMeta}>{booking.event?.startsAt ? when(booking.event.startsAt) : ''} · {seatsText(booking.seats ?? 1)}</span>
                </span>
                <span className={styles.rowMeta}>{ACTIVE.has(booking.status!) ? 'Прошло' : statusText(booking, now)}</span>
                <span />
              </div>
            ))}
          </>
        )}
      </div>

      {cancelling && (
        <CancelBookingModal booking={cancelling} event={asEvent(cancelling)} noticeHours={notice} onClose={() => setCancelling(null)} />
      )}
      {qr && (
        <Modal title="QR-код билета" narrow onClose={() => setQr(false)}>
          <span className={modalStyles.text}>Скоро: билет с QR-кодом появится здесь. Пока на входе достаточно брони в кабинете и документа.</span>
          <div className={modalStyles.actions}>
            <button type="button" className={modalStyles.primary} onClick={() => setQr(false)}>Понятно</button>
          </div>
        </Modal>
      )}
    </section>
  )
}
