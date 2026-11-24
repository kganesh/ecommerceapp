import Add from '@mui/icons-material/Add'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router'
import { useAdminProductSearch } from '../../api/catalog'
import { ErrorNotice } from '../../components/ErrorNotice'
import { formatPrice } from '../../lib/format'
import { tokens } from '../../theme'

/** FR-14: catalog administration (CATALOG_ADMIN). */
export function AdminProductsPage() {
  const [q, setQ] = useState('')
  const products = useAdminProductSearch(q)

  return (
    <Box component="section" aria-labelledby="admin-heading">
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, flexWrap: 'wrap', mb: 3 }}>
        <Typography variant="h2" id="admin-heading">
          Catalog admin
        </Typography>
        <Button component={RouterLink} to="/admin/products/new" variant="contained" startIcon={<Add />}>
          Add a product
        </Button>
      </Box>

      <TextField
        id="admin-q"
        type="search"
        label="Filter by name or SKU"
        size="small"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        sx={{ mb: 2, width: { xs: '100%', sm: 360 } }}
      />

      {products.isPending && <Typography>Loading…</Typography>}
      {products.isError && <ErrorNotice error={products.error} />}
      {products.isSuccess && (
        <Paper sx={{ overflowX: 'auto' }}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>SKU</TableCell>
                <TableCell>Price</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Allergen data</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {products.data.items.map((p) => (
                <TableRow key={p.id} hover>
                  <TableCell>
                    <Link component={RouterLink} to={`/admin/products/${p.id}`} sx={{ fontWeight: 700 }}>
                      {p.name}
                    </Link>
                  </TableCell>
                  <TableCell>
                    <code>{p.sku}</code>
                  </TableCell>
                  <TableCell>{formatPrice(p.priceCents, p.currency)}</TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      label={p.active ? 'Active' : 'Inactive'}
                      sx={p.active ? { bgcolor: tokens.sageTint, color: tokens.sage } : undefined}
                    />
                  </TableCell>
                  <TableCell>
                    {p.allergenDataAvailable ? (
                      `Version ${p.allergenVersion}`
                    ) : (
                      <Typography component="span" variant="body2" sx={{ color: tokens.caution, fontWeight: 700 }}>
                        Missing — enter allergens
                      </Typography>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      )}
    </Box>
  )
}
