import CloseOutlined from '@mui/icons-material/CloseOutlined'
import DoneOutlined from '@mui/icons-material/DoneOutlined'
import AddShoppingCart from '@mui/icons-material/AddShoppingCart'
import Alert from '@mui/material/Alert'
import AlertTitle from '@mui/material/AlertTitle'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import Typography from '@mui/material/Typography'
import { useId, useState } from 'react'
import { Link as RouterLink } from 'react-router'
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
  const contains = warning.matches.some((m) => m.matchType === 'CONTAINS')
  return (
    <Alert
      severity={contains ? 'error' : 'warning'}
      role="alertdialog"
      aria-labelledby={titleId}
      aria-modal="false"
      variant="outlined"
      sx={{ bgcolor: 'background.paper', borderWidth: 2 }}
    >
      <AlertTitle id={titleId} sx={{ fontWeight: 700 }}>
        {warning.unverified ? 'Unable to verify allergens' : 'Allergen warning'}: {warning.productName}
      </AlertTitle>
      {changed && (
        <Typography variant="body2" sx={{ mb: 1 }}>
          <strong>The details changed since you last looked.</strong> Please review them again.
        </Typography>
      )}
      {warning.unverified ? (
        <Typography variant="body2">
          This product has no allergen information yet, so we can't check it for{' '}
          {warning.affectedProfiles.map((p) => p.label).join(', ')}. Read the package label before serving.
        </Typography>
      ) : (
        <>
          <Typography variant="body2">This product matches your children's avoid lists:</Typography>
          <MatchList matches={warning.matches} />
        </>
      )}
      <Typography variant="body2" sx={{ mt: 1 }}>
        It has not been added to your cart.
      </Typography>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mt: 1.5 }}>
        <Button variant="contained" startIcon={<CloseOutlined />} onClick={onCancel} autoFocus>
          Don't add
        </Button>
        <Button variant="outlined" color="inherit" startIcon={<DoneOutlined />} onClick={onConfirm} disabled={busy}>
          I understand, add anyway
        </Button>
      </Box>
    </Alert>
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
      <Button variant="outlined" startIcon={<AddShoppingCart />} onClick={() => signIn(me.data)}>
        Sign in to add to cart
      </Button>
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
    <Box sx={{ display: 'grid', gap: 1 }}>
      {!warning && (
        <Box>
          <Button
            variant="contained"
            startIcon={<AddShoppingCart />}
            onClick={() => submit()}
            disabled={add.isPending}
          >
            Add to cart
          </Button>
        </Box>
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
        <Typography variant="body2" role="status">
          Added.{' '}
          <Link component={RouterLink} to="/cart">
            View cart
          </Link>
        </Typography>
      )}
      {add.isError &&
        (isCheckUnavailable(add.error) ? (
          <Alert severity="warning" role="alert">
            We couldn't check allergens right now, so nothing was added. Please try again.
          </Alert>
        ) : (
          <ErrorNotice error={add.error} />
        ))}
    </Box>
  )
}
