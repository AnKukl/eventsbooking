import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes, useLocation } from 'react-router-dom'
import { SearchPage } from './SearchPage'
import { readSearch, toFilter, writeSearch } from './filters'
import { mockApi, renderAt } from '../../test/render'

afterEach(() => vi.unstubAllGlobals())

const JAZZ = {
  id: 3, title: 'Late set: джаз-трио', startsAt: new Date(Date.now() + 86_400_000).toISOString(), capacity: 20,
  bookedCount: 8, organizerId: 1, seatStatus: 'AVAILABLE', isFavorite: false, venue: 'Grand Cafe, Sopot', price: 18,
}

function Url() {
  const location = useLocation()
  return <div data-testid="url">{location.search}</div>
}

function page(entry: string) {
  return renderAt('/search', (
    <Routes>
      <Route path="*" element={<><SearchPage /><Url /></>} />
    </Routes>
  ), entry)
}

describe('search filters in the URL', () => {
  it('read back exactly what was written', () => {
    const state = { q: 'джаз', when: 'today' as const, cheap: true, city: 'sopot' }
    expect(readSearch(writeSearch(state))).toEqual(state)
  })

  it('ignore unknown values', () => {
    expect(readSearch(new URLSearchParams('when=yesterday&city=paris&maxPrice=7'))).toEqual({ q: '', when: 'all', cheap: false, city: null })
  })

  it('turn into the API filter: Sopot in both spellings, 30 PLN, today only', () => {
    const now = new Date(2030, 0, 10, 15, 0)
    const filter = toFilter({ q: 'джаз', when: 'today', cheap: true, city: 'sopot' }, now)
    expect(filter).toMatchObject({ q: 'джаз', maxPrice: 30, city: 'Sopot,Сопот' })
    expect(new Date(filter.to!).getTime() - new Date(filter.from!).getTime()).toBe(86_400_000)
  })
})

describe('SearchPage (G-search)', () => {
  it('restores the filters from a shared link and asks the API with them', async () => {
    const calls = mockApi({ 'GET /api/events': { body: { content: [JAZZ], page: { totalElements: 1 } } }, 'GET /api/me': { status: 401 } })
    page('/search?q=%D0%B4%D0%B6%D0%B0%D0%B7&maxPrice=30&city=sopot')

    expect(await screen.findByRole('heading', { name: '«джаз» · найдено 1' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'До 30 PLN' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Сопот' })).toHaveAttribute('aria-pressed', 'true')
    const query = new URL(calls.find((call) => call.path.startsWith('/api/events'))!.path, 'http://x').searchParams
    expect(query.get('q')).toBe('джаз')
    expect(query.get('maxPrice')).toBe('30')
    expect(query.get('city')).toBe('Sopot,Сопот')
  })

  it('a chip changes the URL, so the link can be shared', async () => {
    mockApi({ 'GET /api/events': { body: { content: [JAZZ], page: { totalElements: 1 } } }, 'GET /api/me': { status: 401 } })
    page('/search?q=jazz')

    await userEvent.click(await screen.findByRole('button', { name: 'Сегодня' }))
    await userEvent.click(screen.getByRole('button', { name: 'Гданьск' }))

    await waitFor(() => expect(screen.getByTestId('url')).toHaveTextContent('?q=jazz&when=today&city=gdansk'))
  })

  it('nothing found: says so and offers to reset the filters', async () => {
    mockApi({ 'GET /api/events': { body: { content: [], page: { totalElements: 0 } } }, 'GET /api/me': { status: 401 } })
    page('/search?q=zzz&when=week')

    expect(await screen.findByRole('status')).toHaveTextContent('Ничего не нашли')
    expect(screen.getByRole('link', { name: 'посмотрите все события недели' })).toHaveAttribute('href', '/events?when=week')
    await userEvent.click(screen.getByRole('button', { name: 'Сбросить фильтры' }))

    await waitFor(() => expect(screen.getByTestId('url')).toBeEmptyDOMElement())
  })
})
