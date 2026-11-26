import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { startOfToday, type EventItem } from '../../api/events'
import { useCatalogue } from '../../api/catalogue'
import { useEvent } from '../../api/booking'
import { price, seatsLeft, seatsText, when } from '../home/format'
import type { MapHandle, MapPoint } from './leafletMap'
import styles from './MapPage.module.css'

/** Where the map opens when the location is not shared (design/pages/E-buttons): Sopot. */
export const SOPOT: [number, number] = [54.4418, 18.5601]
const DAY = 86_400_000

function hasPoint(event: EventItem): boolean {
  return event.latitude !== undefined && event.latitude !== null && event.longitude !== undefined && event.longitude !== null
}

/** The map: design/pages/H-map. Today's events (plus ?event=id from an event page), as pins and as a list. */
export function MapPage() {
  const [params, setParams] = useSearchParams()
  const view = params.get('view') === 'list' ? 'list' : 'map'
  const focusId = Number(params.get('event')) || undefined
  const today = startOfToday()
  const todays = useCatalogue({ from: today.toISOString(), to: new Date(today.getTime() + DAY).toISOString(), size: 50 })
  const focused = useEvent(focusId ?? 0, focusId !== undefined)
  const [text, setText] = useState('')
  const [selected, setSelected] = useState<number | null>(focusId ?? null)
  const [mapFailed, setMapFailed] = useState(false)
  const [center, setCenter] = useState<[number, number]>(SOPOT)

  const events = useMemo(() => {
    const list = [...(todays.data?.content ?? [])]
    if (focusId && focused.data && !list.some((event) => event.id === focusId)) list.unshift(focused.data)
    const words = text.trim().toLowerCase()
    return words ? list.filter((event) => `${event.title} ${event.venue ?? ''}`.toLowerCase().includes(words)) : list
  }, [todays.data, focused.data, focusId, text])

  // the visitor's location, if they allow it; otherwise the map stays on Sopot
  useEffect(() => {
    navigator.geolocation?.getCurrentPosition(
      (position) => setCenter([position.coords.latitude, position.coords.longitude]),
      () => {},
      { timeout: 5000, maximumAge: 600_000 },
    )
  }, [])

  const chosen = events.find((event) => event.id === selected) ?? null
  const numbered = events.map((event, index) => ({ event, number: index + 1 }))

  function show(next: 'map' | 'list') {
    const copy = new URLSearchParams(params)
    if (next === 'list') copy.set('view', 'list')
    else copy.delete('view')
    setParams(copy, { replace: true })
  }

  const loading = todays.isPending || (focusId !== undefined && focused.isPending)

  return (
    <section>
      <div className={styles.tabs} role="group" aria-label="Вид">
        <button type="button" className={styles.tab} aria-pressed={view === 'list' || mapFailed} onClick={() => show('list')}>Список</button>
        <button type="button" className={styles.tab} aria-pressed={view === 'map' && !mapFailed} disabled={mapFailed} onClick={() => show('map')}>Карта</button>
      </div>

      {loading && <p className={styles.status}>Загружаем события…</p>}
      {todays.isError && <p className={styles.status} role="alert">Не удалось загрузить события.</p>}

      {!loading && !todays.isError && (view === 'list' || mapFailed) && (
        <>
          {mapFailed && <p className={styles.notice} role="status">Карта не загрузилась, вот список мест.</p>}
          <EventList items={numbered} />
        </>
      )}

      {!loading && !todays.isError && view === 'map' && !mapFailed && (
        <div className={styles.area}>
          <LeafletArea
            center={center}
            points={numbered.filter(({ event }) => hasPoint(event)).map(({ event, number }) => ({
              id: event.id!, number, lat: Number(event.latitude), lng: Number(event.longitude), title: event.title ?? '',
            }))}
            selected={selected}
            onSelect={setSelected}
            onFailed={() => setMapFailed(true)}
          />
          <div className={`${styles.glass} ${styles.search}`}>
            <span className={styles.small}>Сегодня рядом · {seatsWord(events.length)}</span>
            <h1 className={styles.where}>Карта событий</h1>
            <input className={styles.input} placeholder="Адрес или название" aria-label="Адрес или название"
              value={text} onChange={(event) => setText(event.target.value)} />
          </div>
          {chosen && (
            <div className={`${styles.glass} ${styles.selected}`} aria-live="polite">
              <span className={styles.selWhen}>
                {numbered.find((item) => item.event.id === chosen.id)?.number} · {when(chosen.startsAt!)} · {seatsText(seatsLeft(chosen))}
              </span>
              <span className={styles.selTitle}>{chosen.title}</span>
              <span className={styles.selMeta}>{[chosen.venue, price(chosen)].filter(Boolean).join(' · ')}</span>
              <div className={styles.selButtons}>
                <Link to={`/events/${chosen.id}/book`} className={styles.book}>Забронировать</Link>
                {hasPoint(chosen) && (
                  <a className={styles.route} target="_blank" rel="noopener noreferrer"
                    href={`https://www.openstreetmap.org/directions?route=;${chosen.latitude},${chosen.longitude}`}>
                    Маршрут
                  </a>
                )}
              </div>
            </div>
          )}
        </div>
      )}
    </section>
  )
}

function seatsWord(count: number): string {
  if (count % 10 === 1 && count % 100 !== 11) return `${count} событие`
  if ([2, 3, 4].includes(count % 10) && ![12, 13, 14].includes(count % 100)) return `${count} события`
  return `${count} событий`
}

function EventList({ items }: { items: { event: EventItem; number: number }[] }) {
  if (items.length === 0) {
    return <p className={styles.status}>Сегодня событий нет. <Link to="/events?when=week">Все события недели</Link></p>
  }
  return (
    <ul className={styles.list}>
      {items.map(({ event, number }) => (
        <li key={event.id}>
          <Link to={`/events/${event.id}`} className={styles.item}>
            <span className={`${styles.pin} ${styles.itemNumber}`}>{number}</span>
            <span className={styles.itemBody}>
              <span className={styles.itemTitle}>{event.title}</span>
              <span className={styles.itemMeta}>{[when(event.startsAt!), event.venue, price(event)].filter(Boolean).join(' · ')}</span>
              {!hasPoint(event) && <span className={styles.itemMeta}>Место не отмечено на карте</span>}
            </span>
          </Link>
        </li>
      ))}
    </ul>
  )
}

/** Mounts Leaflet on demand; any failure (the module, the tiles) switches the page to the list. */
function LeafletArea({ center, points, selected, onSelect, onFailed }: {
  center: [number, number]
  points: MapPoint[]
  selected: number | null
  onSelect: (id: number) => void
  onFailed: () => void
}) {
  const container = useRef<HTMLDivElement>(null)
  const handle = useRef<MapHandle | null>(null)
  const pointsKey = points.map((point) => point.id).join(',')

  useEffect(() => {
    let cancelled = false
    import('./leafletMap')
      .then(({ createMap }) => {
        if (cancelled || !container.current) return
        handle.current = createMap(container.current, {
          center, points, pinClass: styles.pin, pinSelectedClass: styles.pinSelected, onSelect, onTilesFailed: onFailed,
        })
        handle.current.select(selected)
      })
      .catch(() => !cancelled && onFailed())
    return () => {
      cancelled = true
      handle.current?.destroy()
      handle.current = null
    }
    // rebuild when the set of points or the centre changes; selection is handled below
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pointsKey, center[0], center[1]])

  useEffect(() => handle.current?.select(selected), [selected])

  return <div ref={container} className={styles.map} role="region" aria-label="Карта" />
}
