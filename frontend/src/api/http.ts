import axios, { AxiosError } from 'axios'
import { useContextStore } from '@/stores/context'
import type { ApiErrorResponse } from '@/types/api'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30_000,
  headers: { 'Content-Type': 'application/json' },
})

export function apiUrl(path: string): string {
  const baseUrl = String(import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
  return `${baseUrl}${path}`
}

http.interceptors.request.use((config) => {
  const context = useContextStore()
  config.headers.set('X-Agent-Tenant', context.tenantId)
  config.headers.set('X-Agent-User', context.userId)
  config.headers.set('X-Agent-Roles', context.roleHeader)
  return config
})

export function contextHeaders(): Record<string, string> {
  const context = useContextStore()
  return {
    'Content-Type': 'application/json',
    'X-Agent-Tenant': context.tenantId,
    'X-Agent-User': context.userId,
    'X-Agent-Roles': context.roleHeader,
  }
}

export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const response = (error as AxiosError<ApiErrorResponse>).response?.data
    return response?.message || response?.detail || error.message
  }
  return error instanceof Error ? error.message : '请求失败，请稍后重试'
}
