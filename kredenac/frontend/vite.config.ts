import { existsSync, readFileSync } from 'fs'
import { defineConfig } from 'vite'

export default defineConfig({
  server: {
    https: existsSync('../certs/backend.crt') ? {
      cert: readFileSync('../certs/backend.crt'),
      key: readFileSync('../certs/backend.key')
    } : undefined,
    headers: {
      'X-Frame-Options': 'DENY',
      'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data: blob:; connect-src 'self' ws: wss:; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'; frame-src 'none'",
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