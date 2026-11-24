import { expect, test } from '@playwright/test'
import { signInAs } from './login.ts'

// Local checks for M1 and M2 (spec: "Local testing" > "Local check per milestone").
// Needs the local stack running and `make seed` applied.

test('M2: parent1 signs in, sees both children, and no token reaches the browser', async ({ page, context }) => {
  await signInAs(page, 'parent1')

  const cookies = await context.cookies()
  const names = cookies.map((c) => c.name)
  expect(names).toContain('SESSION')
  expect(cookies.find((c) => c.name === 'SESSION')?.httpOnly).toBe(true)
  const storage = await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))
  expect(storage).not.toMatch(/eyJ[A-Za-z0-9_-]+\./) // no JWTs in web storage

  await page.getByRole('link', { name: 'My children' }).click()
  await expect(page.getByText('Avoiding: Tree nuts.')).toBeVisible()
  await expect(page.getByText('Avoiding: Peanut.')).toBeVisible()

  const child1 = page.getByRole('group', { name: 'Allergens to avoid for Child 1' })
  await expect(child1.getByLabel(/Cashew/)).toBeChecked()
  await expect(child1.getByText('(covered by Tree nuts)').first()).toBeVisible()
})

test('M2: an avoid-list change is visible on the next read', async ({ page }) => {
  await signInAs(page, 'parent1')
  await page.getByRole('link', { name: 'My children' }).click()

  const label = `E2E ${Date.now()}`
  await page.getByLabel('New profile label').fill(label)
  await page.getByRole('button', { name: 'Add profile' }).click()
  const picker = page.getByRole('group', { name: `Allergens to avoid for ${label}` })
  await picker.getByLabel('Sesame').check()
  await page.getByRole('form', { name: `Avoid list for ${label}` }).getByRole('button', { name: 'Save avoid list' }).click()

  await page.reload()
  // parent1 is shared by every run, so look inside this test's own profile.
  await expect(page.getByRole('listitem').filter({ hasText: label }).getByText('Avoiding: Sesame.')).toBeVisible()
})

test('M1: admin1 creates a product and a new allergen version', async ({ page }) => {
  await signInAs(page, 'admin1')
  await page.getByRole('link', { name: 'Catalog admin' }).click()
  await page.getByRole('link', { name: 'Add a product' }).click()
  await expect(page.getByRole('heading', { name: 'Add a product' })).toBeVisible()

  const sku = `E2E-${Date.now()}`
  await page.getByLabel('SKU', { exact: true }).fill(sku)
  await page.getByLabel('Name').fill(`Pistachio Cookies ${sku}`)
  await page.getByLabel('Price (USD)').fill('3.99')
  await page.getByLabel('Pistachio').selectOption('CONTAINS')
  await page.getByRole('button', { name: 'Create product' }).click()

  await expect(page.getByText('Allergen version 1 is live.')).toBeVisible()
  await page.getByLabel('Peanut', { exact: true }).selectOption('MAY_CONTAIN')
  await page.getByRole('button', { name: 'Save allergens as new version' }).click()
  await expect(page.getByText('Allergen version 2 is live.')).toBeVisible()
  await expect(page.getByText('Allergens: version 1 → 2')).toBeVisible()
})

test('M1: shoppers do not see catalog admin', async ({ page }) => {
  await signInAs(page, 'parent2')
  await expect(page.getByRole('link', { name: 'Catalog admin' })).toHaveCount(0)
})
