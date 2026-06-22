/// <reference types="vite/client" />

import type { AgentPermission } from '@/types/api'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    public?: boolean
    permission?: AgentPermission
  }
}
