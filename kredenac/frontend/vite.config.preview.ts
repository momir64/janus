import { defineConfig } from 'vite'
export default defineConfig({
  server: { proxy: { '/api': { target: 'https://localhost:8080', changeOrigin: true, secure: false } } }
})
