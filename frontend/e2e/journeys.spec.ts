import { expect, test } from '@playwright/test'
import { PASSWORD, publishedEvent, uniqueEmail } from './helpers'

/** The main journeys of a visitor, in one browser session: register, favourite, book, cancel. */
test.describe.configure({ mode: 'serial' })

let eventId: number
const title = `E2E вечер джаза ${Date.now()}`

test.beforeAll(async ({ baseURL }) => {
  // five API calls with two password hashes: a busy laptop needs more than one test's minute
  test.setTimeout(180_000)
  eventId = await publishedEvent(baseURL!, title)
})

test('registration signs the new user in', async ({ page }) => {
  await page.goto('/register')
  await page.getByLabel('Email').fill(uniqueEmail('e2e-user'))
  await page.getByLabel('Пароль').fill(PASSWORD)
  await page.getByRole('button', { name: 'Зарегистрироваться' }).click()

  await expect(page).toHaveURL(/\/$/)
  await page.getByRole('link', { name: /Кабинет/ }).click()
  await expect(page).toHaveURL(/\/account\/bookings$/)
  await expect(page.getByRole('navigation', { name: 'Кабинет' })).toBeVisible()
  // the session lives in HttpOnly cookies only
  expect(await page.evaluate(() => localStorage.length)).toBe(0)
})

test.describe('signed in', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/register')
    await page.getByLabel('Email').fill(uniqueEmail('e2e-user'))
    await page.getByLabel('Пароль').fill(PASSWORD)
    await page.getByRole('button', { name: 'Зарегистрироваться' }).click()
    await expect(page).toHaveURL(/\/$/)
  })

  test('favourite: the heart, the toast and the favourites tab', async ({ page }) => {
    await page.goto(`/events/${eventId}`)
    await page.getByRole('button', { name: 'Добавить в избранное' }).click()

    await expect(page.getByRole('status')).toContainText('Сохранено в избранное')
    await expect(page.getByRole('button', { name: 'Убрать из избранного' })).toHaveAttribute('aria-pressed', 'true')
    await page.goto('/account/favorites')
    await expect(page.getByRole('link', { name: title })).toBeVisible()
  })

  test('booking and cancellation', async ({ page }) => {
    await page.goto(`/events/${eventId}/book`)
    await page.getByRole('button', { name: 'Больше мест' }).click()
    await page.getByRole('button', { name: 'Перейти к оплате · 90 PLN' }).click()

    await expect(page.getByRole('timer', { name: 'Места удержаны' })).toBeVisible()
    await page.getByRole('button', { name: 'Подтвердить и оплатить · 90 PLN' }).click()
    await expect(page.getByText('Места ваши')).toBeVisible()

    await page.getByRole('link', { name: 'Мои брони' }).click()
    await expect(page.getByRole('heading', { name: /Мои брони|Привет/ })).toBeVisible()
    await page.getByRole('button', { name: 'Отменить' }).first().click()
    const dialog = page.getByRole('dialog', { name: 'Отменить бронь?' })
    await expect(dialog).toContainText('90 PLN')
    await dialog.getByRole('button', { name: 'Отменить' }).click()

    await expect(page.getByRole('status')).toContainText('Бронь отменена')
    await expect(page.getByText('Возврат оформляется')).toBeVisible()
  })

  test('logout closes the cabinet, the back button does not reopen it', async ({ page }) => {
    await page.goto('/search')
    await page.goto('/account/profile')
    await page.getByRole('button', { name: 'Выйти' }).click()
    await page.getByRole('dialog', { name: 'Выйти из аккаунта?' }).getByRole('button', { name: 'Выйти' }).click()
    await expect(page).toHaveURL(/\/$/)

    // logout replaced the cabinet in the history: Back goes to the page before it, the cabinet stays closed
    await page.goBack()
    await expect(page).toHaveURL(/\/search$/)
    await page.goto('/account/profile')
    await expect(page).toHaveURL(/\/login\?returnTo=/)
  })
})
