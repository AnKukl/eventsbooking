import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { AccountLayout } from './AccountLayout'
import { BookingsTab, inTime } from './BookingsTab'
import { FavoritesPage } from './FavoritesPage'
import { ProfileTab } from './ProfileTab'
import { NotificationsTab, groupByDay } from './NotificationsTab'
import { ToastProvider } from '../../components/toast/Toast'
import { mockApi } from '../../test/render'

afterEach(() => vi.unstubAllGlobals())

const ME = { id: 7, email: 'anna@example.com', role: 'USER', name: 'Анна', city: 'Sopot', phone: '', language: 'RU', emailVerified: true }
const inHours = (hours: number) => new Date(Date.now() + hours * 3_600_000).toISOString()

function Url() {
  return <div data-testid="url">{useLocation().pathname}</div>
}

function cabinet(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/account" element={<AccountLayout />}>
              <Route index element={<Navigate to="/account/bookings" replace />} />
              <Route path="bookings" element={<BookingsTab />} />
              <Route path="favorites" element={<FavoritesPage />} />
              <Route path="profile" element={<ProfileTab />} />
              <Route path="notifications" element={<NotificationsTab />} />
            </Route>
            <Route path="*" element={<Url />} />
          </Routes>
          <Url />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  )
}

function api(extra = {}) {
  return mockApi({
    'GET /api/me': { body: ME },
    'GET /api/favorites': { body: { content: [], page: { totalElements: 4 } } },
    'GET /api/me/notifications/unread-count': { body: { count: 3 } },
    'GET /api/bookings/my': { body: { content: [] } },
    'GET /api/settings/booking': { body: { maxSeatsPerBooking: 4, holdDurationSeconds: 600, cancellationNoticeHours: 24 } },
    'GET /api/me/notifications': { body: { content: [] } },
    'GET /api/me/notifications/preferences': { body: { preferences: [] } },
    ...extra,
  })
}

describe('cabinet tabs by URL', () => {
  it('each tab opens by its own URL and the menu follows it', async () => {
    api()
    cabinet('/account/profile')

    expect(await screen.findByRole('heading', { name: 'Личные данные' })).toBeInTheDocument()
    const menu = screen.getByRole('navigation', { name: 'Кабинет' })
    expect(within(menu).getByRole('link', { name: 'Профиль' })).toHaveClass(/active/)
    expect(await within(menu).findByRole('link', { name: 'Избранное · 4' })).toBeInTheDocument()
    expect(await within(menu).findByRole('link', { name: 'Уведомления · 3' })).toBeInTheDocument()

    await userEvent.click(within(menu).getByRole('link', { name: 'Уведомления · 3' }))
    expect(await screen.findByRole('heading', { name: 'Уведомления' })).toBeInTheDocument()
    expect(screen.getAllByTestId('url').at(-1)).toHaveTextContent('/account/notifications')
  })

  it('/account opens "Мои брони"', async () => {
    api()
    cabinet('/account')

    expect(await screen.findByRole('heading', { name: 'Привет, Анна' }, { timeout: 5000 })).toBeInTheDocument()
  })

  it('"Главная" leads home, a guest is sent to sign in', async () => {
    mockApi({ 'GET /api/me': { status: 401 }, 'POST /api/auth/refresh': { status: 401 } })
    cabinet('/account/profile')

    await waitFor(() => expect(screen.getAllByTestId('url').at(-1)).toHaveTextContent('/login'))
  })
})

describe('profile', () => {
  it('saves the changes with PATCH /api/me', async () => {
    const calls = api({ 'PATCH /api/me': { body: { ...ME, city: 'Gdańsk', language: 'PL' } } })
    cabinet('/account/profile')

    const city = await screen.findByRole('textbox', { name: 'Город' })
    await userEvent.clear(city)
    await userEvent.type(city, 'Gdańsk')
    await userEvent.click(screen.getByRole('button', { name: 'PL' }))
    expect(screen.getByText('Изменения не сохранены')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Сохранить' }))

    await waitFor(() => expect(calls).toContainEqual({
      method: 'PATCH', path: '/api/me', body: { name: 'Анна', city: 'Gdańsk', phone: '', language: 'PL' },
    }))
    expect(await screen.findByRole('status')).toHaveTextContent('Профиль сохранён')
  })

  it('shows a validation error of the form and one of the server next to the field', async () => {
    const calls = api({
      'PATCH /api/me': { status: 400, body: { status: 400, errors: [{ field: 'city', message: 'размер должен быть не больше 100' }] } },
    })
    cabinet('/account/profile')

    const phone = await screen.findByRole('textbox', { name: 'Телефон' })
    await userEvent.type(phone, '123')
    await userEvent.click(screen.getByRole('button', { name: 'Сохранить' }))
    expect(screen.getByText('Формат +48123456789')).toBeInTheDocument()
    expect(calls.some((call) => call.method === 'PATCH')).toBe(false)

    await userEvent.clear(phone)
    await userEvent.type(screen.getByRole('textbox', { name: 'Город' }), 'x')
    await userEvent.click(screen.getByRole('button', { name: 'Сохранить' }))
    expect(await screen.findByText('размер должен быть не больше 100')).toBeInTheDocument()
  })
})

describe('notifications', () => {
  it('opening the tab marks everything read once; the dots still show what was new', async () => {
    const calls = api({
      'GET /api/me/notifications': { body: { content: [
        { id: 1, type: 'SEAT_AVAILABLE', title: 'Освободилось место', body: 'Кухня Pomorza', createdAt: new Date().toISOString(), targetEventId: 5 },
        { id: 2, type: 'BOOKING_CONFIRMED', title: 'Бронь подтверждена', body: 'Свет на мраморе', createdAt: new Date(Date.now() - 86_400_000).toISOString(), readAt: new Date().toISOString() },
      ] } },
      'POST /api/me/notifications/read': { status: 204 },
      'GET /api/me/notifications/preferences': { body: { preferences: [{ type: 'REMINDER_2H', email: true, push: false }] } },
      'PUT /api/me/notifications/preferences': { body: { preferences: [{ type: 'REMINDER_2H', email: true, push: true }] } },
    })
    cabinet('/account/notifications')

    expect(await screen.findByText('Освободилось место')).toBeInTheDocument()
    await waitFor(() => expect(calls.filter((call) => call.path === '/api/me/notifications/read')).toHaveLength(1))
    expect(within(screen.getByRole('group', { name: 'Сегодня' })).getByLabelText('Новое')).toBeInTheDocument()
    expect(within(screen.getByRole('group', { name: 'Вчера' })).getByLabelText('Прочитано')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Уведомления' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('switch', { name: 'Напоминание за 2 часа: push' }))
    await waitFor(() => expect(calls).toContainEqual({
      method: 'PUT', path: '/api/me/notifications/preferences', body: { preferences: [{ type: 'REMINDER_2H', email: true, push: true }] },
    }))
  })

  it('groups by today, yesterday and earlier', () => {
    const now = new Date(2030, 0, 10, 12)
    const groups = groupByDay([
      { id: 1, createdAt: new Date(2030, 0, 10, 9).toISOString() },
      { id: 2, createdAt: new Date(2030, 0, 9, 18).toISOString() },
      { id: 3, createdAt: new Date(2030, 0, 2).toISOString() },
    ], now)
    expect(groups.map((group) => group.label)).toEqual(['Сегодня', 'Вчера', 'Ранее'])
  })
})

describe('my bookings', () => {
  it('shows the next event and lets a booking be cancelled through the window', async () => {
    api({
      'GET /api/bookings/my': { body: { content: [
        { id: 77, eventId: 5, seats: 2, status: 'CONFIRMED', event: { title: 'Свет на мраморе', startsAt: inHours(50), venue: 'Музей', price: 45 } },
        { id: 78, eventId: 6, seats: 1, status: 'CANCELLED', event: { title: 'Маяки Балтики', startsAt: inHours(10), price: 0 } },
      ] } },
    })
    cabinet('/account/bookings')

    // the next event card and the row of the list
    expect(await screen.findAllByRole('link', { name: 'Свет на мраморе' }, { timeout: 5000 })).toHaveLength(2)
    expect(screen.getByText(/Следующее событие через 2 дня/)).toBeInTheDocument()
    expect(screen.getByText('Отменена')).toBeInTheDocument()
    await userEvent.click(screen.getAllByRole('button', { name: 'Отменить' })[0])

    const dialog = await screen.findByRole('dialog', { name: 'Отменить бронь?' })
    expect(dialog).toHaveTextContent('90 PLN')
  })

  it('counts down to the next event', () => {
    expect(inTime(170 * 60_000)).toBe('через 2 ч 50 мин')
    expect(inTime(3 * 86_400_000)).toBe('через 3 дня')
  })
})
