<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@lucide/vue'
import MarkdownContent from '@/components/MarkdownContent.vue'
import { fetchAudit } from '@/api/agent'
import { errorMessage } from '@/api/http'
import type { AuditTrace } from '@/types/api'

const traceId = ref('')
const trace = ref<AuditTrace | null>(null)
const loading = ref(false)

async function search() {
  if (!traceId.value.trim()) return
  loading.value = true
  try {
    trace.value = await fetchAudit(traceId.value.trim())
  } catch (error) {
    trace.value = null
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="audit-page">
    <section class="search-band">
      <el-input
        v-model="traceId"
        class="mono"
        placeholder="输入 traceId"
        clearable
        @keyup.enter="search"
      /><el-button type="primary" :icon="Search" @click="search">查询</el-button>
    </section>
    <section v-if="trace" class="audit-body" v-loading="loading">
      <div class="audit-meta">
        <div>
          <span>Trace</span><strong class="mono">{{ trace.traceId }}</strong>
        </div>
        <div>
          <span>租户 / 用户</span><strong>{{ trace.tenantId }} / {{ trace.userId }}</strong>
        </div>
        <div>
          <span>风险 / 耗时</span><strong>{{ trace.riskLevel }} / {{ trace.latencyMs }} ms</strong>
        </div>
      </div>
      <div class="audit-section">
        <h2>用户问题</h2>
        <p>{{ trace.userMessage }}</p>
      </div>
      <div class="audit-section">
        <h2>最终回答</h2>
        <MarkdownContent :content="trace.finalAnswer" />
      </div>
      <div class="audit-section">
        <h2>RAG 审计</h2>
        <div v-for="(rag, index) in trace.ragAudits" :key="index" class="rag-row">
          <strong>{{ rag.retrievalMode }}</strong
          ><span>{{ rag.knowledgeVersion }}</span
          ><small
            >topK {{ rag.topK }} · 候选 {{ rag.candidateCount }} · 返回 {{ rag.returnedCount }} ·
            vector {{ rag.vectorReady ? 'ready' : 'down' }}</small
          >
        </div>
      </div>
    </section>
    <div v-else class="empty-state" v-loading="loading">输入问答响应中的 traceId 查询完整链路</div>
  </div>
</template>

<style scoped>
.audit-page {
  padding: 16px 20px 30px;
}
.search-band {
  display: grid;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
  grid-template-columns: minmax(260px, 600px) auto 1fr;
  gap: 9px;
}
.audit-body {
  margin-top: 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
}
.audit-meta {
  display: grid;
  padding: 14px;
  border-bottom: 1px solid var(--line);
  grid-template-columns: 1.5fr 1fr 1fr;
  gap: 16px;
}
.audit-meta span,
.audit-meta strong {
  display: block;
}
.audit-meta span {
  color: var(--muted);
  font-size: 10px;
}
.audit-meta strong {
  margin-top: 4px;
  overflow-wrap: anywhere;
  font-size: 11px;
}
.audit-section {
  padding: 16px;
  border-bottom: 1px solid var(--line);
}
.audit-section:last-child {
  border-bottom: 0;
}
.audit-section h2 {
  margin: 0 0 10px;
  font-size: 13px;
}
.audit-section > p {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
}
.rag-row {
  display: grid;
  padding: 9px 0;
  grid-template-columns: 160px minmax(0, 1fr);
  gap: 4px 14px;
  border-bottom: 1px solid var(--line);
}
.rag-row:last-child {
  border-bottom: 0;
}
.rag-row strong,
.rag-row span,
.rag-row small {
  font-size: 10px;
}
.rag-row span {
  overflow-wrap: anywhere;
  color: #435160;
}
.rag-row small {
  color: var(--muted);
  grid-column: 1 / -1;
}
.empty-state {
  margin-top: 12px;
  padding: 100px 20px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
  color: var(--muted);
  text-align: center;
  font-size: 12px;
}
@media (max-width: 650px) {
  .audit-page {
    padding: 10px;
  }
  .search-band {
    grid-template-columns: 1fr auto;
  }
  .audit-meta {
    grid-template-columns: 1fr;
  }
  .rag-row {
    grid-template-columns: 1fr;
  }
  .rag-row small {
    grid-column: auto;
  }
}
</style>
