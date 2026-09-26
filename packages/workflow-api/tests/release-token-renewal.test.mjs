import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequestRuntime } from '../dist/request.js'

const now = () => Math.floor(Date.now() / 1000)
const token = (expiresAt, changes = {}) => `${Buffer.from(JSON.stringify({
  purpose: 'ACTIVE_TASK', parentFormId: 'form-1', parentReleaseId: 'release-1',
  parentReleaseVersion: 2, depth: 0, userId: 'user-1', taskId: 'task-1',
  processVersionHistoryId: 'history-1', nodeId: 'review', processInstanceId: 'process-1',
  issuedAt: expiresAt - 600, expiresAt, ...changes
})).toString('base64url')}.signature`
const form = (value, changes = {}) => ({
  id: 'form-1', runtimeReleaseId: 'release-1', runtimeReleaseVersion: 2,
  effectiveReleaseId: 'release-1', releaseResolutionToken: value, ...changes
})
const progressUrl = '/process-instance/process-1/progress'
const eventUrl = '/ui-runtime/events/FIELD_CHANGE/execute'
const body = config => typeof config.data === 'string' ? JSON.parse(config.data) : config.data
const response = (config, data) => ({ config, status: 200, headers: {}, data: { code: 200, data } })
function runtime(adapter) {
  const state = { token: 'access', tokenExpiresAt: new Date(Date.now() + 3600000).toISOString(), userInfo: { id: 'user-1' } }
  const messages = []
  const result = createRequestRuntime({ getSession: () => state, getOrigin: () => 'https://flow.example',
    notifyError: message => messages.push(message), adapter })
  return { ...result, state, messages }
}

test('到期前 30 秒自动续期；并发事件/提交共用查询，草稿和业务同名字段保留', async () => {
  const old = token(now() + 20), fresh = token(now() + 620)
  let loads = 0
  const sent = [], { request } = runtime(async config => {
    if (config.url === progressUrl) {
      loads++
      await new Promise(resolve => setTimeout(resolve, 5))
      return response(config, { formConfigs: [form(loads === 1 ? old : fresh)] })
    }
    sent.push(body(config)); return response(config, { ok: true })
  })
  const loaded = await request.get(progressUrl)
  const draft = { name: '未保存内容', releaseResolutionToken: '业务字段', amount: 0 }
  await Promise.all([
    request.post(eventUrl, { releaseResolutionToken: old, input: draft }),
    request.post('/process-task/complete', { formReleaseResolutionToken: old, formData: draft, requestId: 'once' })
  ])
  assert.equal(loads, 2)
  assert.equal(sent[0].releaseResolutionToken, fresh)
  assert.equal(sent[1].formReleaseResolutionToken, fresh)
  assert.deepEqual(sent[0].input, draft); assert.deepEqual(sent[1].formData, draft)
  assert.equal(sent[1].requestId, 'once')
  assert.equal(loaded.formConfigs[0].releaseResolutionToken, old, '页面对象和用户数据不被刷新响应覆盖')
  await request.post(eventUrl, { releaseResolutionToken: old })
  assert.equal(loads, 2, '子表行里的旧副本复用续期结果')
})

test('休眠后父子令牌均已过期：先重获父表单权限，再加载相同子表单版本', async () => {
  const oldRoot = token(now() - 600), newRoot = token(now() + 600)
  const childContext = { parentFormId: 'child', parentReleaseId: 'child-v1', depth: 1 }
  const oldChild = token(now() - 600, childContext), newChild = token(now() + 600, childContext)
  const childUrl = '/entity-forms/child/runtime-release'
  const calls = [], { request } = runtime(async config => {
    calls.push({ url: config.url, parent: config.params?.releaseResolutionToken })
    if (config.url === progressUrl) return response(config, { formConfig: form(calls.length === 1 ? oldRoot : newRoot) })
    if (config.url === childUrl) return response(config, {
      id: 'child-v1', effectiveReleaseId: 'child-v1',
      releaseResolutionToken: calls.filter(c => c.url === childUrl).length === 1 ? oldChild : newChild
    })
    assert.equal(body(config).releaseResolutionToken, newChild)
    return response(config, {})
  })
  await request.get(progressUrl)
  // 原查询持有父令牌；即使首次子查询前父令牌已过期，也能自动更新。
  await request.get(childUrl, { params: { releaseId: 'child-v1', version: 2, releaseResolutionToken: oldRoot } })
  await request.post(eventUrl, { releaseResolutionToken: oldChild })
  assert.equal(calls.filter(c => c.url === progressUrl).length, 2)
  assert.equal(calls.filter(c => c.url === childUrl).length, 2)
  assert.ok(calls.filter(c => c.url === childUrl).every(c => c.parent === newRoot))
  assert.equal(calls.filter(c => c.url === eventUrl).length, 1)
})

test('列表按钮持有的旧令牌重新经过列表 schema 鉴权，支持多个表单同时续期', async () => {
  const old = token(now() - 1), fresh = token(now() + 600)
  const otherOld = token(now() - 1, { parentFormId: 'other' }), otherFresh = token(now() + 600, { parentFormId: 'other' })
  const url = '/entity-lists/demo/default/schema'
  let loads = 0
  const { request } = runtime(async config => {
    if (config.url === url) {
      loads++
      return response(config, { rowButtons: [
        { targetFormReleaseResolutionToken: loads === 1 ? old : fresh },
        { targetFormReleaseResolutionToken: loads === 1 ? otherOld : otherFresh }
      ] })
    }
    return response(config, body(config))
  })
  await request.get(url)
  const results = await Promise.all([old, otherOld].map(value => request.post(eventUrl, { releaseResolutionToken: value })))
  assert.equal(loads, 2)
  assert.deepEqual(results.map(value => value.releaseResolutionToken), [fresh, otherFresh])
})

test('版本、热修复、任务/节点改变或表单消失时阻止写请求，不悄悄切换上下文', async () => {
  for (const replacement of [
    form(token(now() + 600, { parentReleaseId: 'release-new' })),
    form(token(now() + 600), { effectiveReleaseId: 'hotfix-new' }),
    form(token(now() + 600, { taskId: 'task-new' })),
    form(token(now() + 600, { nodeId: 'node-new' })),
    null
  ]) {
    const old = token(now() - 1)
    let loads = 0, writes = 0
    const { request, messages } = runtime(async config => {
      if (config.url === progressUrl) return response(config, { formConfig: ++loads === 1 ? form(old) : replacement })
      writes++; return response(config, {})
    })
    const loaded = await request.get(progressUrl)
    await assert.rejects(request.post(eventUrl, { releaseResolutionToken: old }), { errorCode: 'FORM_RELEASE_CONTEXT_CHANGED' })
    assert.equal(writes, 0); assert.equal(messages.length, 1)
    assert.equal(loaded.formConfig.releaseResolutionToken, old)
  }
})

test('续期网络失败仅提示一次，下一次操作可重试，业务写请求不重放', async () => {
  const old = token(now() - 1), fresh = token(now() + 600)
  let loads = 0, writes = 0
  const { request, messages } = runtime(async config => {
    if (config.url === progressUrl) {
      if (++loads === 2) throw Object.assign(new Error('网络不可用'), { config })
      return response(config, { formConfig: form(loads === 1 ? old : fresh) })
    }
    writes++; return response(config, {})
  })
  await request.get(progressUrl)
  await assert.rejects(request.post(eventUrl, { releaseResolutionToken: old }), /网络不可用/)
  assert.deepEqual(messages, ['网络不可用']); assert.equal(writes, 0)
  await request.post(eventUrl, { releaseResolutionToken: old })
  assert.equal(writes, 1); assert.equal(loads, 3)
})

test('服务端撤销权限时阻止原请求；普通事件和保存响应不能成为续期来源', async () => {
  const old = token(now() - 1)
  let loads = 0, events = 0
  const { request } = runtime(async config => {
    if (config.url === progressUrl) {
      if (++loads === 2) return { ...response(config, {}), data: { code: 403, message: '无任务权限' } }
      return response(config, { formConfig: form(old) })
    }
    events++; return response(config, form(old))
  })
  await request.post(eventUrl, {})
  await request.post(eventUrl, { releaseResolutionToken: old })
  assert.equal(events, 2); assert.equal(loads, 0)
  await request.get(progressUrl)
  await assert.rejects(request.post(eventUrl, { releaseResolutionToken: old }), /无任务权限/)
  assert.equal(events, 2)
})

test('Embed 和第三方请求不会触发普通页面续期，也不注册外部响应中的令牌', async () => {
  const old = token(now() - 1)
  for (const embed of [false, true]) {
    const calls = [], { request, configureEmbedDelegatedRequest } = runtime(async config => {
      calls.push(config.url); return response(config, form(old))
    })
    if (embed) configureEmbedDelegatedRequest({ getAccessToken: () => 'embed-access' })
    await request.get(embed ? progressUrl : `https://external.example${progressUrl}`)
    await request.post(eventUrl, { releaseResolutionToken: old })
    assert.equal(calls.length, 2)
  }
})

test('切换账号时丢弃旧续期状态；在途响应不能把旧账号令牌带到新账号请求', async () => {
  const old = token(now() - 1), fresh = token(now() + 600)
  let loads = 0, resume, entered, writes = 0
  const waiting = new Promise(resolve => { entered = resolve })
  const { request, state } = runtime(async config => {
    if (config.url === progressUrl) {
      if (++loads === 2) { entered(); await new Promise(resolve => { resume = resolve }) }
      return response(config, { formConfig: form(loads === 1 ? old : fresh) })
    }
    writes++; return response(config, {})
  })
  await request.get(progressUrl)
  const pending = request.post(eventUrl, { releaseResolutionToken: old })
  await waiting
  state.userInfo = { id: 'user-2' }; resume()
  await assert.rejects(pending, { errorCode: 'FORM_RELEASE_CONTEXT_CHANGED' })
  assert.equal(writes, 0)
})

test('有效令牌、未知令牌和无令牌请求不会额外查询', async () => {
  const fresh = token(now() + 600)
  let loads = 0
  const { request } = runtime(async config => {
    if (config.url === progressUrl) { loads++; return response(config, { formConfig: form(fresh) }) }
    return response(config, {})
  })
  await request.get(progressUrl)
  await request.post(eventUrl, { releaseResolutionToken: fresh })
  await request.post(eventUrl, { releaseResolutionToken: 'unknown' })
  await request.post(eventUrl, {})
  assert.equal(loads, 1)
})

test('新增、查看、待办详情均通过各自原入口续期，查询参数保留', async () => {
  for (const url of ['/entity-form-resolve/new-data/demo', '/entity-form-resolve/view-data/demo/record-1', '/process-task/detail/task-1']) {
    const old = token(now() - 1), fresh = token(now() + 600)
    let loads = 0
    const { request } = runtime(async config => {
      if (config.url === url) {
        assert.deepEqual(config.params, { taskId: 'task-1' })
        return response(config, { formConfig: form(++loads === 1 ? old : fresh) })
      }
      assert.equal(config.params.formReleaseResolutionToken, fresh)
      return response(config, {})
    })
    await request.get(url, { params: { taskId: 'task-1' } })
    await request.get('/entity-data/demo/record-1', { params: { formReleaseResolutionToken: old } })
    assert.equal(loads, 2)
  }
})

test('只读关联内容 POST 使用原请求体续期，目标记录改变时拒绝继续使用旧页面', async () => {
  for (const changed of [false, true]) {
    const old = token(now() - 1), fresh = token(now() + 600)
    const url = '/ui-runtime/view-compositions/resolve'
    const query = { ownerType: 'LIST', ownerId: 'list-1', compositionKey: 'related', recordId: 'source-1' }
    let loads = 0, writes = 0
    const { request } = runtime(async config => {
      if (config.url === url) {
        assert.deepEqual(body(config), query)
        loads++
        return response(config, { targetReleaseResolutionToken: loads === 1 ? old : fresh,
          targetRecordId: changed && loads > 1 ? 'target-2' : 'target-1' })
      }
      writes++; assert.equal(body(config).releaseResolutionToken, fresh)
      return response(config, {})
    })
    await request.post(url, query)
    const action = request.post(eventUrl, { releaseResolutionToken: old })
    if (changed) await assert.rejects(action, { errorCode: 'FORM_RELEASE_CONTEXT_CHANGED' })
    else await action
    assert.equal(writes, changed ? 0 : 1)
  }
})

test('创建 transport 和启用 Embed 不提前读取尚未初始化的宿主 store', () => {
  const result = createRequestRuntime({ getSession: () => { throw new Error('store 尚未初始化') } })
  result.configureEmbedDelegatedRequest({ getAccessToken: () => 'embed' })
  result.resetEmbedDelegatedRequest()
})

test('只有当前页面持有的子令牌、缺少父入口时不会自循环或重复写入', async () => {
  const old = token(now() - 1)
  const url = '/entity-forms/form-1/runtime-release'
  let loads = 0, writes = 0
  const { request } = runtime(async config => {
    if (config.url === url) { loads++; return response(config, form(old)) }
    writes++; return response(config, {})
  })
  await request.get(url, { params: { releaseResolutionToken: old } })
  await assert.rejects(request.post(eventUrl, { releaseResolutionToken: old }), { errorCode: 'FORM_RELEASE_CONTEXT_CHANGED' })
  assert.equal(loads, 1); assert.equal(writes, 0)
})
