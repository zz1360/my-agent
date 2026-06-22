<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { LogIn, RefreshCw, ShieldAlert } from '@lucide/vue'
import { useContextStore } from '@/stores/context'

const route = useRoute()
const router = useRouter()
const context = useContextStore()

async function retry() {
  if (await context.bootstrap(true)) {
    await router.replace(String(route.query.redirect || '/chat'))
  }
}
</script>

<template>
  <main class="access-page">
    <section class="access-panel">
      <div class="access-icon"><ShieldAlert :size="24" /></div>
      <h1>需要企业身份</h1>
      <p>{{ context.authError || '当前请求没有通过身份验证。' }}</p>
      <div class="access-actions">
        <el-button type="primary" :icon="RefreshCw" :loading="context.loading" @click="retry"
          >重新验证</el-button
        >
        <el-button :icon="LogIn" @click="context.reauthenticate">统一登录</el-button>
      </div>
    </section>
  </main>
</template>

<style scoped>
.access-page {
  display: grid;
  min-height: 100vh;
  padding: 24px;
  background: var(--surface-soft);
  place-items: center;
}
.access-panel {
  width: min(440px, 100%);
  padding: 28px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  text-align: center;
}
.access-icon {
  display: inline-flex;
  width: 48px;
  height: 48px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: var(--surface-tint);
  color: var(--accent);
}
h1 {
  margin: 18px 0 8px;
  font-size: 20px;
}
p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}
.access-actions {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 22px;
}
</style>
