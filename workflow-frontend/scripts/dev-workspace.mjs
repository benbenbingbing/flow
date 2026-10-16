import { spawn } from 'node:child_process'
import { watch } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { discoverExtensions, manifestDirectory } from './extensions/discover.mjs'
import { writeFieldDefinitions } from './extensions/generate.mjs'
import { moduleConfigFile } from './extensions/modules.mjs'
import { selectedWorkspacePackages } from './workspace-packages.mjs'

const target = process.argv[2]
if (!['web', 'mobile'].includes(target)) throw new Error('用法：node scripts/dev-workspace.mjs web|mobile')
const root = fileURLToPath(new URL('..', import.meta.url))
const coreRoot = fileURLToPath(new URL('../packages/workflow-core', import.meta.url))
const children = new Set()
let stopping = false
let manifestWatcher
let configWatcher
let refreshTimer
let restartTimer
let activeSelection
const restartingChildren = new WeakSet()
let restartPromise = Promise.resolve()

/** npm 会再启动 Vite/包监听器，必须回收整个进程组，不能只结束 npm 本身。 */
function terminate(child) {
  if (!child.pid) return
  try {
    if (process.platform === 'win32') child.kill('SIGTERM')
    else process.kill(-child.pid, 'SIGTERM')
  } catch (error) { if (error.code !== 'ESRCH') console.error(error) }
}

/** 关闭文件监听并终止全部子任务；构建中收到退出信号也不会继续启动应用。 */
function stop(code = 0) {
  if (stopping) return
  stopping = true
  clearTimeout(refreshTimer)
  clearTimeout(restartTimer)
  manifestWatcher?.close()
  configWatcher?.close()
  for (const child of children) terminate(child)
  process.exitCode = code
}

/** 构建和开发使用同一份模块快照，避免磁盘配置在两个 npm 调用之间变化。 */
function run(args, { persistent = false, selection } = {}) {
  const modules = selection?.filter(name => name.startsWith('@flow/biz-')).map(name => name.slice('@flow/'.length))
  const env = modules ? { ...process.env, FLOW_FRONTEND_MODULES: modules.join(',') || 'none' } : process.env
  const child = spawn('npm', args, { cwd: root, stdio: 'inherit', detached: process.platform !== 'win32', env })
  children.add(child)
  return new Promise((resolve, reject) => {
    child.once('error', error => {
      children.delete(child)
      reject(error)
    })
    child.once('exit', (code, signal) => {
      // leader 意外退出后进程组内仍可能有后代；删除句柄前回收，避免 stop 漏掉它们。
      if (persistent) terminate(child)
      children.delete(child)
      if (stopping || restartingChildren.has(child)) return resolve()
      const exitCode = code ?? (signal ? 1 : 0)
      if (persistent) stop(exitCode)
      if (exitCode === 0) resolve()
      else reject(new Error(`npm ${args.join(' ')} 已退出 (${code ?? signal})`))
    })
  })
}

/**
 * 模块选择变化时先停止旧进程，再构建稳定的选择快照，最后启动监听和应用。
 * 构建期间的配置变化会重新构建；应用绝不依赖业务 watcher 与 Vite 的启动时序。
 */
async function startWorkspace() {
  const oldChildren = [...children]
  await Promise.all(oldChildren.map(child => new Promise(resolve => {
    restartingChildren.add(child)
    child.once('exit', resolve)
    terminate(child)
  })))
  while (!stopping) {
    const selection = selectedWorkspacePackages()
    await run(['run', 'build:packages'], { selection })
    if (stopping) return
    if (JSON.stringify(selection) !== JSON.stringify(selectedWorkspacePackages())) continue
    activeSelection = JSON.stringify(selection)
    for (const workspace of selection) {
      run(['run', 'dev', '--workspace', workspace], { persistent: true, selection })
        .catch(error => { console.error(error); stop(1) })
    }
    run(['run', 'dev', '--workspace', `workflow-${target}`, '--', ...process.argv.slice(3)], { persistent: true, selection })
      .catch(error => { console.error(error); stop(1) })
    return
  }
}

for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => stop())
try {
  // 首次构建前就监听，冷启动期间的原子保存也不会漏掉；重启请求串行执行。
  manifestWatcher = watch(manifestDirectory, { recursive: true }, () => {
    clearTimeout(refreshTimer)
    refreshTimer = setTimeout(() => {
      try { writeFieldDefinitions(discoverExtensions(coreRoot, { platform: 'metadata' }), coreRoot) }
      catch (error) { console.error(error); stop(1) }
    }, 80)
  })
  configWatcher = watch(new URL('../config', import.meta.url), (_, filename) => {
    if (String(filename) !== moduleConfigFile.split('/').at(-1)) return
    clearTimeout(restartTimer)
    restartTimer = setTimeout(() => {
      restartPromise = restartPromise.then(() => {
        // 构建循环可能已经处理到最终选择；跳过过期请求，避免刚启动又重复重启。
        if (!stopping && activeSelection !== JSON.stringify(selectedWorkspacePackages())) return startWorkspace()
      }).catch(error => { console.error(error); stop(1) })
    }, 150)
  })
  for (const watcher of [manifestWatcher, configWatcher]) {
    watcher.on('error', error => { console.error(error); stop(1) })
  }
  restartPromise = startWorkspace()
  await restartPromise
} catch (error) { console.error(error); stop(1) }
