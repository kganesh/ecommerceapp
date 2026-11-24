import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const backend = process.env.BACKEND_URL ?? 'http://localhost:8080'

// The dev server proxies API calls to the backend so the browser sees a single
// origin, as it will behind CloudFront. That keeps the BFF session cookie
// (from M2) same-site and avoids CORS.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': backend,
      '/actuator': backend,
      '/oauth2': backend,
      '/login': backend,
      '/logout': backend,
    },
  },
})
