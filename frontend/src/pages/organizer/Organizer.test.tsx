import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { OrganizerLayout } from './OrganizerLayout'
import { OrganizerPage } from './OrganizerPage'
import { AUTOSAVE_MS, EditorPage } from './EditorPage'
import { ToastProvider } from '../../components/toast/Toast'
import { mockApi } from '../../test/render'

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

const ORGANIZER = { id: 1, email: 'org@example.com', role: 'ORGANIZER' }
const SETTINGS = { maxSeatsPerBooking: 4, holdDurationSeconds: 600, cancellationNoticeHours: 24 }
const DRAFT = {
  id: 9, title: 'Свет на мраморе', startsAt: new Date(Date.now() + 7 * 86_400_000).toISOString(), capacity: 40, bookedCount: 0,
  venue: '', price: null, status: 'DRAFT', version: 3, waitlistEnabled: true, audienceFit: [], audienceNotFit: [], media: [],
  shortDescription: '', description: '',
}

function Url() {
  return <div data-testid="url">{useLocation().pathname + useLocation().search}</div>
}

function organizer(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/organizer" element={<OrganizerLayout />}>
              <Route index element={<OrganizerPage />} />
              <Route path="events/:id/edit" element={<EditorPage />} />
            </Route>
            <Route path="*" element={<Url />} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('access', () => {
  it('a user without the ORGANIZER role sees 403', async () => {
    mockApi({ 'GET /api/me': { body: { id: 7, email: 'anna@example.com', role: 'USER' } } })
    organizer('/organizer')

    expect(await screen.findByRole('alert')).toHaveTextContent('403 · Нет доступа')
    expect(screen.queryByText('Мои события')).not.toBeInTheDocument()
  })

  it('a guest is sent to sign in', async () => {
    mockApi({ 'GET /api/me': { status: 401 }, 'POST /api/auth/refresh': { status: 401 } })
    organizer('/organizer')

    await waitFor(() => expect(screen.getByTestId('url')).toHaveTextContent('/login?returnTo=%2Forganizer'))
  })
})

describe('overview (T-organizer)', () => {
  it('lists the events with their state and filters the drafts', async () => {
    const published = { ...DRAFT, id: 10, title: 'Late set', status: 'PUBLISHED', capacity: 40, bookedCount: 34, venue: 'Grand Cafe' }
    mockApi({
      'GET /api/me': { body: ORGANIZER },
      'GET /api/organizer/analytics': { body: { range: '7d', sold: 126, revenue: 5670, occupancy: 0.74 } },
      'GET /api/organizer/events': (_init, url) => url.searchParams.get('status') === 'DRAFT'
        ? { body: { content: [DRAFT], page: { totalElements: 1 } } }
        : { body: { content: [published, DRAFT], page: { totalElements: 2 } } },
    })
    organizer('/organizer')

    expect(await screen.findByText('126')).toBeInTheDocument()
    expect(screen.getByText('74%')).toBeInTheDocument()
    const lateSet = (await screen.findByText('Late set')).closest('tr')!
    expect(within(lateSet).getByText('Мало мест')).toBeInTheDocument()
    expect(within(lateSet).getByText('34 / 40')).toBeInTheDocument()
    await userEvent.click(await screen.findByRole('button', { name: 'Черновики · 1' }))
    expect(screen.queryByText('Late set')).not.toBeInTheDocument()
    expect(screen.getByText('Свет на мраморе').closest('tr')).toHaveTextContent('Черновик')
  })
})

describe('editor (U-editor)', () => {
  function api(extra = {}) {
    return mockApi({
      'GET /api/me': { body: ORGANIZER },
      'GET /api/settings/booking': { body: SETTINGS },
      'GET /api/organizer/events/9': { body: DRAFT },
      ...extra,
    })
  }

  it('saves the draft by itself 3 s after the last change, with the loaded version', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const calls = api({ 'PATCH /api/organizer/events/9': { body: { ...DRAFT, venue: 'Галерея, Гданьск', version: 4 } } })
    organizer('/organizer/events/9/edit')
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })

    await user.type(await screen.findByLabelText('Место'), 'Галерея, Гданьск')
    await act(async () => { vi.advanceTimersByTime(AUTOSAVE_MS - 500) })
    expect(calls.some((call) => call.method === 'PATCH')).toBe(false)
    await act(async () => { vi.advanceTimersByTime(600) })

    await waitFor(() => expect(calls.find((call) => call.method === 'PATCH')?.body).toMatchObject({ version: 3, venue: 'Галерея, Гданьск' }))
    expect(await screen.findByText(/Черновик сохранён в/)).toBeInTheDocument()
  })

  it('409: warns, stops saving and does not overwrite until the data is reloaded', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    let loads = 0
    const calls = api({
      'GET /api/organizer/events/9': () => ({ body: loads++ === 0 ? DRAFT : { ...DRAFT, title: 'Изменено в другой вкладке', version: 5 } }),
      'PATCH /api/organizer/events/9': { status: 409, body: { status: 409, title: 'Stale version', code: 'STALE_VERSION' } },
    })
    organizer('/organizer/events/9/edit')
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })

    await user.type(await screen.findByLabelText('Место'), 'Холл')
    await act(async () => { vi.advanceTimersByTime(AUTOSAVE_MS + 100) })

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Событие изменили в другом окне')
    await user.type(screen.getByLabelText('Место'), ' 2')
    await act(async () => { vi.advanceTimersByTime(AUTOSAVE_MS * 3) })
    expect(calls.filter((call) => call.method === 'PATCH')).toHaveLength(1)

    await user.click(within(alert).getByRole('button', { name: 'Обновить данные' }))
    expect(await screen.findByDisplayValue('Изменено в другой вкладке')).toBeInTheDocument()
    expect(screen.queryByText(/Событие изменили/)).not.toBeInTheDocument()
  })

  it('publishing an incomplete draft lists the missing fields', async () => {
    const calls = api({
      'PUT /api/organizer/events/9/publish': { status: 422, body: { status: 422, errors: [
        { field: 'venue', message: 'is required to publish' },
        { field: 'price', message: 'is required to publish' },
        { field: 'media', message: 'at least one photo' },
      ] } },
    })
    organizer('/organizer/events/9/edit')

    await userEvent.click(await screen.findByRole('button', { name: 'Опубликовать' }))

    const missing = await screen.findByText('Чтобы опубликовать, заполните:')
    const box = missing.closest('[role=alert]')!
    expect(box).toHaveTextContent('Место')
    expect(box).toHaveTextContent('Цена')
    expect(box).toHaveTextContent('Фото')
    expect(screen.getByLabelText('Место')).toHaveAttribute('aria-invalid', 'true')
    expect(calls.some((call) => call.method === 'PATCH')).toBe(false)
  })
})
