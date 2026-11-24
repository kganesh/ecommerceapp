import { useQuery } from '@tanstack/react-query'
import { fetchHealth, fetchMeta } from './api/client'

/** M0 shell: proves the SPA reaches the backend through the same-origin proxy. */
export default function App() {
  const meta = useQuery({ queryKey: ['meta'], queryFn: fetchMeta, retry: false })
  const health = useQuery({ queryKey: ['health'], queryFn: fetchHealth, retry: false })

  const loading = meta.isPending || health.isPending
  const connected = meta.isSuccess && health.data === 'UP'

  return (
    <main className="shell">
      <h1>Nut-Allergy Safe Shopping</h1>
      <p className="lead">Shop with every child's avoid list checked for you.</p>

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
    </main>
  )
}
