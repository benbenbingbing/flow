import assert from 'node:assert/strict'

import {
  FORM_NODE_PROPERTY_SCHEMAS,
  buildFormNodePayload,
  extractFormNodeComponentConfig,
  getFormFieldValidationCapabilities,
  getFormNodeDataSourceUsages,
  getFormNodePropertySchema,
  normalizeFormFieldValidation
} from '../form-node-property-schema.js'
import {
  getBuiltInFormFieldSupportedTypes,
  getDefaultFormFieldComponentType,
  isBuiltInFormFieldComponentCompatible,
  normalizeFormNodeFieldType
} from '../form-field-component-policy.js'

const clone = value => JSON.parse(JSON.stringify(value))

const assertMissing = (value, keys, message) => {
  keys.forEach(key => {
    assert.equal(
      Object.prototype.hasOwnProperty.call(value, key),
      false,
      `${message}: should omit ${key}`
    )
  })
}

const expectedSchemas = {
  SECTION: {
    editable: ['label', 'parentId'],
    capabilities: {}
  },
  GRID: {
    editable: ['parentId', 'gutter', 'defaultSpan'],
    capabilities: {}
  },
  TAB_SET: {
    editable: ['parentId', 'tabPosition'],
    capabilities: {}
  },
  TAB: {
    editable: ['label', 'parentId'],
    capabilities: {}
  },
  COLLAPSE: {
    editable: ['label', 'parentId', 'defaultExpanded', 'accordion'],
    capabilities: {}
  },
  TEXT: {
    editable: ['parentId', 'text'],
    capabilities: {}
  },
  FIELD: {
    editable: [
      'label',
      'parentId',
      'componentType',
      'required',
      'readonly',
      'hidden',
      'defaultValue',
      'placeholder',
      'dataSource',
      'componentProps',
      'validation',
      'modeAccess',
      'gridSpan',
      'events',
      'template',
      'nodeExtension'
    ],
    capabilities: {
      fieldProperties: true,
      nodeExtension: true,
      rules: true,
      binding: true,
      childForm: false,
      template: true,
      gridSpan: true
    }
  },
  SUB_FORM: {
    editable: [
      'label',
      'parentId',
      'displayMode',
      'layout',
      'childFormRelease',
      'dataSource',
      'gridSpan',
      'template',
      'nodeExtension'
    ],
    capabilities: {
      fieldProperties: false,
      nodeExtension: true,
      rules: false,
      binding: true,
      childForm: true,
      template: true,
      gridSpan: true
    }
  },
  REPEATER: {
    editable: [
      'label',
      'parentId',
      'displayMode',
      'layout',
      'childFormRelease',
      'dataSource',
      'gridSpan',
      'template',
      'nodeExtension'
    ],
    capabilities: {
      fieldProperties: false,
      nodeExtension: true,
      rules: false,
      binding: true,
      childForm: true,
      template: true,
      gridSpan: true
    }
  },
  ACTION_SLOT: {
    editable: ['parentId'],
    capabilities: {}
  }
}

assert.deepEqual(
  Object.keys(FORM_NODE_PROPERTY_SCHEMAS).sort(),
  Object.keys(expectedSchemas).sort(),
  'property schema must cover exactly the ten supported node types'
)

Object.entries(expectedSchemas).forEach(([nodeType, expected]) => {
  const actual = getFormNodePropertySchema(nodeType)
  assert.deepEqual(actual.editable, expected.editable, `${nodeType} editable properties`)
  Object.entries({
    fieldProperties: false,
    nodeExtension: false,
    rules: false,
    binding: false,
    childForm: false,
    template: false,
    gridSpan: false,
    ...expected.capabilities
  }).forEach(([capability, expectedValue]) => {
    assert.equal(actual[capability], expectedValue, `${nodeType}.${capability}`)
  })
})

assert.deepEqual(
  getFormNodeDataSourceUsages('FIELD'),
  ['FIELD_OPTIONS', 'FIELD_DEFAULT', 'FIELD_COMPUTE', 'AFTER_LOAD', 'BEFORE_SUBMIT']
)
assert.deepEqual(
  getFormNodeDataSourceUsages('SUB_FORM'),
  ['SUBFORM_ROWS', 'AFTER_LOAD', 'BEFORE_SUBMIT']
)
assert.deepEqual(
  getFormNodeDataSourceUsages('REPEATER'),
  ['SUBFORM_ROWS', 'AFTER_LOAD', 'BEFORE_SUBMIT']
)

const structuralNodeCases = {
  SECTION: {},
  GRID: { gutter: 24, defaultSpan: 8 },
  TAB_SET: { tabPosition: 'left' },
  TAB: {},
  COLLAPSE: { defaultExpanded: false, accordion: true },
  TEXT: { text: '只读说明' },
  ACTION_SLOT: {}
}

Object.entries(structuralNodeCases).forEach(([nodeType, componentProps]) => {
  const payload = buildFormNodePayload(
    {
      id: `node-${nodeType}`,
      nodeType,
      nodeKey: `node_${nodeType.toLowerCase()}`,
      fieldId: 'should-not-leak',
      fieldCode: 'should_not_leak',
      fieldName: '不应进入容器属性',
      fieldLabel: `${nodeType} label`,
      fieldType: 'STRING',
      componentType: 'input',
      componentName: 'forbidden-component',
      componentVersion: 9,
      snapshotVersion: 8,
      validationRules: { required: true },
      extensionConfig: { arbitrary: true },
      dataSourceBindings: {
        FIELD_OPTIONS: { sourceId: 'forbidden-source' }
      },
      dataSourceId: 'forbidden-source',
      dataSourceUsage: 'FIELD_OPTIONS',
      templateId: 'forbidden-template',
      templateVersion: 7,
      localOverrides: { label: 'forbidden' }
    },
    { componentProps }
  )

  assertMissing(
    payload,
    [
      'rules',
      'dataSourceBindings',
      'componentName',
      'componentVersion',
      'snapshotVersion',
      'templateId',
      'templateVersion',
      'localOverrides'
    ],
    `${nodeType} payload`
  )
  assertMissing(
    payload.props,
    ['fieldId', 'fieldCode', 'fieldName', 'fieldType', 'componentType', 'componentProps'],
    `${nodeType} props`
  )
})

const fieldPayload = buildFormNodePayload(
  {
    id: 'field-node',
    nodeType: 'FIELD',
    nodeKey: 'field_amount',
    fieldId: '101',
    fieldCode: 'amount',
    fieldName: '金额',
    fieldLabel: '申请金额',
    fieldType: 'DECIMAL',
    componentType: 'number',
    placeholder: '请输入金额',
    defaultValue: 0,
    gridSpan: 12,
    isRequired: 1,
    isReadonly: 0,
    isHidden: 0,
    componentName: 'currency-field',
    componentVersion: 3,
    snapshotVersion: 5,
    validationRules: {
      min: 0,
      max: 100000
    },
    extensionConfig: {
      modeAccess: {
        create: 'EDIT'
      }
    },
    dataSourceBindings: {
      FIELD_OPTIONS: {
        sourceId: 'option-source'
      },
      SUBFORM_ROWS: {
        sourceId: 'must-be-filtered'
      }
    },
    dataSourceUsage: 'FIELD_DEFAULT',
    dataSourceId: 'default-source',
    dataSourceInputMappingText: '{"entityId":"$context.entityId"}',
    dataSourceOutputMappingText: '{"value":"amount"}',
    templateId: 'field-template',
    templateVersion: 4,
    localOverrides: {
      label: '申请金额'
    }
  },
  {
    componentProps: {
      precision: 2,
      controls: false
    }
  }
)

assert.equal(fieldPayload.bindingType, 'ENTITY_FIELD')
assert.equal(fieldPayload.bindingRef, 'amount')
assert.deepEqual(fieldPayload.props, {
  fieldId: '101',
  fieldCode: 'amount',
  fieldName: '金额',
  label: '申请金额',
  fieldType: 'DECIMAL',
  componentType: 'number',
  placeholder: '请输入金额',
  defaultValue: 0,
  gridSpan: 12,
  required: true,
  readonly: false,
  hidden: false,
  componentProps: {
    precision: 2,
    controls: false
  }
})
assert.deepEqual(fieldPayload.rules, {
  validation: {
    min: 0,
    max: 100000
  },
  extension: {
    modeAccess: {
      create: 'EDIT'
    }
  }
})
assert.deepEqual(Object.keys(fieldPayload.dataSourceBindings).sort(), [
  'FIELD_DEFAULT',
  'FIELD_OPTIONS'
])
assert.deepEqual(fieldPayload.dataSourceBindings.FIELD_OPTIONS, {
  sourceId: 'option-source'
})
assert.deepEqual(fieldPayload.dataSourceBindings.FIELD_DEFAULT, {
  sourceId: 'default-source',
  inputMapping: {
    entityId: '$context.entityId'
  },
  outputMapping: {
    value: 'amount'
  }
})
assert.equal(fieldPayload.componentName, 'currency-field')
assert.equal(fieldPayload.componentVersion, 3)
assert.equal(fieldPayload.snapshotVersion, 5)
assert.equal(fieldPayload.templateId, 'field-template')
assert.equal(fieldPayload.templateVersion, 4)
assert.deepEqual(fieldPayload.localOverrides, {
  label: '申请金额'
})

;['SUB_FORM', 'REPEATER'].forEach(nodeType => {
  const payload = buildFormNodePayload(
    {
      id: `${nodeType.toLowerCase()}-node`,
      nodeType,
      nodeKey: `${nodeType.toLowerCase()}_items`,
      fieldId: 'relation-201',
      fieldCode: 'items',
      fieldName: '明细',
      fieldLabel: nodeType === 'SUB_FORM' ? '子表' : '明细表',
      fieldType: nodeType === 'SUB_FORM' ? 'SUB_FORM' : 'SUB_FORM_LIST',
      componentType: nodeType === 'SUB_FORM' ? 'sub_form' : 'sub_form_list',
      gridSpan: 24,
      relationCode: 'items_relation',
      childFormId: 'child-form-1',
      childFormReleaseId: 'child-release-3',
      childFormReleaseVersion: '3',
      validationRules: {
        minLength: 2
      },
      extensionConfig: {
        required: true
      },
      dataSourceBindings: {
        SUBFORM_ROWS: {
          sourceId: 'row-source'
        },
        AFTER_LOAD: {
          sourceId: 'after-load-source'
        },
        BEFORE_SUBMIT: {
          sourceId: 'before-submit-source'
        },
        FIELD_OPTIONS: {
          sourceId: 'must-be-filtered'
        },
        FIELD_DEFAULT: {
          sourceId: 'must-also-be-filtered'
        }
      },
      dataSourceUsage: nodeType === 'SUB_FORM' ? 'FIELD_OPTIONS' : 'BEFORE_SUBMIT',
      dataSourceId: `${nodeType.toLowerCase()}-source`,
      dataSourceInputMappingText: '{"recordId":"$context.recordId"}',
      dataSourceOutputMappingText: '{"rows":"items"}',
      templateId: 'subform-template',
      templateVersion: 2,
      localOverrides: {
        displayMode: 'embedded'
      }
    },
    {
      componentProps: {
        displayMode: 'embedded',
        layout: 'table'
      }
    }
  )

  assert.equal(
    Object.prototype.hasOwnProperty.call(payload, 'rules'),
    false,
    `${nodeType} must not serialize field rules`
  )
  assert.deepEqual(
    Object.keys(payload.dataSourceBindings).sort(),
    ['AFTER_LOAD', 'BEFORE_SUBMIT', 'SUBFORM_ROWS'],
    `${nodeType} must preserve every allowed row lifecycle usage`
  )
  assert.equal(
    Object.prototype.hasOwnProperty.call(
      payload.dataSourceBindings,
      'FIELD_OPTIONS'
    ),
    false,
    `${nodeType} must remove unsupported field data source usages`
  )
  if (nodeType === 'REPEATER') {
    assert.equal(
      payload.dataSourceBindings.BEFORE_SUBMIT.sourceId,
      'repeater-source',
      'only the selected REPEATER usage should be updated'
    )
  }
  assert.equal(payload.childFormId, 'child-form-1')
  assert.equal(payload.childFormReleaseId, 'child-release-3')
  assert.equal(payload.childFormReleaseVersion, 3)
})

const collapsePatch = buildFormNodePayload(
  {
    id: 'collapse-patch',
    nodeType: 'COLLAPSE',
    nodeKey: 'collapse_patch',
    fieldLabel: '折叠面板'
  },
  {
    componentProps: {
      defaultExpanded: true,
      accordion: false
    },
    forPatch: true
  }
)
const collapseClearFields = new Set(collapsePatch.clearFields)
assertMissing(
  collapsePatch,
  [
    'id',
    'nodeKey',
    'nodeType',
    'bindingType',
    'bindingRef',
    'legacyProps'
  ],
  'PATCH payload must omit technical identity and compatibility fields'
)
;[
  'componentName',
  'componentVersion',
  'snapshotVersion',
  'rules',
  'dataSourceBindings',
  'childFormId',
  'childFormReleaseId',
  'childFormReleaseVersion',
  'templateId',
  'templateVersion',
  'localOverrides',
  'bindingRef'
].forEach(key => {
  assert.equal(collapseClearFields.has(key), true, `COLLAPSE PATCH clears ${key}`)
})

assert.deepEqual(
  getFormFieldValidationCapabilities('STRING'),
  {
    length: true,
    range: false,
    format: true
  }
)
assert.deepEqual(
  getFormFieldValidationCapabilities('DECIMAL'),
  {
    length: false,
    range: true,
    format: false
  }
)
assert.deepEqual(
  getFormFieldValidationCapabilities('DATE'),
  {
    length: false,
    range: false,
    format: false
  }
)
assert.deepEqual(
  normalizeFormFieldValidation('DECIMAL', {
    minLength: 2,
    maxLength: 10,
    min: 0,
    max: 100,
    format: 'EMAIL',
    customRule: 'keep'
  }),
  {
    min: 0,
    max: 100,
    customRule: 'keep'
  }
)
assert.deepEqual(
  normalizeFormFieldValidation('STRING', {
    minLength: 2,
    maxLength: 10,
    min: 0,
    max: 100,
    format: 'EMAIL'
  }),
  {
    minLength: 2,
    maxLength: 10,
    format: 'EMAIL'
  }
)

assert.deepEqual(
  extractFormNodeComponentConfig('GRID', {
    gutter: 32,
    defaultSpan: 6,
    componentProps: {
      gutter: 8,
      defaultSpan: 12
    }
  }),
  {
    gutter: 32,
    defaultSpan: 6
  },
  'new top-level container config takes precedence'
)
assert.deepEqual(
  extractFormNodeComponentConfig(
    'GRID',
    '{"componentProps":{"gutter":16,"defaultSpan":8}}'
  ),
  {
    gutter: 16,
    defaultSpan: 8
  },
  'legacy nested container config remains readable'
)
assert.deepEqual(
  extractFormNodeComponentConfig('COLLAPSE', {
    defaultExpanded: false,
    componentProps: {
      defaultExpanded: true,
      accordion: true
    }
  }),
  {
    defaultExpanded: false,
    accordion: true
  },
  'explicit false in new container config must not fall back to legacy values'
)
assert.deepEqual(
  extractFormNodeComponentConfig('TEXT', {
    componentProps: {
      content: '历史说明文本'
    }
  }),
  {
    text: '历史说明文本'
  })

const legacyTextPatch = buildFormNodePayload(
  {
    id: 'legacy-text',
    nodeType: 'TEXT',
    nodeKey: 'legacy_text',
    parentId: 'section-1',
    fieldLabel: '说明'
  },
  {
    componentProps: {
      content: '历史说明文本'
    },
    forPatch: true
  }
)
assert.equal(legacyTextPatch.props.text, '历史说明文本')
assert.equal(
  Object.prototype.hasOwnProperty.call(legacyTextPatch.props, 'content'),
  false,
  'TEXT history alias content must never be written back'
)

const multiBindingPatch = buildFormNodePayload(
  {
    id: 'multi-binding-field',
    nodeType: 'FIELD',
    nodeKey: 'multi_binding_field',
    parentId: 'section-1',
    fieldId: '303',
    fieldCode: 'category',
    fieldName: '分类',
    fieldLabel: '分类（已编辑标签）',
    fieldType: 'STRING',
    componentType: 'select',
    dataSourceBindings: {
      FIELD_OPTIONS: {
        sourceId: 'options-source',
        cache: {
          ttlSeconds: 60
        }
      },
      FIELD_DEFAULT: {
        sourceId: 'old-default-source',
        timeoutMs: 800
      },
      AFTER_LOAD: {
        sourceId: 'after-load-source'
      },
      SUBFORM_ROWS: {
        sourceId: 'unsupported-source'
      }
    },
    dataSourceUsage: 'FIELD_DEFAULT',
    dataSourceId: 'new-default-source',
    dataSourceInputMappingText: '{"recordId":"$context.recordId"}',
    dataSourceOutputMappingText: '{"value":"defaultValue"}'
  },
  {
    forPatch: true
  }
)
assert.deepEqual(
  Object.keys(multiBindingPatch.dataSourceBindings).sort(),
  ['AFTER_LOAD', 'FIELD_DEFAULT', 'FIELD_OPTIONS'],
  'PATCH must preserve all existing allowed data source usages'
)
assert.deepEqual(multiBindingPatch.dataSourceBindings.FIELD_OPTIONS, {
  sourceId: 'options-source',
  cache: {
    ttlSeconds: 60
  }
})
assert.deepEqual(multiBindingPatch.dataSourceBindings.AFTER_LOAD, {
  sourceId: 'after-load-source'
})
assert.deepEqual(multiBindingPatch.dataSourceBindings.FIELD_DEFAULT, {
  sourceId: 'new-default-source',
  timeoutMs: 800,
  inputMapping: {
    recordId: '$context.recordId'
  },
  outputMapping: {
    value: 'defaultValue'
  }
})

const removeSelectedBindingPatch = buildFormNodePayload(
  {
    id: 'remove-one-binding',
    nodeType: 'FIELD',
    nodeKey: 'remove_one_binding',
    parentId: 'section-1',
    fieldId: '304',
    fieldCode: 'status',
    fieldName: '状态',
    fieldLabel: '状态',
    fieldType: 'STRING',
    componentType: 'select',
    dataSourceBindings: {
      FIELD_OPTIONS: {
        sourceId: 'options-source'
      },
      FIELD_DEFAULT: {
        sourceId: 'default-source'
      }
    },
    dataSourceUsage: 'FIELD_DEFAULT',
    dataSourceId: ''
  },
  {
    forPatch: true
  }
)
assert.deepEqual(removeSelectedBindingPatch.dataSourceBindings, {
  FIELD_OPTIONS: {
    sourceId: 'options-source'
  }
})
assert.equal(
  removeSelectedBindingPatch.clearFields.includes('dataSourceBindings'),
  false,
  'removing one usage must not clear the remaining bindings'
)

const rootParentPatch = buildFormNodePayload(
  {
    id: 'move-to-root',
    nodeType: 'SECTION',
    nodeKey: 'move_to_root',
    parentId: '',
    fieldLabel: '根区块'
  },
  {
    forPatch: true
  }
)
assert.equal(rootParentPatch.parentId, null)
assert.equal(
  rootParentPatch.clearFields.includes('parentId'),
  true,
  'moving a node to root must explicitly clear parentId'
)

const untouchedParentPatch = buildFormNodePayload(
  {
    id: 'leave-parent-untouched',
    nodeType: 'SECTION',
    nodeKey: 'leave_parent_untouched',
    fieldLabel: '不修改父容器'
  },
  {
    forPatch: true
  }
)
assert.equal(
  Object.prototype.hasOwnProperty.call(untouchedParentPatch, 'parentId'),
  false,
  'PATCH without parentId must not imply moving to root'
)
assert.equal(
  untouchedParentPatch.clearFields.includes('parentId'),
  false,
  'PATCH without parentId must not clear parentId'
)

const clearOptionalTextPatch = buildFormNodePayload(
  {
    id: 'clear-optional-text',
    nodeType: 'FIELD',
    nodeKey: 'clear_optional_text',
    fieldId: '305',
    fieldCode: 'description',
    fieldName: '说明',
    fieldLabel: '说明',
    fieldType: 'STRING',
    componentType: 'input',
    placeholder: '',
    defaultValue: ''
  },
  {
    forPatch: true
  }
)
assert.equal(
  Object.prototype.hasOwnProperty.call(
    clearOptionalTextPatch.props,
    'placeholder'
  ),
  true,
  'PATCH must keep an explicit empty placeholder so editing can clear it'
)
assert.equal(clearOptionalTextPatch.props.placeholder, '')
assert.equal(
  Object.prototype.hasOwnProperty.call(
    clearOptionalTextPatch.props,
    'defaultValue'
  ),
  true,
  'PATCH must keep an explicit empty default value so editing can clear it'
)
assert.equal(clearOptionalTextPatch.props.defaultValue, '')

const compatibleDefaults = {
  RICH_TEXT: 'rich_text',
  FILE: 'file',
  IMAGE: 'image',
  USER: 'reference',
  DEPT: 'reference'
}
Object.entries(compatibleDefaults).forEach(([fieldType, componentType]) => {
  assert.equal(
    getDefaultFormFieldComponentType(fieldType),
    componentType,
    `${fieldType} must use a compatible default component`
  )
  assert.equal(
    isBuiltInFormFieldComponentCompatible(fieldType, componentType),
    true,
    `${componentType} must declare ${fieldType} support`
  )
  assert.equal(
    isBuiltInFormFieldComponentCompatible(fieldType, 'input'),
    false,
    `input must not be accepted as the fallback for ${fieldType}`
  )
})
assert.deepEqual(
  getBuiltInFormFieldSupportedTypes('rich_text'),
  ['TEXT', 'RICH_TEXT']
)
assert.equal(
  normalizeFormNodeFieldType('RICH_TEXT', 'rich_text'),
  'TEXT',
  'RICH_TEXT must use the backend-compatible TEXT form-node type'
)
const richTextPayload = buildFormNodePayload({
  id: 'rich-text-field',
  nodeType: 'FIELD',
  nodeKey: 'rich_text_field',
  fieldId: '305',
  fieldCode: 'description',
  fieldName: '说明',
  fieldLabel: '说明',
  fieldType: 'RICH_TEXT',
  componentType: getDefaultFormFieldComponentType('RICH_TEXT')
})
assert.equal(richTextPayload.props.fieldType, 'TEXT')
assert.equal(richTextPayload.props.componentType, 'rich_text')

const immutableField = {
  id: 'immutable-field',
  nodeType: 'FIELD',
  nodeKey: 'immutable_field',
  fieldId: '202',
  fieldCode: 'immutable',
  fieldName: '不可变输入',
  fieldLabel: '不可变输入',
  fieldType: 'STRING',
  componentType: 'input',
  validationRules: {
    pattern: '^safe$'
  },
  extensionConfig: {
    events: {
      change: {
        action: 'refresh'
      }
    }
  },
  dataSourceBindings: {
    FIELD_OPTIONS: {
      sourceId: 'immutable-source',
      inputMapping: {
        tenantId: '$context.tenantId'
      }
    }
  },
  localOverrides: {
    componentProps: {
      maxlength: 64
    }
  }
}
const immutableComponentProps = {
  options: [
    {
      label: 'A',
      value: 'A'
    }
  ],
  events: {
    blur: {
      action: 'validate'
    }
  }
}
const immutableFieldBefore = clone(immutableField)
const immutableComponentPropsBefore = clone(immutableComponentProps)

buildFormNodePayload(immutableField, {
  componentProps: immutableComponentProps,
  forPatch: true
})

assert.deepEqual(
  immutableField,
  immutableFieldBefore,
  'payload serialization must not mutate the field model'
)
assert.deepEqual(
  immutableComponentProps,
  immutableComponentPropsBefore,
  'payload serialization must not mutate component props'
)

const explicitBindingPayload = buildFormNodePayload({
  id: 'context-field',
  nodeType: 'FIELD',
  nodeKey: 'context_current_user',
  fieldLabel: '当前用户',
  fieldType: 'STRING',
  componentType: 'input',
  bindingType: 'CONTEXT',
  bindingRef: 'currentUser.id'
})

assert.equal(
  explicitBindingPayload.bindingType,
  'CONTEXT',
  'serializer must preserve the persisted explicit binding type during editing'
)
assert.equal(
  explicitBindingPayload.bindingRef,
  'currentUser.id',
  'serializer must preserve the persisted explicit binding reference during editing'
)

console.log('form node property schema tests passed')
