import LoginOutlined from '@mui/icons-material/LoginOutlined'
import LogoutOutlined from '@mui/icons-material/LogoutOutlined'
import AccountCircleOutlined from '@mui/icons-material/AccountCircleOutlined'
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
      <Button variant="contained" startIcon={<LoginOutlined />} onClick={() => signIn(me.data)}>
        Sign in
      </Button>
    )
  }
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, color: 'text.secondary' }}>
        <AccountCircleOutlined fontSize="small" aria-hidden />
        <Typography variant="body2">Signed in as {me.data.username}</Typography>
      </Box>
      <Button variant="outlined" startIcon={<LogoutOutlined />} onClick={() => signOut.mutate()} disabled={signOut.isPending}>
        Sign out
      </Button>
    </Box>
  )
}
