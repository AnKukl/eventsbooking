import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { Header } from './Header'

function Location() {
  const location = useLocation()
  return <div data-testid="location">{location.pathname + location.search}</div>
}

function renderHeader(initial?: string) {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Header initial={initial} />
      <Routes>
        <Route path="*" element={<Location />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('Header', () => {
  it('renders the logo with the wordmark, the search and the cabinet button', () => {
    renderHeader()

    const home = screen.getByRole('link', { name: 'Events Booking, на главную' })
    expect(home).toHaveAttribute('href', '/')
    expect(home.querySelector('svg')).toHaveAttribute('width', '40')
    expect(home).toHaveTextContent('Events Booking')
    expect(screen.getByRole('searchbox', { name: 'Поиск мероприятий' }))
      .toHaveAttribute('placeholder', 'Поиск: события, даты, настроение')
    expect(screen.getByRole('link', { name: /Кабинет/ })).toHaveAttribute('href', '/account')
  })

  it('shows the initial of the signed-in user in the avatar', () => {
    renderHeader('анна')

    expect(screen.getByRole('link', { name: /Кабинет/ })).toHaveTextContent('АКабинет')
  })

  it('Enter in the search opens /search with the trimmed query', async () => {
    renderHeader()

    await userEvent.type(screen.getByRole('searchbox'), '  jazz night {enter}')

    expect(screen.getByTestId('location')).toHaveTextContent('/search?q=jazz%20night')
  })

  it('an empty search goes nowhere', async () => {
    renderHeader()

    await userEvent.type(screen.getByRole('searchbox'), '   {enter}')

    expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/)
  })
})
