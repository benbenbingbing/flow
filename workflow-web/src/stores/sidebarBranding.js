import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { readMySetting } from '@/api/system/settings'
import { DEFAULT_SIDEBAR_BRANDING, SIDEBAR_BRANDING_SETTING_KEY, normalizeSidebarBranding } from '@/shared/sidebar-branding'

/** 全系统共用的侧栏标识；普通用户只读取，不产生个人覆盖记录。 */
export const useSidebarBrandingStore = defineStore('sidebarBranding', () => {
  const user = useUserStore()
  const value = ref({ ...DEFAULT_SIDEBAR_BRANDING })
  let revision = 0

  /** 保存或恢复默认后直接应用服务端结果，同时使尚未结束的旧读取失效。 */
  function applySetting(setting) {
    revision += 1
    value.value = normalizeSidebarBranding(setting?.value)
  }

  /** 读取失败保留最近一次成功值；账号切换与保存均能使旧响应失效。 */
  async function refresh() {
    if (!(user.userInfo?.userId ?? user.userInfo?.id)) return
    const requestRevision = ++revision
    try {
      const setting = await readMySetting(SIDEBAR_BRANDING_SETTING_KEY)
      if (requestRevision === revision) applySetting(setting)
    } catch {
      // 标识读取失败不阻塞菜单；首次加载继续显示原有默认标识。
    }
  }

  watch(() => user.userInfo?.userId ?? user.userInfo?.id, () => {
    applySetting({ value: DEFAULT_SIDEBAR_BRANDING })
    void refresh()
  }, { immediate: true, flush: 'sync' })

  return { value, applySetting, refresh }
})
