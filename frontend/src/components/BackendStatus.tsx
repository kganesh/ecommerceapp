import { useQuery } from '@tanstack/react-query'
import { fetchHealth, fetchMeta } from '../api/client'

/** Shows whether the SPA reaches the backend through the same-origin proxy (M0 gate). */
export function BackendStatus() {
  const meta = useQuery({ queryKey: ['meta'], queryFn: fetchMeta, retry: false })
  const health = useQuery({ queryKey: ['health'], queryFn: fetchHealth, retry: false })

  const loading = meta.isPending || health.isPending
  const connected = meta.isSuccess && health.data === 'UP'

  return (
    <section className="status" role="status" aria-live="polite">
      {loading && <p>Checking backend…</p>}
      {!loading && connected && (
        <p>
          <span aria-hidden="true">✔ </span>
          Backend connected: {meta.data.name} {meta.data.version} (health {health.data})
        </p>
      )}
      {!loading && !connected && (
        <p className="problem">
          <span aria-hidden="true">⚠ </span>
          Backend unavailable. Start it with <code>make up &amp;&amp; make app</code>.
        </p>
      )}
    </section>
  )
}
