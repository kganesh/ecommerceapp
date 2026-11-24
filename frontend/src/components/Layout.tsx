import StorefrontOutlined from '@mui/icons-material/StorefrontOutlined'
import ChildCareOutlined from '@mui/icons-material/ChildCareOutlined'
import ShoppingCartOutlined from '@mui/icons-material/ShoppingCartOutlined'
import ReceiptLongOutlined from '@mui/icons-material/ReceiptLongOutlined'
import Inventory2Outlined from '@mui/icons-material/Inventory2Outlined'
import InfoOutlined from '@mui/icons-material/InfoOutlined'
import Alert from '@mui/material/Alert'
import AppBar from '@mui/material/AppBar'
import Badge from '@mui/material/Badge'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Container from '@mui/material/Container'
import Toolbar from '@mui/material/Toolbar'
import Typography from '@mui/material/Typography'
import type { ReactNode } from 'react'
import { NavLink, Outlet, useSearchParams } from 'react-router'
import { useCart } from '../api/cart'
import { hasRole, useMe } from '../api/session'
import { tokens } from '../theme'
import { AccountMenu } from './AccountMenu'
import { BackendStatus } from './BackendStatus'

function NavItem({ to, end, icon, children }: { to: string; end?: boolean; icon: ReactNode; children: ReactNode }) {
  return (
    <Button
      component={NavLink}
      to={to}
      end={end}
      startIcon={icon}
      color="inherit"
      sx={{
        color: tokens.muted,
        borderRadius: 2,
        '&.active': { color: tokens.sage, bgcolor: tokens.sageTint },
      }}
    >
      {children}
    </Button>
  )
}

/** A leaf inside a label outline: the app's mark. */
function Mark() {
  return (
    <Box
      component="svg"
      viewBox="0 0 24 24"
      aria-hidden="true"
      sx={{ width: 28, height: 28, flexShrink: 0, color: tokens.sage }}
    >
      <rect x="2" y="2" width="20" height="20" rx="4" fill="none" stroke="currentColor" strokeWidth="2.2" />
      <path d="M7 15.5c0-4.5 3.5-8 10-8.5-.5 6.5-4 10-8.5 10-.6 0-1-.2-1.5-.5z" fill="currentColor" />
      <path d="M8 16.5c2-3 4-4.6 6.5-6" stroke="#fff" strokeWidth="1.3" fill="none" strokeLinecap="round" />
    </Box>
  )
}

export function Layout() {
  const me = useMe()
  const [params] = useSearchParams()
  const isShopper = hasRole(me.data, 'SHOPPER')
  const cart = useCart(isShopper)
  const items = cart.data?.lines.reduce((n, l) => n + l.qty, 0) ?? 0

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <AppBar position="sticky">
        <Container maxWidth="lg">
          <Toolbar disableGutters sx={{ gap: 2, flexWrap: 'wrap', py: 1 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, mr: 2 }}>
              <Mark />
              <Typography variant="h1" component="h1" sx={{ color: tokens.ink }}>
                Nut-Allergy Safe Shopping
              </Typography>
            </Box>
            <Box component="nav" aria-label="Main" sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', flexGrow: 1 }}>
              <NavItem to="/" end icon={<StorefrontOutlined />}>
                Shop
              </NavItem>
              {isShopper && <NavItem to="/profiles" icon={<ChildCareOutlined />}>
                  My children
                </NavItem>}
              {isShopper && (
                <NavItem to="/cart" icon={<ShoppingCartOutlined />}>
                  <Badge
                    badgeContent={items}
                    color="primary"
                    slotProps={{ badge: { 'aria-hidden': true } }}
                    sx={{ '& .MuiBadge-badge': { right: -12 } }}
                  >
                    Cart
                  </Badge>
                </NavItem>
              )}
              {isShopper && <NavItem to="/orders" icon={<ReceiptLongOutlined />}>
                  Orders
                </NavItem>}
              {hasRole(me.data, 'CATALOG_ADMIN') && <NavItem to="/admin/products" icon={<Inventory2Outlined />}>
                  Catalog admin
                </NavItem>}
            </Box>
            <AccountMenu />
          </Toolbar>
        </Container>
      </AppBar>

      <Container component="main" maxWidth="lg" sx={{ flexGrow: 1, py: { xs: 3, md: 5 } }}>
        {params.get('login') === 'failed' && (
          <Alert severity="error" role="alert" sx={{ mb: 3 }}>
            Sign-in did not complete. Please try again.
          </Alert>
        )}
        <Outlet />
      </Container>

      <Box component="footer" sx={{ borderTop: `1px solid ${tokens.line}`, bgcolor: tokens.surface }}>
        <Container
          maxWidth="lg"
          sx={{ py: 2.5, display: 'flex', gap: 2, flexWrap: 'wrap', justifyContent: 'space-between' }}
        >
          <Typography variant="body2" sx={{ maxWidth: '60ch', display: 'flex', gap: 1, alignItems: 'flex-start' }}>
            <InfoOutlined fontSize="small" aria-hidden sx={{ color: tokens.sage, mt: '2px' }} />
            Always read the label on the package. This app helps you check, but the label is the final word.
          </Typography>
          <BackendStatus />
        </Container>
      </Box>
    </Box>
  )
}
