import { Link } from 'react-router-dom'
import { type EventItem, useEventsFromToday, useEventsThisWeek } from '../../api/events'
import { FavoriteButton } from '../../components/FavoriteButton'
import { useHoverPreview } from '../../components/preview/HoverPreview'
import { price, seatsLeft, seatsText, when, whenTitle } from './format'
import { arrange } from './select'
import styles from './HomePage.module.css'

function Cover({ event }: { event: EventItem }) {
  return event.coverUrl ? <img className={styles.cover} src={event.coverUrl} alt={event.title ?? ''} /> : null
}

function BigTile({ event }: { event: EventItem }) {
  const chip = price(event)
  const left = seatsLeft(event)
  const { trigger, overlay } = useHoverPreview(event)
  return (
    <article className={`photo ${styles.tile} ${styles.big}`} {...trigger}>
      {overlay}
      <Cover event={event} />
      <div className={`glass ${styles.glassBig}`}>
        <div className={styles.meta}>
          {[when(event.startsAt!), event.venue].filter(Boolean).join(' · ')}
        </div>
        <h2 className={styles.bigTitle}>
          <Link to={`/events/${event.id}`}>{event.title}</Link>
        </h2>
        <div className={styles.bigRow}>
          <span>{left > 0 ? `Осталось ${seatsText(left)}` : ''}</span>
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
      <FavoriteButton event={event} className={`fav-btn ${styles.favBig}`} />
    </article>
  )
}

function SmallTile({ event, place }: { event: EventItem; place: string }) {
  const chip = price(event)
  const { trigger, overlay } = useHoverPreview(event)
  return (
    <article className={`photo ${styles.tile} ${place}`} {...trigger}>
      {overlay}
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
      <FavoriteButton event={event} className={`fav-btn ${styles.favSmall}`} />
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

/** Placeholders of the same shape while the catalogue loads. */
function Skeleton() {
  return (
    <div className={styles.grid} aria-busy="true" aria-label="Загружаем события">
      <div className={`photo ${styles.tile} ${styles.big} ${styles.skeleton}`} />
      {SMALL.map((place) => (
        <div key={place} className={`photo ${styles.tile} ${place} ${styles.skeleton}`} />
      ))}
      <div className={`${styles.solid} ${styles.nearby} ${styles.skeleton}`} />
      <div className={`${styles.stripBox} ${styles.strip} ${styles.skeleton}`} />
    </div>
  )
}

function Nearby() {
  return (
    <div className={`${styles.solid} ${styles.nearby}`}>
      <span className={styles.solidLabel}>Сегодня рядом</span>
      <span className={styles.solidTitle}>Что успеть до 21:00</span>
      <Link to="/map" className={styles.map}>
        Открыть карту
      </Link>
    </div>
  )
}

/** The approved home page: design/pages/D-bento. */
export function HomePage() {
  const fromToday = useEventsFromToday(20)
  const week = useEventsThisWeek(12)

  if (fromToday.isPending || week.isPending) return <Skeleton />
  if (fromToday.isError || week.isError) {
    return (
      <div className={styles.status} role="alert">
        <p>Не удалось загрузить события.</p>
        <button
          type="button"
          className="btn-primary"
          onClick={() => {
            void fromToday.refetch()
            void week.refetch()
          }}
        >
          Повторить
        </button>
      </div>
    )
  }

  const events = fromToday.data.content ?? []
  if (events.length === 0) return <p className={styles.status}>Скоро здесь появятся события.</p>

  const { big, tiles, weekCards, more } = arrange(events, week.data.content ?? [], week.data.page?.totalElements ?? 0)

  return (
    <div className={styles.grid}>
      <BigTile event={big!} />
      {tiles.map((event, index) => (
        <SmallTile key={event.id} event={event} place={SMALL[index]} />
      ))}
      <Nearby />

      {weekCards.length > 0 && (
        <section className={`${styles.stripBox} ${styles.strip}`} aria-labelledby="week-title">
          <div className={styles.stripHead}>
            <span className={styles.stripLabel}>Скоро</span>
            <h2 id="week-title" className={styles.stripTitle}>
              В эту неделю
            </h2>
            <Link to="/events?when=week" className={styles.stripLink}>
              {more > 0 ? `Ещё ${more} ${eventsWord(more)} · все →` : 'Все события →'}
            </Link>
          </div>
          <div className={styles.weekCards}>
            {weekCards.map((event) => (
              <WeekCard key={event.id} event={event} />
            ))}
          </div>
        </section>
      )}
    </div>
  )
}

/** "событие", "события", "событий" */
function eventsWord(count: number): string {
  if (count % 10 === 1 && count % 100 !== 11) return 'событие'
  if ([2, 3, 4].includes(count % 10) && ![12, 13, 14].includes(count % 100)) return 'события'
  return 'событий'
}
