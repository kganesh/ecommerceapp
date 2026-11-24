import { expect, test } from '@playwright/test'
import { signInAs } from './login.ts'

// M3 local check (spec: "Local testing"): needs the local stack and `make seed`.
// parent1 has Child 1 avoiding TREE_NUT and Child 2 avoiding PEANUT.

test('M3: cashew warns for Child 1, nothing is added until confirmed, then it is', async ({ page }) => {
  await signInAs(page, 'parent1')
  await page.getByRole('searchbox', { name: 'Search products' }).fill('Cashew Butter')
  await page.getByRole('button', { name: 'Search' }).click()

  const card = page.getByRole('listitem').filter({ hasText: 'Cashew Butter' })
  await expect(card.getByText('Child 1: contains Cashew')).toBeVisible()
  await card.getByRole('button', { name: 'Add to cart' }).click()

  const panel = card.getByRole('alertdialog')
  await expect(panel).toContainText('Child 1: contains Cashew')
  await expect(panel).toContainText('It has not been added to your cart.')
  await panel.getByRole('button', { name: 'I understand, add anyway' }).click()
  await expect(card.getByText('Added.')).toBeVisible()

  await page.getByRole('link', { name: 'Cart' }).click()
  const line = page.getByRole('listitem').filter({ hasText: 'Cashew Butter' })
  await expect(line).toContainText('you confirmed this')
})

test('M3: may-contain peanut warns for Child 2', async ({ page }) => {
  await signInAs(page, 'parent1')
  await page.getByRole('searchbox', { name: 'Search products' }).fill('Chocolate Chip')
  await page.getByRole('button', { name: 'Search' }).click()
  const card = page.getByRole('listitem').filter({ hasText: 'Chocolate Chip Cookies' })
  await card.getByRole('button', { name: 'Add to cart' }).click()
  await expect(card.getByRole('alertdialog')).toContainText('Child 2: may contain Peanut')
  await card.getByRole('button', { name: "Don't add" }).click()
  await expect(card.getByRole('alertdialog')).toHaveCount(0)
})

test('M3: product without allergen data is "unable to verify"', async ({ page }) => {
  await signInAs(page, 'parent1')
  await page.getByRole('searchbox', { name: 'Search products' }).fill('Trail Mix Surprise')
  await page.getByRole('button', { name: 'Search' }).click()
  const card = page.getByRole('listitem').filter({ hasText: 'Trail Mix Surprise' })
  await card.getByRole('button', { name: 'Add to cart' }).click()
  await expect(card.getByRole('alertdialog', { name: /Unable to verify allergens/ })).toBeVisible()
})

test('M3: hide unsafe leaves only products with no match', async ({ page }) => {
  await signInAs(page, 'parent1')
  await page.getByLabel(/Hide products that match/).check()
  await expect(page.getByText('Plain Rice Cakes')).toBeVisible()
  await expect(page.getByText('Cashew Butter')).toHaveCount(0)
  await expect(page.getByText('Trail Mix Surprise')).toHaveCount(0)
})
