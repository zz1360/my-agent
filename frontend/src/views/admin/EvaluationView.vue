<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshCw } from '@lucide/vue'
import { fetchEvalRuns, fetchEvalSuites } from '@/api/agent'
import { errorMessage } from '@/api/http'
import { useContextStore } from '@/stores/context'
import type { EvalRun, EvalSuite } from '@/types/api'

const context = useContextStore()
const suites = ref<EvalSuite[]>([])
const runs = ref<EvalRun[]>([])
const loading = ref(false)

onMounted(load)
async function load() {
  loading.value = true
  const params = new URLSearchParams({
    tenantId: context.tenantId,
    userId: context.userId,
    limit: '20',
    enabledOnly: 'false',
  })
  context.roles.forEach((role) => params.append('roles', role))
  try {
    ;[suites.value, runs.value] = await Promise.all([
      fetchEvalSuites(params),
      fetchEvalRuns(params),
    ])
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="eval-page" v-loading="loading">
    <section class="toolbar">
      <div><strong>评测资产</strong><span>评测集版本与最近运行</span></div>
      <el-button :icon="RefreshCw" @click="load">刷新</el-button>
    </section>
    <div class="eval-grid">
      <section class="data-panel">
        <header>
          <h2>评测集</h2>
          <el-tag effect="plain">{{ suites.length }} 个</el-tag>
        </header>
        <el-table :data="suites" stripe
          ><el-table-column prop="suiteId" label="评测集" min-width="200" /><el-table-column
            prop="suiteVersion"
            label="版本"
            width="100"
          /><el-table-column prop="caseCount" label="用例" width="80" /><el-table-column
            prop="enabled"
            label="启用"
            width="80"
            ><template #default="scope">{{
              scope.row.enabled ? '是' : '否'
            }}</template></el-table-column
          ></el-table
        >
      </section>
      <section class="data-panel">
        <header>
          <h2>最近运行</h2>
          <el-tag effect="plain">{{ runs.length }} 次</el-tag>
        </header>
        <el-table :data="runs" stripe
          ><el-table-column
            prop="runId"
            label="运行 ID"
            min-width="190"
            show-overflow-tooltip /><el-table-column
            prop="status"
            label="状态"
            width="110" /><el-table-column
            prop="passedCases"
            label="通过"
            width="70" /><el-table-column prop="failedCases" label="失败" width="70"
        /></el-table>
      </section>
    </div>
  </div>
</template>

<style scoped>
.eval-page {
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
.toolbar strong {
  font-size: 13px;
}
.toolbar span {
  margin-top: 3px;
  color: var(--muted);
  font-size: 11px;
}
.eval-grid {
  display: grid;
  margin-top: 12px;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.data-panel {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
}
.data-panel header {
  display: flex;
  min-height: 56px;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  border-bottom: 1px solid var(--line);
}
.data-panel h2 {
  margin: 0;
  font-size: 13px;
}
@media (max-width: 1050px) {
  .eval-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 650px) {
  .eval-page {
    padding: 10px;
  }
}
</style>
