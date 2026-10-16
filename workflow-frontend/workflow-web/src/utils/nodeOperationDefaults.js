import { NEW_NODE_OPERATION_PERMISSIONS } from '../shared/process-config/index.js'

/**
 * 创建命令执行前为新用户任务写入权限默认值；跟随节点创建一起撤销/重做。
 * 导入不会触发创建命令；复制或替换已有配置的节点保留原设置，避免覆盖历史权限。
 */
export function initializeNewUserTaskPermissions(shape, moddle) {
  const bo = shape?.businessObject
  if (shape?.type !== 'bpmn:UserTask' || shape.labelTarget || !bo) return
  const extensions = bo.extensionElements?.values || []
  const properties = extensions.filter(value => value.$type === 'flowable:Properties')
  if (properties.some(group => (group.values || []).some(value =>
    ['assigneeConfig', 'nodeOperationPolicy'].includes(value.name)))) return
  const extensionElements = bo.extensionElements || moddle.create('bpmn:ExtensionElements', { values: [] })
  const group = properties[0] || moddle.create('flowable:Properties', { values: [] })
  const property = moddle.create('flowable:Property', {
    name: 'assigneeConfig', value: JSON.stringify(NEW_NODE_OPERATION_PERMISSIONS)
  })
  property.$parent = group
  group.values = [...(group.values || []), property]
  if (!properties.length) {
    group.$parent = extensionElements
    extensionElements.values = [...extensions, group]
  }
  extensionElements.$parent = bo
  bo.extensionElements = extensionElements
}
