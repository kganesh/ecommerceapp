import type { Allergen } from '../api/client'

/** The selected ancestor that already covers this allergen, if any (TREE_NUT covers TREE_NUT.CASHEW). */
export function coveringAncestor(
  id: string,
  selected: ReadonlySet<string>,
  byId: ReadonlyMap<string, Allergen>,
): Allergen | undefined {
  let parentId = byId.get(id)?.parentId
  while (parentId) {
    if (selected.has(parentId)) return byId.get(parentId)
    parentId = byId.get(parentId)?.parentId
  }
  return undefined
}

/**
 * Toggles an allergen. Selecting a parent drops its now-redundant descendants, so the
 * saved list says what the parent chose ("Tree nuts"), not a long list of nuts.
 */
export function toggleAllergen(
  id: string,
  selected: ReadonlySet<string>,
  allergens: readonly Allergen[],
): Set<string> {
  const next = new Set(selected)
  if (next.has(id)) {
    next.delete(id)
    return next
  }
  next.add(id)
  const byId = new Map(allergens.map((a) => [a.id, a]))
  for (const other of allergens) {
    if (other.id !== id && next.has(other.id) && coveringAncestor(other.id, new Set([id]), byId)) {
      next.delete(other.id)
    }
  }
  return next
}
