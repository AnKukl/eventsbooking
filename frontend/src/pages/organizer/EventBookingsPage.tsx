import { Link, useParams } from 'react-router-dom'
import { useEventBookings, useOrganizerEvent } from '../../api/organizer'
import styles from './Organizer.module.css'

const STATUS: Record<string, string> = { HOLD: 'Ждёт оплаты', CONFIRMED: 'Подтверждена', CANCELLED: 'Отменена', REFUND_PENDING: 'Возврат' }
const date = new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })

/** The bookings of one event, without the attendees' email and phone (privacy). */
export function EventBookingsPage() {
  const id = Number(useParams().id)
  const event = useOrganizerEvent(id)
  const bookings = useEventBookings(id)
  const items = bookings.data?.content ?? []

  return (
    <div className={styles.card}>
      <div className={styles.listHead}>
        <h1 className={styles.listTitle}>Брони · {event.data?.title ?? '…'}</h1>
        <Link to={`/organizer/events/${id}/edit`} className={styles.small}>К редактору</Link>
      </div>
      {bookings.isPending && <p className={styles.wait}>Загружаем брони…</p>}
      {bookings.isError && <p className={styles.wait} role="alert">Не удалось загрузить брони.</p>}
      {bookings.isSuccess && items.length === 0 && <p className={styles.empty}>Броней пока нет.</p>}
      {items.length > 0 && (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th className={styles.th}>Участник</th>
                <th className={styles.th}>Мест</th>
                <th className={styles.th}>Статус</th>
                <th className={styles.th}>Создана</th>
              </tr>
            </thead>
            <tbody>
              {items.map((booking) => (
                <tr key={booking.id}>
                  <td className={styles.td}>{booking.attendee?.name || `Участник ${booking.attendee?.id ?? ''}`}</td>
                  <td className={styles.td}>{booking.seats}</td>
                  <td className={styles.td}>{STATUS[booking.status!] ?? booking.status}</td>
                  <td className={styles.td}>{booking.createdAt ? date.format(new Date(booking.createdAt)) : ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
