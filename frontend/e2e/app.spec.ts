import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

test.beforeEach(async ({ page }) => {
  await page.route('**/api/agent/security/config', (route) =>
    route.fulfill({
      json: { mode: 'api-key', loginUrl: '', logoutUrl: '/api/agent/security/logout', csrfUrl: '' },
    }),
  )
  await page.route('**/api/ops/frontend-events', (route) => route.fulfill({ status: 202 }))
  await page.route('**/api/agent/security/context', (route) =>
    route.fulfill({
      json: {
        tenantId: 'T001',
        userId: 'admin-console',
        roles: ['OPS_MANAGER'],
        permissions: [
          'CHAT_USE',
          'OPS_VIEW',
          'AUDIT_VIEW',
          'ACTION_MANAGE',
          'KNOWLEDGE_MANAGE',
          'QUALITY_MANAGE',
          'EVAL_MANAGE',
        ],
        authenticated: true,
        apiKeyRequired: false,
        authenticationType: 'GatewayAuthentication',
      },
    }),
  )
  await page.route('**/api/demo/questions', (route) =>
    route.fulfill({ json: ['运单 WB202606010023 现在是什么状态？'] }),
  )
  await page.route('**/api/agent/conversations?**', (route) => route.fulfill({ json: [] }))
  await page.route('**/api/agent/chat/stream', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: [
        'event:status\ndata:{"message":"正在查询"}\n\n',
        'event:delta\ndata:{"delta":"运单状态为"}\n\n',
        'event:delta\ndata:{"delta":"异常运输中。"}\n\n',
        'event:complete\ndata:{"traceId":"trace-e2e","conversationId":"conv-e2e","messageId":"msg-e2e","answer":"运单状态为异常运输中。","riskLevel":"L2","confidence":0.91,"citations":[],"toolCalls":[],"createdAt":"2026-06-22T00:00:00Z"}\n\n',
      ].join(''),
    }),
  )
})

async function mockOverview(page: import('@playwright/test').Page) {
  await page.route('**/api/ops/readiness', (route) =>
    route.fulfill({
      json: {
        application: 'logistics-agent',
        activeProfiles: ['local'],
        ready: true,
        checks: [],
        checkedAt: '2026-06-22T00:00:00Z',
      },
    }),
  )
  await page.route('**/api/ops/metrics/summary', (route) =>
    route.fulfill({
      json: {
        totalQuestions: 12,
        averageAgentLatencyMs: 80,
        latestRagRecallAtK: 0.9,
        toolCallSuccessRate: 1,
        releaseGatePassed: 2,
        releaseGateBlocked: 0,
        flywayVersion: '17',
        measuredAt: '2026-06-22T00:00:00Z',
      },
    }),
  )
}

test('chat workspace streams an answer', async ({ page }) => {
  await page.goto('/')
  await expect(page).toHaveURL(/\/chat$/)
  await expect(page.getByRole('heading', { name: '物流业务问答' })).toBeVisible()

  await page.getByPlaceholder('输入物流业务问题…').fill('查询运单状态')
  await page.getByTitle('发送').click()

  await expect(page.getByText('运单状态为异常运输中。')).toBeVisible()
  await expect(page.getByText('91% 置信度')).toBeVisible()
})

test('admin framework exposes module navigation', async ({ page }) => {
  await mockOverview(page)

  await page.goto('/operations/overview')
  await expect(page.getByText('物流 Agent 管理台')).toBeVisible()
  await expect(page.getByRole('link', { name: '动作管理' })).toBeVisible()
  await expect(page.getByRole('link', { name: '质量治理' })).toBeVisible()
  await expect(page.getByRole('link', { name: '知识运营' })).toBeVisible()
  await expect(page.getByText('累计问题')).toBeVisible()
})

test('admin shell has no serious accessibility violations', async ({ page }) => {
  await mockOverview(page)
  await page.goto('/operations/overview')
  await expect(page.getByText('物流 Agent 管理台')).toBeVisible()
  const results = await new AxeBuilder({ page }).exclude('.vue-devtools__anchor-btn').analyze()
  expect(
    results.violations.filter((item) => ['serious', 'critical'].includes(item.impact || '')),
  ).toEqual([])
})

test('@mobile admin navigation remains usable and layout-stable', async ({ page }) => {
  await mockOverview(page)
  await page.goto('/operations/overview')
  await expect(page.getByTitle('打开导航')).toBeVisible()
  await page.getByTitle('打开导航').click()
  await expect(page.getByRole('link', { name: '动作管理' })).toBeVisible()

  const layout = await page.evaluate(() => {
    const topbar = document.querySelector('.admin-topbar')!.getBoundingClientRect()
    const sidebar = document.querySelector('.admin-sidebar')!.getBoundingClientRect()
    const main = document.querySelector('.admin-main')!.getBoundingClientRect()
    return JSON.stringify({
      viewport: [window.innerWidth, window.innerHeight],
      topbar: [Math.round(topbar.width), Math.round(topbar.height)],
      sidebar: [Math.round(sidebar.width), Math.round(sidebar.height)],
      main: [Math.round(main.width), Math.round(main.left)],
      horizontalOverflow: document.documentElement.scrollWidth > window.innerWidth,
    })
  })
  expect(layout).toMatchSnapshot('admin-mobile-layout.json')
})

test('route guard blocks missing management permission', async ({ page }) => {
  await page.route('**/api/agent/security/context', (route) =>
    route.fulfill({
      json: {
        tenantId: 'T001',
        userId: 'u-cs-001',
        roles: ['CUSTOMER_SERVICE'],
        permissions: ['CHAT_USE', 'AUDIT_VIEW'],
        authenticated: true,
        apiKeyRequired: false,
        authenticationType: 'GatewayAuthentication',
      },
    }),
  )

  await page.goto('/operations/actions')
  await expect(page).toHaveURL(/\/forbidden/)
  await expect(page.getByRole('heading', { name: '当前角色无权访问' })).toBeVisible()
})
