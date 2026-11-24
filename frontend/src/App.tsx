import { Route, Routes } from 'react-router'
import { Layout } from './components/Layout'
import { BrowsePage } from './pages/BrowsePage'
import { CartPage } from './pages/CartPage'
import { CheckoutPage } from './pages/CheckoutPage'
import { OrderPage, OrdersPage } from './pages/OrdersPage'
import { ProductPage } from './pages/ProductPage'
import { ProfilesPage } from './pages/ProfilesPage'
import { AdminProductsPage } from './pages/admin/AdminProductsPage'
import { ProductEditorPage } from './pages/admin/ProductEditorPage'

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
        <Route path="admin/products" element={<AdminProductsPage />} />
        <Route path="admin/products/new" element={<ProductEditorPage />} />
        <Route path="admin/products/:id" element={<ProductEditorPage />} />
        <Route path="*" element={<p>Page not found.</p>} />
      </Route>
    </Routes>
  )
}
