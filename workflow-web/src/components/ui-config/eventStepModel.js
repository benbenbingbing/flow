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
    if (step.extensionId && !interfaces.some(item =>
      item.extensionId === step.extensionId
    )) {
      return formButtonEventSelected
          ? '表单自定义按钮事件链仅允许无副作用读接口，请重新选择扩展接口'
          : '请选择当前事件可用的扩展接口'
    }
    if (!step.extensionId && !step.outputMapping.length) {
      return '未选择扩展接口的步骤必须配置结果回填'
    }
  }
  return ''
}
