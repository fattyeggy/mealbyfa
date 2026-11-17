import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// H5 开发态把 /api 代理到本地后端，避免跨域；小程序构建用 VITE_API_BASE 指向真实域名
export default defineConfig({
  plugins: [uni()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  }
})
