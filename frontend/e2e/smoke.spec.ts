import { expect, test } from '@playwright/test'

// M0 local gate: the SPA loads and reaches the backend through the proxy.
test('shell shows the backend as connected', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Nut-Allergy Safe Shopping')
  const status = page.getByRole('status')
  await expect(status).toContainText('Backend connected')
  await expect(status).toContainText('ecommerceapp')
})
