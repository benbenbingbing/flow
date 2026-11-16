import assert from 'node:assert/strict'
import { effectScope, reactive } from 'vue'
import {
  useFormField,
  normalizeFieldCollectionValue,
  normalizeFieldDefaultValue
} from '@flow/workflow-core/vue/useFormField'

const booleanField = { fieldType: 'BOOLEAN', componentType: 'switch' }

assert.equal(normalizeFieldDefaultValue(booleanField, 'false'), false)
assert.equal(normalizeFieldDefaultValue(booleanField, '0'), false)
assert.equal(normalizeFieldDefaultValue(booleanField, false), false)
assert.equal(normalizeFieldDefaultValue(booleanField, 'true'), true)
assert.equal(normalizeFieldDefaultValue(booleanField, '1'), true)
assert.equal(normalizeFieldDefaultValue(booleanField, 1), true)
assert.equal(
  normalizeFieldDefaultValue({ fieldType: 'STRING', componentType: 'input' }, 'false'),
  'false'
)
assert.deepEqual(normalizeFieldCollectionValue(''), [])
assert.deepEqual(normalizeFieldCollectionValue(null), [])
assert.deepEqual(normalizeFieldCollectionValue('dev'), ['dev'])
assert.deepEqual(normalizeFieldCollectionValue(['dev', 'qa']), ['dev', 'qa'])

console.log('useFormField default value tests passed')

// 已解析的代码表/动态候选是唯一来源；旧三种来源和显式空候选不得触发兜底。
const scope = effectScope()
scope.run(() => {
  const legacy = [{ label: '历史项', value: 'OLD' }]
  const props = reactive({ field: { fieldType: 'SELECT', options: legacy,
    optionsJson: JSON.stringify(legacy), componentProps: JSON.stringify({ options: legacy }) }, modelValue: 'OLD' })
  const { currentOptions } = useFormField(props, () => {})
  assert.deepEqual(currentOptions.value, [])
  props.options = [{ label: '代码表项', value: 'DICT' }]
  assert.deepEqual(currentOptions.value, props.options)
  props.options = []
  assert.deepEqual(currentOptions.value, [])
})
scope.stop()
