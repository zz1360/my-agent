import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'

import App from './App.vue'
import router from './router'
import { installFrontendTelemetry } from './telemetry'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(VueQueryPlugin, {
  queryClient: new QueryClient({
    defaultOptions: {
      queries: { staleTime: 15_000, retry: 1, refetchOnWindowFocus: false },
      mutations: { retry: 0 },
    },
  }),
})

installFrontendTelemetry(app, router)

window.addEventListener('agent-auth-invalid', () => {
  const currentPath = router.currentRoute.value.fullPath
  void router.replace({ name: 'access', query: { redirect: currentPath } })
})

app.mount('#app')
