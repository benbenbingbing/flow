import { spawn } from 'node:child_process'
import { watch } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { discoverExtensions, manifestDirectory } from './extensions/discover.mjs'
import { writeFieldDefinitions } from './extensions/generate.mjs'

const target = process.argv[2]
if (!['web', 'mobile'].includes(target)) throw new Error('用法：node scripts/dev-workspace.mjs web|mobile')
const root = fileURLToPath(new URL('..', import.meta.url))
const coreRoot = fileURLToPath(new URL('../packages/workflow-core', import.meta.url))
const children = new Set()
let stopping = false
let manifestWatcher
let refreshTimer
function stop(code = 0) {
  if (stopping) return
  stopping = true
  clearTimeout(refreshTimer)
  manifestWatcher?.close()
  for (const child of children) {
    try {
      if (process.platform === 'win32') child.kill('SIGTERM')
      else process.kill(-child.pid, 'SIGTERM')
    } catch (error) { if (error.code !== 'ESRCH') console.error(error) }
  }
  process.exitCode = code
}
function run(args, persistent = false) {
  const child = spawn('npm', args, { cwd: root, stdio: 'inherit', detached: process.platform !== 'win32' })
  children.add(child)
  return new Promise((resolve, reject) => {
    child.once('error', reject)
    child.once('exit', (code, signal) => {
      children.delete(child)
      if (stopping) return resolve()
      if (persistent) stop(code || 0)
      if (code === 0) resolve()
      else reject(new Error(`npm ${args.join(' ')} 已退出 (${code ?? signal})`))
    })
  })
}
for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => stop())
try {
  // 先生成完整 dist，再同时启动监听和应用，冷启动不会解析到缺失/残留产物。
  await run(['run', 'build:packages'])
  if (!stopping) {
    manifestWatcher = watch(manifestDirectory, { recursive: true }, () => {
      clearTimeout(refreshTimer)
      refreshTimer = setTimeout(() => {
        try { writeFieldDefinitions(discoverExtensions(coreRoot, { platform: 'metadata' }), coreRoot) }
        catch (error) { console.error(error); stop(1) }
      }, 80)
    })
    await Promise.all([
      ...['@flow/workflow-core', '@flow/workflow-api', '@flow/workflow-mobile-ui'].map(workspace =>
        run(['run', 'dev', '--workspace', workspace], true)),
      run(['run', 'dev', '--workspace', `workflow-${target}`, '--', ...process.argv.slice(3)], true)
    ])
  }
} catch (error) { console.error(error); stop(1) }
