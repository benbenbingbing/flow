export const TASK_RETURN_PROPERTY = 'taskReturnConfig'
export const TASK_RETURN_DEFAULT_PROPERTY = 'taskReturnDefault'

export function readTaskReturnConfiguration(value, defaultMode = 'OFF') {
  const config = value ? JSON.parse(value) : {}
  return {
    mode: config.mode || defaultMode,
    targetNodeIds: Array.isArray(config.targetNodeIds) ? config.targetNodeIds : [],
    explicit: Boolean(value)
  }
}

/** 回退设置兼容两种容器。同名设置优先读取新版 Flowable 扩展。 */
export function taskReturnProperty(node, name) {
  const containers = node?.extensionElements?.values || []
  for (const type of ['flowable:Properties', 'camunda:Properties']) {
    for (const container of containers.filter(item => item.$type === type)) {
      const property = container.values?.find(item => item.name === name)
      if (property) return property.value
    }
  }
}

function isSupported(node, isMultiInstance = Boolean(node?.loopCharacteristics)) {
  if (!node || node.async || node.asyncLeave) return false
  return node.$type === 'bpmn:ExclusiveGateway'
    || (node.$type === 'bpmn:UserTask' && !isMultiInstance && !node.isForCompensation
      && (node.outgoing?.length || 0) <= 1
      && !node.$parent?.flowElements?.some(item => item.$type === 'bpmn:BoundaryEvent' && item.attachedToRef === node))
}

// 草稿只展示结构可达的用户任务。真正执行时还需要该实例的历史活动。
export function taskReturnNodeOptions(source, isMultiInstance) {
  if (!supportsTaskReturnSource(source, isMultiInstance)) return []
  const seen = new Set([source.id])
  const pending = [source]
  const options = []
  while (pending.length) {
    const node = pending.shift()
    for (const flow of node.incoming || []) {
      const previous = flow.sourceRef
      if (previous?.$parent !== source.$parent || !isSupported(previous) || seen.has(previous.id)) continue
      seen.add(previous.id)
      if (previous.$type === 'bpmn:UserTask') {
        options.push({ value: previous.id, label: previous.name || previous.id })
      }
      pending.push(previous)
    }
  }
  return options
}

export function validateTaskReturnConfiguration(config, options, supportedSource) {
  if (!config.explicit || config.mode === 'OFF') return ''
  if (!supportedSource || !['HISTORY', 'FIXED'].includes(config.mode)) return '当前节点不支持配置选择节点回退'
  if (config.mode === 'FIXED' && !config.targetNodeIds.length) return '请至少指定一个回退目标'
  const validIds = new Set(options.map(option => option.value))
  if (config.mode === 'FIXED' && config.targetNodeIds.some(id => !validIds.has(id))) {
    return '请更新无效的回退目标'
  }
  return ''
}

export function supportsTaskReturnSource(source, isMultiInstance) {
  if (source?.$type !== 'bpmn:UserTask' || source.$parent?.$type !== 'bpmn:Process' || isMultiInstance) return false
  if (source.$parent.flowElements?.some(item => item.$type === 'bpmn:SubProcess' && item.triggeredByEvent)) return false
  // 多人办理的草稿还未写回画布，使用面板当前的多实例选项计算源节点能力。
  return isSupported(source, isMultiInstance)
}

// 类型替换时 moddle 已复制扩展到新节点，清理副本可让原节点在撤销时保留配置。
export function clearCopiedTaskReturnConfiguration(shape) {
  if (shape?.type === 'bpmn:UserTask') return
  const containers = shape?.businessObject?.extensionElements?.values || []
  for (const container of containers) {
    if (['flowable:Properties', 'camunda:Properties'].includes(container.$type) && container.values) {
      container.values = container.values.filter(property => property.name !== TASK_RETURN_PROPERTY)
    }
  }
}
