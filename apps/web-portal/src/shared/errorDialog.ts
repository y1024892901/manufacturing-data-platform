import { ElMessageBox } from 'element-plus'

const shownAt = new Map<string, number>()

export function showErrorDialog(message: unknown, title = '操作失败') {
  const text = typeof message === 'string' && message.trim()
    ? message
    : '发生未知错误，请稍后重试'
  const key = title + ':' + text
  const now = Date.now()
  if (now - (shownAt.get(key) || 0) < 1200) return
  shownAt.set(key, now)
  if (shownAt.size > 40) {
    for (const [oldKey, time] of shownAt) if (now - time > 10000) shownAt.delete(oldKey)
  }
  void ElMessageBox.alert(text, title, {
    type: 'error',
    confirmButtonText: '我知道了',
    closeOnClickModal: true
  }).catch(() => undefined)
}
