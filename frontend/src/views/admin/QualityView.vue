<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useQuery } from '@tanstack/vue-query'
import { ClipboardPlus, Play, RefreshCw } from '@lucide/vue'
import {
  createQualityTask,
  evaluateQualityAlerts,
  fetchQualityAlertsPage,
  fetchQualityMetrics,
  fetchQualityTasksPage,
  transitionQualityTask,
} from '@/api/agent'
import { errorMessage } from '@/api/http'
import { contextParams, contextPayload } from '@/utils/context'
import { formatTime, percent } from '@/utils/format'
import type { QualityAlert, QualityMetrics, QualityTask } from '@/types/api'
import ServerPagination from '@/components/ServerPagination.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useConfirmAction } from '@/composables/useConfirmAction'

const operationLoading = ref(false)
const activeTab = ref('alerts')
const alertStatus = ref('OPEN')
const taskStatus = ref('')
const appliedAlertStatus = ref('OPEN')
const appliedTaskStatus = ref('')
const alertPage = ref(1)
const taskPage = ref(1)
const pageSize = ref(20)
const taskDialog = ref(false)
const selectedTask = ref<QualityTask | null>(null)
const taskForm = reactive({ status: 'PROCESSING', ownerUserId: '', comment: '' })
const { confirm } = useConfirmAction()

const metricsQuery = useQuery({
  queryKey: ['quality-metrics'],
  queryFn: () => fetchQualityMetrics(contextParams()),
})
const alertsQuery = useQuery({
  queryKey: computed(() => [
    'quality-alerts',
    appliedAlertStatus.value,
    alertPage.value,
    pageSize.value,
  ]),
  queryFn: () => {
    const params = contextParams({ page: String(alertPage.value), size: String(pageSize.value) })
    if (appliedAlertStatus.value) params.set('status', appliedAlertStatus.value)
    return fetchQualityAlertsPage(params)
  },
})
const tasksQuery = useQuery({
  queryKey: computed(() => [
    'quality-tasks',
    appliedTaskStatus.value,
    taskPage.value,
    pageSize.value,
  ]),
  queryFn: () => {
    const params = contextParams({ page: String(taskPage.value), size: String(pageSize.value) })
    if (appliedTaskStatus.value) params.set('status', appliedTaskStatus.value)
    return fetchQualityTasksPage(params)
  },
})
const metrics = computed<QualityMetrics | null>(() => metricsQuery.data.value || null)
const alerts = computed<QualityAlert[]>(() => alertsQuery.data.value?.items || [])
const tasks = computed<QualityTask[]>(() => tasksQuery.data.value?.items || [])
const alertTotal = computed(() => alertsQuery.data.value?.total || 0)
const taskTotal = computed(() => tasksQuery.data.value?.total || 0)
const loading = computed(
  () =>
    operationLoading.value ||
    metricsQuery.isFetching.value ||
    alertsQuery.isFetching.value ||
    tasksQuery.isFetching.value,
)

watch(
  () => [metricsQuery.error.value, alertsQuery.error.value, tasksQuery.error.value],
  (errors) => errors.find(Boolean) && ElMessage.error(errorMessage(errors.find(Boolean))),
)

const cards = computed(() => [
  ['负反馈', metrics.value?.notHelpfulFeedback || 0, '条'],
  ['评测候选', metrics.value?.candidateCount || 0, '条'],
  ['已通过候选', metrics.value?.approvedCandidates || 0, '条'],
  ['候选转化率', percent(metrics.value?.candidateConversionRate), ''],
  ['RAG 实验通过率', percent(metrics.value?.ragExperimentPassRate), ''],
])

async function load() {
  appliedAlertStatus.value = alertStatus.value
  appliedTaskStatus.value = taskStatus.value
  alertPage.value = 1
  taskPage.value = 1
  await Promise.all([metricsQuery.refetch(), alertsQuery.refetch(), tasksQuery.refetch()])
}

function changeAlertPage(page: number, size: number) {
  alertPage.value = page
  pageSize.value = size
}

function changeTaskPage(page: number, size: number) {
  taskPage.value = page
  pageSize.value = size
}

async function evaluate() {
  try {
    await confirm('将按当前规则重新计算质量告警。', '运行告警评估')
    operationLoading.value = true
    const result = await evaluateQualityAlerts(contextParams())
    ElMessage.success(`评估完成，当前开放告警 ${result.openAlerts} 条`)
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error))
  } finally {
    operationLoading.value = false
  }
}

async function createTask(alert: QualityAlert) {
  try {
    await confirm(`从告警“${alert.summary}”创建治理任务？`, '创建治理任务')
    operationLoading.value = true
    const result = await createQualityTask(alert.alertId, contextParams())
    ElMessage.success(`任务已创建：${result.taskId}`)
    activeTab.value = 'tasks'
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error))
  } finally {
    operationLoading.value = false
  }
}

function openTask(task: QualityTask) {
  selectedTask.value = task
  taskForm.status = task.status === 'OPEN' ? 'PROCESSING' : task.status
  taskForm.ownerUserId = task.ownerUserId || ''
  taskForm.comment = task.lastComment || ''
  taskDialog.value = true
}

async function transitionTask() {
  if (!selectedTask.value || !taskForm.comment.trim()) {
    ElMessage.warning('处理意见不能为空')
    return
  }
  operationLoading.value = true
  try {
    await transitionQualityTask(
      selectedTask.value.taskId,
      contextPayload({
        status: taskForm.status,
        ownerUserId: taskForm.ownerUserId.trim(),
        comment: taskForm.comment.trim(),
      }),
    )
    ElMessage.success('任务状态已更新')
    taskDialog.value = false
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    operationLoading.value = false
  }
}

function severityType(value: string) {
  if (value === 'CRITICAL' || value === 'HIGH') return 'danger'
  if (value === 'MEDIUM') return 'warning'
  return 'info'
}
</script>

<template>
  <div class="quality-page" v-loading="loading">
    <section class="toolbar">
      <div><strong>质量治理</strong><span>反馈、告警与治理任务</span></div>
      <div>
        <el-button :icon="RefreshCw" @click="load">刷新</el-button
        ><el-button type="primary" :icon="Play" @click="evaluate">运行评估</el-button>
      </div>
    </section>
    <section class="metric-grid">
      <div v-for="card in cards" :key="String(card[0])">
        <span>{{ card[0] }}</span
        ><strong
          >{{ card[1] }}<small>{{ card[2] }}</small></strong
        >
      </div>
    </section>

    <section class="governance-panel">
      <el-tabs v-model="activeTab">
        <el-tab-pane :label="`质量告警 ${alertTotal}`" name="alerts">
          <div class="filter-row">
            <el-select v-model="alertStatus" placeholder="全部状态" clearable @change="load"
              ><el-option label="开放" value="OPEN" /><el-option label="已解决" value="RESOLVED"
            /></el-select>
          </div>
          <el-table :data="alerts" stripe empty-text="暂无质量告警">
            <el-table-column prop="summary" label="告警" min-width="280" show-overflow-tooltip />
            <el-table-column prop="metricType" label="指标" min-width="150" />
            <el-table-column label="当前/阈值" width="130"
              ><template #default="scope"
                >{{ scope.row.metricValue }} / {{ scope.row.thresholdValue }}</template
              ></el-table-column
            >
            <el-table-column label="级别" width="90"
              ><template #default="scope"
                ><el-tag :type="severityType(scope.row.severity)" effect="plain">{{
                  scope.row.severity
                }}</el-tag></template
              ></el-table-column
            >
            <el-table-column label="状态" width="100"
              ><template #default="scope"><StatusTag :value="scope.row.status" /></template
            ></el-table-column>
            <el-table-column label="最近触发" min-width="170"
              ><template #default="scope">{{
                formatTime(scope.row.lastTriggeredAt)
              }}</template></el-table-column
            >
            <el-table-column label="操作" width="120" fixed="right"
              ><template #default="scope"
                ><el-button
                  v-if="!scope.row.taskId && scope.row.status === 'OPEN'"
                  link
                  type="primary"
                  :icon="ClipboardPlus"
                  @click="createTask(scope.row)"
                  >建任务</el-button
                ><span v-else class="muted">{{ scope.row.taskId || '-' }}</span></template
              ></el-table-column
            >
          </el-table>
          <ServerPagination
            :page="alertPage"
            :size="pageSize"
            :total="alertTotal"
            @change="changeAlertPage"
          />
        </el-tab-pane>

        <el-tab-pane :label="`治理任务 ${taskTotal}`" name="tasks">
          <div class="filter-row">
            <el-select v-model="taskStatus" placeholder="全部状态" clearable @change="load"
              ><el-option label="待处理" value="OPEN" /><el-option
                label="处理中"
                value="PROCESSING" /><el-option label="已解决" value="RESOLVED" /><el-option
                label="已驳回"
                value="REJECTED"
            /></el-select>
          </div>
          <el-table :data="tasks" stripe empty-text="暂无治理任务">
            <el-table-column prop="taskId" label="任务 ID" min-width="170" />
            <el-table-column prop="title" label="任务" min-width="260" show-overflow-tooltip />
            <el-table-column prop="ownerRole" label="负责角色" width="130" />
            <el-table-column prop="ownerUserId" label="负责人" width="130" />
            <el-table-column label="状态" width="110"
              ><template #default="scope"><StatusTag :value="scope.row.status" /></template
            ></el-table-column>
            <el-table-column label="更新时间" min-width="170"
              ><template #default="scope">{{
                formatTime(scope.row.updatedAt)
              }}</template></el-table-column
            >
            <el-table-column label="操作" width="100" fixed="right"
              ><template #default="scope"
                ><el-button link type="primary" @click="openTask(scope.row)"
                  >处理</el-button
                ></template
              ></el-table-column
            >
          </el-table>
          <ServerPagination
            :page="taskPage"
            :size="pageSize"
            :total="taskTotal"
            @change="changeTaskPage"
          />
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="taskDialog" title="处理治理任务" width="min(520px, 92vw)">
      <el-form label-position="top">
        <el-form-item label="任务"
          ><el-input :model-value="selectedTask?.title" disabled
        /></el-form-item>
        <el-form-item label="负责人"
          ><el-input v-model="taskForm.ownerUserId" placeholder="用户 ID"
        /></el-form-item>
        <el-form-item label="状态"
          ><el-select v-model="taskForm.status" style="width: 100%"
            ><el-option label="待处理" value="OPEN" /><el-option
              label="处理中"
              value="PROCESSING" /><el-option label="已解决" value="RESOLVED" /><el-option
              label="已驳回"
              value="REJECTED" /></el-select
        ></el-form-item>
        <el-form-item label="处理意见"
          ><el-input v-model="taskForm.comment" type="textarea" :rows="4"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="taskDialog = false">取消</el-button
        ><el-button type="primary" @click="transitionTask">保存</el-button></template
      >
    </el-dialog>
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
.toolbar > div:first-child {
  display: flex;
  flex-direction: column;
}
.toolbar strong {
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
.governance-panel {
  margin-top: 12px;
  padding: 0 14px 14px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
}
.filter-row {
  display: flex;
  width: 180px;
  padding-bottom: 10px;
}
.filter-row :deep(.el-select) {
  width: 100%;
}
.muted {
  color: var(--muted);
  font-size: 10px;
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
  .toolbar {
    align-items: stretch;
    flex-direction: column;
    gap: 10px;
    padding: 12px;
  }
}
</style>
