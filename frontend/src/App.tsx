import Typography from '@mui/material/Typography'
import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router'
import { Layout } from './components/Layout'
import { BrowsePage } from './pages/BrowsePage'
import { CartPage } from './pages/CartPage'
import { CheckoutPage } from './pages/CheckoutPage'
import { OrderPage, OrdersPage } from './pages/OrdersPage'
import { ProductPage } from './pages/ProductPage'
import { ProfilesPage } from './pages/ProfilesPage'

// Catalog admin is loaded only when an admin opens it; shoppers never download it.
const AdminProductsPage = lazy(() =>
  import('./pages/admin/AdminProductsPage').then((m) => ({ default: m.AdminProductsPage })),
)
const ProductEditorPage = lazy(() =>
  import('./pages/admin/ProductEditorPage').then((m) => ({ default: m.ProductEditorPage })),
)

const loading = <Typography>Loading…</Typography>

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<BrowsePage />} />
        <Route path="products/:id" element={<ProductPage />} />
        <Route path="profiles" element={<ProfilesPage />} />
        <Route path="cart" element={<CartPage />} />
        <Route path="checkout" element={<CheckoutPage />} />
        <Route path="orders" element={<OrdersPage />} />
        <Route path="orders/:id" element={<OrderPage />} />
        <Route path="admin/products" element={<Suspense fallback={loading}><AdminProductsPage /></Suspense>} />
        <Route path="admin/products/new" element={<Suspense fallback={loading}><ProductEditorPage /></Suspense>} />
        <Route path="admin/products/:id" element={<Suspense fallback={loading}><ProductEditorPage /></Suspense>} />
        <Route path="*" element={<Typography>Page not found.</Typography>} />
      </Route>
    </Routes>
  )
}
