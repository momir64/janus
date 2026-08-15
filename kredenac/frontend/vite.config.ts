import { defineConfig } from 'vite'

export default defineConfig({
    server: {
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
                target: 'http://localhost:8080',
                changeOrigin: true
            }
        }
    }
})
