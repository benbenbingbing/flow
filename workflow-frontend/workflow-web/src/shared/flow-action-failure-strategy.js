/** 原内置策略保留原顺序和语义，自定义策略只在动作失败后参与处理。 */
export function failurePolicyOptions(mode) {
  const builtins = mode === 'AFTER_COMMIT'
    ? [{ label: '失败自动重试', value: 'RETRY' }, { label: '记录失败后忽略', value: 'IGNORE' }]
    : [{ label: '失败回滚流程', value: 'ROLLBACK' }, { label: '记录失败后继续', value: 'CONTINUE' }]
  return [...builtins, { label: '自定义策略', value: 'CUSTOM' }]
}

/** 将后端有限参数契约转换为现有表单编辑器的 Schema，保留明确的默认值。 */
export function strategyFormSchema(strategy) {
  return (strategy?.configSchema || []).map(field => ({
    ...field,
    options: (field.options || []).map(value => ({ value, label: value }))
  }))
}

export function strategyDefaults(strategy) {
  return Object.fromEntries((strategy?.configSchema || [])
    .filter(field => field.defaultValue != null)
    .map(field => [field.key, field.defaultValue]))
}

/** 不静默改用另一策略；缺失版本、模式冲突和重试能力不足都必须显式处理。 */
export function strategyProblem(strategy, mode, retryable) {
  if (!strategy) return '所选策略版本当前不可用，请重新选择或恢复该版本实现。'
  if (!strategy.supportedExecutionModes?.includes(mode)) {
    const modeLabels = { IN_TRANSACTION: '事务内执行', AFTER_COMMIT: '提交后执行' }
    const supported = (strategy.supportedExecutionModes || []).map(value => modeLabels[value] || value).join('、')
    return supported ? `仅支持${supported}；当前为${modeLabels[mode] || mode}。` : '策略未声明支持当前执行方式。'
  }
  if (mode === 'AFTER_COMMIT' && strategy.possibleDispositions?.includes('RETRY') && retryable === false) {
    return '该策略可能重试，当前动作处理器未声明可安全重试（retryable() 需返回 true）。'
  }
  return ''
}

/** 损坏参数不能回退成空对象保存，以免覆盖已发布配置所依据的业务规则。 */
export function parseStrategyConfig(raw) {
  if (raw == null || raw === '') return {}
  const value = typeof raw === 'string' ? JSON.parse(raw) : raw
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('策略参数必须为 JSON 对象')
  return { ...value }
}
