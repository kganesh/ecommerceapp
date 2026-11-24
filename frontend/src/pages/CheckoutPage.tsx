import Alert from '@mui/material/Alert'
import AlertTitle from '@mui/material/AlertTitle'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import FormControlLabel from '@mui/material/FormControlLabel'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import Radio from '@mui/material/Radio'
import RadioGroup from '@mui/material/RadioGroup'
import Typography from '@mui/material/Typography'
import { useId, useState, type FormEvent } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router'
import { useCart } from '../api/cart'
import { errorCode, useCheckout, type CheckoutWarning } from '../api/orders'
import { hasRole, signIn, useMe } from '../api/session'
import { ErrorNotice } from '../components/ErrorNotice'
import { MatchList } from '../components/SafetyNotice'
import { formatPrice } from '../lib/format'
import { tokens } from '../theme'

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
  const contains = warning.lines.some((l) => l.matches.some((m) => m.matchType === 'CONTAINS'))
  return (
    <Alert
      severity={contains ? 'error' : 'warning'}
      variant="outlined"
      role="alertdialog"
      aria-labelledby={titleId}
      aria-modal="false"
      sx={{ bgcolor: 'background.paper', borderWidth: 2 }}
    >
      <AlertTitle id={titleId} sx={{ fontWeight: 700 }}>
        Please confirm before we place your order
      </AlertTitle>
      <Typography variant="body2">
        These items match your children's avoid lists, or their details changed since you added them:
      </Typography>
      <Box component="ul" sx={{ m: 0, mt: 1, p: 0, listStyle: 'none', display: 'grid', gap: 1.5 }}>
        {warning.lines.map((line) => (
          <li key={line.productId}>
            <Typography variant="body2" sx={{ fontWeight: 700 }}>
              {line.productName}
            </Typography>
            {line.unverified ? (
              <Typography variant="body2">
                Unable to verify allergens for {line.affectedProfiles.map((p) => p.label).join(', ')}. Read the package
                label before serving.
              </Typography>
            ) : (
              <MatchList matches={line.matches} />
            )}
          </li>
        ))}
      </Box>
      <Typography variant="body2" sx={{ mt: 1.5 }}>
        Nothing has been charged.
      </Typography>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mt: 1.5 }}>
        <Button component={RouterLink} to="/cart" variant="contained">
          Back to cart
        </Button>
        <Button variant="outlined" color="inherit" onClick={onConfirm} disabled={busy}>
          I understand, place the order
        </Button>
      </Box>
    </Alert>
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

  if (me.isPending) return <Typography>Loading…</Typography>
  if (!me.data?.authenticated) {
    return (
      <Button variant="contained" onClick={() => signIn(me.data)}>
        Sign in to check out
      </Button>
    )
  }
  if (!isShopper) return <Alert severity="info">This account is not a shopper account.</Alert>

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
    <Box component="section" aria-labelledby="checkout-heading">
      <Typography variant="h2" id="checkout-heading" sx={{ mb: 3 }}>
        Checkout
      </Typography>
      {cart.isPending && <Typography>Loading…</Typography>}
      {cart.isError && <ErrorNotice error={cart.error} />}
      {cart.isSuccess && cart.data.lines.length === 0 && (
        <Paper sx={{ p: 3 }}>
          <Typography>
            Your cart is empty.{' '}
            <Link component={RouterLink} to="/">
              Go shopping
            </Link>
          </Typography>
        </Paper>
      )}
      {cart.isSuccess && cart.data.lines.length > 0 && (
        <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: '1fr 340px' }, alignItems: 'start' }}>
          <Box sx={{ display: 'grid', gap: 2 }}>
            {warning ? (
              <CheckoutWarningPanel warning={warning} busy={checkout.isPending} onConfirm={() => place(warning.ackToken)} />
            ) : (
              <Paper component="form" onSubmit={submit} sx={{ p: { xs: 2, md: 3 }, display: 'grid', gap: 2 }}>
                <Box component="fieldset" sx={{ border: 0, p: 0, m: 0 }}>
                  <Typography component="legend" variant="h3" sx={{ mb: 1 }}>
                    Payment (test mode)
                  </Typography>
                  <RadioGroup name="payment" value={paymentToken} onChange={(e) => setPaymentToken(e.target.value)}>
                    {TEST_PAYMENT_METHODS.map((m) => (
                      <FormControlLabel key={m.token} value={m.token} control={<Radio />} label={m.label} />
                    ))}
                  </RadioGroup>
                </Box>
                <Typography variant="body2" color="text.secondary" sx={{ maxWidth: '60ch' }}>
                  We check every item against your children's avoid lists again now. Always read the label on each
                  package before serving.
                </Typography>
                <Box>
                  <Button type="submit" variant="contained" size="large" disabled={checkout.isPending}>
                    Place order
                  </Button>
                </Box>
              </Paper>
            )}
            {checkout.isError && (
              <Alert severity={code === 'PAYMENT_DECLINED' ? 'error' : 'warning'} role="alert">
                {code === 'PAYMENT_DECLINED' && 'Your payment was declined. No order was placed and nothing was charged.'}
                {code === 'ALLERGEN_CHECK_UNAVAILABLE' &&
                  "We couldn't check allergens right now, so no order was placed. Please try again."}
                {code === 'PRODUCT_UNAVAILABLE' && (
                  <>
                    {checkout.error.message}{' '}
                    <Link component={RouterLink} to="/cart">
                      Go to cart
                    </Link>
                  </>
                )}
                {code !== 'PAYMENT_DECLINED' &&
                  code !== 'ALLERGEN_CHECK_UNAVAILABLE' &&
                  code !== 'PRODUCT_UNAVAILABLE' &&
                  checkout.error.message}
              </Alert>
            )}
          </Box>

          <Paper sx={{ p: 2.5 }}>
            <Typography variant="h3" sx={{ mb: 1.5 }}>
              Order summary
            </Typography>
            <Box component="ul" sx={{ m: 0, p: 0, listStyle: 'none', display: 'grid', gap: 1 }}>
              {cart.data.lines.map((line) => (
                <Box component="li" key={line.product.id} sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
                  <Typography variant="body2">
                    {line.qty} × {line.product.name}
                    {line.status === 'NEEDS_ACKNOWLEDGMENT' && (
                      <Typography component="strong" variant="body2" sx={{ display: 'block', color: tokens.caution, fontWeight: 700 }}>
                        Needs your confirmation
                      </Typography>
                    )}
                  </Typography>
                  <Typography variant="body2">{formatPrice(line.lineTotalCents, line.product.currency)}</Typography>
                </Box>
              ))}
            </Box>
            <Box
              className="total"
              sx={{ display: 'flex', justifyContent: 'space-between', borderTop: `1px solid ${tokens.line}`, mt: 1.5, pt: 1.5 }}
            >
              <Typography>Total:</Typography>
              <Typography sx={{ fontWeight: 700 }}>{formatPrice(cart.data.totalCents, cart.data.currency)}</Typography>
            </Box>
          </Paper>
        </Box>
      )}
    </Box>
  )
}
