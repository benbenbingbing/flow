export function listMetadataFingerprint(config, viewConfig) {
  return JSON.stringify({
    listName: config.listName || '',
    description: config.description || '',
    isDefault: Boolean(config.isDefault),
    customComponent: config.customComponent || '',
    dataScopeMode: config.dataScopeMode || 'INHERIT',
    accessPermissionCode: config.accessPermissionCode || '',
    selectionMode: config.selectionMode || 'NONE',
    selectionValueField: config.selectionValueField || 'id',
    selectionReturnMappingsText: config.selectionReturnMappingsText || '',
    fixedFilterConfig: config.fixedFilterConfig || '',
    contextBindingConfig: config.contextBindingConfig || '',
    viewConfig,
    queryProviderCode: config.queryProviderCode || '',
    queryDataSourceId: config.queryDataSourceId || null
  })
}

export function listMetadataDetailEntries(config, viewConfig) {
  return [
    { key: 'listName', label: '列表设置：列表名称', value: config.listName || '' },
    { key: 'description', label: '列表设置：列表说明', value: config.description || '' },
    { key: 'isDefault', label: '列表设置：默认列表', value: Boolean(config.isDefault) },
    { key: 'dataScopeMode', label: '列表设置：数据范围模式', value: config.dataScopeMode || 'INHERIT' },
    { key: 'accessPermissionCode', label: '列表设置：访问权限码', value: config.accessPermissionCode || '' },
    { key: 'selectionMode', label: '列表设置：选择模式', value: config.selectionMode || 'NONE' },
    { key: 'selectionValueField', label: '列表设置：返回值字段', value: config.selectionValueField || 'id' },
    { key: 'selectionReturnMappingsText', label: '列表设置：返回映射', value: config.selectionReturnMappingsText || '' },
    { key: 'fixedFilterConfig', label: '列表设置：固定条件', value: config.fixedFilterConfig || '' },
    { key: 'contextBindingConfig', label: '列表设置：上下文绑定', value: config.contextBindingConfig || '' },
    { key: 'search.defaultVisibleCount', label: '列表设置：收起时显示条件数', value: viewConfig.search.defaultVisibleCount },
    { key: 'search.collapsible', label: '列表设置：启用查询区折叠', value: viewConfig.search.collapsible },
    { key: 'search.labelWidth', label: '列表设置：查询区标签宽度', value: viewConfig.search.labelWidth },
    { key: 'table.stripe', label: '列表设置：斑马纹', value: viewConfig.table.stripe },
    { key: 'table.border', label: '列表设置：表格边框', value: viewConfig.table.border },
    { key: 'table.showIndex', label: '列表设置：序号列', value: viewConfig.table.showIndex },
    { key: 'table.size', label: '列表设置：表格尺寸', value: viewConfig.table.size },
    { key: 'pagination.pageSize', label: '列表设置：默认每页', value: viewConfig.pagination.pageSize },
    { key: 'pagination.pageSizes', label: '列表设置：分页选项', value: viewConfig.pagination.pageSizes },
    { key: 'customComponent', label: '列表设置：自定义列表组件', value: config.customComponent || '' },
    { key: 'customComponentProps', label: '列表设置：组件参数', value: viewConfig.customComponentProps },
    { key: 'queryProviderCode', label: '列表设置：安全查询提供者', value: config.queryProviderCode || '' },
    { key: 'queryDataSourceId', label: '列表设置：统一查询数据源', value: config.queryDataSourceId || null }
  ]
}

const LIST_BUTTON_DEFAULT_TYPES = Object.freeze({
  create: 'primary',
  exportSelected: 'default',
  exportAll: 'default',
  batchDelete: 'danger',
  view: 'primary',
  edit: 'primary',
  approve: 'warning',
  delete: 'danger'
})

export function getListButtonDefaultType(buttonOrKey) {
  const key = typeof buttonOrKey === 'string'
    ? buttonOrKey
    : buttonOrKey?.key || buttonOrKey?.buttonKey
  return LIST_BUTTON_DEFAULT_TYPES[key] || 'default'
}

export function resolveListButtonType(button = {}) {
  return button.buttonType
    || button.styleType
    || getListButtonDefaultType(button)
}

export function withListButtonTypeDefault(button = {}) {
  return {
    ...button,
    buttonType: resolveListButtonType(button)
  }
}

export function normalizeListActionForSave(button, position) {
  const actionParams = {}
  for (const key of [
    'targetEntityCode', 'targetListKey', 'presentation', 'selectionMode',
    'openListTitle', 'relationKey', 'selectionHandler',
    'targetFormId', 'targetFormMode'
  ]) {
    if (button[key] !== undefined && button[key] !== '') actionParams[key] = button[key]
  }
  return {
    expectedRevision: button.id ? button.revision : null,
    position,
    buttonKey: button.key,
    buttonType: button.type,
    buttonLabel: button.label,
    icon: button.icon || '',
    styleType: resolveListButtonType(button),
    linkMode: button.link === true,
    customMode: button.customMode || '',
    handlerCode: button.customHandler || '',
    permissionCode: button.perm || '',
    enabled: button.enabled !== false,
    unavailableBehavior: button.availabilityRule?.unavailableBehavior || '',
    sortOrder: Number(button.sort || 0),
    orderKey: button.orderKey || (Number(button.sort || 0) + 1) * 1000000,
    actionParams,
    availabilityRule: button.availabilityRule || {},
    templateId: button.templateId || null,
    templateVersion: button.templateVersion || null,
    localOverridesDocument: button.localOverridesDocument || button.localOverrides || null,
    clearFields: button.templateId
      ? []
      : ['templateId', 'templateVersion', 'localOverridesDocument']
  }
}

export function listActionFingerprint(button, position) {
  const normalized = normalizeListActionForSave(button, position)
  delete normalized.expectedRevision
  return JSON.stringify(normalized)
}

export function listActionBaselineKey(button, position) {
  return `${position}:${button.id || button.key || button.buttonKey || 'new'}`
}

export function calculateListActionOrderKey(buttons, index) {
  const previous = buttons.slice(0, index).reverse()
    .map(item => Number(item.orderKey))
    .find(orderKey => Number.isFinite(orderKey) && orderKey > 0)
  const next = buttons.slice(index + 1)
    .map(item => Number(item.orderKey))
    .find(orderKey => Number.isFinite(orderKey) && orderKey > 0)
  if (previous && next && next - previous > 1) {
    return previous + Math.floor((next - previous) / 2)
  }
  if (previous) return previous + 1000000
  if (next) return Math.max(1, Math.floor(next / 2))
  return (index + 1) * 1000000
}

export function describeListPublishChanges(diff) {
  const labels = { ADDED: '新增：', UPDATED: '修改：', MOVED: '移动：', REMOVED: '删除：' }
  const items = (diff.changedItems || []).slice(0, 8)
    .map(item => `${labels[item.changeType] || '修改：'}${item.label || item.id}`)
  if (!items.length) return diff.changedSections?.join('、') || '当前列表草稿'
  const remaining = Math.max(0, (diff.changedItems?.length || 0) - items.length)
  return `${items.join('、')}${remaining ? `等 ${remaining + items.length} 项` : ''}`
}
