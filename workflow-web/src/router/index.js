import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getPermissions } from '@/api/auth'
import { restoreAuthSession } from '@/shared/request'
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
  {
    path: '/change-password',
    name: 'ChangePassword',
    component: () => import('@/views/ChangePassword.vue'),
    meta: { title: '修改密码' }
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
        meta: { title: '流程设计', activeMenu: '/process' }
      },
      {
        path: '/process/sla-policies',
        name: 'TaskSlaPolicyManagement',
        component: () => import('@/views/process/TaskSlaPolicyManagement.vue'),
        meta: {
          title: 'SLA策略',
          requiredPermissions: ['process:sla-policy:view']
        }
      },
      {
        path: '/process/sla-monitor',
        name: 'TaskSlaMonitor',
        component: () => import('@/views/process/TaskSlaMonitor.vue'),
        meta: {
          title: 'SLA监控',
          requiredPermissions: ['process:sla:monitor']
        }
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
        meta: { title: '实体设计', activeMenu: '/entity' }
      },
      {
        path: '/entity/data/:code',
        name: 'LegacyEntityDataRedirect',
        component: () => import('@/views/entity/LegacyEntityDataRedirect.vue'),
        meta: { title: '打开业务数据', activeMenu: '/entity', deprecated: true }
      },
      // entityCode + listKey 驱动的通用实体列表
      {
        path: '/entity-list/:entityCode/:listKey',
        name: 'EntityListRuntime',
        component: () => import('@/views/entity/EntityDataList.vue'),
        meta: { title: '实体数据列表' }
      },
      // 实体列表配置
      {
        path: '/entity-list-config/:entityId',
        name: 'EntityListConfig',
        component: () => import('@/views/EntityListConfig.vue'),
        meta: { title: '实体列表配置', activeMenu: '/entity' }
      },
      {
        path: '/entity-list-config/design/:id',
        name: 'EntityListConfigDesign',
        component: () => import('@/views/EntityListConfigDesign.vue'),
        meta: { title: '列表配置设计', activeMenu: '/entity' }
      },
      // 实体表单管理
      {
        path: '/entity-form/list-by-entity/:entityId',
        name: 'EntityFormList',
        component: () => import('@/views/EntityFormList.vue'),
        meta: { title: '实体表单', activeMenu: '/entity' }
      },
      {
        path: '/entity-form/design/:id',
        name: 'EntityFormDesign',
        component: () => import('@/views/EntityFormDesignByEntity.vue'),
        meta: { title: '表单设计', activeMenu: '/entity' }
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
      {
        path: '/manual/open-integration',
        name: 'OpenIntegrationManual',
        component: () => import('@/views/manual/OpenIntegrationManual.vue'),
        meta: { title: '开放集成手册' }
      },
      {
        path: '/manual/interface-service',
        name: 'InterfaceServiceManual',
        component: () => import('@/views/manual/InterfaceServiceManual.vue'),
        meta: { title: '接口服务手册' }
      },
      // 流程进度查看
      {
        path: '/process/progress/:instanceId',
        name: 'ProcessProgress',
        component: () => import('@/views/ProcessProgress.vue'),
        meta: { title: '流程进度', activeMenu: '/process' }
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
        path: '/system/work-calendars',
        name: 'WorkCalendarManagement',
        component: () => import('@/views/system/WorkCalendarManagement.vue'),
        meta: {
          title: '工作日历',
          requiredPermissions: ['system:work-calendar:view']
        }
      },
      {
        path: '/system/audit-logs',
        name: 'SystemAudit',
        component: () => import('@/views/system/SystemAudit.vue'),
        meta: {
          title: '系统日志',
          requiredPermissions: ['system:audit:list']
        }
      },
      {
        path: '/system/extensions',
        name: 'ExtensionManagement',
        component: () => import('@/views/system/ExtensionManagement.vue'),
        meta: {
          title: '扩展管理',
          requiredPermissions: ['system:extension:list']
        }
      },
      {
        path: '/system/open-integration',
        name: 'OpenIntegration',
        component: () => import('@/views/system/OpenIntegration.vue'),
        meta: {
          title: '开放集成',
          requiredPermissions: ['system:integration:view']
        }
      },
      {
        path: '/system/interface-services',
        name: 'InterfaceServices',
        component: () => import('@/views/system/InterfaceServices.vue'),
        meta: {
          title: '接口服务',
          requiredPermissions: ['system:interface-service:list']
        }
      },
      {
        path: '/system/list-column-templates',
        name: 'ListColumnTemplateManagement',
        component: () => import('@/views/system/ListColumnTemplateManagement.vue'),
        meta: {
          title: '列表列模板',
          requiredPermissions: ['system:list-column-template:view']
        }
      },
      {
        path: '/system/entity-versions',
        name: 'EntityVersionManagement',
        component: () => import('@/views/system/EntityVersionManagement.vue'),
        meta: {
          title: '数据版本',
          requiredPermissions: ['entity:version:config:list']
        }
      },
      {
        path: '/system/config-migration',
        name: 'ConfigMigration',
        component: () => import('@/views/system/ConfigMigration.vue'),
        meta: {
          title: '配置迁移',
          developerOnly: true,
          requiredPermissions: ['config-migration:list']
        }
      },
      {
        path: '/system/dev-guide',
        name: 'DevGuide',
        component: () => import('@/views/system/DevGuide.vue'),
        meta: { title: '列表字段扩展', developerOnly: true }
      },
      {
        path: '/system/custom-list-guide',
        name: 'CustomListGuide',
        component: () => import('@/views/system/CustomListGuide.vue'),
        meta: { title: '自定义列表组件', developerOnly: true }
      },
      {
        path: '/system/custom-form-guide',
        name: 'CustomFormGuide',
        component: () => import('@/views/system/CustomFormGuide.vue'),
        meta: { title: '自定义表单组件', developerOnly: true }
      },
      {
        path: '/system/flow-action-guide',
        name: 'FlowActionGuide',
        component: () => import('@/views/system/FlowActionGuide.vue'),
        meta: { title: '流程动作', developerOnly: true }
      }
    ]
  },
  // 未知地址保留原路径并给出明确反馈，避免静默跳转造成误解。
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()

  await restoreAuthSession()
  
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
    next(userStore.userInfo?.passwordResetRequired ? '/change-password' : '/')
    return
  }

  if (isLoggedIn && userStore.userInfo?.passwordResetRequired && to.path !== '/change-password') {
    next('/change-password')
    return
  }

  if (!isPublic && userStore.permissions.length === 0) {
    try {
      const permissions = await getPermissions()
      userStore.setPermissions(permissions || [])
    } catch {
      // 请求拦截器负责展示鉴权失败；这里按最小权限继续判断。
    }
  }

  if (to.meta?.developerOnly) {
    const roleCodes = userStore.roles.map(role => typeof role === 'string' ? role : role?.roleCode)
    const requiredPermissions = to.meta.requiredPermissions || []
    const hasRequiredPermission = requiredPermissions.some(permission =>
      userStore.permissions.includes(permission)
    )
    const canAccessDeveloperArea = userStore.isSuperAdmin
      || roleCodes.includes('admin')
      || userStore.permissions.includes('*')
      || hasRequiredPermission
    if (!canAccessDeveloperArea) {
      ElMessage.warning('该页面面向系统管理员和开发人员，当前账号无权访问')
      next('/home')
      return
    }
  }

  if (!to.meta?.developerOnly && to.meta?.requiredPermissions?.length) {
    const hasRequiredPermission = userStore.isSuperAdmin
      || userStore.permissions.includes('*')
      || to.meta.requiredPermissions.some(permission =>
        userStore.permissions.includes(permission)
      )
    if (!hasRequiredPermission) {
      ElMessage.warning('当前账号无权访问该页面')
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
