import { expect, test, type Page } from '@playwright/test'
import { signInAs } from './login.ts'

// M4 local check (spec: "Local testing"): needs the local stack and `make seed`.
// parent1: Child 1 avoids TREE_NUT, Child 2 avoids PEANUT.

async function emptyCart(page: Page) {
  await page.goto('/cart')
  await page.waitForLoadState('networkidle') // count Remove buttons only once the cart has loaded
  const remove = page.getByRole('button', { name: 'Remove' })
  while ((await remove.count()) > 0) {
    await remove.first().click()
    await page.waitForTimeout(200)
  }
}

async function addFromSearch(page: Page, name: string, confirm: boolean) {
  await page.goto(`/?q=${encodeURIComponent(name)}`)
  const card = page.getByRole('listitem').filter({ hasText: name })
  await card.getByRole('button', { name: 'Add to cart' }).click()
  if (confirm) await card.getByRole('button', { name: 'I understand, add anyway' }).click()
  await expect(card.getByText('Added.')).toBeVisible()
}

test('M4: purchase with a confirmed warning; the order records what was confirmed', async ({ page }) => {
  await signInAs(page, 'parent1')
  await emptyCart(page)
  await addFromSearch(page, 'Plain Rice Cakes', false)
  await addFromSearch(page, 'Almond Granola Bars', true)

  await page.goto('/checkout')
  await page.getByRole('button', { name: 'Place order' }).click()
  await expect(page.getByText('Order placed. Thank you!')).toBeVisible()
  await expect(page.getByText('You confirmed this when adding to cart')).toBeVisible()
  await expect(page.getByText('Child 1: contains Almond')).toBeVisible()

  await page.getByRole('link', { name: 'Orders', exact: true }).click()
  await expect(page.getByRole('cell', { name: '1 confirmed' }).first()).toBeVisible()
})

test('M4: a declined test card places no order and keeps the cart', async ({ page }) => {
  await signInAs(page, 'parent1')
  await emptyCart(page)
  await addFromSearch(page, 'Plain Rice Cakes', false)
  await page.goto('/checkout')
  await page.getByLabel('Test card (declines)').check()
  await page.getByRole('button', { name: 'Place order' }).click()
  await expect(page.getByRole('alert')).toContainText('Your payment was declined. No order was placed')
  await page.goto('/cart')
  await expect(page.getByText('Plain Rice Cakes')).toBeVisible()
})

test('M4: an avoid-list change after adding is caught at checkout (FR-8, FR-9)', async ({ page }) => {
  await signInAs(page, 'parent1')
  await emptyCart(page)
  // Stir-Fry Sauce contains peanut (Child 2) and soy; confirm the peanut warning when adding.
  await addFromSearch(page, 'Stir-Fry Sauce', true)

  // A new child who avoids soy: the earlier confirmation no longer covers the cart.
  await page.getByRole('link', { name: 'My children' }).click()
  const label = `E2E ${Date.now()}`
  await page.getByLabel('New profile label').fill(label)
  await page.getByRole('button', { name: 'Add profile' }).click()
  await page.getByRole('group', { name: `Allergens to avoid for ${label}` }).getByLabel('Soy').check()
  await page.getByRole('form', { name: `Avoid list for ${label}` }).getByRole('button', { name: 'Save avoid list' }).click()
  await expect(page.getByText('Avoiding: Soy.')).toBeVisible()

  await page.goto('/checkout')
  await page.getByRole('button', { name: 'Place order' }).click()
  const panel = page.getByRole('alertdialog', { name: /Please confirm before we place your order/ })
  await expect(panel).toContainText(`${label}: contains Soy`)
  await expect(panel).toContainText('Nothing has been charged.')
  await panel.getByRole('button', { name: 'I understand, place the order' }).click()

  await expect(page.getByText('Order placed. Thank you!')).toBeVisible()
  await expect(page.getByText('You confirmed this at checkout')).toBeVisible()

  // Clean up so later runs start from the seeded two children.
  await page.getByRole('link', { name: 'My children' }).click()
  await page.getByText('Rename or delete').last().click()
  await page.getByRole('button', { name: `Delete ${label}` }).click()
  await page.getByRole('button', { name: 'Yes, delete' }).click()
})
