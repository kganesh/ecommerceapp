import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { signInAs } from './login.ts'

// M5 accessibility scan (spec: Local testing > M5; NFR-9): WCAG 2.2 A/AA rules on every
// main screen, for visitors, a signed-in parent and a catalog admin. Needs `make seed`.

async function expectNoViolations(page: Page, name: string) {
  await page.waitForLoadState('networkidle')
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
    .analyze()
  const summary = results.violations.map(
    (v) => `${v.id} (${v.impact}): ${v.help} -> ${v.nodes.map((n) => n.target.join(' ')).slice(0, 3).join(', ')}`,
  )
  expect(summary, `${name} has accessibility violations`).toEqual([])
}

test('visitor screens', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  await expectNoViolations(page, 'shop')

  await page.goto('/products/00000000-0000-4000-8000-000000000001')
  await expect(page.getByRole('heading', { name: 'Cashew Butter' })).toBeVisible()
  await expectNoViolations(page, 'product')
})

test('parent screens, including an allergen warning', async ({ page }) => {
  await signInAs(page, 'parent1')
  for (const [name, path] of [
    ['shop', '/'],
    ['children', '/profiles'],
    ['cart', '/cart'],
    ['orders', '/orders'],
  ]) {
    await page.goto(path)
    await expectNoViolations(page, name)
  }

  await page.goto('/?q=Stir-Fry')
  const card = page.getByRole('listitem').filter({ hasText: 'Stir-Fry Sauce' })
  await card.getByRole('button', { name: 'Add to cart' }).click()
  await expect(page.getByRole('alertdialog')).toBeVisible()
  await expectNoViolations(page, 'warning panel')
  await page.getByRole('button', { name: "Don't add" }).click()
})

test('catalog admin screens', async ({ page }) => {
  await signInAs(page, 'admin1')
  await page.goto('/admin/products')
  await expectNoViolations(page, 'admin products')
  await page.goto('/admin/products/00000000-0000-4000-8000-000000000003')
  await expect(page.getByRole('heading', { name: 'Stir-Fry Sauce' })).toBeVisible()
  await expectNoViolations(page, 'product editor')
})
