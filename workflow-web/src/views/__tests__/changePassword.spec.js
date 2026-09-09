import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { babelParse, parse } from '@vue/compiler-sfc'
import { computed, reactive, ref } from 'vue'

const source = await readFile(new URL('../ChangePassword.vue', import.meta.url), 'utf8')
const script = parse(source).descriptor.scriptSetup.content
const ast = babelParse(script, { sourceType: 'module' })
const code = ast.program.body.filter(node => node.type !== 'ImportDeclaration')
  .map(node => script.slice(node.start, node.end)).join('\n')

/** 执行真实页面提交逻辑，验证改密成功才退出，失败仍保留可重试的登录态。 */
function createPage({ required = true, validate = async () => true, change = async () => null } = {}) {
  const calls = []
  const userStore = {
    token: 'current-session',
    userInfo: { passwordResetRequired: required },
    permissions: required ? [] : ['entity:definition:view'],
    logout() {
      calls.push('clear-session')
      this.token = ''
      this.userInfo = null
      this.permissions = []
    },
    applySession() { assert.fail('改密成功不得自动保存新登录会话') }
  }
  const dependencies = {
    computed, reactive, ref,
    useUserStore: () => userStore,
    useRouter: () => ({ replace: async path => { calls.push(['navigate', path]) } }),
    ElMessage: { success: message => { calls.push(['success', message]) } },
    changePassword: async data => { calls.push(['change', data]); return change() },
    getPermissions: () => { assert.fail('重新登录前不得请求业务权限') },
    logout: () => { assert.fail('改密接口已撤销服务端会话，无需额外退出请求') }
  }
  const page = new Function(...Object.keys(dependencies),
    `${code}\nreturn { submit, form, formRef, submitting }`
  )(...Object.values(dependencies))
  page.formRef.value = { validate }
  Object.assign(page.form, {
    currentPassword: 'CurrentPass123',
    newPassword: 'UpdatedPass456',
    confirmPassword: 'UpdatedPass456'
  })
  return { ...page, calls, userStore }
}

for (const required of [true, false]) {
  const page = createPage({ required })
  await page.submit()
  assert.deepEqual(page.calls, [
    ['change', { currentPassword: 'CurrentPass123', newPassword: 'UpdatedPass456' }],
    'clear-session',
    ['success', '密码已修改，请使用新密码重新登录'],
    ['navigate', '/login']
  ], '强制改密和主动改密都必须清理会话并返回登录页')
  assert.equal(page.userStore.token, '')
  assert.equal(page.userStore.userInfo, null)
  assert.deepEqual(page.userStore.permissions, [])
  assert.equal(page.submitting.value, false)
}

const failure = new Error('当前密码不正确')
const failed = createPage({ change: async () => { throw failure } })
await assert.rejects(failed.submit(), error => error === failure)
assert.equal(failed.calls.length, 1, '改密失败不能退出或跳转')
assert.equal(failed.userStore.token, 'current-session')
assert.equal(failed.submitting.value, false)

const invalid = createPage({ validate: async () => { throw new Error('校验失败') } })
await assert.rejects(invalid.submit(), /校验失败/)
assert.deepEqual(invalid.calls, [], '表单无效时不能改密或清理会话')
assert.equal(invalid.submitting.value, false)

// 等待表单校验期间也须禁止重复提交，避免第一次改密撤销会话后第二次请求报鉴权失败。
let releaseValidation
const pending = createPage({ validate: () => new Promise(resolve => { releaseValidation = resolve }) })
const first = pending.submit()
await pending.submit()
assert.deepEqual(pending.calls, [])
releaseValidation(true)
await first
assert.equal(pending.calls.filter(call => call[0] === 'change').length, 1)

console.log('change password relogin tests passed')
