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
        <el-menu-item index="/home">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </el-menu-item>
        <el-menu-item index="/process">
          <el-icon><Share /></el-icon>
          <span>流程管理</span>
        </el-menu-item>
        <el-menu-item index="/process-center">
          <el-icon><Bell /></el-icon>
          <span>流程中心</span>
        </el-menu-item>
        <el-menu-item index="/view-engine">
          <el-icon><View /></el-icon>
          <span>视图引擎</span>
        </el-menu-item>
        <el-menu-item index="/report-engine">
          <el-icon><DataLine /></el-icon>
          <span>报表引擎</span>
        </el-menu-item>
        <el-menu-item index="/service-orchestration">
          <el-icon><Link /></el-icon>
          <span>服务编排</span>
        </el-menu-item>
        <el-menu-item index="/script-engine">
          <el-icon><EditPen /></el-icon>
          <span>脚本引擎</span>
        </el-menu-item>
        <el-sub-menu index="/entity">
          <template #title>
            <el-icon><Box /></el-icon>
            <span>实体管理</span>
          </template>
          <el-menu-item index="/entity">
            <span>实体列表</span>
          </el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="/system">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/system/user">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="/system/role">
            <el-icon><UserFilled /></el-icon>
            <span>角色管理</span>
          </el-menu-item>
          <el-menu-item index="/system/group">
            <el-icon><FolderOpened /></el-icon>
            <span>用户组管理</span>
          </el-menu-item>
          <el-menu-item index="/system/org">
            <el-icon><OfficeBuilding /></el-icon>
            <span>组织部门管理</span>
          </el-menu-item>
          <el-menu-item index="/system/menu">
            <el-icon><Menu /></el-icon>
            <span>菜单管理</span>
          </el-menu-item>
        </el-sub-menu>
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { HomeFilled, Share, Box, Setting, User, UserFilled, FolderOpened, Menu, Connection, ArrowDown, OfficeBuilding, Bell, View, DataLine, Link, EditPen } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { logout } from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()

const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

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
