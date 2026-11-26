import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HomePage } from './HomePage'
import { arrange } from './select'
import { mockApi, renderAt } from '../../test/render'

afterEach(() => vi.unstubAllGlobals())

const inDays = (days: number, hour = 19) => {
  const date = new Date()
  date.setDate(date.getDate() + days)
  date.setHours(hour, 30, 0, 0)
  return date.toISOString()
}

function event(id: number, title: string, days: number, extra: Record<string, unknown> = {}) {
  return {
    id, title, startsAt: inDays(days), capacity: 20, bookedCount: 6, organizerId: 1,
    seatStatus: 'AVAILABLE', isFavorite: false, venue: `Место ${id}`, price: 45, coverUrl: `/api/media/${id}.png`, ...extra,
  }
}

const SOLD_OUT_FIRST = event(1, 'Аншлаг сегодня', 0, { bookedCount: 20, seatStatus: 'SOLD_OUT' })
const FROM_TODAY = [
  SOLD_OUT_FIRST,
  event(2, 'Свет на мраморе', 1),
  event(3, 'Late set: джаз-трио', 2, { price: 18 }),
  event(4, 'Маяки Балтики', 3, { price: 0 }),
  event(5, 'Pomorze: дегустация', 4),
  event(6, 'Квиз-вечер', 5),
  event(7, 'Мастер-класс по керамике', 6),
  event(8, 'Через месяц', 30),
]
const WEEK = FROM_TODAY.slice(0, 7)

function catalogue(fromToday = FROM_TODAY, week = WEEK, weekTotal = 9) {
  return (_init: RequestInit | undefined, url: URL) =>
    url.searchParams.has('to')
      ? { body: { content: week, page: { totalElements: weekTotal } } }
      : { body: { content: fromToday, page: { totalElements: fromToday.length } } }
}

function api(extra = {}) {
  return mockApi({
    'GET /api/events': catalogue(),
    'GET /api/me': { status: 401 },
    'POST /api/auth/refresh': { status: 401 },
    ...extra,
  })
}

describe('arrange', () => {
  it('puts the nearest bookable event on the big tile and counts the rest of the week', () => {
    const { big, tiles, weekCards, more } = arrange(FROM_TODAY as never, WEEK as never, 9)

    expect(big!.id).toBe(2)
    expect(tiles.map((e) => e.id)).toEqual([1, 3, 4])
    expect(weekCards.map((e) => e.id)).toEqual([5, 6, 7])
    expect(more).toBe(2) // 9 this week, 7 of them on the page
  })

  it('a later event never lands in the week strip', () => {
    const { weekCards } = arrange(FROM_TODAY as never, [] as never, 0)

    expect(weekCards).toEqual([])
  })
})

describe('HomePage (D-bento)', () => {
  it('asks the API for events from today, sorted by start', async () => {
    const calls = api()
    renderAt('/', <HomePage />)

    await screen.findByRole('link', { name: 'Свет на мраморе' })
    const fromToday = calls.find((call) => call.path.startsWith('/api/events?') && !call.path.includes('to='))!
    const params = new URL(fromToday.path, 'http://localhost').searchParams
    expect(params.get('sort')).toBe('startsAt')
    expect(new Date(params.get('from')!).getHours()).toBe(0)
  })

  it('renders the grid: big tile, three tiles, "Сегодня рядом" and the week strip', async () => {
    api()
    renderAt('/', <HomePage />)

    const big = (await screen.findByRole('link', { name: 'Свет на мраморе' })).closest('article')!
    expect(within(big).getByText('45 PLN')).toBeInTheDocument()
    expect(within(big).getByText('Осталось 14 мест')).toBeInTheDocument()
    expect(within(big).getByRole('link', { name: 'Забронировать' })).toHaveAttribute('href', '/events/2/book')
    expect(within(big).getByRole('img')).toHaveAttribute('src', '/api/media/2.png')

    expect(screen.getByText('Late set: джаз-трио')).toBeInTheDocument()
    expect(screen.getByText('Бесплатно')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Открыть карту' })).toHaveAttribute('href', '/map')

    const week = screen.getByRole('region', { name: 'В эту неделю' })
    expect(within(week).getByText('Квиз-вечер')).toBeInTheDocument()
    expect(within(week).queryByText('Через месяц')).not.toBeInTheDocument()
    expect(within(week).getByRole('link', { name: 'Ещё 2 события · все →' })).toHaveAttribute('href', '/events?when=week')
  })

  it('shows placeholders of the same shape while loading', async () => {
    mockApi({ 'GET /api/events': () => new Promise(() => {}), 'GET /api/me': { status: 401 } })
    renderAt('/', <HomePage />)

    const skeleton = await screen.findByLabelText('Загружаем события')
    expect(skeleton).toHaveAttribute('aria-busy', 'true')
    expect(skeleton.children).toHaveLength(6)
  })

  it('an error offers "Повторить", which loads again', async () => {
    let fail = true
    mockApi({
      'GET /api/events': (init, url) => (fail ? { status: 500, body: { status: 500 } } : catalogue()(init, url)),
      'GET /api/me': { status: 401 },
      'POST /api/auth/refresh': { status: 401 },
    })
    renderAt('/', <HomePage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Не удалось загрузить события.')
    fail = false
    await userEvent.click(screen.getByRole('button', { name: 'Повторить' }))

    expect(await screen.findByRole('link', { name: 'Свет на мраморе' })).toBeInTheDocument()
  })

  it('an empty catalogue says so', async () => {
    mockApi({ 'GET /api/events': catalogue([], [], 0), 'GET /api/me': { status: 401 } })
    renderAt('/', <HomePage />)

    expect(await screen.findByText('Скоро здесь появятся события.')).toBeInTheDocument()
  })

  it('a sold-out big tile (no other choice) cannot be booked and does not repeat itself', async () => {
    mockApi({ 'GET /api/events': catalogue([SOLD_OUT_FIRST], [SOLD_OUT_FIRST], 1), 'GET /api/me': { status: 401 } })
    renderAt('/', <HomePage />)

    expect(await screen.findByRole('button', { name: 'Мест нет' })).toBeDisabled()
    expect(screen.getAllByText('Мест нет')).toHaveLength(1)
  })

  it('a guest pressing ♡ is sent to sign in with returnTo', async () => {
    api()
    renderAt('/', <HomePage />)

    const hearts = await screen.findAllByRole('button', { name: 'Добавить в избранное' })
    await waitFor(() => expect(hearts[0]).toBeEnabled())
    await userEvent.click(hearts[0])

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login?returnTo=%2F'))
  })
})
