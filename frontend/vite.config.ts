import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  const target = env.VITE_API_TARGET || 'http://127.0.0.1:8081'
  const port = Number(env.VITE_DEV_PORT || 5173)
  return {
    plugins: [vue()],
    server: { host: '127.0.0.1', port, strictPort: true, proxy: Object.fromEntries(
      ['/api', '/auth', '/mock', '/healthz'].map(path => [path, { target, changeOrigin: false }]),
    ) },
  }
})
