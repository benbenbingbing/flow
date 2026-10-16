/** 内置动作参数属于事件绑定，不能当作无接口的字段映射步骤处理。 */
export function isBuiltinExcelStep(step) {
  return step?.actionType === 'ENTITY_EXCEL'
}

/** 面向配置人员的摘要；操作参数统一通过 Excel 快捷配置维护。 */
export function builtinExcelSummary(step) {
  const config = step?.actionConfig || {}
  const operation = { IMPORT: '导入', EXPORT: '导出', TEMPLATE: '下载导入模板' }[config.operation] || '未设置操作'
  const scheme = config.schemeRef?.mode === 'EXPLICIT' ? `方案：${config.schemeRef.schemeCode}` : '跟随实体默认方案'
  const scope = { CURRENT_RECORD: '当前已保存记录', SELECTED: '选中记录', FILTERED: '当前筛选结果' }[config.dataScope]
  return [operation, scheme, config.operation === 'EXPORT' ? scope : ''].filter(Boolean).join(' · ')
}

/** 事件步骤的持久化/编辑模型转换，不依赖 Vue、接口或当前 owner。 */
export function parseEventDocument(document, fallback) {
  if (!document) return fallback
  if (typeof document !== 'string') return document
  try {
    return JSON.parse(document)
  } catch {
    return fallback
  }
}

/**
 * 为一个编辑器实例创建行标识生成器，避免多个弹窗共享计数或相互覆盖编辑状态。
 * normalizeStep 保留旧接口坐标供会话内迁移，serializeEventStep 不将这些临时值写回。
 */
export function createEventStepEditor() {
  let rowSequence = 0
  function normalizeStep(step, index) {
    const condition = step.condition || {}
    const operator = ['equals', 'notEquals', 'exists', 'truthy']
      .find(key => Object.prototype.hasOwnProperty.call(condition, key)) || 'equals'
    return {
      ...step,
      // 深拷贝内置参数，事件编辑草稿不能修改列表中原绑定或其他按钮的数据。
      ...(isBuiltinExcelStep(step) ? { actionConfig: JSON.parse(JSON.stringify(step.actionConfig || {})) } : {}),
      rowKey: `step_${++rowSequence}`,
      name: step.name || '',
      strategy: String(step.strategy || 'BEFORE').toUpperCase(),
      extensionId: step.extensionId || '',
      // 仅保留到本次编辑会话，用于把迁移前草稿解析到新接口 ID；序列化不会写回。
      legacyServiceId: step.serviceId || '',
      legacyOperationCode: step.operationCode || '',
      order: Number(step.order ?? index * 10),
      failurePolicy: String(step.failurePolicy || 'STOP').toUpperCase(),
      inputRows: mappingRows(step.inputMapping, 'input'),
      outputRows: mappingRows(step.outputMapping, 'output'),
      conditionPath: condition.path || '',
      conditionOperator: operator,
      conditionValue: condition[operator] ?? '',
      conditionBoolean: Boolean(condition[operator])
    }
  }

  function mappingRows(mapping, mode) {
    if (Array.isArray(mapping)) {
      return mapping.map(row => ({
        rowKey: `mapping_${++rowSequence}`,
        overwrite: 'ALWAYS',
        clearOnEmpty: true,
        transform: 'IDENTITY',
        separator: ',',
        ...row
      }))
    }
    if (!mapping || typeof mapping !== 'object') return []
    return Object.entries(mapping).map(([targetPath, sourcePath]) => ({
      rowKey: `mapping_${++rowSequence}`,
      targetPath,
      sourcePath: typeof sourcePath === 'string' ? sourcePath : '',
      overwrite: 'ALWAYS',
      clearOnEmpty: true,
      transform: 'IDENTITY',
      separator: ',',
      mode
    }))
  }
  return { normalizeStep }
}

function serializeCondition(step) {
  if (!step.conditionPath) return {}
  return {
    path: step.conditionPath,
    [step.conditionOperator]: ['exists', 'truthy'].includes(step.conditionOperator)
      ? step.conditionBoolean
      : step.conditionValue
  }
}

function cleanMappings(rows) {
  return (rows || [])
    .filter(row => row.targetPath && (row.sourcePath || Object.prototype.hasOwnProperty.call(row, 'literal')))
    .map(({ rowKey, ...row }) => row)
}

/** 将当前顺序转换为持久化步骤；保留继承策略、兼容标记及输出覆盖/空值规则。 */
export function serializeEventStep(step, index) {
  if (isBuiltinExcelStep(step)) {
    return {
      stepCode: step.stepCode || 'excel_action', name: step.name || undefined,
      strategy: step.strategy, actionType: 'ENTITY_EXCEL',
      actionConfig: JSON.parse(JSON.stringify(step.actionConfig || {})),
      order: (index + 1) * 10, failurePolicy: step.failurePolicy
    }
  }
  return {
    stepCode: step.stepCode || undefined,
    name: step.name || undefined,
    strategy: step.strategy,
    extensionId: step.extensionId || undefined,
    // 已迁移的历史查询接口仍按原 LIST_QUERY 契约执行，避免旧 Provider 返回事件消息。
    legacyListQuery: step.legacyListQuery === true || undefined,
    order: (index + 1) * 10,
    condition: serializeCondition(step),
    inputMapping: Object.fromEntries(
      cleanMappings(step.inputRows).map(row => [row.targetPath, row.sourcePath])
    ),
    outputMapping: cleanMappings(step.outputRows),
    failurePolicy: step.failurePolicy
  }
}

/** 校验可持久化步骤链；返回首个业务错误，空字符串表示可以提交。 */
export function validateEventStepChain(steps, { inheritanceMode, formButtonExactTarget, formButtonEventSelected, interfaces }) {
  if (formButtonEventSelected && steps.some(step =>
    step.strategy === 'REPLACE'
    && Object.keys(step.condition || {}).length > 0)) {
    return '主处理必须无条件执行，请先清空执行条件'
  }
  if (formButtonExactTarget) {
    const mainStepCount = steps.filter(step => step.strategy === 'REPLACE').length
    if (inheritanceMode === 'REPLACE' && mainStepCount !== 1) {
      return '仅使用当前层时，必须且只能配置一个主处理步骤'
    }
    if (inheritanceMode === 'INHERIT' && mainStepCount > 1) {
      return '当前按钮层最多只能配置一个主处理步骤'
    }
  }
  for (const step of steps) {
    if (isBuiltinExcelStep(step)) {
      if (step.strategy !== 'REPLACE' || step.failurePolicy !== 'STOP') return '内置 Excel 操作必须为主处理，失败时停止执行'
      if (!['IMPORT', 'EXPORT', 'TEMPLATE'].includes(step.actionConfig?.operation)
        || !['DEFAULT', 'EXPLICIT'].includes(step.actionConfig?.schemeRef?.mode)
        || step.actionConfig.schemeRef.mode === 'EXPLICIT' && !step.actionConfig.schemeRef.schemeCode) {
        return '请通过 Excel 操作快捷配置补全操作和方案'
      }
      continue
    }
    if (step.extensionId && !interfaces.some(item =>
      item.extensionId === step.extensionId
    )) {
      return formButtonEventSelected
          ? '表单自定义按钮事件链仅允许无副作用读接口，请重新选择扩展接口'
          : '请选择当前事件可用的扩展接口'
    }
    if (!step.extensionId && !step.outputMapping?.length) {
      return '未选择扩展接口的步骤必须配置结果回填'
    }
  }
  return ''
}
