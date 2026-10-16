import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import path from 'node:path'
import { tmpdir } from 'node:os'
import { fileURLToPath } from 'node:url'
import { build } from 'vite'
import { checkBusinessBoundaries, createBusinessBoundaryContext, inspectBusinessDependency, readModuleDependencies } from '../check-business-boundaries.mjs'
import { workflowBoundaryPlugin } from '../workflow-boundary.mjs'

function fixture(t) {
  const root = mkdtempSync(path.join(tmpdir(), 'flow-business-boundaries-'))
  t.after(() => rmSync(root, { recursive: true, force: true }))
  const write = (filename, content) => {
    mkdirSync(path.dirname(path.join(root, filename)), { recursive: true })
    writeFileSync(path.join(root, filename), content)
  }
  const pkg = (name, extra = {}) => {
    write(`packages/${name}/package.json`, JSON.stringify({ name: `@flow/${name}`, type: 'module', exports: { './*': './src/*.js', './private/*': null }, ...extra }))
  }
  pkg('workflow-core')
  pkg('workflow-api')
  pkg('workflow-extension-sdk')
  pkg('workflow-mobile-ui')
  pkg('biz-project', { dependencies: { '@flow/workflow-core': '*', '@flow/workflow-extension-sdk': '*', '@flow/workflow-mobile-ui': '*' }, peerDependencies: { vue: '*', 'element-plus': '*', vant: '*' } })
  pkg('biz-purchase')
  mkdirSync(path.join(root, 'extensions/manifests/biz-project'), { recursive: true })
  mkdirSync(path.join(root, 'extensions/manifests/biz-purchase'), { recursive: true })
  write('config/frontend-modules.json', JSON.stringify({ enabledModules: ['biz-project', 'biz-purchase'] }))
  return { root, write, pkg, check: () => checkBusinessBoundaries(root).errors }
}

test('AST 识别静态导入、重导出、懒加载、require、Vue 外部脚本；不扫描注释字符串', () => {
  const source = `
    // import wrong from '@flow/biz-project/pc/private'
    const text = "import('@flow/biz-project/pc/private')"
    import x from 'vue'
    export { y } from '@flow/workflow-core/public'
    export * from './public.js'
    const lazy = () => import('./lazy.js')
    const common = require('./common.js')
  `
  assert.deepEqual(readModuleDependencies(source, 'entry.js').map(item => item.source), ['vue', '@flow/workflow-core/public', './public.js', './lazy.js', './common.js'])
  assert.deepEqual(readModuleDependencies('<script src="./external.js"></script>', 'Form.vue').map(item => item.source), ['./external.js'])
  assert.deepEqual(readModuleDependencies('<script setup>import x from "vue"</script>', 'Form.vue').map(item => item.source), ['vue'])
})

test('公开包入口、声明的跨业务依赖和同包相对导入可通过', t => {
  const f = fixture(t)
  f.pkg('biz-project', { dependencies: { '@flow/workflow-core': '*', '@flow/biz-purchase': '*' } })
  f.write('packages/biz-project/src/pc/Form.js', "export { value } from '../shared/value.js'; export * from '@flow/biz-purchase/shared/public'; import '@flow/workflow-core/value'")
  f.write('packages/biz-project/src/shared/value.js', 'export const value = 1')
  f.write('packages/biz-purchase/src/shared/public.js', 'export const shared = 1')
  f.write('workflow-web/src/main.js', "import 'virtual:flow-extension-manifest'")
  f.write('workflow-web/src/example.js', "// import '@flow/biz-project/pc/Form'\nconst example = '@flow/biz-project/pc/Form'")
  assert.deepEqual(f.check(), [])
})

for (const [label, filename, source, expected] of [
  ['核心重导出业务', 'packages/workflow-core/src/index.js', "export * from '@flow/biz-project/shared/model'", /产品代码不能依赖业务包/],
  ['API 动态加载业务', 'packages/workflow-api/src/index.js', "export const load = () => import('@flow/biz-project/shared/model')", /产品代码不能依赖业务包/],
  ['SDK 引用业务', 'packages/workflow-extension-sdk/src/index.js', "import '@flow/biz-project/shared/model'", /产品代码不能依赖业务包/],
  ['移动 UI 引用业务', 'packages/workflow-mobile-ui/src/index.js', "export * from '@flow/biz-project/mobile/Form'", /产品代码不能依赖业务包/],
  ['应用直接加载业务', 'workflow-web/src/main.js', "import '@flow/biz-project/pc/Form'", /产品代码不能依赖业务包/],
  ['产品示例依赖业务', 'workflow-mobile/src/extensions/Demo.vue', '<script setup>import x from "@flow/biz-project/shared/model"</script>', /产品代码不能依赖业务包/],
  ['应用相对路径依赖业务', 'workflow-web/src/main.js', "export * from '../../packages/biz-project/src/shared/model.js'", /产品代码不能依赖业务包/],
  ['业务应用别名', 'packages/biz-project/src/pc/Form.vue', '<script setup>import x from "@/components/Private.vue"</script>', /应用私有实现/],
  ['业务应用相对路径', 'packages/biz-project/src/pc/Form.js', "import '../../../../workflow-web/src/private.js'", /应用私有实现/],
  ['业务移动应用相对路径', 'packages/biz-project/src/mobile/Form.js', "import '../../../../workflow-mobile/src/private.js'", /应用私有实现/],
  ['PC 引用同包移动组件', 'packages/biz-project/src/pc/Form.js', "export * from '../mobile/Form.js'", /PC 业务不能依赖移动端/],
  ['移动引用 PC', 'packages/biz-project/src/mobile/Form.js', "import('../pc/Form.js')", /移动业务不能依赖 PC/],
  ['PC 引用移动 UI', 'packages/biz-project/src/pc/Form.js', "import '@flow/workflow-mobile-ui/index'", /PC 业务不能依赖移动端/],
  ['PC 引用 Vant', 'packages/biz-project/src/pc/Form.js', "import 'vant'", /PC 业务不能依赖移动端/],
  ['移动引用 Element Plus', 'packages/biz-project/src/mobile/Form.js', "import 'element-plus'", /移动业务不能依赖 PC/],
  ['共享模型反向引用平台', 'packages/biz-project/src/shared/model.js', "import '../pc/Form.js'", /跨端业务逻辑不能依赖平台 UI/],
  ['共享 API 引用 UI', 'packages/biz-project/src/api/member.js', "import 'element-plus'", /跨端业务逻辑不能依赖平台 UI/],
  ['跨包相对私有路径', 'packages/biz-project/src/shared/model.js', "import '../../../biz-purchase/src/shared/secret.js'", /不得通过文件路径/],
  ['产品包相对私有路径', 'packages/biz-project/src/shared/model.js', "import '../../../workflow-core/src/secret.js'", /不得通过文件路径/],
  ['跨业务缺少声明', 'packages/biz-project/src/shared/model.js', "import '@flow/biz-purchase/shared/public'", /必须显式声明运行依赖/],
  ['未公开的产品入口', 'packages/biz-project/src/shared/model.js', "import '@flow/workflow-core/private/secret'", /exports 公开入口/],
  ['运行时依赖仅 devDependency 不够', 'packages/biz-project/src/shared/model.js', "import 'not-declared'", /必须显式声明运行依赖/],
  ['计算式懒加载', 'packages/biz-project/src/pc/Form.js', 'const load = name => import(name)', /可静态解析的显式导入/],
  ['计算式 require', 'packages/biz-project/src/pc/Form.js', 'const load = name => require(name)', /可静态解析的显式导入/],
  ['glob 越界', 'packages/biz-project/src/pc/Form.js', "const modules = import.meta.glob('../../../../workflow-web/src/*.js')", /可静态解析的显式导入/]
]) {
  test(`拒绝${label}`, t => {
    const f = fixture(t)
    f.write(filename, source)
    assert.ok(f.check().some(error => expected.test(error)), f.check().join('\n'))
  })
}

test('跨业务声明依赖仍不能使用被 exports 排除的私有入口', t => {
  const f = fixture(t)
  f.pkg('biz-project', { dependencies: { '@flow/biz-purchase': '*' } })
  f.write('packages/biz-project/src/shared/model.js', "export * from '@flow/biz-purchase/private/secret'")
  assert.match(f.check().join('\n'), /exports 公开入口/)
})

test('只有明确的清单虚拟模块可以将业务装入产品', t => {
  const f = fixture(t)
  const ctx = createBusinessBoundaryContext(f.root)
  for (const importer of ['\0virtual:flow-extension-manifest', '\0virtual:flow-mobile-extensions']) assert.equal(inspectBusinessDependency(ctx, importer, '@flow/biz-project/pc/Form'), null)
  assert.match(inspectBusinessDependency(ctx, '\0virtual:unreviewed', '@flow/biz-project/pc/Form'), /产品代码不能依赖业务包/)
})

test('最终模块图检查覆盖合法入口后间接懒加载应用私有文件', t => {
  const f = fixture(t)
  const form = path.join(f.root, 'packages/biz-project/src/pc/Form.js')
  const helper = path.join(f.root, 'packages/biz-project/src/pc/helper.js')
  const privateFile = path.join(f.root, 'workflow-web/src/private.js')
  const ids = ['\0virtual:flow-extension-manifest', form, helper, privateFile]
  assert.throws(() => workflowBoundaryPlugin('pc', { repository: f.root }).generateBundle.call({
    getModuleIds: () => ids,
    getModuleInfo: id => ({ importedIds: id === ids[0] ? [form] : id === form ? [helper] : [], dynamicallyImportedIds: id === helper ? [privateFile] : [] }),
    error(message) { throw new Error(message) }, emitFile() { assert.fail('越界不能输出产物') }
  }), /应用私有实现/)
})

test('最终模块图拒绝已启用业务间接带入未启用业务', t => {
  const f = fixture(t)
  const target = path.join(f.root, 'packages/biz-purchase/src/shared/model.js')
  assert.throws(() => workflowBoundaryPlugin('pc', { repository: f.root, enabledModules: ['biz-project'] }).generateBundle.call({
    getModuleIds: () => [target],
    error(message) { throw new Error(message) }, emitFile() { assert.fail('未选业务不能输出产物') }
  }), /产物包含未启用的业务模块/)
})

test('真实 Vite 构建拒绝通过 alias 间接引入业务包', async t => {
  const f = fixture(t)
  f.write('workflow-web/src/main.js', "export const load = () => import('@customer')")
  f.write('packages/biz-project/src/pc/Form.js', 'export const value = 1')
  assert.deepEqual(f.check(), [], '此 alias 只在 Vite 中定义，源码检查无法解析')
  await assert.rejects(build({
    configFile: false, root: path.join(f.root, 'workflow-web'), logLevel: 'silent',
    plugins: [workflowBoundaryPlugin('pc', { repository: f.root })],
    resolve: { alias: { '@customer': path.join(f.root, 'packages/biz-project/src/pc/Form.js') } },
    build: { write: false, minify: false, rollupOptions: { input: path.join(f.root, 'workflow-web/src/main.js') } }
  }), /产品代码不能依赖业务包/)
})

test('真实仓库的所有业务和产品源码遵守边界', () => {
  const result = checkBusinessBoundaries(fileURLToPath(new URL('../..', import.meta.url)))
  assert.ok(result.files > 100, '检查必须覆盖应用与共享包源码')
  assert.deepEqual(result.errors, [])
})
