import ArrowBack from '@mui/icons-material/ArrowBack'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Checkbox from '@mui/material/Checkbox'
import FormControlLabel from '@mui/material/FormControlLabel'
import Paper from '@mui/material/Paper'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useState, type FormEvent } from 'react'
import { Link as RouterLink, useNavigate, useParams } from 'react-router'
import {
  useAdminProduct,
  useAllergens,
  useCreateProduct,
  useProductAudit,
  useSetAllergens,
  useUpdateProduct,
  type ProductDetailsInput,
} from '../../api/catalog'
import type { Product } from '../../api/client'
import { AllergenEditor } from '../../components/AllergenEditor'
import { ErrorNotice } from '../../components/ErrorNotice'
import { LabelPhotoReader } from '../../components/LabelPhotoReader'
import { formatDateTime, toEntries, toSelection, type AllergenSelection } from '../../lib/format'

const EMPTY: ProductDetailsInput = { sku: '', name: '', description: '', priceCents: 0, active: true }

function DetailsFields({
  value,
  onChange,
  isNew,
}: {
  value: ProductDetailsInput
  onChange: (next: ProductDetailsInput) => void
  isNew: boolean
}) {
  return (
    <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
      <TextField id="sku" label="SKU" required disabled={!isNew} value={value.sku} onChange={(e) => onChange({ ...value, sku: e.target.value })} />
      <TextField
        id="price"
        label="Price (USD)"
        type="number"
        required
        defaultValue={(value.priceCents / 100).toFixed(2)}
        onChange={(e) => onChange({ ...value, priceCents: Math.round(Number(e.target.value) * 100) })}
        slotProps={{ htmlInput: { min: 0, step: 0.01 } }}
      />
      <TextField
        id="name"
        label="Name"
        required
        value={value.name}
        onChange={(e) => onChange({ ...value, name: e.target.value })}
        sx={{ gridColumn: { sm: '1 / -1' } }}
      />
      <TextField
        id="description"
        label="Description"
        multiline
        minRows={2}
        value={value.description}
        onChange={(e) => onChange({ ...value, description: e.target.value })}
        sx={{ gridColumn: { sm: '1 / -1' } }}
      />
      {!isNew && (
        <FormControlLabel
          control={<Checkbox checked={value.active} onChange={(e) => onChange({ ...value, active: e.target.checked })} />}
          label="Active (visible to shoppers)"
        />
      )}
    </Box>
  )
}

function NewProductForm() {
  const navigate = useNavigate()
  const allergens = useAllergens()
  const create = useCreateProduct()
  const [details, setDetails] = useState<ProductDetailsInput>(EMPTY)
  const [selection, setSelection] = useState<AllergenSelection>({})
  const [allergenDataKnown, setAllergenDataKnown] = useState(true)
  const [labelExtractionId, setLabelExtractionId] = useState<string>()

  const submit = (event: FormEvent) => {
    event.preventDefault()
    create.mutate(
      { details, allergens: allergenDataKnown ? toEntries(selection) : null, labelExtractionId },
      { onSuccess: (created) => navigate(`/admin/products/${created.id}`) },
    )
  }

  return (
    <Box component="form" onSubmit={submit} sx={{ display: 'grid', gap: 3 }}>
      <Paper sx={{ p: { xs: 2, md: 3 } }}>
        <Typography variant="h3" sx={{ mb: 2 }}>
          Details
        </Typography>
        <DetailsFields value={details} onChange={setDetails} isNew />
      </Paper>
      <Paper component="fieldset" sx={{ p: { xs: 2, md: 3 }, m: 0, display: 'grid', gap: 1.5 }}>
        <Typography component="legend" variant="h3" sx={{ float: 'left', mb: 1 }}>
          Allergens (version 1)
        </Typography>
        <FormControlLabel
          control={<Checkbox checked={allergenDataKnown} onChange={(e) => setAllergenDataKnown(e.target.checked)} />}
          label="I have the label and have entered its allergens"
        />
        {!allergenDataKnown && (
          <Alert severity="warning">Shoppers will see “Unable to verify allergens” until allergens are entered.</Alert>
        )}
        {allergenDataKnown && (
          <LabelPhotoReader
            onApply={(next, extractionId) => {
              setSelection(next)
              setLabelExtractionId(extractionId)
            }}
          />
        )}
        {allergens.isSuccess && (
          <AllergenEditor allergens={allergens.data} value={selection} onChange={setSelection} disabled={!allergenDataKnown} />
        )}
      </Paper>
      {create.isError && <ErrorNotice error={create.error} />}
      <Box>
        <Button type="submit" variant="contained" size="large" disabled={create.isPending}>
          Create product
        </Button>
      </Box>
    </Box>
  )
}

/** Keyed by product id + allergen version, so its form state starts from the saved product. */
function ExistingProductForms({ product }: { product: Product }) {
  const allergens = useAllergens()
  const audit = useProductAudit(product.id)
  const update = useUpdateProduct(product.id)
  const setAllergens = useSetAllergens(product.id)
  const [details, setDetails] = useState<ProductDetailsInput>({
    sku: product.sku,
    name: product.name,
    description: product.description ?? '',
    priceCents: product.priceCents,
    active: product.active,
  })
  const [selection, setSelection] = useState<AllergenSelection>(() => toSelection(product.allergens))
  const [labelExtractionId, setLabelExtractionId] = useState<string>()

  const submitDetails = (event: FormEvent) => {
    event.preventDefault()
    update.mutate(details)
  }

  const submitAllergens = (event: FormEvent) => {
    event.preventDefault()
    setAllergens.mutate({ allergens: toEntries(selection), labelExtractionId })
  }

  return (
    <Box sx={{ display: 'grid', gap: 3 }}>
      <Paper component="form" onSubmit={submitDetails} sx={{ p: { xs: 2, md: 3 }, display: 'grid', gap: 2 }}>
        <Typography variant="h3">Details</Typography>
        <DetailsFields value={details} onChange={setDetails} isNew={false} />
        {update.isError && <ErrorNotice error={update.error} />}
        {update.isSuccess && (
          <Alert severity="success" role="status">
            Details saved.
          </Alert>
        )}
        <Box>
          <Button type="submit" variant="contained" disabled={update.isPending}>
            Save details
          </Button>
        </Box>
      </Paper>

      <Paper component="form" onSubmit={submitAllergens} sx={{ p: { xs: 2, md: 3 }, display: 'grid', gap: 2 }}>
        <Typography variant="h3">Allergens</Typography>
        <Typography variant="body2" color="text.secondary">
          {product.allergenDataAvailable
            ? `Current allergen version: ${product.allergenVersion}. Saving creates a new version; earlier versions are kept.`
            : 'No allergen data yet. Shoppers see “Unable to verify allergens”.'}
        </Typography>
        <LabelPhotoReader
          onApply={(next, extractionId) => {
            setSelection(next)
            setLabelExtractionId(extractionId)
          }}
        />
        {allergens.isSuccess && <AllergenEditor allergens={allergens.data} value={selection} onChange={setSelection} />}
        {setAllergens.isError && <ErrorNotice error={setAllergens.error} />}
        <Box>
          <Button type="submit" variant="contained" disabled={setAllergens.isPending}>
            Save allergens as new version
          </Button>
        </Box>
      </Paper>

      <Paper sx={{ p: { xs: 2, md: 3 } }}>
        <Typography variant="h3" sx={{ mb: 1.5 }}>
          Edit history
        </Typography>
        {audit.isSuccess && (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>When</TableCell>
                <TableCell>Who</TableCell>
                <TableCell>What</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {audit.data.map((entry, i) => (
                <TableRow key={i}>
                  <TableCell>{formatDateTime(entry.at)}</TableCell>
                  <TableCell>{entry.actor}</TableCell>
                  <TableCell>
                    {entry.action === 'ALLERGENS_CHANGED'
                      ? `Allergens: version ${entry.fromVersion ?? '—'} → ${entry.toVersion}${
                          entry.labelExtractionId ? ' (reviewed from a label photo)' : ''
                        }`
                      : entry.action === 'PRODUCT_CREATED'
                        ? 'Created'
                        : 'Details updated'}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Paper>
    </Box>
  )
}

/** FR-14: create a product, edit its details, and change allergens (each change = new version). */
export function ProductEditorPage() {
  const { id } = useParams()
  const product = useAdminProduct(id)

  return (
    <Box component="section" sx={{ maxWidth: 880 }}>
      <Button component={RouterLink} to="/admin/products" startIcon={<ArrowBack />} color="inherit" sx={{ mb: 2 }}>
        All products
      </Button>
      {id === undefined && (
        <>
          <Typography variant="h2" sx={{ mb: 3 }}>
            Add a product
          </Typography>
          <NewProductForm />
        </>
      )}
      {id !== undefined && product.isPending && <Typography>Loading…</Typography>}
      {id !== undefined && product.isError && <ErrorNotice error={product.error} />}
      {id !== undefined && product.isSuccess && (
        <>
          <Typography variant="h2">{product.data.name}</Typography>
          {product.data.allergenVersion !== undefined && (
            <Typography role="status" color="text.secondary" sx={{ mt: 0.5, mb: 3 }}>
              Allergen version {product.data.allergenVersion} is live.
            </Typography>
          )}
          <ExistingProductForms key={`${product.data.id}:${product.data.allergenVersion ?? 'none'}`} product={product.data} />
        </>
      )}
    </Box>
  )
}
