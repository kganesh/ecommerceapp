import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  api,
  unwrap,
  type Allergen,
  type AllergenEntry,
  type Product,
} from './client'

export const catalogKeys = {
  allergens: ['allergens'] as const,
  search: (q: string, page: number, hideUnsafe: boolean) => ['products', 'search', q, page, hideUnsafe] as const,
  product: (id: string) => ['products', id] as const,
  adminSearch: (q: string, page: number) => ['admin', 'products', q, page] as const,
  adminProduct: (id: string) => ['admin', 'products', id] as const,
  audit: (id: string) => ['admin', 'products', id, 'audit'] as const,
}

export function useAllergens() {
  return useQuery({
    queryKey: catalogKeys.allergens,
    queryFn: async () => unwrap(await api.GET('/api/v1/allergens')),
    staleTime: 5 * 60_000,
  })
}

export function useProductSearch(q: string, page = 0, hideUnsafe = false) {
  return useQuery({
    queryKey: catalogKeys.search(q, page, hideUnsafe),
    queryFn: async () =>
      unwrap(
        await api.GET('/api/v1/products', {
          params: { query: { q: q || undefined, page, size: 20, hideUnsafe: hideUnsafe || undefined } },
        }),
      ),
    placeholderData: keepPreviousData,
  })
}

export function useProduct(id: string) {
  return useQuery({
    queryKey: catalogKeys.product(id),
    queryFn: async () => unwrap(await api.GET('/api/v1/products/{id}', { params: { path: { id } } })),
  })
}

export function useAdminProductSearch(q: string, page = 0) {
  return useQuery({
    queryKey: catalogKeys.adminSearch(q, page),
    queryFn: async () =>
      unwrap(await api.GET('/api/v1/admin/products', { params: { query: { q: q || undefined, page, size: 50 } } })),
    placeholderData: keepPreviousData,
    retry: false,
  })
}

export function useAdminProduct(id: string | undefined) {
  return useQuery({
    queryKey: catalogKeys.adminProduct(id ?? 'new'),
    queryFn: async () => unwrap(await api.GET('/api/v1/admin/products/{id}', { params: { path: { id: id! } } })),
    enabled: id !== undefined,
    retry: false,
  })
}

export function useProductAudit(id: string | undefined) {
  return useQuery({
    queryKey: catalogKeys.audit(id ?? 'new'),
    queryFn: async () =>
      unwrap(await api.GET('/api/v1/admin/products/{id}/audit', { params: { path: { id: id! } } })),
    enabled: id !== undefined,
    retry: false,
  })
}

export interface ProductDetailsInput {
  sku: string
  name: string
  description: string
  priceCents: number
  active: boolean
}

function invalidateProducts(client: ReturnType<typeof useQueryClient>, product: Product) {
  client.setQueryData(catalogKeys.adminProduct(product.id), product)
  void client.invalidateQueries({ queryKey: ['products'] })
  void client.invalidateQueries({ queryKey: ['admin', 'products'] })
}

export function useCreateProduct() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: async (input: {
      details: ProductDetailsInput
      allergens: AllergenEntry[] | null
      labelExtractionId?: string
    }) =>
      unwrap(
        await api.POST('/api/v1/admin/products', {
          body: {
            sku: input.details.sku,
            name: input.details.name,
            description: input.details.description || undefined,
            priceCents: input.details.priceCents,
            allergens: input.allergens ?? undefined,
            labelExtractionId: input.allergens === null ? undefined : input.labelExtractionId,
          },
        }),
      ),
    onSuccess: (product) => invalidateProducts(client, product),
  })
}

export function useUpdateProduct(id: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: async (details: ProductDetailsInput) =>
      unwrap(
        await api.PUT('/api/v1/admin/products/{id}', {
          params: { path: { id } },
          body: {
            name: details.name,
            description: details.description || undefined,
            priceCents: details.priceCents,
            active: details.active,
          },
        }),
      ),
    onSuccess: (product) => invalidateProducts(client, product),
  })
}

export function useSetAllergens(id: string) {
  const client = useQueryClient()
  return useMutation({
    /** labelExtractionId: the label photo reading the admin reviewed, kept in the audit (FR-19). */
    mutationFn: async (input: { allergens: AllergenEntry[]; labelExtractionId?: string }) =>
      unwrap(
        await api.PUT('/api/v1/admin/products/{id}/allergens', {
          params: { path: { id } },
          body: { allergens: input.allergens, labelExtractionId: input.labelExtractionId },
        }),
      ),
    onSuccess: (product) => invalidateProducts(client, product),
  })
}

/** Orders the taxonomy as a tree: each root followed by its children. */
export function asTree(allergens: Allergen[]): { node: Allergen; depth: number }[] {
  const children = new Map<string, Allergen[]>()
  for (const allergen of allergens) {
    if (allergen.parentId) {
      const list = children.get(allergen.parentId) ?? []
      list.push(allergen)
      children.set(allergen.parentId, list)
    }
  }
  const out: { node: Allergen; depth: number }[] = []
  const visit = (node: Allergen, depth: number) => {
    out.push({ node, depth })
    for (const child of children.get(node.id) ?? []) visit(child, depth + 1)
  }
  for (const root of allergens.filter((a) => !a.parentId)) visit(root, 0)
  return out
}
