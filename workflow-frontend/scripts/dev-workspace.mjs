import { spawn } from 'node:child_process'
import { existsSync, watch } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { discoverExtensions, extensionWatchDirectories, isExtensionManifestChange } from './extensions/discover.mjs'
import { writeFieldDefinitions } from './extensions/generate.mjs'
import { moduleConfigFile } from './extensions/modules.mjs'
import { selectedWorkspacePackages } from './workspace-packages.mjs'

const target = process.argv[2]
if (!['web', 'mobile'].includes(target)) throw new Error('用法：node scripts/dev-workspace.mjs web|mobile')
const root = fileURLToPath(new URL('..', import.meta.url))
const coreRoot = fileURLToPath(new URL('../packages/workflow-core', import.meta.url))
const children = new Set()
let stopping = false
const manifestWatchers = new Map()
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
  for (const watcher of manifestWatchers.values()) watcher.close()
  configWatcher?.close()
  for (const child of children) terminate(child)
  process.exitCode = code
}

/**
 * 构建和开发使用同一份模块快照；包 watcher 必须报告首次 built 后才允许消费者启动。
 * Vite 的 watch 会重新清理 dist，仅等待前面的生产构建不足以保证开发入口可用。
 */
function run(args, { persistent = false, selection, waitForReady = false } = {}) {
  const modules = selection?.filter(name => name.startsWith('@flow/biz-')).map(name => name.slice('@flow/'.length))
  const env = modules ? { ...process.env, FLOW_FRONTEND_MODULES: modules.join(',') || 'none' } : process.env
  const child = spawn('npm', args, { cwd: root, stdio: waitForReady ? ['inherit', 'pipe', 'pipe'] : 'inherit', detached: process.platform !== 'win32', env })
  children.add(child)
  return new Promise((resolve, reject) => {
    let ready = false
    let readinessTimer
    if (waitForReady) {
      let output = ''
      readinessTimer = setTimeout(() => {
        reject(new Error(`npm ${args.join(' ')} 首次构建未在 30 秒内就绪`))
        stop(1)
      }, 30_000)
      child.stdout.on('data', data => {
        process.stdout.write(data)
        output = (output + data.toString()).slice(-8_192)
        if (!ready && /\bbuilt(?: in|\s*$)/m.test(output)) {
          ready = true
          clearTimeout(readinessTimer)
          resolve()
        }
      })
      child.stderr.on('data', data => process.stderr.write(data))
    }
    child.once('error', error => {
      clearTimeout(readinessTimer)
      children.delete(child)
      if (persistent) stop(1)
      reject(error)
    })
    child.once('exit', (code, signal) => {
      clearTimeout(readinessTimer)
      // leader 意外退出后进程组内仍可能有后代；删除句柄前回收，避免 stop 漏掉它们。
      if (persistent) terminate(child)
      children.delete(child)
      if (stopping || restartingChildren.has(child)) return resolve()
      const exitCode = waitForReady && !ready ? 1 : code ?? (signal ? 1 : 0)
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
  while (!stopping) {
    const oldChildren = [...children]
    await Promise.all(oldChildren.map(child => new Promise(resolve => {
      restartingChildren.add(child)
      child.once('exit', resolve)
      terminate(child)
    })))
    if (stopping) return
    const selection = selectedWorkspacePackages()
    await run(['run', 'build:packages'], { selection })
    if (stopping) return
    if (JSON.stringify(selection) !== JSON.stringify(selectedWorkspacePackages())) continue
    await Promise.all(selection.map(workspace => run(['run', 'dev', '--workspace', workspace], {
      persistent: true, selection, waitForReady: true
    })))
    if (stopping) return
    // watcher 就绪期间也可能切换配置；本轮尚未启动应用，先清理再重建最终选择。
    if (JSON.stringify(selection) !== JSON.stringify(selectedWorkspacePackages())) continue
    activeSelection = JSON.stringify(selection)
    run(['run', 'dev', '--workspace', `workflow-${target}`, '--', ...process.argv.slice(3)], { persistent: true, selection })
      .catch(error => { console.error(error); stop(1) })
    return
  }
}

for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => stop())
try {
  // 首次构建前就监听，冷启动期间的原子保存也不会漏掉；重启请求串行执行。
  const refreshManifests = () => {
    clearTimeout(refreshTimer)
    refreshTimer = setTimeout(() => {
      try { writeFieldDefinitions(discoverExtensions(coreRoot, { platform: 'metadata' }), coreRoot) }
      catch (error) { console.error(error); stop(1) }
    }, 80)
  }
  const watchManifests = directory => {
    if (!existsSync(directory)) {
      manifestWatchers.get(directory)?.close()
      manifestWatchers.delete(directory)
      return
    }
    if (manifestWatchers.has(directory)) return
    const watcher = watch(directory, { recursive: true }, (_, filename) => {
      if (!filename || isExtensionManifestChange(path.join(directory, String(filename)))) refreshManifests()
    })
    watcher.on('error', error => { console.error(error); stop(1) })
    manifestWatchers.set(directory, watcher)
  }
  for (const directory of extensionWatchDirectories()) watchManifests(directory)
  // 纯产品交付可以没有 custom；监听根目录本身，后来新建 custom 时再补上递归监听。
  const directoryWatcher = watch(root, (_, filename) => {
    if (String(filename) !== 'custom') return
    try {
      for (const directory of extensionWatchDirectories()) watchManifests(directory)
      refreshManifests()
    } catch (error) { console.error(error); stop(1) }
  })
  directoryWatcher.on('error', error => { console.error(error); stop(1) })
  manifestWatchers.set(root, directoryWatcher)
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
  configWatcher.on('error', error => { console.error(error); stop(1) })
  restartPromise = startWorkspace()
  await restartPromise
} catch (error) { console.error(error); stop(1) }
