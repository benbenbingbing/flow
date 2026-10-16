import { computed } from 'vue'
import { useUserInterfacePreferencesStore } from './userInterfacePreferences'

/** 保留各界面的字段级状态和恢复入口，实际读写统一交由 JSON 偏好队列。 */
export function useUserInterfacePreferenceField(key) {
  const preferences = useUserInterfacePreferencesStore()
  const state = computed(() => ({ ...preferences.state, value: preferences.state.value[key],
    overridden: Object.hasOwn(preferences.state.overrideValue, key) }))
  return { state, toggle: () => preferences.setValue(key, !state.value.value),
    setValue: value => preferences.setValue(key, value), reset: () => preferences.reset(key), refresh: preferences.refresh }
}
