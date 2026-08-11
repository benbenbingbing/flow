import assert from 'node:assert/strict'

import {
  FORM_FIELD_EXTENSION_TYPE,
  isFormFieldExtensionNode,
  resolveFormFieldExtensionName,
  resolveRuntimeFormFieldComponentType
} from '../form-field-extension.js'

const fieldNode = {
  nodeType: 'FIELD',
  componentName: 'project_acceptance_score',
  props: {
    componentType: 'number',
    componentExtensionType: FORM_FIELD_EXTENSION_TYPE
  }
}

assert.equal(isFormFieldExtensionNode(fieldNode), true)
assert.equal(
  resolveFormFieldExtensionName(fieldNode),
  'project_acceptance_score'
)
assert.equal(
  resolveRuntimeFormFieldComponentType(
    fieldNode,
    'number',
    name => name === 'project_acceptance_score'
  ),
  'project_acceptance_score'
)
assert.equal(
  resolveRuntimeFormFieldComponentType(
    fieldNode,
    'number',
    () => false
  ),
  'number',
  '未安装字段扩展时必须使用内置组件回退'
)
assert.equal(
  isFormFieldExtensionNode({
    ...fieldNode,
    props: {
      ...fieldNode.props,
      componentExtensionType: 'NODE'
    }
  }),
  false,
  'NODE 扩展不能被误判为字段组件'
)

console.log('form field extension tests passed')
