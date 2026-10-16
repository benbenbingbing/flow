import assert from 'node:assert/strict'

import {
  applyTemplateSnapshot,
  clearTemplateBinding,
  restoreButtonTemplateBinding,
  serializeButtonTemplateBinding,
  setTemplateBinding,
  templateSnapshot
} from '../shared/config-runtime/templateBinding.js'

assert.deepEqual(
  templateSnapshot({ button: { label: '提交' } }, 'button'),
  { label: '提交' }
)

const field = {
  id: 'field-1',
  revision: 7,
  orderKey: 1000000,
  fieldName: '旧名称'
}
applyTemplateSnapshot(
  field,
  { fieldName: '模板名称', revision: 99, orderKey: 9000000 },
  { preserveKeys: ['id', 'revision', 'orderKey'] }
)
assert.deepEqual(field, {
  id: 'field-1',
  revision: 7,
  orderKey: 1000000,
  fieldName: '模板名称'
})

setTemplateBinding(field, 'template-1', 3, { width: 180 })
assert.equal(field.templateId, 'template-1')
assert.equal(field.templateVersion, 3)
assert.deepEqual(JSON.parse(field.localOverridesDocument), { width: 180 })

const button = restoreButtonTemplateBinding({
  id: 'button-1',
  revision: 4,
  __templateBinding: {
    templateId: 'button-template-1',
    templateVersion: 2,
    localOverrides: { buttonType: 'danger' }
  }
})
assert.equal(button.templateId, 'button-template-1')
assert.equal(button.templateVersion, 2)
assert.deepEqual(
  serializeButtonTemplateBinding(button),
  {
    templateId: 'button-template-1',
    templateVersion: 2,
    localOverrides: { buttonType: 'danger' }
  }
)

clearTemplateBinding(button)
assert.equal(button.templateId, null)
assert.equal(button.templateVersion, null)
assert.equal(button.localOverridesDocument, '')
assert.equal(serializeButtonTemplateBinding(button), null)

console.log('template upgrade closure tests passed')
