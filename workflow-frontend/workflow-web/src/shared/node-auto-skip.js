export const AUTO_SKIP_MODE = Object.freeze({
  OFF: 'OFF',
  ALWAYS: 'ALWAYS',
  CONDITIONAL: 'CONDITIONAL'
})

export const AUTO_SKIP_OPERATOR_OPTIONS = Object.freeze([
  { label: '等于 (==)', value: '==' },
  { label: '不等于 (!=)', value: '!=' },
  { label: '大于 (>)', value: '>' },
  { label: '小于 (<)', value: '<' },
  { label: '大于等于 (>=)', value: '>=' },
  { label: '小于等于 (<=)', value: '<=' }
])

const AUTO_SKIP_MODES = new Set(Object.values(AUTO_SKIP_MODE))

/**
 * 读取 Flowable 字符串属性，并兼容旧版前端错误写入的 FormalExpression 对象。
 * 新保存的数据始终使用字符串，避免 XML 被序列化为 "[object Object]"。
 */
export function normalizeSkipExpression(value) {
  if (typeof value === 'string') return value.trim()
  if (!value || typeof value !== 'object') return ''

  let body = value.body ?? value.$body
  if (body == null && typeof value.get === 'function') {
    try {
      body = value.get('body')
    } catch {
      return ''
    }
  }
  return typeof body === 'string' ? body.trim() : ''
}

/**
 * 识别发布端生成的恒真表达式和旧版全局跳过开关。
 * 这两种表达式在运行时都是“始终跳过”，不应误回显为可编辑的条件模式。
 */
export function isAlwaysSkipExpression(value) {
  return /^[#$]\{\s*(?:true|skipNodeEnabled)\s*}$/i.test(
    normalizeSkipExpression(value)
  )
}

/** 将 BPMN/扩展属性组合还原成设计器的互斥三态。 */
export function createAutoSkipForm(skipNode, skipExpression) {
  const expression = normalizeSkipExpression(skipExpression)
  const alwaysSkip = skipNode === true
    || String(skipNode).trim().toLowerCase() === 'true'
    || isAlwaysSkipExpression(expression)

  return {
    skipMode: alwaysSkip
      ? AUTO_SKIP_MODE.ALWAYS
      : expression
        ? AUTO_SKIP_MODE.CONDITIONAL
        : AUTO_SKIP_MODE.OFF,
    // ALWAYS 下的表达式可能是发布端恒真式，也可能是历史冲突残留。
    // 不把它带入编辑态，避免切换到 CONDITIONAL 时静默复活。
    skipExpression: alwaysSkip ? '' : expression
  }
}

/**
 * 应用互斥的三态切换。只要离开当前模式，就不携带隐藏的旧表达式；
 * 返回条件模式时必须由用户明确重新配置条件。
 */
export function transitionAutoSkipMode(form = {}, nextMode) {
  const normalizedMode = AUTO_SKIP_MODES.has(nextMode)
    ? nextMode
    : AUTO_SKIP_MODE.OFF
  if (normalizedMode === form.skipMode) {
    return { ...form, skipMode: normalizedMode }
  }
  return {
    ...form,
    skipMode: normalizedMode,
    skipExpression: ''
  }
}

/**
 * 选择条件组回显来源。XML 表达式决定真实运行行为，因此只要它存在，
 * 即使无法解析也不能回退到可能陈旧的设计期元数据。
 */
export function resolveAutoSkipConditionRoot(expression, expressionRoot, savedRoot) {
  return normalizeSkipExpression(expression) ? expressionRoot : savedRoot
}

/**
 * 把编辑态三态映射为现有运行时契约；skipMode 本身不写入 BPMN。
 * null 表示必须移除旧属性，防止切换模式后残留规则继续生效。
 */
export function serializeAutoSkipForm(form = {}) {
  const expression = normalizeSkipExpression(form.skipExpression)
  if (form.skipMode === AUTO_SKIP_MODE.ALWAYS) {
    return { skipNode: 'true', skipExpression: null }
  }
  if (form.skipMode === AUTO_SKIP_MODE.CONDITIONAL) {
    return { skipNode: 'false', skipExpression: expression || null }
  }
  return { skipNode: null, skipExpression: null }
}

/**
 * 按发布端的受控数据表达式边界做前置校验，避免设计器生成带属性访问、
 * 方法调用或脚本符号的可执行表达式。字符串字面量内容不参与符号检查。
 */
export function validateAutoSkipExpression(expression) {
  const source = normalizeSkipExpression(expression)
  if (!source.startsWith('${') || !source.endsWith('}') || source.length > 1002) {
    return { valid: false, message: '条件表达式必须使用 ${...} 格式，且长度不能超过 1002 个字符' }
  }

  const body = source.slice(2, -1)
  if (!body.trim()) {
    return { valid: false, message: '条件表达式不能为空' }
  }
  const stripped = stripQuotedLiterals(body)
  if (stripped == null) {
    return { valid: false, message: '条件表达式中的字符串引号未闭合' }
  }
  const withoutDecimals = stripped.replace(/(?<=\d)\.(?=\d)/g, '')
  if (/[.\[\]{};,:@?#\\]/.test(withoutDecimals)
      || /->|\)\s*\(/.test(withoutDecimals)
      || /(^|[^=!<>])=($|[^=])|={3,}|!={2,}|>={2,}|<={2,}/.test(withoutDecimals)
      || /\b[A-Za-z_][A-Za-z0-9_]*\s*\(/.test(withoutDecimals)
      || !/^[A-Za-z0-9_\s=!<>&|()+\-*/%]*$/.test(withoutDecimals)) {
    return {
      valid: false,
      message: '条件表达式仅支持变量、比较、算术及 AND/OR 组合，不支持赋值、属性访问、Lambda 或动态调用'
    }
  }
  return { valid: true, message: '' }
}

function stripQuotedLiterals(value) {
  let quote = ''
  let escaped = false
  let result = ''
  for (const char of value) {
    if (quote) {
      if (escaped) escaped = false
      else if (char === '\\') escaped = true
      else if (char === quote) quote = ''
      result += ' '
    } else if (char === "'" || char === '"') {
      quote = char
      result += ' '
    } else {
      result += char
    }
  }
  return quote ? null : result
}
