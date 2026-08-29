import { defineConfig } from 'vite'
import { readFileSync } from 'fs'

export default defineConfig({
  server: {
    https: {
      cert: readFileSync('../certs/backend.crt'),
      key: readFileSync('../certs/backend.key')
    },
    headers: {
      'X-Frame-Options': 'DENY',
      'Content-Security-Policy': "frame-ancestors 'none'; frame-src 'none'",
      'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
      'Referrer-Policy': 'strict-origin-when-cross-origin',
      'Cross-Origin-Opener-Policy': 'same-origin',
      'X-Content-Type-Options': 'nosniff'
    },
    allowedHosts: ['kredenac.moma.rs'],
    proxy: {
      '/api': {
        target: 'https://localhost:8080',
        changeOrigin: true,
        secure: false
      }
    }
  }
})