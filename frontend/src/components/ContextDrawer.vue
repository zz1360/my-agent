<script setup lang="ts">
import { reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Save } from '@lucide/vue'
import { useContextStore } from '@/stores/context'

const open = defineModel<boolean>({ required: true })
const context = useContextStore()

const form = reactive({
  tenantId: context.tenantId,
  userId: context.userId,
  roles: [...context.roles],
})

watch(open, (visible) => {
  if (!visible) return
  form.tenantId = context.tenantId
  form.userId = context.userId
  form.roles = [...context.roles]
})

function save() {
  context.update(form)
  open.value = false
  ElMessage.success('运行上下文已更新')
}
</script>

<template>
  <el-drawer v-model="open" title="运行上下文" size="360px">
    <el-form label-position="top">
      <el-form-item label="租户">
        <el-input v-model="form.tenantId" placeholder="T001" />
      </el-form-item>
      <el-form-item label="用户">
        <el-input v-model="form.userId" placeholder="u-cs-001" />
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="form.roles" multiple style="width: 100%">
          <el-option label="客户服务" value="CUSTOMER_SERVICE" />
          <el-option label="运营" value="OPERATIONS" />
          <el-option label="运营经理" value="OPS_MANAGER" />
          <el-option label="销售" value="SALES" />
          <el-option label="管理员" value="ADMIN" />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" :icon="Save" @click="save">保存</el-button>
    </template>
  </el-drawer>
</template>
