import { asTree } from '../api/catalog'
import type { Allergen } from '../api/client'
import type { AllergenSelection } from '../lib/format'

/** One select per taxonomy node: not on label, contains, or may contain. */
export function AllergenEditor({
  allergens,
  value,
  onChange,
  disabled = false,
}: {
  allergens: Allergen[]
  value: AllergenSelection
  onChange: (next: AllergenSelection) => void
  disabled?: boolean
}) {
  const set = (id: string, choice: string) => {
    const next = { ...value }
    if (choice === 'CONTAINS' || choice === 'MAY_CONTAIN') next[id] = choice
    else delete next[id]
    onChange(next)
  }

  return (
    <table className="allergen-editor">
      <thead>
        <tr>
          <th scope="col">Allergen</th>
          <th scope="col">On the label</th>
        </tr>
      </thead>
      <tbody>
        {asTree(allergens).map(({ node, depth }) => (
          <tr key={node.id}>
            <th scope="row" style={{ paddingLeft: `${0.5 + depth * 1.5}rem` }}>
              <label htmlFor={`allergen-${node.id}`}>{node.displayName}</label>
              {node.synonyms.length > 0 && <span className="hint"> (label terms: {node.synonyms.join(', ')})</span>}
            </th>
            <td>
              <select
                id={`allergen-${node.id}`}
                value={value[node.id] ?? 'NONE'}
                disabled={disabled}
                onChange={(e) => set(node.id, e.target.value)}
              >
                <option value="NONE">Not on label</option>
                <option value="CONTAINS">Contains</option>
                <option value="MAY_CONTAIN">May contain</option>
              </select>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
