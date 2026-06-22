import { createRouter, createWebHistory } from 'vue-router'
import { useContextStore } from '@/stores/context'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/chat' },
    {
      path: '/access',
      name: 'access',
      component: () => import('@/views/AccessView.vue'),
      meta: { title: '身份验证', public: true },
    },
    {
      path: '/forbidden',
      name: 'forbidden',
      component: () => import('@/views/ForbiddenView.vue'),
      meta: { title: '无权访问', public: true },
    },
    {
      path: '/chat',
      name: 'chat',
      component: () => import('@/views/ChatView.vue'),
      meta: { title: '物流 Agent 对话台', permission: 'CHAT_USE' },
    },
    {
      path: '/operations',
      component: () => import('@/layouts/AdminLayout.vue'),
      children: [
        { path: '', redirect: '/operations/overview' },
        {
          path: 'overview',
          name: 'operations-overview',
          component: () => import('@/views/admin/OpsOverviewView.vue'),
          meta: { title: '运行概览', permission: 'OPS_VIEW' },
        },
        {
          path: 'actions',
          name: 'operations-actions',
          component: () => import('@/views/admin/ActionListView.vue'),
          meta: { title: '动作管理', permission: 'ACTION_MANAGE' },
        },
        {
          path: 'quality',
          name: 'operations-quality',
          component: () => import('@/views/admin/QualityView.vue'),
          meta: { title: '质量治理', permission: 'QUALITY_MANAGE' },
        },
        {
          path: 'evaluation',
          name: 'operations-evaluation',
          component: () => import('@/views/admin/EvaluationView.vue'),
          meta: { title: '评测中心', permission: 'EVAL_MANAGE' },
        },
        {
          path: 'knowledge',
          name: 'operations-knowledge',
          component: () => import('@/views/admin/KnowledgeView.vue'),
          meta: { title: '知识运营', permission: 'KNOWLEDGE_MANAGE' },
        },
        {
          path: 'audit',
          name: 'operations-audit',
          component: () => import('@/views/admin/AuditView.vue'),
          meta: { title: '审计追踪', permission: 'AUDIT_VIEW' },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/chat' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  if (to.meta.public) return true
  const context = useContextStore()
  const authenticated = await context.bootstrap()
  if (!authenticated) return { name: 'access', query: { redirect: to.fullPath } }
  if (!context.hasPermission(to.meta.permission)) {
    return { name: 'forbidden', query: { permission: to.meta.permission || '' } }
  }
  return true
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title || '物流 Agent')} · Logistics Agent`
})

export default router
