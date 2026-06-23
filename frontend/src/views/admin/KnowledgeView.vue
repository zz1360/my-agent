<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useQuery } from '@tanstack/vue-query'
import { FilePlus2, Pencil, RefreshCw, Search, UploadCloud } from '@lucide/vue'
import {
  changeKnowledgeDocumentStatus,
  fetchKnowledgeDocumentsPage,
  fetchKnowledgeIndexJobs,
  fetchRetrievalStatus,
  fetchSearchPreview,
  reindexKnowledge,
  saveKnowledgeDocument,
} from '@/api/agent'
import { errorMessage } from '@/api/http'
import { contextParams, contextPayload } from '@/utils/context'
import { formatTime } from '@/utils/format'
import type {
  KnowledgeDocument,
  KnowledgeIndexJob,
  RetrievalStatus,
  SearchPreview,
} from '@/types/api'
import ServerPagination from '@/components/ServerPagination.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useConfirmAction } from '@/composables/useConfirmAction'

const preview = ref<SearchPreview | null>(null)
const activeTab = ref('documents')
const docStatus = ref('')
const bizDomain = ref('')
const appliedDocStatus = ref('')
const appliedBizDomain = ref('')
const page = ref(1)
const pageSize = ref(20)
const query = ref('冷链运输超温后应该怎么处理？')
const mode = ref('hybrid_reranker')
const topK = ref(5)
const operationLoading = ref(false)
const saving = ref(false)
const drawerOpen = ref(false)
const { confirm } = useConfirmAction()

const retrievalQuery = useQuery({ queryKey: ['retrieval-status'], queryFn: fetchRetrievalStatus })
const jobsQuery = useQuery({
  queryKey: ['knowledge-index-jobs'],
  queryFn: () => fetchKnowledgeIndexJobs(contextParams({ limit: '30' })),
})
const documentsQuery = useQuery({
  queryKey: computed(() => [
    'knowledge-documents',
    appliedDocStatus.value,
    appliedBizDomain.value,
    page.value,
    pageSize.value,
  ]),
  queryFn: () => {
    const params = contextParams({ page: String(page.value), size: String(pageSize.value) })
    if (appliedDocStatus.value) params.set('status', appliedDocStatus.value)
    if (appliedBizDomain.value) params.set('bizDomain', appliedBizDomain.value)
    return fetchKnowledgeDocumentsPage(params)
  },
})
const retrieval = computed<RetrievalStatus | null>(() => retrievalQuery.data.value || null)
const documents = computed<KnowledgeDocument[]>(() => documentsQuery.data.value?.items || [])
const total = computed(() => documentsQuery.data.value?.total || 0)
const jobs = computed<KnowledgeIndexJob[]>(() => jobsQuery.data.value || [])
const loading = computed(
  () =>
    operationLoading.value ||
    retrievalQuery.isFetching.value ||
    jobsQuery.isFetching.value ||
    documentsQuery.isFetching.value,
)

watch(
  () => [retrievalQuery.error.value, jobsQuery.error.value, documentsQuery.error.value],
  (errors) => errors.find(Boolean) && ElMessage.error(errorMessage(errors.find(Boolean))),
)

const emptyForm = () => ({
  baseDocId: '',
  docId: '',
  title: '',
  docType: 'POLICY',
  bizDomain: 'LOGISTICS',
  version: 'v1.0',
  status: 'DRAFT',
  sourceUrl: '',
  aclRoles: ['CUSTOMER_SERVICE', 'OPERATIONS', 'OPS_MANAGER'],
  effectiveFrom: '',
  effectiveTo: '',
  content: '',
  autoIndex: true,
})
const form = reactive(emptyForm())

async function loadAll() {
  appliedDocStatus.value = docStatus.value
  appliedBizDomain.value = bizDomain.value.trim()
  page.value = 1
  await Promise.all([retrievalQuery.refetch(), documentsQuery.refetch(), jobsQuery.refetch()])
}

function changePage(nextPage: number, nextSize: number) {
  page.value = nextPage
  pageSize.value = nextSize
}

function openCreate() {
  Object.assign(form, emptyForm())
  drawerOpen.value = true
}

function openEdit(row: KnowledgeDocument) {
  Object.assign(form, {
    baseDocId: row.baseDocId,
    docId: row.docId,
    title: row.title,
    docType: row.docType,
    bizDomain: row.bizDomain,
    version: row.version || '',
    status: row.status,
    sourceUrl: row.sourceUrl || '',
    aclRoles: row.aclRoles?.split(',').filter(Boolean) || [],
    effectiveFrom: row.effectiveFrom || '',
    effectiveTo: row.effectiveTo || '',
    content: row.content,
    autoIndex: true,
  })
  drawerOpen.value = true
}

async function save() {
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.warning('标题和正文不能为空')
    return
  }
  try {
    if (form.status === 'ACTIVE') {
      await confirm('保存后将立即替换生效内容并触发重新索引。', '更新生效文档')
    }
    saving.value = true
    await saveKnowledgeDocument(
      contextPayload({
        ...form,
        effectiveFrom: form.effectiveFrom || null,
        effectiveTo: form.effectiveTo || null,
      }),
    )
    ElMessage.success('知识文档已保存')
    drawerOpen.value = false
    await loadAll()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function changeStatus(row: KnowledgeDocument, operation: 'publish' | 'disable' | 'expire') {
  const labels = { publish: '发布', disable: '停用', expire: '标记到期' }
  try {
    await confirm(`确认${labels[operation]}“${row.title}”？`, `${labels[operation]}知识文档`)
    operationLoading.value = true
    await changeKnowledgeDocumentStatus(row.docId, operation, contextParams())
    ElMessage.success(`文档已${labels[operation]}`)
    await loadAll()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error))
  } finally {
    operationLoading.value = false
  }
}

async function reindex() {
  try {
    await confirm('将为当前租户重建知识索引。', '重建知识索引')
    operationLoading.value = true
    const result = await reindexKnowledge(contextParams())
    ElMessage.success(`索引任务已创建：${result.jobId}`)
    activeTab.value = 'jobs'
    await loadAll()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error))
  } finally {
    operationLoading.value = false
  }
}

async function search() {
  if (!query.value.trim()) return
  operationLoading.value = true
  try {
    preview.value = await fetchSearchPreview(
      contextParams({ query: query.value.trim(), mode: mode.value, topK: String(topK.value) }),
    )
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    operationLoading.value = false
  }
}
</script>

<template>
  <div class="knowledge-page" v-loading="loading">
    <section class="status-band">
      <div>
        <span>默认模式</span><strong>{{ retrieval?.defaultMode || '-' }}</strong>
      </div>
      <div>
        <span>PGVector</span
        ><strong>{{ retrieval?.vectorStoreReady ? 'READY' : 'NOT READY' }}</strong>
      </div>
      <div>
        <span>向量表</span><strong class="mono">{{ retrieval?.vectorTable || '-' }}</strong>
      </div>
      <el-button :icon="RefreshCw" @click="loadAll">刷新</el-button>
    </section>

    <section class="workspace">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="知识文档" name="documents">
          <div class="tab-toolbar">
            <div class="filters">
              <el-select v-model="docStatus" placeholder="全部状态" clearable @change="loadAll">
                <el-option label="草稿" value="DRAFT" /><el-option label="生效" value="ACTIVE" />
                <el-option label="停用" value="DISABLED" /><el-option
                  label="到期"
                  value="EXPIRED"
                />
              </el-select>
              <el-input v-model="bizDomain" placeholder="业务域" clearable @keyup.enter="loadAll" />
            </div>
            <div>
              <el-button :icon="UploadCloud" @click="reindex">重建索引</el-button>
              <el-button type="primary" :icon="FilePlus2" @click="openCreate">新建文档</el-button>
            </div>
          </div>
          <el-table :data="documents" stripe empty-text="暂无知识文档">
            <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
            <el-table-column prop="bizDomain" label="业务域" width="130" />
            <el-table-column prop="version" label="版本" width="90" />
            <el-table-column prop="chunkCount" label="分块" width="70" />
            <el-table-column label="状态" width="100">
              <template #default="scope"><StatusTag :value="scope.row.status" /></template>
            </el-table-column>
            <el-table-column label="更新时间" min-width="160">
              <template #default="scope">{{ formatTime(scope.row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="250" fixed="right">
              <template #default="scope">
                <el-button link type="primary" :icon="Pencil" @click="openEdit(scope.row)"
                  >编辑</el-button
                >
                <el-button
                  v-if="scope.row.status === 'DRAFT'"
                  link
                  type="success"
                  @click="changeStatus(scope.row, 'publish')"
                  >发布</el-button
                >
                <el-button
                  v-if="scope.row.status === 'ACTIVE'"
                  link
                  type="danger"
                  @click="changeStatus(scope.row, 'disable')"
                  >停用</el-button
                >
                <el-button
                  v-if="scope.row.status === 'ACTIVE'"
                  link
                  type="warning"
                  @click="changeStatus(scope.row, 'expire')"
                  >到期</el-button
                >
              </template>
            </el-table-column>
          </el-table>
          <ServerPagination :page="page" :size="pageSize" :total="total" @change="changePage" />
        </el-tab-pane>

        <el-tab-pane label="检索预览" name="search">
          <div class="search-band">
            <el-input v-model="query" placeholder="输入检索问题" clearable @keyup.enter="search" />
            <el-radio-group v-model="mode">
              <el-radio-button value="keyword">关键词</el-radio-button>
              <el-radio-button value="hybrid">混合</el-radio-button>
              <el-radio-button value="hybrid_reranker">精排</el-radio-button>
            </el-radio-group>
            <el-input-number v-model="topK" :min="1" :max="8" />
            <el-button type="primary" :icon="Search" @click="search">检索</el-button>
          </div>
          <div v-if="preview?.hits.length" class="hit-list">
            <article v-for="hit in preview.hits" :key="hit.chunkId">
              <div>
                <strong>{{ hit.title }}</strong
                ><span class="mono">{{ hit.docId }} / {{ hit.chunkId }}</span>
              </div>
              <div class="scores">
                <b>{{ hit.score.toFixed(3) }}</b
                ><small
                  >向量 {{ hit.vectorScore.toFixed(2) }} · 关键词
                  {{ hit.keywordScore.toFixed(2) }} · 规则 {{ hit.ruleScore.toFixed(2) }}</small
                >
              </div>
              <p>{{ hit.excerpt }}</p>
            </article>
          </div>
          <el-empty v-else description="输入问题后查看检索结果" :image-size="72" />
        </el-tab-pane>

        <el-tab-pane :label="`索引任务 ${jobs.length}`" name="jobs">
          <el-table :data="jobs" stripe empty-text="暂无索引任务">
            <el-table-column prop="jobId" label="任务 ID" min-width="190" />
            <el-table-column prop="triggerType" label="触发方式" width="150" />
            <el-table-column prop="documentId" label="文档" min-width="170" />
            <el-table-column prop="chunkCount" label="分块" width="80" />
            <el-table-column label="状态" width="110"
              ><template #default="scope"><StatusTag :value="scope.row.status" /></template
            ></el-table-column>
            <el-table-column label="创建时间" min-width="170"
              ><template #default="scope">{{
                formatTime(scope.row.createdAt)
              }}</template></el-table-column
            >
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-drawer v-model="drawerOpen" title="知识文档" size="min(680px, 94vw)">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="标题" class="wide"><el-input v-model="form.title" /></el-form-item>
          <el-form-item label="基础文档 ID"
            ><el-input v-model="form.baseDocId" placeholder="自动生成"
          /></el-form-item>
          <el-form-item label="文档 ID"
            ><el-input v-model="form.docId" placeholder="自动生成"
          /></el-form-item>
          <el-form-item label="类型"
            ><el-select v-model="form.docType"
              ><el-option label="制度" value="POLICY" /><el-option
                label="SOP"
                value="SOP" /><el-option label="FAQ" value="FAQ" /><el-option
                label="规则"
                value="RULE" /></el-select
          ></el-form-item>
          <el-form-item label="业务域"><el-input v-model="form.bizDomain" /></el-form-item>
          <el-form-item label="版本"><el-input v-model="form.version" /></el-form-item>
          <el-form-item label="状态"
            ><el-select v-model="form.status"
              ><el-option label="草稿" value="DRAFT" /><el-option
                label="生效"
                value="ACTIVE" /></el-select
          ></el-form-item>
          <el-form-item label="生效日期"
            ><el-date-picker v-model="form.effectiveFrom" type="date" value-format="YYYY-MM-DD"
          /></el-form-item>
          <el-form-item label="到期日期"
            ><el-date-picker v-model="form.effectiveTo" type="date" value-format="YYYY-MM-DD"
          /></el-form-item>
          <el-form-item label="可见角色" class="wide"
            ><el-select v-model="form.aclRoles" multiple
              ><el-option label="客服" value="CUSTOMER_SERVICE" /><el-option
                label="运营"
                value="OPERATIONS" /><el-option label="运营经理" value="OPS_MANAGER" /><el-option
                label="管理员"
                value="ADMIN" /></el-select
          ></el-form-item>
          <el-form-item label="来源 URL" class="wide"
            ><el-input v-model="form.sourceUrl"
          /></el-form-item>
          <el-form-item label="正文" class="wide"
            ><el-input v-model="form.content" type="textarea" :rows="14"
          /></el-form-item>
        </div>
      </el-form>
      <template #footer
        ><el-button @click="drawerOpen = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save">保存文档</el-button></template
      >
    </el-drawer>
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
.workspace {
  margin-top: 12px;
  padding: 0 14px 14px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
}
.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 12px;
}
.filters {
  display: grid;
  grid-template-columns: 160px 180px;
  gap: 8px;
}
.search-band {
  display: grid;
  padding-bottom: 12px;
  grid-template-columns: minmax(260px, 1fr) auto auto auto;
  gap: 9px;
}
.hit-list {
  border: 1px solid var(--line);
  border-radius: 6px;
}
.hit-list article {
  display: grid;
  padding: 13px 14px;
  border-bottom: 1px solid var(--line);
  grid-template-columns: minmax(0, 1fr) 210px;
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
.hit-list p {
  margin: 0;
  color: #435160;
  font-size: 11px;
  line-height: 1.6;
  grid-column: 1 / -1;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 12px;
}
.form-grid .wide {
  grid-column: 1 / -1;
}
.form-grid :deep(.el-select),
.form-grid :deep(.el-date-editor) {
  width: 100%;
}
@media (max-width: 760px) {
  .knowledge-page {
    padding: 10px;
  }
  .status-band {
    grid-template-columns: 1fr 1fr;
  }
  .tab-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .filters,
  .search-band,
  .form-grid {
    grid-template-columns: 1fr;
  }
  .form-grid .wide {
    grid-column: auto;
  }
  .hit-list article {
    grid-template-columns: 1fr;
  }
  .scores {
    text-align: left;
  }
}
</style>
