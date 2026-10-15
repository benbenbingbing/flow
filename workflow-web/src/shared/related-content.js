import { relatedContentPresentationError } from './related-content-presentation.js'
const SUPPORTED_ACTIONS = new Set([
  'VIEW',
  'CREATE',
  'EDIT'
])
let relatedContentKeySequence = 0

export const RELATED_CONTENT_TYPE_OPTIONS = Object.freeze([
  { value: 'FORM', label: '表单', description: '查看或编辑一条目标数据。' },
  { value: 'LIST', label: '列表', description: '查看多条目标数据，并按权限新增或编辑。' }
])

export const RELATED_CONTENT_POSITION_OPTIONS = Object.freeze([
  { value: 'INLINE', label: '嵌入当前页面', description: '直接显示在当前页面，适合经常查看的内容。' },
  { value: 'TAB', label: '表单 Tab 页', description: '显示在当前表单已有的 Tab 页中，只适用于表单设计。' },
  { value: 'ROW_EXPAND', label: '行展开', description: '从列表行展开查看，只适用于列表设计。' },
  { value: 'DIALOG', label: '弹窗', description: '在当前页面中央打开，适合短时间查看或操作。' },
  { value: 'DRAWER', label: '抽屉', description: '从页面侧边打开，可保留当前页面上下文。' },
  { value: 'PAGE', label: '全屏打开', description: '在全屏窗口中打开，适合复杂内容；不会离开当前业务页面。' }
])

export const RELATED_CONTENT_RELATION_OPTIONS = Object.freeze([
  {
    value: 'SAME_RECORD',
    label: '使用当前记录',
    description: '来源和目标是同一种实体时，直接使用当前记录。'
  },
  {
    value: 'ENTITY_RELATION',
    label: '使用已有实体关系',
    description: '适合已经在实体配置中建立父子或关联关系的场景。'
  },
  {
    value: 'REFERENCE_FIELD',
    label: '使用引用字段',
    description: '例如需求的“所属项目”字段指向项目。'
  },
  {
    value: 'REVERSE_REFERENCE',
    label: '使用反向引用',
    description: '例如查找所有“所属项目=当前项目”的需求。'
  },
  {
    value: 'FIELD_MATCH',
    label: '使用字段匹配',
    description: '适合两边通过业务编码、客户编号等字段匹配。'
  },
  {
    value: 'INTERFACE_SERVICE',
    label: '使用扩展接口',
    description: '普通关系无法表达复杂规则时，由一个已注册扩展接口返回目标数据。'
  }
])

export const RELATED_CONTENT_ACTION_OPTIONS = Object.freeze([
  { value: 'VIEW', label: '仅查看', description: '查看关联记录，不提供新增或编辑操作。' },
  { value: 'CREATE', label: '新增记录', description: '目标为表单且尚无关联记录时，在当前区域填写新记录；目标为列表时，使用列表自带的新增入口。新记录独立保存，不随主表提交。' },
  { value: 'EDIT', label: '编辑记录', description: '目标为表单时，在当前区域修改关联记录；目标为列表时，通过行操作中的编辑入口修改。修改需独立保存，取消主表不会撤销已保存的修改。' },
])

export const RELATED_CONTENT_FAILURE_OPTIONS = Object.freeze([
  { value: 'ERROR', label: '显示错误', description: '明确告知用户处理失败，不展示不可信数据。' },
  { value: 'PLACEHOLDER', label: '显示占位', description: '保留页面位置并显示暂不可用说明。' },
  { value: 'HIDE', label: '隐藏内容', description: '隐藏关联内容，不影响页面其他部分。' }
])

function clone(value) {
  if (value === undefined) return undefined
  return JSON.parse(JSON.stringify(value))
}

function parseConfig(value) {
  if (!value) return {}
  if (typeof value === 'object') return clone(value)
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

function optionLabel(options, value, fallback = '') {
  return options.find(item => item.value === value)?.label || fallback || value || '-'
}

export function createEmptyRelatedContent({ ownerType = 'FORM', sourceEntity = {} } = {}) {
  const normalizedOwnerType = String(ownerType || 'FORM').toUpperCase()
  const compositionKey = createRelatedContentKey()
  const listOwner = normalizedOwnerType === 'LIST'
  return {
    id: '',
    revision: 0,
    compositionKey,
    orderKey: 1000000,
    // 列表的入口由标准按钮引用配置，关联内容本身不再决定工具栏或操作列。
    anchorType: listOwner ? 'LIST_ACTION' : 'OWNER',
    anchorKey: listOwner ? compositionKey : '',
    config: {
      schemaVersion: 1,
      parameterMappings: [],
      name: '',
      enabled: true,
      source: {
        entityId: sourceEntity.id || '',
        entityCode: sourceEntity.entityCode || '',
        entityName: sourceEntity.entityName || ''
      },
      target: {
        entityId: '',
        entityCode: '',
        entityName: '',
        contentType: 'FORM',
        contentId: '',
        contentKey: '',
        contentName: ''
      },
      presentation: {
        position: 'DRAWER',
        loadMode: 'ON_DEMAND'
      },
      relation: {
        type: 'ENTITY_RELATION',
        relationCode: '',
        relationName: '',
        sourceField: '',
        sourceFieldName: '',
        targetField: '',
        targetFieldName: '',
        mappings: []
      },
      actions: ['VIEW'],
      specialHandling: {
        mode: 'NONE',
        interfaceService: {
          extensionId: '',
          extensionName: '',
          inputMappings: [],
          outputMappings: []
        },
        actionServices: [],
        customComponent: {
          name: '',
          displayName: '',
          version: 1,
          artifactDigest: '',
          props: {}
        },
        failurePolicy: 'ERROR'
      }
    }
  }
}

/** 把历史服务绑定读取为单接口模型，返回值不会携带旧二段字段。 */
function normalizeInterfaceBinding(binding = {}, action = false) {
  return {
    ...(action ? { actionKey: binding?.actionKey || '' } : {}),
    extensionId: binding?.extensionId || binding?.serviceId || '',
    extensionName: binding?.extensionName || binding?.serviceName
      || binding?.operationName || '',
    inputMappings: Array.isArray(binding?.inputMappings)
      ? clone(binding.inputMappings)
      : [],
    outputMappings: Array.isArray(binding?.outputMappings)
      ? clone(binding.outputMappings)
      : [],
    ...(action ? { failurePolicy: binding?.failurePolicy || 'ERROR' } : {})
  }
}

/**
 * 持久化时只保留后端契约字段；extensionName 仅用于编辑态中文回显，
 * 不能随配置写回，否则严格键校验会把它识别为未知字段。
 */
function serializeInterfaceBinding(binding = {}, action = false) {
  return {
    ...(action ? { actionKey: binding.actionKey || '' } : {}),
    extensionId: binding.extensionId || '',
    inputMappings: Array.isArray(binding.inputMappings)
      ? clone(binding.inputMappings)
      : [],
    outputMappings: Array.isArray(binding.outputMappings)
      ? clone(binding.outputMappings)
      : [],
    ...(action ? { failurePolicy: binding.failurePolicy || 'ERROR' } : {})
  }
}

/**
 * 按当前配置契约构造编辑模型，参数传递统一使用 parameterMappings。展示快照只用于中文回显，
 * 运行时仍必须由服务端根据 ID 重新解析并鉴权。普通关系不再使用接口取数
 * 或动作接口，读取时清除这些配置，防止隐藏字段在后续保存中重新启用。
 */
export function normalizeRelatedContent(value = {}, defaults = {}) {
  const empty = createEmptyRelatedContent(defaults)
  const rawConfig = parseConfig(value.config ?? value.configDocument)
  const config = {
    schemaVersion: rawConfig.schemaVersion ?? empty.config.schemaVersion,
    name: rawConfig.name ?? empty.config.name,
    enabled: rawConfig.enabled ?? empty.config.enabled,
    parameterMappings: Array.isArray(rawConfig.parameterMappings) ? clone(rawConfig.parameterMappings) : [],
    ...(rawConfig.entitySnapshots ? { entitySnapshots: clone(rawConfig.entitySnapshots) } : {}),
    source: { ...empty.config.source, ...(rawConfig.source || {}) },
    target: { ...empty.config.target, ...(rawConfig.target || {}) },
    presentation: {
      ...empty.config.presentation,
      ...(rawConfig.presentation || {})
    },
    relation: {
      ...empty.config.relation,
      ...(rawConfig.relation || {}),
      mappings: Array.isArray(rawConfig.relation?.mappings)
        ? clone(rawConfig.relation.mappings)
        : []
    },
    actions: Array.isArray(rawConfig.actions)
      ? [...new Set(rawConfig.actions)]
      : ['VIEW'],
    specialHandling: {
      mode: rawConfig.specialHandling?.mode ?? empty.config.specialHandling.mode,
      interfaceService: normalizeInterfaceBinding(
        rawConfig.specialHandling?.interfaceService
      ),
      actionServices: Array.isArray(rawConfig.specialHandling?.actionServices)
        ? rawConfig.specialHandling.actionServices.map(binding =>
            normalizeInterfaceBinding(binding, true))
        : [],
      customComponent: {
        ...empty.config.specialHandling.customComponent,
        ...(rawConfig.specialHandling?.customComponent || {}),
        props: parseConfig(rawConfig.specialHandling?.customComponent?.props)
      },
      failurePolicy: rawConfig.specialHandling?.failurePolicy ?? empty.config.specialHandling.failurePolicy
    }
  }
  if (!config.actions.length) config.actions = ['VIEW']
  if (!['SAME_RECORD', 'INTERFACE_SERVICE'].includes(config.relation.type)) {
    // 编辑模型保留空结构供共享表单绑定；持久化时会省略普通关系的接口字段。
    config.specialHandling.interfaceService = normalizeInterfaceBinding()
    config.specialHandling.actionServices = []
    config.specialHandling.mode = ['CUSTOM_COMPONENT', 'BOTH'].includes(config.specialHandling.mode)
      ? 'CUSTOM_COMPONENT'
      : 'NONE'
  }
  if (config.relation.type === 'INTERFACE_SERVICE'
    && config.specialHandling.mode === 'NONE') {
    config.specialHandling.mode = 'INTERFACE_SERVICE'
  }
  return {
    ...empty,
    ...value,
    revision: Number(value.revision || 0),
    orderKey: Number(value.orderKey || empty.orderKey),
    anchorType: value.anchorType || empty.anchorType,
    anchorKey: value.anchorKey || empty.anchorKey,
    config
  }
}

/** 构造宿主草稿的保存载荷；普通关系省略接口配置，独立扩展页只写入服务端允许的绑定字段。 */
export function buildRelatedContentPayload(value, ownerType, ownerId) {
  const normalized = normalizeRelatedContent(value, {
    ownerType,
    sourceEntity: value?.config?.source || {}
  })
  const config = clone(normalized.config)
  if (['SAME_RECORD', 'INTERFACE_SERVICE'].includes(config.relation.type)) {
    config.specialHandling.interfaceService = serializeInterfaceBinding(
      normalized.config.specialHandling.interfaceService
    )
    config.specialHandling.actionServices = (
      normalized.config.specialHandling.actionServices || []
    ).map(binding => serializeInterfaceBinding(binding, true))
  } else {
    // 普通关系的空编辑占位不属于保存契约，避免重新携带已移除的扩展能力。
    delete config.specialHandling.interfaceService
    delete config.specialHandling.actionServices
  }
  const result = {
    compositionKey: normalized.compositionKey || undefined,
    anchorType: normalized.anchorType,
    anchorKey: normalized.anchorKey,
    orderKey: normalized.orderKey,
    config
  }
  if (normalized.id) {
    result.expectedRevision = normalized.revision
    result.expectedOwnerRevision = normalized.ownerRevision
  }
  return result
}

export function relatedContentPositionOptions(ownerType) {
  const normalizedOwnerType = String(ownerType || 'FORM').toUpperCase()
  return RELATED_CONTENT_POSITION_OPTIONS.filter(option =>
    (option.value !== 'ROW_EXPAND' || normalizedOwnerType === 'LIST')
      && (option.value !== 'TAB' || normalizedOwnerType === 'FORM'))
}

export function updateRelatedContentAnchor(value, ownerType) {
  const position = value?.config?.presentation?.position
  if (String(ownerType || '').toUpperCase() === 'LIST') {
    if (position === 'ROW_EXPAND') {
      value.anchorType = 'ROW_EXPAND'
      value.anchorKey = 'ROW'
    } else if (['INLINE', 'TAB'].includes(position)) {
      value.anchorType = 'PAGE_SECTION'
      value.anchorKey = 'PAGE_MAIN'
    } else {
      value.anchorType = 'LIST_ACTION'
      value.anchorKey = value.compositionKey
    }
  } else {
    // Tab 必须落在真实 Tab 节点中；即使用户尚未选中节点也保留 FORM_NODE，
    // 让分步校验给出准确提示，而不是悄悄退化成表单末尾卡片。
    if (position === 'TAB') {
      value.anchorType = 'FORM_NODE'
    } else if (position === 'INLINE' && value.anchorKey) {
      value.anchorType = 'FORM_NODE'
    } else {
      value.anchorType = 'OWNER'
      value.anchorKey = ''
      delete value.config.presentation.anchorSide
    }
  }
  return value
}

export function createRelatedContentKey(now = Date.now()) {
  relatedContentKeySequence = (relatedContentKeySequence + 1) % 46656
  return `related_${Number(now).toString(36)}_${relatedContentKeySequence.toString(36)}`
}

export function recommendRelatedContentRelation({
  sourceEntity = {},
  targetEntity = {},
  relations = [],
  sourceFields = [],
  targetFields = []
} = {}) {
  const targetId = String(targetEntity.id || '')
  const targetCode = String(targetEntity.entityCode || '')
  const sourceId = String(sourceEntity.id || '')
  const sourceCode = String(sourceEntity.entityCode || '')
  if ((sourceId && sourceId === targetId)
    || (sourceCode && sourceCode === targetCode)) {
    return { type: 'SAME_RECORD' }
  }
  const relation = relations.find(item =>
    String(item.childEntityId || '') === targetId
    || String(item.childEntityCode || '') === targetCode)
  if (relation) {
    return {
      type: 'ENTITY_RELATION',
      relationCode: relation.relationCode || '',
      relationName: relation.relationName || relation.relationCode || ''
    }
  }
  const sourceReference = sourceFields.find(field =>
    String(field.refEntityId || field.referenceEntityId || '') === targetId
    || String(field.refEntityCode || field.referenceEntityCode || '') === targetCode)
  if (sourceReference) {
    return {
      type: 'REFERENCE_FIELD',
      sourceField: sourceReference.fieldCode,
      sourceFieldName: sourceReference.fieldName || sourceReference.fieldCode
    }
  }
  const reverseReference = targetFields.find(field =>
    String(field.refEntityId || field.referenceEntityId || '') === sourceId
    || String(field.refEntityCode || field.referenceEntityCode || '') === sourceCode)
  if (reverseReference) {
    return {
      type: 'REVERSE_REFERENCE',
      targetField: reverseReference.fieldCode,
      targetFieldName: reverseReference.fieldName || reverseReference.fieldCode
    }
  }
  return { type: 'FIELD_MATCH' }
}

/** 返回分步骤的可定位错误，配置页可直接把用户带到需要修改的位置。 */
export function validateRelatedContent(value, ownerType = 'FORM') {
  const item = normalizeRelatedContent(value, { ownerType })
  const { target, presentation, relation, actions, specialHandling } = item.config
  const errors = []
  const add = (step, field, message) => errors.push({ step, field, message })

  if (!target.entityId) add(1, 'target.entityId', '请选择目标实体')
  if (!target.contentType) add(1, 'target.contentType', '请选择显示表单还是列表')
  if (!target.contentId) add(1, 'target.contentId', '请选择一个已发布的表单或列表')
  const presentationError = relatedContentPresentationError(presentation, target.contentType)
  if (presentationError) add(1, 'presentation', presentationError)
  if (!presentation.position) add(1, 'presentation.position', '请选择显示位置')
  if (!presentation.loadMode) add(1, 'presentation.loadMode', '请选择加载方式')
  if (presentation.anchorSide != null && !['BEFORE', 'AFTER'].includes(presentation.anchorSide)) {
    add(1, 'presentation.anchorSide', '节点相对位置无效')
  }
  if (Object.hasOwn(presentation, 'showBorder') && typeof presentation.showBorder !== 'boolean') {
    add(1, 'presentation.showBorder', '显示边框必须为开关值')
  }
  if (Object.hasOwn(presentation, 'showTitle') && typeof presentation.showTitle !== 'boolean') {
    add(1, 'presentation.showTitle', '显示标题必须为开关值')
  }
  if (String(ownerType).toUpperCase() === 'LIST' && presentation.position === 'TAB') {
    add(1, 'presentation.position', '列表设计暂不支持 Tab 位置，请改用页面区块、行展开、弹窗或抽屉')
  }
  if (String(ownerType).toUpperCase() === 'FORM'
    && presentation.position === 'TAB'
    && (item.anchorType !== 'FORM_NODE' || !item.anchorKey)) {
    add(1, 'anchorKey', '请选择当前表单中的一个 Tab 页作为放置位置')
  }
  if (!relation.type) add(2, 'relation.type', '请选择数据关联方式')
  if (relation.type === 'ENTITY_RELATION' && !relation.relationCode) {
    add(2, 'relation.relationCode', '请选择已有实体关系')
  }
  if (relation.type === 'REFERENCE_FIELD' && !relation.sourceField) {
    add(2, 'relation.sourceField', '请选择当前实体的引用字段')
  }
  if (relation.type === 'REVERSE_REFERENCE' && !relation.targetField) {
    add(2, 'relation.targetField', '请选择目标实体中指向当前实体的字段')
  }
  if (relation.type === 'FIELD_MATCH'
    && (!relation.sourceField || !relation.targetField)) {
    add(2, 'relation.mappings', '请选择当前字段和目标字段')
  }

  if (!actions.length) add(3, 'actions', '请至少选择一种允许的操作')
  if (actions.some(action => !SUPPORTED_ACTIONS.has(action))) {
    add(3, 'actions', '包含不支持的关联内容操作')
  }
  const usesService = ['INTERFACE_SERVICE', 'BOTH'].includes(specialHandling.mode)
  const needsDataService = relation.type === 'INTERFACE_SERVICE'
  const actionServices = Array.isArray(specialHandling.actionServices)
    ? specialHandling.actionServices
    : []
  if (needsDataService && !specialHandling.interfaceService.extensionId) {
    add(4, 'specialHandling.interfaceService.extensionId', '请选择扩展接口')
  }
  if (usesService && !specialHandling.interfaceService.extensionId && !actionServices.length) {
    add(4, 'specialHandling.actionServices', '请至少配置一个数据或动作扩展接口')
  }
  const actionKeys = new Set()
  actionServices.forEach((binding, index) => {
    if (!binding.actionKey) {
      add(4, `specialHandling.actionServices.${index}.actionKey`, '请选择该扩展接口用于哪个页面操作')
    } else if (actionKeys.has(String(binding.actionKey).toUpperCase())) {
      add(4, `specialHandling.actionServices.${index}.actionKey`, '同一个页面操作只能绑定一个扩展接口')
    } else {
      actionKeys.add(String(binding.actionKey).toUpperCase())
    }
    if (!binding.extensionId) {
      add(4, `specialHandling.actionServices.${index}.extensionId`, '请选择动作扩展接口')
    }
    if (!['ERROR', 'PLACEHOLDER', 'HIDE'].includes(binding.failurePolicy)) {
      add(4, `specialHandling.actionServices.${index}.failurePolicy`, '请选择动作失败后的处理方式')
    }
  })
  if (['CUSTOM_COMPONENT', 'BOTH'].includes(specialHandling.mode)
    && !specialHandling.customComponent.name) {
    add(4, 'specialHandling.customComponent.name', '请选择自定义组件')
  }
  if (['CUSTOM_COMPONENT', 'BOTH'].includes(specialHandling.mode)
    && specialHandling.customComponent.name
    && !/^[a-f0-9]{64}$/.test(String(
      specialHandling.customComponent.artifactDigest || ''
    ).trim().toLowerCase())) {
    add(
      4,
      'specialHandling.customComponent.artifactDigest',
      '当前组件版本缺少可发布制品，请重新选择组件版本或联系开发人员完成注册'
    )
  }
  if (!['ERROR', 'PLACEHOLDER', 'HIDE'].includes(specialHandling.failurePolicy)) {
    add(4, 'specialHandling.failurePolicy', '请选择特殊处理失败后的页面表现')
  }
  return {
    valid: errors.length === 0,
    errors,
    firstStep: errors[0]?.step || 1
  }
}

export function describeRelatedContent(value, sourceName = '当前内容') {
  const item = normalizeRelatedContent(value)
  const target = item.config.target
  const source = item.config.source
  const position = optionLabel(
    RELATED_CONTENT_POSITION_OPTIONS,
    item.config.presentation.position
  )
  const targetName = target.contentName
    || (target.contentType === 'FORM' ? '目标表单' : '目标列表')
  return `在${source.entityName || sourceName}中，以${position}方式显示“${targetName}”。`
}

export function describeRelatedContentRelation(value) {
  const relation = normalizeRelatedContent(value).config.relation
  if (relation.type === 'SAME_RECORD') {
    return '目标内容直接使用当前记录，不追加跨实体匹配条件。'
  }
  if (relation.type === 'ENTITY_RELATION') {
    return `通过已有关系“${relation.relationName || relation.relationCode || '未选择'}”查找目标数据。`
  }
  if (relation.type === 'REFERENCE_FIELD') {
    return `当前记录的“${relation.sourceFieldName || relation.sourceField || '未选择'}”用于定位目标数据。`
  }
  if (relation.type === 'REVERSE_REFERENCE') {
    return `目标数据的“${relation.targetFieldName || relation.targetField || '未选择'}”将匹配当前记录。`
  }
  if (relation.type === 'FIELD_MATCH') {
    return `当前记录的“${relation.sourceFieldName || relation.sourceField || '未选择'}”将匹配目标数据的“${relation.targetFieldName || relation.targetField || '未选择'}”。`
  }
  return '目标数据将由所选扩展接口按当前记录上下文查找。'
}

export function describeRelatedContentActions(value) {
  const actions = normalizeRelatedContent(value).config.actions
  return actions.map(action => optionLabel(RELATED_CONTENT_ACTION_OPTIONS, action)).join('、')
}

export function describeRelatedContentSpecial(value) {
  const special = normalizeRelatedContent(value).config.specialHandling
  if (special.mode === 'NONE') return '平台默认能力'
  const parts = []
  if (['INTERFACE_SERVICE', 'BOTH'].includes(special.mode)) {
    if (special.interfaceService.extensionId) {
      parts.push(special.interfaceService.extensionName || '数据扩展接口')
    }
    if (special.actionServices?.length) {
      parts.push(`${special.actionServices.length} 个动作扩展接口`)
    }
  }
  if (['CUSTOM_COMPONENT', 'BOTH'].includes(special.mode)) {
    const label = special.customComponent.displayName || '自定义组件'
    parts.push(`${label} v${Number(special.customComponent.version || 1)}`)
  }
  return parts.join(' + ')
}

export function nextRelatedContentOrderKey(rows = []) {
  const max = rows.reduce((result, row) =>
    Math.max(result, Number(row?.orderKey || 0)), 0)
  return max > 0 ? max + 1000000 : 1000000
}
