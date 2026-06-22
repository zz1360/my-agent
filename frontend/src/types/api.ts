export interface Citation {
  sourceType?: string
  title?: string
  docId?: string
  chunkId?: string
  excerpt?: string
}

export interface ToolCall {
  tool?: string
  toolName?: string
  status: string
  summary?: string
  latencyMs?: number
  errorCode?: string
}

export interface AgentChatRequest {
  conversationId: string
  userId: string
  tenantId: string
  roles: string[]
  message: string
  returnCitations: boolean
}

export interface AgentChatResponse {
  traceId: string
  conversationId: string
  messageId: string
  answer: string
  riskLevel: string
  confidence: number
  citations: Citation[]
  toolCalls: ToolCall[]
  createdAt: string
}

export interface ConversationSummary {
  tenantId: string
  conversationId: string
  userId: string
  title: string
  lastMessage: string
  lastTraceId?: string
  lastRiskLevel?: string
  messageCount: number
  createdAt: string
  updatedAt: string
}

export interface ConversationMessage {
  messageId: string
  role: 'USER' | 'ASSISTANT'
  content: string
  traceId?: string
  riskLevel?: string
  confidence?: number
  citations?: Citation[]
  toolCalls?: ToolCall[]
  createdAt?: string
}

export interface ConversationDetail extends ConversationSummary {
  messages: ConversationMessage[]
}

export interface ApiErrorResponse {
  code?: string
  message?: string
  detail?: string
  status?: number
  traceId?: string
}

export interface OpsReadiness {
  application: string
  activeProfiles: string[]
  ready: boolean
  checks: Array<{ name: string; status: string; summary: string; details: Record<string, unknown> }>
  checkedAt: string
}

export interface OpsMetrics {
  totalQuestions: number
  averageAgentLatencyMs: number
  latestRagRecallAtK: number
  toolCallSuccessRate: number
  releaseGatePassed: number
  releaseGateBlocked: number
  flywayVersion: string
  measuredAt: string
}

export interface AgentAction {
  actionId: string
  traceId: string
  customerId: string
  actionType: string
  title: string
  riskLevel: string
  status: string
  createdAt: string
  [key: string]: unknown
}

export interface QualityMetrics {
  notHelpfulFeedback: number
  candidateCount: number
  approvedCandidates: number
  candidateConversionRate: number
  ragExperimentPassRate: number
  [key: string]: unknown
}

export interface EvalSuite {
  suiteId: string
  suiteName?: string
  name?: string
  suiteVersion: string
  caseCount: number
  enabled: boolean
  [key: string]: unknown
}

export interface EvalRun {
  runId: string
  status: string
  totalCases: number
  passedCases: number
  failedCases: number
  modelVersion?: string
  createdAt?: string
  startedAt?: string
  [key: string]: unknown
}

export interface RetrievalStatus {
  defaultMode: string
  vectorStoreEnabled: boolean
  vectorStoreReady: boolean
  vectorTable: string
  [key: string]: unknown
}

export interface SearchHit {
  title: string
  docId: string
  chunkId: string
  excerpt: string
  score: number
  vectorScore: number
  keywordScore: number
  ruleScore: number
  rerankerScore?: number
  rerankerProvider?: string
}

export interface SearchPreview {
  mode: string
  vectorReady: boolean
  topK: number
  hits: SearchHit[]
}

export interface RagAudit {
  retrievalMode: string
  knowledgeVersion: string
  topK: number
  vectorReady: boolean
  candidateCount: number
  returnedCount: number
  hits: SearchHit[]
}

export interface AuditTrace {
  traceId: string
  tenantId: string
  userId: string
  conversationId?: string
  userMessage: string
  finalAnswer: string
  riskLevel: string
  latencyMs: number
  createdAt: string
  toolCalls: ToolCall[]
  ragAudits: RagAudit[]
}
