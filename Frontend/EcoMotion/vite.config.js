import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Forward /api/* to the Spring Boot backend so the app talks to a single
      // origin in dev. This keeps the httpOnly refresh cookie (path /api/auth)
      // first-party and avoids CORS preflight entirely.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
