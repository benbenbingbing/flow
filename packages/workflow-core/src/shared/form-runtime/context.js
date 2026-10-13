/**
 * 仅提取业务定位信息，避免把表单数据、组件实例或客户端权限当作运行授权传入。
 * 服务端仍须重新查询任务、根表单和逐层引用关系；配置摘要不参与授权。
 */
export function buildRuntimeContext(context = {}, form = context.form || {}) {
  const allowed = ['taskId', 'processInstanceId', 'nodeId', 'rootFormId', 'rootRecordId', 'rootEntityCode', 'componentPath', 'recordId', 'childRecordId', 'listKey', 'buttonId', 'compositionContext']
  const source = Object.fromEntries(Object.entries({ ...(form.runtimeContext || {}), ...(context.runtimeContext || {}) }).filter(([key]) => allowed.includes(key)))
  for (const key of ['taskId', 'processInstanceId', 'nodeId', 'rootFormId', 'rootRecordId', 'rootEntityCode', 'componentPath', 'listKey', 'buttonId', 'compositionContext']) {
    if (context[key] !== undefined && context[key] !== null && context[key] !== '') source[key] = context[key]
  }
  // 新增行的临时 UI 标识只用于渲染和初始化去重，不得被当作持久化记录定位。
  const currentRecordId = context.row?.isNew ? undefined : context.recordId || context.record?.id
  if (source.rootFormId && source.componentPath?.length) {
    if (currentRecordId) {
      source.childRecordId = currentRecordId
      const path = [...source.componentPath]
      const last = path.at(-1)
      path[path.length - 1] = { nodeKey: typeof last === 'string' ? last : last.nodeKey, recordId: currentRecordId }
      source.componentPath = path
    }
  } else if (currentRecordId) source.recordId = currentRecordId
  return source
}

/** 子表单沿根表单的稳定字段路径加载，不能靠目标表单 ID 越过父级来源。 */
export function childFormRuntimeContext(context = {}, field = {}) {
  const parent = buildRuntimeContext(context)
  const key = field.nodeKey || field.fieldCode || field.fieldKey || field.id
  return {
    ...parent,
    rootFormId: parent.rootFormId || context.form?.id || context.form?.formId,
    rootRecordId: parent.rootRecordId || parent.recordId || context.record?.id || context.recordId,
    rootEntityCode: parent.rootEntityCode || context.entityCode || context.form?.entityCode,
    componentPath: [...(parent.componentPath || []), String(key || '')],
    recordId: parent.recordId || context.record?.id || context.recordId
  }
}
