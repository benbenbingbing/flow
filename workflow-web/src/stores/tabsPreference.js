import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { readMySetting, saveMySetting, resetMySetting } from '@/api/system/settings'
import { createBooleanPreference } from '@/shared/boolean-preference'

export const TABS_ENABLED_SETTING_KEY = 'ui.layout.tabs_enabled'
const initialState = () => ({ value: false, loaded: false, loading: false, saving: false, source: 'DEFAULT', overridden: false, error: '' })

/** 系统提供默认模式；用户明确切换才写入个人偏好，保存失败不撤销本次选择。 */
export const useTabsPreferenceStore = defineStore('tabsPreference', () => {
  const user = useUserStore()
  const state = ref(initialState())
  let runtime = null
  watch(() => user.userInfo?.userId ?? user.userInfo?.id, userId => {
    runtime?.dispose()
    runtime = null
    state.value = initialState()
    if (!userId) return
    runtime = createBooleanPreference({
      read: () => readMySetting(TABS_ENABLED_SETTING_KEY),
      write: data => saveMySetting(TABS_ENABLED_SETTING_KEY, data),
      remove: data => resetMySetting(TABS_ENABLED_SETTING_KEY, data),
      onChange: value => { state.value = value }
    })
    void runtime.refresh()
  }, { immediate: true, flush: 'sync' })
  return { state, setValue: value => runtime?.setValue(value), refresh: () => runtime?.refresh() }
})
