import { computed, onBeforeUnmount, reactive, toRef } from 'vue'
import { createInboxPageLoader, createInboxPageState, INBOX_KEYS } from './inboxPageState'

/** 连接页签 UI 与请求状态；查询条件由页面在调用时快照，保持现有切页和筛选体验。 */
export function useWorkInbox(activeTab, getParams, api, prepareRows) {
  const state = reactive(createInboxPageState())
  const loader = createInboxPageLoader(state, api, { prepareRows })
  const loadedTabs = reactive(Object.fromEntries(INBOX_KEYS.map(key => [key, toRef(state[key], 'loaded')])))
  onBeforeUnmount(loader.dispose)
  return {
    state, loadedTabs,
    loading: computed(() => state[activeTab.value].loading),
    activeError: computed(() => state[activeTab.value].error),
    load: key => loader.load(key, getParams())
  }
}
