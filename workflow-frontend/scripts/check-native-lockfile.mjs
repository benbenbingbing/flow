import { readFileSync } from 'node:fs'
import path from 'node:path'

const lockfile = process.argv[2] ?? new URL('../package-lock.json', import.meta.url)
const { packages } = JSON.parse(readFileSync(lockfile, 'utf8'))
const nativePackage = /^(?:@rolldown\/binding-|lightningcss-|@parcel\/watcher-)/
const errors = []
let checked = 0

/** 按 Node 的就近解析规则读取锁定依赖，兼容 workspace 内嵌依赖和根目录提升后的依赖。 */
function resolveLockedDependency(owner, name) {
  let directory = owner
  while (true) {
    if (path.posix.basename(directory) !== 'node_modules') {
      const candidate = path.posix.join(directory, 'node_modules', name)
      if (packages[candidate]) return packages[candidate]
    }
    if (directory === '.') return undefined
    directory = path.posix.dirname(directory)
  }
}

// npm 可能从本机 node_modules 生成只含当前平台原生包的锁文件。
// 检查所有声明的平台，才能在 macOS 上提前发现 GitHub Linux GNU 和 Alpine musl 缺包。
for (const [owner, entry] of Object.entries(packages)) {
  for (const [name, version] of Object.entries(entry.optionalDependencies ?? {})) {
    if (!nativePackage.test(name)) continue
    checked += 1
    const dependency = resolveLockedDependency(owner, name)
    if (!dependency || dependency.version !== version || !dependency.resolved || !dependency.integrity) {
      errors.push(`${owner}: 缺少完整的 ${name}@${version} 锁定记录`)
    }
  }
}

if (!checked) errors.push('未找到构建工具的原生依赖声明，请检查 package-lock.json 是否完整')
if (errors.length) {
  console.error(errors.join('\n'))
  console.error('请在无 node_modules 的隔离目录中修复跨平台锁文件，再提交 package-lock.json；不要仅在 CI 临时安装缺失包。')
  process.exitCode = 1
} else {
  console.log(`原生依赖锁文件检查通过（${checked} 项，包含 Linux GNU / musl）`)
}
