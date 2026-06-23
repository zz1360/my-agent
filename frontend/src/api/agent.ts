import { apiUrl, contextHeaders, http } from './http'
import type {
  AgentAction,
  ActionBusinessLink,
  ActionExecution,
  AgentChatRequest,
  AgentChatResponse,
  AuditTrace,
  ConversationDetail,
  ConversationSummary,
  EvalRun,
  EvalReleaseGate,
  EvalRunComparison,
  EvalSuite,
  KnowledgeDocument,
  KnowledgeIndexJob,
  OpsMetrics,
  OpsReadiness,
  QualityMetrics,
  QualityAlert,
  QualityTask,
  RetrievalStatus,
  SearchPreview,
  SecurityContext,
  SecurityConfig,
  PageResponse,
} from '@/types/api'

export async function fetchSecurityConfig(): Promise<SecurityConfig> {
  return (await http.get<SecurityConfig>('/api/agent/security/config')).data
}

export async function fetchCsrfToken(url: string): Promise<void> {
  await http.get(url)
}

export async function logoutSession(url: string): Promise<void> {
  await http.post(url)
}

export async function fetchSecurityContext(): Promise<SecurityContext> {
  return (await http.get<SecurityContext>('/api/agent/security/context')).data
}

export async function fetchDemoQuestions(): Promise<string[]> {
  const { data } = await http.get<string[] | { questions: string[] }>('/api/demo/questions')
  return Array.isArray(data) ? data : data.questions || []
}

export async function fetchConversations(params: URLSearchParams): Promise<ConversationSummary[]> {
  const { data } = await http.get<ConversationSummary[]>(`/api/agent/conversations?${params}`)
  return data
}

export async function fetchConversation(
  conversationId: string,
  params: URLSearchParams,
): Promise<ConversationDetail> {
  const { data } = await http.get<ConversationDetail>(
    `/api/agent/conversations/${encodeURIComponent(conversationId)}?${params}`,
  )
  return data
}

export async function sendChat(request: AgentChatRequest): Promise<AgentChatResponse> {
  const { data } = await http.post<AgentChatResponse>('/api/agent/chat', request)
  return data
}

export async function sendChatStream(
  request: AgentChatRequest,
  onEvent: (event: string, payload: unknown) => void,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(apiUrl('/api/agent/chat/stream'), {
    method: 'POST',
    credentials: 'include',
    headers: contextHeaders(),
    body: JSON.stringify(request),
    signal,
  })
  if (!response.ok || !response.body) {
    const text = await response.text()
    throw new Error(text || response.statusText)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
    const blocks = buffer.split(/\r?\n\r?\n/)
    buffer = blocks.pop() || ''
    blocks.forEach((block) => consumeSseBlock(block, onEvent))
    if (done) {
      if (buffer.trim()) consumeSseBlock(buffer, onEvent)
      break
    }
  }
}

function consumeSseBlock(block: string, onEvent: (event: string, payload: unknown) => void) {
  const lines = block.split(/\r?\n/)
  const event =
    lines
      .find((line) => line.startsWith('event:'))
      ?.slice(6)
      .trim() || 'message'
  const data = lines
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n')
  if (data) onEvent(event, JSON.parse(data))
}

export async function submitFeedback(
  messageId: string,
  payload: Record<string, unknown>,
): Promise<{ rating: string }> {
  const { data } = await http.post<{ rating: string }>(
    `/api/agent/messages/${encodeURIComponent(messageId)}/feedback`,
    payload,
  )
  return data
}

export async function generateActions(payload: Record<string, unknown>): Promise<AgentAction[]> {
  const { data } = await http.post<AgentAction[]>('/api/agent/actions/from-diagnosis', payload)
  return data
}

export async function fetchReadiness(): Promise<OpsReadiness> {
  return (await http.get<OpsReadiness>('/api/ops/readiness')).data
}

export async function fetchOpsMetrics(): Promise<OpsMetrics> {
  return (await http.get<OpsMetrics>('/api/ops/metrics/summary')).data
}

export async function fetchActions(params: URLSearchParams): Promise<AgentAction[]> {
  return (await http.get<AgentAction[]>(`/api/agent/actions?${params}`)).data
}

export async function fetchActionsPage(
  params: URLSearchParams,
): Promise<PageResponse<AgentAction>> {
  return (await http.get<PageResponse<AgentAction>>(`/api/agent/actions/page?${params}`)).data
}

export async function fetchAction(actionId: string, params: URLSearchParams): Promise<AgentAction> {
  return (
    await http.get<AgentAction>(`/api/agent/actions/${encodeURIComponent(actionId)}?${params}`)
  ).data
}

export async function reviewAction(
  actionId: string,
  payload: Record<string, unknown>,
): Promise<AgentAction> {
  return (
    await http.post<AgentAction>(
      `/api/agent/actions/${encodeURIComponent(actionId)}/review`,
      payload,
    )
  ).data
}

export async function executeAction(
  actionId: string,
  payload: Record<string, unknown>,
): Promise<ActionExecution> {
  return (
    await http.post<ActionExecution>(
      `/api/agent/actions/${encodeURIComponent(actionId)}/execute`,
      payload,
    )
  ).data
}

export async function fetchActionExecutions(
  actionId: string,
  params: URLSearchParams,
): Promise<ActionExecution[]> {
  return (
    await http.get<ActionExecution[]>(
      `/api/agent/actions/${encodeURIComponent(actionId)}/executions?${params}`,
    )
  ).data
}

export async function fetchActionBusinessLink(
  actionId: string,
  params: URLSearchParams,
): Promise<ActionBusinessLink> {
  return (
    await http.get<ActionBusinessLink>(
      `/api/agent/actions/${encodeURIComponent(actionId)}/business-link?${params}`,
    )
  ).data
}

export async function retryExecution(
  executionId: string,
  payload: Record<string, unknown>,
): Promise<ActionExecution> {
  return (
    await http.post<ActionExecution>(
      `/api/agent/actions/executions/${encodeURIComponent(executionId)}/retry`,
      payload,
    )
  ).data
}

export async function fetchQualityMetrics(params: URLSearchParams): Promise<QualityMetrics> {
  return (await http.get<QualityMetrics>(`/api/agent/feedback/quality-metrics?${params}`)).data
}

export async function fetchEvalSuites(params: URLSearchParams): Promise<EvalSuite[]> {
  return (await http.get<EvalSuite[]>(`/api/agent/evals/suites?${params}`)).data
}

export async function fetchEvalRuns(params: URLSearchParams): Promise<EvalRun[]> {
  return (await http.get<EvalRun[]>(`/api/agent/evals/runs?${params}`)).data
}

export async function fetchEvalRunsPage(params: URLSearchParams): Promise<PageResponse<EvalRun>> {
  return (await http.get<PageResponse<EvalRun>>(`/api/agent/evals/runs/page?${params}`)).data
}

export async function runEvalSuite(suiteId: string, params: URLSearchParams): Promise<EvalRun> {
  return (
    await http.post<EvalRun>(`/api/agent/evals/suites/${encodeURIComponent(suiteId)}/run?${params}`)
  ).data
}

export async function fetchReleaseGates(params: URLSearchParams): Promise<EvalReleaseGate[]> {
  return (await http.get<EvalReleaseGate[]>(`/api/agent/evals/release-gates?${params}`)).data
}

export async function fetchReleaseGatesPage(
  params: URLSearchParams,
): Promise<PageResponse<EvalReleaseGate>> {
  return (
    await http.get<PageResponse<EvalReleaseGate>>(`/api/agent/evals/release-gates/page?${params}`)
  ).data
}

export async function runReleaseGate(payload: Record<string, unknown>): Promise<EvalReleaseGate> {
  return (await http.post<EvalReleaseGate>('/api/agent/evals/release-gates/run', payload)).data
}

export async function compareEvalRuns(params: URLSearchParams): Promise<EvalRunComparison> {
  return (await http.get<EvalRunComparison>(`/api/agent/evals/runs/compare?${params}`)).data
}

export async function fetchRetrievalStatus(): Promise<RetrievalStatus> {
  return (await http.get<RetrievalStatus>('/api/knowledge/retrieval/status')).data
}

export async function fetchKnowledgeDocuments(
  params: URLSearchParams,
): Promise<KnowledgeDocument[]> {
  return (await http.get<KnowledgeDocument[]>(`/api/knowledge/documents?${params}`)).data
}

export async function fetchKnowledgeDocumentsPage(
  params: URLSearchParams,
): Promise<PageResponse<KnowledgeDocument>> {
  return (
    await http.get<PageResponse<KnowledgeDocument>>(`/api/knowledge/documents/page?${params}`)
  ).data
}

export async function saveKnowledgeDocument(
  payload: Record<string, unknown>,
): Promise<KnowledgeDocument> {
  return (await http.post<KnowledgeDocument>('/api/knowledge/documents', payload)).data
}

export async function changeKnowledgeDocumentStatus(
  docId: string,
  operation: 'publish' | 'disable' | 'expire',
  params: URLSearchParams,
): Promise<KnowledgeDocument> {
  return (
    await http.post<KnowledgeDocument>(
      `/api/knowledge/documents/${encodeURIComponent(docId)}/${operation}?${params}`,
    )
  ).data
}

export async function reindexKnowledge(params: URLSearchParams): Promise<{ jobId: string }> {
  return (await http.post<{ jobId: string }>(`/api/knowledge/reindex?${params}`)).data
}

export async function fetchKnowledgeIndexJobs(
  params: URLSearchParams,
): Promise<KnowledgeIndexJob[]> {
  return (await http.get<KnowledgeIndexJob[]>(`/api/knowledge/index-jobs?${params}`)).data
}

export async function fetchQualityAlerts(params: URLSearchParams): Promise<QualityAlert[]> {
  return (await http.get<QualityAlert[]>(`/api/agent/quality/alerts?${params}`)).data
}

export async function fetchQualityAlertsPage(
  params: URLSearchParams,
): Promise<PageResponse<QualityAlert>> {
  return (await http.get<PageResponse<QualityAlert>>(`/api/agent/quality/alerts/page?${params}`))
    .data
}

export async function evaluateQualityAlerts(
  params: URLSearchParams,
): Promise<{ openAlerts: number }> {
  return (await http.post<{ openAlerts: number }>(`/api/agent/quality/alerts/evaluate?${params}`))
    .data
}

export async function createQualityTask(
  alertId: string,
  params: URLSearchParams,
): Promise<{ taskId: string }> {
  return (
    await http.post<{ taskId: string }>(
      `/api/agent/quality/alerts/${encodeURIComponent(alertId)}/task?${params}`,
    )
  ).data
}

export async function fetchQualityTasks(params: URLSearchParams): Promise<QualityTask[]> {
  return (await http.get<QualityTask[]>(`/api/agent/quality/alert-tasks?${params}`)).data
}

export async function fetchQualityTasksPage(
  params: URLSearchParams,
): Promise<PageResponse<QualityTask>> {
  return (
    await http.get<PageResponse<QualityTask>>(`/api/agent/quality/alert-tasks/page?${params}`)
  ).data
}

export async function transitionQualityTask(
  taskId: string,
  payload: Record<string, unknown>,
): Promise<QualityTask> {
  return (
    await http.post<QualityTask>(
      `/api/agent/quality/alert-tasks/${encodeURIComponent(taskId)}/transition`,
      payload,
    )
  ).data
}

export async function fetchSearchPreview(params: URLSearchParams): Promise<SearchPreview> {
  return (await http.get<SearchPreview>(`/api/knowledge/search/preview?${params}`)).data
}

export async function fetchAudit(traceId: string): Promise<AuditTrace> {
  return (await http.get<AuditTrace>(`/api/agent/audit/${encodeURIComponent(traceId)}`)).data
}
