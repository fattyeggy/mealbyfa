import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  base: '/admin/',
  plugins: [vue()],
  server: {
    port: 5174,
    proxy: {
      '/admin': { target: 'http://127.0.0.1:8080', changeOrigin: true }
    }
  }
})
