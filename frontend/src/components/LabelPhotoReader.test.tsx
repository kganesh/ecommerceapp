import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderWithProviders, stubFetch } from '../test/render'
import { LabelPhotoReader } from './LabelPhotoReader'

const enabled = { enabled: true, model: 'us.anthropic.claude-sonnet-5-5' }

const reading = {
  extractionId: 'ex-1',
  model: 'us.anthropic.claude-sonnet-5-5',
  readable: true,
  ingredientsText: 'Oats, cashews, sugar',
  mayContainStatement: 'May contain peanuts',
  allergens: [
    { allergenId: 'PEANUT', displayName: 'Peanut', matchType: 'MAY_CONTAIN', source: 'MODEL', evidence: 'May contain peanuts' },
    { allergenId: 'TREE_NUT.CASHEW', displayName: 'Cashew', matchType: 'CONTAINS', source: 'MODEL_AND_KEYWORD_SCAN', evidence: 'cashews' },
  ],
  unmatchedTerms: ['lupin'],
}

const photo = () => new File([new Uint8Array([0xff, 0xd8, 0xff])], 'label.jpg', { type: 'image/jpeg' })

describe('LabelPhotoReader (FR-16 to FR-20)', () => {
  it('is hidden when label reading is off', async () => {
    const fetchMock = stubFetch({
      '/api/v1/admin/label-extractions/status': { status: 200, body: { enabled: false, model: 'disabled' } },
    })
    const { container } = renderWithProviders(<LabelPhotoReader onApply={vi.fn()} />)
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled())
    expect(container).toBeEmptyDOMElement()
  })

  it('shows suggestions with their evidence and applies them only when asked', async () => {
    stubFetch({
      '/api/v1/admin/label-extractions/status': { status: 200, body: enabled },
      'POST /api/v1/admin/label-extractions': { status: 201, body: reading },
    })
    const onApply = vi.fn()
    renderWithProviders(<LabelPhotoReader onApply={onApply} />)

    await userEvent.upload(await screen.findByLabelText('Label photo'), photo())

    const review = await screen.findByLabelText('Label reading')
    expect(review).toHaveTextContent('Oats, cashews, sugar')
    expect(review).toHaveTextContent('Cashew')
    expect(review).toHaveTextContent('“cashews”')
    expect(review).toHaveTextContent('AI and keyword check agree')
    expect(review).toHaveTextContent('not in our allergen list: lupin')
    expect(onApply).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: 'Use these in the allergen editor' }))
    expect(onApply).toHaveBeenCalledWith({ PEANUT: 'MAY_CONTAIN', 'TREE_NUT.CASHEW': 'CONTAINS' }, 'ex-1')
    expect(await screen.findByRole('status')).toHaveTextContent('Check them against the label, then save.')
  })

  it('says when a photo cannot be read and offers nothing to apply', async () => {
    stubFetch({
      '/api/v1/admin/label-extractions/status': { status: 200, body: enabled },
      'POST /api/v1/admin/label-extractions': {
        status: 201,
        body: { ...reading, readable: false, allergens: [], unmatchedTerms: [], notes: 'Ingredients are cut off' },
      },
    })
    renderWithProviders(<LabelPhotoReader onApply={vi.fn()} />)
    await userEvent.upload(await screen.findByLabelText('Label photo'), photo())

    expect(await screen.findByText(/couldn’t be read: Ingredients are cut off/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Use these in the allergen editor' })).not.toBeInTheDocument()
  })

  it('explains an AI outage so the admin can enter allergens by hand', async () => {
    stubFetch({
      '/api/v1/admin/label-extractions/status': { status: 200, body: enabled },
      'POST /api/v1/admin/label-extractions': {
        status: 503,
        body: { code: 'AI_UNAVAILABLE', detail: 'Label reading is unavailable right now. Enter the allergens from the label by hand, or try again.' },
      },
    })
    renderWithProviders(<LabelPhotoReader onApply={vi.fn()} />)
    await userEvent.upload(await screen.findByLabelText('Label photo'), photo())
    expect(await screen.findByText(/Enter the allergens from the label by hand/)).toBeInTheDocument()
  })
})
