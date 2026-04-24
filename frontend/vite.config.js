import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// NODE_PORT controls which backend node the frontend talks to.
// Default: 8081 (node1). If node1 dies, restart with NODE_PORT=8082.
// Example: $env:NODE_PORT=8082; npm run dev
const port = process.env.NODE_PORT ?? '8081'
const backendHttp = `http://localhost:${port}`
const backendWs   = `ws://localhost:${port}`

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api':     { target: backendHttp, changeOrigin: true },
      '/ws/jobs': { target: backendWs,  ws: true, changeOrigin: true }
    }
  }
})