import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: true,
    port: 5173,
    proxy: {
      // Forward API calls to the Spring Boot backend during development.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
