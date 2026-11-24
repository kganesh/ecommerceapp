import type { ReactNode } from 'react'
import CheckCircleOutlined from '@mui/icons-material/CheckCircleOutlined'
import ErrorOutlined from '@mui/icons-material/ErrorOutlined'
import HelpOutlined from '@mui/icons-material/HelpOutlined'
import ReportProblemOutlined from '@mui/icons-material/ReportProblemOutlined'
import type { SvgIconComponent } from '@mui/icons-material'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import type { Product } from '../api/client'
import { tokens } from '../theme'

/** A label line with its icon. The words carry the meaning; the icon only helps scanning (NFR-9). */
function Line({ Icon, color, children, sx }: { Icon: SvgIconComponent; color: string; children: ReactNode; sx?: object }) {
  return (
    <Typography variant="body2" component="p" sx={{ display: 'flex', gap: 0.75, alignItems: 'flex-start', ...sx }}>
      <Icon aria-hidden sx={{ fontSize: 18, color, mt: '1px', flexShrink: 0 }} />
      <span>{children}</span>
    </Typography>
  )
}

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
        <Line Icon={HelpOutlined} color={tokens.caution}>
          <strong>Unable to verify allergens.</strong> No allergen data for this product yet.
        </Line>
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
        <Line Icon={CheckCircleOutlined} color={tokens.sage}>
          No listed allergens.
        </Line>
      )}
      {contains.length > 0 && (
        <Line Icon={ErrorOutlined} color={tokens.contains}>
          <strong>Contains:</strong> {contains.map((a) => a.displayName).join(', ')}
        </Line>
      )}
      {mayContain.length > 0 && (
        <Line
          Icon={ReportProblemOutlined}
          color={tokens.caution}
          sx={contains.length > 0 ? { borderTop: `1px solid ${tokens.ink}`, mt: 0.75, pt: 0.75 } : undefined}
        >
          <strong>May contain:</strong> {mayContain.map((a) => a.displayName).join(', ')}
        </Line>
      )}
    </Box>
  )
}
