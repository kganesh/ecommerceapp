import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Typography from '@mui/material/Typography'
import { signIn, useMe, useSignOut } from '../api/session'

export function AccountMenu() {
  const me = useMe()
  const signOut = useSignOut()

  if (me.isPending) return null
  if (!me.data?.authenticated) {
    return (
      <Button variant="contained" onClick={() => signIn(me.data)}>
        Sign in
      </Button>
    )
  }
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
      <Typography variant="body2" color="text.secondary">
        Signed in as {me.data.username}
      </Typography>
      <Button variant="outlined" onClick={() => signOut.mutate()} disabled={signOut.isPending}>
        Sign out
      </Button>
    </Box>
  )
}
