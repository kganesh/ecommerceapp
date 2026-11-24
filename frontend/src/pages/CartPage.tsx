import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { Link as RouterLink } from 'react-router'
import { isCheckUnavailable, useCart, useRemoveCartLine, useUpdateCartLine, type CartLine } from '../api/cart'
import { hasRole, signIn, useMe } from '../api/session'
import { ErrorNotice } from '../components/ErrorNotice'
import { MatchList, Verdict } from '../components/SafetyNotice'
import { formatPrice } from '../lib/format'
import { tokens } from '../theme'

function LineStatus({ line }: { line: CartLine }) {
  if (!line.available) {
    return (
      <Alert severity="error" sx={{ py: 0 }}>
        No longer available. Remove it to check out.
      </Alert>
    )
  }
  if (line.status === 'CLEAR') {
    return (
      <Verdict tone="clear">
        <Typography variant="body2">No match with your children's avoid lists.</Typography>
      </Verdict>
    )
  }
  const heading = line.unverified ? 'Unable to verify allergens' : 'Allergen warning'
  const contains = line.matches.some((m) => m.matchType === 'CONTAINS')
  return (
    <Verdict tone={contains ? 'contains' : 'caution'}>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1 }}>
        <Typography variant="body2" component="p">
          <strong>{heading}</strong>
          {line.status === 'ACKNOWLEDGED' ? ' — you confirmed this.' : ' — you will be asked to confirm at checkout.'}
        </Typography>
        {line.status === 'ACKNOWLEDGED' ? (
          <Chip size="small" label="Confirmed" variant="outlined" sx={{ borderColor: tokens.sage, color: tokens.sage }} />
        ) : (
          <Chip size="small" label="Needs confirmation" sx={{ bgcolor: tokens.caution, color: '#fff', fontWeight: 700 }} />
        )}
      </Box>
      {line.unverified ? (
        <Typography variant="body2">
          No allergen information for {line.affectedProfiles.map((p) => p.label).join(', ')}.
        </Typography>
      ) : (
        <MatchList matches={line.matches} />
      )}
    </Verdict>
  )
}

/** FR-6: the cart, re-checked against current data on every visit. */
export function CartPage() {
  const me = useMe()
  const isShopper = hasRole(me.data, 'SHOPPER')
  const cart = useCart(isShopper)
  const update = useUpdateCartLine()
  const remove = useRemoveCartLine()

  if (me.isPending) return <Typography>Loading…</Typography>
  if (!me.data?.authenticated) {
    return (
      <Paper sx={{ p: 3, maxWidth: 560 }}>
        <Typography variant="h2">Cart</Typography>
        <Button variant="contained" sx={{ mt: 2 }} onClick={() => signIn(me.data)}>
          Sign in to see your cart
        </Button>
      </Paper>
    )
  }
  if (!isShopper) return <Alert severity="info">This account is not a shopper account.</Alert>

  return (
    <Box component="section" aria-labelledby="cart-heading">
      <Typography variant="h2" id="cart-heading" sx={{ mb: 3 }}>
        Cart
      </Typography>
      {cart.isPending && <Typography>Loading…</Typography>}
      {cart.isError &&
        (isCheckUnavailable(cart.error) ? (
          <Alert severity="warning" role="alert">
            We couldn't check your cart for allergens right now. Please try again in a moment.
          </Alert>
        ) : (
          <ErrorNotice error={cart.error} />
        ))}
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
        <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: '1fr 300px' }, alignItems: 'start' }}>
          <Box>
            {cart.data.needsAcknowledgment && (
              <Alert severity="warning" role="alert" sx={{ mb: 2 }}>
                Some items need your confirmation because their allergen details or your children's avoid lists
                changed. You'll review them at checkout.
              </Alert>
            )}
            <Box component="ul" className="cart-lines" sx={{ m: 0, p: 0, listStyle: 'none', display: 'grid', gap: 2 }}>
              {cart.data.lines.map((line) => (
                <Paper component="li" key={line.product.id} className="cart-line" sx={{ p: 2, display: 'grid', gap: 1.5 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, alignItems: 'baseline' }}>
                    <Typography variant="h3">
                      <Link component={RouterLink} to={`/products/${line.product.id}`} color="inherit" underline="hover">
                        {line.product.name}
                      </Link>
                    </Typography>
                    <Typography sx={{ fontWeight: 700 }}>
                      {formatPrice(line.lineTotalCents, line.product.currency)}
                    </Typography>
                  </Box>
                  <LineStatus line={line} />
                  <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center' }}>
                    <TextField
                      id={`qty-${line.product.id}`}
                      select
                      label="Quantity"
                      size="small"
                      value={line.qty}
                      onChange={(e) => update.mutate({ productId: line.product.id, qty: Number(e.target.value) })}
                      slotProps={{ select: { native: true } }}
                      sx={{ width: 110 }}
                    >
                      {Array.from({ length: Math.max(10, line.qty) }, (_, i) => i + 1).map((n) => (
                        <option key={n} value={n}>
                          {n}
                        </option>
                      ))}
                    </TextField>
                    <Button color="inherit" onClick={() => remove.mutate(line.product.id)}>
                      Remove
                    </Button>
                  </Box>
                </Paper>
              ))}
            </Box>
          </Box>

          <Paper sx={{ p: 2.5, display: 'grid', gap: 1.5, position: { md: 'sticky' }, top: { md: 96 } }}>
            <Typography variant="h3">Summary</Typography>
            <Box sx={{ display: 'flex', justifyContent: 'space-between' }} className="total">
              <Typography>Total:</Typography>
              <Typography sx={{ fontWeight: 700 }}>{formatPrice(cart.data.totalCents, cart.data.currency)}</Typography>
            </Box>
            <Button component={RouterLink} to="/checkout" variant="contained" size="large">
              Go to checkout
            </Button>
            <Typography variant="body2" color="text.secondary">
              Always read the label on each package before serving.
            </Typography>
          </Paper>
        </Box>
      )}
      {(update.isError || remove.isError) && <ErrorNotice error={update.error ?? remove.error} />}
    </Box>
  )
}
