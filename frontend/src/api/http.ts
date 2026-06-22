import axios, { AxiosError } from 'axios'
import type { ApiErrorResponse } from '@/types/api'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30_000,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const requestUrl = String(error.config?.url || '')
    if (error.response?.status === 401 && !requestUrl.includes('/security/context')) {
      window.dispatchEvent(new CustomEvent('agent-auth-invalid'))
    }
    return Promise.reject(error)
  },
)

export function apiUrl(path: string): string {
  const baseUrl = String(import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
  return `${baseUrl}${path}`
}

export function contextHeaders(): Record<string, string> {
  return {
    'Content-Type': 'application/json',
  }
}

export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const response = (error as AxiosError<ApiErrorResponse>).response?.data
    return response?.message || response?.detail || error.message
  }
  return error instanceof Error ? error.message : '请求失败，请稍后重试'
}
