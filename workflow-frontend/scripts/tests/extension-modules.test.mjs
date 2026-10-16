import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync, symlinkSync, realpathSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { EventEmitter } from 'node:events'
import { discoverExtensions } from '../extensions/discover.mjs'
import { generateExtensionModule } from '../extensions/generate.mjs'
import { resolveEnabledModules } from '../extensions/modules.mjs'
import { resolveImplementation } from '../extensions/validate.mjs'
import { generateMobileExtensions, mobileExtensionsPlugin } from '../mobile-extensions.mjs'
import { flowExtensionsPlugin } from '../../workflow-web/build/extensions/vite-plugin.mjs'

const repository = fileURLToPath(new URL('../..', import.meta.url))

/** 独立工作区故意不生成 dist，以验证声明校验无需先构建业务包。 */
function fixture(t) {
  const repositoryRoot = realpathSync(mkdtempSync(path.join(tmpdir(), 'flow-biz-modules-')))
  t.after(() => rmSync(repositoryRoot, { recursive: true, force: true }))
  const put = (relative, value) => {
    const filename = path.join(repositoryRoot, relative)
    mkdirSync(path.dirname(filename), { recursive: true })
    writeFileSync(filename, typeof value === 'string' ? value : JSON.stringify(value))
    return filename
  }
  const manifestDirectory = path.join(repositoryRoot, 'extensions/manifests')
  const moduleConfigFile = put('config/frontend-modules.json', { enabledModules: ['biz-project'] })
  const pc = 'packages/biz-project/src/pc/forms/ProjectForm.vue'
  const mobile = 'packages/biz-project/src/mobile/forms/ProjectForm.vue'
  put(pc, '<template>PC</template>')
  put(mobile, '<template>Mobile</template>')
  const descriptor = { name: '@flow/biz-project', exports: {
    './pc/forms/ProjectForm': './dist/pc/forms/ProjectForm.vue',
    './mobile/forms/ProjectForm': './dist/mobile/forms/ProjectForm.vue'
  } }
  put('packages/biz-project/package.json', descriptor)
  const manifest = { schemaVersion: 1, type: 'FORM', name: 'ProjectForm', label: '项目表单', version: 3,
    implementation: { package: '@flow/biz-project/pc/forms/ProjectForm', export: 'default', kind: 'COMPONENT' },
    platforms: { mobile: { implementation: { package: '@flow/biz-project/mobile/forms/ProjectForm', export: 'default', kind: 'COMPONENT' }, capabilities: { readonly: true, editable: true, validate: true } } }
  }
  const manifestFile = 'extensions/manifests/biz-project/forms/ProjectForm.extension.json'
  put(manifestFile, manifest)
  put('workflow-web/src/common.js', 'export const run = () => true')
  put('extensions/manifests/common/actions/Common.extension.json', {
    schemaVersion: 1, type: 'LIST_ACTION', name: 'Common', label: '通用动作', version: 1, targets: ['ROW'],
    implementation: { path: 'src/common.js', export: 'run', kind: 'FUNCTION' }
  })
  const options = { repositoryRoot, manifestDirectory, moduleConfigFile, env: {} }
  return { repositoryRoot, root: path.join(repositoryRoot, 'workflow-web'), put, manifest, manifestFile, descriptor, pc, mobile, options }
}

test('配置与环境选择业务模块：none、覆盖、未知、重复及空配置', t => {
  const f = fixture(t)
  const selected = extra => [...resolveEnabledModules(f.options.manifestDirectory, { ...f.options, ...extra })]
  assert.deepEqual(selected(), ['biz-project'])
  assert.deepEqual(selected({ env: { FLOW_FRONTEND_MODULES: 'none' } }), [])
  assert.deepEqual(selected({ env: { FLOW_FRONTEND_MODULES: ' biz-project ' } }), ['biz-project'])
  assert.deepEqual(selected({ enabledModules: [], env: { FLOW_FRONTEND_MODULES: 'bad' } }), [])
  for (const value of ['', 'biz-missing', 'biz-project,biz-project', 'none,biz-project']) {
    assert.throws(() => selected({ env: { FLOW_FRONTEND_MODULES: value } }), /不能为空|未知|重复|名称数组/)
  }
  for (const enabledModules of [null, 'biz-project', [1], ['project']]) {
    f.put('config/frontend-modules.json', { enabledModules })
    assert.throws(() => selected(), /名称数组/)
  }
})

test('PC/移动只生成自己的公开子入口，禁用模块在两个导入图中均消失', t => {
  const f = fixture(t)
  const entries = discoverExtensions(f.root, f.options)
  const biz = entries.find(entry => entry.origin === 'BUSINESS')
  assert.equal(biz.module, 'biz-project')
  assert.equal(biz.name, 'ProjectForm')
  assert.equal(biz.version, 3)
  const pc = generateExtensionModule(entries, f.root).split('\n').filter(line => line.startsWith('import ')).join('\n')
  assert.match(pc, /from "@flow\/biz-project\/pc\/forms\/ProjectForm"/)
  assert.doesNotMatch(pc, /@flow\/biz-project\/mobile|packages\/biz-project\/src/)
  const mobile = generateMobileExtensions(f.options)
  assert.match(mobile, /from "@flow\/biz-project\/mobile\/forms\/ProjectForm"/)
  assert.doesNotMatch(mobile, /@flow\/biz-project\/pc|common\.js/)
  const disabled = { ...f.options, env: { FLOW_FRONTEND_MODULES: 'none' } }
  assert.doesNotMatch(generateExtensionModule(discoverExtensions(f.root, disabled), f.root), /biz-project|ProjectForm/)
  assert.doesNotMatch(generateMobileExtensions(disabled), /biz-project|ProjectForm/)
})

test('未选业务不依赖源码/产物，metadata 从不读取业务组件，但坏声明仍失败', t => {
  const f = fixture(t)
  rmSync(path.join(f.repositoryRoot, 'packages'), { recursive: true })
  assert.doesNotThrow(() => discoverExtensions(f.root, { ...f.options, enabledModules: [] }))
  assert.doesNotThrow(() => discoverExtensions(f.root, { ...f.options, platform: 'metadata' }))
  assert.throws(() => discoverExtensions(f.root, f.options), /ENOENT/)
  f.manifest.implementation.package = 'https://example.com/evil.js'
  f.put(f.manifestFile, f.manifest)
  assert.throws(() => discoverExtensions(f.root, { ...f.options, enabledModules: [] }), /公开包入口/)
  f.manifest.implementation = { path: 'src/common.js', package: '@flow/biz-project/pc/forms/ProjectForm', kind: 'COMPONENT', export: 'default' }
  f.put(f.manifestFile, f.manifest)
  assert.throws(() => discoverExtensions(f.root, { ...f.options, enabledModules: [] }), /只能填写一个/)
})

test('业务入口必须匹配本业务、本平台、公开 exports 和真实源码边界', t => {
  const f = fixture(t)
  const resolve = (specifier, extra = {}) => resolveImplementation(f.root, { package: specifier }, { ...f.options, moduleId: 'biz-project', platform: 'pc', ...extra })
  assert.equal(resolve('@flow/biz-project/pc/forms/ProjectForm'), path.join(f.repositoryRoot, f.pc))
  for (const entry of ['vue', '@flow/biz-other/pc/forms/ProjectForm', '@flow/biz-project/src/pc/forms/ProjectForm', '@flow/biz-project/pc/../secret', '@flow/biz-project/pc/forms/Unexported', '@flow/biz-project/mobile/forms/ProjectForm']) {
    assert.throws(() => resolve(entry), /公开包入口|未导出/)
  }
  f.descriptor.exports['./pc/forms/ProjectForm'] = './dist/mobile/forms/ProjectForm.vue'
  f.put('packages/biz-project/package.json', f.descriptor)
  assert.throws(() => resolve('@flow/biz-project/pc/forms/ProjectForm'), /本平台/)
  f.descriptor.exports = { './pc/forms/*': './dist/pc/forms/*.vue' }
  f.put('packages/biz-project/package.json', f.descriptor)
  assert.equal(resolve('@flow/biz-project/pc/forms/ProjectForm'), path.join(f.repositoryRoot, f.pc))
  rmSync(path.join(f.repositoryRoot, f.pc))
  symlinkSync(path.join(f.repositoryRoot, f.mobile), path.join(f.repositoryRoot, f.pc))
  assert.throws(() => resolve('@flow/biz-project/pc/forms/ProjectForm'), /路径越界/)
})

test('本地扩展仍禁止目录穿越、注册基础设施和符号链接逃逸', t => {
  const f = fixture(t)
  assert.throws(() => resolveImplementation(f.root, { path: 'src/../secret.js' }), /实现路径/)
  assert.throws(() => resolveImplementation(f.root, { path: 'src/extensions/register.js' }), /注册基础设施/)
  const outside = f.put('outside.js', 'export default 1')
  symlinkSync(outside, path.join(f.root, 'src/escape.js'))
  assert.throws(() => resolveImplementation(f.root, { path: 'src/escape.js' }), /越界/)
  f.manifest.implementation = { path: 'src/common.js', kind: 'COMPONENT', export: 'default' }
  f.put(f.manifestFile, f.manifest)
  assert.throws(() => discoverExtensions(f.root, { ...f.options, enabledModules: [] }), /必须通过 package/)
})

test('目标平台检查不依赖另一端源码，移动实现 kind 在未选择时也校验', t => {
  const f = fixture(t)
  rmSync(path.join(f.repositoryRoot, f.pc))
  assert.doesNotThrow(() => generateMobileExtensions(f.options))
  assert.throws(() => discoverExtensions(f.root, f.options), /ENOENT/)
  f.manifest.platforms.mobile.implementation.kind = 'FUNCTION'
  f.put(f.manifestFile, f.manifest)
  assert.throws(() => discoverExtensions(f.root, { ...f.options, enabledModules: [] }), /移动扩展实现类型不符/)
})

test('PC 和移动开发监听配置切换、包字段产物更新，并把坏配置显示为错误', t => {
  for (const platform of ['pc', 'mobile']) {
    const f = fixture(t)
    f.manifest.type = 'FIELD'
    f.put(f.manifestFile, f.manifest)
    const watcher = new EventEmitter(), httpServer = new EventEmitter(), watched = new Set(), messages = []
    watcher.add = filenames => { for (const file of [filenames].flat()) watched.add(file) }
    let invalidations = 0
    const server = { watcher, httpServer, ws: { send: value => messages.push(value) }, moduleGraph: {
      getModuleById: () => ({}), invalidateModule: () => invalidations++
    } }
    const plugin = platform === 'pc' ? flowExtensionsPlugin(f.options) : mobileExtensionsPlugin(f.options)
    plugin.configResolved?.({ root: f.root })
    plugin.configureServer(server)
    const emit = file => platform === 'pc' ? watcher.emit('change', file) : watcher.emit('all', 'change', file)
    const moduleId = plugin.resolveId(platform === 'pc' ? 'virtual:flow-extension-manifest' : 'virtual:flow-mobile-extensions')
    const load = () => plugin.load.call({ addWatchFile() {} }, moduleId)
    assert.match(load(), /@flow\/biz-project/)
    assert.ok(watched.has(f.options.moduleConfigFile))
    const output = path.join(f.repositoryRoot, 'packages/biz-project/dist', platform, 'forms/ProjectForm.vue')
    assert.ok(watched.has(output), '字段产物完成复制后应重新提取组件的事件能力')
    f.put('config/frontend-modules.json', { enabledModules: [] })
    emit(f.options.moduleConfigFile)
    assert.equal(messages.at(-1).type, 'full-reload')
    assert.doesNotMatch(load(), /@flow\/biz-project/)
    f.put('config/frontend-modules.json', { enabledModules: ['biz-project'] })
    emit(f.options.moduleConfigFile)
    assert.match(load(), /@flow\/biz-project/)
    emit(output)
    assert.equal(invalidations, 3)
    f.put('config/frontend-modules.json', { enabledModules: ['biz-missing'] })
    emit(f.options.moduleConfigFile)
    assert.equal(messages.at(-1).type, 'error')
    assert.match(messages.at(-1).err.message, /未知前端业务模块/)
    httpServer.emit('close')
    assert.equal(watcher.listenerCount(platform === 'pc' ? 'change' : 'all'), 0)
  }
})

test('迁移后的项目扩展身份、版本和 Schema 引用保持有效', () => {
  const entries = discoverExtensions(path.join(repository, 'workflow-web'), { platform: 'metadata', enabledModules: ['biz-project'] })
    .filter(entry => entry.module === 'biz-project')
  assert.deepEqual(entries.map(entry => `${entry.type}:${entry.name}@${entry.version}`).sort(), [
    'ACTION_CONDITION:PROJECT:CUSTOM_CONDITION@1', 'FIELD:ProjectAcceptanceSummaryNode@1', 'FIELD:project_acceptance_level@1',
    'FIELD:project_acceptance_score@1', 'FORM:ProjectExtensionAcceptanceForm@1', 'FORM:ProjectMemberChangeForm@1',
    'LIST:PROJECT_CUSTOM_LIST_SCHEMA@1', 'LIST:ProjectAcceptanceBoardList@1', 'LIST_ACTION:projectAcceptanceRowAction@1',
    'LIST_ACTION:projectAcceptanceSelectionAction@1', 'LIST_ACTION:projectAcceptanceToolbarAction@1',
    'LIST_BUTTON:ProjectAcceptanceInspectButton@1', 'LIST_CELL:ProjectAcceptanceScoreCell@1', 'PERMISSION_PROVIDER:projectPermissionOptions@1'
  ].sort())
  for (const entry of entries) {
    const filename = path.resolve(repository, 'workflow-web', entry.sourceFile)
    assert.equal(JSON.parse(readFileSync(path.resolve(path.dirname(filename), entry.$schema), 'utf8')).title, 'Flow 前端扩展清单')
  }
})
