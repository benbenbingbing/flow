import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import * as vue from 'vue'
import * as model from '../src/shared/memberChangeModel.js'
import * as api from '../src/api/memberChange.js'
import * as extensionHost from '../../workflow-extension-sdk/src/host/index.js'
import * as mobileContract from '../../workflow-extension-sdk/src/contracts/mobile-form.js'
import * as configRuntime from '@flow/workflow-core/config-runtime'

const packageRoot = new URL('../', import.meta.url)
const emptyComponent = { render: () => null }

/** 编译真实 SFC 的 setup，在 Vue 自定义渲染器中运行；仅替换视觉组件，保留宿主注入、watch 和业务 API。 */
function loadForm(platform) {
  const filename = fileURLToPath(new URL(`src/${platform}/forms/ProjectMemberChangeForm.vue`, packageRoot))
  const { descriptor } = parse(readFileSync(filename, 'utf8'), { filename })
  let code = compileScript(descriptor, { id: `member-form-${platform}` }).content
  const imports = {
    vue,
    '@flow/biz-project/shared/memberChangeModel': model,
    '../../shared/memberChangeModel.js': model,
    '../../api/memberChange.js': api,
    '@flow/workflow-extension-sdk/host': extensionHost,
    '@flow/workflow-extension-sdk/contracts/mobile-form': mobileContract,
    '@flow/workflow-mobile-ui': { MobileExtensionFormFields: emptyComponent },
    '@flow/workflow-core/config-runtime': configRuntime,
    '@element-plus/icons-vue': { Loading: emptyComponent },
    vant: { Button: emptyComponent, NoticeBar: emptyComponent },
    './ProjectMemberChangeSections.vue': { default: emptyComponent }
  }
  code = code.replace(/^import ([\s\S]*?) from ['"]([^'"]+)['"]\n/gm, (_, binding, specifier) => {
    assert.ok(imports[specifier], `测试必须明确解析依赖：${specifier}`)
    const target = `imports[${JSON.stringify(specifier)}]`
    if (binding.startsWith('{')) return `const ${binding.replace(/\bas\b/g, ':')} = ${target}\n`
    return `const ${binding} = ${target}.default\n`
  }).replace('export default ', 'return ')
  const component = new Function('imports', code)(imports)
  component.render = () => null
  return component
}

function mountForm(platform, host, props) {
  const renderer = vue.createRenderer({
    createComment: () => ({}), createText: () => ({}), createElement: () => ({}),
    insert() {}, remove() {}, setText() {}, setElementText() {}, patchProp() {},
    parentNode: () => null, nextSibling: () => null
  })
  const app = renderer.createApp(loadForm(platform), props)
  extensionHost.provideExtensionHost(app, host)
  app.mount({})
  return { app, state: app._instance.setupState }
}

async function flush() {
  await new Promise(resolve => setImmediate(resolve))
  await vue.nextTick()
}

for (const platform of ['pc', 'mobile']) {
  test(`${platform} 成员表单通过真实 SDK 注入的宿主实体 API 加载上下文`, async () => {
    const calls = []
    const host = {
      components: { FormFieldRendererLinkage: emptyComponent },
      entityDataApi: {
        async getDetail() { assert.fail('JOIN 不查询成员详情') },
        async getList(entity, query) {
          calls.push([entity, query.user_id])
          return [{ id: 'other-member', status: 'ACTIVE', data: { allocation_percentage: 25 } }]
        }
      }
    }
    const { app, state } = mountForm(platform, host, {
      mode: 'create', modelValue: { project_id: 'project-one', target_user_id: 'user-one', new_allocation_percentage: 50 },
      services: {}
    })
    try {
      await flush()
      assert.deepEqual(calls, [['project_member', 'user-one']])
      assert.equal(state.memberContext.allocation.total, 75)
      assert.equal(state.memberContext.allocation.available, 75)
      assert.equal(state.contextError, '')
    } finally { app.unmount() }
  })
}

test('移动表单切换成员后忽略过期查询，防止错误的跨项目投入覆盖最新选择', async () => {
  const resolvers = new Map()
  const host = { entityDataApi: {
    async getDetail() { assert.fail('JOIN 不查询成员详情') },
    getList(_entity, query) { return new Promise(resolve => resolvers.set(query.user_id, resolve)) }
  } }
  const { app, state } = mountForm('mobile', host, {
    mode: 'create', modelValue: { project_id: 'p1', target_user_id: 'old-user', new_allocation_percentage: 30 }
  })
  try {
    state.value.target_user_id = 'new-user'
    await vue.nextTick()
    resolvers.get('new-user')([{ id: 'new-record', status: 'ACTIVE', data: { allocation_percentage: 20 } }])
    await flush()
    resolvers.get('old-user')([{ id: 'old-record', status: 'ACTIVE', data: { allocation_percentage: 90 } }])
    await flush()
    assert.equal(state.memberContext.allocation.total, 50)
    assert.equal(state.loading, false)
  } finally { app.unmount() }
})

test('业务组件迁移后仍全部可编译，且独立平台入口不加载另一端组件', () => {
  const config = JSON.parse(readFileSync(new URL('package.json', packageRoot), 'utf8'))
  assert.equal(config.exports['.'], undefined, '禁止用总入口同时导入 PC 和移动组件')
  for (const [entry, target] of Object.entries(config.exports)) {
    const source = target.replace('./dist/', 'src/')
    const content = readFileSync(new URL(source, packageRoot), 'utf8')
    if (!source.endsWith('.vue')) continue
    const filename = fileURLToPath(new URL(source, packageRoot))
    const { descriptor, errors } = parse(content, { filename })
    assert.deepEqual(errors, [])
    const script = compileScript(descriptor, { id: entry })
    assert.deepEqual(compileTemplate({ source: descriptor.template.content, id: entry, filename, compilerOptions: { bindingMetadata: script.bindings } }).errors, [])
    assert.doesNotMatch(content, /(?:from|import)\s*['"]@\//)
    if (entry.startsWith('./pc/')) assert.doesNotMatch(content, /from ['"](?:vant|@flow\/workflow-mobile-ui|.*\/mobile\/)/)
    if (entry.startsWith('./mobile/')) assert.doesNotMatch(content, /from ['"](?:element-plus|@element-plus\/|.*\/pc\/)/)
  }
})

test('移动成员表单上下文失败时阻止提交，查看模式不回写审批路由或表单值', async () => {
  const host = { entityDataApi: {
    async getDetail() { throw new Error('无权读取成员') },
    async getList() { throw new Error('无权读取成员') }
  } }
  const errors = mountForm('mobile', host, {
    mode: 'create', modelValue: { project_id: 'p1', target_user_id: 'u1' }
  })
  try {
    await flush()
    assert.equal(errors.state.contextError, '无权读取成员')
    assert.equal(await errors.app._instance.exposed.validate(), false)
  } finally { errors.app.unmount() }

  let changed = 0
  const readonly = mountForm('mobile', { entityDataApi: { async getDetail() { return null }, async getList() { return [] } } }, {
    mode: 'view', modelValue: { project_id: 'p1', target_user_id: 'u1', name: '已保存单据名' },
    'onUpdate:modelValue': () => changed++
  })
  try {
    await flush()
    assert.equal(changed, 0)
    assert.equal(await readonly.app._instance.exposed.validate(), true)
    assert.equal(changed, 0)
  } finally { readonly.app.unmount() }
})
