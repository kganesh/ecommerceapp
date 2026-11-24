import CheckCircleOutline from '@mui/icons-material/CheckCircleOutlined'
import ErrorOutline from '@mui/icons-material/ErrorOutlined'
import HelpOutline from '@mui/icons-material/HelpOutlined'
import WarningAmber from '@mui/icons-material/WarningAmber'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import type { ReactNode } from 'react'
import type { ProductSafety, SafetyMatch } from '../api/cart'
import { tokens } from '../theme'

const MATCH_WORDS = { CONTAINS: 'contains', MAY_CONTAIN: 'may contain' } as const

/** "Child 1: contains Cashew" — every match in words, grouped by child (FR-10). */
export function MatchList({ matches }: { matches: SafetyMatch[] }) {
  const byChild = new Map<string, { label: string; items: string[]; contains: boolean }>()
  for (const m of matches) {
    const entry = byChild.get(m.profileId) ?? { label: m.profileLabel, items: [], contains: false }
    entry.items.push(`${MATCH_WORDS[m.matchType]} ${m.allergenName}`)
    entry.contains ||= m.matchType === 'CONTAINS'
    byChild.set(m.profileId, entry)
  }
  return (
    <Box component="ul" sx={{ m: 0, mt: 0.5, pl: 0, listStyle: 'none', display: 'grid', gap: 0.25 }}>
      {[...byChild.values()].map((child) => (
        <Box
          component="li"
          key={child.label}
          sx={{ display: 'flex', gap: 1, alignItems: 'baseline', typography: 'body2' }}
        >
          <Box
            aria-hidden="true"
            sx={{
              width: 8,
              height: 8,
              flexShrink: 0,
              borderRadius: '50%',
              transform: 'translateY(-1px)',
              bgcolor: child.contains ? tokens.contains : tokens.caution,
            }}
          />
          <span>
            <strong>{child.label}:</strong> {child.items.join(', ')}
          </span>
        </Box>
      ))}
    </Box>
  )
}

export type VerdictTone = 'clear' | 'contains' | 'caution'

const TONES: Record<VerdictTone, { bar: string; bg: string; icon: ReactNode }> = {
  clear: { bar: tokens.sage, bg: tokens.sageTint, icon: <CheckCircleOutline fontSize="small" /> },
  contains: { bar: tokens.contains, bg: tokens.containsTint, icon: <ErrorOutline fontSize="small" /> },
  caution: { bar: tokens.caution, bg: tokens.cautionTint, icon: <WarningAmber fontSize="small" /> },
}

/** A coloured verdict strip; the words carry the meaning, the colour and icon reinforce it. */
export function Verdict({
  tone,
  role,
  children,
  icon,
}: {
  tone: VerdictTone
  role?: string
  children: ReactNode
  icon?: ReactNode
}) {
  const t = TONES[tone]
  return (
    <Box
      role={role}
      className={`safety ${tone === 'clear' ? 'clear' : 'warning'}`}
      sx={{
        display: 'flex',
        gap: 1,
        alignItems: 'flex-start',
        bgcolor: t.bg,
        borderLeft: `4px solid ${t.bar}`,
        borderRadius: 1,
        px: 1.25,
        py: 0.75,
        '& svg': { color: t.bar, mt: '2px' },
      }}
    >
      <Box aria-hidden="true" sx={{ display: 'flex' }}>
        {icon ?? t.icon}
      </Box>
      <Box sx={{ minWidth: 0 }}>{children}</Box>
    </Box>
  )
}

function toneFor(matches: SafetyMatch[]): VerdictTone {
  return matches.some((m) => m.matchType === 'CONTAINS') ? 'contains' : 'caution'
}

/**
 * The allergen check for the signed-in parent's children, on a product (FR-5).
 * CLEAR never says "safe": it says no avoid list matched; the label is still the final word.
 */
export function SafetyNotice({ safety }: { safety: ProductSafety | undefined }) {
  if (!safety) return null
  switch (safety.status) {
    case 'CLEAR':
      return (
        <Verdict tone="clear">
          <Typography variant="body2">No match with your children's avoid lists.</Typography>
        </Verdict>
      )
    case 'WARNING':
      return (
        <Verdict tone={toneFor(safety.matches)} role="note">
          <Typography variant="body2" component="p">
            <strong>Allergen warning</strong>
          </Typography>
          <MatchList matches={safety.matches} />
        </Verdict>
      )
    case 'UNVERIFIED':
      return (
        <Verdict tone="caution" role="note" icon={<HelpOutline fontSize="small" />}>
          <Typography variant="body2">
            <strong>Unable to verify allergens</strong> for {safety.affectedProfiles.map((p) => p.label).join(', ')}.
          </Typography>
        </Verdict>
      )
    case 'UNAVAILABLE':
      return (
        <Verdict tone="caution" role="note" icon={<HelpOutline fontSize="small" />}>
          <Typography variant="body2">
            <strong>Couldn't check allergens right now.</strong> Treat as unverified.
          </Typography>
        </Verdict>
      )
  }
}
