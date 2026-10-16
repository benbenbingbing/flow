import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { readMySetting, saveMySetting, resetMySetting } from '@/api/system/settings'
import { USER_INTERFACE_PREFERENCES_KEY, createUserInterfacePreferences, initialUserInterfacePreferencesState } from '@/shared/user-interface-preferences'

/** 一个账号只有一个偏好对象和保存队列，各界面通过字段访问，避免多份记录版本相互覆盖。 */
export const useUserInterfacePreferencesStore = defineStore('userInterfacePreferences', () => {
  const user = useUserStore()
  const state = ref(initialUserInterfacePreferencesState())
  let runtime = null
  watch(() => user.userInfo?.userId ?? user.userInfo?.id, userId => {
    runtime?.dispose()
    runtime = null
    state.value = initialUserInterfacePreferencesState()
    if (!userId) return
    runtime = createUserInterfacePreferences({
      read: () => readMySetting(USER_INTERFACE_PREFERENCES_KEY),
      write: data => saveMySetting(USER_INTERFACE_PREFERENCES_KEY, data),
      remove: data => resetMySetting(USER_INTERFACE_PREFERENCES_KEY, data),
      onChange: value => { state.value = value }
    })
    void runtime.refresh()
  }, { immediate: true, flush: 'sync' })
  return { state, refresh: () => runtime?.refresh(),
    setValue: (key, value) => runtime?.setValue(key, value), reset: key => runtime?.reset(key) }
})
