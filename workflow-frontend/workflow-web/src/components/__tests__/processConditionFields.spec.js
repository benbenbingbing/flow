import assert from 'node:assert/strict'
import test from 'node:test'
import { loadProcessConditionFieldOptions } from '../node-config/processConditionFields.js'

test('所有选择类字段从代码表加载候选，按代码表去重且保留代码值、子项和停用状态', async () => {
  const fields = ['SELECT', 'MULTI_SELECT', 'RADIO', 'CHECKBOX'].map((fieldType, index) => ({
    fieldCode: `choice${index}`, fieldType, dictType: ' choices ',
    options: [], optionsJson: '[{"value":"stale","label":"过期内嵌项"}]'
  }))
  const original = structuredClone(fields)
  const calls = []
  const loaded = await loadProcessConditionFieldOptions(fields, async code => {
    calls.push(code)
    return [
      { itemCode: 'YES', itemLabel: '通过', status: '0', children: [
        { itemCode: 'CHILD', itemLabel: '子选项', status: 0 }
      ] },
      { itemCode: 'NO', itemLabel: '停用项', status: '1' }
    ]
  })
  assert.deepEqual(calls, ['choices'])
  for (const field of loaded) {
    assert.deepEqual(field.options, [
      { value: 'YES', label: '通过', disabled: false },
      { value: 'CHILD', label: '子选项', disabled: false },
      { value: 'NO', label: '停用项', disabled: true }
    ])
  }
  assert.deepEqual(fields, original)
})

test('未绑定代码表的选择字段不能使用任何旧内嵌来源，非选择字段原样保留', async () => {
  const fields = [
    { fieldCode: 'legacy', fieldType: 'SELECT', optionsJson: '[{"value":"old","label":"旧选项"}]' },
    { fieldCode: 'component', fieldType: 'RADIO', componentProps: '{"options":["A"]}' },
    { fieldCode: 'note', fieldType: 'STRING', dictType: 'unused' }
  ]
  const loaded = await loadProcessConditionFieldOptions(fields, () => assert.fail('不应加载代码表'))
  assert.deepEqual(loaded.slice(0, 2).map(field => field.options), [[], []])
  assert.equal(loaded[2], fields[2])
})

test('空代码表、失败和异常响应不使用过期内嵌值，也不影响其他代码表', async t => {
  const errorLog = t.mock.method(console, 'error', () => {})
  const fields = ['empty', 'failed', 'invalid', 'normal'].map(dictType => ({
    fieldCode: dictType, fieldType: 'SELECT', dictType, options: [{ value: 'stale', label: '过期' }]
  }))
  const loaded = await loadProcessConditionFieldOptions(fields, async code => {
    if (code === 'failed') throw new Error('加载失败')
    if (code === 'invalid') return { data: [] }
    if (code === 'empty') return []
    return [{ itemCode: 'OK', itemLabel: '可用项', status: '0' }]
  })
  for (const field of loaded.slice(0, 3)) assert.deepEqual(field.options, [])
  assert.deepEqual(loaded[3].options, [{ value: 'OK', label: '可用项', disabled: false }])
  assert.equal(errorLog.mock.callCount(), 1)
})
