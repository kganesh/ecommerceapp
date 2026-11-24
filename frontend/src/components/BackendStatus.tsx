import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { useQuery } from '@tanstack/react-query'
import { fetchHealth, fetchMeta } from '../api/client'
import { tokens } from '../theme'

/** Shows whether the app reaches the backend (M0 gate). Quiet when all is well. */
export function BackendStatus() {
  const meta = useQuery({ queryKey: ['meta'], queryFn: fetchMeta, retry: false })
  const health = useQuery({ queryKey: ['health'], queryFn: fetchHealth, retry: false })

  const loading = meta.isPending || health.isPending
  const connected = meta.isSuccess && health.data === 'UP'

  return (
    <Box role="status" aria-live="polite" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      <Box
        aria-hidden="true"
        sx={{
          width: 8,
          height: 8,
          borderRadius: '50%',
          bgcolor: loading ? tokens.line : connected ? tokens.sage : tokens.contains,
        }}
      />
      <Typography variant="body2" color="text.secondary">
        {loading && 'Checking backend…'}
        {!loading && connected && (
          <>
            Backend connected: {meta.data.name} {meta.data.version} (health {health.data})
          </>
        )}
        {!loading && !connected && (
          <>
            Backend unavailable. Start it with <code>make up &amp;&amp; make app</code>.
          </>
        )}
      </Typography>
    </Box>
  )
}
