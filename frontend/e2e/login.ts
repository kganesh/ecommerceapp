import { expect, type Page } from '@playwright/test'

/** Signs in through the local Keycloak login page (test users from local/keycloak). */
export async function signInAs(page: Page, username: string, password = username) {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('#username').fill(username)
  await page.locator('#password').fill(password)
  await page.locator('#kc-login').click()
  await expect(page.getByText(`Signed in as ${username}`)).toBeVisible()
}
