import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HomePage } from './HomePage'
import { mockApi, renderAt } from '../../test/render'

afterEach(() => vi.unstubAllGlobals())

const inDays = (days: number, hour = 19) => {
  const date = new Date()
  date.setDate(date.getDate() + days)
  date.setHours(hour, 30, 0, 0)
  return date.toISOString()
}

function event(id: number, title: string, extra: Record<string, unknown> = {}) {
  return {
    id, title, startsAt: inDays(id), capacity: 20, bookedCount: 6, organizerId: 1,
    seatStatus: 'AVAILABLE', isFavorite: false, venue: `Место ${id}`, price: 45, coverUrl: `/api/media/${id}.png`, ...extra,
  }
}

const EVENTS = [
  event(1, 'Свет на мраморе'),
  event(2, 'Late set: джаз-трио', { price: 18 }),
  event(3, 'Маяки Балтики', { price: 0 }),
  event(4, 'Pomorze: дегустация'),
  event(5, 'Квиз-вечер'),
  event(6, 'Мастер-класс по керамике'),
  event(7, 'Выставка фотографий'),
]

function api(me: 'guest' | 'user', extra = {}) {
  return mockApi({
    'GET /api/events': { body: { content: EVENTS, page: { size: 20, number: 0, totalElements: 16, totalPages: 1 } } },
    'GET /api/me': me === 'user' ? { body: { id: 7, email: 'anna@example.com', name: 'Анна', role: 'USER' } } : { status: 401 },
    'POST /api/auth/refresh': { status: 401 },
    ...extra,
  })
}

describe('HomePage (D-bento)', () => {
  it('shows the nearest event big, three tiles, "Сегодня рядом" and the week strip', async () => {
    api('guest')
    renderAt('/', <HomePage />)

    const big = (await screen.findByRole('link', { name: 'Свет на мраморе' })).closest('article')!
    expect(within(big).getByText('45 PLN')).toBeInTheDocument()
    expect(within(big).getByText('Осталось 14 мест')).toBeInTheDocument()
    expect(within(big).getByRole('link', { name: 'Забронировать' })).toHaveAttribute('href', '/events/1/book')
    expect(within(big).getByRole('img')).toHaveAttribute('src', '/api/media/1.png')

    expect(screen.getByText('Late set: джаз-трио')).toBeInTheDocument()
    expect(screen.getByText('Бесплатно')).toBeInTheDocument()
    expect(screen.getByText('Pomorze: дегустация')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Открыть карту' })).toHaveAttribute('href', '/map')

    const week = screen.getByRole('region', { name: 'В эту неделю' })
    expect(within(week).getByText('Квиз-вечер')).toBeInTheDocument()
    expect(within(week).getByText('Выставка фотографий')).toBeInTheDocument()
    expect(within(week).getByRole('link', { name: 'Ещё 9 событий · все →' })).toHaveAttribute('href', '/events?when=week')
  })

  it('a sold-out event cannot be booked', async () => {
    mockApi({
      'GET /api/events': { body: { content: [event(1, 'Аншлаг', { bookedCount: 20, seatStatus: 'SOLD_OUT' })], page: { totalElements: 1 } } },
      'GET /api/me': { status: 401 },
      'POST /api/auth/refresh': { status: 401 },
    })
    renderAt('/', <HomePage />)

    expect(await screen.findByRole('button', { name: 'Мест нет' })).toBeDisabled()
  })

  it('a guest pressing ♡ is sent to sign in and comes back here', async () => {
    api('guest')
    renderAt('/', <HomePage />)

    const hearts = await screen.findAllByRole('button', { name: 'Добавить в избранное' })
    await waitFor(() => expect(hearts[0]).toBeEnabled())
    await userEvent.click(hearts[0])

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'))
  })

  it('a signed-in user adds an event to favourites with one click', async () => {
    const calls = api('user', { 'POST /api/favorites/1': { status: 201 } })
    renderAt('/', <HomePage />)

    const big = (await screen.findByRole('link', { name: 'Свет на мраморе' })).closest('article')!
    const heart = within(big).getByRole('button', { name: 'Добавить в избранное' })
    expect(heart).toHaveAttribute('aria-pressed', 'false')
    await waitFor(() => expect(calls.some((call) => call.path === '/api/me')).toBe(true))
    await userEvent.click(heart)

    await waitFor(() => expect(calls).toContainEqual({ method: 'POST', path: '/api/favorites/1', body: undefined }))
  })

  it('an empty catalogue says so', async () => {
    mockApi({ 'GET /api/events': { body: { content: [], page: { totalElements: 0 } } }, 'GET /api/me': { status: 401 } })
    renderAt('/', <HomePage />)

    expect(await screen.findByText('Скоро здесь появятся события.')).toBeInTheDocument()
  })
})
