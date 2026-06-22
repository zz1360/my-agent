<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { LogOut, RefreshCw } from '@lucide/vue'
import { useContextStore } from '@/stores/context'

const open = defineModel<boolean>({ required: true })
const context = useContextStore()

async function refresh() {
  const authenticated = await context.bootstrap(true)
  if (authenticated) ElMessage.success('身份与权限已刷新')
  else ElMessage.error(context.authError || '身份验证失败')
}
</script>

<template>
  <el-drawer v-model="open" title="身份与权限" size="360px">
    <div class="identity-block">
      <span>租户</span><strong>{{ context.tenantId }}</strong> <span>用户</span
      ><strong>{{ context.userId }}</strong> <span>认证方式</span
      ><strong>{{ context.authenticationType }}</strong>
    </div>
    <div class="drawer-section">
      <h3>角色</h3>
      <div class="tag-list">
        <el-tag v-for="role in context.roles" :key="role" effect="plain">{{ role }}</el-tag>
      </div>
    </div>
    <div class="drawer-section">
      <h3>权限</h3>
      <div class="tag-list">
        <el-tag
          v-for="permission in context.permissions"
          :key="permission"
          type="info"
          effect="plain"
          >{{ permission }}</el-tag
        >
      </div>
    </div>

    <template #footer>
      <el-button :icon="RefreshCw" :loading="context.loading" @click="refresh">刷新</el-button>
      <el-button :icon="LogOut" @click="context.logout">退出</el-button>
    </template>
  </el-drawer>
</template>

<style scoped>
.identity-block {
  display: grid;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 12px;
}
.identity-block span,
.drawer-section h3 {
  color: var(--muted);
  font-size: 11px;
}
.identity-block strong {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
}
.drawer-section {
  margin-top: 20px;
}
.drawer-section h3 {
  margin: 0 0 9px;
}
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}
</style>
