import { useEffect, useState } from 'react'
import { Link, Navigate, useLocation, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { type Booking, useBookingSettings, useConfirm, useEvent, useHold } from '../../api/booking'
import { usePreview } from '../../api/preview'
import { useMe } from '../../api/session'
import { seatsLeft, seatsText, when } from '../home/format'
import { WaitlistModal } from './WaitlistModal'
import styles from './BookingPage.module.css'

/** "09:42" */
export function mmss(ms: number): string {
  const total = Math.max(0, Math.ceil(ms / 1000))
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
}

/** Remaining time of a hold, ticking every second; 0 once it is over. */
function useCountdown(until: string | undefined): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!until) return
    setNow(Date.now())
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [until])
  return until ? Math.max(0, new Date(until).getTime() - now) : 0
}

function newKey(): string {
  return crypto.randomUUID()
}

function sum(price: number | undefined, seats: number): string {
  if (price === undefined || price === null) return '—'
  const total = Number(price) * seats
  return total === 0 ? 'Бесплатно' : `${total.toLocaleString('ru-RU')} PLN`
}

/** Booking: design/pages/I-booking. Seats → HOLD (with an Idempotency-Key) → confirm before the hold runs out. */
export function BookingPage() {
  const id = Number(useParams().id)
  const location = useLocation()
  const me = useMe()
  const event = useEvent(id)
  const preview = usePreview(id, Number.isFinite(id))
  const settings = useBookingSettings()
  const hold = useHold()
  const confirm = useConfirm()

  const [seats, setSeats] = useState(1)
  // one key per filled-in form: a double click or a retry returns the same booking; another seat count is a new form
  const [key, setKey] = useState(newKey)
  const [booking, setBooking] = useState<Booking | null>(null)
  const [waitlist, setWaitlist] = useState(false)
  const remaining = useCountdown(booking?.status === 'HOLD' ? booking.holdExpiresAt : undefined)
  const expired = booking?.status === 'HOLD' && remaining === 0

  if (me.isPending || event.isPending || settings.isPending) return <p className={styles.status}>Загружаем…</p>
  if (!me.data) return <Navigate to={`/login?returnTo=${encodeURIComponent(location.pathname)}`} replace />
  if (event.isError || settings.isError) return <p className={styles.status} role="alert">Не удалось загрузить событие.</p>

  const item = event.data
  const left = seatsLeft(item)
  const limit = Math.max(1, Math.min(settings.data.maxSeatsPerBooking, left))
  const photo = preview.data?.media?.[0]
  const fit = preview.data?.audienceFit ?? []
  const notFit = preview.data?.audienceNotFit ?? []

  function change(next: number) {
    setSeats(Math.min(limit, Math.max(1, next)))
    setKey(newKey())
    hold.reset()
  }

  function startHold() {
    hold.mutate(
      { eventId: id, seats, key },
      {
        onSuccess: (result) => setBooking(result),
        onError: (error) => {
          if (error instanceof ApiError && error.problem?.code === 'EVENT_IS_FULL') {
            void event.refetch().then((fresh) => {
              if (fresh.data && seatsLeft(fresh.data) === 0) setWaitlist(true)
              else if (fresh.data) setSeats((current) => Math.min(current, Math.max(1, seatsLeft(fresh.data!))))
            })
            setKey(newKey())
          }
        },
      },
    )
  }

  function again() {
    setBooking(null)
    setKey(newKey())
    hold.reset()
    confirm.reset()
    void event.refetch()
  }

  function pay() {
    confirm.mutate(booking!.id!, { onSuccess: (result) => setBooking(result) })
  }

  const holdError = hold.error ? holdMessage(hold.error, left) : null
  const confirmError = confirm.error
    ? confirm.error instanceof ApiError && confirm.error.problem?.code === 'HOLD_EXPIRED'
      ? 'Время удержания истекло.'
      : 'Не удалось подтвердить бронь. Попробуйте ещё раз.'
    : null

  return (
    <>
      <Link to={`/events/${id}`} className={styles.back}>Назад к событию</Link>
      <div className={styles.layout}>
        <div className={styles.left}>
          <div className={styles.photo}>
            {(photo?.url || item.coverUrl) && <img src={photo?.url ?? item.coverUrl} alt={photo?.alt ?? item.title ?? ''} />}
          </div>
          <div className={styles.heading}>
            <span className={styles.when}>{when(item.startsAt!)}</span>
            <h1 className={styles.title}>{item.title}</h1>
            <span className={styles.meta}>
              {[item.venue, left > 0 ? `${seatsText(left)} осталось` : 'мест нет'].filter(Boolean).join(' · ')}
            </span>
          </div>
          {(preview.data?.shortDescription || item.description) && (
            <p className={styles.text}>{item.description || preview.data?.shortDescription}</p>
          )}
          {(fit.length > 0 || notFit.length > 0) && (
            <div className={styles.facts}>
              {fit.length > 0 && <div><div className={styles.factLabel}>Для кого</div>{fit.join(', ')}</div>}
              {notFit.length > 0 && <div><div className={styles.factLabel}>Не подойдёт</div>{notFit.join(', ')}</div>}
            </div>
          )}
        </div>

        <aside className={styles.panel} aria-label="Выбор мест">
          {booking?.status === 'CONFIRMED' ? (
            <div className={styles.done} role="status">
              <span className={styles.doneTitle}>Места ваши</span>
              <span>{seatsText(booking.seats ?? seats)} на «{item.title}». Бронь — в кабинете.</span>
              <Link to="/account/bookings" className={styles.pay}>Мои брони</Link>
            </div>
          ) : (
            <>
              <span className={styles.panelLabel}>Выбор мест</span>
              <div className={styles.counterRow}>
                <span className={styles.counterTitle} id="seats-label">Количество билетов</span>
                <div className={styles.counter} role="group" aria-labelledby="seats-label">
                  <button type="button" className={styles.minus} aria-label="Меньше мест"
                    disabled={Boolean(booking) || seats <= 1} onClick={() => change(seats - 1)}>−</button>
                  <span className={styles.count} aria-live="polite">{seats}</span>
                  <button type="button" className={styles.plus} aria-label="Больше мест"
                    disabled={Boolean(booking) || seats >= limit} onClick={() => change(seats + 1)}>+</button>
                </div>
              </div>
              <span className={styles.limit}>Не больше {seatsText(limit)} в одной брони</span>
              <div className={styles.lines}>
                <div className={styles.line}><span>Билет × {seats}</span><span>{sum(item.price, seats)}</span></div>
                <div className={styles.lineMuted}><span>Сбор сервиса</span><span>включён</span></div>
              </div>
              <div className={styles.total}>
                <span>Итого</span>
                <span className={styles.totalSum}>{sum(item.price, seats)}</span>
              </div>

              {booking?.status === 'HOLD' && !expired && (
                <div className={styles.hold} role="timer" aria-label="Места удержаны">
                  <span>Места удержаны</span>
                  <span className={styles.timer}>{mmss(remaining)}</span>
                </div>
              )}
              {expired && (
                <div className={styles.hold} role="alert">
                  <span>Время удержания истекло</span>
                </div>
              )}

              {holdError && <span className={styles.error} role="alert">{holdError}</span>}
              {confirmError && <span className={styles.error} role="alert">{confirmError}</span>}

              {!booking && (
                <button type="button" className={styles.pay} disabled={hold.isPending || left === 0} onClick={startHold}>
                  Перейти к оплате · {sum(item.price, seats)}
                </button>
              )}
              {booking?.status === 'HOLD' && !expired && (
                <button type="button" className={styles.pay} disabled={confirm.isPending} onClick={pay}>
                  Подтвердить и оплатить · {sum(item.price, booking.seats ?? seats)}
                </button>
              )}
              {(expired || confirmError === 'Время удержания истекло.') && (
                <button type="button" className={styles.pay} onClick={again}>Удержать места заново</button>
              )}
              <span className={styles.note}>
                Отмена бесплатна до {settings.data.cancellationNoticeHours} часов до начала
              </span>
            </>
          )}
        </aside>
      </div>
      {waitlist && <WaitlistModal event={item} onClose={() => setWaitlist(false)} />}
    </>
  )
}

function holdMessage(error: Error, left: number): string {
  if (!(error instanceof ApiError)) return 'Нет связи с сервером. Попробуйте ещё раз.'
  switch (error.problem?.code) {
    case 'EVENT_IS_FULL': return left > 0 ? `Столько мест уже нет: осталось ${seatsText(left)}.` : 'Места закончились.'
    case 'ALREADY_BOOKED': return 'У вас уже есть бронь на это событие — она в кабинете.'
    case 'EVENT_ALREADY_STARTED': return 'Событие уже началось, бронировать поздно.'
    default:
      if (error.status === 400) return error.problem?.detail ?? 'Проверьте количество мест.'
      return 'Не получилось удержать места. Попробуйте ещё раз.'
  }
}
