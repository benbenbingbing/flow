import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = fileURLToPath(new URL('..', import.meta.url))
const repositoryRoot = execFileSync('git', ['rev-parse', '--show-toplevel'], { cwd: root, encoding: 'utf8' }).trim()
const frontendPrefix = path.relative(repositoryRoot, root).split(path.sep).join('/')
// 业务抽包后仍受同一维护预算约束；自动发现后续新增包，避免搬目录绕过检查。
const paths = ['workflow-web/src', 'workflow-mobile/src', ...['packages', 'custom'].flatMap(category =>
  (existsSync(path.join(root, category)) ? readdirSync(path.join(root, category), { withFileTypes: true }) : [])
    .filter(entry => entry.isDirectory() && existsSync(path.join(root, category, entry.name, 'src')))
    .map(entry => `${category}/${entry.name}/src`))]
const defaultLimit = 900
const inputBase = process.env.FRONTEND_BUDGET_BASE || 'HEAD'
const requestedBase = /^0+$/.test(inputBase) ? 'HEAD^' : inputBase
const git = args => execFileSync('git', ['-c', 'core.fsmonitor=false', ...args], { cwd: repositoryRoot, encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 })
// 不能静默忽略不存在的基线；CI 必须检出基线历史，避免把当前新增的大文件误算成旧债务。
const base = git(['rev-parse', '--verify', `${requestedBase}^{commit}`]).trim()
const eligible = file => /\.(vue|js|ts)$/.test(file)
  && !/(\/__tests__\/|\.spec\.|\/generated\/|\/data\/user-manual\/)/.test(file)
const count = source => source ? source.split('\n').length - (source.endsWith('\n') ? 1 : 0) : 0
function walk(folder) {
  return readdirSync(path.join(root, folder), { withFileTypes: true }).flatMap(entry => {
    const filename = `${folder}/${entry.name}`
    return entry.isDirectory() ? walk(filename) : eligible(filename) ? [filename] : []
  })
}
// Git 对象路径始终相对仓库根。迁移前的基线仍按旧工作区路径读取，不能把搬迁后的
// 历史大文件误当新文件；基线已含新工作区时不再回退，以免后续新增文件继承旧额度。
const baselineHasFrontend = frontendPrefix && git(['ls-tree', '--name-only', base, '--', `${frontendPrefix}/package.json`]).trim()
const baselinePath = file => baselineHasFrontend ? `${frontendPrefix}/${file}` : file
const previousFiles = new Set(git(['ls-tree', '-r', '--name-only', base, '--', ...paths.map(baselinePath)]).trim().split('\n'))
let debt = 0, reduced = 0
const issues = []
for (const file of paths.flatMap(walk)) {
  const current = count(readFileSync(path.join(root, file), 'utf8'))
  const previousFile = baselinePath(file)
  const previous = previousFiles.has(previousFile) ? count(git(['show', `${base}:${previousFile}`])) : 0
  const limit = Math.max(defaultLimit, previous)
  if (current > defaultLimit) debt += 1
  if (previous > defaultLimit && current < previous) reduced += 1
  if (current > limit) issues.push(`${file}: ${current} 行 > ${limit} 行（新文件上限 ${defaultLimit}；历史文件禁止继续增长）`)
}
if (issues.length) throw new Error(`前端增量维护预算失败：\n${issues.join('\n')}`)
console.log(`前端增量预算通过：剩余大文件 ${debt}，本次缩减 ${reduced}；基线 ${base.slice(0, 8)}。旧严格预算仍用于报告历史治理目标。`)
