import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import type { Product } from '../api/client'
import { tokens } from '../theme'

/**
 * The product's allergens, drawn like the allergen box printed on real packaging: the one
 * heavy border in the app, so parents learn to read it first. Text, never colour alone
 * (NFR-9). No allergen data is "unable to verify", never safe (FR-11).
 */
export function AllergenLabel({ product }: { product: Product }) {
  if (!product.allergenDataAvailable) {
    return (
      <Box
        className="allergen-label unknown"
        sx={{ border: `2px dashed ${tokens.caution}`, borderRadius: 1, px: 1.5, py: 1, bgcolor: tokens.cautionTint }}
      >
        <Typography variant="body2" component="p">
          <strong>Unable to verify allergens.</strong> No allergen data for this product yet.
        </Typography>
      </Box>
    )
  }

  const contains = product.allergens.filter((a) => a.matchType === 'CONTAINS')
  const mayContain = product.allergens.filter((a) => a.matchType === 'MAY_CONTAIN')

  return (
    <Box
      className="allergen-label"
      sx={{ border: `2px solid ${tokens.ink}`, borderRadius: 1, px: 1.5, py: 1, bgcolor: tokens.surface }}
    >
      {contains.length === 0 && mayContain.length === 0 && (
        <Typography variant="body2" component="p">
          No listed allergens.
        </Typography>
      )}
      {contains.length > 0 && (
        <Typography variant="body2" component="p">
          <strong>Contains:</strong> {contains.map((a) => a.displayName).join(', ')}
        </Typography>
      )}
      {mayContain.length > 0 && (
        <Typography
          variant="body2"
          component="p"
          sx={contains.length > 0 ? { borderTop: `1px solid ${tokens.ink}`, mt: 0.75, pt: 0.75 } : undefined}
        >
          <strong>May contain:</strong> {mayContain.map((a) => a.displayName).join(', ')}
        </Typography>
      )}
    </Box>
  )
}
