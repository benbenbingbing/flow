import { onBeforeUnmount, onMounted, unref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessageBox } from 'element-plus'

function resolveDirtyState(isDirty) {
  return typeof isDirty === 'function' ? Boolean(isDirty()) : Boolean(unref(isDirty))
}

export function useUnsavedChangesGuard(isDirty, options = {}) {
  const message = options.message || '当前页面有未保存的修改，离开后这些修改将丢失。'
  const title = options.title || '确认离开？'

  const handleBeforeUnload = (event) => {
    if (!resolveDirtyState(isDirty)) return
    event.preventDefault()
    event.returnValue = ''
  }

  onMounted(() => {
    window.addEventListener('beforeunload', handleBeforeUnload)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('beforeunload', handleBeforeUnload)
  })

  onBeforeRouteLeave(async () => {
    if (!resolveDirtyState(isDirty)) return true
    try {
      await ElMessageBox.confirm(message, title, {
        type: 'warning',
        confirmButtonText: '放弃修改并离开',
        cancelButtonText: '继续编辑',
        closeOnClickModal: false,
        closeOnPressEscape: false
      })
      return true
    } catch {
      return false
    }
  })
}
