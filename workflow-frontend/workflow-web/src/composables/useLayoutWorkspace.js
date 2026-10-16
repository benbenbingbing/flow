import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useWorkspaceTabsStore } from '@/stores/workspaceTabs'
import { useTabsPreferenceStore } from '@/stores/tabsPreference'
import { workspaceRouteKey, workspaceRouteTitle } from '@/shared/workspace-tabs'
import { findDeepestMenuChain } from '@/utils/breadcrumb'

/** 将成功路由、账号生命周期和模式偏好接到标签容器，所有入口都沿用原路由授权。 */
export function useLayoutWorkspace(menuTree) {
  const route = useRoute()
  const router = useRouter()
  const user = useUserStore()
  const workspace = useWorkspaceTabsStore()
  const preference = useTabsPreferenceStore()
  const ownerId = computed(() => user.userInfo?.userId ?? user.userInfo?.id)
  const layoutRecord = route.matched[0]

  workspace.configureConfirm(async dirty => {
    try {
      const titles = [...new Set(dirty.map(item => item.title))].join('、')
      await ElMessageBox.confirm(`“${titles}”有未保存的修改，关闭后将丢失。是否放弃这些修改？`, '确认关闭页面', {
        type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑',
        closeOnClickModal: false, closeOnPressEscape: false
      })
      return true
    } catch { return false }
  })

  function visitCurrent() {
    if (!ownerId.value || route.matched[0] !== layoutRecord) return
    const chain = findDeepestMenuChain(menuTree.value, route.path)
    workspace.visit(route, chain?.at(-1)?.menuName || workspaceRouteTitle(route))
  }

  watch(ownerId, () => { workspace.clear(); visitCurrent() }, { immediate: true, flush: 'sync' })
  // currentRoute 只在导航成功后变化；失败、取消或重定向前的中间路由不会生成标签。
  watch(() => route.fullPath, visitCurrent, { flush: 'sync' })
  watch(menuTree, visitCurrent)
  watch(() => preference.state.value, value => { void workspace.setEnabled(value) }, { immediate: true })

  const removeGuard = router.beforeEach(async to => {
    // 会话撤销和强制改密不能被草稿确认阻挡；账号 watch 会同步清除旧缓存。
    if (!ownerId.value || user.userInfo?.passwordResetRequired) return true
    const staysInLayout = to.matched[0] === layoutRecord
    if (staysInLayout && (workspace.enabled || workspaceRouteKey(to) === workspace.activeKey)) return true
    return workspace.canDiscard(staysInLayout ? [workspace.activeKey] : workspace.tabs.map(tab => tab.key))
  })

  /** 先确认将销毁的页面，再改变模式并尽力保存个人选择。 */
  async function toggleMode() {
    const enabled = !workspace.enabled
    if (await workspace.setEnabled(enabled)) void preference.setValue(enabled)
  }

  async function navigate(path) {
    const failure = await router.push(path)
    return !failure
  }

  function beforeUnload(event) {
    if (!workspace.hasUnsavedChanges()) return
    event.preventDefault()
    event.returnValue = ''
  }

  onMounted(() => {
    void preference.refresh()
    window.addEventListener('focus', preference.refresh)
    window.addEventListener('beforeunload', beforeUnload)
  })
  onBeforeUnmount(() => {
    removeGuard()
    window.removeEventListener('focus', preference.refresh)
    window.removeEventListener('beforeunload', beforeUnload)
    workspace.clear()
  })

  return { workspace, toggleMode, activate: tab => router.push(tab.route.fullPath),
    closeTab: key => workspace.close(key, navigate) }
}
