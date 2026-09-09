import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { babelParse } from '@vue/compiler-sfc'

const source = await readFile(new URL('../index.js', import.meta.url), 'utf8')
const ast = babelParse(source, { sourceType: 'module' })
const registration = ast.program.body.find(node =>
  node.type === 'ExpressionStatement'
  && node.expression.callee?.object?.name === 'router'
  && node.expression.callee?.property?.name === 'beforeEach'
)
assert.ok(registration, '必须执行生产路由实际注册的守卫')
const callback = registration.expression.arguments[0]

/** 注入会话与 API 依赖，执行真实守卫，避免只验证源代码包含某段条件。 */
function createGuard({ refresh = false, loggedIn = true, disabledPaths = [] } = {}) {
  const persistedUser = { id: 'reset-user', passwordResetRequired: true }
  const calls = { permissions: 0, restoreSession: 0, restoreUser: 0, warnings: [] }
  const userStore = {
    isLoggedIn: loggedIn,
    userInfo: refresh || !loggedIn ? null : persistedUser,
    permissions: [],
    roles: [],
    isSuperAdmin: false,
    restoreUserInfo() {
      calls.restoreUser += 1
      if (loggedIn) this.userInfo = persistedUser
    },
    setPermissions(permissions) { this.permissions = permissions }
  }
  const dependencies = {
    useUserStore: () => userStore,
    restoreAuthSession: async () => { calls.restoreSession += 1 },
    getPermissions: async () => {
      calls.permissions += 1
      // 待改密会话不能访问业务接口；真实请求拦截器收到 428 会触发整页跳转。
      if (userStore.userInfo?.passwordResetRequired) {
        throw Object.assign(new Error('请先修改密码'), { response: { status: 428 } })
      }
      return ['entity:definition:view']
    },
    ElMessage: { warning: message => calls.warnings.push(message) },
    localStorage: { getItem: () => JSON.stringify(disabledPaths) }
  }
  const guard = new Function(...Object.keys(dependencies),
    `return (${source.slice(callback.start, callback.end)})`
  )(...Object.values(dependencies))
  return {
    calls,
    userStore,
    async navigate(path, meta = {}) {
      const destinations = []
      await guard({ path, meta }, {}, destination => destinations.push(destination))
      assert.equal(destinations.length, 1, '每次导航必须且只能作出一次路由决定')
      return destinations[0]
    }
  }
}

for (const refresh of [false, true]) {
  const session = createGuard({ refresh })
  assert.equal(await session.navigate('/change-password'), undefined)
  assert.equal(await session.navigate('/change-password'), undefined, '重复进入仍应停留在改密页')
  assert.equal(session.calls.permissions, 0, '首次进入及刷新改密页不得请求业务权限，避免触发 428 整页跳转')
  assert.equal(session.calls.restoreSession, 2)
  assert.equal(session.calls.restoreUser, refresh ? 1 : 0)
  assert.equal(session.userStore.isLoggedIn, true)
  assert.equal(session.userStore.userInfo.passwordResetRequired, true)
  assert.deepEqual(session.calls.warnings, [])
}

const restricted = createGuard({ disabledPaths: ['/change-password', '/entity', '/dev'] })
for (const [path, meta] of [
  ['/entity', { requiredPermissions: ['entity:definition:view'] }],
  ['/dev/manual', { developerOnly: true }],
  ['/login', { public: true }]
]) {
  assert.equal(await restricted.navigate(path, meta), '/change-password', '待改密用户必须先完成改密')
}
assert.equal(await restricted.navigate('/change-password'), undefined, '禁用菜单不能阻断强制改密')
assert.equal(restricted.calls.permissions, 0)
assert.deepEqual(restricted.calls.warnings, [], '强制改密不能被业务权限或禁用菜单拦截')

const completed = createGuard()
assert.equal(await completed.navigate('/change-password'), undefined)
// 改密后先清理本地会话，必须重新登录才允许恢复业务导航。
completed.userStore.isLoggedIn = false
assert.equal(await completed.navigate('/login', { public: true }), undefined)
assert.equal(await completed.navigate('/entity'), '/login')
assert.equal(completed.calls.permissions, 0)
completed.userStore.isLoggedIn = true
completed.userStore.userInfo.passwordResetRequired = false
assert.equal(await completed.navigate('/entity', {
  requiredPermissions: ['entity:definition:view']
}), undefined, '使用新密码重新登录后应恢复正常业务导航')
assert.equal(completed.calls.permissions, 1, '重新登录且解除强制改密后才加载业务权限')
assert.deepEqual(completed.userStore.permissions, ['entity:definition:view'])
assert.equal(await completed.navigate('/login', { public: true }), '/')

const anonymous = createGuard({ loggedIn: false })
assert.equal(await anonymous.navigate('/change-password'), '/login', '改密页仍须登录才能访问')
assert.equal(anonymous.calls.permissions, 0)

console.log('password reset route guard tests passed')
