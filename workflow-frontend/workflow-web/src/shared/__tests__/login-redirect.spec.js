import assert from 'node:assert/strict'
import { authReturnTarget, authRouteLocation, buildAuthRedirectUrl, resolveLoginRedirect } from '../login-redirect.js'

const target = '/process/progress/instance-1?taskId=task-1&source=%E9%80%9A%E7%9F%A5#history'
assert.equal(resolveLoginRedirect(target), target)
assert.equal(resolveLoginRedirect('/a/../../home?tab=todo#list'), '/home?tab=todo#list')
for (const invalid of [undefined, '', ['/home', target], 'https://example.com', '//example.com', '/\\example.com', '/%2fexample.com', '/%5cexample.com', '/%00', '/%broken', '/login', '/LOGIN/', '/change-password?redirect=/home', '/a/../login', '/a/%2e%2e/change-password']) {
  assert.equal(resolveLoginRedirect(invalid), '/', JSON.stringify(invalid))
}
assert.deepEqual(authRouteLocation('/login', target), { path: '/login', query: { redirect: target } })
assert.equal(authReturnTarget({ path: '/change-password', fullPath: '/change-password', query: { redirect: target } }), target)

const source = new URL(target, 'https://workflow.test')
for (const authPage of ['/login', '/change-password']) {
  const login = new URL(buildAuthRedirectUrl(source, authPage), source)
  assert.equal(login.pathname, authPage)
  assert.equal(login.searchParams.get('redirect'), target)
  assert.equal(login.hash, '')
  assert.equal(buildAuthRedirectUrl(login, authPage), null)
}
const reset = new URL('/change-password?redirect=' + encodeURIComponent(target), source)
assert.equal(new URL(buildAuthRedirectUrl(reset), source).searchParams.get('redirect'), target)
assert.equal(buildAuthRedirectUrl(new URL('/change-password?redirect=/home&redirect=//example.com', source)), '/login')
assert.equal(buildAuthRedirectUrl(undefined), null)

const base = '/gateway/flow/'
const prefixedSource = new URL(`${base.slice(0, -1)}${target}`, source)
const prefixedLogin = new URL(buildAuthRedirectUrl(prefixedSource, '/login', base), source)
assert.equal(prefixedLogin.pathname, `${base}login`)
assert.equal(prefixedLogin.searchParams.get('redirect'), target)
assert.equal(buildAuthRedirectUrl(prefixedLogin, '/login', base), null)
const prefixedReset = new URL(buildAuthRedirectUrl(prefixedLogin, '/change-password', base), source)
assert.equal(prefixedReset.pathname, `${base}change-password`)
assert.equal(prefixedReset.searchParams.get('redirect'), target)
assert.equal(new URL(buildAuthRedirectUrl(prefixedReset, '/login', base), source).searchParams.get('redirect'), target)
assert.equal(buildAuthRedirectUrl(new URL('/gateway/flows/home', source), '/login', base), `${base}login`)
console.log('login redirect target and full-page navigation tests passed')
