import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, fireEvent, screen } from '@testing-library/react'
import { CLOSE_DELAY_MS, OPEN_DELAY_MS, useHoverPreview } from './HoverPreview'
import type { EventItem } from '../../api/events'
import { mockApi, renderAt } from '../../test/render'

const EVENT: EventItem = {
  id: 9, title: 'Свет на мраморе', startsAt: new Date(Date.now() + 86_400_000).toISOString(), capacity: 20,
  bookedCount: 6, organizerId: 1, seatStatus: 'AVAILABLE', isFavorite: false, venue: 'Национальный музей',
  price: 45, coverUrl: '/api/media/9.png',
}

function Card() {
  const { trigger, overlay } = useHoverPreview(EVENT)
  return (
    <article data-testid="card" {...trigger}>
      <a href="/events/9">Свет на мраморе</a>
      {overlay}
    </article>
  )
}

/** fake timers, but let the mocked fetch promises settle */
async function advance(ms: number) {
  await act(async () => {
    vi.advanceTimersByTime(ms)
  })
}

let calls: ReturnType<typeof mockApi>

beforeEach(() => {
  vi.useFakeTimers()
  vi.stubGlobal('matchMedia', (query: string) => ({ matches: query === '(hover: hover)', media: query, addEventListener() {}, removeEventListener() {} }))
  calls = mockApi({
    'GET /api/events/9/preview': {
      body: { id: 9, shortDescription: 'Вечерний обход зала скульптур.', audienceFit: ['Любителям искусства'], audienceNotFit: ['Детям до 12 лет'], media: [{ url: '/api/media/statue.png', alt: 'Статуя' }] },
    },
    'GET /api/me': { status: 401 },
    'POST /api/auth/refresh': { status: 401 },
  })
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

const hover = () => fireEvent.pointerEnter(screen.getByTestId('card'), { pointerType: 'mouse' })

describe('hover preview (R-hover)', () => {
  it('opens exactly after 2000 ms and starts loading the preview right away', async () => {
    renderAt('/', <Card />)

    hover()
    await advance(0)
    expect(calls.some((call) => call.path === '/api/events/9/preview')).toBe(true)
    await advance(OPEN_DELAY_MS - 1)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await advance(1)

    const dialog = screen.getByRole('dialog', { name: 'Предпросмотр: Свет на мраморе' })
    expect(dialog).toHaveTextContent('Вечерний обход зала скульптур.')
    expect(dialog).toHaveTextContent('Любителям искусства')
    expect(dialog).toHaveTextContent('Детям до 12 лет')
    expect(screen.getByRole('img', { name: 'Статуя' })).toHaveClass('hover-preview-image')
    expect(screen.getByRole('link', { name: 'Открыть событие' })).toHaveAttribute('href', '/events/9')
    expect(screen.getByRole('button', { name: '♡ В избранное' })).toBeInTheDocument()
  })

  it('leaving the card early cancels the timer', async () => {
    renderAt('/', <Card />)

    hover()
    await advance(1500)
    fireEvent.pointerLeave(screen.getByTestId('card'), { pointerType: 'mouse' })
    await advance(5000)

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('leaving the preview closes it after 200 ms; coming back in time keeps it', async () => {
    renderAt('/', <Card />)
    hover()
    await advance(OPEN_DELAY_MS)
    const dialog = screen.getByRole('dialog')

    fireEvent.pointerLeave(dialog, { pointerType: 'mouse' })
    await advance(CLOSE_DELAY_MS - 50)
    fireEvent.pointerEnter(dialog, { pointerType: 'mouse' })
    await advance(1000)
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    fireEvent.pointerLeave(dialog, { pointerType: 'mouse' })
    await advance(CLOSE_DELAY_MS)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('keyboard: focus opens it after the same delay, Escape closes it and returns the focus', async () => {
    renderAt('/', <Card />)
    const link = screen.getByRole('link', { name: 'Свет на мраморе' })

    act(() => link.focus())
    await advance(OPEN_DELAY_MS)
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Открыть событие' })).toHaveFocus()

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(link).toHaveFocus()
    await advance(OPEN_DELAY_MS * 2)            // the returned focus must not open it again
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('never opens from a touch', async () => {
    renderAt('/', <Card />)

    fireEvent.pointerEnter(screen.getByTestId('card'), { pointerType: 'touch' })
    await advance(5000)

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('never opens on a device without hover', async () => {
    vi.stubGlobal('matchMedia', (query: string) => ({ matches: false, media: query, addEventListener() {}, removeEventListener() {} }))
    renderAt('/', <Card />)

    hover()
    await advance(5000)

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
