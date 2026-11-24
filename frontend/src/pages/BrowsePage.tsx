import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useProductSearch } from '../api/catalog'
import { hasRole, useMe } from '../api/session'
import { AddToCart } from '../components/AddToCart'
import { AllergenLabel } from '../components/AllergenLabel'
import { SafetyNotice } from '../components/SafetyNotice'
import { formatPrice } from '../lib/format'
import { ErrorNotice } from '../components/ErrorNotice'

/** FR-4: browse and search. Per-child warnings arrive with the allergen check in M3. */
export function BrowsePage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const hideUnsafe = params.get('hideUnsafe') === 'true'
  const [draft, setDraft] = useState(q)
  const me = useMe()
  const isShopper = hasRole(me.data, 'SHOPPER')
  const search = useProductSearch(q, 0, isShopper && hideUnsafe)

  const setHideUnsafe = (hide: boolean) => {
    const next = new URLSearchParams(params)
    if (hide) next.set('hideUnsafe', 'true')
    else next.delete('hideUnsafe')
    setParams(next)
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const next = new URLSearchParams(params)
    if (draft.trim()) next.set('q', draft.trim())
    else next.delete('q')
    setParams(next)
  }

  return (
    <section aria-labelledby="browse-heading">
      <h2 id="browse-heading">Shop</h2>
      <form role="search" onSubmit={submit} className="search">
        <label htmlFor="q">Search products</label>
        <input id="q" type="search" value={draft} onChange={(e) => setDraft(e.target.value)} />
        <button type="submit">Search</button>
      </form>
      {isShopper && (
        <label className="checkbox">
          <input type="checkbox" checked={hideUnsafe} onChange={(e) => setHideUnsafe(e.target.checked)} />
          Hide products that match my children's avoid lists or can't be verified
        </label>
      )}

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
              <SafetyNotice safety={product.safety} />
              <AddToCart productId={product.id} />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
