import assert from 'node:assert/strict'
import test from 'node:test'
import { assertNoEmbeddedSelectionOptions } from '../selection-option-policy.js'

test('选择字段保存拒绝三种内嵌来源，代码表与动态数据源配置可继续使用', () => {
  const options = [{ label: '历史项', value: 'OLD' }]
  for (const fieldType of ['SELECT', 'MULTI_SELECT', 'RADIO', 'CHECKBOX']) {
    for (const source of [{ options }, { optionsJson: JSON.stringify(options) }, { componentProps: JSON.stringify({ options }) }]) {
      assert.throws(() => assertNoEmbeddedSelectionOptions({ fieldType, ...source }), /不再支持内嵌选项/)
    }
    assert.doesNotThrow(() => assertNoEmbeddedSelectionOptions({ fieldType, dictType: 'choices' }))
    assert.doesNotThrow(() => assertNoEmbeddedSelectionOptions({ fieldType, dataSourceBindings: { FIELD_OPTIONS: [] } }))
  }
})
