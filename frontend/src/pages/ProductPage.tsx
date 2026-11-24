import ArrowBack from '@mui/icons-material/ArrowBack'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import { Link as RouterLink, useParams } from 'react-router'
import { useProduct } from '../api/catalog'
import { AddToCart } from '../components/AddToCart'
import { AllergenLabel } from '../components/AllergenLabel'
import { ErrorNotice } from '../components/ErrorNotice'
import { SafetyNotice } from '../components/SafetyNotice'
import { formatPrice } from '../lib/format'

export function ProductPage() {
  const { id = '' } = useParams()
  const product = useProduct(id)

  return (
    <Box component="section">
      <Button component={RouterLink} to="/" startIcon={<ArrowBack />} color="inherit" sx={{ mb: 2 }}>
        Back to shop
      </Button>
      {product.isPending && <Typography>Loading…</Typography>}
      {product.isError && <ErrorNotice error={product.error} />}
      {product.isSuccess && (
        <Paper component="article" sx={{ p: { xs: 2, md: 4 }, maxWidth: 760 }}>
          <Typography variant="h2">{product.data.name}</Typography>
          <Typography variant="h3" component="p" sx={{ mt: 1 }}>
            {formatPrice(product.data.priceCents, product.data.currency)}
          </Typography>
          {product.data.description && (
            <Typography sx={{ mt: 2, maxWidth: '65ch' }}>{product.data.description}</Typography>
          )}
          <Box sx={{ display: 'grid', gap: 1.5, mt: 3 }}>
            <Typography variant="h3">Allergens</Typography>
            <AllergenLabel product={product.data} />
            <SafetyNotice safety={product.data.safety} />
            <AddToCart productId={product.data.id} />
            <Typography variant="body2" color="text.secondary">
              Always check the label on the package before serving.
            </Typography>
          </Box>
        </Paper>
      )}
    </Box>
  )
}
