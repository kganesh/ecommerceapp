import type { AllergenEntry, MatchType } from '../api/client'

export function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}

export function formatPrice(priceCents: number, currency: string): string {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(priceCents / 100)
}

/** Allergen id → match type, as edited in the admin allergen editor. */
export type AllergenSelection = Record<string, MatchType>

export function toEntries(selection: AllergenSelection): AllergenEntry[] {
  return Object.entries(selection).map(([allergenId, matchType]) => ({ allergenId, matchType }))
}

export function toSelection(entries: { allergenId: string; matchType: MatchType }[]): AllergenSelection {
  return Object.fromEntries(entries.map((e) => [e.allergenId, e.matchType]))
}

/** "Child 1" -> "C1": a short, stable badge for a child profile. */
export function initials(label: string): string {
  const trimmed = label.trim()
  const digits = trimmed.match(/\d+$/)?.[0] ?? ''
  return (trimmed.charAt(0).toUpperCase() + digits).slice(0, 3)
}
