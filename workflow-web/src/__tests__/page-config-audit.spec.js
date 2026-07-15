import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'

const root = process.cwd()
const routerSource = readFileSync(path.join(root, 'src/router/index.js'), 'utf8')
const viewFiles = [...routerSource.matchAll(/import\('@\/views\/([^']+\.vue)'\)/g)].map((match) => match[1])

assert.ok(viewFiles.length >= 20, '应发现主要页面路由')
viewFiles.forEach((viewFile) => {
  const fullPath = path.join(root, 'src/views', viewFile)
  assert.equal(existsSync(fullPath), true, `路由组件不存在: ${viewFile}`)
  const source = readFileSync(fullPath, 'utf8')
  assert.match(source, /<template>[\s\S]*<\/template>/, `页面缺少 template: ${viewFile}`)
  assert.match(source, /<script[\s\S]*>[\s\S]*<\/script>/, `页面缺少 script: ${viewFile}`)
})

;['/home', '/process', '/entity', '/system/menu', '/system/user', '/system/role', '/system/group', '/system/org', '/system/dict'].forEach((routePath) => {
  const routePattern = new RegExp(`path:\\s*'${routePath.replaceAll('/', '\\/')}'[\\s\\S]{0,500}meta:\\s*\\{\\s*title:\\s*'[^']+'`)
  assert.match(routerSource, routePattern, `核心页面缺少标题: ${routePath}`)
})

const dynamicRoutePaths = [...routerSource.matchAll(/path:\s*'([^']*:[^']+)'/g)]
  .map((match) => match[1])
  .filter((routePath) => !routePath.includes('pathMatch'))
assert.deepEqual(
  dynamicRoutePaths.sort(),
  [
    '/entity-form/design/:id',
    '/entity-form/list-by-entity/:entityId',
    '/entity-list-config/:entityId',
    '/entity-list-config/design/:id',
    '/entity/data/:code',
    '/entity/design/:id',
    '/entity/list/:entityCode',
    '/process/design/:id?',
    '/process/form/:nodeId',
    '/process/progress/:instanceId'
  ].sort()
)

const dynamicRuntimeFiles = [
  'src/views/entity/EntityDataList.vue',
  'src/views/entity/components/EntityDataSearchForm.vue',
  'src/views/entity/components/EntityDataTable.vue',
  'src/views/entity/components/EntityDataFormDialog.vue',
  'src/components/FormFieldRenderer.vue',
  'src/components/ListCellRenderer.vue',
  'src/shared/form-runtime/index.js',
  'src/shared/list-runtime/index.js'
]

dynamicRuntimeFiles.forEach((file) => {
  assert.equal(existsSync(path.join(root, file)), true, `动态配置运行时文件不存在: ${file}`)
})

const entityDataList = readFileSync(path.join(root, 'src/views/entity/EntityDataList.vue'), 'utf8')
assert.match(entityDataList, /customListComponent[\s\S]*hasCustomListComponent/, '动态实体列表应支持自定义列表组件')
assert.match(entityDataList, /queryFields[\s\S]*listFields[\s\S]*toolbarButtons[\s\S]*rowActionButtons/s, '动态实体列表应派生查询、表格和按钮配置')

const formFieldRegistry = readFileSync(path.join(root, 'src/components/form-fields/index.js'), 'utf8')
;['text', 'textarea', 'number', 'select', 'radio', 'checkbox', 'date', 'switch', 'file', 'reference', 'sub_form'].forEach((type) => {
  assert.ok(formFieldRegistry.includes(type), `表单运行时缺少字段类型线索: ${type}`)
})

console.log('page configuration audit passed')
