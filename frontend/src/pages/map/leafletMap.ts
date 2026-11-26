import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

export type MapPoint = { id: number; number: number; lat: number; lng: number; title: string }
export type MapHandle = { select: (id: number | null) => void; destroy: () => void }

/**
 * The Leaflet part of the map page, loaded on demand (a failure here must not break the page). OpenStreetMap tiles need
 * no key. If no tile at all can be loaded, `onTilesFailed` lets the page fall back to the list.
 */
export function createMap(container: HTMLElement, options: {
  center: [number, number]
  points: MapPoint[]
  pinClass: string
  pinSelectedClass: string
  onSelect: (id: number) => void
  onTilesFailed: () => void
}): MapHandle {
  const map = L.map(container, { zoomControl: true, attributionControl: true }).setView(options.center, 13)
  let loaded = 0
  let failed = 0
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  })
    .on('tileload', () => { loaded++ })
    .on('tileerror', () => {
      failed++
      if (loaded === 0 && failed >= 4) options.onTilesFailed()
    })
    .addTo(map)

  const markers = new Map<number, L.Marker>()
  const icon = (point: MapPoint, selected: boolean) =>
    L.divIcon({
      className: '',
      html: `<span class="${selected ? `${options.pinClass} ${options.pinSelectedClass}` : options.pinClass}">${point.number}</span>`,
      iconSize: selected ? [40, 40] : [34, 34],
      iconAnchor: selected ? [20, 20] : [17, 17],
    })
  for (const point of options.points) {
    const marker = L.marker([point.lat, point.lng], { icon: icon(point, false), title: point.title, keyboard: true })
      .on('click', () => options.onSelect(point.id))
      .addTo(map)
    markers.set(point.id, marker)
  }
  if (options.points.length > 1) {
    map.fitBounds(L.latLngBounds(options.points.map((point) => [point.lat, point.lng])), { padding: [60, 60], maxZoom: 15 })
  } else if (options.points.length === 1) {
    map.setView([options.points[0].lat, options.points[0].lng], 15)
  }

  return {
    select(id) {
      for (const point of options.points) markers.get(point.id)?.setIcon(icon(point, point.id === id))
      const point = options.points.find((item) => item.id === id)
      if (point) map.panTo([point.lat, point.lng])
    },
    destroy() {
      map.remove()
    },
  }
}
