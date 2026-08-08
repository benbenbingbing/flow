import assert from 'node:assert/strict'
import {
  existsSync,
  mkdirSync,
  readFileSync,
  unlinkSync,
  writeFileSync
} from 'node:fs'
import path from 'node:path'

const apiBase = process.env.WORKFLOW_API_BASE || 'http://127.0.0.1:8080/api'
const username = process.env.TEST_USERNAME
const password = process.env.TEST_PASSWORD
const tokenFile = process.env.TEST_TOKEN_FILE
  || '/private/tmp/workflow-zdw-form-list-e2e.token'
const credentialFile = process.env.TEST_CREDENTIAL_FILE
  || '/private/tmp/workflow-zdw-form-list-e2e.credentials.json'
const evidenceDir = path.resolve('docs/zdw-form-list-e2e')
const parentFormKey = process.env.ZDW_PARENT_FORM_KEY || 'form001'
const childFormKey = process.env.ZDW_CHILD_FORM_KEY || 'form001'

const componentByFieldType = Object.freeze({
  STRING: 'input',
  TEXT: 'textarea',
  RICH_TEXT: 'rich_text',
  INTEGER: 'number',
  LONG: 'number',
  DECIMAL: 'number',
  DOUBLE: 'number',
  DATE: 'date',
  DATETIME: 'datetime',
  BOOLEAN: 'switch',
  SELECT: 'select',
  MULTI_SELECT: 'select_multiple',
  RADIO: 'radio',
  CHECKBOX: 'checkbox',
  FILE: 'file',
  IMAGE: 'image',
  USER: 'reference',
  DEPT: 'reference',
  ROLE: 'reference',
  GROUP: 'reference',
  REFERENCE: 'reference',
  MULTI_REFERENCE: 'multi_reference'
})

const matrixFieldCodes = Object.freeze([
  'contactEmail',
  'plainNote',
  'estimatedCount',
  'budgetAmount',
  'expectedDate',
  'reviewAt',
  'enabledFlag',
  'singleOption',
  'multiOptions',
  'radioOption',
  'checkboxOptions',
  'testFiles',
  'testImage',
  'ownerUser',
  'ownerDept',
  'primaryItem',
  'relatedItems'
])

const expectedFormNodeTypes = Object.freeze([
  'SECTION',
  'GRID',
  'TAB_SET',
  'TAB',
  'COLLAPSE',
  'TEXT',
  'FIELD',
  'SUB_FORM',
  'REPEATER',
  'ACTION_SLOT'
])

assert.ok(
  existsSync(tokenFile)
    || existsSync(credentialFile)
    || (username && password),
  'TEST_TOKEN_FILE, TEST_CREDENTIAL_FILE, or TEST_USERNAME/TEST_PASSWORD is required'
)

mkdirSync(evidenceDir, { recursive: true })

let token = ''
const evidence = {
  generatedAt: new Date().toISOString(),
  apiBase,
  entities: {},
  steps: []
}

async function api(method, url, body) {
  const response = await fetch(`${apiBase}${url}`, {
    method,
    signal: AbortSignal.timeout(30000),
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body == null ? undefined : JSON.stringify(body)
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok
      || !payload
      || ![0, 200, '0', '200'].includes(payload.code)) {
    const error = new Error(
      payload?.message || `${method} ${url} failed: HTTP ${response.status}`
    )
    error.status = response.status
    error.errorCode = payload?.errorCode
    throw error
  }
  return payload.data
}

async function apiExpectError(method, url, body) {
  const response = await fetch(`${apiBase}${url}`, {
    method,
    signal: AbortSignal.timeout(30000),
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body == null ? undefined : JSON.stringify(body)
  })
  const payload = await response.json().catch(() => null)
  assert.ok(
    !response.ok
      || !payload
      || ![0, 200, '0', '200'].includes(payload.code),
    `${method} ${url} 应当失败`
  )
  return {
    status: response.status,
    code: payload?.code,
    errorCode: payload?.errorCode,
    message: payload?.message
  }
}

function record(name, data) {
  evidence.steps.push({ name, data })
  return data
}

function parseObject(value) {
  if (!value) return {}
  if (typeof value === 'object' && !Array.isArray(value)) {
    return structuredClone(value)
  }
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed
      : {}
  } catch {
    return {}
  }
}

function document(value) {
  return value && Object.keys(value).length
    ? JSON.stringify(value)
    : null
}

function formNodeRecord({
  id,
  formId,
  parentId = null,
  nodeKey = id,
  nodeType,
  bindingType = 'NONE',
  bindingRef = null,
  props = {},
  rules = {},
  dataSourceBindings = {},
  legacyProps = {},
  orderKey = 1_000_000,
  revision = 1,
  componentName = null,
  componentVersion = null,
  snapshotVersion = null,
  childFormId = null,
  childFormReleaseId = null,
  childFormReleaseVersion = null,
  templateId = null,
  templateVersion = null,
  localOverrides = {}
}) {
  return {
    id,
    formId,
    parentId,
    nodeKey,
    nodeType,
    bindingType,
    bindingRef,
    componentName,
    componentVersion,
    snapshotVersion,
    propsDocument: document(props),
    rulesDocument: document(rules),
    dataSourceBindingsDocument: document(dataSourceBindings),
    legacyPropsDocument: document(legacyProps),
    orderKey,
    revision,
    templateId,
    templateVersion,
    localOverridesDocument: document(localOverrides),
    deleted: 0,
    childFormId,
    childFormReleaseId,
    childFormReleaseVersion
  }
}

async function entityByCode(code) {
  return api('GET', `/entity/code/${encodeURIComponent(code)}`)
}

async function formByKey(entityId, formKey) {
  const forms = await api('GET', `/entity-form/entity/${entityId}`)
  const form = (forms || []).find(item => item.formKey === formKey)
  assert.ok(form?.id, `实体 ${entityId} 缺少表单 ${formKey}`)
  return api('GET', `/entity-form/${form.id}`)
}

async function activeFormRelease(formId) {
  const releases = await api('GET', `/entity-forms/${formId}/releases`)
  const release = (releases || [])
    .filter(item => item?.id)
    .sort((left, right) => Number(right.version) - Number(left.version))
    .find(item => String(item.status || '').toUpperCase() === 'ACTIVE')
    || (releases || [])
      .filter(item => item?.id)
      .sort((left, right) => Number(right.version) - Number(left.version))[0]
  assert.ok(release?.id, `表单 ${formId} 缺少已发布版本`)
  return release
}

async function listByKey(entityId, listKey = 'list001') {
  const lists = await api('GET', `/entity-list-config/entity/${entityId}`)
  const list = (lists || []).find(item => item.listKey === listKey)
  assert.ok(list?.id, `实体 ${entityId} 缺少列表 ${listKey}`)
  return api('GET', `/entity-list-config/${list.id}`)
}

async function ensureDictionary() {
  const dictCode = 'zdw_e2e_options'
  const dictionaries = await api('GET', '/system/dict/list')
  const existing = (dictionaries || []).find(dict => dict.dictCode === dictCode)
  if (existing) {
    record('reuseDictionary', {
      id: existing.id,
      dictCode,
      dictName: existing.dictName
    })
    return existing
  }
  const created = await api('POST', '/system/dict/with-items', {
    dict: {
      dictCode,
      dictName: 'ZDW 全类型测试选项',
      description: '用于 ZDWREQ 表单、列表和流程真实回归',
      status: '0',
      sort: 900
    },
    items: [
      { itemCode: 'A', itemLabel: '选项 A', itemValue: 'A', sort: 10 },
      { itemCode: 'B', itemLabel: '选项 B', itemValue: 'B', sort: 20 },
      { itemCode: 'C', itemLabel: '选项 C', itemValue: 'C', sort: 30 }
    ]
  })
  record('createDictionary', {
    id: created.id,
    dictCode: created.dictCode,
    dictName: created.dictName
  })
  return created
}

function fieldPayload(field) {
  return {
    isRequired: false,
    isUnique: false,
    editable: true,
    sortOrder: 100,
    ...field
  }
}

async function ensureField(entity, desired) {
  const detail = await entityByCode(entity.entityCode)
  const current = (detail.fields || []).find(
    field => field.fieldCode === desired.fieldCode
  )
  const payload = fieldPayload(desired)
  if (!current) {
    const created = await api(
      'POST',
      `/entity/${entity.id}/fields`,
      payload
    )
    record('createField', {
      entityCode: entity.entityCode,
      fieldCode: created.fieldCode,
      fieldType: created.fieldType,
      id: created.id
    })
    return created
  }
  assert.equal(
    current.fieldType,
    desired.fieldType,
    `${entity.entityCode}.${desired.fieldCode} 类型不一致`
  )
  const updated = await api(
    'POST',
    `/entity/${entity.id}/fields/${current.id}/update`,
    {
      ...current,
      ...payload,
      id: current.id
    }
  )
  record('updateField', {
    entityCode: entity.entityCode,
    fieldCode: updated.fieldCode,
    fieldType: updated.fieldType,
    id: updated.id
  })
  return updated
}

async function publishEntity(entity, description) {
  const published = await api(
    'POST',
    `/entity/${entity.id}/publish`,
    { versionDescription: description }
  )
  record('publishEntity', {
    entityCode: entity.entityCode,
    id: entity.id,
    status: published.status
  })
  return published
}

function assertFieldTypes(fields) {
  const expected = [
    'STRING',
    'TEXT',
    'RICH_TEXT',
    'INTEGER',
    'DECIMAL',
    'DATE',
    'DATETIME',
    'BOOLEAN',
    'SELECT',
    'MULTI_SELECT',
    'RADIO',
    'CHECKBOX',
    'FILE',
    'IMAGE',
    'USER',
    'DEPT',
    'REFERENCE',
    'MULTI_REFERENCE',
    'SUB_FORM',
    'SUB_LIST'
  ]
  const actual = new Set(fields.map(field => field.fieldType))
  expected.forEach(type => {
    assert.ok(actual.has(type), `ZDWREQ 缺少字段类型 ${type}`)
  })
}

function fieldDefaultValue(field) {
  const value = field.defaultValue
  if (value == null || value === '') return null
  switch (String(field.fieldType || '').toUpperCase()) {
    case 'BOOLEAN':
      return value === true || String(value).toLowerCase() === 'true'
    case 'INTEGER':
    case 'LONG':
    case 'DECIMAL':
    case 'DOUBLE': {
      const number = Number(value)
      return Number.isFinite(number) ? number : value
    }
    default:
      return value
  }
}

function matrixOptions() {
  return [
    { label: '选项 A', value: 'A' },
    { label: '选项 B', value: 'B' },
    { label: '选项 C', value: 'C' }
  ]
}

function fieldComponentProps(field, child) {
  const type = String(field.fieldType || '').toUpperCase()
  const commonOptions = matrixOptions()
  switch (type) {
    case 'STRING':
      return {
        maxlength: Number(field.fieldLength || 120),
        showWordLimit: true
      }
    case 'TEXT':
      return { rows: 4, maxlength: 2000 }
    case 'RICH_TEXT':
      return { height: 240 }
    case 'INTEGER':
      return {
        min: 0,
        max: 9999,
        precision: 0,
        step: 1,
        controls: true
      }
    case 'DECIMAL':
      return {
        min: 0,
        max: 99999999.99,
        precision: Number(field.fieldPrecision || 2),
        step: 0.01,
        controls: true
      }
    case 'DATE':
      return { valueFormat: 'YYYY-MM-DD' }
    case 'DATETIME':
      return { valueFormat: 'YYYY-MM-DD HH:mm:ss' }
    case 'BOOLEAN':
      return { activeText: '启用', inactiveText: '停用' }
    case 'SELECT':
    case 'MULTI_SELECT':
    case 'RADIO':
    case 'CHECKBOX':
      return {
        dictType: field.dictType || 'zdw_e2e_options',
        options: commonOptions
      }
    case 'FILE':
    case 'IMAGE':
      return {
        fileTypes: field.fileTypes || '',
        fileMaxSize: Number(field.fileMaxSize || 10),
        fileMaxCount: Number(field.fileMaxCount || 3),
        fileItems: Array.isArray(field.fileItems)
          ? field.fileItems
          : []
      }
    case 'USER':
    case 'DEPT':
      return {
        refConfig: {
          refEntityType: field.refEntityType || type,
          refEntityId: '',
          entityCode: '',
          listKey: '',
          apiUrl: ''
        }
      }
    case 'REFERENCE':
    case 'MULTI_REFERENCE':
      return {
        refConfig: {
          refEntityType: field.refEntityType || 'CUSTOM',
          refEntityId: field.refEntityId || child.id,
          entityCode: child.entityCode,
          listKey: 'list001',
          apiUrl: ''
        }
      }
    default:
      return {}
  }
}

function fieldModeExtension(fieldCode) {
  const approvalVisible = fieldCode !== 'plainNote'
  const approvalEditable = fieldCode === 'expectedDate'
  return {
    modes: {
      create: { visible: true, editable: true },
      edit: { visible: true, editable: true },
      approve: {
        visible: approvalVisible,
        editable: approvalEditable
      },
      view: { visible: true }
    }
  }
}

function matrixFieldNode(field, parent, child, index) {
  const fieldType = String(field.fieldType || '').toUpperCase()
  const componentType = componentByFieldType[fieldType]
  assert.ok(componentType, `${field.fieldCode} 缺少组件映射`)
  const isWide = [
    'TEXT',
    'RICH_TEXT',
    'FILE',
    'IMAGE',
    'MULTI_REFERENCE',
    'MULTI_SELECT',
    'CHECKBOX'
  ].includes(fieldType)
  return formNodeRecord({
    id: `zdw_e2e_field_${field.fieldCode}`,
    formId: parent.formId,
    parentId: parent.parentId,
    nodeKey: field.fieldCode,
    nodeType: 'FIELD',
    bindingType: 'ENTITY_FIELD',
    bindingRef: field.fieldCode,
    orderKey: (index + 1) * 1_000_000,
    props: {
      fieldId: field.id,
      fieldCode: field.fieldCode,
      fieldName: field.fieldName,
      label: field.fieldName,
      fieldType,
      componentType,
      placeholder: `请填写${field.fieldName}`,
      defaultValue: fieldDefaultValue(field),
      gridSpan: isWide ? 24 : 12,
      required: field.isRequired === true || field.isRequired === 1,
      readonly: field.fieldCode === 'ownerDept',
      hidden: false,
      componentProps: fieldComponentProps(field, child)
    },
    rules: {
      validation: parseObject(field.validateRules),
      extension: fieldModeExtension(field.fieldCode)
    }
  })
}

function mergeExistingNodeProps(node, patch) {
  return {
    ...node,
    propsDocument: JSON.stringify({
      ...parseObject(node.propsDocument),
      ...patch
    })
  }
}

function subFormNode(field, parentForm, child, childForm, childRelease) {
  const parameterContract = {
    version: 1,
    parameterMapping: {},
    fieldInitializationMapping: {
      name: 'parent.data.name'
    }
  }
  const subFormConfig = {
    layout: 'form',
    refEntityId: child.id,
    childEntityId: child.id,
    childFormId: childForm.id,
    refFormId: childForm.id,
    publishedFormId: childForm.id,
    childFormReleaseId: childRelease.id,
    refFormReleaseId: childRelease.id,
    publishedFormReleaseId: childRelease.id,
    childFormReleaseVersion: Number(childRelease.version),
    refFormReleaseVersion: Number(childRelease.version),
    publishedFormReleaseVersion: Number(childRelease.version),
    repeatable: false,
    relationType: field.relationType,
    relationCode: field.relationCode,
    childRefFieldCode: field.childRefFieldCode,
    refFieldCode: field.childRefFieldCode,
    parameterContract
  }
  return formNodeRecord({
    id: 'zdw_e2e_sub_form_reqSingleForm',
    formId: parentForm.id,
    parentId: 'zdw_e2e_collapse',
    nodeKey: field.fieldCode,
    nodeType: 'SUB_FORM',
    bindingType: 'RELATION',
    bindingRef: field.relationCode,
    orderKey: 1_000_000,
    childFormId: childForm.id,
    childFormReleaseId: childRelease.id,
    childFormReleaseVersion: Number(childRelease.version),
    props: {
      fieldId: field.id,
      fieldCode: field.fieldCode,
      fieldName: field.fieldName,
      label: field.fieldName,
      fieldType: 'SUB_FORM',
      componentType: 'sub_form',
      gridSpan: 24,
      componentProps: { subFormConfig },
      childFormId: childForm.id,
      refFormId: childForm.id,
      publishedFormId: childForm.id,
      childFormReleaseId: childRelease.id,
      refFormReleaseId: childRelease.id,
      publishedFormReleaseId: childRelease.id,
      childFormReleaseVersion: Number(childRelease.version),
      refFormReleaseVersion: Number(childRelease.version),
      publishedFormReleaseVersion: Number(childRelease.version)
    }
  })
}

async function configureParentForm(parent, child) {
  const parentForm = await formByKey(parent.id, parentFormKey)
  const childForm = await formByKey(child.id, childFormKey)
  const childRelease = await activeFormRelease(childForm.id)
  const parentDetail = await entityByCode(parent.entityCode)
  const fieldByCode = new Map(
    (parentDetail.fields || []).map(field => [field.fieldCode, field])
  )
  matrixFieldCodes.forEach(fieldCode => {
    assert.ok(fieldByCode.has(fieldCode), `表单矩阵缺少实体字段 ${fieldCode}`)
  })
  const singleField = fieldByCode.get('reqSingleForm')
  assert.ok(singleField, '缺少一对一子表字段 reqSingleForm')

  const existingNodes = await api(
    'GET',
    `/entity-forms/${parentForm.id}/nodes`
  )
  const existingNodeById = new Map(
    (existingNodes || []).map(node => [node.id, node])
  )
  const managedNodeIds = new Set([
    'zdw_e2e_section',
    'zdw_e2e_intro',
    'zdw_e2e_grid',
    'zdw_e2e_collapse',
    'zdw_e2e_sub_form_reqSingleForm',
    'zdw_e2e_action_slot',
    ...matrixFieldCodes.map(code => `zdw_e2e_field_${code}`)
  ])
  const preserved = (existingNodes || [])
    .filter(node => !managedNodeIds.has(node.id))
    .map(node => {
      if (node.nodeKey === 'name') {
        return mergeExistingNodeProps(node, {
          label: '需求名称',
          placeholder: '请输入需求名称'
        })
      }
      if (node.nodeKey === 'code') {
        return mergeExistingNodeProps(node, {
          label: '需求编码',
          readonly: true
        })
      }
      if (node.nodeKey === 'status') {
        return mergeExistingNodeProps(node, {
          label: '流程状态',
          hidden: true,
          readonly: true
        })
      }
      if (node.nodeKey === 'req_desc') {
        return mergeExistingNodeProps(node, {
          label: '需求描述（富文本）',
          componentType: 'rich_text',
          componentProps: { height: 240 }
        })
      }
      return node
    })

  const section = formNodeRecord({
    id: 'zdw_e2e_section',
    formId: parentForm.id,
    nodeType: 'SECTION',
    props: { label: '全字段与属性测试' },
    orderKey: 2_000_000
  })
  const intro = formNodeRecord({
    id: 'zdw_e2e_intro',
    formId: parentForm.id,
    parentId: section.id,
    nodeType: 'TEXT',
    props: {
      label: '测试说明',
      text: '覆盖 20 种字段、栅格布局、校验、模式权限、引用显示与子表参数传递。',
      textStyle: 'SECTION_TITLE'
    },
    orderKey: 1_000_000
  })
  const grid = formNodeRecord({
    id: 'zdw_e2e_grid',
    formId: parentForm.id,
    parentId: section.id,
    nodeType: 'GRID',
    props: {
      label: '全类型字段栅格',
      gutter: 16,
      defaultSpan: 12
    },
    orderKey: 2_000_000
  })
  const fields = matrixFieldCodes.map((fieldCode, index) =>
    matrixFieldNode(
      fieldByCode.get(fieldCode),
      { formId: parentForm.id, parentId: grid.id },
      child,
      index
    )
  )
  const collapse = formNodeRecord({
    id: 'zdw_e2e_collapse',
    formId: parentForm.id,
    parentId: section.id,
    nodeType: 'COLLAPSE',
    props: {
      label: '一对一子表单测试',
      defaultExpanded: true,
      accordion: false
    },
    orderKey: 3_000_000
  })
  const singleSubForm = subFormNode(
    singleField,
    parentForm,
    child,
    childForm,
    childRelease
  )
  const actionSlot = formNodeRecord({
    id: 'zdw_e2e_action_slot',
    formId: parentForm.id,
    parentId: section.id,
    nodeType: 'ACTION_SLOT',
    props: { label: 'zdw_e2e_primary_actions' },
    orderKey: 4_000_000
  })

  const nodes = [
    ...preserved,
    section,
    intro,
    grid,
    ...fields,
    collapse,
    singleSubForm,
    actionSlot
  ].map(node => ({
    ...node,
    revision: existingNodeById.get(node.id)?.revision || node.revision || 1
  }))
  await api(
    'POST',
    `/entity-forms/${parentForm.id}/nodes/update`
      + `?expectedRevision=${parentForm.revision}`,
    nodes
  )
  const published = await api(
    'POST',
    `/entity-forms/${parentForm.id}/publish`,
    {
      description: 'ZDWREQ 全字段、全节点与主子表单真实回归版本'
    }
  )
  const refreshed = await formByKey(parent.id, parentFormKey)
  const nodeTypes = new Set(
    (refreshed.nodes || []).map(node => String(node.nodeType).toUpperCase())
  )
  expectedFormNodeTypes.forEach(nodeType => {
    assert.ok(nodeTypes.has(nodeType), `表单缺少节点类型 ${nodeType}`)
  })
  const coveredFieldTypes = new Set()
  ;(refreshed.nodes || [])
    .filter(node => ['FIELD', 'SUB_FORM', 'REPEATER'].includes(
      String(node.nodeType).toUpperCase()
    ))
    .forEach(node => {
      const props = parseObject(node.propsDocument)
      const entityField = fieldByCode.get(
        node.bindingType === 'RELATION'
          ? props.fieldCode
          : node.bindingRef
      )
      const fieldType = entityField?.fieldType || props.fieldType
      if (fieldType) coveredFieldTypes.add(String(fieldType).toUpperCase())
    })
  const expectedFieldTypes = [
    'STRING',
    'TEXT',
    'RICH_TEXT',
    'INTEGER',
    'DECIMAL',
    'DATE',
    'DATETIME',
    'BOOLEAN',
    'SELECT',
    'MULTI_SELECT',
    'RADIO',
    'CHECKBOX',
    'FILE',
    'IMAGE',
    'USER',
    'DEPT',
    'REFERENCE',
    'MULTI_REFERENCE',
    'SUB_FORM',
    'SUB_LIST'
  ]
  expectedFieldTypes.forEach(fieldType => {
    assert.ok(
      coveredFieldTypes.has(fieldType),
      `已发布表单缺少字段类型 ${fieldType}`
    )
  })
  const richTextNode = (refreshed.nodes || []).find(
    node => node.nodeKey === 'req_desc'
  )
  assert.equal(
    parseObject(richTextNode?.propsDocument).componentType,
    'rich_text',
    'RICH_TEXT 字段必须使用富文本组件'
  )
  record('configureParentFormMatrix', {
    formId: parentForm.id,
    formKey: parentForm.formKey,
    publishedReleaseId: published?.id || refreshed.activeReleaseId,
    nodeCount: refreshed.nodes?.length || 0,
    coveredNodeTypes: [...nodeTypes].sort(),
    coveredFieldTypes: [...coveredFieldTypes].sort(),
    childForm: {
      formId: childForm.id,
      releaseId: childRelease.id,
      releaseVersion: Number(childRelease.version)
    },
    modeAccessCases: {
      readonlyApproval: 'contactEmail',
      editableApproval: 'expectedDate',
      hiddenApproval: 'plainNote',
      readonlyAllModes: 'ownerDept',
      hiddenAllModes: 'status'
    }
  })
  return refreshed
}

function jsonConfig(value) {
  return value && Object.keys(value).length
    ? JSON.stringify(value)
    : null
}

function listFieldConfig(list, entityField, current, index, config = {}) {
  return {
    ...(current?.id ? { id: current.id } : {}),
    ...(current?.revision ? { revision: current.revision } : {}),
    listConfigId: list.id,
    fieldId: entityField.id,
    fieldCode: entityField.fieldCode,
    fieldName: config.fieldName || entityField.fieldName,
    sortOrder: index,
    orderKey: (index + 1) * 1_000_000,
    width: config.width ?? 0,
    showInList: config.showInList !== false,
    isQuery: config.isQuery === true,
    queryType: config.queryType || 'EQ',
    align: config.align || 'left',
    dataSourceType: 'ENTITY_FIELD',
    dataSourceConfig: jsonConfig(config.dataSourceConfig || {}),
    dataSourceId: null,
    renderComponent: config.renderComponent || '',
    formatter: config.formatter || '',
    columnConfig: jsonConfig(config.columnConfig || {}),
    queryConfig: jsonConfig(config.queryConfig || {}),
    renderConfig: jsonConfig(config.renderConfig || {}),
    templateId: null,
    templateVersion: null,
    localOverridesDocument: null,
    deleted: 0
  }
}

function listFieldDefinitions(entityCode) {
  if (entityCode === 'ZDWREQ') {
    return [
      {
        code: 'name',
        fieldName: '需求名称',
        width: 220,
        isQuery: true,
        queryType: 'LIKE',
        columnConfig: {
          fixed: 'left',
          showOverflowTooltip: true
        },
        queryConfig: {
          componentType: 'input',
          placeholder: '输入需求名称'
        }
      },
      {
        code: 'code',
        fieldName: '需求编码',
        width: 190,
        isQuery: true,
        queryType: 'EQ',
        queryConfig: {
          componentType: 'input',
          placeholder: '精确输入需求编码'
        }
      },
      {
        code: 'status',
        fieldName: '流程状态',
        width: 120,
        align: 'center',
        isQuery: true,
        queryType: 'IN',
        renderComponent: 'StatusBadge',
        renderConfig: {
          size: 'small',
          labelMap: {
            DRAFT: '草稿',
            RUNNING: '审批中',
            COMPLETED: '已完成',
            REJECTED: '已驳回'
          },
          statusMap: {
            draft: 'info',
            running: 'warning',
            completed: 'success',
            rejected: 'danger'
          }
        },
        queryConfig: {
          componentType: 'select_multiple',
          placeholder: '选择流程状态'
        }
      },
      {
        code: 'contactEmail',
        fieldName: '联系邮箱',
        width: 220,
        isQuery: true,
        queryType: 'LIKE',
        queryConfig: {
          componentType: 'input',
          placeholder: '输入邮箱关键字'
        }
      },
      {
        code: 'estimatedCount',
        fieldName: '预估数量',
        width: 110,
        align: 'right',
        isQuery: true,
        queryType: 'BETWEEN',
        formatter: '0'
      },
      {
        code: 'budgetAmount',
        fieldName: '预算金额',
        width: 130,
        align: 'right',
        isQuery: true,
        queryType: 'GE',
        formatter: '2',
        queryConfig: {
          componentType: 'number',
          placeholder: '最低预算'
        }
      },
      {
        code: 'expectedDate',
        fieldName: '期望日期',
        width: 130,
        align: 'center',
        isQuery: true,
        queryType: 'BETWEEN',
        renderComponent: 'DateFormatter',
        renderConfig: { pattern: 'yyyy-MM-dd' }
      },
      {
        code: 'reviewAt',
        fieldName: '评审时间',
        width: 170,
        align: 'center',
        isQuery: true,
        queryType: 'BETWEEN',
        renderComponent: 'DateFormatter',
        renderConfig: { pattern: 'yyyy-MM-dd HH:mm' }
      },
      {
        code: 'enabledFlag',
        fieldName: '启用状态',
        width: 100,
        align: 'center',
        isQuery: true,
        queryType: 'EQ',
        renderComponent: 'DefaultText',
        renderConfig: {
          labelMap: {
            true: '启用',
            false: '停用'
          }
        },
        queryConfig: {
          componentType: 'switch'
        }
      },
      {
        code: 'singleOption',
        fieldName: '单选分类',
        width: 120,
        align: 'center',
        isQuery: true,
        queryType: 'IN',
        queryConfig: {
          componentType: 'select_multiple',
          placeholder: '选择分类'
        }
      },
      {
        code: 'multiOptions',
        fieldName: '多选分类',
        width: 180,
        isQuery: true,
        queryType: 'IN',
        queryConfig: {
          componentType: 'select_multiple',
          placeholder: '选择一个或多个分类'
        }
      },
      {
        code: 'ownerUser',
        fieldName: '负责人',
        width: 150,
        isQuery: true,
        queryType: 'EQ',
        queryConfig: {
          componentType: 'reference',
          placeholder: '选择负责人'
        }
      },
      {
        code: 'ownerDept',
        fieldName: '负责部门',
        width: 160,
        isQuery: true,
        queryType: 'EQ',
        queryConfig: {
          componentType: 'reference',
          placeholder: '选择负责部门'
        }
      },
      {
        code: 'primaryItem',
        fieldName: '主需求条目',
        width: 180,
        isQuery: true,
        queryType: 'EQ',
        queryConfig: {
          componentType: 'reference',
          placeholder: '选择主需求条目'
        }
      },
      {
        code: 'relatedItems',
        fieldName: '相关需求条目',
        width: 220,
        isQuery: true,
        queryType: 'IN',
        queryConfig: {
          componentType: 'multi_reference',
          placeholder: '选择相关需求条目'
        }
      },
      {
        code: 'submitterName',
        fieldName: '提交人',
        width: 150,
        isQuery: false
      }
    ]
  }
  return [
    {
      code: 'name',
      fieldName: '条目名称',
      width: 220,
      isQuery: true,
      queryType: 'LIKE',
      columnConfig: {
        fixed: 'left',
        showOverflowTooltip: true
      },
      queryConfig: {
        componentType: 'input',
        placeholder: '输入条目名称'
      }
    },
    {
      code: 'code',
      fieldName: '条目编码',
      width: 180,
      isQuery: true,
      queryType: 'EQ'
    },
    {
      code: 'status',
      fieldName: '状态',
      width: 110,
      align: 'center',
      isQuery: true,
      queryType: 'IN',
      renderComponent: 'StatusBadge',
      renderConfig: {
        size: 'small',
        labelMap: {
          DRAFT: '草稿',
          ACTIVE: '有效',
          COMPLETED: '完成'
        }
      }
    },
    {
      code: 'reqId',
      fieldName: '所属需求（一对多）',
      width: 220,
      isQuery: true,
      queryType: 'EQ',
      queryConfig: {
        componentType: 'reference',
        placeholder: '选择所属需求'
      }
    },
    {
      code: 'reqSingleId',
      fieldName: '所属需求（一对一）',
      width: 220,
      isQuery: true,
      queryType: 'EQ',
      queryConfig: {
        componentType: 'reference',
        placeholder: '选择所属需求'
      }
    },
    {
      code: 'deptId',
      fieldName: '所属部门',
      width: 160,
      isQuery: true,
      queryType: 'EQ',
      queryConfig: {
        componentType: 'reference',
        placeholder: '选择部门'
      }
    }
  ]
}

async function configureEntityList(entity) {
  const list = await listByKey(entity.id)
  const entityDetail = await entityByCode(entity.entityCode)
  const fieldByCode = new Map(
    (entityDetail.fields || []).map(field => [field.fieldCode, field])
  )
  const currentByCode = new Map(
    (list.fields || []).map(field => [field.fieldCode, field])
  )
  const definitions = listFieldDefinitions(entity.entityCode)
  definitions.forEach(definition => {
    assert.ok(
      fieldByCode.has(definition.code),
      `${entity.entityCode} 列表缺少字段 ${definition.code}`
    )
  })
  const fields = definitions.map((definition, index) =>
    listFieldConfig(
      list,
      fieldByCode.get(definition.code),
      currentByCode.get(definition.code),
      index,
      definition
    )
  )
  const parentList = entity.entityCode === 'ZDWREQ'
  const payload = {
    id: list.id,
    entityId: list.entityId,
    entityCode: list.entityCode,
    listKey: list.listKey,
    listName: parentList ? '需求全属性测试列表' : '需求条目引用测试列表',
    description: parentList
      ? '覆盖列、查询、排序、分页、格式化、选择返回和流程行操作'
      : '覆盖父需求引用名称回显、单选返回和子表上下文',
    isDefault: true,
    customComponent: null,
    toolbarConfig: list.toolbarConfig || [],
    rowActionConfig: list.rowActionConfig || [],
    viewConfig: {
      search: {
        defaultVisibleCount: parentList ? 5 : 4,
        collapsible: true,
        labelWidth: parentList ? 110 : 120
      },
      table: {
        stripe: true,
        border: true,
        showIndex: true,
        size: 'small',
        defaultSortField: parentList ? 'reviewAt' : 'name',
        defaultSortDirection: parentList ? 'DESC' : 'ASC'
      },
      pagination: {
        pageSize: parentList ? 20 : 10,
        pageSizes: [10, 20, 50, 100]
      },
      customComponentProps: {
        density: 'compact',
        testMarker: `${entity.entityCode}_LIST_E2E`
      }
    },
    dataScopeMode: 'INHERIT',
    accessPermissionCode: null,
    allowedScenes: [
      'MENU',
      'PAGE',
      'DIALOG',
      'DRAWER',
      'EMBEDDED',
      'FORM_PICKER',
      'SUB_TABLE'
    ],
    selectionConfig: parentList
      ? {
          selectionMode: 'MULTIPLE',
          valueField: 'id',
          returnMappings: [
            { sourceField: 'id', targetField: 'requirementId' },
            { sourceField: 'name', targetField: 'requirementName' },
            { sourceField: 'code', targetField: 'requirementCode' },
            { sourceField: 'contactEmail', targetField: 'contactEmail' }
          ]
        }
      : {
          selectionMode: 'SINGLE',
          valueField: 'id',
          returnMappings: [
            { sourceField: 'id', targetField: 'itemId' },
            { sourceField: 'name', targetField: 'itemName' },
            { sourceField: 'code', targetField: 'itemCode' },
            { sourceField: 'reqId', targetField: 'requirementId' }
          ]
        },
    fixedFilterConfig: {},
    contextBindingConfig: parentList
      ? {}
      : {
          parentField: 'reqId',
          relationCode: 'ZDWREQ_reqItemForm'
        },
    queryProviderCode: null,
    queryDataSourceId: null,
    expectedRevision: list.revision,
    fields
  }
  const saved = await api(
    'POST',
    '/entity-list-config/save',
    payload
  )
  const published = await api(
    'POST',
    `/entity-list-config/${list.id}/publish`,
    {
      description: `${entity.entityCode} 列表全属性真实回归版本`
    }
  )
  const schema = await api(
    'GET',
    `/entity-lists/${entity.entityCode}/${list.listKey}/schema?scene=PAGE`
  )
  assert.equal(
    schema.fields.length,
    definitions.length,
    `${entity.entityCode} 运行列表字段数量不一致`
  )
  assert.equal(
    schema.viewConfig?.table?.border,
    true,
    `${entity.entityCode} 表格边框配置未发布`
  )
  assert.equal(
    schema.selectionConfig?.selectionMode,
    parentList ? 'MULTIPLE' : 'SINGLE',
    `${entity.entityCode} 选择模式未发布`
  )
  const page = await api(
    'POST',
    `/entity-lists/${entity.entityCode}/${list.listKey}/query`,
    {
      pageNum: 1,
      pageSize: 500,
      scene: 'PAGE',
      filters: {}
    }
  )
  assert.ok(Number(page.pageSize || page.size || 0) <= 200)
  const illegalFilter = await apiExpectError(
    'POST',
    `/entity-lists/${entity.entityCode}/${list.listKey}/query`,
    {
      pageNum: 1,
      pageSize: 10,
      scene: 'PAGE',
      filters: {
        notConfiguredField: 'forbidden'
      }
    }
  )
  assert.equal(illegalFilter.status, 400)
  record('configureEntityList', {
    entityCode: entity.entityCode,
    listId: list.id,
    listKey: list.listKey,
    publishedReleaseId: published?.id || saved.activeReleaseId,
    publishedVersion: schema.publishedVersion,
    fieldCount: schema.fields.length,
    queryFields: schema.fields
      .filter(field => field.isQuery)
      .map(field => ({
        fieldCode: field.fieldCode,
        queryType: field.queryType
      })),
    viewConfig: schema.viewConfig,
    selectionConfig: schema.selectionConfig,
    allowedScenes: schema.allowedScenes,
    contextBindingConfig: schema.contextBindingConfig,
    pageSizeCap: page.pageSize || page.size,
    illegalFilter
  })
  return { list: saved, schema, page }
}

async function configureFieldMatrix(parent, child, dict) {
  await ensureField(child, {
    fieldCode: 'reqSingleId',
    fieldName: '单条需求引用',
    fieldType: 'REFERENCE',
    isRequired: false,
    isUnique: true,
    refEntityId: parent.id,
    refEntityType: 'CUSTOM',
    sortOrder: 100
  })
  await publishEntity(child, 'ZDWITEM 增加一对一子表外键')

  const fields = [
    {
      fieldCode: 'contactEmail',
      fieldName: '联系邮箱',
      fieldType: 'STRING',
      fieldLength: 120,
      isRequired: true,
      isUnique: true,
      validateRules: '{"minLength":6,"maxLength":120,"format":"EMAIL"}',
      sortOrder: 110
    },
    {
      fieldCode: 'plainNote',
      fieldName: '纯文本说明',
      fieldType: 'TEXT',
      validateRules: '{"minLength":2,"maxLength":2000}',
      sortOrder: 120
    },
    {
      fieldCode: 'estimatedCount',
      fieldName: '预估数量',
      fieldType: 'INTEGER',
      defaultValue: '1',
      validateRules: '{"min":0,"max":9999}',
      sortOrder: 130
    },
    {
      fieldCode: 'budgetAmount',
      fieldName: '预算金额',
      fieldType: 'DECIMAL',
      fieldLength: 18,
      fieldPrecision: 2,
      defaultValue: '0',
      validateRules: '{"min":0,"max":99999999.99}',
      sortOrder: 140
    },
    {
      fieldCode: 'expectedDate',
      fieldName: '期望日期',
      fieldType: 'DATE',
      sortOrder: 150
    },
    {
      fieldCode: 'reviewAt',
      fieldName: '评审时间',
      fieldType: 'DATETIME',
      sortOrder: 160
    },
    {
      fieldCode: 'enabledFlag',
      fieldName: '是否启用',
      fieldType: 'BOOLEAN',
      defaultValue: 'true',
      sortOrder: 170
    },
    {
      fieldCode: 'singleOption',
      fieldName: '下拉单选',
      fieldType: 'SELECT',
      dictType: dict.dictCode,
      defaultValue: 'A',
      sortOrder: 180
    },
    {
      fieldCode: 'multiOptions',
      fieldName: '下拉多选',
      fieldType: 'MULTI_SELECT',
      dictType: dict.dictCode,
      valueStorage: 'MULTI_TABLE',
      sortOrder: 190
    },
    {
      fieldCode: 'radioOption',
      fieldName: '平铺单选',
      fieldType: 'RADIO',
      dictType: dict.dictCode,
      defaultValue: 'B',
      sortOrder: 200
    },
    {
      fieldCode: 'checkboxOptions',
      fieldName: '平铺多选',
      fieldType: 'CHECKBOX',
      dictType: dict.dictCode,
      valueStorage: 'MULTI_TABLE',
      sortOrder: 210
    },
    {
      fieldCode: 'testFiles',
      fieldName: '测试附件',
      fieldType: 'FILE',
      fileTypes: '.pdf,.doc,.docx,.txt',
      fileMaxSize: 20,
      fileMaxCount: 5,
      fileItems: [
        {
          itemName: '需求文档',
          fileTypes: '.pdf,.doc,.docx',
          maxSize: 20,
          maxCount: 3,
          sortOrder: 10
        },
        {
          itemName: '补充材料',
          fileTypes: '.txt,.pdf',
          maxSize: 10,
          maxCount: 2,
          sortOrder: 20
        }
      ],
      sortOrder: 220
    },
    {
      fieldCode: 'testImage',
      fieldName: '测试图片',
      fieldType: 'IMAGE',
      fileTypes: '.jpg,.jpeg,.png',
      fileMaxSize: 10,
      fileMaxCount: 3,
      fileItems: [
        {
          itemName: '效果图',
          fileTypes: '.jpg,.jpeg,.png',
          maxSize: 10,
          maxCount: 3,
          sortOrder: 10
        }
      ],
      sortOrder: 230
    },
    {
      fieldCode: 'ownerUser',
      fieldName: '负责人',
      fieldType: 'USER',
      refEntityType: 'USER',
      sortOrder: 240
    },
    {
      fieldCode: 'ownerDept',
      fieldName: '负责部门',
      fieldType: 'DEPT',
      refEntityType: 'DEPT',
      sortOrder: 250
    },
    {
      fieldCode: 'primaryItem',
      fieldName: '主需求条目',
      fieldType: 'REFERENCE',
      refEntityId: child.id,
      refEntityType: 'CUSTOM',
      sortOrder: 260
    },
    {
      fieldCode: 'relatedItems',
      fieldName: '相关需求条目',
      fieldType: 'MULTI_REFERENCE',
      refEntityId: child.id,
      refEntityType: 'CUSTOM',
      valueStorage: 'MULTI_TABLE',
      sortOrder: 270
    },
    {
      fieldCode: 'reqSingleForm',
      fieldName: '单条需求条目',
      fieldType: 'SUB_FORM',
      childEntityId: child.id,
      refEntityId: child.id,
      refEntityType: 'CUSTOM',
      childRefFieldCode: 'reqSingleId',
      refFieldCode: 'reqSingleId',
      relationCode: 'ZDWREQ_reqSingleForm',
      relationName: '单条需求条目',
      relationType: 'ONE_TO_ONE',
      cascadeDelete: true,
      relationRequired: false,
      sortOrder: 280
    }
  ]

  for (const field of fields) {
    await ensureField(parent, field)
  }
  await publishEntity(parent, 'ZDWREQ 补齐全字段类型与字段属性测试矩阵')
}

async function main() {
  if (existsSync(tokenFile)) {
    token = readFileSync(tokenFile, 'utf8').trim()
    unlinkSync(tokenFile)
    assert.ok(token, 'TEST_TOKEN_FILE is empty')
    record('login', { source: 'ephemeral-token-file' })
  } else {
    let loginUsername = username
    let loginPassword = password
    if (existsSync(credentialFile)) {
      const credentials = JSON.parse(readFileSync(credentialFile, 'utf8'))
      unlinkSync(credentialFile)
      loginUsername = credentials.username
      loginPassword = credentials.password
    }
    const login = await api('POST', '/auth/login', {
      username: loginUsername,
      password: loginPassword
    })
    token = login.token
    record('login', { id: login.id, username: login.username })
  }

  const parent = await entityByCode('ZDWREQ')
  const child = await entityByCode('ZDWITEM')
  assert.ok(parent?.id, 'ZDWREQ 不存在')
  assert.ok(child?.id, 'ZDWITEM 不存在')
  evidence.entities.parent = {
    id: parent.id,
    entityCode: parent.entityCode,
    entityName: parent.entityName
  }
  evidence.entities.child = {
    id: child.id,
    entityCode: child.entityCode,
    entityName: child.entityName
  }

  const dict = await ensureDictionary()
  await configureFieldMatrix(parent, child, dict)
  await configureParentForm(parent, child)
  const parentListRuntime = await configureEntityList(parent)
  const childListRuntime = await configureEntityList(child)

  const parentAfter = await entityByCode('ZDWREQ')
  const childAfter = await entityByCode('ZDWITEM')
  assertFieldTypes(parentAfter.fields || [])
  const singleRelation = (parentAfter.fields || []).find(
    field => field.fieldCode === 'reqSingleForm'
  )
  assert.equal(singleRelation?.relationType, 'ONE_TO_ONE')
  assert.equal(singleRelation?.childRefFieldCode, 'reqSingleId')
  const listRelation = (parentAfter.fields || []).find(
    field => field.fieldCode === 'reqItemForm'
  )
  assert.equal(listRelation?.relationType, 'ONE_TO_MANY')
  assert.equal(listRelation?.childRefFieldCode, 'reqId')

  const childRows =
    childListRuntime.page?.records
    || childListRuntime.page?.list
    || childListRuntime.page?.rows
    || []
  const candidateRequirementIds = [...new Set(
    childRows
      .map(row => (
        row.data?.reqId
        ?? row.extData?.reqId
        ?? row.reqId
      ))
      .filter(Boolean)
      .map(String)
  )]
  let sampleRequirementId = null
  let resolvedRequirement = []
  if (candidateRequirementIds.length) {
    resolvedRequirement = await api(
      'GET',
      `/entity-selector/CUSTOM/batch?ids=${encodeURIComponent(candidateRequirementIds.join(','))}`
        + `&refEntityId=${encodeURIComponent(parent.id)}`
    )
    assert.ok(
      resolvedRequirement.length > 0,
      '子实体列表中的有效父需求引用无法批量解析'
    )
    sampleRequirementId = String(resolvedRequirement[0].id)
    assert.ok(
      candidateRequirementIds.includes(sampleRequirementId),
      '批量解析返回了不属于当前子实体列表的父需求'
    )
    assert.ok(
      resolvedRequirement?.[0]?.name
        || resolvedRequirement?.[0]?.code
        || resolvedRequirement?.[0]?.title,
      '父需求引用批量解析未返回可展示名称'
    )
  }

  record('verifyFieldMatrix', {
    parentFieldCount: parentAfter.fields?.length || 0,
    childFieldCount: childAfter.fields?.length || 0,
    coveredTypes: [...new Set(
      (parentAfter.fields || []).map(field => field.fieldType)
    )].sort(),
    relations: [
      {
        fieldCode: singleRelation.fieldCode,
        relationCode: singleRelation.relationCode,
        relationType: singleRelation.relationType,
        childRefFieldCode: singleRelation.childRefFieldCode
      },
      {
        fieldCode: listRelation.fieldCode,
        relationCode: listRelation.relationCode,
        relationType: listRelation.relationType,
        childRefFieldCode: listRelation.childRefFieldCode
      }
    ],
    referenceFields: (childAfter.fields || [])
      .filter(field => [
        'REFERENCE',
        'MULTI_REFERENCE',
        'USER',
        'DEPT',
        'ROLE',
        'GROUP'
      ].includes(field.fieldType))
      .map(field => ({
        fieldCode: field.fieldCode,
        fieldType: field.fieldType,
        refEntityType: field.refEntityType,
        refEntityId: field.refEntityId
      })),
    optionFields: (parentAfter.fields || [])
      .filter(field => field.dictType || field.optionsJson)
      .map(field => ({
        fieldCode: field.fieldCode,
        fieldType: field.fieldType,
        dictType: field.dictType,
        optionsJson: field.optionsJson
      })),
    parentListFields: (parentListRuntime.schema?.fields || [])
      .filter(field => [
        'singleOption',
        'multiOptions',
        'ownerUser',
        'ownerDept',
        'primaryItem',
        'relatedItems'
      ].includes(field.fieldCode))
      .map(field => ({
        fieldCode: field.fieldCode,
        fieldType: field.fieldType,
        dictType: field.dictType,
        optionsJson: field.optionsJson,
        refEntityType: field.refEntityType,
        refEntityId: field.refEntityId
      })),
    childListFields: (childListRuntime.schema?.fields || [])
      .filter(field => ['reqId', 'reqSingleId', 'deptId'].includes(field.fieldCode))
      .map(field => ({
        fieldCode: field.fieldCode,
        fieldType: field.fieldType,
        refEntityType: field.refEntityType,
        refEntityId: field.refEntityId
      })),
    sampleChildReference: sampleRequirementId
      ? {
          value: String(sampleRequirementId),
          resolved: resolvedRequirement[0]
        }
      : null
  })

  evidence.result = 'PASS'
  const evidencePath = path.join(evidenceDir, 'latest.json')
  writeFileSync(evidencePath, JSON.stringify(evidence, null, 2), {
    mode: 0o600
  })
  console.log(`PASS ${evidencePath}`)
}

main().catch(error => {
  evidence.result = 'FAIL'
  evidence.error = {
    message: error.message,
    status: error.status,
    errorCode: error.errorCode
  }
  const evidencePath = path.join(evidenceDir, 'latest-failed.json')
  writeFileSync(evidencePath, JSON.stringify(evidence, null, 2), {
    mode: 0o600
  })
  console.error(error)
  process.exitCode = 1
})
