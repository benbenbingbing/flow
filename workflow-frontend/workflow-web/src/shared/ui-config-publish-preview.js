/** 发布确认的展示模型：所有数量、分组与提醒均由同一份语义变更生成。 */
const SECTIONS = {
  form: '表单设置', nodes: '表单字段与布局', legacyFields: '表单字段',
  list: '列表设置', fields: '列表列与查询条件', toolbarActions: '工具栏按钮',
  rowActions: '行操作按钮', eventBindings: '事件与联动', viewCompositions: '关联内容'
}
const LABELS = {
  formName: '表单名称', listName: '列表名称', description: '说明', label: '显示名称',
  fieldLabel: '字段名称', fieldName: '字段名称', title: '标题', text: '文本', content: '内容',
  helpText: '帮助说明', placeholder: '输入提示', width: '宽度', minWidth: '最小宽度',
  align: '对齐方式', fixed: '固定位置', orderKey: '排列顺序', sortOrder: '排列顺序',
  parentId: '所在容器', gridSpan: '占用列数', layoutType: '布局方式', labelWidth: '标签宽度',
  showInList: '列表展示', isQuery: '查询条件', queryType: '匹配方式',
  sortable: '是否排序', quickCopy: '快捷复制', maskingEnabled: '内容脱敏', maskPrefixLength: '脱敏前保留位数',
  maskSuffixLength: '脱敏后保留位数', labelMap: '显示文本映射', statusMap: '状态样式映射',
  required: '必填', isRequired: '必填', readonly: '只读', isReadonly: '只读',
  hidden: '隐藏', isHidden: '隐藏', disabled: '禁用', enabled: '启用状态',
  defaultValue: '默认值', validation: '校验规则', validationRules: '校验规则',
  rulesDocument: '校验与联动规则', propsDocument: '控件设置', legacyPropsDocument: '字段设置',
  columnConfig: '列设置', queryConfig: '查询设置', renderConfig: '显示设置',
  viewConfig: '页面设置', selectionConfig: '选择设置', componentProps: '组件参数',
  configDocument: '配置内容', localOverridesDocument: '局部配置',
  pageSize: '每页条数', pageSizes: '可选分页条数', emptyText: '空内容提示',
  showOverflowTooltip: '溢出提示', defaultExpanded: '默认展开', accordion: '手风琴模式',
  showPadding: '内边距', showBorder: '边框', border: '边框', stripe: '斑马纹',
  showIndex: '序号列', size: '尺寸', collapsible: '允许折叠', defaultVisibleCount: '默认显示条件数',
  icon: '图标', confirmText: '确认提示', buttonType: '按钮样式', styleType: '按钮样式',
  formatter: '格式化规则', renderComponent: '显示组件', componentType: '控件类型',
  componentVersion: '组件版本', nodeType: '节点类型', fieldType: '字段类型',
  fieldCode: '字段编码', fieldId: '绑定字段', bindingType: '绑定方式', bindingRef: '绑定对象',
  accessPermissionCode: '访问权限', permissionCode: '操作权限', perm: '操作权限',
  dataScopeMode: '数据范围', fixedFilterConfig: '固定筛选条件',
  dataSourceId: '数据源', dataSourceType: '数据来源', dataSourceConfig: '数据源配置',
  dataSourceBindingsDocument: '数据源绑定', dataSourceOperationCode: '数据源操作',
  queryProviderCode: '查询提供者', queryInterfaceExtensionId: '查询接口', queryOperationCode: '查询操作',
  interfaceExtensionId: '扩展接口', interfaceId: '接口', handlerCode: '执行逻辑', customHandler: '执行逻辑',
  actionCode: '操作行为', actionType: '操作类型', actionParams: '操作参数',
  customComponent: '自定义组件', customComponentProps: '自定义组件参数', customMode: '自定义方式',
  targetFormId: '目标表单', targetFormMode: '打开方式', targetListKey: '目标列表',
  targetEntityCode: '目标实体', linkMode: '关联方式', relationId: '关联关系', relationCode: '关联关系',
  submitMapping: '提交映射', beforeSubmit: '提交前处理', afterSubmit: '提交后处理',
  selectionMode: '选择方式', selectionValueField: '返回值字段', selectionReturnMappingsText: '附加返回字段',
  parameterMapping: '运行参数映射', parameterMappings: '运行参数映射',
  fieldInitializationMapping: '子字段初始化映射', parameterContract: '参数传递契约',
  inputParameterSchema: '输入参数契约', extensionConfig: '扩展配置',
  eventCode: '触发事件', eventType: '触发事件', eventKey: '触发事件', steps: '执行步骤',
  isDefault: '默认入口', rendererMode: '渲染方式', formKey: '表单编码', listKey: '列表编码',
  entityId: '所属实体', formId: '所属表单', nodeKey: '节点编码',
  search: '查询区', table: '表格', pagination: '分页', requiredWhen: '必填条件',
  visibleWhen: '显示条件', readonlyWhen: '只读条件'
}
const CHANGE_LABELS = { ADDED: '新增', MODIFIED: '修改', REMOVED: '移除', MOVED: '移动' }
const PRESENTATION = new Set(('formName listName description label fieldLabel fieldName title text content helpText placeholder width minWidth align fixed orderKey sortOrder gridSpan layoutType labelWidth pageSize pageSizes emptyText showOverflowTooltip defaultExpanded accordion showPadding showBorder border stripe showIndex size collapsible defaultVisibleCount icon confirmText buttonType styleType').split(' '))
// 首次启用时这些标识建立正常绑定，不应把每个新字段都当成风险；后续变更仍须复核。
const INITIAL_IDENTITY = new Set(('id entityId formId listConfigId formKey listKey nodeKey fieldId fieldCode fieldType nodeType bindingType bindingRef componentType renderComponent').split(' '))
const INTERNAL = new Set(('id revision version deleted createdAt updatedAt createTime updateTime createdBy updatedBy configType schemaVersion').split(' '))
const CONTAINERS = new Set(('propsDocument rulesDocument legacyPropsDocument localOverridesDocument viewConfig columnConfig queryConfig renderConfig selectionConfig componentProps configDocument').split(' '))
const CONFIG_PARENTS = new Set([...CONTAINERS, 'search', 'table', 'pagination'])
const MOVE_FIELDS = new Set(['parentId', 'orderKey', 'sortOrder'])
// 列表按钮保存时会把这些可选文本补成空串，运行时将缺省与空串作为同一状态处理。
// 不推广到表单 customMode：其缺省可能表示 event，而显式空串有不同的校验语义。
const OPTIONAL_ACTION_TEXT = new Set(['icon', 'customMode', 'handlerCode', 'customHandler', 'permissionCode', 'perm'])
const JSON_FIELDS = new Set([...CONTAINERS, 'actionParams', 'actionParamsDocument', 'dataSourceConfig', 'dataSourceBindingsDocument', 'availabilityRuleDocument'])
const ENUMS = {
  align: { left: '居左', center: '居中', right: '居右' },
  fixed: { left: '固定左侧', right: '固定右侧' },
  queryType: { EQ: '等于', NE: '不等于', LIKE: '包含', NOT_LIKE: '不包含', GT: '大于', GE: '大于等于', GTE: '大于等于', LT: '小于', LE: '小于等于', LTE: '小于等于', IN: '属于', BETWEEN: '范围', IS_NULL: '为空', IS_NOT_NULL: '非空' },
  selectionMode: { NONE: '不启用', SINGLE: '单选', MULTIPLE: '多选', MULTI: '多选' },
  dataScopeMode: { INHERIT: '继承实体权限', CUSTOM: '自定义范围', ALL: '全部数据' },
  dataSourceType: { ENTITY_FIELD: '实体字段', FIELD_TEMPLATE: '字段组合', INTERFACE: '扩展接口' },
  targetFormMode: { CREATE: '新增', EDIT: '编辑', VIEW: '查看' },
  size: { small: '紧凑', default: '标准', large: '宽松' },
  rendererMode: { STANDARD: '标准表单', CUSTOM: '自定义组件', SCHEMA: '标准表单' }
}

function segments(path) { return String(path || '').split('/').filter(Boolean).map(part => part.replace(/~1/g, '/').replace(/~0/g, '~')) }
function last(path) { return segments(path).at(-1) || '' }
// 映射下的 required、id 等是业务枚举值，不能再按同名控件属性解释或过滤。
function displayMapping(path) {
  const parts = segments(path)
  return parts[0] === 'renderConfig' && ['labelMap', 'statusMap'].includes(parts[1])
    ? { name: parts[1], keys: parts.slice(2) }
    : null
}
function knownConfigParents(path) { return !displayMapping(path) && segments(path).slice(0, -1).every(part => CONFIG_PARENTS.has(part)) }
function movePath(path) { return knownConfigParents(path) && MOVE_FIELDS.has(last(path)) }
function object(value) { return value && typeof value === 'object' && !Array.isArray(value) }
function objectDocument(value) {
  if (object(value)) return value
  if (typeof value === 'string') {
    try { const parsed = JSON.parse(value); return object(parsed) ? parsed : null } catch { /* 不推断无效文档的默认值，交由未知配置提醒。 */ }
  }
  return null
}
function empty(value) {
  if (typeof value === 'string' && /^[\[{]/.test(value.trim())) {
    try { return empty(JSON.parse(value)) } catch { return false }
  }
  return value == null || value === '' || (object(value) && !Object.keys(value).length) || (Array.isArray(value) && !value.length)
}
function enabled(value) { return value === true || value === 1 || value === 'true' || value === '1' }
function changeType(type) { return ({ ADD: 'ADDED', REMOVE: 'REMOVED', MODIFY: 'MODIFIED', UPDATED: 'MODIFIED' })[type] || (CHANGE_LABELS[type] ? type : 'MODIFIED') }
function keyOf(section, id) { return JSON.stringify([section || '', id || '']) }

/** 动态映射保留实际数据和层级，避免枚举键名碰撞导致两个不同值显示成同一开关状态。 */
function mappingValue(value) {
  if (value === undefined) return '未设置'
  if (value === '') return '空文本'
  return value !== null && typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value)
}

/** 对原始类型和值做比较，不用展示文字判断相等，避免把字符串 1、数字 1 或不同空值误合并。 */
function equalValues(left, right) {
  if (left === right) return true
  if (Array.isArray(left) && Array.isArray(right)) return left.length === right.length && left.every((value, index) => equalValues(value, right[index]))
  if (!object(left) || !object(right)) return false
  const keys = Object.keys(left)
  return keys.length === Object.keys(right).length
    && keys.every(key => Object.hasOwn(right, key) && equalValues(left[key], right[key]))
}

/** 仅已声明的配置文档允许解析 JSON；未知业务字段中的 JSON 文本仍是文本。 */
function comparisonValue(value, path) {
  if (typeof value !== 'string' || !knownConfigParents(path) || !JSON_FIELDS.has(last(path))) return value
  try {
    const parsed = JSON.parse(value)
    return object(parsed) || Array.isArray(parsed) ? parsed : value
  } catch { return value }
}

/**
 * 在分组和计数前移除可确认的等价更新；新增、移除、缺值的旧接口以及阻断问题始终保留。
 * 空值归一只适用于已确认的可选字段，不能把未知配置或动态映射中的空文本当成未设置。
 */
function equivalentUpdate(operation) {
  if (operation.legacy || operation.riskLevel === 'BLOCKED'
      || !['UPDATED', 'MODIFIED', 'MODIFY', 'MOVED'].includes(operation.changeType)
      || (!Object.hasOwn(operation, 'beforeValue') && !Object.hasOwn(operation, 'afterValue'))) return false
  const path = operation.path
  const before = comparisonValue(operation.beforeValue, path)
  const after = comparisonValue(operation.afterValue, path)
  if (equalValues(before, after)) return true
  const parts = segments(path)
  const action = ['toolbarActions', 'rowActions'].includes(operation.section) && parts.length === 1
  const optionalText = (action && OPTIONAL_ACTION_TEXT.has(last(path)))
    || (knownConfigParents(path) && last(path) === 'icon')
  const unsetText = value => value == null || value === ''
  if (optionalText && unsetText(before) && unsetText(after)) return true
  if (action && ['actionParams', 'actionParamsDocument'].includes(last(path))) {
    const unsetParams = value => value == null || value === '' || (object(value) && !Object.keys(value).length)
    return unsetParams(before) && unsetParams(after)
  }
  return false
}

/** 展示翻译碰撞时补充原始类型和值，让保留下来的真实变化仍可区分，而不是显示相同文字。 */
function comparisonDisplay(operation) {
  let before = operation.beforeDisplayValue ?? formatPublishValue(operation.beforeValue, operation.path)
  let after = operation.afterDisplayValue ?? formatPublishValue(operation.afterValue, operation.path)
  if (before === after) {
    const describe = value => {
      if (value === undefined) return '未提供（缺省）'
      if (value === null) return '未设置（null）'
      if (typeof value === 'string') return value === '' ? '空文本（""）' : `文本：${JSON.stringify(value)}`
      if (typeof value === 'number') return `数值：${value}`
      if (typeof value === 'boolean') return `布尔值：${value}`
      return `${Array.isArray(value) ? '数组' : '对象'}：${JSON.stringify(value)}`
    }
    before = describe(operation.beforeValue)
    after = describe(operation.afterValue)
  }
  return { before, after }
}

/** 将接口值翻译为可比较的文字；复杂配置也保留具体内容，未知值不伪装为“未变化”。 */
export function formatPublishValue(value, path = '') {
  if (displayMapping(path)) return mappingValue(value)
  const field = knownConfigParents(path) ? last(path) : ''
  if (value === undefined || value === null) return '未设置'
  if (value === '') return field === 'fixed' ? '不固定' : '未设置'
  if (['required', 'isRequired'].includes(field)) return enabled(value) ? '必填' : '非必填'
  if (field === 'showInList') return enabled(value) ? '显示' : '隐藏'
  if (typeof value === 'boolean') return value ? '启用' : '关闭'
  if (['isQuery', 'readonly', 'isReadonly', 'hidden', 'isHidden', 'enabled', 'disabled'].includes(field)
      && [0, 1, '0', '1', 'true', 'false'].includes(value)) return enabled(value) ? '启用' : '关闭'
  if (ENUMS[field]?.[value] !== undefined) return ENUMS[field][value]
  if (['width', 'minWidth', 'labelWidth'].includes(field) && /^\d+(\.\d+)?$/.test(String(value))) return Number(value) === 0 ? '自动' : `${value} px`
  if (Array.isArray(value)) return value.length ? value.map(item => formatPublishValue(item, path)).join('；') : '未设置'
  if (object(value)) return Object.entries(value).map(([key, entry]) => `${LABELS[key] || key}：${formatPublishValue(entry, `${path}/${key}`)}`).join('；') || '未设置'
  if (typeof value === 'string' && /^[\[{]/.test(value.trim())) {
    try { return formatPublishValue(JSON.parse(value), path) } catch { /* 非 JSON 文本（如模板）按原文保留。 */ }
  }
  return String(value)
}

function pathLabel(path) {
  const parts = segments(path)
  if (!parts.length) return '配置项'
  const mapping = displayMapping(path)
  if (mapping) return [LABELS[mapping.name], ...mapping.keys].join(' · ')
  const known = parts.filter(part => LABELS[part])
  // 不把无法识别的技术路径直接放到主列；原路径仍在技术详情中完整保留。
  if (!knownConfigParents(path) || !LABELS[parts.at(-1)]) return known.length && CONTAINERS.has(known[0]) ? `${LABELS[known[0]]} · 其他配置` : '其他配置'
  const labels = known.filter(part => !CONTAINERS.has(part)).map(part => LABELS[part])
  return [...new Set(labels.length ? labels : known.map(part => LABELS[part]))].join(' · ')
}

/** 对发布影响作具体解释。此分级仅用于提醒，不替代服务端 canPublish/blockers 校验。 */
function assess(operation, firstPublish, initialItem) {
  const path = String(operation.path || '')
  const field = last(path)
  const meaningfulInitial = !empty(operation.afterValue) && operation.afterValue !== false && operation.afterValue !== 0
  if (operation.riskLevel === 'BLOCKED') return { reviewRequired: true, impact: operation.reason || '此配置未通过发布检查，请修复后重试。' }
  if (displayMapping(path)) return { reviewRequired: false, impact: '调整列表值的显示文本或状态样式，不修改业务数据。' }
  // 仅在已知配置层级解释字段语义；未来的 xxx/width 并不一定是显示宽度。
  if (!knownConfigParents(path)) return { reviewRequired: true, impact: '包含尚未识别的配置变化，请展开详情核对后再发布。' }
  if (path === '/columnConfig/sortable') return { reviewRequired: false, impact: enabled(operation.afterValue) ? '用户可点击此列表头切换升序、降序和取消排序。' : '此列表头不提供排序入口，列表默认排序仍生效。' }
  if (path === '/columnConfig/quickCopy') return { reviewRequired: false, impact: enabled(operation.afterValue) ? '用户可快捷复制此列显示的内容。' : '此列不提供快捷复制入口。' }
  if (path === '/renderConfig/maskingEnabled') return {
    reviewRequired: !firstPublish || enabled(operation.afterValue),
    impact: enabled(operation.afterValue)
      ? '此列显示和快捷复制的内容将脱敏，请检查隐藏范围。'
      : (firstPublish ? '此列按原内容显示。' : '关闭脱敏后，此列显示和快捷复制可能包含原始敏感内容，请检查访问范围。')
  }
  if (['/renderConfig/maskPrefixLength', '/renderConfig/maskSuffixLength'].includes(path)) {
    // 编辑器会连同关闭的脱敏开关保存默认长度；只有整项新增时才能据此确定尚未生效。
    const initialRender = objectDocument(initialItem?.renderConfig)
    const inactiveInitial = firstPublish && initialRender && !enabled(initialRender.maskingEnabled)
    return { reviewRequired: !inactiveInitial, impact: inactiveInitial
      ? '脱敏未启用，此保留位数暂不影响显示或复制。'
      : '调整脱敏后可见的字符范围，请检查敏感内容是否仍被隐藏。' }
  }
  if (firstPublish && field === 'dataSourceType' && operation.afterValue === 'ENTITY_FIELD') return { reviewRequired: false, impact: '读取此列绑定的实体字段。' }
  if (firstPublish && field === 'rendererMode' && ['STANDARD', 'SCHEMA'].includes(operation.afterValue)) return { reviewRequired: false, impact: '启用标准表单布局。' }
  if (/permission|\bperm\b|dataScope|fixedFilter/i.test(path)) return { reviewRequired: !firstPublish || meaningfulInitial, impact: '影响访问权限或可见数据范围，请检查相关用户能看到的数据。' }
  if (/dataSource|interface|queryProvider|queryOperation|bindingRef|submit|handler|actionCode|actionParams|steps|Mapping|Contract|Schema|extension|customComponent|relation|targetForm|targetList|linkMode/i.test(path)) {
    if (firstPublish && INITIAL_IDENTITY.has(field)) return { reviewRequired: false, impact: '建立此配置项的初始字段绑定。' }
    return { reviewRequired: !firstPublish || meaningfulInitial, impact: '影响数据读取、联动或操作执行，请检查绑定目标、参数和执行结果。' }
  }
  if (/required|validation|rulesDocument/i.test(path)) return {
    reviewRequired: !firstPublish || (['required', 'isRequired'].includes(field) ? enabled(operation.afterValue) : meaningfulInitial),
    impact: ['required', 'isRequired'].includes(field)
      ? (enabled(operation.afterValue) ? '改为必填后，空值可能无法提交，请检查新建及存量记录编辑场景。' : '不再要求此字段必填，请确认符合业务规则。')
      : '影响校验或联动行为，请检查填写和提交场景。'
  }
  if (field === 'showInList') return { reviewRequired: false, impact: enabled(operation.afterValue) ? '此字段将在列表中展示。' : '列表不再展示此字段，业务数据保留。' }
  if (field === 'isQuery') return { reviewRequired: false, impact: enabled(operation.afterValue) ? '用户可使用此字段筛选列表。' : '不再提供此字段的查询条件，业务数据保留。' }
  if (field === 'queryType' || path.includes('queryConfig')) return { reviewRequired: !firstPublish, impact: '影响列表筛选方式，请检查查询条件和结果。' }
  if (/readonly|hidden|visible|disabled|enabled/i.test(path)) return { reviewRequired: !firstPublish || meaningfulInitial, impact: '影响内容显示或可编辑状态，请检查对应入口。' }
  if (MOVE_FIELDS.has(field)) return { reviewRequired: field === 'parentId' && !firstPublish, impact: field === 'parentId' ? '配置项所在容器发生变化，请检查布局及容器内的联动。' : '调整页面中的排列顺序。' }
  if (PRESENTATION.has(field)) return { reviewRequired: false, impact: '调整显示内容或页面布局。' }
  if (firstPublish && INITIAL_IDENTITY.has(field)) return { reviewRequired: false, impact: '启用此配置项的初始设置。' }
  return { reviewRequired: true, impact: LABELS[field] ? `调整${LABELS[field]}，请检查发布后的使用行为。` : '包含尚未识别的配置变化，请展开详情核对后再发布。' }
}

/** 整项增删拆出有意义的属性，跳过内部标识；新增对象里的敏感规则仍需单独检查。 */
function objectDetails(operation) {
  const added = operation.changeType === 'ADDED'
  const value = added ? operation.afterValue : operation.beforeValue
  const section = operation.section
  const removedImpact = section === 'fields' ? '此列及其查询配置将从列表移除，业务数据保留。'
    : ['nodes', 'legacyFields'].includes(section) ? '此字段或布局将从表单移除，业务数据保留；请检查相关联动和提交规则。'
      : '此配置项将停用，请检查相关入口和操作。'
  const complexSection = ['eventBindings', 'viewCompositions', 'toolbarActions', 'rowActions'].includes(section)
  const details = [{ key: '/', label: '配置项', before: added ? '未配置' : '已配置', after: added ? '启用' : '移除',
    reviewRequired: added ? complexSection : section !== 'fields',
    impact: added ? (complexSection ? '新增操作或关联配置，请检查可用范围和执行结果。' : '发布后启用此配置项。') : removedImpact }]
  function visit(entry, prefix) {
    if (!object(entry)) return
    for (const [field, raw] of Object.entries(entry)) {
      const path = `${prefix}/${field}`
      if (knownConfigParents(path) && (INTERNAL.has(field) || INITIAL_IDENTITY.has(field) || MOVE_FIELDS.has(field))) continue
      let item = raw
      if (CONTAINERS.has(field) && typeof item === 'string') {
        try { item = JSON.parse(item) } catch { /* 非法或历史文档必须可见，由服务端发布校验决定是否阻断。 */ }
      }
      // 显示映射整体保留，内部的 id/fieldCode/required 都是数据，不能作为元数据丢弃。
      if (!displayMapping(path) && object(item) && Object.keys(item).length) { visit(item, path); continue }
      if (empty(item)) continue
      const assessment = added ? assess({ ...operation, path, beforeValue: undefined, afterValue: item }, true, value) : { reviewRequired: false, impact: removedImpact }
      details.push({ key: path, label: pathLabel(path), before: added ? '未设置' : formatPublishValue(item, path),
        after: added ? formatPublishValue(item, path) : '移除', ...assessment })
    }
  }
  visit(value, '')
  if (operation.riskLevel === 'BLOCKED') Object.assign(details[0], { reviewRequired: true, impact: operation.reason || '此配置未通过发布检查，请修复后重试。' })
  return details
}

/** 老服务端没有前后值时保留可读条目和风险，明确缺失信息，避免将缺失值误称“未设置”。 */
function legacyOperations(preview) {
  const items = preview.changedItems || []
  const risks = preview.riskItems || []
  const result = []
  const matched = new Set()
  for (const item of items) {
    const related = risks.filter(risk => keyOf(risk.section, risk.itemId) === keyOf(item.section, item.id))
    related.forEach(risk => matched.add(risk))
    const sources = related.length ? related : (item.changedFields?.length ? item.changedFields.map(field => ({ path: `/${field}` })) : [{ path: '/' }])
    for (const risk of sources) result.push({ ...risk, section: item.section, itemId: item.id, itemLabel: item.label,
      changeType: item.changeType, legacy: true })
  }
  for (const risk of risks.filter(risk => !matched.has(risk))) result.push({ ...risk, legacy: true })
  return result
}

/**
 * 构造表单和列表共用的发布确认模型，不修改接口对象。
 * 以分区与稳定 ID 合并属性（禁止按名称或路径合并），计数与筛选共用合并后的条目。
 * 首次发布只降低已知初始设置的噪声，未知路径、敏感规则及服务端阻断保持可见。
 */
export function buildPublishPreview(preview = {}, configType = 'LIST') {
  preview ||= {}
  const firstPublish = preview.firstPublish === true
  const operations = Array.isArray(preview.changeDetails) ? preview.changeDetails : legacyOperations(preview)
  const grouped = new Map()
  operations.forEach((operation, index) => {
    if (equivalentUpdate(operation)) return
    const section = operation.section || (configType === 'FORM' ? 'form' : 'list')
    const key = keyOf(section, operation.itemId ?? `unknown-${index}`)
    let item = grouped.get(key)
    if (!item) {
      item = { key, section, label: operation.itemLabel || ({ fields: '未命名字段', nodes: '未命名控件', legacyFields: '未命名字段' })[section] || SECTIONS[section] || '其他配置',
        previousLabel: operation.previousItemLabel || '', changeType: changeType(operation.changeType), details: [], technicalDetails: [], operations: [] }
      grouped.set(key, item)
    }
    item.operations.push(operation)
    item.technicalDetails.push({ section, itemId: operation.itemId, path: operation.path, changeType: operation.changeType })
    if (operation.legacy) {
      item.details.push({ key: `${operation.path}-${index}`, label: pathLabel(operation.path), before: '当前接口未提供', after: '当前接口未提供',
        reviewRequired: operation.riskLevel !== 'SAFE', impact: `${operation.reason ? `${operation.reason}；` : ''}当前接口未提供前后值，请核对配置；升级服务后可查看完整对比。` })
    } else if (operation.path === '/' && ['ADDED', 'REMOVED'].includes(operation.changeType)) {
      item.details.push(...objectDetails(operation))
    } else {
      item.details.push({ key: operation.path || String(index), label: pathLabel(operation.path),
        // 稀疏排序键等内部值由服务端结合快照换成业务位置；缺失时保留原值，不能凭键值猜序号。
        ...comparisonDisplay(operation), ...assess(operation, firstPublish) })
    }
  })
  const counts = { ADDED: 0, MODIFIED: 0, REMOVED: 0, MOVED: 0 }
  const groups = new Map()
  const reviewItems = []
  for (const item of grouped.values()) {
    if (firstPublish) item.changeType = 'ADDED'
    else if (item.operations.every(op => movePath(op.path)) && !['ADDED', 'REMOVED'].includes(item.changeType)) item.changeType = 'MOVED'
    item.changeLabel = CHANGE_LABELS[item.changeType]
    item.reviewRequired = item.details.some(detail => detail.reviewRequired)
    item.impact = [...new Set(item.details.filter(detail => detail.reviewRequired).map(detail => detail.impact))].join('；') || item.details[0]?.impact || ''
    item.details.sort((a, b) => Number(b.reviewRequired) - Number(a.reviewRequired))
    delete item.operations
    counts[item.changeType]++
    if (item.reviewRequired) reviewItems.push(item)
    if (!groups.has(item.section)) groups.set(item.section, { key: item.section, label: SECTIONS[item.section] || '其他配置', items: [] })
    groups.get(item.section).items.push(item)
  }
  return { firstPublish, total: grouped.size, counts, groups: [...groups.values()], reviewItems,
    equivalentOnly: !firstPublish && operations.length > 0 && grouped.size === 0 }
}
