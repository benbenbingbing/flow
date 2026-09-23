import test from 'node:test'
import assert from 'node:assert/strict'
import { createFormReleaseSession } from '../src/formReleaseSession.js'

const token = (expiresAt, extra = {}) => `${Buffer.from(JSON.stringify({ expiresAt, purpose: 'ACTIVE_TASK', taskId: 'task-1', nodeId: 'review', ...extra })).toString('base64url')}.test-signature`
const formFor = value => ({ id: 'form-1', runtimeReleaseId: 'release-1', runtimeReleaseVersion: 2, releaseResolutionToken: value })

test('并发事件和提交共用一次续期，只替换协议令牌，不动记录、表单版本或业务同名字段', async () => {
  const old = token(100), fresh = token(700), form = formFor(old)
  let calls = 0, resolve
  const session = createFormReleaseSession({ getForm: () => form, now: () => 400_000, loadForm: () => { calls++; return new Promise(done => { resolve = done }) } })
  const record = { name: '未提交草稿', releaseResolutionToken: '业务字段' }
  const first = session.prepare({ data: { releaseResolutionToken: old, formData: record } })
  const second = session.prepare({ data: { formReleaseResolutionToken: old } })
  assert.equal(calls, 1)
  resolve({ ...formFor(fresh), fields: [{ fieldCode: '不能覆盖原字段' }] })
  const [event, submit] = await Promise.all([first, second])
  assert.equal(event.data.releaseResolutionToken, fresh)
  assert.equal(submit.data.formReleaseResolutionToken, fresh)
  assert.equal(event.data.formData, record)
  assert.equal(record.releaseResolutionToken, '业务字段')
  assert.deepEqual(form, formFor(fresh))
  assert.equal((await session.prepare({ params: { releaseResolutionToken: old } })).params.releaseResolutionToken, fresh)
  assert.equal(calls, 1)
})

test('网络失败可再次续期；未过期、无令牌和其它表单请求不触发续期', async () => {
  const old = token(100), form = formFor(old)
  let calls = 0
  const session = createFormReleaseSession({ getForm: () => form, now: () => 400_000, loadForm: async () => {
    if (++calls === 1) throw new Error('网络不可用')
    return formFor(token(700))
  } })
  await session.prepare({ data: { releaseResolutionToken: 'another-form' } })
  await session.prepare({ url: '/auth/refresh' })
  assert.equal(calls, 0)
  await assert.rejects(session.prepare({ data: { releaseResolutionToken: old } }), /网络不可用/)
  assert.equal(form.releaseResolutionToken, old)
  await session.prepare({ data: { releaseResolutionToken: old } })
  await session.prepare({ data: { releaseResolutionToken: form.releaseResolutionToken } })
  assert.equal(calls, 2)
})

test('任务、节点、发布版本或热修复变化时停止请求，保留原表单', async () => {
  const old = token(100)
  for (const change of [{ runtimeReleaseId: 'new-release' }, { effectiveReleaseId: 'hotfix' }, { releaseResolutionToken: token(700, { taskId: 'other-task' }) }, { releaseResolutionToken: token(700, { purpose: 'HISTORY_VIEW' }) }, { releaseResolutionToken: token(100) }]) {
    const form = formFor(old)
    const session = createFormReleaseSession({ getForm: () => form, now: () => 400_000, loadForm: async () => ({ ...formFor(token(700)), ...change }) })
    await assert.rejects(session.prepare({ data: { releaseResolutionToken: old } }), { errorCode: 'FORM_RELEASE_CONTEXT_CHANGED' })
    assert.deepEqual(form, formFor(old))
  }
})

test('离开页面或切换任务后迟到的续期结果不得落到新表单', async () => {
  const old = token(100), previous = formFor(old)
  let form = previous, resolve
  const session = createFormReleaseSession({ getForm: () => form, now: () => 400_000, loadForm: () => new Promise(done => { resolve = done }) })
  const pending = session.prepare({ data: { releaseResolutionToken: old } })
  session.reset(); form = formFor('new-page-token')
  resolve(formFor(token(700)))
  await assert.rejects(pending, { errorCode: 'FORM_RELEASE_CONTEXT_CHANGED' })
  assert.equal(form.releaseResolutionToken, 'new-page-token'); assert.equal(previous.releaseResolutionToken, old)
})
