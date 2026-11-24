import type { ReactNode } from 'react'
import { NavLink, Outlet } from 'react-router'
import { BackendStatus } from './BackendStatus'

export function Layout({ account }: { account?: ReactNode }) {
  return (
    <div className="shell">
      <header className="site-header">
        <h1>Nut-Allergy Safe Shopping</h1>
        <nav aria-label="Main">
          <NavLink to="/" end>
            Shop
          </NavLink>
          <NavLink to="/admin/products">Catalog admin</NavLink>
        </nav>
        {account}
      </header>
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
