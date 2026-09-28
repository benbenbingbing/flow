import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { babelParse, parse } from '@vue/compiler-sfc'
import { computed, reactive, ref } from 'vue'
import { authRouteLocation, resolveLoginRedirect } from '../../shared/login-redirect.js'

const source = await readFile(new URL('../Login.vue', import.meta.url), 'utf8')
const script = parse(source).descriptor.scriptSetup.content
const ast = babelParse(script, { sourceType: 'module' })
const code = ast.program.body.filter(node => node.type !== 'ImportDeclaration')
  .map(node => script.slice(node.start, node.end)).join('\n')

/** 执行实际登录页面脚本，覆盖登录失败重试与强制改密中转，而非仅检查 helper 返回值。 */
function createPage({ redirect, required = false, fail = false } = {}) {
  const calls = [], route = { query: { redirect } }
  let shouldFail = fail
  const values = {
    computed, reactive, ref, authRouteLocation, resolveLoginRedirect,
    useRoute: () => route,
    useRouter: () => ({ replace: async location => calls.push(['navigate', location]) }),
    useUserStore: () => ({ applySession: value => calls.push(['session', value]), setPermissions: value => calls.push(['permissions', value]) }),
    login: async () => { if (shouldFail) throw Error('密码错误'); return { token: 'test-token', passwordResetRequired: required } },
    getPermissions: async () => ['process:view'],
    ElMessage: { success() {}, error: message => calls.push(['error', message]) },
    console: { error() {} }
  }
  const page = new Function(...Object.keys(values), `${code}\nreturn { handleLogin, loginForm, loginFormRef, loading }`)(...Object.values(values))
  page.loginFormRef.value = { validate: async () => true }
  Object.assign(page.loginForm, { username: 'test', password: 'test-password' })
  return { ...page, calls, route, allowLogin: () => { shouldFail = false } }
}

const target = '/process/progress/instance-1?taskId=task-1#history'
const retry = createPage({ redirect: target, fail: true })
await retry.handleLogin()
assert.deepEqual(retry.calls, [['error', '密码错误']])
assert.equal(retry.route.query.redirect, target)
retry.allowLogin()
await retry.handleLogin()
assert.deepEqual(retry.calls.at(-1), ['navigate', target])
assert.equal(retry.loading.value, false)

const reset = createPage({ redirect: target, required: true })
await reset.handleLogin()
assert.deepEqual(reset.calls.at(-1), ['navigate', { path: '/change-password', query: { redirect: target } }])
assert.deepEqual(reset.calls.find(call => call[0] === 'permissions'), ['permissions', []])
for (const redirect of [undefined, '//example.com', '/login', '/change-password']) {
  const page = createPage({ redirect })
  await page.handleLogin()
  assert.deepEqual(page.calls.at(-1), ['navigate', '/'])
}
console.log('login page return target tests passed')
