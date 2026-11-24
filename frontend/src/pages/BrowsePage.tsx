import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useProductSearch } from '../api/catalog'
import { AllergenLabel } from '../components/AllergenLabel'
import { formatPrice } from '../lib/format'
import { ErrorNotice } from '../components/ErrorNotice'

/** FR-4: browse and search. Per-child warnings arrive with the allergen check in M3. */
export function BrowsePage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const [draft, setDraft] = useState(q)
  const search = useProductSearch(q)

  const submit = (event: FormEvent) => {
    event.preventDefault()
    setParams(draft.trim() ? { q: draft.trim() } : {})
  }

  return (
    <section aria-labelledby="browse-heading">
      <h2 id="browse-heading">Shop</h2>
      <form role="search" onSubmit={submit} className="search">
        <label htmlFor="q">Search products</label>
        <input id="q" type="search" value={draft} onChange={(e) => setDraft(e.target.value)} />
        <button type="submit">Search</button>
      </form>

      {search.isPending && <p>Loading products…</p>}
      {search.isError && <ErrorNotice error={search.error} />}
      {search.isSuccess && search.data.items.length === 0 && <p>No products found.</p>}
      {search.isSuccess && search.data.items.length > 0 && (
        <ul className="product-list">
          {search.data.items.map((product) => (
            <li key={product.id} className="product-card">
              <h3>
                <Link to={`/products/${product.id}`}>{product.name}</Link>
              </h3>
              <p className="price">{formatPrice(product.priceCents, product.currency)}</p>
              <AllergenLabel product={product} />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
