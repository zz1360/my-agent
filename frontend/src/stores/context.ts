import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  fetchCsrfToken,
  fetchSecurityConfig,
  fetchSecurityContext,
  logoutSession,
} from '@/api/agent'
import { errorMessage } from '@/api/http'
import type { AgentPermission, SecurityConfig, SecurityContext } from '@/types/api'

export const useContextStore = defineStore('context', () => {
  const tenantId = ref('T001')
  const userId = ref('api-client')
  const roles = ref<string[]>([])
  const permissions = ref<AgentPermission[]>([])
  const authenticated = ref(false)
  const initialized = ref(false)
  const loading = ref(false)
  const authError = ref('')
  const apiKeyRequired = ref(false)
  const authenticationType = ref('none')
  const securityConfig = ref<SecurityConfig>({
    mode: 'api-key',
    loginUrl: '',
    logoutUrl: '',
    csrfUrl: '',
  })

  const primaryRole = computed(() => roles.value[0] || 'UNASSIGNED')
  const roleHeader = computed(() => roles.value.join(','))

  async function bootstrap(force = false): Promise<boolean> {
    if (initialized.value && !force) return authenticated.value
    loading.value = true
    authError.value = ''
    try {
      securityConfig.value = await fetchSecurityConfig()
      if (securityConfig.value.mode === 'oidc-bff' && securityConfig.value.csrfUrl) {
        await fetchCsrfToken(securityConfig.value.csrfUrl)
      }
      apply(await fetchSecurityContext())
      return authenticated.value
    } catch (error) {
      authenticated.value = false
      permissions.value = []
      authError.value = errorMessage(error)
      initialized.value = true
      return false
    } finally {
      loading.value = false
    }
  }

  function apply(context: SecurityContext) {
    tenantId.value = context.tenantId
    userId.value = context.userId
    roles.value = [...context.roles]
    permissions.value = [...context.permissions]
    authenticated.value = context.authenticated
    apiKeyRequired.value = context.apiKeyRequired
    authenticationType.value = context.authenticationType
    initialized.value = true
  }

  function hasPermission(permission?: AgentPermission): boolean {
    return !permission || permissions.value.includes(permission)
  }

  function reauthenticate() {
    const entryUrl = securityConfig.value.loginUrl
    if (entryUrl) {
      window.location.assign(entryUrl)
      return
    }
    initialized.value = false
    window.location.reload()
  }

  async function logout() {
    const logoutUrl = securityConfig.value.logoutUrl
    initialized.value = false
    authenticated.value = false
    permissions.value = []
    if (logoutUrl && securityConfig.value.mode === 'oidc-bff') {
      try {
        await logoutSession(logoutUrl)
      } catch {
        // The local API-key mode has no server session to invalidate.
      }
    }
    window.location.assign('/access')
  }

  return {
    tenantId,
    userId,
    roles,
    permissions,
    primaryRole,
    roleHeader,
    authenticated,
    initialized,
    loading,
    authError,
    apiKeyRequired,
    authenticationType,
    securityConfig,
    bootstrap,
    hasPermission,
    reauthenticate,
    logout,
  }
})
