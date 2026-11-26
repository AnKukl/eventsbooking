import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { MapPage } from './MapPage'
import { mockApi, renderAt } from '../../test/render'

// the Leaflet module cannot be loaded (offline, blocked CDN, broken bundle)
vi.mock('./leafletMap', () => { throw new Error('leaflet failed to load') })

afterEach(() => vi.unstubAllGlobals())

const EVENTS = [
  { id: 1, title: 'Late set: джаз-трио', startsAt: new Date(Date.now() + 3_600_000).toISOString(), capacity: 20, bookedCount: 8,
    organizerId: 1, seatStatus: 'AVAILABLE', isFavorite: false, venue: 'Grand Cafe, Sopot', price: 18, latitude: 54.44, longitude: 18.56 },
  { id: 2, title: 'Квиз-вечер', startsAt: new Date(Date.now() + 7_200_000).toISOString(), capacity: 20, bookedCount: 8,
    organizerId: 1, seatStatus: 'AVAILABLE', isFavorite: false, venue: 'Stara Bramka', price: 12 },
]

describe('MapPage (H-map)', () => {
  it('a map that cannot load leaves the page working: the list of places is shown', async () => {
    mockApi({ 'GET /api/events': { body: { content: EVENTS, page: { totalElements: 2 } } }, 'GET /api/me': { status: 401 } })
    renderAt('/map', <MapPage />)

    expect(await screen.findByRole('status')).toHaveTextContent('Карта не загрузилась, вот список мест.')
    expect(screen.getByRole('link', { name: /Late set: джаз-трио/ })).toHaveAttribute('href', '/events/1')
    expect(screen.getByText('Место не отмечено на карте')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Карта' })).toBeDisabled()
  })

  it('the list view needs no map at all', async () => {
    mockApi({ 'GET /api/events': { body: { content: EVENTS, page: { totalElements: 2 } } }, 'GET /api/me': { status: 401 } })
    renderAt('/map', <MapPage />, '/map?view=list')

    expect(await screen.findByRole('link', { name: /Квиз-вечер/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Список' })).toHaveAttribute('aria-pressed', 'true')
  })
})
