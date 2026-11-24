import ArrowBack from '@mui/icons-material/ArrowBack'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import { Link as RouterLink, useLocation, useParams } from 'react-router'
import { useOrder, useOrders, type Order } from '../api/orders'
import { ErrorNotice } from '../components/ErrorNotice'
import { MatchList, Verdict } from '../components/SafetyNotice'
import { formatDateTime, formatPrice } from '../lib/format'

function warningCount(order: Order): number {
  return order.lines.filter((l) => l.acknowledgedWarning).length
}

/** FR-15: past orders. */
export function OrdersPage() {
  const orders = useOrders()
  return (
    <Box component="section" aria-labelledby="orders-heading">
      <Typography variant="h2" id="orders-heading" sx={{ mb: 3 }}>
        Orders
      </Typography>
      {orders.isPending && <Typography>Loading…</Typography>}
      {orders.isError && <ErrorNotice error={orders.error} />}
      {orders.isSuccess && orders.data.length === 0 && (
        <Paper sx={{ p: 3 }}>
          <Typography>No orders yet.</Typography>
        </Paper>
      )}
      {orders.isSuccess && orders.data.length > 0 && (
        <Paper sx={{ overflowX: 'auto' }}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Placed</TableCell>
                <TableCell>Items</TableCell>
                <TableCell>Total</TableCell>
                <TableCell>Allergen confirmations</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {orders.data.map((order) => (
                <TableRow key={order.id} hover>
                  <TableCell>
                    <Link component={RouterLink} to={`/orders/${order.id}`}>
                      {formatDateTime(order.placedAt)}
                    </Link>
                  </TableCell>
                  <TableCell>{order.lines.reduce((n, l) => n + l.qty, 0)}</TableCell>
                  <TableCell>{formatPrice(order.totalCents, order.currency)}</TableCell>
                  <TableCell>{warningCount(order) === 0 ? 'None' : `${warningCount(order)} confirmed`}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      )}
    </Box>
  )
}

/** FR-15: one order, including every allergen warning the parent confirmed for it. */
export function OrderPage() {
  const { id = '' } = useParams()
  const location = useLocation()
  const order = useOrder(id)
  const justPlaced = (location.state as { justPlaced?: boolean } | null)?.justPlaced === true

  return (
    <Box component="section" sx={{ maxWidth: 820 }}>
      <Button component={RouterLink} to="/orders" startIcon={<ArrowBack />} color="inherit" sx={{ mb: 2 }}>
        All orders
      </Button>
      {justPlaced && (
        <Alert severity="success" role="status" sx={{ mb: 2 }}>
          Order placed. Thank you!
        </Alert>
      )}
      {order.isPending && <Typography>Loading…</Typography>}
      {order.isError && <ErrorNotice error={order.error} />}
      {order.isSuccess && (
        <>
          <Typography variant="h2">Order from {formatDateTime(order.data.placedAt)}</Typography>
          <Typography color="text.secondary" sx={{ mt: 0.5, mb: 3 }}>
            Total {formatPrice(order.data.totalCents, order.data.currency)}. Payment reference{' '}
            <code>{order.data.paymentReference}</code>
          </Typography>
          <Box component="ul" sx={{ m: 0, p: 0, listStyle: 'none', display: 'grid', gap: 2 }}>
            {order.data.lines.map((line) => (
              <Paper component="li" key={line.productId} sx={{ p: 2, display: 'grid', gap: 1.5 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
                  <Typography variant="h3">
                    {line.qty} × {line.productName}
                  </Typography>
                  <Typography sx={{ fontWeight: 700 }}>
                    {formatPrice(line.lineTotalCents, order.data.currency)}
                  </Typography>
                </Box>
                {line.acknowledgedWarning && (
                  <Verdict
                    tone={line.acknowledgedWarning.matches.some((m) => m.matchType === 'CONTAINS') ? 'contains' : 'caution'}
                  >
                    <Typography variant="body2" component="p">
                      You confirmed this{' '}
                      {line.acknowledgedWarning.context === 'CHECKOUT' ? 'at checkout' : 'when adding to cart'}
                      {line.acknowledgedWarning.acknowledgedAt &&
                        ` on ${formatDateTime(line.acknowledgedWarning.acknowledgedAt)}`}
                      :
                    </Typography>
                    {line.acknowledgedWarning.unverified ? (
                      <Typography variant="body2">Allergens could not be verified (no allergen information).</Typography>
                    ) : (
                      <MatchList matches={line.acknowledgedWarning.matches} />
                    )}
                  </Verdict>
                )}
              </Paper>
            ))}
          </Box>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
            Always read the label on each package before serving.
          </Typography>
        </>
      )}
    </Box>
  )
}
