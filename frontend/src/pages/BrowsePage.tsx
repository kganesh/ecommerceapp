import InputAdornment from '@mui/material/InputAdornment'
import Search from '@mui/icons-material/Search'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Avatar from '@mui/material/Avatar'
import Chip from '@mui/material/Chip'
import IconButton from '@mui/material/IconButton'
import Switch from '@mui/material/Switch'
import { visuallyHidden } from '@mui/utils'
import EditOutlined from '@mui/icons-material/EditOutlined'
import ShieldOutlined from '@mui/icons-material/ShieldOutlined'
import FamilyRestroomOutlined from '@mui/icons-material/FamilyRestroomOutlined'
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
import { initials } from '../lib/format'
import { tokens } from '../theme'

/** Who the shop is checking against, so a parent can see it at a glance. */
function ChildrenSummary() {
  const profiles = useProfiles()
  const allergens = useAllergens()
  if (!profiles.isSuccess || !allergens.isSuccess) return null
  const names = new Map(allergens.data.map((a) => [a.id, a.displayName]))
  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
        <FamilyRestroomOutlined fontSize="small" aria-hidden sx={{ color: tokens.sage }} />
        <Typography variant="h4" component="h3">
          Checking for
        </Typography>
      </Box>
      {profiles.data.length === 0 ? (
        <Typography variant="body2">
          No children set up yet.{' '}
          <Link component={RouterLink} to="/profiles">
            Add a child
          </Link>
        </Typography>
      ) : (
        <Box component="ul" sx={{ m: 0, p: 0, listStyle: 'none', display: 'grid', gap: 1.5 }}>
          {profiles.data.map((p) => (
            <Box component="li" key={p.id} sx={{ display: 'flex', gap: 1.25, alignItems: 'flex-start' }}>
              <Avatar
                aria-hidden="true"
                sx={{ width: 32, height: 32, bgcolor: tokens.sageTint, color: tokens.sage, fontWeight: 700, fontSize: '0.8125rem' }}
              >
                {initials(p.label)}
              </Avatar>
              <Box sx={{ minWidth: 0 }}>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                  {p.label}
                </Typography>
                {p.avoidList.length === 0 ? (
                  <Typography variant="body2" color="text.secondary">
                    Avoids nothing yet
                  </Typography>
                ) : (
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mt: 0.5 }}>
                    <Box component="span" sx={visuallyHidden}>
                      Avoids{' '}
                    </Box>
                    {p.avoidList.map((id) => (
                      <Chip
                        key={id}
                        size="small"
                        label={names.get(id) ?? id}
                        sx={{ bgcolor: tokens.containsTint, color: tokens.contains, fontWeight: 600, height: 24 }}
                      />
                    ))}
                  </Box>
                )}
              </Box>
            </Box>
          ))}
        </Box>
      )}
      <Button
        component={RouterLink}
        to="/profiles"
        size="small"
        startIcon={<EditOutlined />}
        sx={{ mt: 1.5, ml: -0.75 }}
      >
        Edit avoid lists
      </Button>
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
          gridTemplateColumns: { xs: '1fr', md: '280px 1fr' },
          alignItems: 'start',
        }}
      >
        <Paper sx={{ p: 2, display: 'grid', gap: 2.5, position: { md: 'sticky' }, top: { md: 96 } }}>
          <Box component="form" role="search" onSubmit={submit}>
            <TextField
              id="q"
              type="search"
              fullWidth
              placeholder="Search products"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              slotProps={{
                htmlInput: { 'aria-label': 'Search products' },
                input: {
                  sx: { borderRadius: 999, bgcolor: tokens.canvas, pr: 0.5 },
                  endAdornment: (
                    <InputAdornment position="end">
                      <IconButton
                        type="submit"
                        aria-label="Search"
                        sx={{ bgcolor: tokens.sage, color: '#fff', '&:hover': { bgcolor: tokens.sageDark } }}
                        size="small"
                      >
                        <Search fontSize="small" />
                      </IconButton>
                    </InputAdornment>
                  ),
                },
              }}
            />
          </Box>
          {isShopper && (
            <Box
              sx={{
                borderRadius: 1.5,
                p: 1.5,
                bgcolor: hideUnsafe ? tokens.sageTint : tokens.canvas,
                border: `1px solid ${hideUnsafe ? tokens.sage : tokens.line}`,
                transition: 'background-color 120ms, border-color 120ms',
              }}
            >
              <FormControlLabel
                labelPlacement="start"
                sx={{ m: 0, width: '100%', justifyContent: 'space-between', alignItems: 'flex-start', gap: 1 }}
                control={
                  <Switch checked={hideUnsafe} onChange={(e) => setHideUnsafe(e.target.checked)} sx={{ mt: -0.5, mr: -1 }} />
                }
                label={
                  <Box sx={{ display: 'flex', gap: 1 }}>
                    <ShieldOutlined fontSize="small" aria-hidden sx={{ color: tokens.sage, mt: '2px' }} />
                    <Box>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>
                        Safe products only
                      </Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ fontSize: '0.8125rem' }}>
                        Hide products that match an avoid list or can't be verified
                      </Typography>
                    </Box>
                  </Box>
                }
              />
            </Box>
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
