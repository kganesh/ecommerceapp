import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
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
import { toEntries, toSelection, type AllergenSelection } from '../../lib/format'

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
    <>
      <label htmlFor="sku">SKU</label>
      <input id="sku" required disabled={!isNew} value={value.sku} onChange={(e) => onChange({ ...value, sku: e.target.value })} />
      <label htmlFor="name">Name</label>
      <input id="name" required value={value.name} onChange={(e) => onChange({ ...value, name: e.target.value })} />
      <label htmlFor="description">Description</label>
      <textarea
        id="description"
        value={value.description}
        onChange={(e) => onChange({ ...value, description: e.target.value })}
      />
      <label htmlFor="price">Price (USD)</label>
      <input
        id="price"
        type="number"
        min="0"
        step="0.01"
        required
        defaultValue={(value.priceCents / 100).toFixed(2)}
        onChange={(e) => onChange({ ...value, priceCents: Math.round(Number(e.target.value) * 100) })}
      />
      {!isNew && (
        <label className="checkbox">
          <input
            type="checkbox"
            checked={value.active}
            onChange={(e) => onChange({ ...value, active: e.target.checked })}
          />
          Active (visible to shoppers)
        </label>
      )}
    </>
  )
}

function NewProductForm() {
  const navigate = useNavigate()
  const allergens = useAllergens()
  const create = useCreateProduct()
  const [details, setDetails] = useState<ProductDetailsInput>(EMPTY)
  const [selection, setSelection] = useState<AllergenSelection>({})
  const [allergenDataKnown, setAllergenDataKnown] = useState(true)

  const submit = (event: FormEvent) => {
    event.preventDefault()
    create.mutate(
      { details, allergens: allergenDataKnown ? toEntries(selection) : null },
      { onSuccess: (created) => navigate(`/admin/products/${created.id}`) },
    )
  }

  return (
    <form onSubmit={submit} className="stacked">
      <DetailsFields value={details} onChange={setDetails} isNew />
      <fieldset>
        <legend>Allergens (version 1)</legend>
        <label className="checkbox">
          <input type="checkbox" checked={allergenDataKnown} onChange={(e) => setAllergenDataKnown(e.target.checked)} />I
          have the label and have entered its allergens
        </label>
        {!allergenDataKnown && (
          <p className="problem">Shoppers will see “Unable to verify allergens” until allergens are entered.</p>
        )}
        {allergens.isSuccess && (
          <AllergenEditor
            allergens={allergens.data}
            value={selection}
            onChange={setSelection}
            disabled={!allergenDataKnown}
          />
        )}
      </fieldset>
      {create.isError && <ErrorNotice error={create.error} />}
      <button type="submit" disabled={create.isPending}>
        Create product
      </button>
    </form>
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

  const submitDetails = (event: FormEvent) => {
    event.preventDefault()
    update.mutate(details)
  }

  const submitAllergens = (event: FormEvent) => {
    event.preventDefault()
    setAllergens.mutate(toEntries(selection))
  }

  return (
    <>
      <form onSubmit={submitDetails} className="stacked">
        <h3>Details</h3>
        <DetailsFields value={details} onChange={setDetails} isNew={false} />
        {update.isError && <ErrorNotice error={update.error} />}
        {update.isSuccess && <p role="status">Details saved.</p>}
        <button type="submit" disabled={update.isPending}>
          Save details
        </button>
      </form>

      <form onSubmit={submitAllergens} className="stacked">
        <h3>Allergens</h3>
        <p>
          {product.allergenDataAvailable
            ? `Current allergen version: ${product.allergenVersion}. Saving creates a new version; earlier versions are kept.`
            : 'No allergen data yet. Shoppers see “Unable to verify allergens”.'}
        </p>
        {allergens.isSuccess && <AllergenEditor allergens={allergens.data} value={selection} onChange={setSelection} />}
        {setAllergens.isError && <ErrorNotice error={setAllergens.error} />}
        <button type="submit" disabled={setAllergens.isPending}>
          Save allergens as new version
        </button>
      </form>

      <h3>Edit history</h3>
      {audit.isSuccess && (
        <table>
          <thead>
            <tr>
              <th scope="col">When</th>
              <th scope="col">Who</th>
              <th scope="col">What</th>
            </tr>
          </thead>
          <tbody>
            {audit.data.map((entry, i) => (
              <tr key={i}>
                <td>{new Date(entry.at).toLocaleString()}</td>
                <td>{entry.actor}</td>
                <td>
                  {entry.action === 'ALLERGENS_CHANGED'
                    ? `Allergens: version ${entry.fromVersion ?? '—'} → ${entry.toVersion}`
                    : entry.action === 'PRODUCT_CREATED'
                      ? 'Created'
                      : 'Details updated'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  )
}

/** FR-14: create a product, edit its details, and change allergens (each change = new version). */
export function ProductEditorPage() {
  const { id } = useParams()
  const product = useAdminProduct(id)

  return (
    <section>
      <p>
        <Link to="/admin/products">← All products</Link>
      </p>
      {id === undefined && (
        <>
          <h2>Add a product</h2>
          <NewProductForm />
        </>
      )}
      {id !== undefined && product.isPending && <p>Loading…</p>}
      {id !== undefined && product.isError && <ErrorNotice error={product.error} />}
      {id !== undefined && product.isSuccess && (
        <>
          <h2>{product.data.name}</h2>
          {product.data.allergenVersion !== undefined && (
            <p role="status">Allergen version {product.data.allergenVersion} is live.</p>
          )}
          <ExistingProductForms
            key={`${product.data.id}:${product.data.allergenVersion ?? 'none'}`}
            product={product.data}
          />
        </>
      )}
    </section>
  )
}
