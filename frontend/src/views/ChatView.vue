<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Bot,
  History,
  LayoutDashboard,
  Menu,
  MessageSquarePlus,
  PanelRight,
  RefreshCw,
  Send,
  Settings,
  Square,
  User,
} from '@lucide/vue'
import ChatDetailsPanel from '@/components/ChatDetailsPanel.vue'
import ContextDrawer from '@/components/ContextDrawer.vue'
import MarkdownContent from '@/components/MarkdownContent.vue'
import {
  fetchConversation,
  fetchConversations,
  fetchDemoQuestions,
  generateActions,
  sendChat,
  sendChatStream,
  submitFeedback,
} from '@/api/agent'
import { errorMessage } from '@/api/http'
import { useContextStore } from '@/stores/context'
import type {
  AgentChatRequest,
  AgentChatResponse,
  ConversationMessage,
  ConversationSummary,
} from '@/types/api'

interface UiMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  response?: AgentChatResponse
}

const context = useContextStore()
const router = useRouter()

const conversationId = ref(newConversationId())
const input = ref('')
const messages = ref<UiMessage[]>([])
const conversations = ref<ConversationSummary[]>([])
const samples = ref<string[]>([])
const lastResponse = ref<AgentChatResponse | null>(null)
const sending = ref(false)
const loadingConversations = ref(false)
const streamEnabled = ref(true)
const returnCitations = ref(true)
const statusText = ref('就绪')
const contextDrawer = ref(false)
const detailsDrawer = ref(false)
const mobileSidebar = ref(false)
const messageList = ref<HTMLElement | null>(null)
let controller: AbortController | null = null

const contextLabel = computed(
  () => `${context.tenantId} · ${context.userId} · ${context.primaryRole}`,
)

watch(
  () => [context.tenantId, context.userId, context.roleHeader],
  () => {
    void loadConversations()
  },
)

onMounted(async () => {
  await Promise.all([loadConversations(), loadSamples()])
})

function newConversationId() {
  const date = new Date().toISOString().slice(0, 10).replaceAll('-', '')
  return `conv-web-${date}-${crypto.randomUUID().slice(0, 6)}`
}

function contextParams() {
  const params = new URLSearchParams({ tenantId: context.tenantId, userId: context.userId })
  context.roles.forEach((role) => params.append('roles', role))
  return params
}

function buildRequest(message: string): AgentChatRequest {
  return {
    conversationId: conversationId.value,
    tenantId: context.tenantId,
    userId: context.userId,
    roles: [...context.roles],
    message,
    returnCitations: returnCitations.value,
  }
}

async function loadSamples() {
  try {
    samples.value = await fetchDemoQuestions()
  } catch {
    samples.value = [
      '客户 C001 最近 30 天为什么投诉量上升？相关处理制度是什么？',
      '运单 WB202606010023 是否可能满足延误赔付条件？',
      '冷链运输超温后应该怎么处理？',
    ]
  }
}

async function loadConversations() {
  loadingConversations.value = true
  try {
    const params = contextParams()
    params.set('limit', '30')
    conversations.value = await fetchConversations(params)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loadingConversations.value = false
  }
}

async function loadConversation(id: string) {
  try {
    const detail = await fetchConversation(id, contextParams())
    conversationId.value = detail.conversationId
    messages.value = detail.messages.map(toUiMessage)
    const assistant = [...detail.messages].reverse().find((message) => message.role === 'ASSISTANT')
    lastResponse.value = assistant ? messageToResponse(assistant, detail.conversationId) : null
    statusText.value = '已加载历史'
    mobileSidebar.value = false
    await scrollToBottom()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

function toUiMessage(message: ConversationMessage): UiMessage {
  return {
    id: message.messageId,
    role: message.role === 'USER' ? 'user' : 'assistant',
    content: message.content,
    response:
      message.role === 'ASSISTANT' ? messageToResponse(message, conversationId.value) : undefined,
  }
}

function messageToResponse(message: ConversationMessage, id: string): AgentChatResponse {
  return {
    traceId: message.traceId || '',
    conversationId: id,
    messageId: message.messageId,
    answer: message.content,
    riskLevel: message.riskLevel || 'UNKNOWN',
    confidence: message.confidence || 0,
    citations: message.citations || [],
    toolCalls: message.toolCalls || [],
    createdAt: message.createdAt || new Date().toISOString(),
  }
}

async function submit() {
  const message = input.value.trim()
  if (!message || sending.value) return

  input.value = ''
  sending.value = true
  statusText.value = '正在查询'
  messages.value.push({ id: crypto.randomUUID(), role: 'user', content: message })
  const assistant: UiMessage = {
    id: crypto.randomUUID(),
    role: 'assistant',
    content: streamEnabled.value ? '' : '正在查询业务数据和知识库…',
  }
  messages.value.push(assistant)
  await scrollToBottom()

  try {
    const request = buildRequest(message)
    if (streamEnabled.value) {
      controller = new AbortController()
      await sendChatStream(
        request,
        (event, payload) => handleStreamEvent(event, payload, assistant),
        controller.signal,
      )
    } else {
      const response = await sendChat(request)
      applyResponse(assistant, response)
    }
    statusText.value = '已完成'
    await loadConversations()
  } catch (error) {
    if ((error as Error).name === 'AbortError') {
      statusText.value = '已停止'
      if (!assistant.content) assistant.content = '回答生成已停止。'
    } else {
      assistant.content = `请求失败：${errorMessage(error)}`
      statusText.value = '失败'
      ElMessage.error(errorMessage(error))
    }
  } finally {
    sending.value = false
    controller = null
    await scrollToBottom()
  }
}

function handleStreamEvent(event: string, raw: unknown, assistant: UiMessage) {
  const payload = raw as Record<string, unknown>
  if (event === 'status') {
    statusText.value = String(payload.message || '处理中')
  } else if (event === 'delta') {
    assistant.content += String(payload.delta || '')
    void scrollToBottom()
  } else if (event === 'complete') {
    applyResponse(assistant, raw as AgentChatResponse)
  } else if (event === 'error') {
    throw new Error(String(payload.message || '流式请求失败'))
  }
}

function applyResponse(message: UiMessage, response: AgentChatResponse) {
  message.id = response.messageId || message.id
  message.content = response.answer
  message.response = response
  lastResponse.value = response
}

function stopStreaming() {
  controller?.abort()
}

function resetConversation() {
  controller?.abort()
  conversationId.value = newConversationId()
  messages.value = []
  lastResponse.value = null
  statusText.value = '就绪'
}

function useSample(question: string) {
  input.value = question
  void submit()
}

function selectResponse(message: UiMessage) {
  if (!message.response) return
  lastResponse.value = message.response
  if (window.innerWidth < 1200) detailsDrawer.value = true
}

async function feedback(rating: 'HELPFUL' | 'NOT_HELPFUL', reason: string) {
  if (!lastResponse.value?.messageId) return
  try {
    const result = await submitFeedback(lastResponse.value.messageId, {
      tenantId: context.tenantId,
      userId: context.userId,
      roles: context.roles,
      conversationId: conversationId.value,
      traceId: lastResponse.value.traceId,
      rating,
      reason,
      comment: '',
    })
    ElMessage.success(`反馈已记录：${result.rating}`)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

async function createActions() {
  const response = lastResponse.value
  if (!response?.traceId) return
  const customerId = `${messages.value.at(-2)?.content || ''}\n${response.answer}`.match(
    /\bC\d{3}\b/i,
  )?.[0]
  if (!customerId) {
    ElMessage.warning('当前问答中没有识别到客户编号')
    return
  }
  try {
    const actions = await generateActions({
      tenantId: context.tenantId,
      userId: context.userId,
      roles: context.roles,
      traceId: response.traceId,
      conversationId: conversationId.value,
      customerId: customerId.toUpperCase(),
      days: 30,
    })
    ElMessage.success(`已生成 ${actions.length} 条动作草稿`)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

async function scrollToBottom() {
  await nextTick()
  if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight
}
</script>

<template>
  <div class="chat-app">
    <header class="topbar">
      <div class="brand">
        <button class="icon-button mobile-only" title="会话列表" @click="mobileSidebar = true">
          <Menu :size="19" />
        </button>
        <div class="brand-mark"><Bot :size="20" /></div>
        <div><strong>物流 Agent 对话台</strong><span>业务问答与可审计 RAG</span></div>
      </div>
      <div class="top-actions">
        <span class="context-label truncate">{{ contextLabel }}</span>
        <span class="request-status"
          ><i :class="['status-dot', sending ? 'warn' : 'up']" />{{ statusText }}</span
        >
        <button class="icon-button" title="回答详情" @click="detailsDrawer = true">
          <PanelRight :size="18" />
        </button>
        <button class="icon-button" title="运行上下文" @click="contextDrawer = true">
          <Settings :size="18" />
        </button>
        <button class="icon-button" title="进入管理台" @click="router.push('/operations/overview')">
          <LayoutDashboard :size="18" />
        </button>
      </div>
    </header>

    <div class="chat-layout">
      <aside :class="['conversation-sidebar', { 'mobile-open': mobileSidebar }]">
        <div class="sidebar-heading">
          <div><History :size="17" /><strong>历史会话</strong></div>
          <div>
            <button class="icon-button small" title="刷新" @click="loadConversations">
              <RefreshCw :size="15" />
            </button>
            <button class="icon-button small" title="新建会话" @click="resetConversation">
              <MessageSquarePlus :size="15" />
            </button>
          </div>
        </div>
        <div v-if="loadingConversations" class="sidebar-empty">正在加载…</div>
        <div v-else-if="!conversations.length" class="sidebar-empty">暂无历史会话</div>
        <div v-else class="conversation-list">
          <button
            v-for="item in conversations"
            :key="item.conversationId"
            :class="['conversation-row', { active: item.conversationId === conversationId }]"
            @click="loadConversation(item.conversationId)"
          >
            <strong class="truncate">{{ item.title || item.conversationId }}</strong>
            <span class="truncate"
              >{{ item.lastRiskLevel || 'UNKNOWN' }} · {{ item.messageCount }} 条</span
            >
            <small class="mono truncate">{{ item.conversationId }}</small>
          </button>
        </div>
        <div class="sample-area">
          <span>常用问题</span>
          <button
            v-for="question in samples.slice(0, 4)"
            :key="question"
            @click="useSample(question)"
          >
            {{ question }}
          </button>
        </div>
      </aside>
      <button
        v-if="mobileSidebar"
        class="sidebar-backdrop"
        aria-label="关闭会话列表"
        @click="mobileSidebar = false"
      />

      <main class="chat-main">
        <div ref="messageList" class="message-list">
          <div v-if="!messages.length" class="welcome-state">
            <div class="welcome-icon"><Bot :size="26" /></div>
            <h1>物流业务问答</h1>
            <p>查询客户、运单、异常、工单与制度知识。</p>
            <div class="welcome-samples">
              <button
                v-for="question in samples.slice(0, 3)"
                :key="question"
                @click="useSample(question)"
              >
                {{ question }}
              </button>
            </div>
          </div>

          <article v-for="message in messages" :key="message.id" :class="['message', message.role]">
            <div class="message-avatar">
              <User v-if="message.role === 'user'" :size="16" /><Bot v-else :size="16" />
            </div>
            <div class="message-content" @click="selectResponse(message)">
              <p v-if="message.role === 'user'">{{ message.content }}</p>
              <MarkdownContent v-else-if="message.content" :content="message.content" />
              <div v-else class="typing"><span /><span /><span /></div>
              <div v-if="message.response" class="message-meta">
                <el-tag size="small" effect="plain">{{ message.response.riskLevel }}</el-tag>
                <span>{{ Math.round(message.response.confidence * 100) }}% 置信度</span>
                <span>{{ message.response.citations.length }} 条引用</span>
                <span>{{ message.response.toolCalls.length }} 次工具</span>
              </div>
            </div>
          </article>
        </div>

        <div class="composer-wrap">
          <div class="composer-options">
            <el-checkbox v-model="streamEnabled">流式输出</el-checkbox>
            <el-checkbox v-model="returnCitations">返回引用</el-checkbox>
            <span class="mono truncate">{{ conversationId }}</span>
          </div>
          <form class="composer" @submit.prevent="submit">
            <textarea
              v-model="input"
              rows="2"
              placeholder="输入物流业务问题…"
              @keydown.enter.exact.prevent="submit"
            />
            <button
              v-if="sending"
              class="send-button stop"
              type="button"
              title="停止生成"
              @click="stopStreaming"
            >
              <Square :size="17" />
            </button>
            <button v-else class="send-button" type="submit" title="发送" :disabled="!input.trim()">
              <Send :size="18" />
            </button>
          </form>
        </div>
      </main>

      <aside class="details-aside">
        <ChatDetailsPanel
          :response="lastResponse"
          @feedback="feedback"
          @generate-actions="createActions"
        />
      </aside>
    </div>

    <ContextDrawer v-model="contextDrawer" />
    <el-drawer
      v-model="detailsDrawer"
      title="回答详情"
      size="min(420px, 94vw)"
      class="details-drawer"
    >
      <ChatDetailsPanel
        :response="lastResponse"
        @feedback="feedback"
        @generate-actions="createActions"
      />
    </el-drawer>
  </div>
</template>

<style scoped>
.chat-app {
  height: 100vh;
  overflow: hidden;
}

.topbar {
  display: flex;
  height: 64px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 0 18px;
  border-bottom: 1px solid var(--line);
  background: var(--surface);
}

.brand,
.brand > div,
.top-actions,
.request-status,
.sidebar-heading,
.sidebar-heading > div {
  display: flex;
  align-items: center;
}

.brand {
  gap: 10px;
  min-width: 0;
}

.brand-mark {
  width: 36px;
  height: 36px;
  justify-content: center;
  border-radius: 7px;
  background: var(--surface-tint);
  color: var(--accent);
}

.brand > div:last-child {
  align-items: flex-start;
  flex-direction: column;
}

.brand strong {
  font-size: 15px;
}

.brand span {
  color: var(--muted);
  font-size: 11px;
}

.top-actions {
  min-width: 0;
  gap: 8px;
}

.context-label {
  max-width: 280px;
  color: var(--muted);
  font-size: 12px;
}

.request-status {
  gap: 7px;
  padding: 6px 9px;
  border: 1px solid var(--line);
  border-radius: 5px;
  font-size: 11px;
}

.icon-button {
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

.icon-button:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.icon-button.small {
  width: 28px;
  height: 28px;
}

.chat-layout {
  display: grid;
  height: calc(100vh - 64px);
  grid-template-columns: 290px minmax(420px, 1fr) 360px;
}

.conversation-sidebar,
.details-aside {
  min-width: 0;
  border-right: 1px solid var(--line);
  background: var(--surface);
}

.details-aside {
  border-right: 0;
  border-left: 1px solid var(--line);
}

.conversation-sidebar {
  display: flex;
  min-height: 0;
  flex-direction: column;
}

.sidebar-heading {
  height: 54px;
  justify-content: space-between;
  padding: 0 14px;
  border-bottom: 1px solid var(--line);
}

.sidebar-heading > div {
  gap: 8px;
}

.sidebar-heading strong {
  font-size: 13px;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conversation-row {
  display: block;
  width: 100%;
  min-height: 70px;
  margin-bottom: 4px;
  padding: 10px;
  overflow: hidden;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
}

.conversation-row:hover,
.conversation-row.active {
  border-color: #b9ddd7;
  background: var(--surface-tint);
}

.conversation-row strong,
.conversation-row span,
.conversation-row small {
  display: block;
}

.conversation-row strong {
  font-size: 12px;
}

.conversation-row span {
  margin-top: 5px;
  color: var(--muted);
  font-size: 11px;
}

.conversation-row small {
  margin-top: 4px;
  color: #98a2b3;
  font-size: 9px;
}

.sidebar-empty {
  flex: 1;
  padding: 40px 12px;
  color: var(--muted);
  text-align: center;
  font-size: 12px;
}

.sample-area {
  max-height: 218px;
  overflow-y: auto;
  padding: 12px;
  border-top: 1px solid var(--line);
}

.sample-area > span {
  display: block;
  margin-bottom: 8px;
  color: var(--muted);
  font-size: 11px;
  font-weight: 700;
}

.sample-area button,
.welcome-samples button {
  width: 100%;
  margin-bottom: 6px;
  padding: 8px 9px;
  border: 1px solid var(--line);
  border-radius: 5px;
  background: var(--surface);
  color: #435160;
  text-align: left;
  font-size: 11px;
  line-height: 1.45;
  cursor: pointer;
}

.sample-area button:hover,
.welcome-samples button:hover {
  border-color: var(--accent);
}

.chat-main {
  display: grid;
  min-width: 0;
  min-height: 0;
  grid-template-rows: minmax(0, 1fr) auto;
  background: #f8fafb;
}

.message-list {
  overflow-y: auto;
  padding: 28px max(24px, calc((100% - 780px) / 2));
}

.welcome-state {
  display: flex;
  min-height: 70%;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  text-align: center;
}

.welcome-icon {
  display: flex;
  width: 50px;
  height: 50px;
  align-items: center;
  justify-content: center;
  border: 1px solid #b9ddd7;
  border-radius: 8px;
  background: var(--surface-tint);
  color: var(--accent);
}

.welcome-state h1 {
  margin: 14px 0 4px;
  font-size: 20px;
}

.welcome-state p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}

.welcome-samples {
  display: grid;
  width: min(620px, 100%);
  margin-top: 22px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.message {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 20px;
}

.message.user {
  justify-content: flex-end;
}

.message.user .message-avatar {
  order: 2;
  background: #eaf0ff;
  color: var(--blue);
}

.message-avatar {
  display: flex;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: var(--surface-tint);
  color: var(--accent);
}

.message-content {
  width: fit-content;
  max-width: min(720px, calc(100% - 46px));
  padding: 13px 15px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: var(--surface);
  box-shadow: 0 3px 10px rgba(24, 34, 45, 0.04);
  font-size: 13px;
}

.message.user .message-content {
  border-color: #cbd8fa;
  background: #f2f6ff;
}

.message-content > p {
  margin: 0;
  line-height: 1.65;
  white-space: pre-wrap;
}

.message-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  padding-top: 9px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: 10px;
  cursor: pointer;
}

.typing {
  display: flex;
  gap: 4px;
  padding: 7px 2px;
}

.typing span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  animation: pulse 1.2s infinite ease-in-out;
}

.typing span:nth-child(2) {
  animation-delay: 0.15s;
}
.typing span:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes pulse {
  0%,
  80%,
  100% {
    opacity: 0.3;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.composer-wrap {
  padding: 10px max(24px, calc((100% - 780px) / 2)) 16px;
  border-top: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.96);
}

.composer-options {
  display: flex;
  height: 30px;
  align-items: center;
  gap: 14px;
  color: var(--muted);
  font-size: 11px;
}

.composer-options > span {
  margin-left: auto;
  max-width: 300px;
}

.composer {
  display: grid;
  min-height: 72px;
  grid-template-columns: minmax(0, 1fr) 42px;
  align-items: end;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--line-strong);
  border-radius: 7px;
  background: var(--surface);
  box-shadow: var(--shadow);
}

.composer:focus-within {
  border-color: var(--accent);
}

.composer textarea {
  width: 100%;
  max-height: 150px;
  resize: none;
  border: 0;
  outline: 0;
  color: var(--ink);
  line-height: 1.55;
}

.send-button {
  display: flex;
  width: 38px;
  height: 38px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: var(--accent);
  color: white;
  cursor: pointer;
}

.send-button:hover {
  background: var(--accent-strong);
}
.send-button:disabled {
  background: #c4ccd5;
  cursor: not-allowed;
}
.send-button.stop {
  background: var(--red);
}

.mobile-only,
.sidebar-backdrop {
  display: none;
}

:deep(.details-drawer .el-drawer__body) {
  padding: 0;
}

@media (max-width: 1199px) {
  .chat-layout {
    grid-template-columns: 270px minmax(0, 1fr);
  }
  .details-aside {
    display: none;
  }
}

@media (max-width: 760px) {
  .topbar {
    height: 58px;
    padding: 0 10px;
  }
  .brand span,
  .context-label,
  .request-status {
    display: none;
  }
  .mobile-only {
    display: inline-flex;
  }
  .chat-layout {
    height: calc(100vh - 58px);
    grid-template-columns: minmax(0, 1fr);
  }
  .conversation-sidebar {
    position: fixed;
    z-index: 20;
    top: 58px;
    bottom: 0;
    left: 0;
    width: min(310px, 86vw);
    transform: translateX(-100%);
    transition: transform 0.2s ease;
  }
  .conversation-sidebar.mobile-open {
    transform: translateX(0);
  }
  .sidebar-backdrop {
    position: fixed;
    z-index: 19;
    inset: 58px 0 0;
    display: block;
    border: 0;
    background: rgba(20, 28, 36, 0.3);
  }
  .message-list {
    padding: 18px 12px;
  }
  .message-content {
    max-width: calc(100% - 40px);
    padding: 11px 12px;
  }
  .composer-wrap {
    padding: 7px 10px 10px;
  }
  .composer-options > span {
    display: none;
  }
  .welcome-samples {
    grid-template-columns: 1fr;
  }
  .welcome-state {
    min-height: 55%;
  }
}
</style>
