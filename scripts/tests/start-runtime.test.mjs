import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const startScript = fileURLToPath(new URL('../../start.sh', import.meta.url))

/**
 * 每个用例在临时仓库副本中执行入口，避免测试 source 或进程管理行为时接触本地服务和 .env。
 * 所有进程启动都由调用方替换；JAR 使用可辨识的字节内容验证构建后运行快照是否保持不变。
 */
function fixture(t) {
  const root = mkdtempSync(path.join(tmpdir(), 'flow-start-runtime-'))
  t.after(() => rmSync(root, { recursive: true, force: true }))
  copyFileSync(startScript, path.join(root, 'start.sh'))
  const serverJar = path.join(root, 'workflow-server/workflow-app/target/workflow-server-1.0.0.jar')
  const migratorJar = path.join(root, 'workflow-server/workflow-db-migrator/target/workflow-db-migrator-1.0.0-exec.jar')
  for (const jar of [serverJar, migratorJar]) {
    mkdirSync(path.dirname(jar), { recursive: true })
    writeFileSync(jar, 'build-v1')
  }
  return {
    root,
    serverJar,
    migratorJar,
    run(body) {
      return spawnSync('bash', ['--noprofile', '--norc', '-c', `source "$TEST_ROOT/start.sh"\n${body}`], {
        cwd: root,
        env: { PATH: process.env.PATH, TEST_ROOT: root },
        encoding: 'utf8',
        timeout: 10_000
      })
    }
  }
}

function assertSucceeded(result) {
  assert.equal(result.error, undefined)
  assert.equal(result.status, 0, `stdout:\n${result.stdout}\nstderr:\n${result.stderr}`)
}

test('sourcing the startup helpers does not load environment or launch services', t => {
  const app = fixture(t)
  const result = app.run('printf "helpers-loaded\\n"')
  assertSucceeded(result)
  assert.equal(result.stdout, 'helpers-loaded\n')
})

for (const [service, jarVariable, jarProperty] of [
  ['server', 'SERVER_JAR', 'serverJar'],
  ['schema_worker', 'MIGRATOR_JAR', 'migratorJar']
]) {
  test(`${service} runs a unique snapshot that survives rebuilt and deleted target output`, t => {
    const app = fixture(t)
    const result = app.run(`
start_detached() {
    printf '%s\\n' "\${!#}" >> "$ROOT_DIR/launched-jars"
}
start_${service}
printf 'build-v2' > "$${jarVariable}"
start_${service}
rm "$${jarVariable}"
`)
    assertSucceeded(result)
    const [first, second] = readFileSync(path.join(app.root, 'launched-jars'), 'utf8').trim().split('\n')
    assert.ok(first && second)
    assert.notEqual(first, second, 'a later launch must never overwrite the running snapshot')
    for (const snapshot of [first, second]) {
      assert.notEqual(snapshot, app[jarProperty])
      assert.equal(path.basename(snapshot), path.basename(app[jarProperty]))
      assert.ok(snapshot.startsWith(`${app.root}${path.sep}`), 'runtime files belong to the test repository')
      assert.ok(!snapshot.includes(`${path.sep}target${path.sep}`), 'mvn clean must not remove runtime snapshots')
    }
    assert.equal(readFileSync(first, 'utf8'), 'build-v1')
    assert.equal(readFileSync(second, 'utf8'), 'build-v2')
  })
}

test('a missing server artifact fails before attempting to launch Java', t => {
  const app = fixture(t)
  rmSync(app.serverJar)
  const result = app.run(`
start_detached() {
    printf 'UNEXPECTED_LAUNCH\\n'
}
start_server
`)
  assert.notEqual(result.status, 0)
  assert.ok(!result.stdout.includes('UNEXPECTED_LAUNCH'))
})

// 只替换外部操作，保留真实分派、stop_server 和退出清理，验证恢复边界而非真实停止服务。
const backendStubs = `
record() { printf '%s\\n' "$1" >> "$ROOT_DIR/operations"; }
load_environment() { SERVER_PORT=18080; }
validate_start_environment() { :; }
require_command() { :; }
stop_pid_file() { record "stop-pid:$2"; }
stop_owned_listener() { record "stop-listener:$2"; }
wait_for_port_release() { record "wait-port:$2"; }
start_server() { record 'start:server'; }
wait_for_process() { record "ready:$1"; }
for unrelated in start_application stop_application start_mysql build_application run_migrations start_schema_worker start_web start_mobile start_package_watchers; do
    eval "$unrelated() { record 'UNEXPECTED:$unrelated'; return 91; }"
done
action=restart-backend
`

const serverStopOperations = ['stop-pid:server', 'stop-listener:server', 'wait-port:server']

test('restart-backend uses existing build and only stops, starts and waits for the server', t => {
  const app = fixture(t)
  const result = app.run(`${backendStubs}\nmain`)
  assertSucceeded(result)
  assert.deepEqual(readFileSync(path.join(app.root, 'operations'), 'utf8').trim().split('\n'), [
    ...serverStopOperations, 'start:server', 'ready:server'
  ])
})

test('failed backend readiness only cleans up the server and retains the failing exit status', t => {
  const app = fixture(t)
  const result = app.run(`${backendStubs}
wait_for_process() { record "ready:$1"; return 17; }
main
`)
  assert.equal(result.status, 17)
  assert.deepEqual(readFileSync(path.join(app.root, 'operations'), 'utf8').trim().split('\n'), [
    ...serverStopOperations, 'start:server', 'ready:server', ...serverStopOperations
  ])
})

test('restart-backend keeps the running server untouched when no build is available', t => {
  const app = fixture(t)
  rmSync(app.serverJar)
  const result = app.run(`${backendStubs}\nmain`)
  assert.notEqual(result.status, 0)
  assert.throws(() => readFileSync(path.join(app.root, 'operations')), { code: 'ENOENT' })
})
