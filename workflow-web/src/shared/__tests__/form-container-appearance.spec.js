import assert from 'node:assert/strict'

import {
  FORM_CONTAINER_APPEARANCE_DEFAULTS,
  getDefaultFormContainerAppearance,
  resolveFormContainerAppearance,
  supportsFormContainerAppearance
} from '../form-container-appearance.js'
import {
  buildFormNodePayload,
  extractFormNodeComponentConfig,
  formNodeSupports,
  getFormNodePropertySchema
} from '../form-node-property-schema.js'

assert.deepEqual(FORM_CONTAINER_APPEARANCE_DEFAULTS, {
  SECTION: { showPadding: true, showBorder: true },
  GRID: { showPadding: false, showBorder: false },
  TAB_SET: { showPadding: true, showBorder: true },
  TAB: { showPadding: false, showBorder: false },
  COLLAPSE: { showPadding: true, showBorder: true },
  SUB_FORM: { showPadding: true, showBorder: true },
  REPEATER: { showPadding: true, showBorder: true }
})

Object.keys(FORM_CONTAINER_APPEARANCE_DEFAULTS).forEach(nodeType => {
  assert.equal(
    supportsFormContainerAppearance(nodeType.toLowerCase()),
    true,
    `${nodeType} should support container appearance`
  )
  assert.equal(
    formNodeSupports(nodeType, 'containerAppearance'),
    true,
    `${nodeType} schema should expose the appearance capability`
  )
  assert.equal(
    getFormNodePropertySchema(nodeType).editable.includes('showPadding'),
    true,
    `${nodeType} schema should expose showPadding`
  )
  assert.equal(
    getFormNodePropertySchema(nodeType).editable.includes('showBorder'),
    true,
    `${nodeType} schema should expose showBorder`
  )
  assert.equal(
    getFormNodePropertySchema(nodeType).configKeys.includes('showPadding'),
    true,
    `${nodeType} schema should register showPadding as node config`
  )
  assert.equal(
    getFormNodePropertySchema(nodeType).configKeys.includes('showBorder'),
    true,
    `${nodeType} schema should register showBorder as node config`
  )
})
;['TEXT', 'FIELD', 'ACTION_SLOT', 'UNKNOWN'].forEach(nodeType => {
  assert.equal(
    supportsFormContainerAppearance(nodeType),
    false,
    `${nodeType} should not support container appearance`
  )
  assert.equal(formNodeSupports(nodeType, 'containerAppearance'), false)
})

assert.deepEqual(
  resolveFormContainerAppearance('SECTION', {}),
  { showPadding: true, showBorder: true },
  'missing settings must preserve the legacy SECTION appearance'
)
assert.deepEqual(
  resolveFormContainerAppearance('GRID', {
    componentProps: {
      showPadding: true,
      showBorder: true
    }
  }),
  { showPadding: true, showBorder: true },
  'legacy nested appearance remains readable'
)
assert.deepEqual(
  resolveFormContainerAppearance('SECTION', {
    showPadding: false,
    showBorder: false,
    componentProps: {
      showPadding: true,
      showBorder: true
    }
  }),
  { showPadding: false, showBorder: false },
  'explicit top-level false must take precedence over legacy nested values'
)
assert.deepEqual(
  resolveFormContainerAppearance(
    'TAB',
    '{"showPadding":"true","showBorder":1}'
  ),
  { showPadding: true, showBorder: true },
  'serialized boolean-compatible values should normalize predictably'
)
assert.deepEqual(
  resolveFormContainerAppearance('COLLAPSE', {
    showPadding: null,
    showBorder: 'invalid'
  }),
  { showPadding: true, showBorder: true },
  'invalid values must fall back to the legacy appearance'
)
assert.deepEqual(
  resolveFormContainerAppearance('FIELD', {
    showPadding: true,
    showBorder: true
  }),
  { showPadding: false, showBorder: false },
  'unsupported nodes must ignore appearance settings'
)

assert.deepEqual(
  extractFormNodeComponentConfig('TAB_SET', {
    tabPosition: 'left'
  }),
  {
    tabPosition: 'left',
    showPadding: true,
    showBorder: true
  },
  'schema extraction must add legacy-compatible defaults to old configs'
)

const sectionPayload = buildFormNodePayload(
  {
    id: 'section-appearance',
    nodeType: 'SECTION',
    nodeKey: 'section_appearance',
    fieldLabel: '外观区块'
  },
  {
    componentProps: {
      showPadding: false,
      showBorder: false
    }
  }
)
assert.equal(sectionPayload.props.showPadding, false)
assert.equal(sectionPayload.props.showBorder, false)

const subFormPayload = buildFormNodePayload(
  {
    id: 'sub-form-appearance',
    nodeType: 'SUB_FORM',
    nodeKey: 'sub_form_appearance',
    fieldLabel: '外观子表单'
  },
  {
    componentProps: {
      showPadding: false,
      showBorder: false
    }
  }
)
assert.equal(subFormPayload.props.componentProps.showPadding, false)
assert.equal(subFormPayload.props.componentProps.showBorder, false)

const mutableDefaults = getDefaultFormContainerAppearance('SECTION')
mutableDefaults.showPadding = false
assert.deepEqual(
  getDefaultFormContainerAppearance('SECTION'),
  { showPadding: true, showBorder: true },
  'callers must not be able to mutate shared defaults'
)

console.log('form-container-appearance tests passed')
