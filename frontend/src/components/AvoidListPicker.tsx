import { asTree } from '../api/catalog'
import type { Allergen } from '../api/client'
import { coveringAncestor, toggleAllergen } from '../lib/avoidList'

/**
 * FR-3: pick allergens to avoid from the taxonomy. Choosing a group (Tree nuts) covers
 * every item in it; covered items show as checked and say what covers them.
 */
export function AvoidListPicker({
  idPrefix,
  allergens,
  selected,
  onChange,
}: {
  idPrefix: string
  allergens: Allergen[]
  selected: ReadonlySet<string>
  onChange: (next: Set<string>) => void
}) {
  const byId = new Map(allergens.map((a) => [a.id, a]))

  return (
    <ul className="avoid-list-picker">
      {asTree(allergens).map(({ node, depth }) => {
        const coveredBy = coveringAncestor(node.id, selected, byId)
        const inputId = `${idPrefix}-${node.id}`
        return (
          <li key={node.id} style={{ paddingLeft: `${depth * 1.5}rem` }}>
            <input
              id={inputId}
              type="checkbox"
              checked={selected.has(node.id) || coveredBy !== undefined}
              disabled={coveredBy !== undefined}
              onChange={() => onChange(toggleAllergen(node.id, selected, allergens))}
            />
            <label htmlFor={inputId}>
              {node.displayName}
              {coveredBy && <span className="hint"> (covered by {coveredBy.displayName})</span>}
            </label>
          </li>
        )
      })}
    </ul>
  )
}
