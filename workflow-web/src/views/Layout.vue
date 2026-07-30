<template>
  <el-container class="layout-container">
    <el-aside width="200px" class="sidebar desktop-sidebar">
      <div class="logo">
        <el-icon size="24"><Connection /></el-icon>
        <span>流程配置系统</span>
      </div>
      <el-menu
        :default-active="$route.path"
        router
        class="menu"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <sidebar-menu-item
          v-for="menu in menuTree"
          :key="menu.id"
          :menu="menu"
          :icon-map="iconMap"
        />
      </el-menu>
    </el-aside>
    <el-container>
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
          <el-breadcrumb separator="/" class="breadcrumb" v-if="breadcrumb.length > 0">
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
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view />
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
        :default-active="$route.path"
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
          :icon-map="iconMap"
        />
      </el-menu>
    </el-drawer>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { HomeFilled, Share, Box, Setting, User, UserFilled, FolderOpened, Menu, Connection, ArrowDown, OfficeBuilding, Document, Notebook } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { getPermissions, logout } from '@/api/auth'
import { getSidebarMenuTree } from '@/api/system/menu'
import SidebarMenuItem from '@/components/SidebarMenuItem.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const mobileMenuVisible = ref(false)

const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

// 菜单树
const menuTree = ref([])

// 根据当前路由生成面包屑
const breadcrumb = computed(() => {
  const target = route.path
  if (!target || !menuTree.value || menuTree.value.length === 0) {
    return []
  }

  const findChain = (menus, parents = []) => {
    for (const m of menus) {
      const chain = [...parents, m]
      if (m.path && m.path === target) {
        return chain
      }
      if (m.children && m.children.length > 0) {
        const res = findChain(m.children, chain)
        if (res) return res
      }
    }
    return null
  }

  return findChain(menuTree.value) || []
})

// 图标映射（将菜单配置中的图标名映射到 Element Plus 图标组件）
const iconMap = {
  HomeFilled,
  Share,
  Box,
  Setting,
  User,
  UserFilled,
  FolderOpened,
  Menu,
  Connection,
  ArrowDown,
  OfficeBuilding,
  Document,
  Notebook
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
    console.error('加载菜单失败:', error)
  }
}

onMounted(() => {
  loadMenus()
})

watch(() => route.fullPath, () => {
  mobileMenuVisible.value = false
})

async function handleCommand(command) {
  if (command === 'logout') {
    try {
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
  height: 100vh;
}

.sidebar {
  background-color: #304156;
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

.menu {
  border-right: none;
}

.mobile-menu-button {
  display: none;
}

.header {
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
}

.breadcrumb {
  margin-left: 20px;
}

.header-right {
  display: flex;
  align-items: center;
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
  min-width: 0;
  background-color: #f0f2f5;
  padding: 10px;
  overflow-y: auto;
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
