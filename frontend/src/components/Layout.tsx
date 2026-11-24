import { NavLink, Outlet, useSearchParams } from 'react-router'
import { hasRole, useMe } from '../api/session'
import { AccountMenu } from './AccountMenu'
import { BackendStatus } from './BackendStatus'

export function Layout() {
  const me = useMe()
  const [params] = useSearchParams()

  return (
    <div className="shell">
      <header className="site-header">
        <h1>Nut-Allergy Safe Shopping</h1>
        <nav aria-label="Main">
          <NavLink to="/" end>
            Shop
          </NavLink>
          {hasRole(me.data, 'SHOPPER') && <NavLink to="/profiles">My children</NavLink>}
          {hasRole(me.data, 'SHOPPER') && <NavLink to="/cart">Cart</NavLink>}
          {hasRole(me.data, 'SHOPPER') && <NavLink to="/orders">Orders</NavLink>}
          {hasRole(me.data, 'CATALOG_ADMIN') && <NavLink to="/admin/products">Catalog admin</NavLink>}
        </nav>
        <AccountMenu />
      </header>
      {params.get('login') === 'failed' && (
        <p className="problem" role="alert">
          <span aria-hidden="true">⚠ </span>
          Sign-in did not complete. Please try again.
        </p>
      )}
      <main>
        <Outlet />
      </main>
      <footer className="site-footer">
        <p className="disclaimer">
          Always read the label on the package. This app helps you check, but the label is the final word.
        </p>
        <BackendStatus />
      </footer>
    </div>
  )
}
