<template>
  <el-container class="layout-container">
    <el-aside
      :width="desktopSidebarWidth"
      class="sidebar desktop-sidebar"
      :class="{
        'is-collapsed': sidebarCollapsed,
        'is-resizing': sidebarResizing
      }"
    >
      <div class="logo" :title="sidebarCollapsed ? '流程配置系统' : undefined">
        <el-icon size="24"><Connection /></el-icon>
        <span v-if="!sidebarCollapsed">流程配置系统</span>
      </div>
      <el-menu
        :default-active="activeMenuPath"
        :collapse="sidebarCollapsed"
        :collapse-transition="false"
        router
        class="menu desktop-menu"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <sidebar-menu-item
          v-for="menu in menuTree"
          :key="menu.id"
          :menu="menu"
        />
      </el-menu>
      <div
        v-show="!sidebarCollapsed"
        class="sidebar-resizer"
        role="separator"
        aria-label="调整左侧菜单宽度"
        aria-orientation="vertical"
        :aria-valuemin="SIDEBAR_MIN_WIDTH"
        :aria-valuemax="SIDEBAR_MAX_WIDTH"
        :aria-valuenow="sidebarWidth"
        tabindex="0"
        title="拖拽调整宽度，双击恢复默认宽度"
        @pointerdown="startSidebarResize"
        @keydown="handleSidebarResizeKeydown"
        @dblclick="resetSidebarWidth"
      />
    </el-aside>
    <el-container class="content-container">
      <el-header class="header">
        <div class="header-left">
          <el-button
            class="mobile-menu-button"
            text
            circle
            aria-label="打开导航菜单"
            @click="mobileMenuVisible = true"
          >
            <el-icon size="22"><Menu /></el-icon>
          </el-button>
          <el-button
            class="desktop-sidebar-toggle"
            text
            circle
            :aria-label="sidebarCollapsed ? '展开左侧菜单' : '收起左侧菜单'"
            :aria-expanded="!sidebarCollapsed"
            :title="sidebarPreference.state.error || (sidebarCollapsed ? '展开左侧菜单' : '收起左侧菜单')"
            @click="toggleSidebar"
          >
            <el-icon size="20">
              <Expand v-if="sidebarCollapsed" />
              <Fold v-else />
            </el-icon>
          </el-button>
          <WorkspaceTabs v-if="workspace.enabled" :tabs="workspace.tabs" :active-key="workspace.activeKey" :busy="workspace.busy"
            @activate="activateWorkspaceTab" @close="closeWorkspaceTab" />
          <el-breadcrumb v-else-if="breadcrumb.length > 0" separator="/" class="breadcrumb">
            <el-breadcrumb-item
              v-for="item in breadcrumb"
              :key="item.id"
              :to="item.path ? { path: item.path } : undefined"
            >
              {{ item.menuName }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <MenuSearch :menus="menuTree" :loading="menusLoading" />
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar 
                :size="28" 
                :src="userStore.avatar || defaultAvatar" 
                class="user-avatar"
              />
              {{ userStore.nickname || '未登录' }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="toggle-tabs" :disabled="workspace.busy">
                  {{ workspace.enabled ? '关闭多标签页' : '开启多标签页' }}
                </el-dropdown-item>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main-content">
        <WorkspacePages />
      </el-main>
    </el-container>

    <el-drawer
      v-model="mobileMenuVisible"
      class="mobile-nav-drawer"
      direction="ltr"
      size="min(82vw, 300px)"
      :with-header="false"
    >
      <div class="logo mobile-logo">
        <el-icon size="24"><Connection /></el-icon>
        <span>流程配置系统</span>
      </div>
      <el-menu
        :default-active="activeMenuPath"
        router
        class="menu mobile-menu"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <sidebar-menu-item
          v-for="menu in menuTree"
          :key="menu.id"
          :menu="menu"
        />
      </el-menu>
    </el-drawer>
  </el-container>
</template>

<script setup>
import { ref, computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Menu, Connection, ArrowDown, Expand, Fold } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useSidebarPreferenceStore } from '@/stores/sidebarPreference'
import { getPermissions, logout } from '@/api/auth'
import { getSidebarMenuTree } from '@/api/system/menu'
import SidebarMenuItem from '@/components/SidebarMenuItem.vue'
import MenuSearch from '@/components/MenuSearch.vue'
import WorkspaceTabs from '@/components/workspace/WorkspaceTabs.vue'
import WorkspacePages from '@/components/workspace/WorkspacePages.vue'
import { useLayoutWorkspace } from '@/composables/useLayoutWorkspace'
import { provideBreadcrumbParents } from '@/composables/useBreadcrumbParents'
import {
  SIDEBAR_MENU_REFRESH_EVENT,
  SIDEBAR_MENU_REVISION_KEY
} from '@/utils/menuRefresh'
import {
  buildBreadcrumb,
  getActiveMenuPath
} from '@/utils/breadcrumb'
import {
  SIDEBAR_COLLAPSED_WIDTH,
  SIDEBAR_DEFAULT_WIDTH,
  SIDEBAR_MAX_WIDTH,
  SIDEBAR_MIN_WIDTH,
  SIDEBAR_RESIZE_STEP,
  SIDEBAR_WIDTH_STORAGE_KEY,
  calculateSidebarWidth,
  normalizeSidebarWidth,
  persistSidebarLayout,
  readSidebarLayout
} from '@/utils/sidebarLayout'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const sidebarPreference = useSidebarPreferenceStore()
const mobileMenuVisible = ref(false)
const initialSidebarLayout = readSidebarLayout()
const sidebarWidth = ref(initialSidebarLayout.width)
const sidebarCollapsed = computed(() => sidebarPreference.state.value)
const sidebarResizing = ref(false)

const desktopSidebarWidth = computed(() => {
  return `${sidebarCollapsed.value ? SIDEBAR_COLLAPSED_WIDTH : sidebarWidth.value}px`
})

let sidebarResizeSession = null

const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

// 菜单树
const menuTree = ref([])
const menusLoading = ref(false)
const { workspace, toggleMode: toggleWorkspaceMode, activate: activateWorkspaceTab, closeTab: closeWorkspaceTab } = useLayoutWorkspace(menuTree)

const activeMenuPath = computed(() => getActiveMenuPath(route))
const breadcrumbParents = provideBreadcrumbParents()
const breadcrumb = computed(() => buildBreadcrumb(menuTree.value, route, breadcrumbParents.value))

const saveSidebarLayout = () => {
  persistSidebarLayout({
    width: sidebarWidth.value
  })
}

/**
 * 清理一次侧栏拖拽会话，确保鼠标释放到浏览器外或组件卸载后不会残留全局状态。
 */
const stopSidebarResize = () => {
  sidebarResizeSession = null
  sidebarResizing.value = false
  document.body.classList.remove('sidebar-resizing')
  window.removeEventListener('pointermove', handleSidebarResize)
  window.removeEventListener('pointerup', finishSidebarResize)
  window.removeEventListener('pointercancel', finishSidebarResize)
}

const handleSidebarResize = event => {
  if (!sidebarResizeSession || event.pointerId !== sidebarResizeSession.pointerId) return

  sidebarWidth.value = calculateSidebarWidth(
    sidebarResizeSession.startWidth,
    sidebarResizeSession.startPointerX,
    event.clientX
  )
}

const finishSidebarResize = event => {
  if (!sidebarResizeSession || event.pointerId !== sidebarResizeSession.pointerId) return
  saveSidebarLayout()
  stopSidebarResize()
}

/**
 * 从拖拽起点而非上一帧宽度计算位移，避免连续 pointermove 事件产生累计误差。
 */
const startSidebarResize = event => {
  if (event.button !== 0 || sidebarCollapsed.value) return

  event.preventDefault()
  event.currentTarget.setPointerCapture?.(event.pointerId)
  sidebarResizeSession = {
    pointerId: event.pointerId,
    startPointerX: event.clientX,
    startWidth: sidebarWidth.value
  }
  sidebarResizing.value = true
  document.body.classList.add('sidebar-resizing')
  window.addEventListener('pointermove', handleSidebarResize)
  window.addEventListener('pointerup', finishSidebarResize)
  window.addEventListener('pointercancel', finishSidebarResize)
}

/**
 * 为无法使用指针拖拽的用户提供等价的键盘调宽能力。
 */
const handleSidebarResizeKeydown = event => {
  let nextWidth
  if (event.key === 'ArrowLeft') {
    nextWidth = sidebarWidth.value - SIDEBAR_RESIZE_STEP
  } else if (event.key === 'ArrowRight') {
    nextWidth = sidebarWidth.value + SIDEBAR_RESIZE_STEP
  } else if (event.key === 'Home') {
    nextWidth = SIDEBAR_MIN_WIDTH
  } else if (event.key === 'End') {
    nextWidth = SIDEBAR_MAX_WIDTH
  } else {
    return
  }

  event.preventDefault()
  sidebarWidth.value = normalizeSidebarWidth(nextWidth)
  saveSidebarLayout()
}

const resetSidebarWidth = () => {
  sidebarWidth.value = SIDEBAR_DEFAULT_WIDTH
  saveSidebarLayout()
}

/** 用户主动操作才保存个人覆盖；读取系统默认值和拖动宽度都不会产生覆盖。 */
const toggleSidebar = () => {
  if (sidebarResizing.value) stopSidebarResize()
  void sidebarPreference.toggle()
}

// 收集所有被禁用菜单的路径（用于路由守卫拦截）
const collectDisabledPaths = (menus) => {
  const paths = []
  const walk = (list) => {
    list?.forEach(m => {
      if (m.status === '1' && m.path) paths.push(m.path)
      if (m.children?.length) walk(m.children)
    })
  }
  walk(menus)
  return paths
}

// 加载菜单
const loadMenus = async () => {
  menusLoading.value = true
  try {
    const permissions = await getPermissions()
    userStore.setPermissions(permissions || [])
    const res = await getSidebarMenuTree()
    // 保存完整的原始数据，用于提取禁用路径
    const disabledPaths = collectDisabledPaths(res)
    localStorage.setItem('disabled_menu_paths', JSON.stringify(disabledPaths))
    // 后端已按当前用户的角色菜单授权裁剪；前端只处理禁用、按钮和隐藏状态。
    const clean = (menus, parentVisible = '0') => {
      if (!menus) return []
      return menus
        .filter(m => m.status !== '1')
        .filter(m => m.menuType !== 'F')
        .filter(m => parentVisible !== '1' && m.visible !== '1')
        .map(m => {
          const item = { ...m }
          if (item.children && Array.isArray(item.children) && item.children.length > 0) {
            const children = clean(item.children, item.visible)
            item.children = children.length > 0 ? children : undefined
          }
          return item
        })
    }
    const cleaned = clean(res)
    menuTree.value = cleaned
  } catch (error) {
    // 授权菜单刷新失败时清除旧数据，避免搜索继续使用过期的授权结果。
    menuTree.value = []
    console.error('加载菜单失败:', error)
  } finally {
    menusLoading.value = false
  }
}

const handleStorageChange = event => {
  if (event.key === SIDEBAR_MENU_REVISION_KEY) {
    loadMenus()
  } else if (event.key === SIDEBAR_WIDTH_STORAGE_KEY) {
    // 宽度仍按设备同步；折叠必须重新读取当前账号的有效设置。
    const savedLayout = readSidebarLayout()
    sidebarWidth.value = savedLayout.width
  }
}

onMounted(() => {
  loadMenus()
  void sidebarPreference.refresh()
  window.addEventListener(SIDEBAR_MENU_REFRESH_EVENT, loadMenus)
  window.addEventListener('storage', handleStorageChange)
  window.addEventListener('focus', sidebarPreference.refresh)
})

onBeforeUnmount(() => {
  stopSidebarResize()
  window.removeEventListener(SIDEBAR_MENU_REFRESH_EVENT, loadMenus)
  window.removeEventListener('storage', handleStorageChange)
  window.removeEventListener('focus', sidebarPreference.refresh)
})

// 系统默认值刷新也可能收起菜单，此时必须结束尚未完成的宽度拖拽。
watch(sidebarCollapsed, collapsed => {
  if (collapsed && sidebarResizing.value) stopSidebarResize()
})

watch(() => route.fullPath, () => {
  mobileMenuVisible.value = false
})

async function handleCommand(command) {
  if (command === 'toggle-tabs') {
    await toggleWorkspaceMode()
    return
  }
  if (command === 'logout') {
    try {
      if (!(await workspace.canDiscard(workspace.tabs.map(tab => tab.key)))) return
      await ElMessageBox.confirm('退出后需要重新输入账号和密码才能进入系统。', '退出登录', {
        type: 'warning',
        confirmButtonText: '确认退出',
        cancelButtonText: '取消'
      })
      
      // 调用退出登录接口
      await logout().catch(() => {})
      
      // 清除登录状态
      userStore.logout()
      
      ElMessage.success('已退出登录')
      
      // 跳转到登录页
      router.push('/login')
    } catch (error) {
      // 用户取消
    }
  }
}
</script>

<style scoped>
.layout-container {
  width: 100%;
  max-width: 100vw;
  height: 100vh;
  overflow: hidden;
}

.content-container {
  width: 0;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
}

.sidebar {
  background-color: #304156;
  position: relative;
  flex-shrink: 0;
  overflow: visible;
  transition: width 0.18s ease;
}

.sidebar.is-resizing {
  transition: none;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  font-weight: bold;
  border-bottom: 1px solid #1f2d3d;
}

.logo .el-icon {
  margin-right: 10px;
}

.sidebar.is-collapsed .logo .el-icon {
  margin-right: 0;
}

.menu {
  border-right: none;
}

.desktop-menu {
  width: 100%;
  height: calc(100vh - 60px);
  overflow-x: hidden;
  overflow-y: auto;
}

.sidebar-resizer {
  position: absolute;
  z-index: 10;
  top: 0;
  right: -3px;
  width: 7px;
  height: 100%;
  cursor: col-resize;
  touch-action: none;
  outline: none;
}

.sidebar-resizer::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 3px;
  width: 2px;
  background-color: #409eff;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.sidebar-resizer:hover::before,
.sidebar-resizer:focus-visible::before,
.sidebar.is-resizing .sidebar-resizer::before {
  opacity: 1;
}

:global(body.sidebar-resizing) {
  cursor: col-resize;
  user-select: none;
}

.mobile-menu-button {
  display: none;
}

.desktop-sidebar-toggle {
  flex-shrink: 0;
  /* 移动端按钮虽然隐藏，仍会触发 Element Plus 相邻按钮的默认左间距。 */
  margin-left: 0;
}

.header {
  min-width: 0;
  flex-shrink: 0;
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px 0 8px;
}

.header-left {
  display: flex;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  align-items: center;
}

.breadcrumb {
  min-width: 0;
  overflow: hidden;
  margin-left: 12px;
}

.header-right {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 20px;
  margin-left: 16px;
}

.user-info {
  cursor: pointer;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-avatar {
  margin-right: 4px;
}

.main-content {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  background-color: #f0f2f5;
  padding: 10px;
  overflow: hidden;
}

:deep(.mobile-nav-drawer .el-drawer__body) {
  padding: 0;
  background: #304156;
}

.mobile-logo {
  justify-content: flex-start;
  padding: 0 20px;
}

.mobile-menu {
  min-height: calc(100vh - 60px);
}

@media (max-width: 760px) {
  .desktop-sidebar {
    display: none;
  }

  .header {
    height: 52px;
    padding: 0 12px;
  }

  .mobile-menu-button {
    display: inline-flex;
    margin-right: 4px;
  }

  .desktop-sidebar-toggle {
    display: none;
  }

  .breadcrumb {
    margin-left: 0;
  }

  .breadcrumb :deep(.el-breadcrumb__item:not(:last-child)) {
    display: none;
  }

  .breadcrumb :deep(.el-breadcrumb__inner) {
    max-width: 52vw;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .user-info {
    gap: 4px;
    font-size: 0;
  }

  .header-right {
    gap: 10px;
    margin-left: 8px;
  }

  .user-info .el-icon {
    font-size: 14px;
  }

  .user-avatar {
    margin-right: 0;
  }

  .main-content {
    padding: 0;
  }
}
</style>
