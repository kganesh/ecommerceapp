import { Link, useParams } from 'react-router'
import { useProduct } from '../api/catalog'
import { AddToCart } from '../components/AddToCart'
import { AllergenLabel } from '../components/AllergenLabel'
import { SafetyNotice } from '../components/SafetyNotice'
import { formatPrice } from '../lib/format'
import { ErrorNotice } from '../components/ErrorNotice'

export function ProductPage() {
  const { id = '' } = useParams()
  const product = useProduct(id)

  return (
    <section>
      <p>
        <Link to="/">← Back to shop</Link>
      </p>
      {product.isPending && <p>Loading…</p>}
      {product.isError && <ErrorNotice error={product.error} />}
      {product.isSuccess && (
        <article>
          <h2>{product.data.name}</h2>
          <p className="price">{formatPrice(product.data.priceCents, product.data.currency)}</p>
          {product.data.description && <p>{product.data.description}</p>}
          <h3>Allergens</h3>
          <AllergenLabel product={product.data} />
          <SafetyNotice safety={product.data.safety} />
          <AddToCart productId={product.data.id} />
          <p className="disclaimer">Always check the label on the package before serving.</p>
        </article>
      )}
    </section>
  )
}
