<template>
  <el-container class="layout-container">
    <el-aside width="200px" class="sidebar">
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
        <template v-for="menu in menuTree" :key="menu.id">
          <!-- 有子菜单的，渲染为 sub-menu -->
          <el-sub-menu v-if="menu.children && menu.children.length > 0" :index="menu.path || menu.id">
            <template #title>
              <el-icon v-if="menu.icon"><component :is="iconMap[menu.icon]" /></el-icon>
              <span>{{ menu.menuName }}</span>
            </template>
            <template v-for="child in menu.children" :key="child.id">
              <!-- 子菜单也有 children，递归渲染 sub-menu（支持三级及以上） -->
              <el-sub-menu v-if="child.children && child.children.length > 0" :index="child.path || child.id">
                <template #title>
                  <el-icon v-if="child.icon"><component :is="iconMap[child.icon]" /></el-icon>
                  <span>{{ child.menuName }}</span>
                </template>
                <template v-for="grandchild in child.children" :key="grandchild.id">
                  <el-menu-item :index="grandchild.path">
                    <el-icon v-if="grandchild.icon"><component :is="iconMap[grandchild.icon]" /></el-icon>
                    <span>{{ grandchild.menuName }}</span>
                  </el-menu-item>
                </template>
              </el-sub-menu>
              <el-menu-item v-else :index="child.path">
                <el-icon v-if="child.icon"><component :is="iconMap[child.icon]" /></el-icon>
                <span>{{ child.menuName }}</span>
              </el-menu-item>
            </template>
          </el-sub-menu>
          <!-- 无子菜单的，渲染为 menu-item -->
          <el-menu-item v-else :index="menu.path">
            <el-icon v-if="menu.icon"><component :is="iconMap[menu.icon]" /></el-icon>
            <span>{{ menu.menuName }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
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
                <el-dropdown-item command="profile">个人设置</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { HomeFilled, Share, Box, Setting, User, UserFilled, FolderOpened, Menu, Connection, ArrowDown, OfficeBuilding, Document, Notebook } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { logout } from '@/api/auth'
import { getSidebarMenuTree } from '@/api/system/menu'

const router = useRouter()
const userStore = useUserStore()

const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

// 菜单树
const menuTree = ref([])

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
    const res = await getSidebarMenuTree()
    // 保存完整的原始数据，用于提取禁用路径
    const disabledPaths = collectDisabledPaths(res)
    localStorage.setItem('disabled_menu_paths', JSON.stringify(disabledPaths))
    // 递归清洗：只过滤禁用(status=1)菜单；隐藏(visible=1)菜单不显示在侧边栏但保留路由
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

async function handleCommand(command) {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        type: 'warning'
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
  } else if (command === 'profile') {
    ElMessage.info('个人设置功能开发中...')
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

.header {
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  display: flex;
  align-items: center;
  justify-content: flex-end;
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
  background-color: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}
</style>
