<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshCw } from '@lucide/vue'
import { fetchOpsMetrics, fetchReadiness } from '@/api/agent'
import { errorMessage } from '@/api/http'
import type { OpsMetrics, OpsReadiness } from '@/types/api'

const readiness = ref<OpsReadiness | null>(null)
const metrics = ref<OpsMetrics | null>(null)
const loading = ref(false)

const metricRows = computed(() => [
  { label: '累计问题', value: metrics.value?.totalQuestions ?? 0, suffix: '条' },
  {
    label: '平均回答耗时',
    value: Number(metrics.value?.averageAgentLatencyMs || 0).toFixed(0),
    suffix: 'ms',
  },
  {
    label: '工具成功率',
    value: `${Math.round(Number(metrics.value?.toolCallSuccessRate || 0) * 100)}%`,
    suffix: '',
  },
  {
    label: 'RAG Recall@K',
    value: Number(metrics.value?.latestRagRecallAtK || 0).toFixed(2),
    suffix: '',
  },
  { label: '发布门禁通过', value: metrics.value?.releaseGatePassed ?? 0, suffix: '次' },
  { label: 'Flyway', value: metrics.value?.flywayVersion || '-', suffix: '' },
])

onMounted(load)

async function load() {
  loading.value = true
  try {
    ;[readiness.value, metrics.value] = await Promise.all([fetchReadiness(), fetchOpsMetrics()])
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function statusClass(status: string) {
  return status.toLowerCase()
}
</script>

<template>
  <div class="page-content" v-loading="loading">
    <section class="toolbar-band">
      <div><strong>企业运行状态</strong><span>核心依赖、指标和发布门禁</span></div>
      <el-button :icon="RefreshCw" @click="load">刷新</el-button>
    </section>

    <section class="metric-grid">
      <div v-for="item in metricRows" :key="item.label" class="metric-cell">
        <span>{{ item.label }}</span>
        <strong
          >{{ item.value }}<small>{{ item.suffix }}</small></strong
        >
      </div>
    </section>

    <section class="data-section">
      <div class="section-title">
        <div>
          <h2>依赖检查</h2>
          <p>{{ readiness?.application || 'logistics-agent' }}</p>
        </div>
        <el-tag :type="readiness?.ready ? 'success' : 'danger'">{{
          readiness?.ready ? 'READY' : 'NOT READY'
        }}</el-tag>
      </div>
      <div class="check-table">
        <div class="check-row check-head">
          <span>组件</span><span>状态</span><span>说明</span><span>详情</span>
        </div>
        <div v-for="check in readiness?.checks || []" :key="check.name" class="check-row">
          <strong>{{ check.name }}</strong>
          <span class="check-status"
            ><i :class="['status-dot', statusClass(check.status)]" />{{ check.status }}</span
          >
          <span>{{ check.summary }}</span>
          <code>{{ JSON.stringify(check.details) }}</code>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page-content {
  padding: 16px 20px 30px;
}
.toolbar-band,
.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.toolbar-band {
  min-height: 58px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
}
.toolbar-band div,
.section-title div {
  display: flex;
  flex-direction: column;
}
.toolbar-band strong {
  font-size: 13px;
}
.toolbar-band span,
.section-title p {
  margin: 3px 0 0;
  color: var(--muted);
  font-size: 11px;
}
.metric-grid {
  display: grid;
  margin-top: 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
  grid-template-columns: repeat(6, minmax(0, 1fr));
}
.metric-cell {
  min-width: 0;
  padding: 16px;
  border-right: 1px solid var(--line);
}
.metric-cell:last-child {
  border-right: 0;
}
.metric-cell span {
  display: block;
  color: var(--muted);
  font-size: 11px;
}
.metric-cell strong {
  display: block;
  margin-top: 7px;
  font-size: 20px;
}
.metric-cell small {
  margin-left: 3px;
  color: var(--muted);
  font-size: 10px;
}
.data-section {
  margin-top: 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
}
.section-title {
  min-height: 60px;
  padding: 0 14px;
  border-bottom: 1px solid var(--line);
}
.section-title h2 {
  margin: 0;
  font-size: 13px;
}
.check-table {
  overflow-x: auto;
}
.check-row {
  display: grid;
  min-width: 780px;
  grid-template-columns: 150px 130px 240px minmax(260px, 1fr);
  border-bottom: 1px solid var(--line);
}
.check-row:last-child {
  border-bottom: 0;
}
.check-row > * {
  min-width: 0;
  padding: 11px 14px;
  overflow-wrap: anywhere;
  font-size: 11px;
}
.check-head {
  background: var(--surface-soft);
  color: var(--muted);
  font-weight: 700;
}
.check-status {
  display: flex;
  align-items: center;
  gap: 7px;
}
.check-row code {
  color: var(--muted);
  font-size: 9px;
}
@media (max-width: 1100px) {
  .metric-grid {
    grid-template-columns: repeat(3, 1fr);
  }
  .metric-cell:nth-child(3) {
    border-right: 0;
  }
  .metric-cell:nth-child(-n + 3) {
    border-bottom: 1px solid var(--line);
  }
}
@media (max-width: 650px) {
  .page-content {
    padding: 10px;
  }
  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .metric-cell:nth-child(3) {
    border-right: 1px solid var(--line);
  }
  .metric-cell:nth-child(even) {
    border-right: 0;
  }
  .metric-cell:nth-child(-n + 4) {
    border-bottom: 1px solid var(--line);
  }
}
</style>
