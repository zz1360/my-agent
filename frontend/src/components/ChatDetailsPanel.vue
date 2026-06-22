<script setup lang="ts">
import { computed, ref } from 'vue'
import { ClipboardList, ExternalLink, FileText, ThumbsDown, ThumbsUp, Wrench } from '@lucide/vue'
import type { AgentChatResponse } from '@/types/api'

const props = defineProps<{ response: AgentChatResponse | null }>()
const emit = defineEmits<{
  feedback: [rating: 'HELPFUL' | 'NOT_HELPFUL', reason: string]
  generateActions: []
}>()

const reason = ref('ANSWER_USEFUL')
const confidence = computed(() =>
  props.response ? `${Math.round((props.response.confidence || 0) * 100)}%` : '暂无',
)

function riskType(risk?: string) {
  if (risk === 'L1') return 'success'
  if (risk === 'L2') return 'warning'
  return 'danger'
}
</script>

<template>
  <section class="details-panel">
    <div class="details-heading">
      <div>
        <h2>回答详情</h2>
        <p>引用、工具调用与审计链路</p>
      </div>
    </div>

    <div v-if="!response" class="empty-detail">
      <FileText :size="24" />
      <span>暂无回答详情</span>
    </div>

    <template v-else>
      <div class="trace-summary">
        <div class="trace-line">
          <span>风险等级</span>
          <el-tag :type="riskType(response.riskLevel)" effect="plain">{{
            response.riskLevel
          }}</el-tag>
        </div>
        <div class="trace-line">
          <span>置信度</span><strong>{{ confidence }}</strong>
        </div>
        <div class="trace-line">
          <span>工具调用</span><strong>{{ response.toolCalls.length }} 次</strong>
        </div>
        <div class="trace-id mono">{{ response.traceId }}</div>
        <div class="detail-actions">
          <el-button
            tag="a"
            :href="`/api/agent/audit/${encodeURIComponent(response.traceId)}`"
            target="_blank"
            :icon="ExternalLink"
          >
            审计
          </el-button>
          <el-button :icon="ClipboardList" @click="emit('generateActions')">生成动作</el-button>
        </div>
      </div>

      <div class="feedback-bar">
        <el-select v-model="reason" size="small">
          <el-option label="回答可用" value="ANSWER_USEFUL" />
          <el-option label="回答不可用" value="ANSWER_NOT_USEFUL" />
          <el-option label="引用不准" value="CITATION_WEAK" />
          <el-option label="业务数据不足" value="BUSINESS_DATA_MISSING" />
          <el-option label="动作风险" value="ACTION_RISK" />
        </el-select>
        <el-button
          title="有帮助"
          :icon="ThumbsUp"
          circle
          @click="emit('feedback', 'HELPFUL', reason)"
        />
        <el-button
          title="无帮助"
          :icon="ThumbsDown"
          circle
          @click="emit('feedback', 'NOT_HELPFUL', reason)"
        />
      </div>

      <div class="detail-section">
        <div class="section-label">
          <FileText :size="16" /><span>引用</span><b>{{ response.citations.length }}</b>
        </div>
        <div v-if="response.citations.length" class="citation-list">
          <article
            v-for="citation in response.citations"
            :key="`${citation.docId}-${citation.chunkId}`"
            class="citation-item"
          >
            <strong>{{ citation.title || citation.sourceType || '知识引用' }}</strong>
            <span class="mono">{{ citation.docId }} / {{ citation.chunkId }}</span>
            <p>{{ citation.excerpt }}</p>
          </article>
        </div>
        <div v-else class="empty-row">暂无引用</div>
      </div>

      <div class="detail-section">
        <div class="section-label">
          <Wrench :size="16" /><span>工具调用</span><b>{{ response.toolCalls.length }}</b>
        </div>
        <div v-if="response.toolCalls.length" class="tool-list">
          <article
            v-for="(tool, index) in response.toolCalls"
            :key="`${tool.tool || tool.toolName}-${index}`"
            class="tool-item"
          >
            <div>
              <strong class="mono">{{ tool.tool || tool.toolName }}</strong
              ><el-tag size="small" effect="plain">{{ tool.status }}</el-tag>
            </div>
            <p>{{ tool.summary || tool.errorCode || '无摘要' }}</p>
            <span>{{ tool.latencyMs || 0 }} ms</span>
          </article>
        </div>
        <div v-else class="empty-row">暂无工具调用</div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.details-panel {
  height: 100%;
  overflow-y: auto;
  background: var(--surface);
}

.details-heading {
  position: sticky;
  top: 0;
  z-index: 2;
  padding: 18px 20px;
  border-bottom: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.96);
}

.details-heading h2 {
  margin: 0;
  font-size: 15px;
}

.details-heading p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12px;
}

.empty-detail {
  display: flex;
  min-height: 260px;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--muted);
}

.trace-summary,
.detail-section {
  padding: 18px 20px;
  border-bottom: 1px solid var(--line);
}

.trace-line {
  display: flex;
  min-height: 30px;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
}

.trace-line span:first-child {
  color: var(--muted);
}

.trace-id {
  margin-top: 8px;
  padding: 9px;
  overflow-wrap: anywhere;
  border: 1px solid var(--line);
  border-radius: 5px;
  background: var(--surface-soft);
  color: var(--muted);
  font-size: 11px;
}

.detail-actions,
.feedback-bar {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.feedback-bar {
  padding: 14px 20px;
  border-bottom: 1px solid var(--line);
}

.feedback-bar .el-select {
  flex: 1;
}

.section-label {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 700;
}

.section-label b {
  margin-left: auto;
  color: var(--muted);
}

.citation-list,
.tool-list {
  display: grid;
  gap: 10px;
}

.citation-item,
.tool-item {
  padding: 11px;
  border: 1px solid var(--line);
  border-radius: 6px;
}

.citation-item strong,
.citation-item span,
.tool-item strong,
.tool-item span {
  display: block;
  font-size: 12px;
}

.citation-item span,
.tool-item span {
  margin-top: 4px;
  color: var(--muted);
  font-size: 10px;
}

.citation-item p,
.tool-item p {
  margin: 8px 0 0;
  color: #435160;
  font-size: 12px;
  line-height: 1.55;
}

.tool-item > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.empty-row {
  padding: 18px 0;
  color: var(--muted);
  text-align: center;
  font-size: 12px;
}
</style>
