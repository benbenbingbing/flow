import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { readMySetting, saveMySetting, resetMySetting } from '@/api/system/settings'
import { createBooleanPreference } from '@/shared/boolean-preference'

export const SIDEBAR_COLLAPSED_SETTING_KEY = 'ui.layout.sidebar_collapsed'
const initialState = () => ({ value: false, loaded: false, loading: false, saving: false, source: 'DEFAULT', overridden: false, error: '' })

/** 主菜单折叠按账号持久化；只读取有效设置，手动切换才创建个人覆盖。 */
export const useSidebarPreferenceStore = defineStore('sidebarPreference', () => {
  const user = useUserStore()
  const state = ref(initialState())
  let runtime = null

  // 切换账号立即丢弃旧响应和待写队列；浏览器原有的通用折叠值不能推断属于哪个用户。
  watch(() => user.userInfo?.userId ?? user.userInfo?.id, userId => {
    runtime?.dispose()
    runtime = null
    state.value = initialState()
    if (!userId) return
    runtime = createBooleanPreference({
      read: () => readMySetting(SIDEBAR_COLLAPSED_SETTING_KEY),
      write: data => saveMySetting(SIDEBAR_COLLAPSED_SETTING_KEY, data),
      remove: data => resetMySetting(SIDEBAR_COLLAPSED_SETTING_KEY, data),
      onChange: value => { state.value = value }
    })
    void runtime.refresh()
  }, { immediate: true, flush: 'sync' })

  return {
    state,
    toggle: () => runtime?.setValue(!state.value.value),
    reset: () => runtime?.reset(),
    refresh: () => runtime?.refresh()
  }
})
