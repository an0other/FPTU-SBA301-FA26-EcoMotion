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
        '/images': {
          target: apiTarget,
          changeOrigin: true,
        },
      },
    },
  }
})
