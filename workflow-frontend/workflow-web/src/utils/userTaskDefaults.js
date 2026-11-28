import { NEW_NODE_OPERATION_PERMISSIONS } from '../shared/process-config/index.js'

/**
 * 新建节点默认要求驳回备注。读取未保存审批配置的节点时，沿用原有的可选备注。
 */
export function createApprovalConfig(rejectRemarkRequired = false) {
  return {
    enabled: true,
    commentLabel: '审批意见',
    options: [
      { value: 'approve', label: '通过', type: 'primary', showComment: true, remarkRequired: false },
      { value: 'reject', label: '驳回', type: 'danger', showComment: true, remarkRequired: rejectRemarkRequired }
    ]
  }
}

/**
 * 在创建命令中持久化用户任务默认配置，随节点一起撤销或重做。
 * 导入不触发创建命令；复制或类型替换时保留已经配置的扩展属性。
 */
export function initializeNewUserTaskDefaults(shape, moddle) {
  const bo = shape?.businessObject
  if (shape?.type !== 'bpmn:UserTask' || shape.labelTarget || !bo) return
  const extensions = bo.extensionElements?.values || []
  const properties = extensions.filter(value => value.$type === 'flowable:Properties')
  const existingNames = new Set(properties.flatMap(group => (group.values || []).map(value => value.name)))
  const defaults = []
  if (!existingNames.has('assigneeConfig') && !existingNames.has('nodeOperationPolicy')) {
    defaults.push(['assigneeConfig', NEW_NODE_OPERATION_PERMISSIONS])
  }
  if (!existingNames.has('approvalConfig')) {
    defaults.push(['approvalConfig', createApprovalConfig(true)])
  }
  if (!defaults.length) return

  const extensionElements = bo.extensionElements || moddle.create('bpmn:ExtensionElements', { values: [] })
  const group = properties[0] || moddle.create('flowable:Properties', { values: [] })
  const newProperties = defaults.map(([name, config]) => {
    const property = moddle.create('flowable:Property', { name, value: JSON.stringify(config) })
    property.$parent = group
    return property
  })
  group.values = [...(group.values || []), ...newProperties]
  if (!properties.length) {
    group.$parent = extensionElements
    extensionElements.values = [...extensions, group]
  }
  extensionElements.$parent = bo
  bo.extensionElements = extensionElements
}
