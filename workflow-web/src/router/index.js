import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
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
      // 首页
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
router.beforeEach((to, from, next) => {
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
  } else if (to.path === '/login' && isLoggedIn) {
    // 已登录但访问登录页，跳转到首页
    next('/')
  } else {
    next()
  }
})

export default router
