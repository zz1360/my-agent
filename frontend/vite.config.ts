import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxyHeaders: Record<string, string> = {
    'X-Agent-Tenant': env.DEV_AGENT_TENANT || 'T001',
    'X-Agent-User': env.DEV_AGENT_USER || 'admin-console',
    'X-Agent-Roles': env.DEV_AGENT_ROLES || 'OPS_MANAGER',
  }
  if (env.DEV_AGENT_API_KEY) proxyHeaders['X-Agent-Api-Key'] = env.DEV_AGENT_API_KEY

  return {
    plugins: [
      vue(),
      vueDevTools(),
      AutoImport({ dts: false, resolvers: [ElementPlusResolver()] }),
      Components({ resolvers: [ElementPlusResolver()] }),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      host: '127.0.0.1',
      port: 5173,
      proxy: {
        '/api': {
          target: env.DEV_AGENT_BACKEND_URL || 'http://127.0.0.1:8080',
          changeOrigin: true,
          headers: proxyHeaders,
        },
        '/actuator': {
          target: env.DEV_AGENT_BACKEND_URL || 'http://127.0.0.1:8080',
          changeOrigin: true,
          headers: proxyHeaders,
        },
      },
    },
    build: {
      manifest: true,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes('markdown-it') || id.includes('dompurify')) return 'markdown'
            if (id.includes('@tanstack')) return 'query'
          },
        },
      },
    },
  }
})
