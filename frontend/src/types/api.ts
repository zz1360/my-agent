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

export type AgentPermission =
  | 'CHAT_USE'
  | 'OPS_VIEW'
  | 'AUDIT_VIEW'
  | 'ACTION_MANAGE'
  | 'KNOWLEDGE_MANAGE'
  | 'QUALITY_MANAGE'
  | 'EVAL_MANAGE'

export interface SecurityContext {
  tenantId: string
  userId: string
  roles: string[]
  permissions: AgentPermission[]
  authenticated: boolean
  apiKeyRequired: boolean
  authenticationType: string
}

export interface SecurityConfig {
  mode: string
  loginUrl: string
  logoutUrl: string
  csrfUrl: string
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  total: number
  totalPages: number
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
  tenantId: string
  actionId: string
  traceId: string
  conversationId?: string
  customerId: string
  waybillId?: string
  actionType: string
  title: string
  priority?: string
  riskLevel: string
  status: string
  draftContent?: string
  evidenceJson?: string
  createdBy?: string
  reviewerId?: string
  reviewComment?: string
  createdAt: string
  updatedAt?: string
  reviewedAt?: string
  [key: string]: unknown
}

export interface ActionExecution {
  tenantId: string
  executionId: string
  actionId: string
  actionType: string
  executorName: string
  targetSystem: string
  externalRefId?: string
  idempotencyKey: string
  lowRisk: boolean
  status: string
  requestJson?: string
  responseJson?: string
  failureReason?: string
  retryCount: number
  maxRetryCount: number
  nextRetryAt?: string
  executedBy: string
  startedAt?: string
  finishedAt?: string
}

export interface ActionBusinessLink {
  businessTable?: string
  businessId?: string
  status: string
  latestExecutionId?: string
  traceId?: string
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

export interface EvalReleaseGate {
  gateId: string
  tenantId: string
  suiteId: string
  status: string
  candidateRunId: string
  baselineRunId?: string
  totalCases: number
  passedCases: number
  failedCases: number
  passRate: number
  minPassRate: number
  regressedCases: number
  maxRegressions: number
  reasons: string[]
  createdAt: string
}

export interface EvalRunComparison {
  baselineRunId: string
  candidateRunId: string
  totalCases: number
  unchangedCases: number
  improvedCases: number
  regressedCases: number
  newCases: number
  removedCases: number
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

export interface KnowledgeDocument {
  tenantId: string
  docId: string
  baseDocId: string
  title: string
  docType: string
  bizDomain: string
  version: string
  sourceUrl?: string
  aclRoles: string
  effectiveFrom?: string
  effectiveTo?: string
  status: string
  content: string
  chunkCount: number
  indexJobId?: string
  publishedAt?: string
  indexedAt?: string
  createdAt: string
  updatedAt: string
}

export interface KnowledgeIndexJob {
  jobId: string
  triggerType: string
  requestedBy: string
  status: string
  documentId?: string
  chunkCount: number
  vectorEnabled: boolean
  vectorReady: boolean
  errorMessage?: string
  createdAt: string
  finishedAt?: string
}

export interface QualityAlert {
  alertId: string
  ruleId: string
  metricType: string
  severity: string
  status: string
  metricValue: number
  thresholdValue: number
  summary: string
  taskId?: string
  lastTriggeredAt: string
}

export interface QualityTask {
  alertId: string
  taskId: string
  actionId: string
  title: string
  description: string
  ownerRole: string
  ownerUserId?: string
  status: string
  lastComment?: string
  createdAt: string
  updatedAt: string
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
