const EMPTY_VALUE = Object.freeze({
  rawValue: null,
  displayText: '',
  displayItems: [],
  resolution: 'RESOLVED',
  state: 'EMPTY'
})

export function normalizePage(payload, fallbackPageSize = 20) {
  if (Array.isArray(payload)) {
    return {
      records: payload,
      total: payload.length,
      pageNum: 1,
      pageSize: Math.max(payload.length, fallbackPageSize),
      counts: undefined
    }
  }
  const records = payload?.records || payload?.items || payload?.content || []
  return {
    records: Array.isArray(records) ? records : [],
    total: Number(payload?.total ?? payload?.totalElements ?? records.length),
    pageNum: Number(payload?.pageNum ?? payload?.page ?? 1),
    pageSize: Number(payload?.pageSize ?? payload?.size ?? fallbackPageSize),
    counts: payload?.counts
  }
}

export function createVersionDraft(seed = {}) {
  const legacyScenarios = Array.isArray(seed.scenarios) ? seed.scenarios : []
  const triggers = Array.isArray(seed.triggers) && seed.triggers.length
    ? seed.triggers
    : legacyScenarios.map(scenarioToTrigger)
  const scope = seed.snapshotScope || seed.scope || {}
  const root = scope.root || {}
  const relationOptions = seed.relationOptions
    || seed.availableRelations
    || scope.availableRelations
    || []
  return {
    ...seed,
    schemaVersion: Number(seed.schemaVersion || 2),
    entityId: seed.entityId || '',
    entityCode: seed.entityCode || '',
    entityName: seed.entityName || seed.entityCode || '',
    enabled: seed.enabled === true,
    revision: Number(seed.revision || 0),
    status: seed.status || 'UNCONFIGURED',
    activeReleaseVersion: seed.activeReleaseVersion ?? null,
    triggers: triggers.map(normalizeTrigger),
    snapshotScope: {
      root: {
        nodeCode: root.nodeCode || 'ROOT',
        entityCode: root.entityCode || seed.entityCode || '',
        entityName: root.entityName || seed.entityName || seed.entityCode || '',
        fieldMode: root.fieldMode || 'ALL_PUBLISHED',
        fieldCodes: [...(root.fieldCodes || [])]
      },
      relations: (scope.relations || scope.nodes || [])
        .filter(node => node.nodeKind !== 'ROOT' && node.selectorType !== 'ROOT')
        .map(normalizeScopeRelation),
      limits: {
        maxRowsPerRelation: clampNumber(scope.limits?.maxRowsPerRelation, 1, 500, 500),
        maxRowsPerVersion: clampNumber(scope.limits?.maxRowsPerVersion, 1, 2000, 2000),
        maxBytesPerVersion: clampNumber(
          scope.limits?.maxBytesPerVersion,
          1,
          5 * 1024 * 1024,
          5 * 1024 * 1024
        )
      }
    },
    diffPolicy: {
      changedOnlyDefault: seed.diffPolicy?.changedOnlyDefault !== false,
      trackOrder: seed.diffPolicy?.trackOrder === true,
      ignoredFieldCodes: [...(seed.diffPolicy?.ignoredFieldCodes || [])]
    },
    relationOptions: relationOptions.map(normalizeRelationOption),
    fieldOptions: seed.fieldOptions || seed.availableFields || [],
    // V1 的步骤和变更目标必须原样保留，避免 V2 页面保存时丢失旧草稿。
    scenarios: legacyScenarios,
    steps: Array.isArray(seed.steps) ? seed.steps : [],
    targetBindings: Array.isArray(seed.targetBindings) ? seed.targetBindings : []
  }
}

export function serializeVersionDraft(draft) {
  const result = JSON.parse(JSON.stringify(draft || {}))
  delete result.relationOptions
  delete result.fieldOptions
  result.schemaVersion = 2
  result.triggers = (result.triggers || []).map(trigger => ({
    ...trigger,
    sourceTypes: trigger.sourceTypes || [],
    operationTypes: trigger.operationTypes || [],
    businessIntents: trigger.businessIntents || []
  }))
  result.snapshotScope = result.snapshotScope || { root: {}, relations: [] }
  const limits = result.snapshotScope?.limits || {}
  limits.maxRowsPerRelation = clampNumber(
    limits.maxRowsPerRelation,
    1,
    500,
    500
  )
  limits.maxRowsPerVersion = clampNumber(
    limits.maxRowsPerVersion,
    1,
    2000,
    2000
  )
  limits.maxBytesPerVersion = clampNumber(
    limits.maxBytesPerVersion,
    1,
    5 * 1024 * 1024,
    5 * 1024 * 1024
  )
  result.snapshotScope.limits = limits
  result.snapshotScope.relations = (result.snapshotScope.relations || [])
    .map(relation => ({
      ...relation,
      maxRows: clampNumber(
        relation.maxRows,
        1,
        limits.maxRowsPerRelation,
        limits.maxRowsPerRelation
      ),
      filter: normalizeScopeFilter(relation.filter)
    }))
  // V2 数据版本策略不再拥有变更规则、写入步骤和跨实体目标。
  delete result.scenarios
  delete result.steps
  delete result.targetBindings
  return result
}

export function normalizeComparison(payload = {}) {
  if (Array.isArray(payload.nodes)) {
    return {
      ...payload,
      compatibilityMode: payload.compatibilityMode || 'FULL',
      summary: normalizeSummary(payload.summary, payload.nodes),
      nodes: payload.nodes.map(normalizeComparisonNode),
      warnings: payload.warnings || []
    }
  }
  const groups = payload.groups || []
  const sections = groups.map((group, index) => ({
    sectionCode: group.code || `LEGACY_${index}`,
    sectionName: group.name || '历史字段',
    fields: (group.fields || []).map(normalizeDiffField)
  }))
  return {
    ...payload,
    compatibilityMode: 'LEGACY',
    summary: normalizeSummary(payload.summary, [{ formSections: sections }]),
    nodes: [{
      nodeCode: 'ROOT',
      nodeKind: 'ROOT',
      name: payload.entityName || '主记录',
      oldName: payload.entityName || '主记录',
      newName: payload.entityName || '主记录',
      formSections: sections,
      rowChanges: [],
      counts: countChanges(sections.flatMap(section => section.fields))
    }],
    warnings: [
      ...(payload.warnings || []),
      '当前比较来自历史 V1 快照，仅展示可可靠识别的字段差异。'
    ]
  }
}

export function normalizeSnapshot(payload = {}) {
  if (Array.isArray(payload.nodes)) {
    return {
      ...payload,
      nodes: payload.nodes.map(normalizeSnapshotNode)
    }
  }
  if (payload.snapshot && Array.isArray(payload.datasets)) {
    const rootDocument = payload.snapshot || {}
    return {
      ...payload,
      nodes: [
        normalizeSnapshotNode({
          ...rootDocument,
          nodeCode: 'ROOT',
          nodeKind: 'ROOT',
          entityName: rootDocument.entity?.entityName
            || rootDocument.entityName
            || payload.entityName
            || '主记录'
        }, 0),
        ...payload.datasets.map((dataset, index) =>
          normalizeSnapshotNode(dataset, index + 1))
      ]
    }
  }
  if (Array.isArray(payload.datasets)) {
    return {
      ...payload,
      nodes: payload.datasets.map(normalizeSnapshotNode)
    }
  }
  const fields = payload.snapshot?.fields || payload.fields || []
  const grouped = new Map()
  fields.forEach((field) => {
    const code = field.group || 'BUSINESS'
    if (!grouped.has(code)) grouped.set(code, [])
    grouped.get(code).push(normalizeSnapshotField(field))
  })
  return {
    ...payload,
    compatibilityMode: 'LEGACY',
    nodes: [{
      nodeCode: 'ROOT',
      nodeKind: 'ROOT',
      name: payload.entityName || '主记录',
      formSections: [...grouped.entries()].map(([code, sectionFields]) => ({
        sectionCode: code,
        sectionName: legacyGroupName(code),
        fields: sectionFields
      })),
      rows: []
    }]
  }
}

export function normalizeDiffField(field = {}) {
  const oldName = field.oldFieldName || field.oldLabel || field.fieldName || field.fieldLabel || field.fieldCode
  const newName = field.newFieldName || field.newLabel || field.fieldName || field.fieldLabel || field.fieldCode
  return {
    ...field,
    fieldCode: field.fieldCode || field.code || '',
    oldFieldName: oldName,
    newFieldName: newName,
    label: field.displayLabel || (oldName === newName ? newName : `${newName || oldName}（原：${oldName || '-'}）`),
    oldValue: legacyFrozenValue(field.oldValue, field.oldDisplayValue),
    newValue: legacyFrozenValue(field.newValue, field.newDisplayValue),
    changeType: normalizeChangeType(field.changeType || field.valueChangeType),
    displayChanged: field.displayChanged === true,
    schemaChanges: field.schemaChanges || []
  }
}

export function normalizeFrozenValue(value, state) {
  if (value == null && !state) return { ...EMPTY_VALUE }
  if (value && typeof value === 'object' && !Array.isArray(value)
    && ('rawValue' in value || 'displayText' in value || 'displayItems' in value || 'state' in value)) {
    return {
      rawValue: value.rawValue ?? null,
      displayText: value.displayText ?? value.label ?? '',
      displayItems: Array.isArray(value.displayItems) ? value.displayItems : [],
      resolution: value.resolution || 'RESOLVED',
      state: value.state || state || inferValueState(value.rawValue)
    }
  }
  return {
    rawValue: value,
    displayText: '',
    displayItems: [],
    resolution: 'RAW_FALLBACK',
    state: state || inferValueState(value)
  }
}

export function frozenValueText(value) {
  const frozen = normalizeFrozenValue(value)
  const stateText = {
    EMPTY: '未填写',
    NOT_CAPTURED: '未采集',
    FIELD_MISSING: '字段不存在',
    NOT_COMPARABLE: '不可比较'
  }[frozen.state]
  if (stateText) return stateText
  if (frozen.displayText !== '') return String(frozen.displayText)
  if (frozen.displayItems.length) {
    return frozen.displayItems
      .map(item => item?.label ?? item?.displayText ?? item?.value)
      .filter(item => item !== undefined && item !== null && item !== '')
      .join('、') || '未填写'
  }
  return safeRawText(frozen.rawValue)
}

export function isChanged(changeType) {
  return normalizeChangeType(changeType) !== 'UNCHANGED'
}

function normalizeTrigger(trigger = {}) {
  return {
    ...trigger,
    triggerCode: trigger.triggerCode || trigger.scenarioCode || '',
    triggerName: trigger.triggerName || trigger.scenarioName || '',
    triggerType: trigger.triggerType || 'ROOT_MUTATION',
    relationCode: trigger.relationCode || '',
    sourceTypes: [...(trigger.sourceTypes || [])],
    operationTypes: [...(trigger.operationTypes || [])],
    businessIntents: [...(trigger.businessIntents || [])],
    condition: trigger.condition || {},
    versionTitleTemplate: trigger.versionTitleTemplate || 'V${versionNo} ${triggerName}',
    priority: Number(trigger.priority || 0),
    enabled: trigger.enabled !== false
  }
}

function scenarioToTrigger(scenario) {
  return normalizeTrigger({
    ...scenario,
    triggerCode: scenario.scenarioCode,
    triggerName: scenario.scenarioName,
    triggerType: 'ROOT_MUTATION'
  })
}

function normalizeScopeRelation(relation = {}) {
  return {
    ...relation,
    nodeCode: relation.nodeCode || relation.relationCode || '',
    relationCode: relation.relationCode || relation.nodeCode || '',
    relationName: relation.relationName || relation.name || relation.relationCode || '',
    childEntityCode: relation.childEntityCode || relation.entityCode || '',
    childEntityName: relation.childEntityName || relation.entityName || '',
    fieldMode: relation.fieldMode || 'ALL_PUBLISHED',
    fieldCodes: [...(relation.fieldCodes || [])],
    filter: normalizeScopeFilter(relation.filter),
    maxRows: clampNumber(relation.maxRows, 1, 500, 500),
    enabled: relation.enabled !== false
  }
}

function normalizeScopeFilter(filter = {}) {
  return {
    ...filter,
    logic: filter.logic === 'ANY' ? 'ANY' : 'ALL',
    conditions: (Array.isArray(filter.conditions) ? filter.conditions : []).map(condition => ({
      ...condition,
      operator: normalizeFilterOperator(condition.operator)
    }))
  }
}

function normalizeFilterOperator(operator) {
  return ({
    GE: 'GTE',
    LE: 'LTE',
    IS_NULL: 'EMPTY',
    IS_NOT_NULL: 'NOT_EMPTY'
  })[operator] || operator || 'EQ'
}

function clampNumber(value, min, max, fallback) {
  const number = Number(value)
  if (!Number.isFinite(number)) return fallback
  return Math.min(max, Math.max(min, number))
}

function normalizeRelationOption(relation = {}) {
  return {
    ...relation,
    relationCode: relation.relationCode || relation.code || '',
    relationName: relation.relationName || relation.name || relation.relationCode || relation.code || '',
    childEntityCode: relation.childEntityCode || relation.entityCode || '',
    childEntityName: relation.childEntityName || relation.entityName || relation.childEntityCode || '',
    fields: relation.fields || relation.fieldOptions || []
  }
}

function normalizeComparisonNode(node = {}, index = 0) {
  const rawSections = node.formSections || node.presentation?.sections || node.sections || []
  const formSections = rawSections.map((section, sectionIndex) => ({
    ...section,
    sectionCode: section.sectionCode || section.code || `SECTION_${sectionIndex}`,
    sectionName: section.sectionName || section.name || '基本信息',
    fields: (section.fields || []).map(normalizeDiffField)
  }))
  const rawCounts = node.rowChangeCounts || node.counts || {}
  const rowChanges = normalizePage(node.rowChanges || node.rows || [], 20)
  if (!rowChanges.total && Number(rawCounts.total || 0)) {
    rowChanges.total = Number(rawCounts.total)
  }
  return {
    ...node,
    nodeCode: node.nodeCode || node.relationCode || `NODE_${index}`,
    nodeKind: node.nodeKind || (node.relationCode ? 'RELATION' : 'ROOT'),
    name: node.displayName || node.newRelationName || node.oldRelationName || node.entityName || node.name || '关联数据',
    oldName: node.oldRelationName || node.oldEntityName || node.name || '',
    newName: node.newRelationName || node.newEntityName || node.name || '',
    formSections,
    rowChanges: rowChanges.records.map(normalizeRowChange),
    rowPage: rowChanges,
    counts: Object.keys(rawCounts).length ? rawCounts : countChanges([
      ...formSections.flatMap(section => section.fields), ...rowChanges.records
    ])
  }
}

function normalizeSnapshotNode(node = {}, index = 0) {
  const presentation = node.presentation || {}
  const rawSections = node.formSections || presentation.sections || node.sections || []
  const valueMap = node.values || {}
  let formSections = rawSections.map((section, sectionIndex) => ({
    ...section,
    sectionCode: section.sectionCode || section.code || `SECTION_${sectionIndex}`,
    sectionName: section.sectionName || section.name || '基本信息',
    fields: (section.fields || []).map(field => normalizeSnapshotField({
      ...field,
      value: Object.hasOwn(field, 'value')
        ? field.value
        : valueMap[field.fieldCode || field.code]
    }))
  }))
  if (!formSections.length && Array.isArray(presentation.fields)) {
    formSections = [{
      sectionCode: 'BASIC',
      sectionName: '基本信息',
      fields: presentation.fields.map(field => normalizeSnapshotField({
        ...field,
        value: Object.hasOwn(field, 'value')
          ? field.value
          : valueMap[field.fieldCode || field.code]
      }))
    }]
  }
  const rowPage = normalizePage(node.rows || [], 20)
  if (!rowPage.total && Number(node.rowCount || 0)) {
    rowPage.total = Number(node.rowCount)
  }
  return {
    ...node,
    nodeCode: node.nodeCode || node.relationCode || `NODE_${index}`,
    nodeKind: node.nodeKind || (node.relationCode ? 'RELATION' : 'ROOT'),
    name: node.relationName
      || node.entity?.entityName
      || node.entityName
      || node.name
      || '关联数据',
    formSections,
    rows: rowPage.records.map(row => normalizeSnapshotRow(row, formSections)),
    rowPage
  }
}

function normalizeSnapshotField(field = {}) {
  return {
    ...field,
    fieldCode: field.fieldCode || field.code || '',
    label: field.fieldName || field.fieldLabel || field.label || field.fieldCode || '',
    value: legacyFrozenValue(field.value, field.displayValue, field.valueState)
  }
}

function normalizeRowChange(row = {}) {
  const sections = row.formSections || row.sections || []
  return {
    ...row,
    recordId: row.recordId || row.id || '',
    title: row.newRecordTitle || row.oldRecordTitle || row.newTitle || row.oldTitle || row.title || row.recordId || row.id || '关联记录',
    oldTitle: row.oldRecordTitle || row.oldTitle || row.title || '',
    newTitle: row.newRecordTitle || row.newTitle || row.title || '',
    changeType: normalizeChangeType(row.changeType),
    moved: row.moved === true || normalizeChangeType(row.changeType) === 'MOVED',
    formSections: sections.map((section, index) => ({
      ...section,
      sectionCode: section.sectionCode || section.code || `ROW_SECTION_${index}`,
      sectionName: section.sectionName || section.name || '详细信息',
      fields: (section.fields || []).map(normalizeDiffField)
    }))
  }
}

function normalizeSnapshotRow(row = {}, presentationSections = []) {
  const explicitSections = row.formSections || row.sections || []
  const valueMap = row.values || {}
  const sections = explicitSections.length
    ? explicitSections
    : presentationSections.map(section => ({
        ...section,
        fields: (section.fields || []).map(field => ({
          ...field,
          value: valueMap[field.fieldCode]
        }))
      }))
  return {
    ...row,
    recordId: row.recordId || row.id || '',
    title: row.recordTitle || row.title || row.displayTitle || row.recordId || row.id || '关联记录',
    formSections: sections.map((section, index) => ({
      ...section,
      sectionCode: section.sectionCode || section.code || `ROW_SECTION_${index}`,
      sectionName: section.sectionName || section.name || '详细信息',
      fields: (section.fields || []).map(normalizeSnapshotField)
    }))
  }
}

function legacyFrozenValue(rawValue, displayValue, state) {
  if (rawValue && typeof rawValue === 'object' && !Array.isArray(rawValue)
    && ('rawValue' in rawValue || 'displayText' in rawValue || 'displayItems' in rawValue || 'state' in rawValue)) {
    return normalizeFrozenValue(rawValue, state)
  }
  if (displayValue !== undefined && displayValue !== null && displayValue !== '') {
    return normalizeFrozenValue({
      rawValue,
      displayText: displayValue,
      state: state || inferValueState(rawValue),
      resolution: 'RESOLVED'
    })
  }
  return normalizeFrozenValue(rawValue, state)
}

function normalizeSummary(summary = {}, nodes = []) {
  const fields = nodes.flatMap(node =>
    (node.formSections || []).flatMap(section => section.fields || []))
  const counts = countChanges(fields)
  return {
    dataChangedCount: Number(summary?.dataChangedCount ?? counts.changed),
    displayChangedCount: Number(summary?.displayChangedCount ?? fields.filter(field => field.displayChanged).length),
    schemaChangedCount: Number(summary?.schemaChangedCount ?? fields.filter(field => field.schemaChanges?.length).length),
    addedRowCount: Number(summary?.addedRowCount ?? summary?.added ?? 0),
    removedRowCount: Number(summary?.removedRowCount ?? summary?.removed ?? 0),
    modifiedRowCount: Number(summary?.modifiedRowCount ?? summary?.modified ?? 0),
    movedRowCount: Number(summary?.movedRowCount ?? summary?.moved ?? 0),
    scopeChanged: summary?.scopeChanged === true
  }
}

function countChanges(items = []) {
  return items.reduce((counts, item) => {
    const type = normalizeChangeType(item.changeType)
    if (type === 'ADDED') counts.added += 1
    else if (type === 'REMOVED') counts.removed += 1
    else if (type === 'MODIFIED') counts.modified += 1
    else if (type === 'MOVED') counts.moved += 1
    if (item.moved === true && type !== 'MOVED') counts.moved += 1
    if (type !== 'UNCHANGED' || item.moved === true) counts.changed += 1
    return counts
  }, { added: 0, removed: 0, modified: 0, moved: 0, changed: 0 })
}

function normalizeChangeType(value) {
  const type = String(value || 'UNCHANGED').toUpperCase()
  return ['ADDED', 'REMOVED', 'MODIFIED', 'MOVED', 'UNCHANGED', 'NOT_COMPARABLE'].includes(type)
    ? type
    : 'MODIFIED'
}

function inferValueState(value) {
  return value === null || value === undefined || value === '' ? 'EMPTY' : 'VALUE'
}

function safeRawText(value) {
  if (value === null || value === undefined || value === '') return '未填写'
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  if (Array.isArray(value)) {
    const primitives = value.filter(item => ['string', 'number', 'boolean'].includes(typeof item))
    if (primitives.length === value.length) return primitives.join('、') || '未填写'
    const labels = value.map(item => item?.label || item?.name || item?.title).filter(Boolean)
    return labels.length === value.length ? labels.join('、') : `${value.length} 项数据`
  }
  const preferred = value?.label || value?.name || value?.title || value?.displayText
  return preferred == null ? '结构化数据' : String(preferred)
}

function legacyGroupName(code) {
  return {
    BUSINESS: '业务字段',
    SYSTEM: '系统字段',
    SUBFORM: '子表单',
    RELATION: '关系数据'
  }[code] || code || '历史字段'
}
