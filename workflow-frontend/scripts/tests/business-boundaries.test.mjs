import test from 'node:test'
import assert from 'node:assert/strict'
import { cpSync, mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
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
    const category = name.startsWith('biz-') ? 'custom' : 'packages'
    write(`${category}/${name}/package.json`, JSON.stringify({ name: `@flow/${name}`, type: 'module', exports: { './*': './src/*.js', './private/*': null }, ...extra }))
  }
  pkg('workflow-core')
  pkg('workflow-api')
  pkg('workflow-extensions')
  pkg('workflow-mobile-ui')
  pkg('biz-project', { dependencies: { '@flow/workflow-core': '*', '@flow/workflow-extensions': '*', '@flow/workflow-mobile-ui': '*' }, peerDependencies: { vue: '*', 'element-plus': '*', vant: '*' } })
  pkg('biz-purchase')
  mkdirSync(path.join(root, 'custom/biz-project/manifests'), { recursive: true })
  mkdirSync(path.join(root, 'custom/biz-purchase/manifests'), { recursive: true })
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
  f.write('custom/biz-project/src/pc/Form.js', "export { value } from '../shared/value.js'; export * from '@flow/biz-purchase/shared/public'; import '@flow/workflow-core/value'")
  f.write('custom/biz-project/src/shared/value.js', 'export const value = 1')
  f.write('custom/biz-purchase/src/shared/public.js', 'export const shared = 1')
  f.write('workflow-web/src/main.js', "import 'virtual:flow-extension-manifest'")
  f.write('workflow-web/src/example.js', "// import '@flow/biz-project/pc/Form'\nconst example = '@flow/biz-project/pc/Form'")
  assert.deepEqual(f.check(), [])
})

for (const [label, filename, source, expected] of [
  ['核心重导出业务', 'packages/workflow-core/src/index.js', "export * from '@flow/biz-project/shared/model'", /产品代码不能依赖业务包/],
  ['API 动态加载业务', 'packages/workflow-api/src/index.js', "export const load = () => import('@flow/biz-project/shared/model')", /产品代码不能依赖业务包/],
  ['SDK 引用业务', 'packages/workflow-extensions/src/index.js', "import '@flow/biz-project/shared/model'", /产品代码不能依赖业务包/],
  ['移动 UI 引用业务', 'packages/workflow-mobile-ui/src/index.js', "export * from '@flow/biz-project/mobile/Form'", /产品代码不能依赖业务包/],
  ['应用直接加载业务', 'workflow-web/src/main.js', "import '@flow/biz-project/pc/Form'", /产品代码不能依赖业务包/],
  ['产品示例依赖业务', 'workflow-mobile/src/extensions/Demo.vue', '<script setup>import x from "@flow/biz-project/shared/model"</script>', /产品代码不能依赖业务包/],
  ['应用相对路径依赖业务', 'workflow-web/src/main.js', "export * from '../../custom/biz-project/src/shared/model.js'", /产品代码不能依赖业务包/],
  ['业务应用别名', 'custom/biz-project/src/pc/Form.vue', '<script setup>import x from "@/components/Private.vue"</script>', /应用私有实现/],
  ['业务应用相对路径', 'custom/biz-project/src/pc/Form.js', "import '../../../../workflow-web/src/private.js'", /应用私有实现/],
  ['业务移动应用相对路径', 'custom/biz-project/src/mobile/Form.js', "import '../../../../workflow-mobile/src/private.js'", /应用私有实现/],
  ['PC 引用同包移动组件', 'custom/biz-project/src/pc/Form.js', "export * from '../mobile/Form.js'", /PC 业务不能依赖移动端/],
  ['移动引用 PC', 'custom/biz-project/src/mobile/Form.js', "import('../pc/Form.js')", /移动业务不能依赖 PC/],
  ['PC 引用移动 UI', 'custom/biz-project/src/pc/Form.js', "import '@flow/workflow-mobile-ui/index'", /PC 业务不能依赖移动端/],
  ['PC 引用 Vant', 'custom/biz-project/src/pc/Form.js', "import 'vant'", /PC 业务不能依赖移动端/],
  ['移动引用 Element Plus', 'custom/biz-project/src/mobile/Form.js', "import 'element-plus'", /移动业务不能依赖 PC/],
  ['共享模型反向引用平台', 'custom/biz-project/src/shared/model.js', "import '../pc/Form.js'", /跨端业务逻辑不能依赖平台 UI/],
  ['共享 API 引用 UI', 'custom/biz-project/src/api/member.js', "import 'element-plus'", /跨端业务逻辑不能依赖平台 UI/],
  ['跨包相对私有路径', 'custom/biz-project/src/shared/model.js', "import '../../../biz-purchase/src/shared/secret.js'", /不得通过文件路径/],
  ['产品包相对私有路径', 'custom/biz-project/src/shared/model.js', "import '../../../../packages/workflow-core/src/secret.js'", /不得通过文件路径/],
  ['跨业务缺少声明', 'custom/biz-project/src/shared/model.js', "import '@flow/biz-purchase/shared/public'", /必须显式声明运行依赖/],
  ['未公开的产品入口', 'custom/biz-project/src/shared/model.js', "import '@flow/workflow-core/private/secret'", /exports 公开入口/],
  ['运行时依赖仅 devDependency 不够', 'custom/biz-project/src/shared/model.js', "import 'not-declared'", /必须显式声明运行依赖/],
  ['计算式懒加载', 'custom/biz-project/src/pc/Form.js', 'const load = name => import(name)', /可静态解析的显式导入/],
  ['计算式 require', 'custom/biz-project/src/pc/Form.js', 'const load = name => require(name)', /可静态解析的显式导入/],
  ['glob 越界', 'custom/biz-project/src/pc/Form.js', "const modules = import.meta.glob('../../../../workflow-web/src/*.js')", /可静态解析的显式导入/]
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
  f.write('custom/biz-project/src/shared/model.js', "export * from '@flow/biz-purchase/private/secret'")
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
  const form = path.join(f.root, 'custom/biz-project/src/pc/Form.js')
  const helper = path.join(f.root, 'custom/biz-project/src/pc/helper.js')
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
  const target = path.join(f.root, 'custom/biz-purchase/src/shared/model.js')
  assert.throws(() => workflowBoundaryPlugin('pc', { repository: f.root, enabledModules: ['biz-project'] }).generateBundle.call({
    getModuleIds: () => [target],
    error(message) { throw new Error(message) }, emitFile() { assert.fail('未选业务不能输出产物') }
  }), /产物包含未启用的业务模块/)
})

test('真实 Vite 构建拒绝通过 alias 间接引入业务包', async t => {
  const f = fixture(t)
  f.write('workflow-web/src/main.js', "export const load = () => import('@customer')")
  f.write('custom/biz-project/src/pc/Form.js', 'export const value = 1')
  assert.deepEqual(f.check(), [], '此 alias 只在 Vite 中定义，源码检查无法解析')
  await assert.rejects(build({
    configFile: false, root: path.join(f.root, 'workflow-web'), logLevel: 'silent',
    plugins: [workflowBoundaryPlugin('pc', { repository: f.root })],
    resolve: { alias: { '@customer': path.join(f.root, 'custom/biz-project/src/pc/Form.js') } },
    build: { write: false, minify: false, rollupOptions: { input: path.join(f.root, 'workflow-web/src/main.js') } }
  }), /产品代码不能依赖业务包/)
})

test('真实仓库的所有业务和产品源码遵守边界', () => {
  const result = checkBusinessBoundaries(fileURLToPath(new URL('../..', import.meta.url)))
  assert.ok(result.files > 100, '检查必须覆盖应用与共享包源码')
  assert.deepEqual(result.errors, [])
})

// 即使包名伪装为产品包，目录身份也必须保留，不能遗漏隔离检查。
test('custom 目录身份不受 package.name 影响，非法模块名也不能绕过隔离', t => {
  const f = fixture(t)
  f.pkg('biz-project', { name: '@customer/innocent-name' })
  f.write('custom/biz-project/src/pc/Form.js', "import '@/private'")
  f.write('workflow-web/src/main.js', "import '@customer/innocent-name/pc/Form'")
  const errors = f.check().join('\n')
  assert.match(errors, /业务包名称不匹配/)
  assert.match(errors, /应用私有实现/)
  assert.match(errors, /产品代码不能依赖业务包/)
  f.write('custom/renamed/package.json', JSON.stringify({ name: '@flow/renamed' }))
  f.write('custom/renamed/src/pc/Form.js', "import '@/private'")
  assert.equal(createBusinessBoundaryContext(f.root).classify(path.join(f.root, 'custom/renamed/src/pc/Form.js')).pkg.business, true)
})

test('业务模块的实现和清单可整体复制到另一工作区，无需产品中的额外登记', t => {
  const source = fixture(t), target = fixture(t)
  source.write('custom/biz-project/src/pc/Form.js', "export * from '../shared/model.js'")
  source.write('custom/biz-project/src/shared/model.js', 'export const value = 1')
  source.write('custom/biz-project/manifests/Field.extension.json', JSON.stringify({ name: 'self-contained' }))
  rmSync(path.join(target.root, 'custom/biz-project'), { recursive: true })
  cpSync(path.join(source.root, 'custom/biz-project'), path.join(target.root, 'custom/biz-project'), { recursive: true })
  assert.deepEqual(target.check(), [])
  const context = createBusinessBoundaryContext(target.root)
  assert.equal(context.resolve('@flow/biz-project/pc/Form', path.join(target.root, 'workflow-web/src/main.js')), path.join(context.root, 'custom/biz-project/src/pc/Form.js'))
})

for (const selection of [undefined, []]) {
  test(`真实 Vite 依赖图遵守 ${selection ? 'none' : 'default'} 业务选择`, async t => {
    const f = fixture(t)
    f.write('workflow-web/src/main.js', "export * from 'virtual:flow-extension-manifest'")
    f.write('custom/biz-project/src/pc/Form.js', 'export const businessValue = 1')
    const result = await build({
      configFile: false, root: path.join(f.root, 'workflow-web'), logLevel: 'silent',
      plugins: [workflowBoundaryPlugin('pc', { repository: f.root, enabledModules: selection }), {
        name: 'test-extension-assembly',
        resolveId(id) { if (id === 'virtual:flow-extension-manifest') return '\0' + id },
        load(id) {
          if (id !== '\0virtual:flow-extension-manifest') return
          return selection ? 'export const productValue = 1' : `export * from ${JSON.stringify(path.join(f.root, 'custom/biz-project/src/pc/Form.js'))}`
        }
      }],
      build: { write: false, minify: false, rollupOptions: { input: path.join(f.root, 'workflow-web/src/main.js'), preserveEntrySignatures: 'strict' } }
    })
    const asset = result.output.find(item => item.fileName === 'module-boundary.json')
    const modules = JSON.parse(asset.source).modules
    assert.equal(modules.some(id => id.includes('/custom/biz-project/')), selection === undefined)
    assert.ok(modules.some(id => id.includes('/workflow-web/src/main.js')))
  })
}

test('产品包和应用不能以运行依赖声明固定业务模块，根工作区负责链接', t => {
  const f = fixture(t)
  f.pkg('workflow-core', { peerDependencies: { '@flow/biz-project': '*' } })
  f.write('workflow-web/package.json', JSON.stringify({ dependencies: { '@flow/biz-project': '*' } }))
  f.write('workflow-mobile/package.json', JSON.stringify({ optionalDependencies: { '@flow/biz-purchase': '*' } }))
  const errors = f.check().filter(error => error.includes('产品不能声明业务运行依赖'))
  assert.equal(errors.length, 3, errors.join('\n'))
})
