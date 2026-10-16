import assert from 'node:assert/strict'
import { buildMenuSearchOptions, filterMenuSearchOptions } from '../menuSearch.js'

const page = (id, menuName, path, extra = {}) => ({ id, menuName, path, menuType: 'C', ...extra })
const menus = [
  page('home', '首页', '/home', { menuType: 'M' }),
  {
    id: 'config', menuName: '配置管理', menuType: 'M', path: '/config',
    children: [
      page('process', '流程管理', '/process', {
        children: [{ id: 'create', menuName: '新增流程', menuType: 'F', path: '/process/create' }]
      }),
      page('entity', '实体配置', '/entity'),
      page('disabled', '禁用菜单', '/disabled', { status: '1' }),
      page('hidden', '隐藏菜单', '/hidden', { visible: '1' })
    ]
  },
  {
    id: 'manual', menuName: '用户手册', menuType: 'M',
    children: [
      page('manual-process', '流程管理', '/manual/process'),
      page('embed', '集成应用与 Embed', '/manual/embed-integration'),
      page('dynamic', '项目列表', '/entity-list/project/default?status=active#list')
    ]
  },
  { menuName: '隐藏目录', menuType: 'M', visible: '1', children: [page('hidden-child', '子菜单', '/hidden-child')] },
  { menuName: '禁用目录', menuType: 'M', status: '1', children: [page('disabled-child', '子菜单', '/disabled-child')] },
  { menuName: '空目录', menuType: 'M', path: '', children: [] },
  page('no-path', '无路径', ''),
  page('placeholder', '占位项', '#'),
  page('external', '外链', 'https://example.com'),
  page('relative-external', '协议相对地址', '//example.com'),
  page('duplicate', '重复入口', '/home')
]
const originalMenus = structuredClone(menus)
const options = buildMenuSearchOptions(menus)

assert.deepEqual(options.map(option => option.path), [
  '/home', '/process', '/entity', '/manual/process', '/manual/embed-integration',
  '/entity-list/project/default?status=active#list'
], '仅收录可跳转的末级菜单（包括目录类型首页），过滤不可见子树、无路径目录、按钮和重复路径')
assert.deepEqual(menus, originalMenus, '建立搜索索引不能修改侧栏菜单数据')
assert.equal(options.find(option => option.path === '/process').parentLabel, '配置管理')
assert.equal(options.find(option => option.path === '/manual/process').label, '用户手册 / 流程管理')

assert.deepEqual(filterMenuSearchOptions(options, '流程').map(option => option.path), [
  '/process', '/manual/process'
], '同名菜单按所属层级分别保留，跳转路径不能混淆')
assert.deepEqual(filterMenuSearchOptions(options, '  用户手册   流程 ').map(option => option.path), [
  '/manual/process'
], '支持按父级和菜单名称组合匹配')
assert.equal(filterMenuSearchOptions(options, 'EMBED')[0].path, '/manual/embed-integration')
assert.deepEqual(filterMenuSearchOptions(options, ' \t '), options)
assert.deepEqual(filterMenuSearchOptions(options, '不存在的菜单'), [])

// 模拟普通用户仅有首页和手册授权；搜索不能从静态路由或其他用户数据补出菜单。
const restrictedMenus = [menus[0], menus[2]]
const restrictedOptions = buildMenuSearchOptions(restrictedMenus)
assert.deepEqual(filterMenuSearchOptions(restrictedOptions, '配置管理'), [])
assert.deepEqual(filterMenuSearchOptions(restrictedOptions, '流程').map(option => option.path), ['/manual/process'])
assert.deepEqual(buildMenuSearchOptions([]), [], '权限收回后重新计算不得保留旧菜单')
assert.deepEqual(buildMenuSearchOptions(null), [])

console.log('menu search tests passed')
