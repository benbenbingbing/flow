import test from 'node:test'
import assert from 'node:assert/strict'
import { chmodSync, copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'

const repository = fileURLToPath(new URL('../..', import.meta.url))
const productPackages = ['workflow-core', 'workflow-extension-sdk', 'workflow-api', 'workflow-mobile-ui']

/** 只替换服务启动与 PID 探测；真实 Bash 分派和模块选择在临时仓库中执行。 */
function fixture(t) {
  const root = mkdtempSync(path.join(tmpdir(), 'flow-start-frontend-'))
  t.after(() => rmSync(root, { recursive: true, force: true }))
  const frontendRoot = path.join(root, 'workflow-frontend')
  for (const name of ['start.sh', 'workflow-frontend/scripts/workspace-packages.mjs', 'workflow-frontend/scripts/extensions/modules.mjs']) {
    const target = path.join(root, name)
    mkdirSync(path.dirname(target), { recursive: true })
    copyFileSync(path.join(repository, name), target)
  }
  for (const name of [...productPackages, 'biz-project', 'biz-purchase']) {
    const folder = path.join(frontendRoot, 'packages', name)
    mkdirSync(folder, { recursive: true })
    writeFileSync(path.join(folder, 'package.json'), JSON.stringify({ name: `@flow/${name}` }))
  }
  for (const module of ['biz-project', 'biz-purchase']) mkdirSync(path.join(frontendRoot, 'extensions/manifests', module), { recursive: true })
  mkdirSync(path.join(frontendRoot, 'config'))
  writeFileSync(path.join(frontendRoot, 'config/frontend-modules.json'), JSON.stringify({ enabledModules: ['biz-project'] }))
  return { root, frontendRoot,
    run(body, env = {}) {
      return spawnSync('bash', ['--noprofile', '--norc', '-c', `source "$TEST_ROOT/start.sh"\n${body}`], {
        cwd: root, env: { PATH: process.env.PATH, TEST_ROOT: root, ...env }, encoding: 'utf8', timeout: 5_000
      })
    }
  }
}
function succeeded(result) {
  assert.equal(result.error, undefined)
  assert.equal(result.status, 0, `stdout:\n${result.stdout}\nstderr:\n${result.stderr}`)
}

const fakeWatchers = `
pid_is_running() { return 0; }
start_detached() {
    local package="\${3##*/}"
    printf '%s|%s|%s|%s\\n' "$package" "$4" "\${5:-}" "\${6:-}" >> "$ROOT_DIR/watchers"
    printf '100\\n' > "$1"
    printf '%s: built\\n' "$package" > "$2"
    mkdir -p "$3/dist"
    if [[ "$package" == biz-* ]]; then
        mkdir -p "$3/dist/pc"
        printf 'component' > "$3/dist/pc/entry.vue"
    else
        printf 'module' > "$3/dist/index.js"
    fi
}
`

test('start.sh 和 npm 共享 SDK/业务选择与依赖顺序，none 只保留产品包', t => {
  const app = fixture(t)
  const body = 'load_package_services; printf "%s\\n" "${PACKAGE_SERVICES[@]}"; printf "selection=%s\\n" "$FLOW_FRONTEND_MODULES"'
  let result = app.run(body)
  succeeded(result)
  assert.deepEqual(result.stdout.trim().split('\n'), [...productPackages, 'biz-project', 'selection=biz-project'])
  result = app.run(body, { FLOW_FRONTEND_MODULES: 'none' })
  succeeded(result)
  assert.deepEqual(result.stdout.trim().split('\n'), [...productPackages, 'selection=none'])
  result = app.run(body, { FLOW_FRONTEND_MODULES: 'biz-purchase' })
  succeeded(result)
  assert.deepEqual(result.stdout.trim().split('\n'), [...productPackages, 'biz-purchase', 'selection=biz-purchase'])
})

test('业务 watcher 按公开子入口构建就绪，不要求 dist/index.js', t => {
  const app = fixture(t)
  const result = app.run(`${fakeWatchers}\nstart_package_watchers`)
  succeeded(result)
  const lines = readFileSync(path.join(app.root, 'watchers'), 'utf8').trim().split('\n').map(line => line.split('|'))
  assert.deepEqual(lines.map(line => line[0]), [...productPackages, 'biz-project'])
  for (const [name, command, arg, flag] of lines) {
    if (name === 'workflow-mobile-ui') {
      assert.match(command, /node_modules\/\.bin\/vite$/)
      assert.equal(arg, 'build')
    } else {
      assert.equal(command, 'node')
      assert.match(arg, /scripts\/build-module-package\.mjs$/)
    }
    assert.equal(flag, '--watch')
  }
  assert.equal(existsSync(path.join(app.frontendRoot, 'packages/biz-project/dist/index.js')), false)
})

test('同一次启动固定模块快照，磁盘配置变化不改变构建与 watcher 的选择', t => {
  const app = fixture(t)
  const result = app.run(`${fakeWatchers}
load_package_services
printf '{"enabledModules":[]}' > "$FRONTEND_DIR/config/frontend-modules.json"
node --input-type=module -e 'import {selectedWorkspacePackages} from "./workflow-frontend/scripts/workspace-packages.mjs"; console.log(selectedWorkspacePackages().join(","))'
start_package_watchers
`)
  succeeded(result)
  assert.equal(result.stdout.trim(), [...productPackages, 'biz-project'].map(name => `@flow/${name}`).join(','))
  assert.equal(readFileSync(path.join(app.root, 'watchers'), 'utf8').includes('biz-project|'), true)
})

test('停止扫描已记录 PID，当前配置禁用或损坏也不会遗留旧业务 watcher', t => {
  const app = fixture(t)
  for (const name of ['workflow-extension-sdk', 'workflow-mobile-ui', 'biz-project']) {
    writeFileSync(path.join(app.frontendRoot, 'packages', name, 'watch.pid'), '123\n')
  }
  writeFileSync(path.join(app.frontendRoot, 'config/frontend-modules.json'), '{invalid')
  const result = app.run(`
stop_pid_file() { printf '%s|%s|%s\\n' "$2" "$3" "$4"; }
stop_package_watchers
`)
  succeeded(result)
  const lines = result.stdout.trim().split('\n').map(line => line.split('|'))
  assert.deepEqual(lines.map(line => line[0]).sort(), ['biz-project watch', 'workflow-extension-sdk watch', 'workflow-mobile-ui watch'].sort())
  for (const [name, command, directory] of lines) {
    assert.equal(directory, path.join(app.frontendRoot, 'packages', name.replace(/ watch$/, '')))
    assert.equal(command, name.startsWith('workflow-mobile-ui ') ? 'node_modules/.bin/vite' : 'build-module-package.mjs --watch')
  }
})

test('无效模块选择在停止旧应用和开始构建之前失败', t => {
  const app = fixture(t)
  const result = app.run(`
validate_start_environment() { :; }
validate_toolchain() { :; }
stop_application() { printf 'UNEXPECTED_STOP\\n'; }
build_application() { printf 'UNEXPECTED_BUILD\\n'; }
start_application
`, { FLOW_FRONTEND_MODULES: 'biz-missing' })
  assert.notEqual(result.status, 0)
  assert.doesNotMatch(result.stdout, /UNEXPECTED_/)
  assert.match(result.stderr, /未知前端业务模块/)
})

test('status 继续显示已禁用业务和 SDK 的已记录进程', t => {
  const app = fixture(t)
  writeFileSync(path.join(app.frontendRoot, 'packages/biz-project/watch.pid'), '123\n')
  const result = app.run(`
SERVER_PORT=8080; WEB_PORT=3000; MOBILE_PORT=3001
process_status() { printf '%s|%s\\n' "$1" "$2"; }
curl() { return 1; }
show_status
`, { FLOW_FRONTEND_MODULES: 'none' })
  succeeded(result)
  assert.match(result.stdout, /workflow-extension-sdk\|/)
  assert.match(result.stdout, /biz-project\|/)
})

test('完整启动分别在后端根和前端工作区构建，Vite 使用前端依赖及应用目录', t => {
  const app = fixture(t)
  mkdirSync(path.join(app.root, 'workflow-server'))
  const vite = path.join(app.frontendRoot, 'node_modules/.bin/vite')
  mkdirSync(path.dirname(vite), { recursive: true })
  writeFileSync(vite, '#!/bin/sh\nexit 0\n')
  chmodSync(vite, 0o755)
  const result = app.run(`
SERVER_PORT=8080; WEB_PORT=3000; MOBILE_PORT=3001
mvn() { printf 'mvn|%s|%s\\n' "$PWD" "$*" >> "$ROOT_DIR/builds"; }
npm() { printf 'npm|%s|%s\\n' "$PWD" "$*" >> "$ROOT_DIR/builds"; }
start_detached() { printf '%s|%s\\n' "$3" "$*" >> "$ROOT_DIR/servers"; }
build_application
start_web
start_mobile
`)
  succeeded(result)
  const builds = readFileSync(path.join(app.root, 'builds'), 'utf8').trim().split('\n').map(line => line.split('|'))
  assert.deepEqual(builds.map(([command, cwd]) => [command, cwd]), [
    ['mvn', path.join(app.root, 'workflow-server')], ['npm', app.frontendRoot], ['npm', app.frontendRoot]
  ])
  const servers = readFileSync(path.join(app.root, 'servers'), 'utf8').trim().split('\n').map(line => line.split('|'))
  assert.deepEqual(servers.map(([cwd]) => cwd), ['workflow-web', 'workflow-mobile'].map(name => path.join(app.frontendRoot, name)))
  for (const [, command] of servers) assert.ok(command.includes(vite))
})
