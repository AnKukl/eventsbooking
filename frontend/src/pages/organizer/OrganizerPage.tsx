import { useState } from 'react'
import { Link } from 'react-router-dom'
import { type OrganizerEvent, useAnalytics, useMyEvents } from '../../api/organizer'
import { when } from '../home/format'
import styles from './Organizer.module.css'

/** The badge of a row: published with few seats left is "Мало мест" (a fifth or less), as on the board. */
export function badge(event: OrganizerEvent): { text: string; className: string } {
  switch (event.status) {
    case 'DRAFT': return { text: 'Черновик', className: styles.draft }
    case 'CANCELLED': return { text: 'Отменено', className: styles.cancelled }
    case 'SOLD_OUT': return { text: 'Распродано', className: styles.soldOut }
    default: {
      const left = (event.capacity ?? 0) - (event.bookedCount ?? 0)
      return left <= (event.capacity ?? 0) / 5 ? { text: 'Мало мест', className: styles.few } : { text: 'Опубликовано', className: styles.published }
    }
  }
}

/** The organizer's overview: design/pages/T-organizer. */
export function OrganizerPage() {
  const [drafts, setDrafts] = useState(false)
  const all = useMyEvents()
  const draftList = useMyEvents('DRAFT')
  const analytics = useAnalytics('7d')
  const list = drafts ? draftList : all
  const draftCount = draftList.data?.page?.totalElements ?? draftList.data?.content?.length ?? 0
  const items = list.data?.content ?? []

  return (
    <>
      <div className={styles.stats}>
        <div className={`${styles.card} ${styles.stat}`}>
          <span className={styles.statLabel}>Продано за неделю</span>
          <span className={styles.statValue}>{analytics.data?.sold ?? '—'}</span>
          <span className={styles.statNote}>мест в подтверждённых бронях</span>
        </div>
        <div className={`${styles.card} ${styles.stat}`}>
          <span className={styles.statLabel}>Выручка, PLN</span>
          <span className={styles.statValue}>{analytics.data ? Number(analytics.data.revenue ?? 0).toLocaleString('ru-RU') : '—'}</span>
          <span className={styles.statNote}>за 7 дней</span>
        </div>
        <div className={`${styles.card} ${styles.stat}`}>
          <span className={styles.statLabel}>Заполненность</span>
          <span className={styles.statValue}>{analytics.data ? `${Math.round(Number(analytics.data.occupancy ?? 0) * 100)}%` : '—'}</span>
          <span className={styles.statNote}>по событиям периода</span>
        </div>
      </div>

      <div className={styles.card}>
        <div className={styles.listHead}>
          <h1 className={styles.listTitle}>Мои события</h1>
          <div className={styles.chips} role="group" aria-label="Фильтр">
            <button type="button" className={styles.chip} aria-pressed={!drafts} onClick={() => setDrafts(false)}>Все</button>
            <button type="button" className={styles.chip} aria-pressed={drafts} onClick={() => setDrafts(true)}>
              Черновики{draftCount ? ` · ${draftCount}` : ''}
            </button>
          </div>
        </div>
        {list.isPending && <p className={styles.wait}>Загружаем события…</p>}
        {list.isError && (
          <p className={styles.wait} role="alert">
            Не удалось загрузить события. <button type="button" className={styles.small} onClick={() => void list.refetch()}>Повторить</button>
          </p>
        )}
        {list.isSuccess && items.length === 0 && (
          <p className={styles.empty}>{drafts ? 'Черновиков нет.' : 'Событий пока нет — создайте первое кнопкой «+ Создать событие».'}</p>
        )}
        {items.length > 0 && (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th className={styles.th}>Событие</th>
                  <th className={styles.th}>Дата</th>
                  <th className={styles.th}>Продано</th>
                  <th className={styles.th}>Статус</th>
                  <th className={styles.th}><span className="visually-hidden">Действия</span></th>
                </tr>
              </thead>
              <tbody>
                {items.map((event) => {
                  const status = badge(event)
                  return (
                    <tr key={event.id}>
                      <td className={styles.td}><b>{event.title}</b><br /><span className={styles.sub}>{event.venue ?? 'Место не указано'}</span></td>
                      <td className={styles.td}>{event.startsAt ? when(event.startsAt) : ''}</td>
                      <td className={styles.td}>{event.bookedCount} / {event.capacity}</td>
                      <td className={styles.td}><span className={`${styles.status} ${status.className}`}>{status.text}</span></td>
                      <td className={`${styles.td} ${styles.rowButtons}`}>
                        <Link to={`/organizer/events/${event.id}/edit`} className={styles.small}>Редактировать</Link>
                        <Link to={`/organizer/events/${event.id}/bookings`} className={styles.small}>Брони</Link>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  )
}
