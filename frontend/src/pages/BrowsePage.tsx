import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Checkbox from '@mui/material/Checkbox'
import FormControlLabel from '@mui/material/FormControlLabel'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useState, type FormEvent } from 'react'
import { Link as RouterLink, useSearchParams } from 'react-router'
import { useAllergens, useProductSearch } from '../api/catalog'
import { useProfiles } from '../api/profiles'
import { hasRole, useMe } from '../api/session'
import { ErrorNotice } from '../components/ErrorNotice'
import { ProductCard } from '../components/ProductCard'
import { tokens } from '../theme'

/** Who the shop is checking against, so a parent can see it at a glance. */
function ChildrenSummary() {
  const profiles = useProfiles()
  const allergens = useAllergens()
  if (!profiles.isSuccess || !allergens.isSuccess) return null
  const names = new Map(allergens.data.map((a) => [a.id, a.displayName]))
  return (
    <Box>
      <Typography variant="h4" component="h3" sx={{ mb: 1 }}>
        Checking for
      </Typography>
      {profiles.data.length === 0 ? (
        <Typography variant="body2">
          No children set up yet.{' '}
          <Link component={RouterLink} to="/profiles">
            Add a child
          </Link>
        </Typography>
      ) : (
        <Box component="ul" sx={{ m: 0, p: 0, listStyle: 'none', display: 'grid', gap: 1 }}>
          {profiles.data.map((p) => (
            <Typography component="li" variant="body2" key={p.id}>
              <strong>{p.label}</strong>
              <br />
              {p.avoidList.length === 0 ? 'Avoids nothing yet' : `Avoids ${p.avoidList.map((id) => names.get(id) ?? id).join(', ')}`}
            </Typography>
          ))}
        </Box>
      )}
      <Link component={RouterLink} to="/profiles" variant="body2" sx={{ display: 'inline-block', mt: 1.5 }}>
        Edit avoid lists
      </Link>
    </Box>
  )
}

/** FR-4, FR-5: browse and search, with each product checked against the children's avoid lists. */
export function BrowsePage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const hideUnsafe = params.get('hideUnsafe') === 'true'
  const [draft, setDraft] = useState(q)
  const me = useMe()
  const isShopper = hasRole(me.data, 'SHOPPER')
  const search = useProductSearch(q, 0, isShopper && hideUnsafe)

  const setHideUnsafe = (hide: boolean) => {
    const next = new URLSearchParams(params)
    if (hide) next.set('hideUnsafe', 'true')
    else next.delete('hideUnsafe')
    setParams(next)
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const next = new URLSearchParams(params)
    if (draft.trim()) next.set('q', draft.trim())
    else next.delete('q')
    setParams(next)
  }

  return (
    <Box component="section" aria-labelledby="browse-heading">
      <Box sx={{ mb: 3 }}>
        <Typography variant="h2" id="browse-heading">
          Shop
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 0.5, maxWidth: '62ch' }}>
          {isShopper
            ? "Every product is checked against your children's avoid lists, including “may contain” labels."
            : 'Sign in to check every product against your children’s avoid lists.'}
        </Typography>
      </Box>

      <Box
        sx={{
          display: 'grid',
          gap: 3,
          gridTemplateColumns: { xs: '1fr', md: '260px 1fr' },
          alignItems: 'start',
        }}
      >
        <Paper sx={{ p: 2, display: 'grid', gap: 2.5, position: { md: 'sticky' }, top: { md: 96 } }}>
          <Box component="form" role="search" onSubmit={submit} sx={{ display: 'grid', gap: 1 }}>
            <TextField
              id="q"
              type="search"
              label="Search products"
              size="small"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
            />
            <Button type="submit" variant="outlined">
              Search
            </Button>
          </Box>
          {isShopper && (
            <FormControlLabel
              sx={{ alignItems: 'flex-start', mr: 0, '& .MuiCheckbox-root': { pt: 0.25 } }}
              control={<Checkbox checked={hideUnsafe} onChange={(e) => setHideUnsafe(e.target.checked)} />}
              label={
                <Typography variant="body2">
                  Hide products that match my children's avoid lists or can't be verified
                </Typography>
              }
            />
          )}
          {isShopper && (
            <Box sx={{ borderTop: `1px solid ${tokens.line}`, pt: 2, display: { xs: 'none', md: 'block' } }}>
              <ChildrenSummary />
            </Box>
          )}
        </Paper>

        <Box>
          {search.isPending && <Typography>Loading products…</Typography>}
          {search.isError && <ErrorNotice error={search.error} />}
          {search.isSuccess && search.data.items.length === 0 && (
            <Paper sx={{ p: 3 }}>
              <Typography variant="h4" component="p">
                No products found.
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                {hideUnsafe ? 'Some products are hidden because they match an avoid list. ' : ''}Try a different
                search.
              </Typography>
            </Paper>
          )}
          {search.isSuccess && search.data.items.length > 0 && (
            <>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                {search.data.total} {search.data.total === 1 ? 'product' : 'products'}
                {q ? ` for “${q}”` : ''}
              </Typography>
              <Box
                component="ul"
                className="product-list"
                sx={{
                  m: 0,
                  p: 0,
                  listStyle: 'none',
                  display: 'grid',
                  gap: 2,
                  gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
                }}
              >
                {search.data.items.map((product) => (
                  <ProductCard key={product.id} product={product} />
                ))}
              </Box>
            </>
          )}
        </Box>
      </Box>
    </Box>
  )
}
