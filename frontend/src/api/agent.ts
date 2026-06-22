import { apiUrl, contextHeaders, http } from './http'
import type {
  AgentAction,
  AgentChatRequest,
  AgentChatResponse,
  AuditTrace,
  ConversationDetail,
  ConversationSummary,
  EvalRun,
  EvalSuite,
  OpsMetrics,
  OpsReadiness,
  QualityMetrics,
  RetrievalStatus,
  SearchPreview,
} from '@/types/api'

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

export async function fetchQualityMetrics(params: URLSearchParams): Promise<QualityMetrics> {
  return (await http.get<QualityMetrics>(`/api/agent/feedback/quality-metrics?${params}`)).data
}

export async function fetchEvalSuites(params: URLSearchParams): Promise<EvalSuite[]> {
  return (await http.get<EvalSuite[]>(`/api/agent/evals/suites?${params}`)).data
}

export async function fetchEvalRuns(params: URLSearchParams): Promise<EvalRun[]> {
  return (await http.get<EvalRun[]>(`/api/agent/evals/runs?${params}`)).data
}

export async function fetchRetrievalStatus(): Promise<RetrievalStatus> {
  return (await http.get<RetrievalStatus>('/api/knowledge/retrieval/status')).data
}

export async function fetchSearchPreview(params: URLSearchParams): Promise<SearchPreview> {
  return (await http.get<SearchPreview>(`/api/knowledge/search/preview?${params}`)).data
}

export async function fetchAudit(traceId: string): Promise<AuditTrace> {
  return (await http.get<AuditTrace>(`/api/agent/audit/${encodeURIComponent(traceId)}`)).data
}
