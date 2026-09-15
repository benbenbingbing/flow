import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from './user'
import { readMySetting, saveMySetting, resetMySetting } from '@/api/system/settings'
import { createBooleanPreference } from '@/shared/boolean-preference'

const KEY = 'ui.entity_design.field_types_collapsed'
const initialState = () => ({ value: false, loaded: false, loading: false, saving: false, source: 'DEFAULT', overridden: false, error: '' })

/** 按登录账号隔离的内存偏好；跨实体页面继续完成保存，数据库作为持久化来源。 */
export const useFieldTypesPreferenceStore = defineStore('fieldTypesPreference', () => {
  const user = useUserStore()
  const state = ref(initialState())
  let runtime = null

  watch(() => user.userInfo?.userId ?? user.userInfo?.id, (userId) => {
    runtime?.dispose()
    runtime = null
    state.value = initialState()
    if (!userId) return
    runtime = createBooleanPreference({
      read: () => readMySetting(KEY),
      write: (data) => saveMySetting(KEY, data),
      remove: (data) => resetMySetting(KEY, data),
      onChange: (value) => { state.value = value },
      onError: (message) => ElMessage.warning(message)
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
