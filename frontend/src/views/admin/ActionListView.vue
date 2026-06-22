<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Eye, Play, RefreshCw, RotateCcw, Search, X } from '@lucide/vue'
import {
  executeAction,
  fetchAction,
  fetchActionBusinessLink,
  fetchActionExecutions,
  fetchActions,
  retryExecution,
  reviewAction,
} from '@/api/agent'
import { errorMessage } from '@/api/http'
import { contextParams, contextPayload } from '@/utils/context'
import { formatTime } from '@/utils/format'
import type { ActionBusinessLink, ActionExecution, AgentAction } from '@/types/api'

const rows = ref<AgentAction[]>([])
const loading = ref(false)
const operating = ref(false)
const customerId = ref('')
const status = ref('')
const page = ref(1)
const pageSize = ref(10)
const drawerOpen = ref(false)
const selected = ref<AgentAction | null>(null)
const executions = ref<ActionExecution[]>([])
const businessLink = ref<ActionBusinessLink | null>(null)
const activeTab = ref('detail')

const pageRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return rows.value.slice(start, start + pageSize.value)
})

onMounted(load)

async function load() {
  loading.value = true
  const params = contextParams({ limit: '100' })
  if (customerId.value.trim()) params.set('customerId', customerId.value.trim())
  if (status.value) params.set('status', status.value)
  try {
    rows.value = await fetchActions(params)
    page.value = 1
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function openDetail(row: AgentAction) {
  drawerOpen.value = true
  activeTab.value = 'detail'
  operating.value = true
  try {
    const params = contextParams()
    const [action, actionExecutions, link] = await Promise.all([
      fetchAction(row.actionId, params),
      fetchActionExecutions(row.actionId, params),
      fetchActionBusinessLink(row.actionId, params),
    ])
    selected.value = action
    executions.value = actionExecutions
    businessLink.value = link
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    operating.value = false
  }
}

async function review(nextStatus: 'APPROVED' | 'REJECTED') {
  if (!selected.value) return
  const title = nextStatus === 'APPROVED' ? '批准动作' : '驳回动作'
  try {
    const { value } = await ElMessageBox.prompt('请输入复核意见', title, {
      confirmButtonText: title,
      cancelButtonText: '取消',
      inputPlaceholder: '记录判断依据',
      inputValidator: (input) => Boolean(input.trim()) || '复核意见不能为空',
      type: nextStatus === 'APPROVED' ? 'warning' : 'error',
    })
    operating.value = true
    selected.value = await reviewAction(
      selected.value.actionId,
      contextPayload({ status: nextStatus, comment: value.trim() }),
    )
    ElMessage.success(`动作已${nextStatus === 'APPROVED' ? '批准' : '驳回'}`)
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error))
  } finally {
    operating.value = false
  }
}

async function execute() {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm(
      `即将执行“${selected.value.title}”，动作会写入目标业务系统。`,
      '确认执行动作',
      { confirmButtonText: '确认执行', cancelButtonText: '取消', type: 'warning' },
    )
    operating.value = true
    const execution = await executeAction(
      selected.value.actionId,
      contextPayload({
        force: true,
        comment: '运营管理台人工确认执行',
        idempotencyKey: `web-${selected.value.actionId}-${crypto.randomUUID()}`,
        simulateFailure: false,
      }),
    )
    ElMessage.success(`执行已提交：${execution.status}`)
    await openDetail(selected.value)
    await load()
    activeTab.value = 'executions'
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error))
  } finally {
    operating.value = false
  }
}

async function retry(row: ActionExecution) {
  try {
    await ElMessageBox.confirm(`确认重试执行 ${row.executionId}？`, '失败重试', {
      confirmButtonText: '确认重试',
      cancelButtonText: '取消',
      type: 'warning',
    })
    operating.value = true
    await retryExecution(
      row.executionId,
      contextPayload({ force: true, comment: '运营管理台人工重试', simulateFailure: false }),
    )
    ElMessage.success('重试已提交')
    if (selected.value) await openDetail(selected.value)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error))
  } finally {
    operating.value = false
  }
}

function tagType(value: string) {
  if (['APPROVED', 'APPLIED', 'SUCCESS', 'SUCCEEDED'].includes(value)) return 'success'
  if (['REJECTED', 'FAILED'].includes(value)) return 'danger'
  return 'warning'
}
</script>

<template>
  <div class="page-content">
    <section class="filter-band">
      <el-input v-model="customerId" placeholder="客户编号" clearable @keyup.enter="load" />
      <el-select v-model="status" placeholder="全部状态" clearable>
        <el-option label="待复核" value="PENDING_REVIEW" />
        <el-option label="已批准" value="APPROVED" />
        <el-option label="已驳回" value="REJECTED" />
        <el-option label="已执行" value="APPLIED" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="load">查询</el-button>
      <el-button :icon="RefreshCw" @click="load">刷新</el-button>
    </section>

    <section class="table-section">
      <div class="table-title">
        <div>
          <h2>动作草稿</h2>
          <p>人工复核、执行与失败重试</p>
        </div>
        <el-tag effect="plain">{{ rows.length }} 条</el-tag>
      </div>
      <el-table v-loading="loading" :data="pageRows" stripe empty-text="暂无动作草稿">
        <el-table-column prop="actionId" label="动作 ID" min-width="180" show-overflow-tooltip />
        <el-table-column prop="customerId" label="客户" width="100" />
        <el-table-column prop="actionType" label="类型" min-width="160" />
        <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
        <el-table-column prop="riskLevel" label="风险" width="80" />
        <el-table-column label="状态" width="130">
          <template #default="scope">
            <el-tag :type="tagType(scope.row.status)" effect="plain">{{ scope.row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="scope">
            <el-button link type="primary" :icon="Eye" @click="openDetail(scope.row)"
              >详情</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-row">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="rows.length"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </section>

    <el-drawer v-model="drawerOpen" title="动作工作台" size="min(720px, 94vw)">
      <div v-loading="operating" class="drawer-body">
        <template v-if="selected">
          <div class="action-head">
            <div>
              <strong>{{ selected.title }}</strong
              ><span>{{ selected.actionId }}</span>
            </div>
            <el-tag :type="tagType(selected.status)" effect="plain">{{ selected.status }}</el-tag>
          </div>
          <el-tabs v-model="activeTab">
            <el-tab-pane label="动作详情" name="detail">
              <el-descriptions :column="2" border>
                <el-descriptions-item label="客户">{{ selected.customerId }}</el-descriptions-item>
                <el-descriptions-item label="运单">{{
                  selected.waybillId || '-'
                }}</el-descriptions-item>
                <el-descriptions-item label="类型">{{ selected.actionType }}</el-descriptions-item>
                <el-descriptions-item label="风险">{{ selected.riskLevel }}</el-descriptions-item>
                <el-descriptions-item label="创建人">{{
                  selected.createdBy || '-'
                }}</el-descriptions-item>
                <el-descriptions-item label="创建时间">{{
                  formatTime(selected.createdAt)
                }}</el-descriptions-item>
              </el-descriptions>
              <div class="content-block">
                <h3>动作内容</h3>
                <pre>{{ selected.draftContent || '-' }}</pre>
              </div>
              <div v-if="selected.reviewComment" class="content-block">
                <h3>复核意见</h3>
                <p>{{ selected.reviewComment }}</p>
              </div>
            </el-tab-pane>
            <el-tab-pane :label="`执行记录 ${executions.length}`" name="executions">
              <el-table :data="executions" stripe empty-text="暂无执行记录">
                <el-table-column prop="executionId" label="执行 ID" min-width="170" />
                <el-table-column prop="targetSystem" label="目标系统" min-width="120" />
                <el-table-column label="状态" width="100">
                  <template #default="scope">
                    <el-tag :type="tagType(scope.row.status)" effect="plain">{{
                      scope.row.status
                    }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="重试" width="80">
                  <template #default="scope"
                    >{{ scope.row.retryCount }}/{{ scope.row.maxRetryCount }}</template
                  >
                </el-table-column>
                <el-table-column label="操作" width="90">
                  <template #default="scope">
                    <el-button
                      v-if="
                        scope.row.status === 'FAILED' &&
                        scope.row.retryCount < scope.row.maxRetryCount
                      "
                      link
                      type="primary"
                      :icon="RotateCcw"
                      @click="retry(scope.row)"
                      >重试</el-button
                    >
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="业务回链" name="business">
              <el-descriptions :column="1" border>
                <el-descriptions-item label="业务表">{{
                  businessLink?.businessTable || '-'
                }}</el-descriptions-item>
                <el-descriptions-item label="业务 ID">{{
                  businessLink?.businessId || '-'
                }}</el-descriptions-item>
                <el-descriptions-item label="最新执行">{{
                  businessLink?.latestExecutionId || '-'
                }}</el-descriptions-item>
                <el-descriptions-item label="审计 Trace">{{
                  businessLink?.traceId || selected.traceId
                }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>
          </el-tabs>
        </template>
      </div>
      <template #footer>
        <el-button @click="drawerOpen = false">关闭</el-button>
        <el-button
          v-if="selected?.status === 'PENDING_REVIEW'"
          :icon="X"
          type="danger"
          plain
          @click="review('REJECTED')"
          >驳回</el-button
        >
        <el-button
          v-if="selected?.status === 'PENDING_REVIEW'"
          :icon="Check"
          type="success"
          @click="review('APPROVED')"
          >批准</el-button
        >
        <el-button
          v-if="selected?.status === 'APPROVED'"
          :icon="Play"
          type="primary"
          @click="execute"
          >执行</el-button
        >
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-content {
  padding: 16px 20px 30px;
}
.filter-band {
  display: grid;
  grid-template-columns: 200px 180px auto auto 1fr;
  gap: 9px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
}
.table-section {
  margin-top: 12px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
}
.table-title,
.action-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.table-title {
  min-height: 60px;
  padding: 0 14px;
  border-bottom: 1px solid var(--line);
}
.table-title h2,
.content-block h3 {
  margin: 0;
  font-size: 13px;
}
.table-title p {
  margin: 3px 0 0;
  color: var(--muted);
  font-size: 11px;
}
.pagination-row {
  display: flex;
  justify-content: flex-end;
  padding: 11px 14px;
  border-top: 1px solid var(--line);
}
.drawer-body {
  min-height: 320px;
}
.action-head {
  padding-bottom: 12px;
}
.action-head div {
  display: flex;
  min-width: 0;
  flex-direction: column;
}
.action-head strong {
  font-size: 14px;
}
.action-head span {
  margin-top: 4px;
  color: var(--muted);
  font-family: var(--mono);
  font-size: 10px;
}
.content-block {
  margin-top: 14px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface-soft);
}
.content-block pre,
.content-block p {
  margin: 8px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.65;
}
@media (max-width: 700px) {
  .page-content {
    padding: 10px;
  }
  .filter-band {
    grid-template-columns: 1fr 1fr;
  }
  .pagination-row {
    overflow-x: auto;
    justify-content: flex-start;
  }
}
</style>
