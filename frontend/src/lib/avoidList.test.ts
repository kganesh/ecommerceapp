import { describe, expect, it } from 'vitest'
import type { Allergen } from '../api/client'
import { coveringAncestor, toggleAllergen } from './avoidList'

const allergens: Allergen[] = [
  { id: 'PEANUT', displayName: 'Peanut', synonyms: [] },
  { id: 'TREE_NUT', displayName: 'Tree nuts', synonyms: [] },
  { id: 'TREE_NUT.CASHEW', parentId: 'TREE_NUT', displayName: 'Cashew', synonyms: [] },
  { id: 'TREE_NUT.ALMOND', parentId: 'TREE_NUT', displayName: 'Almond', synonyms: [] },
]
const byId = new Map(allergens.map((a) => [a.id, a]))

describe('avoid list', () => {
  it('treats a child as covered when its group is selected', () => {
    expect(coveringAncestor('TREE_NUT.CASHEW', new Set(['TREE_NUT']), byId)?.id).toBe('TREE_NUT')
    expect(coveringAncestor('TREE_NUT.CASHEW', new Set(['PEANUT']), byId)).toBeUndefined()
  })

  it('peanut is never covered by tree nuts', () => {
    expect(coveringAncestor('PEANUT', new Set(['TREE_NUT']), byId)).toBeUndefined()
  })

  it('selecting a group drops the individual nuts it now covers', () => {
    const next = toggleAllergen('TREE_NUT', new Set(['TREE_NUT.CASHEW', 'PEANUT']), allergens)
    expect([...next].sort()).toEqual(['PEANUT', 'TREE_NUT'])
  })

  it('unselecting removes only that entry', () => {
    const next = toggleAllergen('PEANUT', new Set(['TREE_NUT', 'PEANUT']), allergens)
    expect([...next]).toEqual(['TREE_NUT'])
  })
})
