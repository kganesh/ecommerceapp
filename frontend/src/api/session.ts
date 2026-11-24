import { useMutation, useQuery } from '@tanstack/react-query'
import { api, readCookie, unwrap, type Schemas } from './client'

export type Me = Schemas['Me']

export const sessionKeys = { me: ['me'] as const }

/** Who is signed in. The backend never exposes tokens: only identity and roles. */
export function useMe() {
  return useQuery({
    queryKey: sessionKeys.me,
    queryFn: async () => unwrap(await api.GET('/api/v1/me')),
    staleTime: 60_000,
    retry: false,
  })
}

export function hasRole(me: Me | undefined, role: 'SHOPPER' | 'CATALOG_ADMIN'): boolean {
  return me?.authenticated === true && me.roles.includes(role)
}

/** Full-page navigation: the backend runs the OIDC Authorization Code + PKCE flow. */
export function signIn(me: Me | undefined) {
  window.location.assign(me?.loginUrl ?? '/oauth2/authorization/idp')
}

/** Ends the server session, then the identity-provider session. */
export function useSignOut() {
  return useMutation({
    mutationFn: async () => {
      const token = readCookie('XSRF-TOKEN')
      const response = await fetch('/logout', {
        method: 'POST',
        credentials: 'same-origin',
        headers: token === undefined ? {} : { 'X-XSRF-TOKEN': token },
      })
      if (!response.ok) throw new Error(`Sign-out failed with HTTP ${response.status}`)
      const body = (await response.json()) as { redirectUrl?: string }
      return body.redirectUrl ?? '/'
    },
    onSuccess: (redirectUrl) => window.location.assign(redirectUrl),
  })
}
