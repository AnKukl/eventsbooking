import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { Header } from './Header'

function Location() {
  const location = useLocation()
  return <div data-testid="location">{location.pathname + location.search}</div>
}

function renderHeader() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Header />
      <Routes>
        <Route path="*" element={<Location />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('Header', () => {
  it('shows the logo, the search and the cabinet button', () => {
    renderHeader()

    expect(screen.getByRole('img', { name: 'Events Booking' })).toBeInTheDocument()
    expect(screen.getByRole('searchbox', { name: 'Поиск мероприятий' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Кабинет' })).toHaveAttribute('href', '/organizer')
  })

  it('draws the logo as a 40x40 mark with a four-part letter E', () => {
    renderHeader()

    const logo = screen.getByRole('img', { name: 'Events Booking' })
    expect(logo).toHaveAttribute('width', '40')
    expect(logo).toHaveAttribute('height', '40')
    expect(logo.querySelectorAll('g > rect')).toHaveLength(4)
  })

  it('searching goes to the catalogue with the trimmed query', async () => {
    renderHeader()

    await userEvent.type(screen.getByRole('searchbox'), '  jazz night {enter}')

    expect(screen.getByTestId('location')).toHaveTextContent('/?q=jazz%20night')
  })
})
