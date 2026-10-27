import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  base: '/',
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('monaco-editor') || id.includes('node_modules/monaco')) {
            return 'monaco'
          }
        },
        chunkFileNames: (chunkInfo) => {
          const name = chunkInfo.name.replace(/[^a-zA-Z0-9_-]/g, '_');
          return `assets/${name}-[hash].js`;
        },
        entryFileNames: 'assets/[name]-[hash].js',
        assetFileNames: (assetInfo) => {
          const name = assetInfo.name ? assetInfo.name.replace(/[^a-zA-Z0-9._-]/g, '_') : '[name]';
          return `assets/${name}-[hash].[ext]`;
        }
      }
    }
  },
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true
      },
      '/ws': {
        target: 'ws://localhost:8090',
        ws: true,
        changeOrigin: true
      }
    }
  }
})
