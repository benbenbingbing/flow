import test from 'node:test'
import assert from 'node:assert/strict'
import { cpSync, existsSync, mkdtempSync, readFileSync, readdirSync, rmSync } from 'node:fs'
import path from 'node:path'
import { tmpdir } from 'node:os'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'

const root = fileURLToPath(new URL('../..', import.meta.url))
const repositoryRoot = path.resolve(root, '..')
const dockerfile = readFileSync(path.join(root, 'workflow-web/Dockerfile'), 'utf8')
const ignores = readFileSync(path.join(root, '.dockerignore'), 'utf8').split('\n').map(line => line.trim()).filter(line => line && !line.startsWith('#'))

function included(filename) {
  const relative = path.relative(root, filename).split(path.sep).join('/')
  const segments = relative.split('/')
  return !ignores.some(pattern => segments.some((_, index) => path.matchesGlob(segments.slice(0, index + 1).join('/'), pattern)))
}

/** 根据实际 Dockerfile 的 COPY 顺序模拟构建输入；无需 Docker daemon，不能替代最终镜像构建。 */
function stageInputs(destination, stopAtInstall = false) {
  for (const line of dockerfile.split('\n')) {
    if (line.startsWith('FROM nginx')) break
    if (stopAtInstall && line === 'RUN npm ci') break
    if (!line.startsWith('COPY ')) continue
    const [, source, target] = line.split(/\s+/)
    const sources = source.includes('*') ? readdirSync(root).filter(name => path.matchesGlob(name, source)) : [source]
    for (const entry of sources) {
      const filename = path.join(root, entry)
      assert.ok(included(filename), `${entry} 被 .dockerignore 排除`)
      cpSync(filename, path.join(destination, source.includes('*') ? path.join(target, entry) : target), {
        recursive: true, filter: included
      })
    }
  }
}

test('Compose 和 CI 从前端工作区构建，Dockerfile 与锁文件路径可解析', () => {
  const compose = readFileSync(path.join(repositoryRoot, 'docker-compose.yml'), 'utf8')
  const web = compose.slice(compose.indexOf('\n  web:'))
  const context = /^\s+context:\s*(\S+)$/m.exec(web)?.[1]
  const file = /^\s+dockerfile:\s*(\S+)$/m.exec(web)?.[1]
  assert.equal(path.resolve(repositoryRoot, context), path.resolve(root))
  assert.equal(readFileSync(path.resolve(repositoryRoot, context, file), 'utf8'), dockerfile)
  for (const workflow of ['ci.yml', 'deploy.yml', 'security-audit.yml']) {
    const source = readFileSync(path.join(repositoryRoot, '.github/workflows', workflow), 'utf8')
    assert.match(source, /context: \.\/workflow-frontend\s+file: workflow-frontend\/workflow-web\/Dockerfile/)
    for (const [, lockfile] of source.matchAll(/cache-dependency-path:\s*(\S+)/g)) {
      assert.equal(path.resolve(repositoryRoot, lockfile), path.join(root, 'package-lock.json'))
      assert.ok(existsSync(path.resolve(repositoryRoot, lockfile)))
    }
  }
})

test('容器 npm ci 前包含全部 workspace 描述，并通过真实离线依赖计划校验', t => {
  const stage = mkdtempSync(path.join(tmpdir(), 'flow-container-install-'))
  t.after(() => rmSync(stage, { recursive: true, force: true }))
  stageInputs(stage, true)
  for (const folder of ['packages', 'custom']) {
    for (const entry of readdirSync(path.join(root, folder))) {
      if (!existsSync(path.join(root, folder, entry, 'package.json'))) continue
      assert.ok(existsSync(path.join(stage, folder, entry, 'package.json')), `缺少 workspace ${folder}/${entry}`)
    }
  }
  const install = spawnSync('npm', ['ci', '--dry-run', '--ignore-scripts', '--offline', '--no-audit', '--no-fund', '--cache', path.join(stage, '.npm-cache')], {
    cwd: stage, encoding: 'utf8', timeout: 60000, shell: process.platform === 'win32'
  })
  assert.equal(install.status, 0, install.error?.message || install.stderr || install.stdout)
})

test('容器构建输入支持默认配置和 none 选择，且不复制宿主产物', t => {
  const stage = mkdtempSync(path.join(tmpdir(), 'flow-container-build-'))
  t.after(() => rmSync(stage, { recursive: true, force: true }))
  stageInputs(stage)
  assert.ok(existsSync(path.join(stage, 'config/frontend-modules.json')))
  for (const excluded of ['node_modules', 'custom/biz-project/dist', 'workflow-web/dist', 'workflow-mobile/dist']) assert.equal(existsSync(path.join(stage, excluded)), false, excluded)
  const script = "import { selectedWorkspacePackages } from './scripts/workspace-packages.mjs'; console.log(JSON.stringify(selectedWorkspacePackages()))"
  const baseEnv = { ...process.env }
  delete baseEnv.FLOW_FRONTEND_MODULES
  for (const enabled of [undefined, 'none']) {
    const env = enabled ? { ...baseEnv, FLOW_FRONTEND_MODULES: enabled } : baseEnv
    const result = spawnSync(process.execPath, ['--input-type=module', '-e', script], { cwd: stage, env, encoding: 'utf8' })
    assert.equal(result.status, 0, result.stderr)
    const packages = JSON.parse(result.stdout)
    assert.ok(packages.includes('@flow/workflow-extensions'))
    const expected = enabled === 'none' ? [] : JSON.parse(readFileSync(path.join(stage, 'config/frontend-modules.json'))).enabledModules
    assert.deepEqual(packages.filter(name => name.startsWith('@flow/biz-')), expected.map(name => `@flow/${name}`))
  }
  assert.match(dockerfile, /ARG FLOW_FRONTEND_MODULES/)
  assert.match(dockerfile, /unset FLOW_FRONTEND_MODULES/)
})
