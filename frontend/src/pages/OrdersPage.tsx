import { Link, useLocation, useParams } from 'react-router'
import { useOrder, useOrders, type Order } from '../api/orders'
import { ErrorNotice } from '../components/ErrorNotice'
import { MatchList } from '../components/SafetyNotice'
import { formatPrice } from '../lib/format'

function warningCount(order: Order): number {
  return order.lines.filter((l) => l.acknowledgedWarning).length
}

/** FR-15: past orders. */
export function OrdersPage() {
  const orders = useOrders()
  return (
    <section aria-labelledby="orders-heading">
      <h2 id="orders-heading">Orders</h2>
      {orders.isPending && <p>Loading…</p>}
      {orders.isError && <ErrorNotice error={orders.error} />}
      {orders.isSuccess && orders.data.length === 0 && <p>No orders yet.</p>}
      {orders.isSuccess && orders.data.length > 0 && (
        <table>
          <thead>
            <tr>
              <th scope="col">Placed</th>
              <th scope="col">Items</th>
              <th scope="col">Total</th>
              <th scope="col">Allergen confirmations</th>
            </tr>
          </thead>
          <tbody>
            {orders.data.map((order) => (
              <tr key={order.id}>
                <td>
                  <Link to={`/orders/${order.id}`}>{new Date(order.placedAt).toLocaleString()}</Link>
                </td>
                <td>{order.lines.reduce((n, l) => n + l.qty, 0)}</td>
                <td>{formatPrice(order.totalCents, order.currency)}</td>
                <td>{warningCount(order) === 0 ? 'None' : `${warningCount(order)} confirmed`}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}

/** FR-15: one order, including every allergen warning the parent confirmed for it. */
export function OrderPage() {
  const { id = '' } = useParams()
  const location = useLocation()
  const order = useOrder(id)
  const justPlaced = (location.state as { justPlaced?: boolean } | null)?.justPlaced === true

  return (
    <section>
      <p>
        <Link to="/orders">← All orders</Link>
      </p>
      {justPlaced && (
        <p role="status" className="safety clear">
          <span aria-hidden="true">✔ </span>
          Order placed. Thank you!
        </p>
      )}
      {order.isPending && <p>Loading…</p>}
      {order.isError && <ErrorNotice error={order.error} />}
      {order.isSuccess && (
        <>
          <h2>Order from {new Date(order.data.placedAt).toLocaleString()}</h2>
          <p>
            Total {formatPrice(order.data.totalCents, order.data.currency)} · payment reference{' '}
            <code>{order.data.paymentReference}</code>
          </p>
          <ul className="cart-lines">
            {order.data.lines.map((line) => (
              <li key={line.productId} className="cart-line">
                <h3>
                  {line.qty} × {line.productName}
                </h3>
                <p>{formatPrice(line.lineTotalCents, order.data.currency)}</p>
                {line.acknowledgedWarning && (
                  <div className="safety warning">
                    <p>
                      <span aria-hidden="true">⚠ </span>
                      You confirmed this{' '}
                      {line.acknowledgedWarning.context === 'CHECKOUT' ? 'at checkout' : 'when adding to cart'}
                      {line.acknowledgedWarning.acknowledgedAt &&
                        ` on ${new Date(line.acknowledgedWarning.acknowledgedAt).toLocaleString()}`}
                      :
                    </p>
                    {line.acknowledgedWarning.unverified ? (
                      <p>Allergens could not be verified (no allergen information).</p>
                    ) : (
                      <MatchList matches={line.acknowledgedWarning.matches} />
                    )}
                  </div>
                )}
              </li>
            ))}
          </ul>
          <p className="disclaimer">Always read the label on each package before serving.</p>
        </>
      )}
    </section>
  )
}
