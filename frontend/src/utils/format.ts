export function formatTime(value?: string): string {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

export function percent(value?: number): string {
  return `${Math.round(Number(value || 0) * 100)}%`
}
