import Box from '@mui/material/Box'
import Checkbox from '@mui/material/Checkbox'
import FormControlLabel from '@mui/material/FormControlLabel'
import Typography from '@mui/material/Typography'
import type { Allergen } from '../api/client'
import { coveringAncestor, toggleAllergen } from '../lib/avoidList'

/**
 * FR-3: pick allergens to avoid. Roots (Peanut, Tree nuts, Milk…) form the grid; tree nuts
 * expand beneath their group. Choosing a group covers every item in it, shown as checked
 * with "covered by".
 */
export function AvoidListPicker({
  idPrefix,
  allergens,
  selected,
  onChange,
}: {
  idPrefix: string
  allergens: Allergen[]
  selected: ReadonlySet<string>
  onChange: (next: Set<string>) => void
}) {
  const byId = new Map(allergens.map((a) => [a.id, a]))
  const roots = allergens.filter((a) => !a.parentId)
  const childrenOf = (id: string) => allergens.filter((a) => a.parentId === id)

  const option = (node: Allergen) => {
    const coveredBy = coveringAncestor(node.id, selected, byId)
    const inputId = `${idPrefix}-${node.id}`
    return (
      <FormControlLabel
        key={node.id}
        sx={{ mr: 0, '& .MuiFormControlLabel-label': { typography: 'body2' } }}
        control={
          <Checkbox
            size="small"
            id={inputId}
            checked={selected.has(node.id) || coveredBy !== undefined}
            disabled={coveredBy !== undefined}
            onChange={() => onChange(toggleAllergen(node.id, selected, allergens))}
          />
        }
        label={
          <span>
            {node.displayName}
            {coveredBy && (
              <Typography component="span" variant="body2" color="text.secondary">
                {' '}
                <span>(covered by {coveredBy.displayName})</span>
              </Typography>
            )}
          </span>
        }
      />
    )
  }

  const leaves = roots.filter((r) => childrenOf(r.id).length === 0)
  const groups = roots.filter((r) => childrenOf(r.id).length > 0)

  return (
    <Box sx={{ display: 'grid', gap: 2 }}>
      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(170px, 1fr))', columnGap: 1 }}>
        {leaves.map(option)}
      </Box>
      {groups.map((group) => (
        <Box key={group.id} sx={{ display: 'grid', gap: 0.5 }}>
          {option(group)}
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(170px, 1fr))',
              columnGap: 1,
              pl: 3.5,
              borderLeft: '2px solid',
              borderColor: 'divider',
              ml: 1.25,
            }}
          >
            {childrenOf(group.id).map(option)}
          </Box>
        </Box>
      ))}
    </Box>
  )
}
