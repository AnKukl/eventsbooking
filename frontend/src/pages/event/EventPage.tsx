import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { useBookingSettings, useEvent } from '../../api/booking'
import { useFavorite } from '../../api/favorites'
import { usePreview } from '../../api/preview'
import { useMe } from '../../api/session'
import { useToast } from '../../components/toast/Toast'
import { price, seatsLeft, seatsText, whenTitle } from '../home/format'
import { WaitlistModal } from './WaitlistModal'
import styles from './EventPage.module.css'

/** The event page: design/pages/O-event. */
export function EventPage() {
  const id = Number(useParams().id)
  const event = useEvent(id)
  const preview = usePreview(id, Number.isFinite(id))
  const settings = useBookingSettings()
  const { toggle, ready } = useFavorite()
  const toast = useToast()
  const [waitlist, setWaitlist] = useState(false)
  const me = useMe()
  const navigate = useNavigate()
  const location = useLocation()

  /** the waitlist needs an account: a guest signs in first and comes back here */
  function openWaitlist() {
    if (!me.data) navigate(`/login?returnTo=${encodeURIComponent(location.pathname)}`)
    else setWaitlist(true)
  }

  if (event.isPending) return <p className={styles.status}>Загружаем событие…</p>
  if (event.isError) {
    const missing = event.error instanceof ApiError && event.error.status === 404
    return (
      <div className={styles.status} role="alert">
        <p>{missing ? 'Такого события нет или оно ещё не опубликовано.' : 'Не удалось загрузить событие.'}</p>
        {missing ? <Link to="/">На главную</Link> : (
          <button type="button" className="btn-primary" onClick={() => void event.refetch()}>Повторить</button>
        )}
      </div>
    )
  }

  const item = event.data
  const left = seatsLeft(item)
  const soldOut = item.seatStatus === 'SOLD_OUT' || left === 0
  const chip = price(item)
  const photo = preview.data?.media?.[0]
  const fit = preview.data?.audienceFit ?? []
  const notFit = preview.data?.audienceNotFit ?? []
  const notice = settings.data?.cancellationNoticeHours ?? 24

  async function share() {
    const url = window.location.href
    try {
      if (navigator.share) {
        await navigator.share({ title: item.title, url })
      } else {
        await navigator.clipboard.writeText(url)
        toast.show({ message: 'Ссылка скопирована' })
      }
    } catch {
      // the user closed the share sheet: nothing to do
    }
  }

  return (
    <>
      <Link to="/" className={styles.back}>← Все события</Link>
      <div className={styles.layout}>
        <div className={styles.photo}>
          {(photo?.url || item.coverUrl) && <img src={photo?.url ?? item.coverUrl} alt={photo?.alt ?? item.title ?? ''} />}
          {!soldOut && <span className={`price-chip ${styles.seatsChip}`}>Осталось {seatsText(left)}</span>}
        </div>

        <div className={styles.side}>
          <span className={styles.when}>{whenTitle(item.startsAt!)}</span>
          <h1 className={styles.title}>{item.title}</h1>
          {item.venue && (
            <div className={styles.chips}>
              <span className={styles.chip}>{item.venue}</span>
            </div>
          )}

          <div className={styles.buttons}>
            <button
              type="button"
              className={`${styles.outline} ${item.isFavorite ? styles.saved : ''}`}
              aria-pressed={Boolean(item.isFavorite)}
              aria-label={item.isFavorite ? 'Убрать из избранного' : 'Добавить в избранное'}
              disabled={!ready}
              onClick={() => toggle(item)}
            >
              {item.isFavorite ? '♥ В избранном' : '♡ В избранное'}
            </button>
            <button type="button" className={styles.outline} onClick={() => void share()}>
              Поделиться
            </button>
            <Link to={`/map?event=${item.id}`} className={styles.outline}>
              На карте
            </Link>
          </div>

          <div className={styles.card}>
            <div className={styles.priceRow}>
              <span>Билет за место</span>
              <span className={styles.price}>{chip ?? '—'}</span>
            </div>
            {soldOut ? (
              <>
                <button type="button" className={styles.disabled} disabled>Мест нет</button>
                <button type="button" className={styles.notify} onClick={openWaitlist}>
                  Сообщить, когда появятся
                </button>
              </>
            ) : (
              <Link to={`/events/${item.id}/book`} className={styles.book}>Забронировать</Link>
            )}
            <span className={styles.note}>Отмена бесплатна до {notice} часов до начала</span>
          </div>

          {(item.description || fit.length > 0 || notFit.length > 0) && (
            <div className={styles.details}>
              {item.description && <p className={styles.description}>{item.description}</p>}
              {fit.length > 0 && <span><b>Для кого:</b> {fit.join(', ')}.</span>}
              {notFit.length > 0 && <span><b>Не подойдёт:</b> {notFit.join(', ')}.</span>}
            </div>
          )}
        </div>
      </div>
      {waitlist && <WaitlistModal event={item} onClose={() => setWaitlist(false)} />}
    </>
  )
}
