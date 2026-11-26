import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import { WeekPage, byDay, range } from './WeekPage'
import { mockApi, renderAt } from '../../test/render'

afterEach(() => vi.unstubAllGlobals())

const at = (day: number, hour: number) => new Date(2030, 0, day, hour, 0).toISOString()
const event = (id: number, title: string, startsAt: string, extra = {}) => ({
  id, title, startsAt, capacity: 20, bookedCount: 6, organizerId: 1, seatStatus: 'AVAILABLE', isFavorite: false,
  venue: 'Grand Cafe, Sopot', price: 18, ...extra,
})

describe('week ranges', () => {
  it('a week is the next seven days from today', () => {
    const { from, to } = range('week', new Date(2030, 0, 10, 15)) // Thursday
    expect(from).toEqual(new Date(2030, 0, 10))
    expect(to).toEqual(new Date(2030, 0, 17))
  })

  it('the weekend is the coming Saturday and Sunday, or what is left of the current one', () => {
    expect(range('weekend', new Date(2030, 0, 10, 15))).toEqual({ from: new Date(2030, 0, 12), to: new Date(2030, 0, 14) })
    expect(range('weekend', new Date(2030, 0, 13, 9))).toEqual({ from: new Date(2030, 0, 13), to: new Date(2030, 0, 14) })
  })

  it('groups events by day in order', () => {
    const groups = byDay([event(1, 'a', at(10, 12)), event(2, 'b', at(10, 19)), event(3, 'c', at(12, 11))] as never)
    expect(groups.map((group) => group.events.map((e) => e.id))).toEqual([[1, 2], [3]])
  })
})

describe('WeekPage (J-week)', () => {
  it('shows the events by day and how many are left to show', async () => {
    const today = new Date()
    const day = (offset: number, hour: number) => new Date(today.getFullYear(), today.getMonth(), today.getDate() + offset, hour).toISOString()
    mockApi({
      'GET /api/events': { body: { content: [event(1, 'Лекция о Балтике', day(1, 12), { price: 0 }), event(2, 'Джаз-трио', day(1, 19)), event(3, 'Кухня Pomorza', day(2, 20))], page: { totalElements: 30 } } },
      'GET /api/me': { status: 401 },
    })
    renderAt('/events', <WeekPage />)

    expect(await screen.findByText('Показано 3 из 30 событий на неделе')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Все события недели' })).toBeInTheDocument()
    const days = screen.getAllByRole('region')
    expect(days).toHaveLength(2)
    expect(within(days[0]).getByText(/Лекция о Балтике/)).toBeInTheDocument()
    expect(within(days[0]).getByText('Бесплатно · 14 мест')).toBeInTheDocument()
    expect(within(days[1]).getByText(/Кухня Pomorza/)).toBeInTheDocument()
    expect(screen.getByText('Показано 3 из 30 событий на неделе')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Показать ещё' })).toBeInTheDocument()
  })
})
