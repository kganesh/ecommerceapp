import { useId, useState } from 'react'
import { Link } from 'react-router'
import { isCheckUnavailable, useAddToCart, type AllergenWarning } from '../api/cart'
import { hasRole, signIn, useMe } from '../api/session'
import { ErrorNotice } from './ErrorNotice'
import { MatchList } from './SafetyNotice'

/** The allergen warning (FR-7, FR-10): nothing is in the cart until the parent confirms. */
export function WarningPanel({
  warning,
  changed,
  busy,
  onConfirm,
  onCancel,
}: {
  warning: AllergenWarning
  changed: boolean
  busy: boolean
  onConfirm: () => void
  onCancel: () => void
}) {
  const titleId = useId()
  return (
    <div className="warning-panel" role="alertdialog" aria-labelledby={titleId} aria-modal="false">
      <h4 id={titleId}>
        <span aria-hidden="true">⚠ </span>
        {warning.unverified ? 'Unable to verify allergens' : 'Allergen warning'}: {warning.productName}
      </h4>
      {changed && (
        <p>
          <strong>The details changed since you last looked.</strong> Please review them again.
        </p>
      )}
      {warning.unverified ? (
        <p>
          This product has no allergen information yet, so we can't check it for{' '}
          {warning.affectedProfiles.map((p) => p.label).join(', ')}. Read the package label before serving.
        </p>
      ) : (
        <>
          <p>This product matches your children's avoid lists:</p>
          <MatchList matches={warning.matches} />
        </>
      )}
      <p>It has not been added to your cart.</p>
      <div className="actions">
        <button type="button" onClick={onCancel} autoFocus>
          Don't add
        </button>
        <button type="button" className="secondary" onClick={onConfirm} disabled={busy}>
          I understand, add anyway
        </button>
      </div>
    </div>
  )
}

/** Add-to-cart with the server-enforced warning contract. Shoppers only. */
export function AddToCart({ productId }: { productId: string }) {
  const me = useMe()
  const add = useAddToCart()
  const [warning, setWarning] = useState<AllergenWarning | null>(null)
  const [changed, setChanged] = useState(false)
  const [added, setAdded] = useState(false)

  if (!me.data?.authenticated) {
    return (
      <button type="button" onClick={() => signIn(me.data)}>
        Sign in to add to cart
      </button>
    )
  }
  if (!hasRole(me.data, 'SHOPPER')) return null

  const submit = (ackToken?: string) => {
    setAdded(false)
    add.mutate(
      { productId, ackToken },
      {
        onSuccess: (outcome) => {
          if (outcome.kind === 'warning') {
            setChanged(ackToken !== undefined)
            setWarning(outcome.warning)
          } else {
            setWarning(null)
            setChanged(false)
            setAdded(true)
          }
        },
      },
    )
  }

  return (
    <div className="add-to-cart">
      {!warning && (
        <button type="button" onClick={() => submit()} disabled={add.isPending}>
          Add to cart
        </button>
      )}
      {warning && (
        <WarningPanel
          warning={warning}
          changed={changed}
          busy={add.isPending}
          onConfirm={() => submit(warning.ackToken)}
          onCancel={() => {
            setWarning(null)
            setChanged(false)
          }}
        />
      )}
      {added && (
        <p role="status">
          Added. <Link to="/cart">View cart</Link>
        </p>
      )}
      {add.isError &&
        (isCheckUnavailable(add.error) ? (
          <p className="problem" role="alert">
            <span aria-hidden="true">⚠ </span>
            We couldn't check allergens right now, so nothing was added. Please try again.
          </p>
        ) : (
          <ErrorNotice error={add.error} />
        ))}
    </div>
  )
}
