import { expect, test } from '@playwright/test'

const securityContext = {
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
}

test.beforeEach(async ({ page }) => {
  await page.route('**/api/agent/security/config', (route) =>
    route.fulfill({ json: { mode: 'api-key', loginUrl: '', logoutUrl: '', csrfUrl: '' } }),
  )
  await page.route('**/api/agent/security/context', (route) =>
    route.fulfill({ json: securityContext }),
  )
  await page.route('**/api/ops/frontend-events', (route) => route.fulfill({ status: 202 }))
})

test('reviews an action draft and refreshes its server page', async ({ page }) => {
  let status = 'PENDING_REVIEW'
  const action = () => ({
    tenantId: 'T001',
    actionId: 'act-e2e',
    traceId: 'trace-e2e',
    customerId: 'C001',
    actionType: 'CUSTOMER_REPLY_DRAFT',
    title: '回复客户异常说明',
    riskLevel: 'L2',
    status,
    draftContent: '已核查运输异常。',
    createdAt: '2026-06-23T00:00:00Z',
  })
  await page.route('**/api/agent/actions/page?**', (route) =>
    route.fulfill({ json: { items: [action()], page: 1, size: 20, total: 1, totalPages: 1 } }),
  )
  await page.route('**/api/agent/actions/act-e2e/executions?**', (route) =>
    route.fulfill({ json: [] }),
  )
  await page.route('**/api/agent/actions/act-e2e/business-link?**', (route) =>
    route.fulfill({ json: { status: 'NOT_EXECUTED', traceId: 'trace-e2e' } }),
  )
  await page.route('**/api/agent/actions/act-e2e?**', (route) => route.fulfill({ json: action() }))
  await page.route('**/api/agent/actions/act-e2e/review', async (route) => {
    status = 'APPROVED'
    await route.fulfill({ json: action() })
  })

  await page.goto('/operations/actions')
  await page.getByRole('button', { name: '详情' }).click()
  await page.getByRole('button', { name: '批准' }).click()
  await page.locator('.el-message-box__input input').fill('证据完整，同意执行')
  await page.getByRole('button', { name: '批准动作' }).click()
  await expect(page.getByText('APPROVED').first()).toBeVisible()
})

test('creates a knowledge document through the drawer', async ({ page }) => {
  await page.route('**/api/knowledge/retrieval/status', (route) =>
    route.fulfill({
      json: {
        defaultMode: 'hybrid_reranker',
        vectorStoreReady: true,
        vectorTable: 'ai_knowledge_vector_chunk_v04',
      },
    }),
  )
  await page.route('**/api/knowledge/documents/page?**', (route) =>
    route.fulfill({ json: { items: [], page: 1, size: 20, total: 0, totalPages: 0 } }),
  )
  await page.route('**/api/knowledge/index-jobs?**', (route) => route.fulfill({ json: [] }))
  let saved = false
  await page.route('**/api/knowledge/documents', async (route) => {
    saved = true
    await route.fulfill({ json: { docId: 'doc-e2e', status: 'DRAFT' } })
  })

  await page.goto('/operations/knowledge')
  await page.getByRole('button', { name: '新建文档' }).click()
  const drawer = page.locator('.el-drawer')
  await drawer.locator('input').first().fill('冷链异常处置补充制度')
  await drawer.locator('textarea').fill('发生超温后，应立即隔离货物并发起质量复核。')
  await drawer.getByRole('button', { name: '保存文档' }).click()
  await expect.poll(() => saved).toBe(true)
})

test('runs quality alert evaluation', async ({ page }) => {
  await page.route('**/api/agent/feedback/quality-metrics?**', (route) =>
    route.fulfill({
      json: {
        notHelpfulFeedback: 2,
        candidateCount: 3,
        approvedCandidates: 1,
        candidateConversionRate: 0.33,
        ragExperimentPassRate: 0.8,
      },
    }),
  )
  await page.route('**/api/agent/quality/alerts/page?**', (route) =>
    route.fulfill({ json: { items: [], page: 1, size: 20, total: 0, totalPages: 0 } }),
  )
  await page.route('**/api/agent/quality/alert-tasks/page?**', (route) =>
    route.fulfill({ json: { items: [], page: 1, size: 20, total: 0, totalPages: 0 } }),
  )
  let evaluated = false
  await page.route('**/api/agent/quality/alerts/evaluate?**', async (route) => {
    evaluated = true
    await route.fulfill({ json: { openAlerts: 0 } })
  })

  await page.goto('/operations/quality')
  await page.getByRole('button', { name: '运行评估' }).click()
  await page.getByRole('button', { name: '确认' }).click()
  await expect.poll(() => evaluated).toBe(true)
})

test('runs an evaluation suite', async ({ page }) => {
  await page.route('**/api/agent/evals/suites?**', (route) =>
    route.fulfill({
      json: [
        {
          suiteId: 'suite-e2e',
          suiteName: '回归集',
          suiteVersion: 'v1',
          caseCount: 2,
          enabled: true,
        },
      ],
    }),
  )
  await page.route('**/api/agent/evals/runs/page?**', (route) =>
    route.fulfill({ json: { items: [], page: 1, size: 20, total: 0, totalPages: 0 } }),
  )
  await page.route('**/api/agent/evals/release-gates/page?**', (route) =>
    route.fulfill({ json: { items: [], page: 1, size: 20, total: 0, totalPages: 0 } }),
  )
  let executed = false
  await page.route('**/api/agent/evals/suites/suite-e2e/run?**', async (route) => {
    executed = true
    await route.fulfill({
      json: {
        runId: 'run-e2e',
        status: 'COMPLETED',
        totalCases: 2,
        passedCases: 2,
        failedCases: 0,
      },
    })
  })

  await page.goto('/operations/evaluation')
  await page.getByRole('button', { name: '运行', exact: true }).click()
  await page.getByRole('button', { name: '开始评测' }).click()
  await expect.poll(() => executed).toBe(true)
})
