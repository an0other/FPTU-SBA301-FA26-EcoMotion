import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: '.',
  testMatch: 'vnpay-result.spec.js',
  use: { baseURL: 'http://127.0.0.1:4178' },
  webServer: {
    command: 'npm run preview -- --port 4178 --host 127.0.0.1',
    port: 4178,
    reuseExistingServer: !process.env.CI,
  },
})

