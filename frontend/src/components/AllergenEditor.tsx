import Box from '@mui/material/Box'
import NativeSelect from '@mui/material/NativeSelect'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import { asTree } from '../api/catalog'
import type { Allergen } from '../api/client'
import type { AllergenSelection } from '../lib/format'
import { tokens } from '../theme'

/** One select per taxonomy node: not on label, contains, or may contain. */
export function AllergenEditor({
  allergens,
  value,
  onChange,
  disabled = false,
}: {
  allergens: Allergen[]
  value: AllergenSelection
  onChange: (next: AllergenSelection) => void
  disabled?: boolean
}) {
  const set = (id: string, choice: string) => {
    const next = { ...value }
    if (choice === 'CONTAINS' || choice === 'MAY_CONTAIN') next[id] = choice
    else delete next[id]
    onChange(next)
  }

  return (
    <Box sx={{ border: `1px solid ${tokens.line}`, borderRadius: 1, overflowX: 'auto' }}>
      <Table size="small" className="allergen-editor">
        <TableHead>
          <TableRow>
            <TableCell>Allergen</TableCell>
            <TableCell sx={{ width: 190 }}>On the label</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {asTree(allergens).map(({ node, depth }) => {
            const choice = value[node.id] ?? 'NONE'
            return (
              <TableRow
                key={node.id}
                sx={{
                  bgcolor:
                    choice === 'CONTAINS' ? tokens.containsTint : choice === 'MAY_CONTAIN' ? tokens.cautionTint : undefined,
                }}
              >
                <TableCell component="th" scope="row" sx={{ pl: 2 + depth * 3 }}>
                  <label htmlFor={`allergen-${node.id}`}>
                    <Typography component="span" variant="body2" sx={{ fontWeight: depth === 0 ? 700 : 400 }}>
                      {node.displayName}
                    </Typography>
                  </label>
                  {node.synonyms.length > 0 && (
                    <Typography variant="body2" color="text.secondary" sx={{ fontSize: '0.8125rem' }}>
                      Label terms: {node.synonyms.join(', ')}
                    </Typography>
                  )}
                </TableCell>
                <TableCell>
                  <NativeSelect
                    value={choice}
                    disabled={disabled}
                    onChange={(e) => set(node.id, e.target.value)}
                    inputProps={{ id: `allergen-${node.id}` }}
                    sx={{ width: '100%', typography: 'body2' }}
                  >
                    <option value="NONE">Not on label</option>
                    <option value="CONTAINS">Contains</option>
                    <option value="MAY_CONTAIN">May contain</option>
                  </NativeSelect>
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </Box>
  )
}
