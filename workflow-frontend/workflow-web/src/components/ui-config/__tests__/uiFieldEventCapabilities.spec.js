import assert from 'node:assert/strict'
import { fieldEventDisabledReason, isEntitySelectionEventField, isSingleEntitySelectionEventField } from '../uiFieldEventCapabilities.js'

const single = { nodeType: 'FIELD', fieldType: 'REFERENCE', componentType: 'reference', fieldCode: 'customer' }
const reason = (field, event, capabilities) => fieldEventDisabledReason(field, event, capabilities)
for (const type of ['REFERENCE', 'USER', 'DEPT', 'ROLE', 'GROUP']) {
  const field = { fieldType: type, componentType: 'input' }
  assert.equal(isEntitySelectionEventField(field), true)
  assert.equal(isSingleEntitySelectionEventField(field), true)
  assert.equal(reason(field, 'ENTITY_SELECTED'), '')
}
assert.equal(reason(single, 'FIELD_CHANGE'), '')
assert.equal(reason(single, 'ENTITY_SELECTED'), '')
assert.match(reason(single, 'FIELD_BUTTON_CLICK'), /没有独立/)
assert.match(reason(single, 'SUBFORM_LOAD'), /仅子表单/)
assert.match(reason(single, 'SUBFORM_SAVE'), /仅子表单/)
assert.match(reason({ fieldType: 'STRING', componentType: 'input' }, 'ENTITY_SELECTED'), /仅实体选择/)
assert.equal(isSingleEntitySelectionEventField({ ...single, componentType: 'multi_reference' }), false)
assert.equal(isSingleEntitySelectionEventField({ ...single, componentProps: '{"multiple":true}' }), false)
assert.equal(isEntitySelectionEventField({ componentProps: '{"refConfig":{"refEntityType":"CUSTOM","refEntityId":"customer"}}' }), true)
assert.equal(isEntitySelectionEventField({ componentProps: '{' }), false)
assert.equal(reason({ fieldType: 'SUB_FORM', componentType: 'sub_form' }, 'SUBFORM_SAVE'), '')
assert.equal(isEntitySelectionEventField({ fieldType: 'SUB_FORM', refEntityId: 'child' }), false)
assert.equal(isSingleEntitySelectionEventField({ fieldType: 'SUB_LIST', refEntityId: 'child' }), false)
assert.match(reason({ nodeType: 'TEXT' }, 'FIELD_CHANGE'), /布局节点/)
assert.equal(reason(null, 'SUBFORM_SAVE'), '', 'OWNER 公共事件不按单个字段限制')
assert.equal(reason({ fieldType: 'INTEGER', componentType: 'custom_score' }, 'FIELD_BUTTON_CLICK', { supportedEvents: ['FIELD_BUTTON_CLICK'] }), '')
assert.match(reason({ fieldType: 'INTEGER', componentType: 'custom_score' }, 'FIELD_BUTTON_CLICK'), /没有独立/)
console.log('field event capabilities passed: references, scalar, layout, subform, declared component buttons')
