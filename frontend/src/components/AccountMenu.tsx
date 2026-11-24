import { signIn, useMe, useSignOut } from '../api/session'

export function AccountMenu() {
  const me = useMe()
  const signOut = useSignOut()

  if (me.isPending) return null
  if (!me.data?.authenticated) {
    return (
      <button type="button" className="account" onClick={() => signIn(me.data)}>
        Sign in
      </button>
    )
  }
  return (
    <div className="account">
      <span>Signed in as {me.data.username}</span>{' '}
      <button type="button" onClick={() => signOut.mutate()} disabled={signOut.isPending}>
        Sign out
      </button>
    </div>
  )
}
