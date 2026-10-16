import test from 'node:test'
import assert from 'node:assert/strict'
import { createCascaderRuntime, normalizeCascaderOptions, readCascaderConfig } from '../src/shared/cascader-runtime.js'
import { createFormDataSourceRuntime } from '../src/shared/form-runtime/dataSourceRuntime.js'

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function runtime(config) {
  const instance = createCascaderRuntime()
  instance.configure(config)
  return instance
}
const branch = (value, leaf = false) => ({ value, label: `名称${value}`, leaf })

test('static never invents region data; path matching preserves value types and branch-local identifiers', async () => {
  assert.deepEqual(readCascaderConfig({}), { source: 'STATIC', loadMode: 'ALL', options: [] })
  const controller = runtime({ source: 'STATIC', options: [
    { value: 'a', label: '甲', children: [branch(1, true), branch('1', true)] },
    { value: 'b', label: '乙', children: [{ value: 1, label: '第二分支' }] }
  ] })
  await controller.ensurePath(['b', 1])
  assert.deepEqual(controller.resolvePath(['b', 1]).labels, ['乙', '第二分支'])
  assert.equal(controller.resolvePath(['a', 1]).nodes.at(-1).value, 1)
  assert.equal(controller.resolvePath(['a', '1']).nodes.at(-1).value, '1')
})

test('ALL fetches once, CHILDREN merges concurrent requests and restores readonly saved labels by full path', async () => {
  const calls = []
  const controller = runtime({ source: 'INTERFACE', loadMode: 'CHILDREN', loadOptions: async input => {
    calls.push(input)
    return input.level === 0 ? [branch('a')] : input.level === 1 ? [branch(10)] : [branch('leaf', true)]
  } })
  await Promise.all([controller.ensurePath(['a', 10, 'leaf']), controller.ensurePath(['a', 10, 'leaf'])])
  assert.deepEqual(calls, [
    { loadMode: 'CHILDREN', parentValue: null, path: [], level: 0 },
    { loadMode: 'CHILDREN', parentValue: 'a', path: ['a'], level: 1 },
    { loadMode: 'CHILDREN', parentValue: 10, path: ['a', 10], level: 2 }
  ])
  assert.deepEqual(controller.resolvePath(['a', 10, 'leaf']).labels, ['名称a', '名称10', '名称leaf'])
  let count = 0
  controller.configure({ source: 'INTERFACE', loadMode: 'ALL', loadOptions: async () => {
    count++; return [{ value: 'a', label: '甲', children: [branch(10, true)] }]
  } })
  await controller.ensurePath(['a', 10])
  await controller.loadChildren(['a'])
  assert.equal(count, 1)
})

test('failure remains retryable and cannot turn a non-leaf into a selectable leaf', async () => {
  let attempts = 0
  const controller = runtime({ source: 'INTERFACE', loadMode: 'CHILDREN', loadOptions: async ({ level }) => {
    if (level === 0) return [branch('a')]
    if (++attempts === 1) throw new Error('网络故障')
    return [branch('b', true)]
  } })
  await assert.rejects(controller.ensurePath(['a', 'b']), /网络故障/)
  assert.equal(controller.snapshot().error, '网络故障')
  assert.equal(controller.resolvePath(['a']).nodes[0].leaf, false)
  assert.equal(controller.resolvePath(['a']).nodes[0].children, undefined)
  await controller.retry()
  await controller.ensurePath(['a', 'b'])
  assert.equal(controller.snapshot().error, '')
  assert.equal(attempts, 2)
})

test('configuration invalidation discards old successes and old errors without changing the new loading state', async () => {
  for (const fail of [false, true]) {
    const old = deferred()
    const current = deferred()
    const controller = runtime({ source: 'INTERFACE', loadMode: 'ALL', loadOptions: () => old.promise })
    const first = controller.loadChildren()
    await Promise.resolve()
    controller.configure({ source: 'INTERFACE', loadMode: 'ALL', loadOptions: () => current.promise })
    const second = controller.loadChildren()
    if (fail) old.reject(new Error('旧错误')); else old.resolve([branch('old', true)])
    await first
    assert.deepEqual(controller.snapshot().options, [])
    assert.equal(controller.snapshot().loading, true)
    assert.equal(controller.snapshot().error, '')
    current.resolve([branch('new', true)])
    await second
    assert.equal(controller.snapshot().options[0].value, 'new')
    assert.equal(controller.snapshot().loading, false)
  }
})

test('late failure of a previously expanded branch does not replace active branch state', async () => {
  const old = deferred()
  const controller = runtime({ source: 'INTERFACE', loadMode: 'CHILDREN', loadOptions: ({ parentValue }) =>
    parentValue === null ? [branch('a'), branch('b', true)] : old.promise })
  await controller.loadChildren()
  const first = controller.loadChildren(['a'])
  await Promise.resolve()
  controller.clearError()
  old.reject(new Error('旧分支错误'))
  await assert.rejects(first, /旧分支错误/)
  assert.equal(controller.snapshot().error, '')
  assert.equal(controller.resolvePath(['b']).complete, true)
})

test('strict node contract catches malformed payloads, duplicate siblings and ambiguous leaf declarations', () => {
  for (const value of [null, {}, '[]']) assert.throws(() => normalizeCascaderOptions(value), /数组/)
  for (const value of ['', null, true, Infinity, {}]) assert.throws(() => normalizeCascaderOptions([{ value, label: 'a' }]), /value/)
  assert.throws(() => normalizeCascaderOptions([branch('a'), branch('a')], 'CHILDREN'), /重复/)
  assert.throws(() => normalizeCascaderOptions([{ value: 'a', label: 'a' }], 'CHILDREN'), /leaf/)
  assert.throws(() => normalizeCascaderOptions([{ ...branch('a'), children: [] }], 'CHILDREN'), /children/)
  assert.throws(() => normalizeCascaderOptions([{ ...branch('a'), children: null }], 'CHILDREN'), /children/)
  assert.throws(() => normalizeCascaderOptions([{ value: 'a', label: 'a', children: null }]), /数组/)
  assert.throws(() => normalizeCascaderOptions([{ value: 'a', label: 'a', leaf: null }]), /leaf/)
  assert.throws(() => normalizeCascaderOptions([{ value: 'a', label: 'a', disabled: null }]), /disabled/)
  assert.throws(() => normalizeCascaderOptions([branch('a', false)]), /不一致/)
  assert.throws(() => normalizeCascaderOptions([{ value: 'a', label: 'a', disabled: 'true' }]), /disabled/)
  const tree = depth => depth === 0 ? [] : [{ value: depth, label: String(depth), children: tree(depth - 1) }]
  assert.doesNotThrow(() => normalizeCascaderOptions(tree(32)))
  assert.throws(() => normalizeCascaderOptions(tree(33)), /32/)
  assert.throws(() => normalizeCascaderOptions(Array.from({ length: 10001 }, (_, i) => branch(i, true))), /10000/)
})

test('non-leaf empty children and missing saved paths are visible errors, never successful selections', async () => {
  const controller = runtime({ source: 'INTERFACE', loadMode: 'CHILDREN', loadOptions: ({ level }) => level === 0 ? [branch('a')] : [] })
  await controller.loadChildren()
  await assert.rejects(controller.loadChildren(['a']), /leaf/)
  assert.equal(controller.resolvePath(['a']).nodes[0].leaf, false)
  await assert.rejects(controller.ensurePath(['missing']), /已保存/)
  assert.equal(controller.snapshot().options[0].value, 'a')
  controller.configure({ source: 'INTERFACE', loadMode: 'BOGUS', loadOptions: () => [] })
  await assert.rejects(controller.loadChildren(), /加载方式/)
})

test('FIELD_OPTIONS keeps controlled cascader coordinates through input mapping and rejects malformed interface responses', async () => {
  const requests = []
  const binding = { extensionId: 'regions', inputMapping: { tenant: 'data.tenant', cascader: { literal: { level: 999 } } } }
  const field = { fieldCode: 'region', dataSourceBindings: { FIELD_OPTIONS: binding } }
  let response = [branch('a', true)]
  const dataSource = createFormDataSourceRuntime({ getForm: () => ({ id: 'form-a' }), getRecord: () => ({ tenant: 'T1' }), executeDataSource: async request => { requests.push(request); return response } })
  const cascader = { loadMode: 'CHILDREN', parentValue: null, path: [], level: 0 }
  assert.deepEqual(await dataSource.loadOptions(field, { input: { cascader } }), response)
  assert.deepEqual(requests[0].input, { tenant: 'T1', cascader })
  response = { items: [] }
  await assert.rejects(dataSource.loadOptions(field, { input: { cascader } }), /数组/)
  assert.deepEqual(await dataSource.loadOptions(field), [])
  await assert.rejects(dataSource.loadOptions({ fieldCode: 'region' }, { input: { cascader } }), /必须配置/)
  binding.modes = ['edit']
  await assert.rejects(dataSource.loadOptions(field, { mode: 'view', input: { cascader } }), /未启用/)
})

test('new saved path supersedes old restoration without surfacing its late error', async () => {
  const oldBranch = deferred()
  const controller = runtime({ source: 'INTERFACE', loadMode: 'CHILDREN', loadOptions: ({ parentValue }) => {
    if (parentValue === null) return [branch('a'), branch('b')]
    if (parentValue === 'a') return oldBranch.promise
    return [branch('new', true)]
  } })
  await controller.loadChildren()
  const old = controller.ensurePath(['a', 'old'])
  await new Promise(resolve => setImmediate(resolve))
  const next = await controller.ensurePath(['b', 'new'])
  oldBranch.reject(new Error('旧路径不可用'))
  assert.equal((await old).complete, false)
  assert.equal(next.complete, true)
  assert.equal(controller.snapshot().error, '')
})

test('lazy node budget includes all loaded branches and survives clearing branch errors', async () => {
  const controller = runtime({ source: 'INTERFACE', loadMode: 'CHILDREN', loadOptions: ({ parentValue }) => {
    if (parentValue === null) return [branch('a'), branch('b')]
    return Array.from({ length: parentValue === 'a' ? 6000 : 4000 }, (_, index) => branch(index, true))
  } })
  await controller.loadChildren()
  await controller.loadChildren(['a'])
  controller.clearError()
  await assert.rejects(controller.loadChildren(['b']), /10000/)
  assert.equal(controller.resolvePath(['a', 5999]).complete, true)
  assert.equal(controller.resolvePath(['b']).nodes[0].children, undefined)
})

test('saved values require enabled leaves while interactive drafts can stop at a parent', async () => {
  const controller = runtime({ source: 'STATIC', options: [
    { value: 'a', label: '甲', children: [{ value: 'leaf', label: '禁用子项', disabled: true }] }
  ] })
  assert.equal((await controller.ensurePath(['a'])).complete, true)
  await assert.rejects(controller.ensurePath(['a'], { requireLeaf: true }), /完整末级/)
  await assert.rejects(controller.ensurePath(['a', 'leaf'], { requireLeaf: true }), /禁用选项/)
  assert.deepEqual(controller.resolvePath(['a', 'leaf']).labels, ['甲', '禁用子项'])
})
