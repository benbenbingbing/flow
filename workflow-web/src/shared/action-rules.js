export const ACTION_RULE_VERSION = 2

/** 创建“始终显示且可用”的按钮规则，作为新编辑器的唯一数据结构。 */
export function createEmptyActionRule() {
  return {
    version: ACTION_RULE_VERSION,
    visibleWhen: null,
    enabledWhen: null,
    disabledMessage: ''
  }
}

/**
 * 创建常用规则预设。编辑器只接受 GROUP 作为根节点，因此单条件预设也必须
 * 包进 AND 组，避免规则存在但条件行无法显示和编辑。
 */
export function createActionRulePreset(value) {
  const definitions = {
    ALWAYS: () => ({ root: null, message: '' }),
    OWN_DATA: () => ({
      root: group('OR', [
        relation('CURRENT_USER_IS_CREATOR'),
        relation('CURRENT_USER_IS_SUBMITTER')
      ]),
      message: '仅本人数据可以操作'
    }),
    OWN_DRAFT: () => ({
      root: group('AND', [
        group('OR', [
          relation('CURRENT_USER_IS_CREATOR'),
          relation('CURRENT_USER_IS_SUBMITTER')
        ]),
        condition('PROCESS_STATE', 'EQ', 'NOT_STARTED'),
        condition('STATUS_CATEGORY', 'EQ', 'NEW')
      ]),
      message: '仅本人未流转草稿可以操作'
    }),
    OWN_DRAFT_OR_WITHDRAWN: () => ({
      root: group('AND', [
        group('OR', [
          relation('CURRENT_USER_IS_CREATOR'),
          relation('CURRENT_USER_IS_SUBMITTER')
        ]),
        group('OR', [
          group('AND', [
            condition('PROCESS_STATE', 'EQ', 'NOT_STARTED'),
            condition('STATUS_CATEGORY', 'EQ', 'NEW')
          ]),
          condition('STATUS_CATEGORY', 'EQ', 'WITHDRAWN')
        ])
      ]),
      message: '仅本人未流转草稿或已撤回数据可以操作'
    }),
    CURRENT_ASSIGNEE: () => ({
      root: group('AND', [relation('CURRENT_USER_IS_ASSIGNEE')]),
      message: '仅当前任务办理人可以操作'
    }),
    RUNNING: () => ({
      root: group('AND', [condition('PROCESS_STATE', 'EQ', 'RUNNING')]),
      message: '仅流程进行中的数据可以操作'
    }),
    SAME_DEPT: () => ({
      root: group('AND', [relation('CURRENT_USER_SAME_DEPT')]),
      message: '仅本部门数据可以操作'
    }),
    STATUS: () => ({
      root: group('AND', [condition('STATUS_CODE', 'IN', [])]),
      message: '当前数据状态不允许操作'
    })
  }
  return definitions[value]?.() || null
}

/** 将合法的单条件根包装为编辑器可展示的条件组，不改变规则语义。 */
export function toEditableActionRuleRoot(node) {
  if (!node || String(node.type || '').toUpperCase() === 'GROUP') return node || null
  return group('AND', [node])
}

/** 状态下拉切换单值/多值运算符时同步调整值形态，与后端协议保持一致。 */
export function normalizeActionRuleSelectValue(value, operator) {
  if (isSetOperator(operator)) {
    const values = Array.isArray(value) ? value : [value]
    return values.filter(item => String(item ?? '').trim() !== '')
  }
  if (Array.isArray(value)) {
    return value.find(item => String(item ?? '').trim() !== '') ?? ''
  }
  return value ?? ''
}

/** 按运算符校验比较值形态和内容，避免前端通过、发布时才被后端拒绝。 */
export function hasActionRuleComparisonValue(value, operator) {
  const normalizedOperator = String(operator || '').toUpperCase()
  if (['EMPTY', 'NOT_EMPTY'].includes(normalizedOperator)) return true
  if (isSetOperator(normalizedOperator)) {
    if (Array.isArray(value)) {
      return value.length > 0
        && value.every(item => String(item ?? '').trim() !== '')
    }
    return String(value ?? '')
      .split(',')
      .some(item => item.trim() !== '')
  }
  return value !== null
    && value !== undefined
    && !Array.isArray(value)
    && typeof value !== 'object'
    && String(value).trim() !== ''
}

/** 判断规则是否实际限制了按钮显示或启用状态。 */
export function hasActionRuleConditions(rule) {
  return Boolean(rule?.visibleWhen || rule?.enabledWhen)
}

/** 生成设计器中的简短摘要，让显示限制和启用限制可以一眼区分。 */
export function summarizeActionRule(rule) {
  const parts = []
  if (rule?.visibleWhen) parts.push('显示条件')
  if (rule?.enabledWhen) parts.push('启用条件')
  return parts.length ? parts.join(' + ') : '始终显示且可用'
}

function group(logic, children) {
  return { type: 'GROUP', logic, children }
}

function relation(value) {
  return { type: 'RELATION', relation: value }
}

function condition(type, operator, value) {
  return { type, operator, value }
}

function isSetOperator(operator) {
  return ['IN', 'NOT_IN'].includes(String(operator || '').toUpperCase())
}
