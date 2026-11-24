import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const backend = process.env.BACKEND_URL ?? 'http://localhost:8080'

// In GitHub Codespaces the browser reaches the dev server at
// https://<codespace>-5173.app.github.dev, so Vite must accept that host and
// point hot reload at the forwarded HTTPS port.
const inCodespaces = process.env.CODESPACES === 'true'

// The dev server proxies API calls to the backend so the browser sees a single
// origin, as it will behind CloudFront. That keeps the BFF session cookie
// (from M2) same-site and avoids CORS.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    ...(inCodespaces && {
      allowedHosts: ['.app.github.dev'],
      hmr: { clientPort: 443 },
    }),
    proxy: {
      '/api': backend,
      '/actuator': backend,
      '/oauth2': backend,
      '/login': backend,
      '/logout': backend,
    },
  },
})
