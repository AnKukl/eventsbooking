import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useQuery } from '@tanstack/react-query'
import { EventCard } from './EventCard'
import { api } from '../api/client'
import type { EventItem } from '../api/events'
import { mockApi, renderAt } from '../test/render'

afterEach(() => vi.unstubAllGlobals())

const EVENT = {
  id: 5, title: 'Late set: джаз-трио', startsAt: new Date(Date.now() + 86_400_000).toISOString(), capacity: 20,
  bookedCount: 8, organizerId: 1, seatStatus: 'AVAILABLE', isFavorite: false, venue: 'Grand Cafe, Сопот', price: 18,
}

/** The card as the app shows it: the event comes from the query cache, so the optimistic change is visible. */
function CachedCard() {
  const event = useQuery({ queryKey: ['events', 'single', 5], queryFn: () => api<EventItem>('/api/events/5') })
  return event.data ? <EventCard event={event.data} /> : null
}

const signedIn = { 'GET /api/me': { body: { id: 7, email: 'anna@example.com', role: 'USER' } } }

function deferred() {
  let resolve!: (reply: { status: number }) => void
  const promise = new Promise<{ status: number }>((r) => (resolve = r))
  return { promise, resolve }
}

async function heart() {
  const button = await screen.findByRole('button', { name: /избранно/ })
  await waitFor(() => expect(button).toBeEnabled())
  return button
}

describe('EventCard (K-favorites)', () => {
  it('shows when, title, place with seats, price and "Забронировать"', async () => {
    mockApi({ 'GET /api/events/5': { body: EVENT }, ...signedIn })
    renderAt('/', <CachedCard />)

    expect(await screen.findByRole('link', { name: 'Late set: джаз-трио' })).toHaveAttribute('href', '/events/5')
    expect(screen.getByText('Grand Cafe, Сопот · 12 мест')).toBeInTheDocument()
    expect(screen.getByText('18 PLN')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Забронировать' })).toHaveAttribute('href', '/events/5/book')
  })

  it('a sold-out event offers to be told when seats appear', async () => {
    mockApi({ 'GET /api/events/5': { body: { ...EVENT, bookedCount: 20, seatStatus: 'SOLD_OUT' } }, ...signedIn })
    renderAt('/', <CachedCard />)

    expect(await screen.findByText('Нет мест')).toBeInTheDocument()
    expect(screen.getByText('Сообщим, когда появятся места')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Забронировать' })).not.toBeInTheDocument()
  })

  it('the heart changes at once, before the server answers', async () => {
    const answer = deferred()
    mockApi({ 'GET /api/events/5': { body: EVENT }, 'POST /api/favorites/5': () => answer.promise, ...signedIn })
    renderAt('/', <CachedCard />)

    const button = await heart()
    expect(button).toHaveAttribute('aria-pressed', 'false')
    await userEvent.click(button)

    expect(screen.getByRole('button', { name: 'Убрать из избранного' })).toHaveAttribute('aria-pressed', 'true')
    answer.resolve({ status: 201 })
    expect(await screen.findByRole('status')).toHaveTextContent('Сохранено в избранное')
  })

  it('a failure (500) puts the heart back and says so', async () => {
    mockApi({ 'GET /api/events/5': { body: EVENT }, 'POST /api/favorites/5': { status: 500, body: { status: 500 } }, ...signedIn })
    renderAt('/', <CachedCard />)

    await userEvent.click(await heart())

    expect(await screen.findByRole('status')).toHaveTextContent('Не удалось обновить избранное')
    expect(screen.getByRole('button', { name: 'Добавить в избранное' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('"Отменить" in the toast removes the event from favourites again', async () => {
    const calls = mockApi({
      'GET /api/events/5': { body: EVENT },
      'POST /api/favorites/5': { status: 201 },
      'DELETE /api/favorites/5': { status: 204 },
      ...signedIn,
    })
    renderAt('/', <CachedCard />)

    await userEvent.click(await heart())
    await userEvent.click(await screen.findByRole('button', { name: 'Отменить' }))

    await waitFor(() => expect(calls).toContainEqual({ method: 'DELETE', path: '/api/favorites/5', body: undefined }))
    expect(screen.getByRole('button', { name: 'Добавить в избранное' })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('a guest goes to sign in with returnTo', async () => {
    mockApi({ 'GET /api/events/5': { body: EVENT }, 'GET /api/me': { status: 401 }, 'POST /api/auth/refresh': { status: 401 } })
    renderAt('/account/favorites', <CachedCard />)

    await userEvent.click(await heart())

    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent('/login?returnTo=%2Faccount%2Ffavorites'),
    )
  })
})
