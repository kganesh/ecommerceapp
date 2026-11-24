import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import App from '../App'
import { renderWithProviders, stubFetch } from '../test/render'

const allergens = [
  { id: 'PEANUT', displayName: 'Peanut', synonyms: ['arachis oil'] },
  { id: 'TREE_NUT', displayName: 'Tree nuts', synonyms: [] },
  { id: 'TREE_NUT.CASHEW', parentId: 'TREE_NUT', displayName: 'Cashew', synonyms: [] },
]

const shopper = {
  authenticated: true,
  username: 'parent1',
  roles: ['SHOPPER'],
  loginUrl: '/oauth2/authorization/idp',
}

describe('ProfilesPage', () => {
  it('asks a signed-out visitor to sign in', async () => {
    stubFetch({
      '/api/v1/me': { status: 200, body: { authenticated: false, roles: [], loginUrl: '/oauth2/authorization/idp' } },
      '/api/v1/allergens': { status: 200, body: allergens },
    })
    renderWithProviders(<App />, { route: '/profiles' })
    expect(await screen.findByText(/Sign in to set up each child's avoid list/)).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'My children' })).not.toBeInTheDocument()
  })

  it("shows each child's avoid list, with covered nuts marked, and saves changes", async () => {
    const fetchMock = stubFetch({
      '/api/v1/me': { status: 200, body: shopper },
      '/api/v1/allergens': { status: 200, body: allergens },
      'GET /api/v1/profiles': {
        status: 200,
        body: [
          {
            id: 'p1',
            label: 'Child 1',
            avoidList: ['TREE_NUT'],
            coveredAllergenIds: ['TREE_NUT', 'TREE_NUT.CASHEW'],
            avoidListVersion: 1,
          },
        ],
      },
      'PUT /api/v1/profiles/p1/avoid-list': {
        status: 200,
        body: {
          id: 'p1',
          label: 'Child 1',
          avoidList: ['PEANUT', 'TREE_NUT'],
          coveredAllergenIds: ['PEANUT', 'TREE_NUT', 'TREE_NUT.CASHEW'],
          avoidListVersion: 2,
        },
      },
    })
    renderWithProviders(<App />, { route: '/profiles' })

    expect(await screen.findByText('Avoiding: Tree nuts.')).toBeInTheDocument()
    const picker = screen.getByRole('group', { name: 'Allergens to avoid for Child 1' })
    const cashew = within(picker).getByLabelText(/Cashew/)
    expect(cashew).toBeChecked()
    expect(cashew).toBeDisabled()
    expect(within(picker).getByText('(covered by Tree nuts)')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'My children' })).toBeInTheDocument()

    await userEvent.click(within(picker).getByLabelText('Peanut'))
    await userEvent.click(screen.getByRole('button', { name: 'Save avoid list' }))

    const put = fetchMock.mock.calls
      .map(([input]) => input as Request)
      .find((r) => r.method === 'PUT' && r.url.endsWith('/api/v1/profiles/p1/avoid-list'))
    expect(put).toBeDefined()
    expect(await put!.clone().json()).toEqual({ allergenIds: ['PEANUT', 'TREE_NUT'] })
  })
})
