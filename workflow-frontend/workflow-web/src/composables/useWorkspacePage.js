import { computed, inject, onBeforeUnmount } from 'vue'
import { workspacePageKey } from '../shared/workspace-tabs.js'

/** 隐藏缓存页面暂停外部交互；弹窗隐藏只影响展示，不清除页面自己的打开状态和输入。 */
export function useWorkspacePage() {
  const page = inject(workspacePageKey, null)
  const active = computed(() => page?.active.value ?? true)
  function visibleWhenActive(visible) {
    return computed({
      get: () => active.value && visible.value,
      // 停用引发的 Dialog update:modelValue 不能误写为用户主动关闭。
      set: value => { if (active.value) visible.value = value }
    })
  }
  function registerGuard(isDirty, message = '当前表单有未保存的修改') {
    if (page) onBeforeUnmount(page.registerGuard({ isDirty, message }))
  }
  return { page, active, visibleWhenActive, registerGuard }
}
