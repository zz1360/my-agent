import { ElMessageBox } from 'element-plus'

export function useConfirmAction() {
  async function confirm(message: string, title = '确认操作') {
    await ElMessageBox.confirm(message, title, {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
  }
  return { confirm }
}
