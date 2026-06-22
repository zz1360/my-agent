import './assets/main.css'
import 'element-plus/dist/index.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'

import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus)

window.addEventListener('agent-auth-invalid', () => {
  const currentPath = router.currentRoute.value.fullPath
  void router.replace({ name: 'access', query: { redirect: currentPath } })
})

app.mount('#app')
