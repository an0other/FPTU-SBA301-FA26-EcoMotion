import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  const apiTarget = env.VITE_API_TARGET || 'http://localhost:8080'
  const apiBaseUrl = env.VITE_API_BASE_URL || '/api'

  return {
    plugins: [react(), tailwindcss()],
    server: {
      proxy: {
        [apiBaseUrl]: {
          target: apiTarget,
          changeOrigin: true,
        },
        // Vehicle photos are served by the backend as absolute paths ("/images/{uid}/{vid}/{file}")
        // and are referenced as-is by <img src>, so they must be proxied too. Without this the
        // browser asks the Vite dev server for /images/... and gets a 404 instead of the photo.
        '/images': {
          target: apiTarget,
          changeOrigin: true,
        },
      },
    },
  }
})
