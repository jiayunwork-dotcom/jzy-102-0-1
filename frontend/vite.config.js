import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 本地开发时把 API 请求转发给本机后端
      '/api': 'http://localhost:8080'
    }
  }
})
