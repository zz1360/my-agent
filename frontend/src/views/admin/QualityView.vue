<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshCw } from '@lucide/vue'
import { fetchQualityMetrics } from '@/api/agent'
import { errorMessage } from '@/api/http'
import { useContextStore } from '@/stores/context'
import type { QualityMetrics } from '@/types/api'

const context = useContextStore()
const metrics = ref<QualityMetrics | null>(null)
const loading = ref(false)
const cards = computed(() => [
  ['负反馈', metrics.value?.notHelpfulFeedback || 0, '条'],
  ['评测候选', metrics.value?.candidateCount || 0, '条'],
  ['已通过候选', metrics.value?.approvedCandidates || 0, '条'],
  ['候选转化率', `${Math.round(Number(metrics.value?.candidateConversionRate || 0) * 100)}%`, ''],
  ['RAG 实验通过率', `${Math.round(Number(metrics.value?.ragExperimentPassRate || 0) * 100)}%`, ''],
])

onMounted(load)

async function load() {
  loading.value = true
  const params = new URLSearchParams({ tenantId: context.tenantId, userId: context.userId })
  context.roles.forEach((role) => params.append('roles', role))
  try {
    metrics.value = await fetchQualityMetrics(params)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="quality-page" v-loading="loading">
    <section class="toolbar">
      <div><strong>反馈质量指标</strong><span>从用户反馈到评测资产的转化情况</span></div>
      <el-button :icon="RefreshCw" @click="load">刷新</el-button>
    </section>
    <section class="metric-grid">
      <div v-for="card in cards" :key="String(card[0])">
        <span>{{ card[0] }}</span
        ><strong
          >{{ card[1] }}<small>{{ card[2] }}</small></strong
        >
      </div>
    </section>
    <section class="raw-section">
      <h2>质量数据</h2>
      <pre>{{ JSON.stringify(metrics, null, 2) }}</pre>
    </section>
  </div>
</template>

<style scoped>
.quality-page {
  padding: 16px 20px 30px;
}
.toolbar {
  display: flex;
  min-height: 58px;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
}
.toolbar div {
  display: flex;
  flex-direction: column;
}
.toolbar strong,
.raw-section h2 {
  font-size: 13px;
}
.toolbar span {
  margin-top: 3px;
  color: var(--muted);
  font-size: 11px;
}
.metric-grid {
  display: grid;
  margin-top: 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
  grid-template-columns: repeat(5, 1fr);
}
.metric-grid > div {
  padding: 16px;
  border-right: 1px solid var(--line);
}
.metric-grid > div:last-child {
  border-right: 0;
}
.metric-grid span {
  display: block;
  color: var(--muted);
  font-size: 11px;
}
.metric-grid strong {
  display: block;
  margin-top: 8px;
  font-size: 21px;
}
.metric-grid small {
  margin-left: 3px;
  color: var(--muted);
  font-size: 10px;
}
.raw-section {
  margin-top: 12px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
}
.raw-section h2 {
  margin: 0 0 10px;
}
.raw-section pre {
  max-height: 430px;
  margin: 0;
  overflow: auto;
  color: #435160;
  font-size: 11px;
  line-height: 1.6;
}
@media (max-width: 850px) {
  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .metric-grid > div {
    border-bottom: 1px solid var(--line);
  }
}
@media (max-width: 650px) {
  .quality-page {
    padding: 10px;
  }
}
</style>
