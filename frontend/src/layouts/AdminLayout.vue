<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import {
  Activity,
  Bot,
  BookOpen,
  ClipboardCheck,
  FileSearch,
  Menu,
  MessageSquare,
  Settings,
  ShieldCheck,
  X,
} from '@lucide/vue'
import ContextDrawer from '@/components/ContextDrawer.vue'
import { useContextStore } from '@/stores/context'

const route = useRoute()
const router = useRouter()
const context = useContextStore()
const contextDrawer = ref(false)
const mobileNav = ref(false)

const navItems = [
  { to: '/operations/overview', label: '运行概览', icon: Activity },
  { to: '/operations/actions', label: '动作管理', icon: ClipboardCheck },
  { to: '/operations/quality', label: '质量治理', icon: ShieldCheck },
  { to: '/operations/evaluation', label: '评测中心', icon: FileSearch },
  { to: '/operations/knowledge', label: '知识检索', icon: BookOpen },
  { to: '/operations/audit', label: '审计追踪', icon: FileSearch },
]

const pageTitle = computed(() => String(route.meta.title || '物流 Agent 管理台'))
</script>

<template>
  <div class="admin-app">
    <header class="admin-topbar">
      <div class="admin-brand">
        <button class="header-icon mobile-menu" title="打开导航" @click="mobileNav = true">
          <Menu :size="19" />
        </button>
        <div class="admin-mark"><Bot :size="19" /></div>
        <div><strong>物流 Agent 管理台</strong><span>运营、质量与知识治理</span></div>
      </div>
      <div class="admin-actions">
        <span class="context-text"
          >{{ context.tenantId }} · {{ context.userId }} · {{ context.primaryRole }}</span
        >
        <button class="header-icon" title="运行上下文" @click="contextDrawer = true">
          <Settings :size="18" />
        </button>
        <el-button :icon="MessageSquare" @click="router.push('/chat')">对话台</el-button>
      </div>
    </header>

    <div class="admin-layout">
      <aside :class="['admin-sidebar', { open: mobileNav }]">
        <div class="sidebar-mobile-head">
          <strong>导航</strong>
          <button class="header-icon" title="关闭导航" @click="mobileNav = false">
            <X :size="18" />
          </button>
        </div>
        <nav>
          <RouterLink
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            @click="mobileNav = false"
          >
            <component :is="item.icon" :size="17" />
            <span>{{ item.label }}</span>
          </RouterLink>
        </nav>
        <div class="sidebar-footer">
          <span class="status-dot up" />
          <div><strong>Spring Boot API</strong><small>通过 /api 代理访问</small></div>
        </div>
      </aside>
      <button
        v-if="mobileNav"
        class="nav-backdrop"
        aria-label="关闭导航"
        @click="mobileNav = false"
      />

      <main class="admin-main">
        <div class="page-title-band">
          <h1>{{ pageTitle }}</h1>
        </div>
        <RouterView />
      </main>
    </div>

    <ContextDrawer v-model="contextDrawer" />
  </div>
</template>

<style scoped>
.admin-app {
  min-height: 100vh;
}

.admin-topbar {
  position: sticky;
  z-index: 10;
  top: 0;
  display: flex;
  height: 62px;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid var(--line);
  background: var(--surface);
}

.admin-brand,
.admin-actions,
.admin-brand > div,
.sidebar-footer {
  display: flex;
  align-items: center;
}

.admin-brand {
  gap: 10px;
}

.admin-mark {
  width: 34px;
  height: 34px;
  justify-content: center;
  border-radius: 7px;
  background: var(--surface-tint);
  color: var(--accent);
}

.admin-brand > div:last-child {
  align-items: flex-start;
  flex-direction: column;
}

.admin-brand strong {
  font-size: 14px;
}

.admin-brand span,
.context-text {
  color: var(--muted);
  font-size: 11px;
}

.admin-actions {
  gap: 9px;
}

.header-icon {
  display: inline-flex;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
  color: #435160;
  cursor: pointer;
}

.header-icon:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.admin-layout {
  display: grid;
  min-height: calc(100vh - 62px);
  grid-template-columns: 218px minmax(0, 1fr);
}

.admin-sidebar {
  position: sticky;
  top: 62px;
  display: flex;
  height: calc(100vh - 62px);
  flex-direction: column;
  border-right: 1px solid var(--line);
  background: var(--surface);
}

.admin-sidebar nav {
  display: grid;
  gap: 3px;
  padding: 12px 9px;
}

.admin-sidebar nav a {
  display: flex;
  height: 40px;
  align-items: center;
  gap: 10px;
  padding: 0 11px;
  border-left: 3px solid transparent;
  border-radius: 5px;
  color: #435160;
  font-size: 13px;
}

.admin-sidebar nav a:hover,
.admin-sidebar nav a.router-link-active {
  border-left-color: var(--accent);
  background: var(--surface-tint);
  color: var(--accent-strong);
  font-weight: 600;
}

.sidebar-footer {
  gap: 9px;
  margin-top: auto;
  padding: 14px;
  border-top: 1px solid var(--line);
}

.sidebar-footer div {
  min-width: 0;
}

.sidebar-footer strong,
.sidebar-footer small {
  display: block;
}

.sidebar-footer strong {
  font-size: 11px;
}

.sidebar-footer small {
  margin-top: 2px;
  color: var(--muted);
  font-size: 9px;
}

.admin-main {
  min-width: 0;
  background: var(--surface-soft);
}

.page-title-band {
  display: flex;
  height: 56px;
  align-items: center;
  padding: 0 22px;
  border-bottom: 1px solid var(--line);
  background: var(--surface);
}

.page-title-band h1 {
  margin: 0;
  font-size: 16px;
}

.sidebar-mobile-head,
.mobile-menu,
.nav-backdrop {
  display: none;
}

@media (max-width: 760px) {
  .admin-topbar {
    height: 58px;
    padding: 0 9px;
  }
  .admin-brand span,
  .context-text,
  .admin-actions .el-button {
    display: none;
  }
  .mobile-menu {
    display: inline-flex;
  }
  .admin-layout {
    min-height: calc(100vh - 58px);
    grid-template-columns: minmax(0, 1fr);
  }
  .admin-sidebar {
    position: fixed;
    z-index: 30;
    top: 0;
    bottom: 0;
    left: 0;
    width: 240px;
    height: 100vh;
    transform: translateX(-100%);
    transition: transform 0.2s ease;
  }
  .admin-sidebar.open {
    transform: translateX(0);
  }
  .sidebar-mobile-head {
    display: flex;
    height: 58px;
    align-items: center;
    justify-content: space-between;
    padding: 0 10px 0 16px;
    border-bottom: 1px solid var(--line);
  }
  .nav-backdrop {
    position: fixed;
    z-index: 29;
    inset: 0;
    display: block;
    border: 0;
    background: rgba(20, 28, 36, 0.3);
  }
  .page-title-band {
    height: 50px;
    padding: 0 14px;
  }
}
</style>
