import { useId, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { useCart } from '../api/cart'
import { errorCode, useCheckout, type CheckoutWarning } from '../api/orders'
import { hasRole, signIn, useMe } from '../api/session'
import { ErrorNotice } from '../components/ErrorNotice'
import { MatchList } from '../components/SafetyNotice'
import { formatPrice } from '../lib/format'

/** Until the hosted provider is chosen, the backend's test provider accepts only these tokens. */
const TEST_PAYMENT_METHODS = [
  { token: 'tok_test_approve', label: 'Test card (approves)' },
  { token: 'tok_test_decline', label: 'Test card (declines)' },
]

function CheckoutWarningPanel({
  warning,
  busy,
  onConfirm,
}: {
  warning: CheckoutWarning
  busy: boolean
  onConfirm: () => void
}) {
  const titleId = useId()
  return (
    <div className="warning-panel" role="alertdialog" aria-labelledby={titleId} aria-modal="false">
      <h3 id={titleId}>
        <span aria-hidden="true">⚠ </span>
        Please confirm before we place your order
      </h3>
      <p>These items match your children's avoid lists, or their details changed since you added them:</p>
      <ul className="checkout-warning-lines">
        {warning.lines.map((line) => (
          <li key={line.productId}>
            <strong>{line.productName}</strong>
            {line.unverified ? (
              <p>
                Unable to verify allergens for {line.affectedProfiles.map((p) => p.label).join(', ')}. Read the package
                label before serving.
              </p>
            ) : (
              <MatchList matches={line.matches} />
            )}
          </li>
        ))}
      </ul>
      <p>Nothing has been charged.</p>
      <div className="actions">
        <Link to="/cart">Back to cart</Link>
        <button type="button" className="secondary" onClick={onConfirm} disabled={busy}>
          I understand, place the order
        </button>
      </div>
    </div>
  )
}

/** FR-8: checkout re-checks every line; the parent confirms anything not already confirmed. */
export function CheckoutPage() {
  const me = useMe()
  const isShopper = hasRole(me.data, 'SHOPPER')
  const cart = useCart(isShopper)
  const checkout = useCheckout()
  const navigate = useNavigate()
  // One key for this checkout: retries and the confirmation resend reuse it.
  const [idempotencyKey] = useState(() => `web-${crypto.randomUUID()}`)
  const [paymentToken, setPaymentToken] = useState(TEST_PAYMENT_METHODS[0]!.token)
  const [warning, setWarning] = useState<CheckoutWarning | null>(null)

  if (me.isPending) return <p>Loading…</p>
  if (!me.data?.authenticated) {
    return (
      <button type="button" onClick={() => signIn(me.data)}>
        Sign in to check out
      </button>
    )
  }
  if (!isShopper) return <p className="problem">This account is not a shopper account.</p>

  const place = (ackToken?: string) =>
    checkout.mutate(
      { idempotencyKey, paymentToken, ackToken },
      {
        onSuccess: (outcome) => {
          if (outcome.kind === 'warning') setWarning(outcome.warning)
          else navigate(`/orders/${outcome.order.id}`, { state: { justPlaced: true } })
        },
      },
    )

  const submit = (event: FormEvent) => {
    event.preventDefault()
    setWarning(null)
    place()
  }

  const code = errorCode(checkout.error)

  return (
    <section aria-labelledby="checkout-heading">
      <h2 id="checkout-heading">Checkout</h2>
      {cart.isPending && <p>Loading…</p>}
      {cart.isError && <ErrorNotice error={cart.error} />}
      {cart.isSuccess && cart.data.lines.length === 0 && (
        <p>
          Your cart is empty. <Link to="/">Go shopping</Link>
        </p>
      )}
      {cart.isSuccess && cart.data.lines.length > 0 && (
        <>
          <ul>
            {cart.data.lines.map((line) => (
              <li key={line.product.id}>
                {line.qty} × {line.product.name} — {formatPrice(line.lineTotalCents, line.product.currency)}
                {line.status === 'NEEDS_ACKNOWLEDGMENT' && <strong> (needs your confirmation)</strong>}
              </li>
            ))}
          </ul>
          <p className="total">
            Total: <strong>{formatPrice(cart.data.totalCents, cart.data.currency)}</strong>
          </p>

          {warning ? (
            <CheckoutWarningPanel
              warning={warning}
              busy={checkout.isPending}
              onConfirm={() => place(warning.ackToken)}
            />
          ) : (
            <form onSubmit={submit} className="stacked">
              <fieldset>
                <legend>Payment (test mode)</legend>
                {TEST_PAYMENT_METHODS.map((m) => (
                  <label key={m.token} className="checkbox">
                    <input
                      type="radio"
                      name="payment"
                      value={m.token}
                      checked={paymentToken === m.token}
                      onChange={() => setPaymentToken(m.token)}
                    />
                    {m.label}
                  </label>
                ))}
              </fieldset>
              <p className="disclaimer">
                We check every item against your children's avoid lists again now. Always read the label on each
                package before serving.
              </p>
              <button type="submit" disabled={checkout.isPending}>
                Place order
              </button>
            </form>
          )}
        </>
      )}

      {checkout.isError && (
        <p className="problem" role="alert">
          <span aria-hidden="true">⚠ </span>
          {code === 'PAYMENT_DECLINED' && 'Your payment was declined. No order was placed and nothing was charged.'}
          {code === 'ALLERGEN_CHECK_UNAVAILABLE' &&
            "We couldn't check allergens right now, so no order was placed. Please try again."}
          {code === 'PRODUCT_UNAVAILABLE' && (
            <>
              {checkout.error.message} <Link to="/cart">Go to cart</Link>
            </>
          )}
          {code !== 'PAYMENT_DECLINED' && code !== 'ALLERGEN_CHECK_UNAVAILABLE' && code !== 'PRODUCT_UNAVAILABLE' &&
            checkout.error.message}
        </p>
      )}
    </section>
  )
}
