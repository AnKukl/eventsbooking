import { startOfToday } from '../../api/events'
import type { CatalogueFilter } from '../../api/catalogue'

/** The Tricity cities of the chips; venues spell them in Polish or Russian, so both are sent. */
export const CITIES = [
  { key: 'gdansk', label: 'Гданьск', match: 'Gdańsk,Гданьск,Gdansk' },
  { key: 'sopot', label: 'Сопот', match: 'Sopot,Сопот' },
  { key: 'gdynia', label: 'Гдыня', match: 'Gdynia,Гдыня' },
] as const

export const CHEAP_PRICE = 30

/** What the search page shows, read from (and written to) the query string, so a link reproduces it. */
export type SearchState = { q: string; when: 'all' | 'today' | 'week'; cheap: boolean; city: string | null }

export function readSearch(params: URLSearchParams): SearchState {
  const when = params.get('when')
  const city = params.get('city')
  return {
    q: (params.get('q') ?? '').trim(),
    when: when === 'today' || when === 'week' ? when : 'all',
    cheap: params.get('maxPrice') === String(CHEAP_PRICE),
    city: CITIES.some((item) => item.key === city) ? city : null,
  }
}

export function writeSearch(state: SearchState): URLSearchParams {
  const params = new URLSearchParams()
  if (state.q) params.set('q', state.q)
  if (state.when !== 'all') params.set('when', state.when)
  if (state.cheap) params.set('maxPrice', String(CHEAP_PRICE))
  if (state.city) params.set('city', state.city)
  return params
}

/** The API filter for a search state; the time window is computed from "now". */
export function toFilter(state: SearchState, now: Date = new Date()): CatalogueFilter {
  const today = startOfToday(now)
  const day = 86_400_000
  return {
    q: state.q || undefined,
    from: today.toISOString(), // the catalogue looks forward: from today on
    to: state.when === 'today' ? new Date(today.getTime() + day).toISOString()
      : state.when === 'week' ? new Date(today.getTime() + 7 * day).toISOString() : undefined,
    maxPrice: state.cheap ? CHEAP_PRICE : undefined,
    city: CITIES.find((item) => item.key === state.city)?.match,
    size: 40,
  }
}
