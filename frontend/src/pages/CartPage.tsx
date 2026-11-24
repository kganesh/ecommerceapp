import { Link } from 'react-router'
import { isCheckUnavailable, useCart, useRemoveCartLine, useUpdateCartLine, type CartLine } from '../api/cart'
import { hasRole, signIn, useMe } from '../api/session'
import { ErrorNotice } from '../components/ErrorNotice'
import { MatchList } from '../components/SafetyNotice'
import { formatPrice } from '../lib/format'

function LineStatus({ line }: { line: CartLine }) {
  if (!line.available) {
    return <p className="problem">No longer available. Remove it to check out.</p>
  }
  if (line.status === 'CLEAR') return <p className="safety clear">No match with your children's avoid lists.</p>
  const heading = line.unverified ? 'Unable to verify allergens' : 'Allergen warning'
  return (
    <div className="safety warning">
      <p>
        <span aria-hidden="true">⚠ </span>
        <strong>{heading}</strong>
        {line.status === 'ACKNOWLEDGED' ? ' — you confirmed this.' : ' — you will be asked to confirm at checkout.'}
      </p>
      {line.unverified ? (
        <p>No allergen information for {line.affectedProfiles.map((p) => p.label).join(', ')}.</p>
      ) : (
        <MatchList matches={line.matches} />
      )}
    </div>
  )
}

/** FR-6: the cart, re-checked against current data on every visit. */
export function CartPage() {
  const me = useMe()
  const isShopper = hasRole(me.data, 'SHOPPER')
  const cart = useCart(isShopper)
  const update = useUpdateCartLine()
  const remove = useRemoveCartLine()

  if (me.isPending) return <p>Loading…</p>
  if (!me.data?.authenticated) {
    return (
      <section>
        <h2>Cart</h2>
        <button type="button" onClick={() => signIn(me.data)}>
          Sign in to see your cart
        </button>
      </section>
    )
  }
  if (!isShopper) return <p className="problem">This account is not a shopper account.</p>

  return (
    <section aria-labelledby="cart-heading">
      <h2 id="cart-heading">Cart</h2>
      {cart.isPending && <p>Loading…</p>}
      {cart.isError &&
        (isCheckUnavailable(cart.error) ? (
          <p className="problem" role="alert">
            <span aria-hidden="true">⚠ </span>
            We couldn't check your cart for allergens right now. Please try again in a moment.
          </p>
        ) : (
          <ErrorNotice error={cart.error} />
        ))}
      {cart.isSuccess && cart.data.lines.length === 0 && (
        <p>
          Your cart is empty. <Link to="/">Go shopping</Link>
        </p>
      )}
      {cart.isSuccess && cart.data.lines.length > 0 && (
        <>
          {cart.data.needsAcknowledgment && (
            <p className="problem" role="alert">
              <span aria-hidden="true">⚠ </span>
              Some items need your confirmation because their allergen details or your children's avoid lists
              changed. You'll review them at checkout.
            </p>
          )}
          <ul className="cart-lines">
            {cart.data.lines.map((line) => (
              <li key={line.product.id} className="cart-line">
                <h3>
                  <Link to={`/products/${line.product.id}`}>{line.product.name}</Link>
                </h3>
                <LineStatus line={line} />
                <div className="inline">
                  <label htmlFor={`qty-${line.product.id}`}>Quantity</label>
                  <select
                    id={`qty-${line.product.id}`}
                    value={line.qty}
                    onChange={(e) => update.mutate({ productId: line.product.id, qty: Number(e.target.value) })}
                  >
                    {Array.from({ length: Math.max(10, line.qty) }, (_, i) => i + 1).map((n) => (
                      <option key={n} value={n}>
                        {n}
                      </option>
                    ))}
                  </select>
                  <span>{formatPrice(line.lineTotalCents, line.product.currency)}</span>
                  <button type="button" onClick={() => remove.mutate(line.product.id)}>
                    Remove
                  </button>
                </div>
              </li>
            ))}
          </ul>
          <p className="total">
            Total: <strong>{formatPrice(cart.data.totalCents, cart.data.currency)}</strong>
          </p>
          <p className="disclaimer">Always read the label on each package before serving.</p>
          <Link className="button" to="/checkout" aria-disabled={cart.data.lines.length === 0}>
            Go to checkout
          </Link>
        </>
      )}
      {(update.isError || remove.isError) && <ErrorNotice error={update.error ?? remove.error} />}
    </section>
  )
}
