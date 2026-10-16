import test from 'node:test'
import assert from 'node:assert/strict'
import { cpSync, mkdirSync, mkdtempSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'

const frontend = fileURLToPath(new URL('../..', import.meta.url))

/** 用真实 Git 基线验证迁移前后预算，避免路径变化导致历史额度丢失或新文件绕过上限。 */
function fixture(t) {
  const root = mkdtempSync(path.join(tmpdir(), 'flow-frontend-layout-'))
  t.after(() => rmSync(root, { recursive: true, force: true }))
  const git = (...args) => {
    const result = spawnSync('git', ['-c', 'core.fsmonitor=false', '-c', 'user.name=Layout Test', '-c', 'user.email=layout@example.invalid', ...args], { cwd: root, encoding: 'utf8' })
    assert.equal(result.status, 0, result.stderr)
  }
  const put = (name, content) => {
    mkdirSync(path.dirname(path.join(root, name)), { recursive: true })
    writeFileSync(path.join(root, name), content)
  }
  git('init', '--quiet')
  put('package.json', '{"private":true,"workspaces":["workflow-web","workflow-mobile","packages/*"]}\n')
  put('workflow-web/src/Historical.vue', '<!-- existing -->\n'.repeat(940))
  git('add', '.')
  git('commit', '--quiet', '-m', 'Before frontend directory')
  const workspace = path.join(root, 'workflow-frontend')
  mkdirSync(workspace)
  for (const name of ['package.json', 'workflow-web']) renameSync(path.join(root, name), path.join(workspace, name))
  mkdirSync(path.join(workspace, 'workflow-mobile/src'), { recursive: true })
  mkdirSync(path.join(workspace, 'packages'), { recursive: true })
  mkdirSync(path.join(workspace, 'scripts'))
  cpSync(path.join(frontend, 'scripts/check-frontend-budget.mjs'), path.join(workspace, 'scripts/check-frontend-budget.mjs'))
  const run = () => spawnSync(process.execPath, ['scripts/check-frontend-budget.mjs'], {
    cwd: workspace, encoding: 'utf8', env: { ...process.env, FRONTEND_BUDGET_BASE: 'HEAD' }
  })
  return { put, git, run }
}

test('整体迁移继承旧路径的历史预算，但不能继续扩大文件', t => {
  const f = fixture(t)
  const unchanged = f.run()
  assert.equal(unchanged.status, 0, unchanged.stderr)
  f.put('workflow-frontend/workflow-web/src/Historical.vue', '<!-- growth -->\n'.repeat(941))
  const grown = f.run()
  assert.notEqual(grown.status, 0)
  assert.match(grown.stderr, /941 行 > 940 行/)
})

test('迁移后的基线使用新路径；新增文件仍执行 900 行上限', t => {
  const f = fixture(t)
  f.git('add', '.')
  f.git('commit', '--quiet', '-m', 'Frontend workspace directory')
  assert.equal(f.run().status, 0)
  f.put('workflow-frontend/workflow-mobile/src/New.vue', '<!-- new -->\n'.repeat(901))
  const result = f.run()
  assert.notEqual(result.status, 0)
  assert.match(result.stderr, /New.vue: 901 行 > 900 行/)
})

test('前端 workspace 保持同层包布局与单一锁文件解析', () => {
  const manifest = JSON.parse(readFileSync(path.join(frontend, 'package.json'), 'utf8'))
  const lock = JSON.parse(readFileSync(path.join(frontend, 'package-lock.json'), 'utf8'))
  assert.deepEqual(manifest.workspaces, ['workflow-web', 'workflow-mobile', 'packages/*'])
  for (const target of ['workflow-web', 'workflow-mobile', 'packages/workflow-core', 'packages/biz-project']) {
    const pkg = JSON.parse(readFileSync(path.join(frontend, target, 'package.json'), 'utf8'))
    assert.equal(lock.packages[`node_modules/${pkg.name}`]?.resolved, target)
    assert.equal(lock.packages[`node_modules/${pkg.name}`]?.link, true)
  }
})
