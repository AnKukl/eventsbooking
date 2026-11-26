import { Link, useSearchParams } from 'react-router-dom'
import { startOfToday, type EventItem } from '../../api/events'
import { useCatalogue } from '../../api/catalogue'
import { price, seatsLeft, seatsText } from '../home/format'
import styles from './WeekPage.module.css'

const DAY = 86_400_000
const PAGE = 24
const dayName = new Intl.DateTimeFormat('ru-RU', { weekday: 'short', day: 'numeric' })
const dayMonth = new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long' })
const time = new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit' })

/** [from, to) of the chosen range: the next seven days, or the coming (or current) weekend. */
export function range(when: 'week' | 'weekend', now: Date = new Date()): { from: Date; to: Date } {
  const today = startOfToday(now)
  if (when === 'week') return { from: today, to: new Date(today.getTime() + 7 * DAY) }
  const weekday = today.getDay() // 0 Sunday … 6 Saturday
  const saturday = weekday === 0 ? new Date(today.getTime() - DAY) : new Date(today.getTime() + ((6 - weekday) * DAY))
  const from = weekday === 0 || weekday === 6 ? today : saturday
  return { from, to: new Date(saturday.getTime() + 2 * DAY) }
}

/** Events grouped by local day, in order; days without events are left out (as on the board). */
export function byDay(events: EventItem[]): { key: string; date: Date; events: EventItem[] }[] {
  const groups = new Map<string, { key: string; date: Date; events: EventItem[] }>()
  for (const event of events) {
    const date = new Date(event.startsAt!)
    const key = `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`
    if (!groups.has(key)) groups.set(key, { key, date, events: [] })
    groups.get(key)!.events.push(event)
  }
  return [...groups.values()]
}

/** All events of the week: design/pages/J-week. The chips live in the URL (when=weekend, free=1, shown=48). */
export function WeekPage() {
  const [params, setParams] = useSearchParams()
  const when = params.get('when') === 'weekend' ? 'weekend' : 'week'
  const free = params.get('free') === '1'
  const shown = Math.max(PAGE, Number(params.get('shown')) || PAGE)
  const { from, to } = range(when)
  const events = useCatalogue({ from: from.toISOString(), to: to.toISOString(), maxPrice: free ? 0 : undefined, size: shown })

  function set(change: Record<string, string | null>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(change)) value === null ? next.delete(key) : next.set(key, value)
    setParams(next, { replace: true })
  }

  const items = events.data?.content ?? []
  const total = events.data?.page?.totalElements ?? items.length
  const last = new Date(to.getTime() - 1)

  return (
    <section>
      <div className={styles.head}>
        <div className={styles.titles}>
          <span className={styles.label}>
            {when === 'week' ? 'Неделя' : 'Выходные'} {dayMonth.format(from)} – {dayMonth.format(last)}
          </span>
          <h1 className={styles.title}>{when === 'week' ? 'Все события недели' : 'События выходных'}</h1>
        </div>
        <div className={styles.chips} role="group" aria-label="Период и цена">
          <button type="button" className={styles.chip} aria-pressed={when === 'week'} onClick={() => set({ when: null, shown: null })}>Неделя</button>
          <button type="button" className={styles.chip} aria-pressed={when === 'weekend'} onClick={() => set({ when: 'weekend', shown: null })}>Выходные</button>
          <button type="button" className={styles.chip} aria-pressed={free} onClick={() => set({ free: free ? null : '1', shown: null })}>Бесплатно</button>
        </div>
      </div>

      {events.isPending && <p className={styles.status}>Загружаем события…</p>}
      {events.isError && (
        <div className={styles.status} role="alert">
          Не удалось загрузить события.{' '}
          <button type="button" className="btn-primary" onClick={() => void events.refetch()}>Повторить</button>
        </div>
      )}
      {events.isSuccess && items.length === 0 && (
        <p className={styles.status}>
          {free ? 'Бесплатных событий в эти дни нет.' : 'В эти дни событий нет.'} <Link to="/search">Поиск по всем датам</Link>
        </p>
      )}
      {events.isSuccess && items.length > 0 && (
        <>
          <div className={styles.days}>
            {byDay(items).map((day) => (
              <section key={day.key} className={styles.day} aria-label={dayMonth.format(day.date)}>
                <h2 className={styles.dayName}>{capitalize(dayName.format(day.date))}</h2>
                {day.events.map((event) => (
                  <Link key={event.id} to={`/events/${event.id}`} className={styles.event}>
                    <b>{time.format(new Date(event.startsAt!))}</b>
                    <span>{[event.title, event.venue?.split(',')[0]].filter(Boolean).join(', ')}</span>
                    <span className={styles.eventMeta}>
                      {[price(event), event.seatStatus === 'SOLD_OUT' ? 'мест нет' : seatsText(seatsLeft(event))].filter(Boolean).join(' · ')}
                    </span>
                  </Link>
                ))}
              </section>
            ))}
          </div>
          <div className={styles.foot}>
            <span>Показано {items.length} из {total} {when === 'week' ? 'событий на неделе' : 'событий в выходные'}</span>
            {items.length < total && (
              <button type="button" className={styles.more} onClick={() => set({ shown: String(shown + PAGE) })}>
                Показать ещё
              </button>
            )}
          </div>
        </>
      )}
    </section>
  )
}

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1)
}
