import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuthPage, target } from './AuthPage'
import { QUOTES } from './quotes'
import { mockApi, renderAt } from '../../test/render'

afterEach(() => vi.unstubAllGlobals())

async function fill(email: string, password: string) {
  await userEvent.type(screen.getByLabelText('Email'), email)
  await userEvent.type(screen.getByLabelText('Пароль'), password)
}

describe('AuthPage (S-auth)', () => {
  it('shows the landscape, a quote from the approved list and the sign-in form', () => {
    mockApi({})
    renderAt('/login', <AuthPage mode="login" />)

    expect(screen.getByRole('img', { name: 'Гора Фудзи, озеро и цветущая сакура' })).toBeInTheDocument()
    const note = screen.getByRole('note')
    expect(QUOTES.some((quote) => note.textContent?.includes(quote.author))).toBe(true)
    expect(note.textContent).not.toContain('Роден')
    expect(screen.getByRole('heading', { name: 'С возвращением' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Вход' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('button', { name: 'Войти' })).toBeInTheDocument()
  })

  it('has no controls the API cannot serve', () => {
    mockApi({})
    renderAt('/login', <AuthPage mode="login" />)

    expect(screen.queryByText('Запомнить меня')).not.toBeInTheDocument()
    expect(screen.queryByText('Забыли пароль?')).not.toBeInTheDocument()
    expect(screen.queryByText(/Google/)).not.toBeInTheDocument()
  })

  it('signs in and returns to the page the guest came from', async () => {
    const calls = mockApi({ 'POST /api/auth/login': { body: { expiresIn: 900 } } })
    renderAt('/login', <AuthPage mode="login" />, { pathname: '/login', state: { from: '/account/favorites' } })

    await fill('anna@example.com', 'correct-horse')
    await userEvent.click(screen.getByRole('button', { name: 'Войти' }))

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/account/favorites'))
    expect(calls).toContainEqual({
      method: 'POST', path: '/api/auth/login', body: { email: 'anna@example.com', password: 'correct-horse' },
    })
  })

  it('signs in and follows ?returnTo', async () => {
    mockApi({ 'POST /api/auth/login': { body: { expiresIn: 900 } } })
    renderAt('/login', <AuthPage mode="login" />, '/login?returnTo=%2Fevents%2F5')

    await fill('anna@example.com', 'correct-horse')
    await userEvent.click(screen.getByRole('button', { name: 'Войти' }))

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/events/5'))
  })

  it('returnTo only accepts a path of this site', () => {
    expect(target('?returnTo=%2Faccount', null)).toBe('/account')
    expect(target('?returnTo=https%3A%2F%2Fevil.example', null)).toBe('/')
    expect(target('?returnTo=%2F%2Fevil.example', null)).toBe('/')
    expect(target('?returnTo=%2F%5Cevil.example', null)).toBe('/')
    expect(target('', { from: '/map' })).toBe('/map')
  })

  it('login checks the email and the 8-character password before sending', async () => {
    const calls = mockApi({})
    renderAt('/login', <AuthPage mode="login" />)

    await fill('anna', 'short')
    await userEvent.click(screen.getByRole('button', { name: 'Войти' }))

    expect(screen.getByText('Проверьте email')).toBeInTheDocument()
    expect(screen.getByText('Пароль не короче 8 символов')).toBeInTheDocument()
    expect(calls).toEqual([])
  })

  it('nothing about the session is kept in the browser storage: the cookie is HttpOnly', async () => {
    localStorage.clear()
    sessionStorage.clear()
    mockApi({ 'POST /api/auth/login': { body: { expiresIn: 900 } } })
    renderAt('/login', <AuthPage mode="login" />)

    await fill('anna@example.com', 'correct-horse')
    await userEvent.click(screen.getByRole('button', { name: 'Войти' }))

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/'))
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })

  it('a wrong password is reported under the fields', async () => {
    mockApi({ 'POST /api/auth/login': { status: 401, body: { status: 401, detail: 'Invalid email or password' } } })
    renderAt('/login', <AuthPage mode="login" />)

    await fill('anna@example.com', 'wrong-password')
    await userEvent.click(screen.getByRole('button', { name: 'Войти' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Неверный email или пароль')
  })

  it('too many attempts tell how long to wait', async () => {
    mockApi({ 'POST /api/auth/login': { status: 429, body: { status: 429 }, headers: { 'Retry-After': '42' } } })
    renderAt('/login', <AuthPage mode="login" />)

    await fill('anna@example.com', 'whatever-123')
    await userEvent.click(screen.getByRole('button', { name: 'Войти' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('через 42 с')
  })

  it('registration checks the fields before sending anything', async () => {
    const calls = mockApi({})
    renderAt('/register', <AuthPage mode="register" />)

    await fill('not-an-email', 'short')
    await userEvent.click(screen.getByRole('button', { name: 'Зарегистрироваться' }))

    expect(screen.getByText('Проверьте email')).toBeInTheDocument()
    expect(screen.getByText('Пароль не короче 8 символов')).toBeInTheDocument()
    expect(calls).toEqual([])
  })

  it('registration signs the new user in and goes home', async () => {
    const calls = mockApi({
      'POST /api/auth/register': { status: 201, body: { id: 1, email: 'new@example.com', role: 'USER' } },
      'POST /api/auth/login': { body: { expiresIn: 900 } },
    })
    renderAt('/register', <AuthPage mode="register" />)

    await fill('new@example.com', 'long-enough-password')
    await userEvent.click(screen.getByRole('button', { name: 'Зарегистрироваться' }))

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/))
    expect(calls.map((call) => `${call.method} ${call.path}`)).toEqual(['POST /api/auth/register', 'POST /api/auth/login'])
  })

  it('a taken email is explained', async () => {
    mockApi({ 'POST /api/auth/register': { status: 409, body: { status: 409, title: 'Email already registered' } } })
    renderAt('/register', <AuthPage mode="register" />)

    await fill('taken@example.com', 'long-enough-password')
    await userEvent.click(screen.getByRole('button', { name: 'Зарегистрироваться' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('уже зарегистрирован')
  })
})
