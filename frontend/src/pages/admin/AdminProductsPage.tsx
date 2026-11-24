import { useState } from 'react'
import { Link } from 'react-router'
import { useAdminProductSearch } from '../../api/catalog'
import { formatPrice } from '../../lib/format'
import { ErrorNotice } from '../../components/ErrorNotice'

/** FR-14: catalog administration (CATALOG_ADMIN). */
export function AdminProductsPage() {
  const [q, setQ] = useState('')
  const products = useAdminProductSearch(q)

  return (
    <section aria-labelledby="admin-heading">
      <h2 id="admin-heading">Catalog admin</h2>
      <p>
        <Link to="/admin/products/new">Add a product</Link>
      </p>
      <label htmlFor="admin-q">Filter by name or SKU</label>
      <input id="admin-q" type="search" value={q} onChange={(e) => setQ(e.target.value)} />

      {products.isPending && <p>Loading…</p>}
      {products.isError && <ErrorNotice error={products.error} />}
      {products.isSuccess && (
        <table>
          <thead>
            <tr>
              <th scope="col">Name</th>
              <th scope="col">SKU</th>
              <th scope="col">Price</th>
              <th scope="col">Status</th>
              <th scope="col">Allergen data</th>
            </tr>
          </thead>
          <tbody>
            {products.data.items.map((p) => (
              <tr key={p.id}>
                <td>
                  <Link to={`/admin/products/${p.id}`}>{p.name}</Link>
                </td>
                <td>{p.sku}</td>
                <td>{formatPrice(p.priceCents, p.currency)}</td>
                <td>{p.active ? 'Active' : 'Inactive'}</td>
                <td>{p.allergenDataAvailable ? `Version ${p.allergenVersion}` : 'Missing — enter allergens'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
