import test from 'node:test'
import assert from 'node:assert/strict'
import { buildMobileLoginUrl, resolveLoginRedirect } from '../src/loginRedirect.js'

test('登录回跳保留单据完整路径、查询参数和锚点', () => {
  const target = '/process/instance-test?kind=todo&taskId=task-test&from=%E9%80%9A%E7%9F%A5#history'
  assert.equal(resolveLoginRedirect(target), target)
  assert.equal(resolveLoginRedirect('/inbox/cc'), '/inbox/cc')
  assert.equal(resolveLoginRedirect('/process/id?source=https%3A%2F%2Fexample.com'), '/process/id?source=https%3A%2F%2Fexample.com')
})

test('非法、重复及登录页回跳参数统一回退待办', () => {
  for (const target of [
    undefined, null, '', ['/process/id', '/inbox/cc'],
    'https://example.com', '//example.com', 'javascript:alert(1)', 'process/id',
    '/\\example.com', '/%2fexample.com', '/%5cexample.com', '/process/\nid', '/process/%0aid', '/process/%broken',
    '/login', '/LOGIN/', '/login?redirect=/process/id', '/login#next', '/%6cogin', '/m/login', '/process/../login', '/process/%2e%2e/login'
  ]) assert.equal(resolveLoginRedirect(target), '/inbox/todo', `不应接受 ${JSON.stringify(target)}`)
})

test('会话失效使用移动站点前缀并对回跳参数整体编码', () => {
  const target = { pathname: '/m/process/instance-test', search: '?kind=todo&taskId=task-test', hash: '#history' }
  const result = new URL(buildMobileLoginUrl(target), 'https://flow.test')
  assert.equal(result.pathname, '/m/login')
  assert.equal(result.searchParams.get('redirect'), '/process/instance-test?kind=todo&taskId=task-test#history')
  assert.equal(result.hash, '')
  assert.equal(new URL(buildMobileLoginUrl({ pathname: '/elsewhere' }), 'https://flow.test').searchParams.get('redirect'), '/inbox/todo')
})

test('登录页收到会话失效通知时保留已有回跳参数', () => {
  assert.equal(buildMobileLoginUrl({ pathname: '/m/login', search: '?redirect=%2Fprocess%2Fid' }), null)
  assert.equal(buildMobileLoginUrl({ pathname: '/m/login/' }), null)
})
