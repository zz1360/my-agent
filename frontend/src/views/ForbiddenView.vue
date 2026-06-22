<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ShieldX } from '@lucide/vue'
import { useContextStore } from '@/stores/context'

const route = useRoute()
const router = useRouter()
const context = useContextStore()
</script>

<template>
  <main class="forbidden-page">
    <section class="forbidden-panel">
      <ShieldX :size="32" />
      <h1>当前角色无权访问</h1>
      <p>{{ context.userId }} · 缺少 {{ route.query.permission || '对应' }} 权限</p>
      <el-button :icon="ArrowLeft" @click="router.push('/chat')">返回对话台</el-button>
    </section>
  </main>
</template>

<style scoped>
.forbidden-page {
  display: grid;
  min-height: 100vh;
  padding: 24px;
  background: var(--surface-soft);
  place-items: center;
}
.forbidden-panel {
  width: min(440px, 100%);
  padding: 28px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  color: #9b3d44;
  text-align: center;
}
h1 {
  margin: 14px 0 8px;
  color: var(--text);
  font-size: 20px;
}
p {
  margin: 0 0 20px;
  color: var(--muted);
  font-size: 13px;
}
</style>
