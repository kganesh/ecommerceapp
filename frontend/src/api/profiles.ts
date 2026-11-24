import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, unwrap, type Schemas } from './client'

export type Profile = Schemas['Profile']

export const profileKeys = { all: ['profiles'] as const }

export function useProfiles(enabled = true) {
  return useQuery({
    queryKey: profileKeys.all,
    queryFn: async () => unwrap(await api.GET('/api/v1/profiles')),
    enabled,
    retry: false,
  })
}

function useProfileMutation<TInput>(run: (input: TInput) => Promise<unknown>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: run,
    onSuccess: () => client.invalidateQueries({ queryKey: profileKeys.all }),
  })
}

export function useCreateProfile() {
  return useProfileMutation(async (label: string) =>
    unwrap(await api.POST('/api/v1/profiles', { body: { label } })),
  )
}

export function useRenameProfile() {
  return useProfileMutation(async ({ id, label }: { id: string; label: string }) =>
    unwrap(await api.PATCH('/api/v1/profiles/{id}', { params: { path: { id } }, body: { label } })),
  )
}

export function useDeleteProfile() {
  return useProfileMutation(async (id: string) => {
    const result = await api.DELETE('/api/v1/profiles/{id}', { params: { path: { id } } })
    if (!result.response.ok) unwrap(result)
  })
}

export function useSetAvoidList() {
  return useProfileMutation(async ({ id, allergenIds }: { id: string; allergenIds: string[] }) =>
    unwrap(await api.PUT('/api/v1/profiles/{id}/avoid-list', { params: { path: { id } }, body: { allergenIds } })),
  )
}
