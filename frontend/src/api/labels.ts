import { useMutation, useQuery } from '@tanstack/react-query'
import { api, unwrap, type Schemas } from './client'

export type LabelExtraction = Schemas['LabelExtraction']
export type SuggestedAllergen = Schemas['SuggestedAllergen']

/** Whether AI label reading is on (spec: AI assist). Off means the control is hidden. */
export function useLabelReadingStatus() {
  return useQuery({
    queryKey: ['admin', 'label-reading', 'status'],
    queryFn: async () => unwrap(await api.GET('/api/v1/admin/label-extractions/status')),
    staleTime: 5 * 60_000,
    retry: false,
  })
}

/** Uploads a label photo and returns suggestions. Nothing is saved to the catalog (FR-19). */
export function useExtractLabel() {
  return useMutation({
    mutationFn: async (photo: Blob) => {
      const form = new FormData()
      form.append('image', photo, 'label.jpg')
      return unwrap(
        await api.POST('/api/v1/admin/label-extractions', {
          // The generated type describes the multipart field; the browser sends the FormData as-is.
          body: form as never,
          bodySerializer: () => form,
        }),
      )
    },
  })
}

const MAX_EDGE = 1600

/**
 * Shrinks a camera photo to at most 1600 px on the long edge as JPEG, which keeps uploads
 * small and well under the 3.5 MB limit. Falls back to the original file where the browser
 * cannot decode it (the server checks the type and size either way).
 */
export async function prepareLabelPhoto(file: File): Promise<Blob> {
  if (typeof createImageBitmap !== 'function' || typeof document === 'undefined') return file
  try {
    const bitmap = await createImageBitmap(file)
    const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height))
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(bitmap.width * scale)
    canvas.height = Math.round(bitmap.height * scale)
    const context = canvas.getContext('2d')
    if (context === null) return file
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    bitmap.close()
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.85))
    return blob ?? file
  } catch {
    return file
  }
}
