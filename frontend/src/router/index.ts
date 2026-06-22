import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/chat',
    },
    {
      path: '/chat',
      name: 'chat',
      component: () => import('@/views/ChatView.vue'),
      meta: { title: '物流 Agent 对话台' },
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
          meta: { title: '运行概览' },
        },
        {
          path: 'actions',
          name: 'operations-actions',
          component: () => import('@/views/admin/ActionListView.vue'),
          meta: { title: '动作管理' },
        },
        {
          path: 'quality',
          name: 'operations-quality',
          component: () => import('@/views/admin/QualityView.vue'),
          meta: { title: '质量治理' },
        },
        {
          path: 'evaluation',
          name: 'operations-evaluation',
          component: () => import('@/views/admin/EvaluationView.vue'),
          meta: { title: '评测中心' },
        },
        {
          path: 'knowledge',
          name: 'operations-knowledge',
          component: () => import('@/views/admin/KnowledgeView.vue'),
          meta: { title: '知识检索' },
        },
        {
          path: 'audit',
          name: 'operations-audit',
          component: () => import('@/views/admin/AuditView.vue'),
          meta: { title: '审计追踪' },
        },
      ],
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title || '物流 Agent')} · Logistics Agent`
})

export default router
