<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshCw, Search } from '@lucide/vue'
import { fetchRetrievalStatus, fetchSearchPreview } from '@/api/agent'
import { errorMessage } from '@/api/http'
import { useContextStore } from '@/stores/context'
import type { RetrievalStatus, SearchPreview } from '@/types/api'

const context = useContextStore()
const status = ref<RetrievalStatus | null>(null)
const preview = ref<SearchPreview | null>(null)
const query = ref('冷链运输超温后应该怎么处理？')
const mode = ref('hybrid_reranker')
const topK = ref(5)
const loading = ref(false)

onMounted(loadStatus)
async function loadStatus() {
  try {
    status.value = await fetchRetrievalStatus()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}
async function search() {
  if (!query.value.trim()) return
  loading.value = true
  const params = new URLSearchParams({
    tenantId: context.tenantId,
    userId: context.userId,
    query: query.value.trim(),
    mode: mode.value,
    topK: String(topK.value),
  })
  context.roles.forEach((role) => params.append('roles', role))
  try {
    preview.value = await fetchSearchPreview(params)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="knowledge-page">
    <section class="status-band">
      <div>
        <span>默认模式</span><strong>{{ status?.defaultMode || '-' }}</strong>
      </div>
      <div>
        <span>PGVector</span><strong>{{ status?.vectorStoreReady ? 'READY' : 'NOT READY' }}</strong>
      </div>
      <div>
        <span>向量表</span><strong class="mono">{{ status?.vectorTable || '-' }}</strong>
      </div>
      <el-button :icon="RefreshCw" @click="loadStatus">刷新</el-button>
    </section>
    <section class="search-band">
      <el-input
        v-model="query"
        placeholder="输入检索问题"
        clearable
        @keyup.enter="search"
      /><el-radio-group v-model="mode"
        ><el-radio-button value="keyword">关键词</el-radio-button
        ><el-radio-button value="hybrid">混合</el-radio-button
        ><el-radio-button value="hybrid_reranker">精排</el-radio-button></el-radio-group
      ><el-input-number v-model="topK" :min="1" :max="8" /><el-button
        type="primary"
        :icon="Search"
        @click="search"
        >检索</el-button
      >
    </section>
    <section class="result-panel" v-loading="loading">
      <header>
        <h2>检索结果</h2>
        <el-tag effect="plain">{{ preview?.hits.length || 0 }} 条</el-tag>
      </header>
      <div v-if="preview?.hits.length" class="hit-list">
        <article v-for="hit in preview.hits" :key="hit.chunkId">
          <div>
            <strong>{{ hit.title }}</strong
            ><span class="mono">{{ hit.docId }} / {{ hit.chunkId }}</span>
          </div>
          <div class="scores">
            <b>{{ hit.score.toFixed(3) }}</b
            ><small
              >向量 {{ hit.vectorScore.toFixed(2) }} · 关键词 {{ hit.keywordScore.toFixed(2) }} ·
              规则 {{ hit.ruleScore.toFixed(2) }}</small
            >
          </div>
          <p>{{ hit.excerpt }}</p>
        </article>
      </div>
      <div v-else class="empty">输入问题后查看真实检索结果</div>
    </section>
  </div>
</template>

<style scoped>
.knowledge-page {
  padding: 16px 20px 30px;
}
.status-band {
  display: grid;
  align-items: center;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
  grid-template-columns: 160px 160px minmax(240px, 1fr) auto;
  gap: 14px;
}
.status-band div {
  min-width: 0;
}
.status-band span,
.status-band strong {
  display: block;
}
.status-band span {
  color: var(--muted);
  font-size: 10px;
}
.status-band strong {
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}
.search-band {
  display: grid;
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
  grid-template-columns: minmax(260px, 1fr) auto auto auto;
  gap: 9px;
}
.result-panel {
  margin-top: 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
}
.result-panel header {
  display: flex;
  min-height: 56px;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  border-bottom: 1px solid var(--line);
}
.result-panel h2 {
  margin: 0;
  font-size: 13px;
}
.hit-list article {
  display: grid;
  padding: 13px 14px;
  border-bottom: 1px solid var(--line);
  grid-template-columns: minmax(0, 1fr) 190px;
  gap: 10px 20px;
}
.hit-list article:last-child {
  border-bottom: 0;
}
.hit-list strong,
.hit-list span,
.scores b,
.scores small {
  display: block;
}
.hit-list strong {
  font-size: 12px;
}
.hit-list span,
.scores small {
  margin-top: 4px;
  color: var(--muted);
  font-size: 9px;
}
.scores {
  text-align: right;
}
.scores b {
  color: var(--accent);
  font-size: 16px;
}
.hit-list p {
  margin: 0;
  color: #435160;
  font-size: 11px;
  line-height: 1.6;
  grid-column: 1 / -1;
}
.empty {
  padding: 70px 20px;
  color: var(--muted);
  text-align: center;
  font-size: 12px;
}
@media (max-width: 850px) {
  .status-band {
    grid-template-columns: 1fr 1fr;
  }
  .search-band {
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 650px) {
  .knowledge-page {
    padding: 10px;
  }
  .status-band,
  .search-band {
    grid-template-columns: 1fr;
  }
  .hit-list article {
    grid-template-columns: 1fr;
  }
  .scores {
    text-align: left;
  }
}
</style>
