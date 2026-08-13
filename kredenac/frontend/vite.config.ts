import { defineConfig } from 'vite'

export default defineConfig({
    server: {
        headers: {
            'X-Frame-Options': 'DENY',
            'Content-Security-Policy': "frame-ancestors 'none'; frame-src 'none'"
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