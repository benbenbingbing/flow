import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/views/Layout.vue'

/**
 * 路由配置
 */
const routes = [
  {
    path: '/',
    component: Layout,
    redirect: '/process',
    children: [
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
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
