import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getPermissions } from '@/api/auth'
import Layout from '@/views/Layout.vue'

/**
 * 路由配置
 */
const routes = [
  // 登录页面
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true }
  },
  // 主布局
  {
    path: '/',
    component: Layout,
    redirect: '/home',
    children: [
      // 首页 - 待办/已办/我发起的
      {
        path: '/home',
        name: 'Home',
        component: () => import('@/views/Home.vue'),
        meta: { title: '首页' }
      },
      // 流程管理
      {
        path: '/process',
        name: 'ProcessList',
        component: () => import('@/views/ProcessList.vue'),
        meta: { title: '流程管理' }
      },
      {
        path: '/process/design/:id?',
        name: 'ProcessDesign',
        component: () => import('@/views/ProcessDesign.vue'),
        meta: { title: '流程设计' }
      },
      {
        path: '/process/form/:nodeId',
        name: 'FormDesign',
        component: () => import('@/views/FormDesign.vue'),
        meta: { title: '表单设计' }
      },
      // 实体管理
      {
        path: '/entity',
        name: 'EntityList',
        component: () => import('@/views/EntityList.vue'),
        meta: { title: '实体管理' }
      },
      {
        path: '/entity/design/:id',
        name: 'EntityDesign',
        component: () => import('@/views/EntityDesign.vue'),
        meta: { title: '实体设计' }
      },
      {
        path: '/entity/data/:code',
        name: 'EntityDataManage',
        component: () => import('@/views/EntityDataManage.vue'),
        meta: { title: '数据管理' }
      },
      // 通用实体数据列表（用于菜单跳转）
      {
        path: '/entity/list/:entityCode',
        name: 'EntityDataList',
        component: () => import('@/views/entity/EntityDataList.vue'),
        meta: { title: '实体数据列表' }
      },
      // 实体列表配置
      {
        path: '/entity-list-config/:entityId',
        name: 'EntityListConfig',
        component: () => import('@/views/EntityListConfig.vue'),
        meta: { title: '实体列表配置' }
      },
      {
        path: '/entity-list-config/design/:id',
        name: 'EntityListConfigDesign',
        component: () => import('@/views/EntityListConfigDesign.vue'),
        meta: { title: '列表配置设计' }
      },
      // 实体表单管理
      {
        path: '/entity-form/list-by-entity/:entityId',
        name: 'EntityFormList',
        component: () => import('@/views/EntityFormList.vue'),
        meta: { title: '实体表单' }
      },
      {
        path: '/entity-form/design/:id',
        name: 'EntityFormDesign',
        component: () => import('@/views/EntityFormDesignByEntity.vue'),
        meta: { title: '表单设计' }
      },
      // 用户手册
      {
        path: '/manual',
        redirect: '/manual/entity'
      },
      {
        path: '/manual/entity',
        name: 'EntityManual',
        component: () => import('@/views/manual/EntityManual.vue'),
        meta: { title: '实体配置手册' }
      },
      {
        path: '/manual/process',
        name: 'ProcessManual',
        component: () => import('@/views/manual/ProcessManual.vue'),
        meta: { title: '流程管理手册' }
      },
      // 流程进度查看
      {
        path: '/process/progress/:instanceId',
        name: 'ProcessProgress',
        component: () => import('@/views/ProcessProgress.vue'),
        meta: { title: '流程进度' }
      },
      // 系统管理
      {
        path: '/system/menu',
        name: 'MenuManagement',
        component: () => import('@/views/system/Menu.vue'),
        meta: { title: '菜单管理' }
      },
      {
        path: '/system/user',
        name: 'UserManagement',
        component: () => import('@/views/system/User.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: '/system/role',
        name: 'RoleManagement',
        component: () => import('@/views/system/Role.vue'),
        meta: { title: '角色管理' }
      },
      {
        path: '/system/group',
        name: 'GroupManagement',
        component: () => import('@/views/system/Group.vue'),
        meta: { title: '用户组管理' }
      },
      {
        path: '/system/org',
        name: 'OrganizationManagement',
        component: () => import('@/views/system/Organization.vue'),
        meta: { title: '组织部门管理' }
      },
      {
        path: '/system/dict',
        name: 'DictManagement',
        component: () => import('@/views/system/Dict.vue'),
        meta: { title: '字典设置' }
      },
      {
        path: '/system/config-migration',
        name: 'ConfigMigration',
        component: () => import('@/views/system/ConfigMigration.vue'),
        meta: { title: '配置迁移' }
      },
      {
        path: '/system/dev-guide',
        name: 'DevGuide',
        component: () => import('@/views/system/DevGuide.vue'),
        meta: { title: '列表字段扩展' }
      },
      {
        path: '/system/custom-list-guide',
        name: 'CustomListGuide',
        component: () => import('@/views/system/CustomListGuide.vue'),
        meta: { title: '自定义列表组件' }
      },
      {
        path: '/system/custom-form-guide',
        name: 'CustomFormGuide',
        component: () => import('@/views/system/CustomFormGuide.vue'),
        meta: { title: '自定义表单组件' }
      },
      {
        path: '/system/flow-action-guide',
        name: 'FlowActionGuide',
        component: () => import('@/views/system/FlowActionGuide.vue'),
        meta: { title: '流程动作' }
      }
    ]
  },
  // 404 重定向
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  
  // 恢复用户信息（刷新页面后）
  if (!userStore.userInfo) {
    userStore.restoreUserInfo()
  }
  
  // 判断是否需要登录
  const isPublic = to.meta?.public === true
  const isLoggedIn = userStore.isLoggedIn
  
  if (!isPublic && !isLoggedIn) {
    // 未登录且访问需要登录的页面，跳转到登录页
    next('/login')
    return
  }
  
  if (to.path === '/login' && isLoggedIn) {
    // 已登录但访问登录页，跳转到首页
    next('/')
    return
  }

  if (to.name === 'EntityDataList') {
    const entityCode = String(to.params?.entityCode || '').toLowerCase()
    const requiredPermission = `entity:${entityCode}:list`
    if (!userStore.permissions.includes(requiredPermission)) {
      try {
        const permissions = await getPermissions()
        userStore.setPermissions(permissions || [])
      } catch (e) {}
    }
    if (!userStore.permissions.includes(requiredPermission)) {
      ElMessage.warning('没有权限访问该实体列表')
      next('/home')
      return
    }
  }
  
  // 拦截被禁用的菜单路径
  try {
    const disabledPaths = JSON.parse(localStorage.getItem('disabled_menu_paths') || '[]')
    const isDisabled = disabledPaths.some(path => {
      // 支持精确匹配和子路径匹配（如 /entity 匹配 /entity/list/project_nitiation）
      return to.path === path || to.path.startsWith(path + '/')
    })
    if (isDisabled) {
      ElMessage.warning('该菜单已被禁用，无法访问')
      next('/home')
      return
    }
  } catch (e) {}
  
  next()
})

export default router
