<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshCw, Search } from '@lucide/vue'
import { fetchActions } from '@/api/agent'
import { errorMessage } from '@/api/http'
import { useContextStore } from '@/stores/context'
import type { AgentAction } from '@/types/api'

const context = useContextStore()
const rows = ref<AgentAction[]>([])
const loading = ref(false)
const customerId = ref('')
const status = ref('')

onMounted(load)

async function load() {
  loading.value = true
  const params = new URLSearchParams({
    tenantId: context.tenantId,
    userId: context.userId,
    limit: '50',
  })
  context.roles.forEach((role) => params.append('roles', role))
  if (customerId.value.trim()) params.set('customerId', customerId.value.trim())
  if (status.value) params.set('status', status.value)
  try {
    rows.value = await fetchActions(params)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function tagType(value: string) {
  if (['APPROVED', 'EXECUTED', 'SUCCEEDED'].includes(value)) return 'success'
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
        <el-option label="已执行" value="EXECUTED" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="load">查询</el-button>
      <el-button :icon="RefreshCw" @click="load">刷新</el-button>
    </section>

    <section class="table-section">
      <div class="table-title">
        <div>
          <h2>动作草稿</h2>
          <p>诊断结果生成并等待人工复核的动作</p>
        </div>
        <el-tag effect="plain">{{ rows.length }} 条</el-tag>
      </div>
      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="actionId" label="动作 ID" min-width="180" show-overflow-tooltip />
        <el-table-column prop="customerId" label="客户" width="100" />
        <el-table-column prop="actionType" label="类型" min-width="160" />
        <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
        <el-table-column prop="riskLevel" label="风险" width="80" />
        <el-table-column label="状态" width="120">
          <template #default="scope"
            ><el-tag :type="tagType(scope.row.status)" effect="plain">{{
              scope.row.status
            }}</el-tag></template
          >
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="170" />
      </el-table>
    </section>
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
.table-title {
  display: flex;
  min-height: 60px;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  border-bottom: 1px solid var(--line);
}
.table-title h2 {
  margin: 0;
  font-size: 13px;
}
.table-title p {
  margin: 3px 0 0;
  color: var(--muted);
  font-size: 11px;
}
@media (max-width: 700px) {
  .page-content {
    padding: 10px;
  }
  .filter-band {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
