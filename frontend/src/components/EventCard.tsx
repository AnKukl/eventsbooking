import { Link } from 'react-router-dom'
import type { EventItem } from '../api/events'
import { FavoriteButton } from './FavoriteButton'
import { useHoverPreview } from './preview/HoverPreview'
import { price, seatsLeft, seatsText, whenTitle } from '../pages/home/format'
import styles from './EventCard.module.css'

/**
 * An event as a card (design/pages/K-favorites): when, heart, title, place and seats, price and "Забронировать".
 * A sold-out event shows "Нет мест" and offers to be told when seats appear (the waitlist lives on the event page).
 */
export function EventCard({ event }: { event: EventItem }) {
  const soldOut = event.seatStatus === 'SOLD_OUT'
  const chip = price(event)
  const { trigger, overlay } = useHoverPreview(event)
  return (
    <article className={`${styles.card} ${soldOut ? styles.soldOut : ''}`} {...trigger}>
      {overlay}
      <div className={styles.top}>
        <span className={soldOut ? styles.whenMuted : styles.when}>{soldOut ? 'Нет мест' : whenTitle(event.startsAt!)}</span>
        <FavoriteButton event={event} className={styles.heart} />
      </div>
      <Link to={`/events/${event.id}`} className={styles.title}>
        {event.title}
      </Link>
      <span className={styles.meta}>
        {[event.venue, soldOut ? chip : seatsText(seatsLeft(event))].filter(Boolean).join(' · ')}
      </span>
      <div className={styles.bottom}>
        {soldOut ? (
          <>
            <span className={styles.waitText}>Сообщим, когда появятся места</span>
            <Link to={`/events/${event.id}`} className={styles.notify}>
              Уведомить
            </Link>
          </>
        ) : (
          <>
            {chip ? <span className={styles.price}>{chip}</span> : <span />}
            <Link to={`/events/${event.id}/book`} className={styles.book}>
              Забронировать
            </Link>
          </>
        )}
      </div>
    </article>
  )
}
