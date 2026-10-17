import test from 'node:test'
import assert from 'node:assert/strict'
import { spawn, spawnSync } from 'node:child_process'
import { chmodSync, copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { setTimeout as delay } from 'node:timers/promises'

const repository = fileURLToPath(new URL('../..', import.meta.url))
const productPackages = ['@flow/workflow-core', '@flow/workflow-extensions', '@flow/workflow-api', '@flow/workflow-mobile-ui']

/** 使用临时仓库和替身 npm 观察真实进程组、启动次序和文件监听，不启动项目服务器。 */
function fixture(t, enabledModules = ['biz-project']) {
  const root = mkdtempSync(path.join(tmpdir(), 'flow-workspace-lifecycle-'))
  const children = new Set()
  const put = (name, content) => {
    const filename = path.join(root, name)
    mkdirSync(path.dirname(filename), { recursive: true })
    writeFileSync(filename, typeof content === 'string' ? content : JSON.stringify(content))
    return filename
  }
  for (const name of ['scripts/dev-workspace.mjs', 'scripts/build-workspace-packages.mjs', 'scripts/workspace-packages.mjs',
    'scripts/extensions/modules.mjs', 'scripts/extensions/discover.mjs', 'scripts/extensions/validate.mjs',
    'scripts/extensions/generate.mjs', 'packages/workflow-extensions/schemas/extension.schema.json']) {
    const target = put(name, '')
    copyFileSync(path.join(repository, name), target)
  }
  mkdirSync(path.join(root, 'packages/workflow-extensions/manifests'), { recursive: true })
  for (const moduleId of ['biz-project', 'biz-purchase']) {
    mkdirSync(path.join(root, 'custom', moduleId, 'manifests'), { recursive: true })
    put(`custom/${moduleId}/package.json`, { name: `@flow/${moduleId}` })
  }
  put('package.json', { type: 'module' })
  put('config/frontend-modules.json', { enabledModules })
  put('events.jsonl', '')
  const npm = put('bin/npm', `#!${process.execPath}
import { appendFileSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { spawn } from 'node:child_process'
import path from 'node:path'
const root = process.cwd()
const args = process.argv.slice(2)
const workspace = args[args.indexOf('--workspace') + 1]
const record = (type, extra = {}) => appendFileSync(path.join(root, 'events.jsonl'), JSON.stringify({ type, pid: process.pid, workspace, args, ...extra }) + '\\n')
const wait = ms => new Promise(resolve => setTimeout(resolve, ms))
const marker = name => path.join(root, 'built', name.replaceAll('/', '_'))
for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => { record('stop', { signal }); process.exit(0) })
if (process.env.FAKE_GRANDCHILD === '1') {
  record('grandchild')
  setInterval(() => {}, 1000)
} else if (args[1] === 'build:packages') {
  record('build-group-start')
  const child = spawn(process.execPath, ['scripts/build-workspace-packages.mjs'], { stdio: 'inherit' })
  child.on('exit', (code, signal) => { record('build-group-done', { code, signal }); process.exit(code || (signal ? 1 : 0)) })
} else if (args[1] === 'build') {
  record('build-start')
  await wait(Number(process.env.FAKE_BUILD_DELAY || 15))
  if (workspace === process.env.FAKE_FAIL_BUILD) { record('build-failed'); process.exit(7) }
  mkdirSync(path.dirname(marker(workspace)), { recursive: true })
  writeFileSync(marker(workspace), 'built')
  record('build-done')
} else if (args[1] === 'dev') {
  record('dev-start')
  if (workspace.startsWith('workflow-')) {
    const config = JSON.parse(readFileSync(path.join(root, 'config/frontend-modules.json')))
    const override = process.env.FLOW_FRONTEND_MODULES
    const modules = override === undefined ? config.enabledModules : override === 'none' ? [] : override.split(',')
    const missing = [...${JSON.stringify(productPackages)}, ...modules.map(name => '@flow/' + name)].filter(name => !existsSync(marker(name)))
    if (missing.length) { record('app-missing-build', { missing }); process.exit(13) }
  } else {
    // 模拟 mobile-ui 的 Vite watcher 首轮清理产物；只等待前置 build 无法避免冷启动竞态。
    if (workspace === '@flow/workflow-mobile-ui') rmSync(marker(workspace), { force: true })
    record('watch-start')
    await wait(Number(process.env.FAKE_WATCH_DELAY || 20))
    if (workspace === process.env.FAKE_FAIL_WATCH) { record('watch-failed'); process.exit(8) }
    mkdirSync(path.dirname(marker(workspace)), { recursive: true })
    writeFileSync(marker(workspace), 'built')
    record('watch-ready')
    console.log(workspace + ': built')
  }
  spawn(process.execPath, [process.argv[1], ...args], { stdio: 'inherit', env: { ...process.env, FAKE_GRANDCHILD: '1' } })
  setInterval(() => {}, 1000)
} else { record('unexpected'); process.exit(19) }
`)
  chmodSync(npm, 0o755)
  const env = { ...process.env, PATH: `${path.join(root, 'bin')}${path.delimiter}${process.env.PATH}` }
  delete env.FLOW_FRONTEND_MODULES
  const events = () => readFileSync(path.join(root, 'events.jsonl'), 'utf8').trim().split('\n').filter(Boolean).map(line => JSON.parse(line))
  t.after(async () => {
    for (const child of children) if (child.exitCode === null && child.signalCode === null) child.kill('SIGTERM')
    // 启动失败时已 fork 的 npm 可能稍后才写入日志，重复回收直至启动窗口结束。
    for (let attempt = 0; attempt < 10; attempt++) {
      await delay(30)
      for (const event of events()) {
        for (const pid of [event.pid, -event.pid]) {
          try { process.kill(pid, 'SIGKILL') } catch (error) { if (error.code !== 'ESRCH') throw error }
        }
      }
    }
    for (const child of children) if (child.exitCode === null && child.signalCode === null) child.kill('SIGKILL')
    rmSync(root, { recursive: true, force: true })
  })
  return { root, env, events, put,
    select(modules) {
      // 编辑器常用临时文件 + rename 保存，必须触发目录监听。
      put('config/frontend-modules.next.json', { enabledModules: modules })
      renameSync(path.join(root, 'config/frontend-modules.next.json'), path.join(root, 'config/frontend-modules.json'))
    },
    build(overrides = {}) {
      return spawnSync(process.execPath, ['scripts/build-workspace-packages.mjs'], { cwd: root, env: { ...env, ...overrides }, encoding: 'utf8', timeout: 10_000 })
    },
    dev(target = 'web', overrides = {}) {
      const child = spawn(process.execPath, ['scripts/dev-workspace.mjs', target, '--host', '127.0.0.1'], { cwd: root, env: { ...env, ...overrides }, stdio: ['ignore', 'pipe', 'pipe'] })
      child.output = ''
      child.stdout.on('data', data => { child.output += data })
      child.stderr.on('data', data => { child.output += data })
      children.add(child)
      return child
    }
  }
}

async function until(condition, description, child) {
  const deadline = Date.now() + 8_000
  while (Date.now() < deadline) {
    if (condition()) return
    if (child && (child.exitCode !== null || child.signalCode !== null)) assert.fail(`${description}：开发入口提前退出 ${child.exitCode ?? child.signalCode}\n${child.output}`)
    await delay(20)
  }
  assert.fail(`${description}：等待超时\n${child?.output || ''}`)
}
function alive(pid) {
  try { process.kill(pid, 0); return true } catch (error) { if (error.code === 'ESRCH') return false; throw error }
}
async function stopAndAssertClean(app, child) {
  const pids = [...new Set(app.events().map(event => event.pid))]
  child.kill('SIGTERM')
  await until(() => child.exitCode !== null || child.signalCode !== null, '入口退出')
  await until(() => pids.every(pid => !alive(pid)), '全部子进程和孙进程退出')
  assert.equal(child.exitCode, 0, child.output)
}

test('生产包按依赖顺序构建，none 不构建业务，失败立即停止', t => {
  const app = fixture(t)
  assert.equal(app.build().status, 0)
  assert.deepEqual(app.events().filter(event => event.type === 'build-done').map(event => event.workspace), [...productPackages, '@flow/biz-project'])
  writeFileSync(path.join(app.root, 'events.jsonl'), '')
  assert.equal(app.build({ FLOW_FRONTEND_MODULES: 'none' }).status, 0)
  assert.deepEqual(app.events().filter(event => event.type === 'build-done').map(event => event.workspace), productPackages)
  writeFileSync(path.join(app.root, 'events.jsonl'), '')
  assert.equal(app.build({ FAKE_FAIL_BUILD: '@flow/workflow-extensions' }).status, 7)
  assert.deepEqual(app.events().filter(event => event.type === 'build-start').map(event => event.workspace), productPackages.slice(0, 2))
})

test('冷启动先完整构建后开应用及选中业务 watcher，关闭清理进程组', async t => {
  const app = fixture(t)
  const child = app.dev('mobile')
  await until(() => app.events().filter(event => event.type === 'grandchild').length === 6, '全部开发进程启动', child)
  const events = app.events()
  assert.deepEqual(events.filter(event => event.type === 'dev-start').map(event => event.workspace).sort(), [...productPackages, '@flow/biz-project', 'workflow-mobile'].sort())
  assert.ok(events.findIndex(event => event.type === 'build-group-done') < events.findIndex(event => event.type === 'dev-start'))
  const appStarted = events.findIndex(event => event.type === 'dev-start' && event.workspace === 'workflow-mobile')
  assert.equal(events.filter(event => event.type === 'watch-ready').length, 5)
  assert.ok(events.every((event, index) => event.type !== 'watch-ready' || index < appStarted), '应用须等全部包 watcher 首轮产物就绪')
  assert.deepEqual(events.find(event => event.type === 'dev-start' && event.workspace === 'workflow-mobile').args.slice(-3), ['--', '--host', '127.0.0.1'])
  assert.equal(events.some(event => event.type === 'app-missing-build'), false)
  await stopAndAssertClean(app, child)
})

test('原子保存选择配置会停止旧业务 watcher，先构建新业务再启动应用', async t => {
  const app = fixture(t)
  const child = app.dev()
  await until(() => app.events().filter(event => event.type === 'grandchild').length === 6, '首轮开发进程启动', child)
  const oldPids = app.events().filter(event => ['dev-start', 'grandchild'].includes(event.type)).map(event => event.pid)
  app.select(['biz-purchase'])
  await until(() => app.events().filter(event => event.type === 'grandchild').length === 12, '切换后开发进程启动', child)
  await until(() => oldPids.every(pid => !alive(pid)), '旧进程组退出', child)
  const events = app.events()
  const build = events.findIndex(event => event.type === 'build-done' && event.workspace === '@flow/biz-purchase')
  const newDev = events.findIndex(event => event.type === 'dev-start' && event.workspace === '@flow/biz-purchase')
  assert.ok(build >= 0 && newDev > build)
  assert.equal(events.filter(event => event.type === 'dev-start' && event.workspace === '@flow/biz-project').length, 1)
  assert.equal(events.some(event => event.type === 'app-missing-build'), false)
  app.select([])
  await until(() => app.events().filter(event => event.type === 'grandchild').length === 17, '禁用全部业务后的开发进程启动', child)
  const lastStarts = app.events().filter(event => event.type === 'dev-start').slice(-5).map(event => event.workspace)
  assert.deepEqual(lastStarts.sort(), [...productPackages, 'workflow-web'].sort())
  await stopAndAssertClean(app, child)
})

test('首次构建失败不会启动应用或 watcher，且保留失败退出状态', async t => {
  const app = fixture(t)
  const child = app.dev('web', { FAKE_FAIL_BUILD: '@flow/workflow-api' })
  await until(() => child.exitCode !== null || child.signalCode !== null, '构建失败终止入口')
  assert.notEqual(child.exitCode, 0)
  assert.equal(app.events().some(event => event.type === 'dev-start'), false)
  await until(() => app.events().every(event => !alive(event.pid)), '失败构建的进程组退出')
})

test('首次构建期间切换模块也必须先完成最终选择的构建', async t => {
  const app = fixture(t, [])
  const child = app.dev('web', { FAKE_BUILD_DELAY: '100' })
  await until(() => app.events().some(event => event.type === 'build-start'), '首次构建开始', child)
  app.select(['biz-project'])
  await until(() => app.events().filter(event => event.type === 'grandchild').length === 6, '最终模块选择完成后启动', child)
  assert.equal(app.events().some(event => event.type === 'app-missing-build'), false)
  const events = app.events()
  const built = events.findIndex(event => event.type === 'build-done' && event.workspace === '@flow/biz-project')
  const appStarted = events.findIndex(event => event.type === 'dev-start' && event.workspace === 'workflow-web')
  assert.ok(built >= 0 && appStarted > built)
  await stopAndAssertClean(app, child)
})

test('重启构建中连续切换选择只启动最终模块集合，不留下过期启动请求', async t => {
  const app = fixture(t)
  const child = app.dev('web', { FAKE_BUILD_DELAY: '60' })
  await until(() => app.events().filter(event => event.type === 'grandchild').length === 6, '初始进程启动', child)
  app.select(['biz-purchase'])
  await until(() => app.events().filter(event => event.type === 'build-start').length > 5, '重启构建开始', child)
  app.select(['biz-project', 'biz-purchase'])
  await until(() => app.events().filter(event => event.type === 'grandchild').length >= 13, '最终选择启动', child)
  await delay(250)
  const events = app.events()
  assert.equal(events.filter(event => event.type === 'dev-start' && event.workspace === 'workflow-web').length, 2)
  assert.deepEqual(events.filter(event => event.type === 'dev-start').slice(-7).map(event => event.workspace).sort(), [...productPackages, '@flow/biz-project', '@flow/biz-purchase', 'workflow-web'].sort())
  assert.equal(events.some(event => event.type === 'app-missing-build'), false)
  await stopAndAssertClean(app, child)
})

test('应用 leader 意外被杀时返回失败，并回收其孙进程和其他 watcher', async t => {
  const app = fixture(t)
  const child = app.dev()
  await until(() => app.events().filter(event => event.type === 'grandchild').length === 6, '全部进程启动', child)
  const appPid = app.events().find(event => event.type === 'dev-start' && event.workspace === 'workflow-web').pid
  process.kill(appPid, 'SIGKILL')
  await until(() => child.exitCode !== null || child.signalCode !== null, '异常退出终止入口')
  assert.notEqual(child.exitCode, 0)
  await until(() => app.events().every(event => !alive(event.pid)), '回收异常 leader 的后代和其他进程组')
})

test('构建期间关闭入口不会在构建退出后继续启动应用', async t => {
  const app = fixture(t)
  const child = app.dev('web', { FAKE_BUILD_DELAY: '200' })
  await until(() => app.events().some(event => event.type === 'build-start'), '构建启动', child)
  await stopAndAssertClean(app, child)
  assert.equal(app.events().some(event => event.type === 'dev-start'), false)
})

test('开发中新增、修改、删除产品清单会重新生成字段策略', async t => {
  const app = fixture(t)
  const child = app.dev()
  await until(() => app.events().filter(event => event.type === 'grandchild').length === 6, '全部进程启动', child)
  const relative = 'packages/workflow-extensions/manifests/platform/fields/NewField.extension.json'
  const generated = path.join(app.root, 'packages/workflow-core/src/extensions/generated/field-definitions.js')
  const contents = () => existsSync(generated) ? readFileSync(generated, 'utf8') : ''
  const manifest = { schemaVersion: 1, type: 'FIELD', name: 'NewField', label: '新增字段', version: 1,
    implementation: { path: 'src/NewField.vue', kind: 'COMPONENT', export: 'default' }, metadata: { supportedFieldTypes: ['TEXT'] } }
  app.put(relative, manifest)
  await until(() => contents().includes('NewField'), '新增清单生成字段策略', child)
  manifest.name = 'ChangedField'
  app.put(relative, manifest)
  await until(() => contents().includes('ChangedField'), '变更清单更新字段策略', child)
  assert.doesNotMatch(contents(), /NewField/)
  rmSync(path.join(app.root, relative))
  await until(() => contents().includes('export default []'), '删除清单移除字段策略', child)
  await stopAndAssertClean(app, child)
})

test('custom 清单监听错误会终止入口并清理全部开发进程组', async t => {
  const app = fixture(t)
  const child = app.dev()
  await until(() => app.events().filter(event => event.type === 'grandchild').length === 6, '全部进程启动', child)
  app.put('custom/biz-project/manifests/Invalid.extension.json', { schemaVersion: 1 })
  await until(() => child.exitCode !== null || child.signalCode !== null, '清单校验失败终止入口')
  assert.notEqual(child.exitCode, 0)
  assert.match(child.output, /缺少/)
  await until(() => app.events().every(event => !alive(event.pid)), '清理清单错误后的子进程组')
})

test('包 watcher 首轮构建失败时不启动应用并清理其余 watcher', async t => {
  const app = fixture(t)
  const child = app.dev('mobile', { FAKE_FAIL_WATCH: '@flow/workflow-mobile-ui' })
  await until(() => child.exitCode !== null || child.signalCode !== null, 'watcher 失败终止入口')
  assert.notEqual(child.exitCode, 0)
  assert.equal(app.events().some(event => event.type === 'dev-start' && event.workspace === 'workflow-mobile'), false)
  await until(() => app.events().every(event => !alive(event.pid)), '失败 watcher 的全部后代退出')
})

test('watcher 首轮构建期间切换模块不会启动旧选择的应用', async t => {
  const app = fixture(t)
  const child = app.dev('web', { FAKE_WATCH_DELAY: '250' })
  await until(() => app.events().some(event => event.type === 'watch-start'), '首轮 watcher 开始构建', child)
  app.select(['biz-purchase'])
  await until(() => app.events().some(event => event.type === 'dev-start' && event.workspace === 'workflow-web'), '最终选择的应用启动', child)
  const events = app.events()
  assert.equal(events.filter(event => event.type === 'dev-start' && event.workspace === 'workflow-web').length, 1)
  assert.ok(events.findIndex(event => event.type === 'watch-ready' && event.workspace === '@flow/biz-purchase') < events.findIndex(event => event.type === 'dev-start' && event.workspace === 'workflow-web'))
  assert.equal(events.some(event => event.type === 'app-missing-build'), false)
  await stopAndAssertClean(app, child)
})
