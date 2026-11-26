import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end tests against a running stack (docker compose up: frontend on :8090 with the backend behind it).
 * Locally the installed Microsoft Edge is used (no browser download); CI sets E2E_BROWSER=chromium.
 */
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:8090'
const chromium = process.env.E2E_BROWSER === 'chromium'

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]] : 'list',
  outputDir: 'e2e-results',
  use: {
    baseURL,
    locale: 'ru-RU',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: chromium ? 'chromium' : 'edge',
      use: chromium ? { ...devices['Desktop Chrome'] } : { ...devices['Desktop Edge'], channel: 'msedge' },
    },
  ],
})
