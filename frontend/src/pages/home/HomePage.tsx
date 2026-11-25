import { Link, useLocation, useNavigate } from 'react-router-dom'
import { type EventItem, useToggleFavorite, useUpcomingEvents } from '../../api/events'
import { useMe } from '../../api/session'
import { price, seatsLeft, seatsText, when, whenTitle } from './format'
import styles from './HomePage.module.css'

/** ♡ on a photo tile (design/README.md: aria-pressed, aria-label). A guest is sent to sign in first. */
function FavoriteButton({ event, className }: { event: EventItem; className: string }) {
  const me = useMe()
  const toggle = useToggleFavorite()
  const navigate = useNavigate()
  const location = useLocation()
  const pressed = Boolean(event.isFavorite)

  function onClick() {
    if (!me.data) {
      navigate('/login', { state: { from: location.pathname + location.search } })
      return
    }
    toggle.mutate({ id: event.id!, favorite: !pressed })
  }

  return (
    <button
      type="button"
      className={`fav-btn ${className}`}
      aria-label={pressed ? 'Убрать из избранного' : 'Добавить в избранное'}
      aria-pressed={pressed}
      disabled={toggle.isPending}
      onClick={onClick}
    >
      {pressed ? '♥' : '♡'}
    </button>
  )
}

function Cover({ event }: { event: EventItem }) {
  return event.coverUrl ? <img className={styles.cover} src={event.coverUrl} alt={event.title ?? ''} /> : null
}

function BigTile({ event }: { event: EventItem }) {
  const chip = price(event)
  const left = seatsLeft(event)
  return (
    <article className={`photo ${styles.tile} ${styles.big}`}>
      <Cover event={event} />
      <div className={`glass ${styles.glassBig}`}>
        <div className={styles.meta}>
          {[when(event.startsAt!), event.venue].filter(Boolean).join(' · ')}
        </div>
        <h2 className={styles.bigTitle}>
          <Link to={`/events/${event.id}`}>{event.title}</Link>
        </h2>
        <div className={styles.bigRow}>
          <span>{left > 0 ? `Осталось ${seatsText(left)}` : 'Мест нет'}</span>
          {event.seatStatus === 'SOLD_OUT' ? (
            <button type="button" className={styles.bookDisabled} disabled>
              Мест нет
            </button>
          ) : (
            <Link to={`/events/${event.id}/book`} className={`btn-primary ${styles.book}`}>
              Забронировать
            </Link>
          )}
        </div>
      </div>
      {chip && <span className={`price-chip ${styles.chipBig}`}>{chip}</span>}
      <FavoriteButton event={event} className={styles.favBig} />
    </article>
  )
}

function SmallTile({ event, place }: { event: EventItem; place: string }) {
  const chip = price(event)
  return (
    <article className={`photo ${styles.tile} ${place}`}>
      <Cover event={event} />
      <Link to={`/events/${event.id}`} className={styles.tileLink} aria-label={event.title} />
      <div className={`glass ${styles.glassSmall}`}>
        <div className={styles.smallTitle}>{event.title}</div>
        <div className={styles.smallMeta}>
          {[event.venue, when(event.startsAt!).replace(/^.* (\d\d:\d\d)$/, '$1'), seatsText(seatsLeft(event))]
            .filter(Boolean)
            .join(' · ')}
        </div>
      </div>
      {chip && <span className={`price-chip ${styles.chipSmall}`}>{chip}</span>}
      <FavoriteButton event={event} className={styles.favSmall} />
    </article>
  )
}

function WeekCard({ event }: { event: EventItem }) {
  const chip = price(event)
  return (
    <Link to={`/events/${event.id}`} className={styles.weekCard}>
      <span className={styles.weekWhen}>{whenTitle(event.startsAt!)}</span>
      <span className={styles.weekTitle}>{event.title}</span>
      <span className={styles.weekMeta}>
        {[event.venue, `${seatsText(seatsLeft(event))}`].filter(Boolean).join(' · ')}
      </span>
      {chip && <span className={styles.weekPrice}>{chip}</span>}
    </Link>
  )
}

const SMALL = [styles.small1, styles.small2, styles.small3]

/** The approved home page: design/pages/D-bento. */
export function HomePage() {
  const events = useUpcomingEvents(20)

  if (events.isPending) return <p className={styles.status}>Загружаем события…</p>
  if (events.isError) return <p className={styles.status} role="alert">Не удалось загрузить события. Обновите страницу.</p>

  const now = Date.now()
  const upcoming = (events.data.content ?? []).filter((event) => new Date(event.startsAt!).getTime() >= now)
  if (upcoming.length === 0) return <p className={styles.status}>Скоро здесь появятся события.</p>

  const [first, ...rest] = upcoming
  const tiles = rest.slice(0, 3)
  const week = rest.slice(3, 6)
  const more = Math.max(0, (events.data.page?.totalElements ?? upcoming.length) - 1 - tiles.length - week.length)

  return (
    <div className={styles.grid}>
      <BigTile event={first} />
      {tiles.map((event, index) => (
        <SmallTile key={event.id} event={event} place={SMALL[index]} />
      ))}

      <div className={`${styles.solid} ${styles.nearby}`}>
        <span className={styles.solidLabel}>Сегодня рядом</span>
        <span className={styles.solidTitle}>Что успеть до 21:00</span>
        <Link to="/map" className={styles.map}>
          Открыть карту
        </Link>
      </div>

      {week.length > 0 && (
        <section className={`${styles.stripBox} ${styles.strip}`} aria-labelledby="week-title">
          <div className={styles.stripHead}>
            <span className={styles.stripLabel}>Скоро</span>
            <h2 id="week-title" className={styles.stripTitle}>
              В эту неделю
            </h2>
            <Link to="/events?when=week" className={styles.stripLink}>
              {more > 0 ? `Ещё ${more} ${seatsWord(more)} · все →` : 'Все события →'}
            </Link>
          </div>
          {week.map((event) => (
            <WeekCard key={event.id} event={event} />
          ))}
        </section>
      )}
    </div>
  )
}

/** "событие", "события", "событий" */
function seatsWord(count: number): string {
  if (count % 10 === 1 && count % 100 !== 11) return 'событие'
  if ([2, 3, 4].includes(count % 10) && ![12, 13, 14].includes(count % 100)) return 'события'
  return 'событий'
}
