import type { ProductSafety, SafetyMatch } from '../api/cart'

const MATCH_WORDS = { CONTAINS: 'contains', MAY_CONTAIN: 'may contain' } as const

/** "Child 1: contains Cashew" — every match in words, grouped by child (FR-10). */
export function MatchList({ matches }: { matches: SafetyMatch[] }) {
  const byChild = new Map<string, { label: string; items: string[] }>()
  for (const m of matches) {
    const entry = byChild.get(m.profileId) ?? { label: m.profileLabel, items: [] }
    entry.items.push(`${MATCH_WORDS[m.matchType]} ${m.allergenName}`)
    byChild.set(m.profileId, entry)
  }
  return (
    <ul className="match-list">
      {[...byChild.values()].map((child) => (
        <li key={child.label}>
          <strong>{child.label}:</strong> {child.items.join(', ')}
        </li>
      ))}
    </ul>
  )
}

/**
 * The allergen check result for the signed-in parent's children, on a product card (FR-5).
 * CLEAR never says "safe": it says no avoid list matched, and the label is still the final word.
 */
export function SafetyNotice({ safety }: { safety: ProductSafety | undefined }) {
  if (!safety) return null
  switch (safety.status) {
    case 'CLEAR':
      return <p className="safety clear">No match with your children's avoid lists.</p>
    case 'WARNING':
      return (
        <div className="safety warning" role="note">
          <p>
            <span aria-hidden="true">⚠ </span>
            <strong>Allergen warning</strong>
          </p>
          <MatchList matches={safety.matches} />
        </div>
      )
    case 'UNVERIFIED':
      return (
        <p className="safety warning" role="note">
          <span aria-hidden="true">⚠ </span>
          <strong>Unable to verify allergens</strong> for {safety.affectedProfiles.map((p) => p.label).join(', ')}.
        </p>
      )
    case 'UNAVAILABLE':
      return (
        <p className="safety warning" role="note">
          <span aria-hidden="true">⚠ </span>
          <strong>Couldn't check allergens right now.</strong> Treat as unverified.
        </p>
      )
  }
}
