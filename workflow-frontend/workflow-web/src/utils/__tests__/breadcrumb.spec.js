import assert from 'node:assert/strict'
import {
  buildBreadcrumb,
  findDeepestMenuChain,
  getActiveMenuPath
} from '../breadcrumb.js'

const menus = [
  {
    id: 'config',
    menuName: '配置管理',
    path: '/entity',
    children: [
      {
        id: 'entity',
        menuName: '实体配置',
        path: '/entity'
      },
      {
        id: 'process',
        menuName: '流程管理',
        path: '/process'
      }
    ]
  },
  {
    id: 'system',
    menuName: '系统管理',
    path: '',
    children: [
      {
        id: 'menu',
        menuName: '菜单管理',
        path: '/system/menu'
      }
    ]
  }
]

assert.deepEqual(
  findDeepestMenuChain(menus, '/entity').map(item => item.menuName),
  ['配置管理', '实体配置'],
  '父子菜单路径相同时必须返回最深菜单链'
)

assert.deepEqual(
  buildBreadcrumb(menus, {
    path: '/process',
    name: 'ProcessList',
    meta: { title: '流程管理' }
  }).map(item => item.menuName),
  ['配置管理', '流程管理']
)

assert.deepEqual(
  buildBreadcrumb(menus, {
    path: '/entity/design/1',
    name: 'EntityDesign',
    meta: { title: '实体设计', activeMenu: '/entity' }
  }).map(item => item.menuName),
  ['配置管理', '实体配置', '实体设计']
)

assert.deepEqual(
  buildBreadcrumb(menus, {
    path: '/process/design/1',
    name: 'ProcessDesign',
    meta: { title: '流程设计' }
  }).map(item => item.menuName),
  ['配置管理', '流程管理', '流程设计'],
  '未显式配置所属菜单时应使用最长路径前缀'
)

assert.deepEqual(
  buildBreadcrumb(menus, {
    path: '/entity/reports',
    name: 'EntityReports',
    meta: { title: '实体报表' }
  }).map(item => item.menuName),
  ['配置管理', '实体配置', '实体报表'],
  '父子菜单前缀相同时必须优先返回更深菜单链'
)

assert.deepEqual(
  buildBreadcrumb(menus, {
    path: '/unknown',
    name: 'Unknown',
    meta: { title: '未知页面' }
  }).map(item => item.menuName),
  ['未知页面'],
  '菜单树没有对应项时仍应显示当前路由标题'
)

assert.equal(
  getActiveMenuPath({
    path: '/entity/design/1',
    meta: { activeMenu: '/entity' }
  }),
  '/entity'
)

console.log('breadcrumb tests passed')
