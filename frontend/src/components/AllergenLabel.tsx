import type { Product } from '../api/client'

/**
 * A product's allergens, as text: never colour or icon alone (NFR-9).
 * A product without allergen data is shown as "unable to verify", never as safe (FR-11).
 */
export function AllergenLabel({ product }: { product: Product }) {
  if (!product.allergenDataAvailable) {
    return (
      <p className="allergen-label unknown">
        <span aria-hidden="true">⚠ </span>
        <strong>Unable to verify allergens.</strong> No allergen data for this product yet.
      </p>
    )
  }

  const contains = product.allergens.filter((a) => a.matchType === 'CONTAINS')
  const mayContain = product.allergens.filter((a) => a.matchType === 'MAY_CONTAIN')

  if (contains.length === 0 && mayContain.length === 0) {
    return <p className="allergen-label none">No listed allergens.</p>
  }

  return (
    <div className="allergen-label">
      {contains.length > 0 && (
        <p>
          <strong>Contains:</strong> {contains.map((a) => a.displayName).join(', ')}
        </p>
      )}
      {mayContain.length > 0 && (
        <p>
          <strong>May contain:</strong> {mayContain.map((a) => a.displayName).join(', ')}
        </p>
      )}
    </div>
  )
}
