import assert from 'node:assert/strict'
import {
  existsSync,
  mkdirSync,
  readFileSync,
  unlinkSync,
  writeFileSync
} from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const apiBase = process.env.WORKFLOW_API_BASE || 'http://127.0.0.1:8080/api'
const username = process.env.TEST_USERNAME?.trim()
const password = process.env.TEST_PASSWORD
const tokenFile = process.env.TEST_TOKEN_FILE
const credentialFile = process.env.TEST_CREDENTIAL_FILE

assert.ok(
  (tokenFile && existsSync(tokenFile))
    || (credentialFile && existsSync(credentialFile))
    || (username && password),
  '必须提供 TEST_TOKEN_FILE、TEST_CREDENTIAL_FILE 或 TEST_USERNAME/TEST_PASSWORD，未提供时不会创建验收配置'
)

const entityCode = 'project_extension_acceptance'
const entityName = '项目扩展验收单'
const activeAcceptanceBatch = 'CURRENT'
const archivedAcceptanceBatch = 'ARCHIVED'
const processKey = 'project_extension_acceptance_process'
const processName = '项目扩展能力验收流程'
const permissionCode =
  `entity:${entityCode}:custom:project-review`

const formKeys = {
  full: 'project_extension_acceptance_full',
  matrix: 'project_extension_acceptance_matrix',
  readonly: 'project_extension_acceptance_readonly'
}

const listKeys = {
  board: 'project_extension_acceptance_board',
  schema: 'project_extension_acceptance_schema',
  matrix: 'project_extension_acceptance_matrix',
  provider: 'project_extension_acceptance_provider',
  unified: 'project_extension_acceptance_unified'
}

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const moduleRoot = path.resolve(scriptDir, '..')
const evidenceDir = path.resolve(
  moduleRoot,
  '../../docs/project-extension-acceptance'
)
mkdirSync(evidenceDir, { recursive: true })

let token = ''
let currentUser = null
let effectiveUsername = username || ''
const evidence = {
  apiBase,
  acceptanceBatch: activeAcceptanceBatch,
  entity: {},
  extensions: [],
  dataSources: [],
  forms: [],
  lists: [],
  eventBindings: [],
  actionHandlers: [],
  personResolver: {},
  versionConfiguration: {},
  process: {},
  fixtures: [],
  fixtureCleanup: []
}

const emptyObjectSchema = { type: 'object' }
const objectOutputSchema = { type: 'object' }
const arrayOutputSchema = {
  type: 'array',
  items: { type: 'object' }
}
const pageOutputSchema = {
  type: 'object',
  properties: {
    records: {
      type: 'array',
      items: { type: 'object' }
    },
    total: { type: 'integer' },
    pageNum: { type: 'integer' },
    pageSize: { type: 'integer' }
  },
  required: ['records', 'total', 'pageNum', 'pageSize']
}

async function api(method, endpoint, body) {
  const response = await fetch(apiBase + endpoint, {
    method,
    signal: AbortSignal.timeout(45000),
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body == null ? undefined : JSON.stringify(body)
  })
  const text = await response.text()
  let payload
  try {
    payload = text ? JSON.parse(text) : null
  } catch {
    throw new Error(
      `${method} ${endpoint} returned non-json: HTTP ${response.status}`
    )
  }
  const success =
    payload?.code === 200
    || payload?.code === 0
    || payload?.success === true
    || (payload?.code == null && response.ok)
  if (!response.ok || !success) {
    const message = payload?.message || payload?.msg || 'request failed'
    throw new Error(
      `${method} ${endpoint} failed: HTTP ${response.status}, ${message}`
    )
  }
  return payload?.data ?? payload
}

async function optionalApi(method, endpoint, body) {
  try {
    return await api(method, endpoint, body)
  } catch (error) {
    if (/404|不存在|not found/i.test(error.message)) return null
    throw error
  }
}

function rows(value) {
  if (Array.isArray(value)) return value
  return value?.records || value?.list || value?.rows || []
}

function json(value) {
  return value == null ? null : JSON.stringify(value)
}

function parseDocument(value, label, fallback = {}) {
  if (value == null || value === '') return fallback
  if (typeof value === 'object') return value
  try {
    return JSON.parse(value)
  } catch {
    throw new Error(`${label} 不是合法 JSON`)
  }
}

function today(offsetDays = 0) {
  const value = new Date()
  value.setDate(value.getDate() + offsetDays)
  return [
    value.getFullYear(),
    String(value.getMonth() + 1).padStart(2, '0'),
    String(value.getDate()).padStart(2, '0')
  ].join('-')
}

function operation(
  code,
  name,
  kind,
  contextType,
  outputSchema = objectOutputSchema,
  config = {}
) {
  return {
    code,
    name,
    kind,
    contextType,
    config,
    inputSchema: emptyObjectSchema,
    outputSchema
  }
}

function entityField(fieldCode, fieldName, fieldType, overrides = {}) {
  return {
    fieldCode,
    fieldName,
    fieldType,
    fieldLength: ['STRING', 'SELECT', 'RADIO'].includes(fieldType)
      ? 255
      : undefined,
    isRequired: false,
    isUnique: false,
    editable: true,
    sortOrder: 10,
    ...overrides
  }
}

const acceptanceFields = [
  entityField(
    'acceptance_batch',
    '验收批次',
    'STRING',
    {
      defaultValue: activeAcceptanceBatch,
      sortOrder: 5
    }
  ),
  entityField(
    'acceptance_scene',
    '验收场景',
    'SELECT',
    {
      isRequired: true,
      optionsJson: json([
        { label: '全扩展链路', value: 'FULL_EXTENSION' },
        { label: '表单扩展', value: 'FORM_EXTENSION' },
        { label: '列表扩展', value: 'LIST_EXTENSION' },
        { label: '流程扩展', value: 'PROCESS_EXTENSION' }
      ]),
      sortOrder: 10
    }
  ),
  entityField(
    'owner_name',
    '验收负责人',
    'STRING',
    { isRequired: true, sortOrder: 20 }
  ),
  entityField(
    'planned_date',
    '计划验收日',
    'DATE',
    { sortOrder: 30 }
  ),
  entityField(
    'acceptance_score',
    '验收评分',
    'INTEGER',
    {
      defaultValue: '65',
      validateRules: json({ min: 0, max: 100 }),
      sortOrder: 40
    }
  ),
  entityField(
    'review_level',
    '复核级别',
    'SELECT',
    { sortOrder: 50 }
  ),
  entityField(
    'description',
    '验收说明',
    'TEXT',
    { sortOrder: 60 }
  ),
  entityField(
    'provider_trace',
    '数据源轨迹',
    'TEXT',
    { sortOrder: 70 }
  ),
  entityField(
    'event_trace',
    '事件轨迹',
    'TEXT',
    { sortOrder: 80 }
  ),
  entityField(
    'extension_summary',
    '节点扩展摘要',
    'TEXT',
    { sortOrder: 90 }
  ),
  entityField(
    'extension_result',
    '扩展执行结果',
    'TEXT',
    { sortOrder: 100 }
  ),
  entityField(
    'backend_trace',
    '后端动作轨迹',
    'TEXT',
    { sortOrder: 110 }
  ),
  entityField(
    'last_action_scope',
    '最近动作范围',
    'STRING',
    { sortOrder: 120 }
  ),
  entityField(
    'last_action_timing',
    '最近动作时机',
    'STRING',
    { sortOrder: 130 }
  ),
  entityField(
    'last_action_element',
    '最近动作元素',
    'STRING',
    { sortOrder: 140 }
  ),
  entityField(
    'review_comment',
    '复核意见',
    'TEXT',
    { sortOrder: 150 }
  ),
  entityField(
    'decision',
    '验收结论',
    'RADIO',
    {
      optionsJson: json([
        { label: '通过', value: 'PASS' },
        { label: '待改进', value: 'IMPROVE' }
      ]),
      sortOrder: 160
    }
  ),
  entityField(
    'connector_trace',
    '连接器轨迹',
    'TEXT',
    { sortOrder: 170 }
  )
]

async function ensureEntity() {
  let entity = await optionalApi('GET', `/entity/code/${entityCode}`)
  if (!entity) {
    entity = await api('POST', '/entity', {
      entityCode,
      entityName,
      description:
        '验收 src/project 前端扩展、workflow-project 后端扩展、统一数据源和流程动作。',
      lifecycleMode: 'WORKFLOW',
      storageMode: 'DYNAMIC',
      fields: acceptanceFields
    })
  } else {
    const current = await api('GET', `/entity/${entity.id}`)
    const existing = new Map(
      (current.fields || []).map(field => [field.fieldCode, field])
    )
    for (const field of acceptanceFields) {
      if (!existing.has(field.fieldCode)) {
        await api(
          'POST',
          `/entity/${entity.id}/fields`,
          field
        )
      }
    }
  }

  entity = await api('GET', `/entity/${entity.id}`)
  if (entity.status !== 'PUBLISHED'
      || acceptanceFields.some(field =>
        !entity.fields?.some(current =>
          current.fieldCode === field.fieldCode
          && current.isPublished !== false
        ))) {
    entity = await api('POST', `/entity/${entity.id}/publish`, {
      versionDescription: '项目扩展验收实体发布'
    })
  }
  entity = await api('GET', `/entity/${entity.id}`)
  await api('POST', `/entity/${entity.id}/lifecycle-mode`, {
    lifecycleMode: 'WORKFLOW'
  })
  await api('POST', `/entity-status/save-list/${entityCode}`, [
    {
      statusCode: 'DRAFT',
      statusName: '草稿',
      statusCategory: 'NEW',
      sortOrder: 10,
      color: '#909399'
    },
    {
      statusCode: 'IN_REVIEW',
      statusName: '审批中',
      statusCategory: 'PROCESSING',
      sortOrder: 20,
      color: '#409eff'
    },
    {
      statusCode: 'APPROVED',
      statusName: '已通过',
      statusCategory: 'COMPLETED',
      sortOrder: 30,
      color: '#67c23a'
    },
    {
      statusCode: 'REJECTED',
      statusName: '待改进',
      statusCategory: 'TERMINATED',
      sortOrder: 40,
      color: '#f56c6c'
    }
  ])
  evidence.entity = {
    id: entity.id,
    entityCode: entity.entityCode,
    entityName: entity.entityName,
    status: entity.status,
    fieldCount: entity.fields?.length || 0
  }
  return entity
}

async function ensureUiExtension(definition) {
  const query = new URLSearchParams({
    extensionType: definition.extensionType,
    extensionKey: definition.extensionKey
  })
  const list = await api('GET', `/ui-extensions?${query}`)
  const current = list.find(item =>
    Number(item.version) === Number(definition.version || 1)
  )
  const payload = {
    extensionType: definition.extensionType,
    extensionKey: definition.extensionKey,
    displayName: definition.displayName,
    version: definition.version || 1,
    snapshotVersion: definition.snapshotVersion || 1,
    visibilityScope: definition.visibilityScope || 'GLOBAL',
    entityCodes: definition.entityCodes || [],
    supportedModes: definition.supportedModes || [],
    supportedNodeTypes: definition.supportedNodeTypes || [],
    supportedBindings: definition.supportedBindings || [],
    configSchema: definition.configSchema || [],
    capabilities: definition.capabilities || {},
    status: 'ACTIVE',
    ...(current
      ? { expectedRevision: current.revision }
      : {})
  }
  const saved = current
    ? await api('POST', `/ui-extensions/${current.id}`, payload)
    : await api('POST', '/ui-extensions', payload)
  evidence.extensions.push({
    id: saved.id,
    type: saved.extensionType,
    key: saved.extensionKey,
    version: saved.version,
    status: saved.status
  })
  return saved
}

async function ensureUiExtensions() {
  const definitions = [
    {
      extensionType: 'FORM',
      extensionKey: 'ProjectExtensionAcceptanceForm',
      displayName: '项目扩展验收整表单',
      visibilityScope: 'ENTITY',
      entityCodes: [entityCode],
      supportedModes: ['CREATE', 'EDIT', 'APPROVE', 'VIEW'],
      capabilities: {
        exposesValidate: true,
        executesManagedDataSource: true,
        supportsWorkflowModes: true,
        hotfixCompatible: true
      }
    },
    {
      extensionType: 'LIST',
      extensionKey: 'ProjectAcceptanceBoardList',
      displayName: '项目扩展验收看板列表',
      capabilities: {
        layout: 'acceptance-board',
        reusesPlatformActions: true,
        reusesPlatformPagination: true
      }
    },
    {
      extensionType: 'LIST',
      extensionKey: 'PROJECT_CUSTOM_LIST_SCHEMA',
      displayName: '项目后端 Schema 扩展列表',
      capabilities: {
        backendSchemaProvider: true,
        reusesPlatformActions: true,
        reusesPlatformPagination: true
      }
    },
    {
      extensionType: 'FIELD',
      extensionKey: 'project_acceptance_score',
      displayName: '项目验收评分字段',
      supportedBindings: ['ENTITY_FIELD'],
      capabilities: {
        emitsChange: true,
        supportsReadonly: true
      }
    },
    {
      extensionType: 'FIELD',
      extensionKey: 'project_acceptance_level',
      displayName: '项目复核级别字段',
      supportedBindings: ['ENTITY_FIELD'],
      capabilities: {
        executesManagedDataSource: true,
        supportsReadonly: true
      }
    },
    {
      extensionType: 'NODE',
      extensionKey: 'ProjectAcceptanceSummaryNode',
      displayName: '项目验收摘要节点',
      supportedNodeTypes: ['FIELD'],
      supportedBindings: ['ENTITY_FIELD'],
      capabilities: {
        readsWholeForm: true,
        executesManagedDataSource: true
      }
    }
  ]
  for (const definition of definitions) {
    await ensureUiExtension(definition)
  }
}

function fieldMap(entity) {
  return new Map(
    (entity.fields || []).map(field => [field.fieldCode, field])
  )
}

function formField(fields, fieldCode, overrides = {}) {
  const field = fields.get(fieldCode)
  assert.ok(field?.id, `实体字段 ${fieldCode} 不存在`)
  const componentByType = {
    INTEGER: 'number',
    DATE: 'date',
    TEXT: 'textarea',
    SELECT: 'select',
    RADIO: 'radio'
  }
  return {
    fieldId: field.id,
    fieldCode,
    fieldName: field.fieldName,
    fieldLabel: field.fieldName,
    fieldType: field.fieldType,
    componentType:
      componentByType[field.fieldType] || 'input',
    isRequired: field.isRequired ? 1 : 0,
    isReadonly: 0,
    isHidden: 0,
    gridSpan: ['TEXT'].includes(field.fieldType) ? 24 : 12,
    ...overrides
  }
}

async function ensureForm(entity, formKey, payload) {
  const forms = await api('GET', `/entity-form/entity/${entity.id}`)
  const current = forms.find(item => item.formKey === formKey)
  let saved
  if (!current) {
    saved = await api('POST', '/entity-form', {
      entityId: entity.id,
      formKey,
      formName: payload.formName,
      description: payload.description,
      layoutType: payload.layoutType || 'grid',
      isDefault: payload.isDefault === true,
      status: 1,
      customComponent: payload.customComponent || null,
      customComponentVersion: payload.customComponentVersion || null,
      customComponentSnapshotVersion:
        payload.customComponentSnapshotVersion || null,
      initConfig: payload.initConfig || null,
      dataSourceBindingsDocument:
        payload.dataSourceBindingsDocument || null,
      viewConfig: payload.viewConfig || null,
      fields: payload.fields || []
    })
  } else {
    const detail = await api('GET', `/entity-form/${current.id}`)
    saved = await api('POST', `/entity-form/${current.id}/update`, {
      expectedRevision: detail.revision,
      entityId: entity.id,
      formKey,
      formName: payload.formName,
      description: payload.description,
      layoutType: payload.layoutType || 'grid',
      isDefault: payload.isDefault === true,
      status: 1,
      customComponent: payload.customComponent || null,
      customComponentVersion: payload.customComponentVersion || null,
      customComponentSnapshotVersion:
        payload.customComponentSnapshotVersion || null,
      initConfig: payload.initConfig || null,
      dataSourceBindingsDocument:
        payload.dataSourceBindingsDocument || null,
      viewConfig: payload.viewConfig || null,
      fields: payload.fields || []
    })
  }
  return api('GET', `/entity-form/${saved.id}`)
}

async function ensureDataSource(definition) {
  const list = await api('GET', '/ui-data-sources')
  const current = list.find(item =>
    item.sourceCode === definition.sourceCode
  )
  const payload = {
    sourceCode: definition.sourceCode,
    sourceName: definition.sourceName,
    sourceType: definition.sourceType,
    providerCode: definition.providerCode,
    scopeType: definition.scopeType,
    scopeId: definition.scopeId,
    config: definition.config || {},
    executionPolicy: {
      timeoutMs: 5000,
      cacheSeconds: 0,
      failurePolicy: 'FAIL',
      ...(definition.executionPolicy || {})
    },
    operations: definition.operations,
    enabled: true,
    ...(current
      ? { expectedRevision: current.revision }
      : {})
  }
  const saved = current
    ? await api(
        'POST',
        `/ui-data-sources/${current.id}/update`,
        payload
      )
    : await api('POST', '/ui-data-sources', payload)
  evidence.dataSources.push({
    id: saved.id,
    code: saved.sourceCode,
    type: saved.sourceType,
    providerCode: saved.providerCode,
    scopeType: saved.scopeType,
    scopeId: saved.scopeId,
    revision: saved.revision
  })
  return saved
}

async function updateFormBindings(form, payload) {
  const detail = await api('GET', `/entity-form/${form.id}`)
  return api('POST', `/entity-form/${form.id}/update`, {
    expectedRevision: detail.revision,
    entityId: detail.entityId,
    formName: detail.formName,
    formKey: detail.formKey,
    description: detail.description,
    layoutType: detail.layoutType,
    isDefault: detail.isDefault,
    status: detail.status,
    customComponent: detail.customComponent,
    customComponentVersion: detail.customComponentVersion,
    customComponentSnapshotVersion:
      detail.customComponentSnapshotVersion,
    initConfig: detail.initConfig,
    dataSourceBindingsDocument:
      json(payload.dataSourceBindings || {}),
    viewConfig: detail.viewConfig,
    fields: payload.fields || detail.fields || []
  })
}

function formNodeRecord({
  id,
  formId,
  parentId = '',
  nodeKey = id,
  nodeType,
  bindingType = 'NONE',
  bindingRef = null,
  componentName = null,
  componentVersion = null,
  snapshotVersion = null,
  props = {},
  rules = {},
  dataSourceBindings = {},
  orderKey = 1_000_000
}) {
  assert.ok(
    id && String(id).length <= 64,
    `表单节点 id 必须为 1-64 个字符: ${id || '-'}`
  )
  assert.ok(
    !parentId || String(parentId).length <= 64,
    `表单节点 parentId 不能超过 64 个字符: ${parentId}`
  )
  assert.ok(
    nodeKey && String(nodeKey).length <= 100,
    `表单节点 nodeKey 必须为 1-100 个字符: ${nodeKey || '-'}`
  )
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
    propsDocument: json(props),
    rulesDocument: json(rules),
    dataSourceBindingsDocument: json(dataSourceBindings),
    legacyPropsDocument: null,
    orderKey,
    revision: 1,
    templateId: null,
    templateVersion: null,
    localOverridesDocument: null
  }
}

function fieldNode(
  form,
  fields,
  fieldCode,
  parentId,
  index,
  overrides = {}
) {
  const field = fields.get(fieldCode)
  assert.ok(field, `表单节点字段 ${fieldCode} 不存在`)
  const fieldComponentName =
    overrides.fieldComponentName || null
  const componentByType = {
    INTEGER: 'number',
    DATE: 'date',
    TEXT: 'textarea',
    SELECT: 'select',
    RADIO: 'radio'
  }
  return formNodeRecord({
    id: `pea_field_${form.id}_${field.id}`,
    formId: form.id,
    parentId,
    nodeKey: fieldCode,
    nodeType: 'FIELD',
    bindingType: 'ENTITY_FIELD',
    bindingRef: fieldCode,
    componentName:
      overrides.componentName || fieldComponentName,
    componentVersion:
      overrides.componentVersion
      || (fieldComponentName ? 1 : null),
    snapshotVersion:
      overrides.snapshotVersion
      || (fieldComponentName ? 1 : null),
    props: {
      fieldId: field.id,
      fieldCode,
      fieldName: field.fieldName,
      label: overrides.label || field.fieldName,
      fieldType: field.fieldType,
      componentType:
        overrides.componentType
        || componentByType[field.fieldType]
        || 'input',
      componentExtensionType:
        fieldComponentName ? 'FIELD' : undefined,
      placeholder: `请填写${field.fieldName}`,
      gridSpan: overrides.gridSpan || 12,
      required: field.isRequired === true,
      readonly: overrides.readonly === true,
      hidden: false,
      componentProps: {
        ...(overrides.componentProps || {}),
        ...(overrides.title
          ? { title: overrides.title }
          : {})
      }
    },
    rules: overrides.rules || {},
    dataSourceBindings:
      overrides.dataSourceBindings || {},
    orderKey: (index + 1) * 1_000_000
  })
}

async function replaceFormNodes(form, nodes) {
  const currentNodes = await api(
    'GET',
    `/entity-forms/${form.id}/nodes`
  )
  const revisionById = new Map(
    currentNodes.map(node => [node.id, node.revision])
  )
  const detail = await api('GET', `/entity-form/${form.id}`)
  await api(
    'POST',
    `/entity-forms/${form.id}/nodes/update`
      + `?expectedRevision=${detail.revision}`,
    nodes.map(node => ({
      ...node,
      revision: revisionById.get(node.id)
        || node.revision
        || 1
    }))
  )
  return api('GET', `/entity-form/${form.id}`)
}

async function ensureEventBinding(definition) {
  const bindings = await api(
    'GET',
    `/ui-event-bindings?ownerType=${definition.ownerType}`
      + `&ownerId=${definition.ownerId}`
  )
  const current = bindings.find(item =>
    item.targetType === definition.targetType
    && String(item.targetKey || '') === String(definition.targetKey || '')
    && item.eventCode === definition.eventCode
  )
  const payload = {
    ownerType: definition.ownerType,
    ownerId: definition.ownerId,
    targetType: definition.targetType,
    targetKey: definition.targetKey || '',
    eventCode: definition.eventCode,
    inheritanceMode: definition.inheritanceMode || 'INHERIT',
    steps: definition.steps,
    enabled: true,
    ...(current
      ? { expectedRevision: current.revision }
      : {})
  }
  const saved = current
    ? await api(
        'POST',
        `/ui-event-bindings/${current.id}/update`,
        payload
      )
    : await api('POST', '/ui-event-bindings', payload)
  evidence.eventBindings.push({
    id: saved.id,
    ownerType: saved.ownerType,
    ownerId: saved.ownerId,
    targetType: saved.targetType,
    targetKey: saved.targetKey,
    eventCode: saved.eventCode,
    revision: saved.revision
  })
  return saved
}

function eventStep(service, operationCode, strategy, order) {
  return {
    stepCode: `${operationCode}_${order}`,
    strategy,
    order,
    failurePolicy: 'STOP',
    serviceId: service.id,
    operationCode,
    inputMapping: {},
    outputMapping: []
  }
}

async function publishForm(form, description) {
  const release = await api(
    'POST',
    `/entity-forms/${form.id}/publish`,
    { description, releaseMode: 'STANDARD' }
  )
  evidence.forms.push({
    id: form.id,
    formKey: form.formKey,
    formName: form.formName,
    customComponent: form.customComponent,
    releaseId: release.id,
    releaseVersion: release.version,
    route: `/entity-form-design/${form.id}`
  })
  return release
}

async function configureForms(entity) {
  const fields = fieldMap(entity)
  let fullForm = await ensureForm(
    entity,
    formKeys.full,
    {
      formName: '扩展验收整表单',
      description:
        '全自定义整表单，包含前端初始化器和 FORM 统一数据源。',
      layoutType: 'grid',
      isDefault: true,
      customComponent:
        'ProjectExtensionAcceptanceForm',
      customComponentVersion: 1,
      customComponentSnapshotVersion: 1,
      initConfig: json({
        type: 'custom',
        custom: {
          name: 'projectAcceptanceInitializer',
          params: {
            scene: 'FULL_EXTENSION',
            defaultScore: 72
          }
        }
      }),
      viewConfig: json({
        labelWidth: 110,
        customComponentProps: {
          title: '项目扩展验收单',
          showRuntimeTrace: true
        }
      }),
      fields: [
        formField(fields, 'name', {
          isRequired: 1
        }),
        formField(fields, 'acceptance_scene'),
        formField(fields, 'owner_name'),
        formField(fields, 'planned_date'),
        formField(fields, 'acceptance_score', {
          componentType: 'number'
        }),
        formField(fields, 'description', {
          gridSpan: 24
        }),
        formField(fields, 'provider_trace', {
          gridSpan: 24
        }),
        formField(fields, 'extension_result', {
          gridSpan: 24
        })
      ]
    }
  )

  const fullSource = await ensureDataSource({
    sourceCode: 'PROJECT_ACCEPTANCE_FULL_FORM_SOURCE',
    sourceName: '项目扩展验收整表单数据源',
    sourceType: 'REGISTERED_PROVIDER',
    providerCode: 'PROJECT_CUSTOM_UI_FORM',
    scopeType: 'FORM',
    scopeId: fullForm.id,
    config: {
      messagePrefix: '整表单 Provider',
      targetField: 'provider_trace',
      defaultValue: 'FULL_FORM_DEFAULT'
    },
    operations: [
      operation(
        'FORM_INIT',
        '整表单初始化',
        'READ',
        'FORM'
      ),
      operation(
        'AFTER_LOAD',
        '整表单加载后处理',
        'READ',
        'FORM'
      ),
      operation(
        'BEFORE_SUBMIT',
        '整表单提交前处理',
        'WRITE',
        'FORM'
      ),
      operation(
        'FORM_OPEN',
        '整表单打开事件',
        'READ',
        'FORM'
      ),
      operation(
        'FORM_SAVE',
        '整表单保存事件',
        'WRITE',
        'FORM'
      ),
      operation(
        'FORM_BUTTON_CLICK',
        '整表单按钮事件',
        'WRITE',
        'FORM'
      )
    ]
  })
  fullForm = await updateFormBindings(fullForm, {
    fields: fullForm.fields,
    dataSourceBindings: {
      FORM_INIT: {
        serviceId: fullSource.id,
        operationCode: 'FORM_INIT'
      },
      AFTER_LOAD: {
        serviceId: fullSource.id,
        operationCode: 'AFTER_LOAD'
      },
      BEFORE_SUBMIT: {
        serviceId: fullSource.id,
        operationCode: 'BEFORE_SUBMIT'
      }
    }
  })

  let matrixForm = await ensureForm(
    entity,
    formKeys.matrix,
    {
      formName: '扩展验收节点矩阵表单',
      description:
        '标准渲染器、自定义字段、自定义节点和 ENTITY/FORM 数据源。',
      layoutType: 'grid',
      isDefault: false,
      initConfig: json({
        type: 'custom',
        custom: {
          name: 'projectAcceptanceInitializer',
          params: {
            scene: 'FORM_EXTENSION',
            defaultScore: 68
          }
        }
      }),
      viewConfig: json({
        labelWidth: 120,
        labelPosition: 'right',
        actionBar: {
          version: 1,
          builtInOverrides: {
            save: {
              enabled: true,
              labelByMode: {
                create: '保存验收单',
                edit: '保存验收修改'
              },
              enabledModes: ['create', 'edit'],
              buttonType: 'primary',
              sort: 30
            }
          },
          customButtons: [
            {
              key: 'acceptance_form_log',
              label: '记录表单验收日志',
              icon: 'Document',
              buttonType: 'primary',
              sort: 50,
              enabled: true,
              modes: ['create', 'edit', 'view', 'approve'],
              placement: 'FOOTER',
              slotKey: '',
              perm: permissionCode,
              confirm: {
                enabled: false,
                message: ''
              },
              validateBeforeExecute: false
            },
            {
              key: 'acceptance_inline_log',
              label: '执行插槽验收日志',
              icon: 'Link',
              buttonType: 'success',
              sort: 60,
              enabled: true,
              modes: ['create', 'edit', 'view', 'approve'],
              placement: 'ACTION_SLOT',
              slotKey: 'acceptance_inline_actions',
              perm: permissionCode,
              confirm: {
                enabled: true,
                message: '确认执行动作插槽的验收日志链路？'
              },
              validateBeforeExecute: false
            }
          ]
        }
      }),
      fields: [
        formField(fields, 'name'),
        formField(fields, 'acceptance_scene'),
        formField(fields, 'owner_name'),
        formField(fields, 'acceptance_score', {
          componentType: 'project_acceptance_score',
          componentProps: json({ passScore: 60 })
        }),
        formField(fields, 'review_level', {
          componentType: 'project_acceptance_level'
        }),
        formField(fields, 'provider_trace', {
          gridSpan: 24
        }),
        formField(fields, 'event_trace', {
          gridSpan: 24
        }),
        formField(fields, 'extension_summary', {
          gridSpan: 24
        }),
        formField(fields, 'review_comment', {
          gridSpan: 24
        })
      ]
    }
  )

  const entitySource = await ensureDataSource({
    sourceCode: 'PROJECT_ACCEPTANCE_ENTITY_SOURCE',
    sourceName: '项目扩展验收实体统一数据源',
    sourceType: 'REGISTERED_PROVIDER',
    providerCode: 'PROJECT_CUSTOM_UI_ENTITY',
    scopeType: 'ENTITY',
    scopeId: entity.id,
    config: {
      optionLabelPrefix: '验收级别',
      defaultValue: 'A',
      computedPrefix: '节点计算',
      targetField: 'event_trace'
    },
    operations: [
      operation(
        'FIELD_OPTIONS',
        '字段选项',
        'READ',
        'FORM',
        arrayOutputSchema
      ),
      operation(
        'FIELD_DEFAULT',
        '字段默认值',
        'READ',
        'FORM'
      ),
      operation(
        'FIELD_COMPUTE',
        '字段计算',
        'READ',
        'FORM'
      ),
      operation(
        'AFTER_LOAD',
        '字段加载后处理',
        'READ',
        'FORM'
      ),
      operation(
        'FIELD_CHANGE',
        '字段变化事件',
        'READ',
        'FORM'
      ),
      operation(
        'ENTITY_SELECTED',
        '实体选择事件',
        'READ',
        'FORM'
      ),
      operation(
        'FORM_OPEN',
        '实体级表单打开事件',
        'READ',
        'FORM'
      ),
      operation(
        'FIELD_BUTTON_CLICK',
        '字段按钮事件',
        'WRITE',
        'FORM'
      ),
      operation(
        'MUTATION_PREPARE',
        '实体变更准备',
        'WRITE',
        'ENTITY'
      )
    ]
  })

  const matrixFormSource = await ensureDataSource({
    sourceCode: 'PROJECT_ACCEPTANCE_MATRIX_FORM_SOURCE',
    sourceName: '项目扩展验收节点表单数据源',
    sourceType: 'REGISTERED_PROVIDER',
    providerCode: 'PROJECT_CUSTOM_UI_FORM',
    scopeType: 'FORM',
    scopeId: matrixForm.id,
    config: {
      messagePrefix: '节点矩阵表单',
      targetField: 'provider_trace',
      defaultValue: 'MATRIX_FORM_DEFAULT'
    },
    operations: [
      operation(
        'FORM_INIT',
        '节点表单初始化',
        'READ',
        'FORM'
      ),
      operation(
        'AFTER_LOAD',
        '节点表单加载后处理',
        'READ',
        'FORM'
      ),
      operation(
        'BEFORE_SUBMIT',
        '节点表单提交前处理',
        'WRITE',
        'FORM'
      ),
      operation(
        'FORM_OPEN',
        '节点表单打开事件',
        'READ',
        'FORM'
      ),
      operation(
        'FORM_SAVE',
        '节点表单保存事件',
        'WRITE',
        'FORM'
      ),
      operation(
        'FORM_BUTTON_CLICK',
        '节点表单按钮事件',
        'WRITE',
        'FORM'
      )
    ]
  })

  const connectorSource = await ensureDataSource({
    sourceCode: 'PROJECT_ACCEPTANCE_LOG_CONNECTOR',
    sourceName: '项目扩展验收日志连接器',
    sourceType: 'INTEGRATION_CONNECTOR',
    providerCode: 'PROJECT_CUSTOM_LOG_CONNECTOR',
    scopeType: 'ENTITY',
    scopeId: entity.id,
    config: {
      operation: 'PROJECT_ACCEPTANCE_LOG',
      connectorConfigId: 'project-extension-acceptance'
    },
    operations: [
      operation(
        'FORM_CONNECTOR_LOG',
        '表单连接器日志',
        'WRITE',
        'FORM'
      ),
      operation(
        'LIST_CONNECTOR_LOG',
        '列表连接器日志',
        'WRITE',
        'LIST'
      )
    ]
  })

  matrixForm = await updateFormBindings(matrixForm, {
    fields: matrixForm.fields,
    dataSourceBindings: {
      FORM_INIT: {
        serviceId: matrixFormSource.id,
        operationCode: 'FORM_INIT'
      },
      AFTER_LOAD: {
        serviceId: matrixFormSource.id,
        operationCode: 'AFTER_LOAD'
      },
      BEFORE_SUBMIT: {
        serviceId: matrixFormSource.id,
        operationCode: 'BEFORE_SUBMIT'
      }
    }
  })

  const sectionId = 'project_acceptance_matrix_section'
  const gridId = 'project_acceptance_matrix_grid'
  const nodes = [
    formNodeRecord({
      id: sectionId,
      formId: matrixForm.id,
      nodeType: 'SECTION',
      props: {
        label: '前端与统一数据源验收区'
      },
      orderKey: 1_000_000
    }),
    formNodeRecord({
      id: 'project_acceptance_matrix_intro',
      formId: matrixForm.id,
      parentId: sectionId,
      nodeType: 'TEXT',
      props: {
        label: '验收说明',
        text:
          '评分使用 src/project 自定义字段；底部摘要使用自定义节点；选项、默认值、计算和事件分别调用受控统一数据源。',
        textStyle: 'SECTION_TITLE'
      },
      orderKey: 1_000_000
    }),
    formNodeRecord({
      id: 'project_acceptance_matrix_action_slot',
      formId: matrixForm.id,
      parentId: sectionId,
      nodeKey: 'acceptance_inline_actions',
      nodeType: 'ACTION_SLOT',
      props: {
        label: '节点内动作插槽'
      },
      orderKey: 1_500_000
    }),
    formNodeRecord({
      id: gridId,
      formId: matrixForm.id,
      parentId: sectionId,
      nodeType: 'GRID',
      props: {
        label: '验收字段',
        gutter: 16,
        defaultSpan: 12
      },
      orderKey: 2_000_000
    }),
    fieldNode(
      matrixForm,
      fields,
      'name',
      gridId,
      0,
      { gridSpan: 12 }
    ),
    fieldNode(
      matrixForm,
      fields,
      'acceptance_scene',
      gridId,
      1,
      { gridSpan: 12 }
    ),
    fieldNode(
      matrixForm,
      fields,
      'owner_name',
      gridId,
      2,
      { gridSpan: 12 }
    ),
    fieldNode(
      matrixForm,
      fields,
      'acceptance_score',
      gridId,
      3,
      {
        gridSpan: 12,
        fieldComponentName: 'project_acceptance_score',
        componentProps: { passScore: 60 }
      }
    ),
    fieldNode(
      matrixForm,
      fields,
      'review_level',
      gridId,
      4,
      {
        gridSpan: 12,
        fieldComponentName: 'project_acceptance_level',
        dataSourceBindings: {
          FIELD_OPTIONS: {
            serviceId: entitySource.id,
            operationCode: 'FIELD_OPTIONS'
          },
          FIELD_DEFAULT: {
            serviceId: entitySource.id,
            operationCode: 'FIELD_DEFAULT'
          }
        }
      }
    ),
    fieldNode(
      matrixForm,
      fields,
      'provider_trace',
      gridId,
      5,
      {
        gridSpan: 24,
        dataSourceBindings: {
          AFTER_LOAD: {
            serviceId: entitySource.id,
            operationCode: 'AFTER_LOAD'
          }
        }
      }
    ),
    fieldNode(
      matrixForm,
      fields,
      'event_trace',
      gridId,
      6,
      { gridSpan: 24 }
    ),
    fieldNode(
      matrixForm,
      fields,
      'extension_summary',
      sectionId,
      7,
      {
        gridSpan: 24,
        componentName: 'ProjectAcceptanceSummaryNode',
        componentVersion: 1,
        snapshotVersion: 1,
        title: '扩展执行摘要',
        dataSourceBindings: {
          FIELD_COMPUTE: {
            serviceId: entitySource.id,
            operationCode: 'FIELD_COMPUTE'
          }
        }
      }
    ),
    fieldNode(
      matrixForm,
      fields,
      'review_comment',
      sectionId,
      8,
      { gridSpan: 24 }
    )
  ]
  matrixForm = await replaceFormNodes(matrixForm, nodes)

  const readonlyForm = await ensureForm(
    entity,
    formKeys.readonly,
    {
      formName: '扩展验收最终只读表单',
      description:
        '最终节点查看流程动作写回结果、后端轨迹和验收结论。',
      layoutType: 'grid',
      isDefault: false,
      viewConfig: json({
        labelWidth: 130
      }),
      fields: [
        formField(fields, 'name', {
          isReadonly: 1
        }),
        formField(fields, 'acceptance_score', {
          isReadonly: 1
        }),
        formField(fields, 'extension_result', {
          isReadonly: 1,
          gridSpan: 24
        }),
        formField(fields, 'backend_trace', {
          isReadonly: 1,
          gridSpan: 24
        }),
        formField(fields, 'last_action_scope', {
          isReadonly: 1
        }),
        formField(fields, 'last_action_timing', {
          isReadonly: 1
        }),
        formField(fields, 'last_action_element', {
          isReadonly: 1
        }),
        formField(fields, 'decision', {
          isReadonly: 1
        })
      ]
    }
  )

  await ensureEventBinding({
    ownerType: 'ENTITY',
    ownerId: entity.id,
    targetType: 'OWNER',
    eventCode: 'FORM_OPEN',
    steps: [
      eventStep(
        entitySource,
        'FORM_OPEN',
        'AFTER',
        10
      )
    ]
  })
  await ensureEventBinding({
    ownerType: 'FORM',
    ownerId: fullForm.id,
    targetType: 'OWNER',
    eventCode: 'FORM_SAVE',
    steps: [
      eventStep(
        connectorSource,
        'FORM_CONNECTOR_LOG',
        'AFTER',
        10
      )
    ]
  })
  await ensureEventBinding({
    ownerType: 'FORM',
    ownerId: matrixForm.id,
    targetType: 'FIELD',
    targetKey: 'acceptance_score',
    eventCode: 'FIELD_CHANGE',
    steps: [
      eventStep(
        entitySource,
        'FIELD_CHANGE',
        'AFTER',
        10
      )
    ]
  })
  await ensureEventBinding({
    ownerType: 'FORM',
    ownerId: matrixForm.id,
    targetType: 'FIELD',
    targetKey: 'acceptance_score',
    eventCode: 'FIELD_BUTTON_CLICK',
    steps: [
      eventStep(
        entitySource,
        'FIELD_BUTTON_CLICK',
        'BEFORE',
        10
      ),
      eventStep(
        connectorSource,
        'FORM_CONNECTOR_LOG',
        'AFTER',
        20
      )
    ]
  })
  await ensureEventBinding({
    ownerType: 'FORM',
    ownerId: matrixForm.id,
    targetType: 'BUTTON',
    targetKey: 'acceptance_form_log',
    eventCode: 'FORM_BUTTON_CLICK',
    steps: [
      eventStep(
        matrixFormSource,
        'FORM_BUTTON_CLICK',
        'BEFORE',
        10
      ),
      eventStep(
        connectorSource,
        'FORM_CONNECTOR_LOG',
        'AFTER',
        20
      )
    ]
  })
  await ensureEventBinding({
    ownerType: 'FORM',
    ownerId: matrixForm.id,
    targetType: 'BUTTON',
    targetKey: 'acceptance_inline_log',
    eventCode: 'FORM_BUTTON_CLICK',
    steps: [
      eventStep(
        matrixFormSource,
        'FORM_BUTTON_CLICK',
        'BEFORE',
        10
      ),
      eventStep(
        connectorSource,
        'FORM_CONNECTOR_LOG',
        'AFTER',
        20
      )
    ]
  })

  await publishForm(
    fullForm,
    '项目扩展验收整表单发布'
  )
  await publishForm(
    matrixForm,
    '项目扩展验收节点矩阵表单发布'
  )
  await publishForm(
    readonlyForm,
    '项目扩展验收最终只读表单发布'
  )

  return {
    fullForm: await api(
      'GET',
      `/entity-form/${fullForm.id}`
    ),
    matrixForm: await api(
      'GET',
      `/entity-form/${matrixForm.id}`
    ),
    readonlyForm: await api(
      'GET',
      `/entity-form/${readonlyForm.id}`
    ),
    entitySource,
    connectorSource
  }
}

function listField(fields, fieldCode, index, overrides = {}) {
  const field = fields.get(fieldCode)
  assert.ok(field?.id, `列表字段 ${fieldCode} 不存在`)
  return {
    fieldId: field.id,
    fieldCode,
    fieldName: field.fieldName,
    sortOrder: index,
    orderKey: (index + 1) * 1_000_000,
    width: overrides.width || 0,
    showInList: overrides.showInList !== false,
    isQuery: overrides.isQuery === true,
    queryType: overrides.queryType || 'EQ',
    align: overrides.align || 'left',
    dataSourceType: overrides.dataSourceType || 'ENTITY_FIELD',
    dataSourceConfig:
      overrides.dataSourceConfig
        ? json(overrides.dataSourceConfig)
        : null,
    dataSourceId: overrides.dataSourceId || null,
    dataSourceOperationCode:
      overrides.dataSourceOperationCode || null,
    renderComponent: overrides.renderComponent || '',
    formatter: overrides.formatter || '',
    columnConfig: json(
      overrides.columnConfig || {
        showOverflowTooltip: true
      }
    ),
    queryConfig: json(overrides.queryConfig || {}),
    renderConfig: json(overrides.renderConfig || {})
  }
}

function virtualListField(fieldCode, fieldName, index, overrides = {}) {
  return {
    fieldId: `virtual_${fieldCode}`,
    fieldCode,
    fieldName,
    sortOrder: index,
    orderKey: (index + 1) * 1_000_000,
    width: overrides.width || 180,
    showInList: true,
    isQuery: false,
    queryType: 'EQ',
    align: overrides.align || 'left',
    dataSourceType: overrides.dataSourceType,
    dataSourceConfig: json(overrides.dataSourceConfig || {}),
    dataSourceId: null,
    dataSourceOperationCode: null,
    renderComponent: overrides.renderComponent || '',
    formatter: '',
    columnConfig: json({
      showOverflowTooltip: true
    }),
    queryConfig: null,
    renderConfig: json(overrides.renderConfig || {})
  }
}

async function ensureList(entity, listKey, payload) {
  const lists = await api(
    'GET',
    `/entity-list-config/entity/${entity.id}`
  )
  const current = lists.find(item => item.listKey === listKey)
  const currentDetail = current
    ? await api('GET', `/entity-list-config/${current.id}`)
    : null
  const currentFieldsByCode = new Map(
    (currentDetail?.fields || []).map(field => [
      field.fieldCode,
      field
    ])
  )
  const fields = (payload.fields || []).map(field => {
    const currentField = currentFieldsByCode.get(field.fieldCode)
    return currentField
      ? {
          ...field,
          id: currentField.id,
          revision: currentField.revision
        }
      : field
  })
  const request = {
    ...(currentDetail
      ? {
          id: currentDetail.id,
          expectedRevision: currentDetail.revision
        }
      : {}),
    entityId: entity.id,
    entityCode,
    listKey,
    listName: payload.listName,
    description: payload.description,
    isDefault: payload.isDefault === true,
    customComponent: payload.customComponent || '',
    toolbarConfig: payload.toolbarConfig || [],
    rowActionConfig: payload.rowActionConfig || [],
    viewConfig: payload.viewConfig || {},
    dataScopeMode: payload.dataScopeMode || 'INHERIT',
    accessPermissionCode:
      payload.accessPermissionCode || '',
    allowedScenes: payload.allowedScenes || [
      'PAGE',
      'DIALOG',
      'FORM_PICKER'
    ],
    selectionConfig: payload.selectionConfig || {
      selectionMode: 'MULTIPLE',
      valueField: 'id',
      returnMappings: []
    },
    fixedFilterConfig: payload.fixedFilterConfig || {},
    contextBindingConfig:
      payload.contextBindingConfig || {},
    queryProviderCode:
      payload.queryProviderCode || '',
    queryDataSourceId:
      payload.queryDataSourceId || '',
    queryOperationCode:
      payload.queryOperationCode || '',
    fields
  }
  return api('POST', '/entity-list-config/save', request)
}

async function publishList(list, description) {
  const release = await api(
    'POST',
    `/entity-list-config/${list.id}/publish`,
    { description, releaseMode: 'STANDARD' }
  )
  evidence.lists.push({
    id: list.id,
    listKey: list.listKey,
    listName: list.listName,
    customComponent: list.customComponent,
    queryProviderCode: list.queryProviderCode,
    queryDataSourceId: list.queryDataSourceId,
    releaseId: release.id,
    releaseVersion: release.version,
    route: `/entity-list/${entityCode}/${list.listKey}`
  })
  return release
}

async function configureLists(entity, sources) {
  const fields = fieldMap(entity)
  const builtInToolbar = [
    {
      key: 'create',
      type: 'built-in',
      label: '新增数据',
      icon: 'Plus',
      buttonType: 'primary',
      sort: 1,
      enabled: true,
      perm: ''
    },
    {
      key: 'exportSelected',
      type: 'built-in',
      label: '导出选中',
      icon: 'Download',
      buttonType: 'default',
      sort: 2,
      enabled: true,
      perm: ''
    },
    {
      key: 'batchDelete',
      type: 'built-in',
      label: '批量删除',
      icon: 'Delete',
      buttonType: 'danger',
      sort: 3,
      enabled: true,
      perm: ''
    }
  ]
  const builtInRows = [
    {
      key: 'view',
      type: 'built-in',
      label: '查看',
      buttonType: 'primary',
      link: true,
      sort: 1,
      enabled: true,
      perm: ''
    },
    {
      key: 'edit',
      type: 'built-in',
      label: '编辑',
      buttonType: 'primary',
      link: true,
      sort: 2,
      enabled: true,
      perm: ''
    },
    {
      key: 'approve',
      type: 'built-in',
      label: '审批',
      buttonType: 'warning',
      link: true,
      sort: 3,
      enabled: true,
      perm: ''
    },
    {
      key: 'delete',
      type: 'built-in',
      label: '删除',
      buttonType: 'danger',
      link: true,
      sort: 4,
      enabled: true,
      perm: ''
    }
  ]

  const board = await ensureList(
    entity,
    listKeys.board,
    {
      listName: '扩展验收看板列表',
      description:
        'src/project 全自定义列表组件，复用平台查询、分页和动作。',
      isDefault: false,
      customComponent: 'ProjectAcceptanceBoardList',
      toolbarConfig: builtInToolbar,
      rowActionConfig: builtInRows,
      fixedFilterConfig: {
        acceptance_batch: activeAcceptanceBatch
      },
      viewConfig: {
        search: {
          defaultVisibleCount: 3,
          collapsible: true
        },
        pagination: {
          pageSize: 10,
          pageSizes: [10, 20, 50]
        },
        customComponentProps: {
          showProviderTrace: true,
          searchPlaceholder: '搜索项目扩展验收单'
        }
      },
      fields: [
        listField(fields, 'name', 0, {
          width: 220,
          isQuery: true,
          queryType: 'LIKE'
        }),
        listField(fields, 'acceptance_scene', 1, {
          width: 150,
          isQuery: true
        }),
        listField(fields, 'acceptance_score', 2, {
          width: 200,
          align: 'center'
        }),
        listField(fields, 'status', 3, {
          width: 110,
          isQuery: true,
          align: 'center'
        }),
        listField(fields, 'provider_trace', 4, {
          width: 240
        })
      ]
    }
  )

  const schema = await ensureList(
    entity,
    listKeys.schema,
    {
      listName: '扩展验收后端 Schema 列表',
      description:
        '由 PROJECT_CUSTOM_LIST_SCHEMA 增强列表 Schema，并复用项目看板组件。',
      isDefault: false,
      customComponent: 'PROJECT_CUSTOM_LIST_SCHEMA',
      toolbarConfig: builtInToolbar,
      rowActionConfig: builtInRows,
      fixedFilterConfig: {
        acceptance_batch: activeAcceptanceBatch
      },
      viewConfig: {
        search: {
          defaultVisibleCount: 3,
          collapsible: true
        },
        pagination: {
          pageSize: 10,
          pageSizes: [10, 20, 50]
        },
        customComponentProps: {
          showProviderTrace: true,
          searchPlaceholder: '搜索后端 Schema 扩展列表'
        }
      },
      fields: [
        listField(fields, 'name', 0, {
          width: 220,
          isQuery: true,
          queryType: 'LIKE'
        }),
        listField(fields, 'acceptance_scene', 1, {
          width: 160,
          isQuery: true
        }),
        listField(fields, 'acceptance_score', 2, {
          width: 210,
          align: 'center'
        }),
        listField(fields, 'provider_trace', 3, {
          width: 280
        }),
        listField(fields, 'status', 4, {
          width: 110,
          align: 'center'
        })
      ]
    }
  )

  let matrix = await ensureList(
    entity,
    listKeys.matrix,
    {
      listName: '扩展验收标准列表矩阵',
      description:
        '标准列表集中演示单元格、列 Provider、统一列数据源、按钮组件、处理器、事件、规则和权限。',
      isDefault: true,
      fixedFilterConfig: {
        acceptance_batch: activeAcceptanceBatch
      },
      toolbarConfig: [
        ...builtInToolbar,
        {
          key: 'project_toolbar_handler',
          type: 'custom',
          customMode: 'handler',
          customHandler:
            'projectAcceptanceToolbarAction',
          label: '工具栏处理器',
          icon: 'Operation',
          buttonType: 'default',
          sort: 10,
          enabled: true,
          perm: permissionCode
        },
        {
          key: 'project_toolbar_component',
          type: 'custom',
          customMode: 'component',
          customHandler:
            'ProjectAcceptanceInspectButton',
          label: '按钮组件',
          icon: 'View',
          buttonType: 'default',
          sort: 20,
          enabled: true,
          perm: permissionCode
        },
        {
          key: 'project_toolbar_event',
          type: 'custom',
          customMode: 'event',
          label: '统一接口事件',
          icon: 'Connection',
          buttonType: 'primary',
          sort: 30,
          enabled: true,
          perm: permissionCode
        },
        {
          key: 'project_open_provider_list',
          type: 'custom',
          customMode: 'open-list',
          label: '打开 Provider 列表',
          icon: 'List',
          buttonType: 'default',
          sort: 40,
          enabled: true,
          perm: permissionCode,
          targetEntityCode: entityCode,
          targetListKey: listKeys.provider,
          presentation: 'DIALOG',
          selectionMode: 'MULTIPLE',
          openListTitle: '选择 Provider 演示记录',
          relationKey: 'projectCustomRelation',
          selectionHandler:
            'projectAcceptanceSelectionAction'
        }
      ],
      rowActionConfig: [
        ...builtInRows,
        {
          key: 'project_row_handler',
          type: 'custom',
          customMode: 'handler',
          customHandler: 'projectAcceptanceRowAction',
          label: '行处理器',
          buttonType: 'primary',
          link: true,
          sort: 10,
          enabled: true,
          perm: permissionCode,
          availabilityRule: {
            version: 1,
            unavailableBehavior: 'DISABLE',
            message:
              '仅全扩展或表单扩展场景可执行',
            root: {
              type: 'PROJECT:CUSTOM_CONDITION',
              field: 'acceptance_scene',
              operator: 'IN',
              value: [
                'FULL_EXTENSION',
                'FORM_EXTENSION'
              ]
            }
          }
        },
        {
          key: 'project_row_component',
          type: 'custom',
          customMode: 'component',
          customHandler:
            'ProjectAcceptanceInspectButton',
          label: '扩展检查',
          buttonType: 'primary',
          link: true,
          sort: 20,
          enabled: true,
          perm: permissionCode
        },
        {
          key: 'project_row_event',
          type: 'custom',
          customMode: 'event',
          label: '行接口事件',
          buttonType: 'warning',
          link: true,
          sort: 30,
          enabled: true,
          perm: permissionCode
        }
      ],
      viewConfig: {
        search: {
          defaultVisibleCount: 4,
          collapsible: true,
          labelWidth: 100
        },
        table: {
          stripe: true,
          border: true,
          showIndex: true,
          size: 'default'
        },
        pagination: {
          pageSize: 10,
          pageSizes: [10, 20, 50]
        }
      },
      fields: [
        listField(fields, 'name', 0, {
          width: 220,
          isQuery: true,
          queryType: 'LIKE'
        }),
        listField(fields, 'acceptance_scene', 1, {
          width: 150,
          isQuery: true,
          queryType: 'IN'
        }),
        listField(fields, 'acceptance_score', 2, {
          width: 240,
          align: 'center',
          isQuery: true,
          queryType: 'GE',
          renderComponent:
            'ProjectAcceptanceScoreCell',
          renderConfig: { passScore: 60 }
        }),
        virtualListField(
          'project_extension_label',
          '字段 Provider 虚拟列',
          3,
          {
            width: 220,
            dataSourceType: 'PROJECT_CUSTOM_FIELD',
            dataSourceConfig: {
              labelPrefix: '项目扩展'
            }
          }
        ),
        listField(fields, 'provider_trace', 4, {
          width: 260
        }),
        listField(fields, 'extension_result', 5, {
          width: 260
        }),
        listField(fields, 'status', 6, {
          width: 110,
          isQuery: true,
          align: 'center'
        })
      ]
    }
  )

  const matrixSource = await ensureDataSource({
    sourceCode: 'PROJECT_ACCEPTANCE_MATRIX_LIST_SOURCE',
    sourceName: '项目扩展验收标准列表数据源',
    sourceType: 'REGISTERED_PROVIDER',
    providerCode: 'PROJECT_CUSTOM_UI_LIST',
    scopeType: 'LIST',
    scopeId: matrix.id,
    config: {
      columnPrefix: '统一列表列',
      messagePrefix: '标准列表事件'
    },
    operations: [
      operation(
        'LIST_COLUMN',
        '统一虚拟列',
        'READ',
        'LIST'
      ),
      operation(
        'LIST_LOAD',
        '列表加载事件',
        'READ',
        'LIST'
      ),
      operation(
        'LIST_EXPORT',
        '列表导出事件',
        'READ',
        'LIST'
      ),
      operation(
        'TOOLBAR_BUTTON_CLICK',
        '工具栏按钮事件',
        'WRITE',
        'LIST'
      ),
      operation(
        'ROW_BUTTON_CLICK',
        '行按钮事件',
        'WRITE',
        'LIST'
      )
    ]
  })
  matrix = await ensureList(
    entity,
    listKeys.matrix,
    {
      ...matrix,
      fields: matrix.fields.map(field =>
        field.fieldCode === 'provider_trace'
          ? {
              ...field,
              dataSourceId: matrixSource.id,
              dataSourceOperationCode:
                'LIST_COLUMN'
            }
          : field
      )
    }
  )

  await ensureEventBinding({
    ownerType: 'LIST',
    ownerId: matrix.id,
    targetType: 'OWNER',
    eventCode: 'LIST_LOAD',
    steps: [
      eventStep(
        matrixSource,
        'LIST_LOAD',
        'AFTER',
        10
      )
    ]
  })
  await ensureEventBinding({
    ownerType: 'LIST',
    ownerId: matrix.id,
    targetType: 'BUTTON',
    targetKey: 'project_toolbar_event',
    eventCode: 'TOOLBAR_BUTTON_CLICK',
    steps: [
      eventStep(
        matrixSource,
        'TOOLBAR_BUTTON_CLICK',
        'BEFORE',
        10
      ),
      eventStep(
        sources.connectorSource,
        'LIST_CONNECTOR_LOG',
        'AFTER',
        20
      )
    ]
  })
  await ensureEventBinding({
    ownerType: 'LIST',
    ownerId: matrix.id,
    targetType: 'BUTTON',
    targetKey: 'project_row_event',
    eventCode: 'ROW_BUTTON_CLICK',
    steps: [
      eventStep(
        matrixSource,
        'ROW_BUTTON_CLICK',
        'AFTER',
        10
      )
    ]
  })

  const provider = await ensureList(
    entity,
    listKeys.provider,
    {
      listName: '扩展验收查询 Provider 列表',
      description:
        '查询由 PROJECT_CUSTOM_LIST_QUERY 返回一条不落库的安全演示记录。',
      isDefault: false,
      queryProviderCode: 'PROJECT_CUSTOM_LIST_QUERY',
      toolbarConfig: [],
      rowActionConfig: [],
      viewConfig: {
        table: {
          stripe: true,
          border: true
        },
        pagination: {
          pageSize: 20,
          pageSizes: [20]
        }
      },
      selectionConfig: {
        selectionMode: 'MULTIPLE',
        valueField: 'id',
        returnMappings: []
      },
      fields: [
        listField(fields, 'name', 0, {
          width: 240
        }),
        listField(fields, 'acceptance_scene', 1, {
          width: 180
        }),
        listField(fields, 'acceptance_score', 2, {
          width: 220,
          renderComponent:
            'ProjectAcceptanceScoreCell',
          renderConfig: { passScore: 60 }
        }),
        listField(fields, 'provider_trace', 3, {
          width: 300
        })
      ]
    }
  )

  let unified = await ensureList(
    entity,
    listKeys.unified,
    {
      listName: '扩展验收 LIST 统一数据源列表',
      description:
        '整个列表查询由 LIST 作用范围统一数据源返回一条可见演示记录。',
      isDefault: false,
      toolbarConfig: [],
      rowActionConfig: [],
      viewConfig: {
        table: {
          stripe: true,
          border: true
        },
        pagination: {
          pageSize: 20,
          pageSizes: [20]
        }
      },
      fields: [
        listField(fields, 'name', 0, {
          width: 240
        }),
        listField(fields, 'acceptance_scene', 1, {
          width: 190
        }),
        listField(fields, 'acceptance_score', 2, {
          width: 220,
          renderComponent:
            'ProjectAcceptanceScoreCell',
          renderConfig: { passScore: 60 }
        }),
        listField(fields, 'provider_trace', 3, {
          width: 320
        })
      ]
    }
  )

  const unifiedSource = await ensureDataSource({
    sourceCode: 'PROJECT_ACCEPTANCE_UNIFIED_LIST_SOURCE',
    sourceName: '项目扩展验收 LIST 查询数据源',
    sourceType: 'REGISTERED_PROVIDER',
    providerCode: 'PROJECT_CUSTOM_UI_LIST',
    scopeType: 'LIST',
    scopeId: unified.id,
    config: {
      columnPrefix: '统一查询列',
      messagePrefix: '统一查询列表',
      pageNum: 1,
      pageSize: 20
    },
    operations: [
      operation(
        'LIST_QUERY',
        '列表统一查询',
        'READ',
        'LIST',
        pageOutputSchema
      ),
      operation(
        'LIST_COLUMN',
        '列表统一列',
        'READ',
        'LIST'
      )
    ]
  })
  unified = await ensureList(
    entity,
    listKeys.unified,
    {
      ...unified,
      queryDataSourceId: unifiedSource.id,
      queryOperationCode: 'LIST_QUERY',
      fields: unified.fields
    }
  )

  await publishList(
    board,
    '项目扩展验收看板列表发布'
  )
  await publishList(
    schema,
    '项目扩展验收后端 Schema 列表发布'
  )
  await publishList(
    matrix,
    '项目扩展验收标准列表矩阵发布'
  )
  await publishList(
    provider,
    '项目扩展验收查询 Provider 列表发布'
  )
  await publishList(
    unified,
    '项目扩展验收 LIST 统一数据源列表发布'
  )

  return { board, schema, matrix, provider, unified }
}

async function ensurePersonResolver() {
  const configs = await api('GET', '/person-resolvers/configs')
  const current = configs.find(item =>
    item.resolverCode === 'projectCustomPersonResolver'
    || item.code === 'projectCustomPersonResolver'
  )
  assert.ok(
    current?.available !== false,
    'projectCustomPersonResolver 未注册'
  )
  const saved = await api(
    'POST',
    '/person-resolvers/configs/projectCustomPersonResolver',
    {
      displayName:
        current?.displayName || '项目自定义人员解析器',
      description:
        current?.description
        || '验收流程按 userKeys 分配，缺省回退发起人。',
      enabled: true
    }
  )
  evidence.personResolver = {
    code:
      saved.resolverCode
      || saved.code
      || 'projectCustomPersonResolver',
    enabled: saved.enabled,
    available: saved.available
  }
}

async function ensureActionHandlers() {
  const configs = await api(
    'GET',
    '/process-action-handlers/configs'
  )
  const plans = [
    {
      beanName:
        'projectExtensionAcceptanceFlowActionHandler',
      displayName:
        '项目扩展验收可见动作',
      description:
        '写回流程动作范围、时机和元素，便于从页面和日志核对执行链。'
    },
    {
      beanName: 'projectCustomFlowActionHandler',
      displayName: '项目自定义日志动作',
      description:
        '无类型参数的项目动作扩展示例。'
    },
    {
      beanName:
        'projectCustomTypedFlowActionHandler',
      displayName: '项目类型化日志动作',
      description:
        '使用 Java 参数模型的 AFTER_COMMIT 动作扩展示例。'
    }
  ]
  for (const plan of plans) {
    const current = configs.find(item =>
      item.beanName === plan.beanName
    )
    assert.ok(
      current?.available,
      `流程动作处理器未注册: ${plan.beanName}`
    )
    const saved = await api(
      'POST',
      `/process-action-handlers/configs/${plan.beanName}`,
      {
        displayName:
          current.displayName || plan.displayName,
        description:
          current.description || plan.description,
        visibilityScope: 'ENTITY',
        entityCodes: [
          ...new Set([
            ...(current.entityCodes || []),
            entityCode
          ])
        ],
        enabled: true
      }
    )
    evidence.actionHandlers.push({
      beanName: saved.beanName,
      definitionId: saved.definitionId,
      enabled: saved.enabled,
      available: saved.available,
      visibilityScope: saved.visibilityScope
    })
  }
}

function xmlAttribute(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('"', '&quot;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
}

function assigneeConfig() {
  return xmlAttribute(JSON.stringify({
    assigneeType: 'resolver',
    resolverCode: 'projectCustomPersonResolver',
    extraParams: {
      userKeys: [effectiveUsername],
      fallbackToInitiator: true
    }
  }))
}

function bpmnXml(forms) {
  const resolverConfig = assigneeConfig()
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  xmlns:flowable="http://flowable.org/bpmn"
  id="Definitions_ProjectExtensionAcceptance"
  targetNamespace="http://workflow/project-extension-acceptance">
  <bpmn:process id="${processKey}" name="${processName}" isExecutable="true">
    <bpmn:startEvent id="StartEvent_Acceptance" name="开始">
      <bpmn:outgoing>Flow_Start_Technical</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="Task_Technical_Review" name="技术扩展验收">
      <bpmn:extensionElements>
        <flowable:properties>
          <flowable:property name="entityFormId" value="${forms.fullForm.id}" />
          <flowable:property name="entityFormReadonly" value="false" />
          <flowable:property name="assigneeConfig" value="${resolverConfig}" />
        </flowable:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_Start_Technical</bpmn:incoming>
      <bpmn:outgoing>Flow_Technical_Business</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:userTask id="Task_Business_Review" name="业务配置验收">
      <bpmn:extensionElements>
        <flowable:properties>
          <flowable:property name="entityFormId" value="${forms.matrixForm.id}" />
          <flowable:property name="entityFormReadonly" value="false" />
          <flowable:property name="assigneeConfig" value="${resolverConfig}" />
        </flowable:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_Technical_Business</bpmn:incoming>
      <bpmn:outgoing>Flow_Business_Final</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:userTask id="Task_Final_Acceptance" name="最终结果确认">
      <bpmn:extensionElements>
        <flowable:properties>
          <flowable:property name="entityFormId" value="${forms.readonlyForm.id}" />
          <flowable:property name="entityFormReadonly" value="true" />
          <flowable:property name="assigneeConfig" value="${resolverConfig}" />
        </flowable:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_Business_Final</bpmn:incoming>
      <bpmn:outgoing>Flow_Final_End</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:endEvent id="EndEvent_Acceptance" name="结束">
      <bpmn:incoming>Flow_Final_End</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_Start_Technical"
      sourceRef="StartEvent_Acceptance"
      targetRef="Task_Technical_Review" />
    <bpmn:sequenceFlow id="Flow_Technical_Business"
      sourceRef="Task_Technical_Review"
      targetRef="Task_Business_Review" />
    <bpmn:sequenceFlow id="Flow_Business_Final"
      sourceRef="Task_Business_Review"
      targetRef="Task_Final_Acceptance" />
    <bpmn:sequenceFlow id="Flow_Final_End"
      sourceRef="Task_Final_Acceptance"
      targetRef="EndEvent_Acceptance" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_ProjectExtensionAcceptance">
    <bpmndi:BPMNPlane id="BPMNPlane_ProjectExtensionAcceptance"
      bpmnElement="${processKey}">
      <bpmndi:BPMNShape id="StartEvent_Acceptance_di"
        bpmnElement="StartEvent_Acceptance">
        <dc:Bounds x="50" y="110" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Technical_Review_di"
        bpmnElement="Task_Technical_Review">
        <dc:Bounds x="130" y="88" width="150" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Business_Review_di"
        bpmnElement="Task_Business_Review">
        <dc:Bounds x="340" y="88" width="150" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Final_Acceptance_di"
        bpmnElement="Task_Final_Acceptance">
        <dc:Bounds x="550" y="88" width="150" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_Acceptance_di"
        bpmnElement="EndEvent_Acceptance">
        <dc:Bounds x="770" y="110" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_Start_Technical_di"
        bpmnElement="Flow_Start_Technical">
        <di:waypoint x="86" y="128" />
        <di:waypoint x="130" y="128" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_Technical_Business_di"
        bpmnElement="Flow_Technical_Business">
        <di:waypoint x="280" y="128" />
        <di:waypoint x="340" y="128" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_Business_Final_di"
        bpmnElement="Flow_Business_Final">
        <di:waypoint x="490" y="128" />
        <di:waypoint x="550" y="128" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_Final_End_di"
        bpmnElement="Flow_Final_End">
        <di:waypoint x="700" y="128" />
        <di:waypoint x="770" y="128" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`
}

async function ensureProcess(forms, entity) {
  let process = await optionalApi(
    'GET',
    `/process/key/${processKey}`
  )
  const payload = {
    processKey,
    processName,
    description:
      '技术扩展验收 -> 业务配置验收 -> 最终结果确认，三个节点分别使用不同表单。',
    category: 'project-extension-acceptance',
    bpmnXml: bpmnXml(forms)
  }
  if (!process) {
    process = await api('POST', '/process', payload)
  } else {
    process = await api(
      'POST',
      `/process/${process.id}/update`,
      payload
    )
  }
  await api(
    'POST',
    `/entity/${entity.id}/workflow-binding/update`,
    { processDefinitionId: process.id }
  )

  const draftActions = await api(
    'GET',
    `/process-actions/process/${process.id}`
  )
  for (const action of draftActions) {
    await api(
      'POST',
      `/process-actions/${action.id}`
    )
  }

  const handlers = new Map(
    evidence.actionHandlers.map(item => [
      item.beanName,
      item
    ])
  )
  const actionPlans = [
    {
      scopeType: 'PROCESS',
      elementId: null,
      triggerTiming: 'PROCESS_STARTED',
      executionMode: 'IN_TRANSACTION',
      failurePolicy: 'ROLLBACK',
      actionName: '流程启动写回',
      interfaceName:
        'projectExtensionAcceptanceFlowActionHandler',
      params: {
        stage: 'PROCESS_STARTED',
        visibleMessage:
          '项目扩展验收流程已启动',
        writeBack: true
      }
    },
    {
      scopeType: 'NODE',
      elementId: 'Task_Technical_Review',
      triggerTiming: 'TASK_CREATED',
      executionMode: 'AFTER_COMMIT',
      failurePolicy: 'RETRY',
      actionName: '技术节点创建日志',
      interfaceName:
        'projectCustomTypedFlowActionHandler',
      params: {
        message: '技术扩展验收任务已创建',
        priority: 10,
        dryRun: true
      },
      retryConfig: { maxRetries: 3 }
    },
    {
      scopeType: 'NODE',
      elementId: 'Task_Technical_Review',
      triggerTiming: 'TASK_COMPLETING',
      executionMode: 'IN_TRANSACTION',
      failurePolicy: 'ROLLBACK',
      actionName: '技术节点完成写回',
      interfaceName:
        'projectExtensionAcceptanceFlowActionHandler',
      params: {
        stage: 'TECHNICAL_REVIEW_COMPLETING',
        visibleMessage:
          '技术扩展验收节点已完成',
        writeBack: true
      }
    },
    {
      scopeType: 'SEQUENCE_FLOW',
      elementId: 'Flow_Technical_Business',
      triggerTiming: 'TRANSITION_TAKEN',
      executionMode: 'IN_TRANSACTION',
      failurePolicy: 'ROLLBACK',
      actionName: '技术到业务连线动作',
      interfaceName:
        'projectCustomFlowActionHandler',
      params: {
        scenario: 'SEQUENCE_FLOW_EXTENSION',
        message:
          '技术验收进入业务配置验收'
      }
    },
    {
      scopeType: 'NODE',
      elementId: 'Task_Business_Review',
      triggerTiming: 'NODE_ENTERED',
      executionMode: 'IN_TRANSACTION',
      failurePolicy: 'ROLLBACK',
      actionName: '业务节点进入写回',
      interfaceName:
        'projectExtensionAcceptanceFlowActionHandler',
      params: {
        stage: 'BUSINESS_REVIEW_ENTERED',
        visibleMessage:
          '已进入业务配置验收节点',
        writeBack: true
      }
    },
    {
      scopeType: 'NODE',
      elementId: 'Task_Final_Acceptance',
      triggerTiming: 'NODE_COMPLETED',
      executionMode: 'IN_TRANSACTION',
      failurePolicy: 'ROLLBACK',
      actionName: '最终节点完成写回',
      interfaceName:
        'projectExtensionAcceptanceFlowActionHandler',
      params: {
        stage: 'FINAL_ACCEPTANCE_COMPLETED',
        visibleMessage:
          '最终扩展验收节点已完成',
        writeBack: true
      }
    },
    {
      scopeType: 'PROCESS',
      elementId: null,
      triggerTiming: 'PROCESS_COMPLETED',
      executionMode: 'AFTER_COMMIT',
      failurePolicy: 'RETRY',
      actionName: '流程完成写回',
      interfaceName:
        'projectExtensionAcceptanceFlowActionHandler',
      params: {
        stage: 'PROCESS_COMPLETED',
        visibleMessage:
          '项目扩展验收流程已完成',
        writeBack: true
      },
      retryConfig: { maxRetries: 3 }
    }
  ]

  for (let index = 0; index < actionPlans.length; index++) {
    const plan = actionPlans[index]
    const definition = handlers.get(plan.interfaceName)
    assert.ok(
      definition?.definitionId,
      `动作处理器目录缺少 ${plan.interfaceName}`
    )
    await api('POST', '/process-actions', {
      processConfigId: process.id,
      scopeType: plan.scopeType,
      elementId: plan.elementId,
      sequenceFlowId:
        plan.elementId || '__PROCESS__',
      triggerTiming: plan.triggerTiming,
      executionMode: plan.executionMode,
      failurePolicy: plan.failurePolicy,
      actionName: plan.actionName,
      description:
        '项目扩展验收真实动作',
      interfaceName: plan.interfaceName,
      methodName: 'execute',
      paramsJson: json(plan.params),
      retryConfig: plan.retryConfig
        ? json(plan.retryConfig)
        : null,
      actionDefinitionId:
        definition.definitionId,
      sortOrder: (index + 1) * 10,
      enabled: true
    })
  }

  const published = await api(
    'POST',
    `/process/${process.id}/publish`,
    {
      versionDescription:
        '项目扩展验收流程发布'
    }
  )
  evidence.process = {
    id: process.id,
    processKey,
    processName,
    status: published.status,
    actionCount: actionPlans.length,
    nodeFormIds: {
      Task_Technical_Review: forms.fullForm.id,
      Task_Business_Review: forms.matrixForm.id,
      Task_Final_Acceptance:
        forms.readonlyForm.id
    }
  }
  return process
}

async function configureVersionPolicy(entity, entitySource) {
  const document = {
    entityId: entity.id,
    entityCode,
    entityName,
    enabled: true,
    scenarios: [
      {
        scenarioCode: 'PROJECT_EXTENSION_ACCEPTANCE',
        scenarioName: '项目扩展验收实体变更',
        sourceTypes: [
          'FORM',
          'LIST',
          'FLOW_ACTION',
          'CUSTOM_INTERFACE',
          'SYSTEM_TASK'
        ],
        operationTypes: [
          'CREATE',
          'UPDATE'
        ],
        businessIntents: [],
        condition: {},
        priority: 10,
        versionTitleTemplate:
          '项目扩展验收-${operationType}',
        enabled: true
      }
    ],
    steps: [
      {
        scenarioCode:
          'PROJECT_EXTENSION_ACCEPTANCE',
        phase: 'PREPARE',
        stepType: 'MANAGED_INTERFACE',
        stepName: '统一数据源变更准备日志',
        providerCode: entitySource.id,
        config: {
          dataSourceId: entitySource.id,
          operationCode: 'MUTATION_PREPARE'
        },
        sortOrder: 10,
        enabled: true
      },
      {
        scenarioCode:
          'PROJECT_EXTENSION_ACCEPTANCE',
        phase: 'BEFORE_WRITE',
        stepType: 'JAVA_PROVIDER',
        stepName: '项目自定义实体变更步骤日志',
        providerCode:
          'PROJECT_CUSTOM_MUTATION_STEP',
        config: {
          scene: 'PROJECT_EXTENSION_ACCEPTANCE',
          message:
            '记录验收实体创建和更新的内部执行逻辑'
        },
        sortOrder: 20,
        enabled: true
      }
    ],
    targetBindings: []
  }
  const saved = await api(
    'POST',
    `/entity-versions/configs/${entityCode}/save`,
    document
  )
  const published = await api(
    'POST',
    `/entity-versions/configs/${entityCode}/publish`
  )
  evidence.versionConfiguration = {
    id: saved.id,
    enabled: published.enabled,
    status: published.status,
    activeReleaseId: published.activeReleaseId,
    activeReleaseVersion:
      published.activeReleaseVersion,
    scenarioCount:
      published.scenarios?.length || 0,
    stepCount: published.steps?.length || 0
  }
}

async function listEntityData(filters = {}) {
  const query = new URLSearchParams({
    pageNum: '1',
    pageSize: '200'
  })
  for (const [key, value] of Object.entries(filters)) {
    if (value != null && value !== '') {
      query.set(key, String(value))
    }
  }
  return rows(await api(
    'GET',
    `/entity-data/entity/${entityCode}?${query}`
  ))
}

function fixtureSortValue(item, startProcess) {
  const activeProcess =
    startProcess
    && item.processInstanceId
    && item.currentTaskId
      ? '1'
      : '0'
  return `${activeProcess}:${item.createdAt || ''}:${item.id || ''}`
}

function isActiveProcessFixture(item) {
  return Boolean(
    item.processInstanceId
    && item.currentTaskId
  )
}

async function removeDuplicateFixture(item, fixtureKey) {
  const processTerminated =
    isActiveProcessFixture(item)
  if (processTerminated) {
    await api(
      'POST',
      `/process-instance/${encodeURIComponent(item.processInstanceId)}/terminate`,
      {
        reason:
          `清理项目扩展验收重复样本 ${fixtureKey}`
      }
    )
  }
  await updateFixtureBatch(item, archivedAcceptanceBatch)
  evidence.fixtureCleanup.push({
    fixtureKey,
    id: item.id,
    generatedCode: item.code,
    processInstanceId: item.processInstanceId,
    processTerminated,
    retainedAsProcessHistory:
      Boolean(item.processInstanceId),
    archivedFromAcceptanceLists: true
  })
}

async function updateFixtureBatch(item, batch) {
  return api(
    'POST',
    `/entity-data/entity/${entityCode}/detail/${item.id}/update`
      + `?listKey=${encodeURIComponent(listKeys.matrix)}`,
    {
      data: {
        ...(item.data || {}),
        name: item.name,
        acceptance_batch: batch
      },
      startProcess: false
    }
  )
}

async function ensureFixture(
  fixtureKey,
  name,
  scene,
  score,
  startProcess
) {
  const candidates = (await listEntityData({
    acceptance_scene: scene,
    acceptance_scene_op: 'EQ'
  })).filter(item =>
    item.name === name
    && item.data?.acceptance_scene === scene
    && item.data?.acceptance_batch
      !== archivedAcceptanceBatch
  ).sort((left, right) =>
    fixtureSortValue(right, startProcess)
      .localeCompare(fixtureSortValue(left, startProcess))
  )
  const reusableCandidates = startProcess
    ? candidates.filter(isActiveProcessFixture)
    : candidates.filter(item => !item.processInstanceId)
  let existing = reusableCandidates[0]
  const duplicates = candidates.filter(item =>
    item.id !== existing?.id
  )
  for (const duplicate of duplicates) {
    await removeDuplicateFixture(duplicate, fixtureKey)
  }
  if (existing) {
    if (existing.data?.acceptance_batch
        !== activeAcceptanceBatch) {
      existing = await updateFixtureBatch(
        existing,
        activeAcceptanceBatch
      )
    }
    evidence.fixtures.push({
      id: existing.id,
      fixtureKey,
      code: existing.code,
      name: existing.name,
      processInstanceId:
        existing.processInstanceId,
      acceptanceBatch:
        existing.data?.acceptance_batch
        || activeAcceptanceBatch,
      reused: true
    })
    return existing
  }
  const saved = await api('POST', '/entity-data', {
    entityCode,
    name,
    title: name,
    submitterId: currentUser.id,
    submitterName:
      currentUser.nickname
      || currentUser.username
      || effectiveUsername,
    deptId: currentUser.deptId || '2',
    startProcess,
    data: {
      name,
      acceptance_batch: activeAcceptanceBatch,
      acceptance_scene: scene,
      acceptance_score: score,
      owner_name:
        currentUser.nickname
        || currentUser.username
        || effectiveUsername,
      planned_date: today(1),
      description:
        `用于验收 src/project 前端扩展、workflow-project 后端扩展和平台配置入口；样本标识 ${fixtureKey}。`,
      provider_trace:
        '等待 FORM/LIST/ENTITY 统一数据源执行',
      event_trace:
        '等待字段、表单或列表事件执行',
      extension_summary:
        '等待自定义节点执行',
      extension_result:
        '等待流程动作写回',
      backend_trace:
        '等待后端动作执行',
      review_comment: '',
      decision:
        score >= 60 ? 'PASS' : 'IMPROVE'
    }
  })
  evidence.fixtures.push({
    id: saved.id,
    fixtureKey,
    code: saved.code,
    name: saved.name,
    processInstanceId:
      saved.processInstanceId,
    currentTaskName: saved.currentTaskName,
    acceptanceBatch: activeAcceptanceBatch,
    reused: false
  })
  return saved
}

async function verifyRuntime(lists, forms, process, fixtures) {
  const pageRequest = {
    pageNum: 1,
    pageSize: 20,
    scene: 'PAGE',
    filters: {}
  }
  const [
    providerSchema,
    standardSchema,
    schemaPage,
    standardPage,
    providerPage,
    unifiedPage,
    fullForm,
    matrixForm,
    matrixNodes,
    fullFormEventBindings,
    matrixFormEventBindings,
    entityEventBindings,
    processDefinition,
    processActions
  ] = await Promise.all([
    api(
      'GET',
      `/entity-lists/${entityCode}/${lists.schema.listKey}/schema?scene=PAGE`
    ),
    api(
      'GET',
      `/entity-lists/${entityCode}/${lists.matrix.listKey}/schema?scene=PAGE`
    ),
    api(
      'POST',
      `/entity-lists/${entityCode}/${lists.schema.listKey}/query`,
      pageRequest
    ),
    api(
      'POST',
      `/entity-lists/${entityCode}/${lists.matrix.listKey}/query`,
      pageRequest
    ),
    api(
      'POST',
      `/entity-lists/${entityCode}/${lists.provider.listKey}/query`,
      pageRequest
    ),
    api(
      'POST',
      `/entity-lists/${entityCode}/${lists.unified.listKey}/query`,
      pageRequest
    ),
    api('GET', `/entity-form/${forms.fullForm.id}`),
    api('GET', `/entity-form/${forms.matrixForm.id}`),
    api(
      'GET',
      `/entity-forms/${forms.matrixForm.id}/nodes`
    ),
    api(
      'GET',
      `/ui-event-bindings?ownerType=FORM&ownerId=${forms.fullForm.id}`
    ),
    api(
      'GET',
      `/ui-event-bindings?ownerType=FORM&ownerId=${forms.matrixForm.id}`
    ),
    api(
      'GET',
      `/ui-event-bindings?ownerType=ENTITY&ownerId=${forms.matrixForm.entityId}`
    ),
    api('GET', `/process/key/${processKey}`),
    api(
      'GET',
      `/process-actions/process/${process.id}`
    )
  ])

  const fullFormBindings = parseDocument(
    fullForm.dataSourceBindingsDocument,
    '整表单数据源绑定'
  )
  const matrixViewConfig = parseDocument(
    matrixForm.viewConfig,
    '节点矩阵表单视图配置'
  )
  const actionButtons =
    matrixViewConfig.actionBar?.customButtons || []
  const footerButton = actionButtons.find(
    item => item.key === 'acceptance_form_log'
  )
  const slotButton = actionButtons.find(
    item => item.key === 'acceptance_inline_log'
  )
  const actionSlotNode = matrixNodes.find(
    item =>
      item.nodeType === 'ACTION_SLOT'
      && item.nodeKey === 'acceptance_inline_actions'
  )
  const scoreNode = matrixNodes.find(
    item =>
      item.nodeType === 'FIELD'
      && item.bindingRef === 'acceptance_score'
  )
  const levelNode = matrixNodes.find(
    item =>
      item.nodeType === 'FIELD'
      && item.bindingRef === 'review_level'
  )
  const summaryNode = matrixNodes.find(
    item =>
      item.nodeType === 'FIELD'
      && item.bindingRef === 'extension_summary'
  )
  const scoreProps = parseDocument(
    scoreNode?.propsDocument,
    '评分字段节点属性'
  )
  const levelProps = parseDocument(
    levelNode?.propsDocument,
    '复核级别字段节点属性'
  )
  const summaryProps = parseDocument(
    summaryNode?.propsDocument,
    '摘要字段节点属性'
  )
  const levelBindings = parseDocument(
    levelNode?.dataSourceBindingsDocument,
    '复核级别字段数据源绑定'
  )
  const summaryBindings = parseDocument(
    summaryNode?.dataSourceBindingsDocument,
    '摘要节点数据源绑定'
  )
  const findEventBinding = (
    bindings,
    targetType,
    targetKey,
    eventCode
  ) => bindings.find(item =>
    item.targetType === targetType
    && String(item.targetKey || '') === String(targetKey || '')
    && item.eventCode === eventCode
    && item.enabled === true
  )
  const fieldButtonBinding = findEventBinding(
    matrixFormEventBindings,
    'FIELD',
    'acceptance_score',
    'FIELD_BUTTON_CLICK'
  )
  const footerButtonBinding = findEventBinding(
    matrixFormEventBindings,
    'BUTTON',
    'acceptance_form_log',
    'FORM_BUTTON_CLICK'
  )
  const slotButtonBinding = findEventBinding(
    matrixFormEventBindings,
    'BUTTON',
    'acceptance_inline_log',
    'FORM_BUTTON_CLICK'
  )

  assert.ok(
    providerSchema.viewConfig?.projectCustomSchema?.enabled === true,
    '后端 Schema Provider 没有写入可见执行标记'
  )
  assert.ok(
    rows(schemaPage).some(item =>
      fixtures.some(fixture => fixture.id === item.id)
    ),
    '后端 Schema 扩展列表没有完成真实查询'
  )
  assert.ok(
    rows(standardPage).some(item =>
      fixtures.some(fixture =>
        fixture.id === item.id
      )
    ),
    '标准列表没有返回验收数据'
  )
  const expectedFixtureIds =
    fixtures.map(item => item.id).sort()
  assert.deepEqual(
    rows(schemaPage).map(item => item.id).sort(),
    expectedFixtureIds,
    '后端 Schema 扩展列表没有只返回当前验收批次'
  )
  assert.deepEqual(
    rows(standardPage).map(item => item.id).sort(),
    expectedFixtureIds,
    '标准列表没有只返回当前验收批次'
  )
  assert.ok(
    rows(providerPage).some(item =>
      item.id === 'PROJECT-CUSTOM-LIST-SAMPLE'
    ),
    '查询 Provider 列表没有返回演示记录'
  )
  assert.ok(
    rows(unifiedPage).some(item =>
      item.id === 'PROJECT-UI-LIST-SAMPLE'
    ),
    'LIST 统一数据源没有返回演示记录'
  )
  assert.equal(
    fullForm.customComponent,
    'ProjectExtensionAcceptanceForm',
    '整表单没有绑定项目自定义表单组件'
  )
  ;['FORM_INIT', 'AFTER_LOAD', 'BEFORE_SUBMIT'].forEach(usage => {
    assert.ok(
      fullFormBindings[usage]?.serviceId,
      `整表单缺少 ${usage} 数据源绑定`
    )
  })
  assert.ok(
    fullForm.activeReleaseId,
    '整表单没有激活发布版本'
  )
  assert.ok(
    matrixForm.activeReleaseId,
    '节点矩阵表单没有激活发布版本'
  )
  assert.equal(
    footerButton?.placement,
    'FOOTER',
    '表单底部自定义按钮配置没有落库'
  )
  assert.equal(
    slotButton?.placement,
    'ACTION_SLOT',
    '表单动作插槽按钮配置没有落库'
  )
  assert.ok(
    actionSlotNode,
    '节点矩阵表单缺少 ACTION_SLOT 节点'
  )
  assert.equal(
    scoreProps.componentType,
    'number',
    '评分字段没有保留后端可校验的内置回退组件'
  )
  assert.equal(
    scoreProps.componentExtensionType,
    'FIELD',
    '评分字段没有声明 FIELD 扩展引用'
  )
  assert.equal(
    scoreNode?.componentName,
    'project_acceptance_score',
    '评分字段没有绑定项目自定义字段组件'
  )
  assert.equal(
    levelProps.componentType,
    'select',
    '复核级别字段没有保留后端可校验的内置回退组件'
  )
  assert.equal(
    levelProps.componentExtensionType,
    'FIELD',
    '复核级别字段没有声明 FIELD 扩展引用'
  )
  assert.equal(
    levelNode?.componentName,
    'project_acceptance_level',
    '复核级别字段没有绑定项目自定义字段组件'
  )
  ;['FIELD_OPTIONS', 'FIELD_DEFAULT'].forEach(usage => {
    assert.ok(
      levelBindings[usage]?.serviceId,
      `复核级别字段缺少 ${usage} 数据源绑定`
    )
  })
  assert.equal(
    summaryNode?.componentName,
    'ProjectAcceptanceSummaryNode',
    '摘要字段没有绑定项目自定义节点组件'
  )
  assert.equal(
    parseDocument(
      summaryProps.componentProps,
      '摘要节点组件配置'
    ).title,
    '扩展执行摘要',
    '摘要节点 configSchema 配置没有按 componentProps 契约落库'
  )
  assert.ok(
    summaryBindings.FIELD_COMPUTE?.serviceId,
    '摘要节点缺少 FIELD_COMPUTE 数据源绑定'
  )
  assert.ok(
    findEventBinding(
      fullFormEventBindings,
      'OWNER',
      '',
      'FORM_SAVE'
    ),
    '整表单缺少 FORM_SAVE 事件绑定'
  )
  assert.equal(
    parseDocument(
      fieldButtonBinding?.stepsDocument,
      '评分字段按钮事件步骤',
      []
    ).length,
    2,
    '评分字段按钮没有绑定完整的 Provider 与连接器步骤'
  )
  assert.equal(
    parseDocument(
      footerButtonBinding?.stepsDocument,
      '表单底部按钮事件步骤',
      []
    ).length,
    2,
    '表单底部按钮没有绑定完整的 Provider 与连接器步骤'
  )
  assert.equal(
    parseDocument(
      slotButtonBinding?.stepsDocument,
      '表单插槽按钮事件步骤',
      []
    ).length,
    2,
    '表单插槽按钮没有绑定完整的 Provider 与连接器步骤'
  )
  assert.ok(
    findEventBinding(
      entityEventBindings,
      'OWNER',
      '',
      'FORM_OPEN'
    ),
    '实体作用域缺少 FORM_OPEN 事件绑定'
  )
  assert.equal(
    processDefinition.status,
    'PUBLISHED',
    '验收流程没有处于已发布状态'
  )
  assert.equal(
    processActions.length,
    7,
    '验收流程动作数量不完整'
  )
  assert.ok(
    processActions.every(action =>
      action.enabled === true
      && action.actionDefinitionId
    ),
    '验收流程存在未启用或未绑定动作目录的动作'
  )

  evidence.runtimeVerification = {
    schemaProvider:
      providerSchema.viewConfig?.projectCustomSchema,
    schemaRecordCount:
      rows(schemaPage).length,
    standardFieldCount:
      standardSchema.fields?.length || 0,
    standardRecordCount:
      rows(standardPage).length,
    providerRecordIds:
      rows(providerPage).map(item => item.id),
    unifiedRecordIds:
      rows(unifiedPage).map(item => item.id),
    formContracts: {
      fullCustomComponent:
        fullForm.customComponent,
      fullDataSourceUsages:
        Object.keys(fullFormBindings),
      matrixActionButtons:
        actionButtons.map(item => ({
          key: item.key,
          placement: item.placement,
          slotKey: item.slotKey || ''
        })),
      matrixNodeTypes:
        matrixNodes.map(item => item.nodeType),
      matrixEventCodes:
        matrixFormEventBindings.map(item => ({
          targetType: item.targetType,
          targetKey: item.targetKey || '',
          eventCode: item.eventCode
        }))
    },
    processContracts: {
      status: processDefinition.status,
      actionCount: processActions.length,
      timings:
        processActions.map(item => item.triggerTiming)
    }
  }
}

function writeEvidence(result, error) {
  const report = {
    ...evidence,
    result,
    conclusion:
      result === 'PASS'
        ? 'PASS: 项目扩展验收实体、表单、列表、流程和后端扩展已完成真实配置'
        : 'FAIL: 项目扩展验收初始化失败',
    error: error
      ? {
          name: error.name,
          message: error.message
        }
      : null,
    acceptanceRoutes: {
      defaultList:
        `/entity-list/${entityCode}/${listKeys.matrix}`,
      customBoard:
        `/entity-list/${entityCode}/${listKeys.board}`,
      schemaProviderList:
        `/entity-list/${entityCode}/${listKeys.schema}`,
      providerList:
        `/entity-list/${entityCode}/${listKeys.provider}`,
      unifiedList:
        `/entity-list/${entityCode}/${listKeys.unified}`
    }
  }
  const file = path.join(
    evidenceDir,
    result === 'PASS'
      ? 'latest.json'
      : 'latest-failed.json'
  )
  writeFileSync(
    file,
    JSON.stringify(report, null, 2),
    { mode: 0o600 }
  )
  return file
}

async function main() {
  if (tokenFile && existsSync(tokenFile)) {
    token = readFileSync(tokenFile, 'utf8').trim()
    unlinkSync(tokenFile)
    assert.ok(token, 'TEST_TOKEN_FILE 为空')
  } else {
    let loginUsername = username
    let loginPassword = password
    if (credentialFile && existsSync(credentialFile)) {
      const credentials = JSON.parse(
        readFileSync(credentialFile, 'utf8')
      )
      unlinkSync(credentialFile)
      loginUsername = String(
        credentials.username || ''
      ).trim()
      loginPassword = credentials.password
    }
    assert.ok(
      loginUsername && loginPassword,
      '测试账号或密码为空'
    )
    const login = await api('POST', '/auth/login', {
      username: loginUsername,
      password: loginPassword
    })
    token = login.token
  }
  currentUser = await api('GET', '/auth/current')
  assert.ok(currentUser?.id, '当前登录用户上下文缺少用户 ID')
  assert.equal(
    currentUser.passwordResetRequired,
    false,
    `账号 ${currentUser.username || '-'} 必须先完成首次改密`
  )
  assert.ok(
    (currentUser.roles || []).includes('super_admin'),
    `账号 ${currentUser.username || '-'} 不具备 super_admin 角色`
  )
  effectiveUsername = currentUser.username

  const entity = await ensureEntity()
  await ensureUiExtensions()
  await ensurePersonResolver()
  await ensureActionHandlers()
  const forms = await configureForms(entity)
  await configureVersionPolicy(entity, forms.entitySource)
  const process = await ensureProcess(forms, entity)

  const fixtures = [
    await ensureFixture(
      'EXT-ACCEPT-001',
      '全扩展链路验收单',
      'FULL_EXTENSION',
      88,
      true
    ),
    await ensureFixture(
      'EXT-ACCEPT-002',
      '表单扩展验收单',
      'FORM_EXTENSION',
      72,
      false
    ),
    await ensureFixture(
      'EXT-ACCEPT-003',
      '列表扩展验收单',
      'LIST_EXTENSION',
      56,
      false
    )
  ]
  const lists = await configureLists(entity, forms)
  await verifyRuntime(lists, forms, process, fixtures)

  const evidencePath = writeEvidence('PASS')
  console.log(
    `project extension acceptance bootstrap passed: ${evidencePath}`
  )
  console.log(
    `open: /entity-list/${entityCode}/${listKeys.matrix}`
  )
}

main().catch(error => {
  const evidencePath = writeEvidence('FAIL', error)
  console.error(
    `project extension acceptance bootstrap failed: ${evidencePath}`
  )
  console.error(error)
  process.exitCode = 1
})
