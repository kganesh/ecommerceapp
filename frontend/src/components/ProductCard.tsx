import Box from '@mui/material/Box'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import { Link as RouterLink } from 'react-router'
import type { Product } from '../api/client'
import { formatPrice } from '../lib/format'
import { AddToCart } from './AddToCart'
import { AllergenLabel } from './AllergenLabel'
import { SafetyNotice } from './SafetyNotice'

export function ProductCard({ product }: { product: Product }) {
  return (
    <Paper
      component="li"
      className="product-card"
      sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 1.5, height: '100%' }}
    >
      <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, alignItems: 'baseline' }}>
        <Typography variant="h3" component="h3">
          <Link component={RouterLink} to={`/products/${product.id}`} color="inherit" underline="hover">
            {product.name}
          </Link>
        </Typography>
        <Typography variant="body1" sx={{ fontWeight: 700, whiteSpace: 'nowrap' }}>
          {formatPrice(product.priceCents, product.currency)}
        </Typography>
      </Box>
      <AllergenLabel product={product} />
      <SafetyNotice safety={product.safety} />
      <Box sx={{ mt: 'auto', pt: 0.5 }}>
        <AddToCart productId={product.id} />
      </Box>
    </Paper>
  )
}
