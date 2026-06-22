<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GitCompare, Play, RefreshCw, ShieldCheck } from '@lucide/vue'
import {
  compareEvalRuns,
  fetchEvalRuns,
  fetchEvalSuites,
  fetchReleaseGates,
  runEvalSuite,
  runReleaseGate,
} from '@/api/agent'
import { errorMessage } from '@/api/http'
import { useContextStore } from '@/stores/context'
import { formatTime, percent } from '@/utils/format'
import type { EvalReleaseGate, EvalRun, EvalRunComparison, EvalSuite } from '@/types/api'

const context = useContextStore()
const suites = ref<EvalSuite[]>([])
const runs = ref<EvalRun[]>([])
const gates = ref<EvalReleaseGate[]>([])
const comparison = ref<EvalRunComparison | null>(null)
const loading = ref(false)
const activeTab = ref('suites')
const runDialog = ref(false)
const dialogMode = ref<'run' | 'gate'>('run')
const compareForm = reactive({ baselineRunId: '', candidateRunId: '' })
const runForm = reactive({
  suiteId: '',
  modelVersion: 'deepseek-v4-flash',
  knowledgeVersion: 'kb-current',
  promptVersion: 'prompt-current',
  minPassRate: 0.9,
  maxRegressions: 0,
})

onMounted(load)

async function load() {
  loading.value = true
  const params = new URLSearchParams({ tenantId: context.tenantId, limit: '30' })
  const suiteParams = new URLSearchParams({ tenantId: context.tenantId, enabledOnly: 'false' })
  try {
    ;[suites.value, runs.value, gates.value] = await Promise.all([
      fetchEvalSuites(suiteParams),
      fetchEvalRuns(params),
      fetchReleaseGates(params),
    ])
    if (!compareForm.candidateRunId && runs.value.length)
      compareForm.candidateRunId = runs.value[0]!.runId
    if (!compareForm.baselineRunId && runs.value.length > 1)
      compareForm.baselineRunId = runs.value[1]!.runId
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function openRun(suite: EvalSuite, mode: 'run' | 'gate') {
  runForm.suiteId = suite.suiteId
  dialogMode.value = mode
  runDialog.value = true
}

async function submitRun() {
  loading.value = true
  try {
    if (dialogMode.value === 'run') {
      const params = new URLSearchParams({
        tenantId: context.tenantId,
        modelVersion: runForm.modelVersion,
        knowledgeVersion: runForm.knowledgeVersion,
        promptVersion: runForm.promptVersion,
      })
      const result = await runEvalSuite(runForm.suiteId, params)
      ElMessage.success(`评测运行完成：${result.passedCases}/${result.totalCases} 通过`)
      activeTab.value = 'runs'
    } else {
      await ElMessageBox.confirm('发布门禁会运行候选评测并与最近基线比较。', '确认运行发布门禁', {
        confirmButtonText: '运行门禁',
        cancelButtonText: '取消',
        type: 'warning',
      })
      const result = await runReleaseGate({
        tenantId: context.tenantId,
        suiteId: runForm.suiteId,
        modelVersion: runForm.modelVersion,
        knowledgeVersion: runForm.knowledgeVersion,
        promptVersion: runForm.promptVersion,
        minPassRate: runForm.minPassRate,
        maxRegressions: runForm.maxRegressions,
      })
      ElMessage.success(`发布门禁结果：${result.status}`)
      activeTab.value = 'gates'
    }
    runDialog.value = false
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function compare() {
  if (!compareForm.baselineRunId || !compareForm.candidateRunId) {
    ElMessage.warning('请选择基线与候选运行')
    return
  }
  loading.value = true
  try {
    comparison.value = await compareEvalRuns(
      new URLSearchParams({
        baselineRunId: compareForm.baselineRunId,
        candidateRunId: compareForm.candidateRunId,
      }),
    )
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function statusType(value: string) {
  if (['PASSED', 'COMPLETED', 'SUCCESS'].includes(value)) return 'success'
  if (['BLOCKED', 'FAILED'].includes(value)) return 'danger'
  return 'warning'
}
</script>

<template>
  <div class="eval-page" v-loading="loading">
    <section class="toolbar">
      <div><strong>评测与发布门禁</strong><span>评测版本、运行对比和上线判断</span></div>
      <el-button :icon="RefreshCw" @click="load">刷新</el-button>
    </section>

    <section class="eval-workspace">
      <el-tabs v-model="activeTab">
        <el-tab-pane :label="`评测集 ${suites.length}`" name="suites">
          <el-table :data="suites" stripe empty-text="暂无评测集">
            <el-table-column prop="suiteId" label="评测集" min-width="210" />
            <el-table-column prop="suiteName" label="名称" min-width="180" />
            <el-table-column prop="suiteVersion" label="版本" width="100" />
            <el-table-column prop="caseCount" label="用例" width="80" />
            <el-table-column label="启用" width="80"
              ><template #default="scope">{{
                scope.row.enabled ? '是' : '否'
              }}</template></el-table-column
            >
            <el-table-column label="操作" width="210" fixed="right"
              ><template #default="scope"
                ><el-button link type="primary" :icon="Play" @click="openRun(scope.row, 'run')"
                  >运行</el-button
                ><el-button
                  link
                  type="success"
                  :icon="ShieldCheck"
                  @click="openRun(scope.row, 'gate')"
                  >发布门禁</el-button
                ></template
              ></el-table-column
            >
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`最近运行 ${runs.length}`" name="runs">
          <div class="compare-toolbar">
            <el-select v-model="compareForm.baselineRunId" placeholder="基线运行"
              ><el-option
                v-for="run in runs"
                :key="`base-${run.runId}`"
                :label="run.runId"
                :value="run.runId"
            /></el-select>
            <el-select v-model="compareForm.candidateRunId" placeholder="候选运行"
              ><el-option
                v-for="run in runs"
                :key="`candidate-${run.runId}`"
                :label="run.runId"
                :value="run.runId"
            /></el-select>
            <el-button type="primary" :icon="GitCompare" @click="compare">对比</el-button>
          </div>
          <div v-if="comparison" class="comparison-band">
            <div>
              <span>用例</span><strong>{{ comparison.totalCases }}</strong>
            </div>
            <div>
              <span>提升</span><strong>{{ comparison.improvedCases }}</strong>
            </div>
            <div>
              <span>退化</span><strong class="danger">{{ comparison.regressedCases }}</strong>
            </div>
            <div>
              <span>不变</span><strong>{{ comparison.unchangedCases }}</strong>
            </div>
          </div>
          <el-table :data="runs" stripe empty-text="暂无评测运行">
            <el-table-column prop="runId" label="运行 ID" min-width="190" show-overflow-tooltip />
            <el-table-column prop="suiteVersion" label="评测版本" width="110" />
            <el-table-column prop="modelVersion" label="模型版本" min-width="150" />
            <el-table-column label="通过" width="100"
              ><template #default="scope"
                >{{ scope.row.passedCases }}/{{ scope.row.totalCases }}</template
              ></el-table-column
            >
            <el-table-column label="状态" width="110"
              ><template #default="scope"
                ><el-tag :type="statusType(scope.row.status)" effect="plain">{{
                  scope.row.status
                }}</el-tag></template
              ></el-table-column
            >
            <el-table-column label="开始时间" min-width="170"
              ><template #default="scope">{{
                formatTime(scope.row.startedAt)
              }}</template></el-table-column
            >
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`发布门禁 ${gates.length}`" name="gates">
          <el-table :data="gates" stripe empty-text="暂无发布门禁记录">
            <el-table-column prop="gateId" label="门禁 ID" min-width="190" />
            <el-table-column prop="suiteId" label="评测集" min-width="190" />
            <el-table-column label="通过率" width="100"
              ><template #default="scope">{{
                percent(scope.row.passRate)
              }}</template></el-table-column
            >
            <el-table-column prop="regressedCases" label="退化" width="80" />
            <el-table-column label="状态" width="110"
              ><template #default="scope"
                ><el-tag :type="statusType(scope.row.status)" effect="plain">{{
                  scope.row.status
                }}</el-tag></template
              ></el-table-column
            >
            <el-table-column label="原因" min-width="260"
              ><template #default="scope">{{
                scope.row.reasons?.join('；') || '-'
              }}</template></el-table-column
            >
            <el-table-column label="时间" min-width="170"
              ><template #default="scope">{{
                formatTime(scope.row.createdAt)
              }}</template></el-table-column
            >
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog
      v-model="runDialog"
      :title="dialogMode === 'run' ? '运行评测集' : '运行发布门禁'"
      width="min(560px, 92vw)"
    >
      <el-form label-position="top">
        <el-form-item label="评测集"><el-input v-model="runForm.suiteId" disabled /></el-form-item>
        <el-form-item label="模型版本"><el-input v-model="runForm.modelVersion" /></el-form-item>
        <el-form-item label="知识库版本"
          ><el-input v-model="runForm.knowledgeVersion"
        /></el-form-item>
        <el-form-item label="提示词版本"><el-input v-model="runForm.promptVersion" /></el-form-item>
        <div v-if="dialogMode === 'gate'" class="gate-grid">
          <el-form-item label="最低通过率"
            ><el-input-number v-model="runForm.minPassRate" :min="0" :max="1" :step="0.01"
          /></el-form-item>
          <el-form-item label="最大退化数"
            ><el-input-number v-model="runForm.maxRegressions" :min="0" :max="100"
          /></el-form-item>
        </div>
      </el-form>
      <template #footer
        ><el-button @click="runDialog = false">取消</el-button
        ><el-button type="primary" @click="submitRun">{{
          dialogMode === 'run' ? '开始评测' : '运行门禁'
        }}</el-button></template
      >
    </el-dialog>
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
.eval-workspace {
  margin-top: 12px;
  padding: 0 14px 14px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
}
.compare-toolbar {
  display: grid;
  padding-bottom: 12px;
  grid-template-columns: minmax(180px, 1fr) minmax(180px, 1fr) auto;
  gap: 8px;
}
.comparison-band {
  display: grid;
  margin-bottom: 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  grid-template-columns: repeat(4, 1fr);
}
.comparison-band div {
  padding: 12px;
  border-right: 1px solid var(--line);
}
.comparison-band div:last-child {
  border-right: 0;
}
.comparison-band span,
.comparison-band strong {
  display: block;
}
.comparison-band span {
  color: var(--muted);
  font-size: 10px;
}
.comparison-band strong {
  margin-top: 4px;
  font-size: 18px;
}
.danger {
  color: #a63d46;
}
.gate-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.gate-grid :deep(.el-input-number) {
  width: 100%;
}
@media (max-width: 700px) {
  .eval-page {
    padding: 10px;
  }
  .compare-toolbar,
  .gate-grid {
    grid-template-columns: 1fr;
  }
  .comparison-band {
    grid-template-columns: repeat(2, 1fr);
  }
  .comparison-band div {
    border-bottom: 1px solid var(--line);
  }
}
</style>
