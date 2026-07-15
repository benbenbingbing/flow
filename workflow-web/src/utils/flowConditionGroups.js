export function createFlowCondition() {
  return {
    type: 'CONDITION',
    property: '',
    operator: '==',
    value: ''
  }
}

export function createFlowConditionGroup(logic = 'AND', children = [createFlowCondition()]) {
  return {
    type: 'GROUP',
    logic: logic === 'OR' ? 'OR' : 'AND',
    children
  }
}

export function normalizeFlowConditionRoot(value) {
  const normalized = normalizeNode(value)
  if (!normalized) return createFlowConditionGroup()
  return normalized.type === 'GROUP'
    ? normalized
    : createFlowConditionGroup('AND', [normalized])
}

export function serializeFlowConditionConfig(root) {
  return JSON.stringify({
    version: 1,
    root: normalizeFlowConditionRoot(root)
  })
}

export function parseFlowConditionConfig(value) {
  if (!value) return null
  try {
    const config = typeof value === 'string' ? JSON.parse(value) : value
    if (!config?.root) return null
    return normalizeFlowConditionRoot(config.root)
  } catch {
    return null
  }
}

export function buildFlowConditionExpression(root, getFieldType = () => 'string') {
  const body = buildNode(normalizeFlowConditionRoot(root), getFieldType, true)
  return body ? `\${${body}}` : ''
}

export function parseFlowConditionExpression(expression) {
  if (!expression) return null
  let body = String(expression).trim()
  if (body.startsWith('${') && body.endsWith('}')) {
    body = body.slice(2, -1).trim()
  }
  if (!body) return null
  const parsed = parseNode(body)
  return parsed ? normalizeFlowConditionRoot(parsed) : null
}

function normalizeNode(value) {
  if (!value || typeof value !== 'object') return null
  if (value.type === 'GROUP') {
    const children = Array.isArray(value.children)
      ? value.children.map(normalizeNode).filter(Boolean)
      : []
    return createFlowConditionGroup(value.logic, children)
  }
  if (value.type === 'CONDITION' || value.property) {
    return {
      type: 'CONDITION',
      property: String(value.property || ''),
      operator: normalizeOperator(value.operator),
      value: value.value == null ? '' : String(value.value)
    }
  }
  return null
}

function normalizeOperator(value) {
  return ['==', '!=', '>', '<', '>=', '<=', 'contains'].includes(value) ? value : '=='
}

function buildNode(node, getFieldType, root = false) {
  if (!node) return ''
  if (node.type === 'CONDITION') {
    return buildCondition(node, getFieldType)
  }
  const parts = (node.children || [])
    .map(child => buildNode(child, getFieldType, false))
    .filter(Boolean)
  if (!parts.length) return ''
  const body = parts.join(node.logic === 'OR' ? ' || ' : ' && ')
  return root || parts.length === 1 ? body : `(${body})`
}

function buildCondition(condition, getFieldType) {
  if (!condition.property || !condition.operator) return ''
  if (condition.value == null || condition.value === '') return ''

  const fieldType = getFieldType(condition.property)
  const value = formatValue(condition.value, fieldType)
  if (value === '') return ''

  if (condition.operator === 'contains') {
    return `${condition.property}.contains(${value})`
  }
  return `${condition.property} ${condition.operator} ${value}`
}

function formatValue(rawValue, fieldType) {
  const value = String(rawValue).trim()
  if (!value) return ''
  if ((value.startsWith('${') || value.startsWith('#{')) && value.endsWith('}')) {
    return value.slice(2, -1)
  }
  if (fieldType === 'boolean') {
    return value === 'true' ? 'true' : 'false'
  }
  if (fieldType === 'number') {
    return Number.isFinite(Number(value)) ? value : ''
  }
  return `'${value.replaceAll('\\', '\\\\').replaceAll("'", "\\'")}'`
}

function parseNode(expression) {
  const source = stripOuterParentheses(expression.trim())
  const orParts = splitTopLevel(source, '||')
  if (orParts.length > 1) {
    return createParsedGroup('OR', orParts)
  }
  const andParts = splitTopLevel(source, '&&')
  if (andParts.length > 1) {
    return createParsedGroup('AND', andParts)
  }
  return parseCondition(source)
}

function createParsedGroup(logic, parts) {
  const children = parts.map(parseNode).filter(Boolean)
  if (!children.length) return null
  if (children.length === 1) return children[0]
  return createFlowConditionGroup(logic, children)
}

function parseCondition(expression) {
  const source = expression.trim()
  const containsMatch = source.match(/^([A-Za-z_][\w.]*)\.contains\((.*)\)$/s)
  if (containsMatch) {
    return {
      type: 'CONDITION',
      property: containsMatch[1],
      operator: 'contains',
      value: normalizeParsedValue(containsMatch[1], decodeValue(containsMatch[2]))
    }
  }

  const comparisonMatch = source.match(/^([A-Za-z_][\w.]*)\s*(>=|<=|==|!=|>|<)\s*(.+)$/s)
  if (!comparisonMatch) return null
  return {
    type: 'CONDITION',
    property: comparisonMatch[1],
    operator: comparisonMatch[2],
    value: normalizeParsedValue(comparisonMatch[1], decodeValue(comparisonMatch[3]))
  }
}

function normalizeParsedValue(property, value) {
  if (property === 'approved' && value === 'true') return 'approve'
  if (property === 'approved' && value === 'false') return 'reject'
  return value
}

function decodeValue(value) {
  const source = String(value).trim()
  const first = source[0]
  const last = source[source.length - 1]
  if ((first === "'" && last === "'") || (first === '"' && last === '"')) {
    return source
      .slice(1, -1)
      .replaceAll(`\\${first}`, first)
      .replaceAll('\\\\', '\\')
  }
  return source
}

function stripOuterParentheses(expression) {
  let source = expression
  while (isWrappedByParentheses(source)) {
    source = source.slice(1, -1).trim()
  }
  return source
}

function isWrappedByParentheses(expression) {
  if (!expression.startsWith('(') || !expression.endsWith(')')) return false
  let depth = 0
  let quote = ''
  for (let index = 0; index < expression.length; index += 1) {
    const character = expression[index]
    if (quote) {
      if (character === '\\') {
        index += 1
      } else if (character === quote) {
        quote = ''
      }
      continue
    }
    if (character === "'" || character === '"') {
      quote = character
      continue
    }
    if (character === '(') depth += 1
    if (character === ')') depth -= 1
    if (depth === 0 && index < expression.length - 1) return false
    if (depth < 0) return false
  }
  return depth === 0 && !quote
}

function splitTopLevel(expression, operator) {
  const parts = []
  let start = 0
  let depth = 0
  let quote = ''
  for (let index = 0; index < expression.length; index += 1) {
    const character = expression[index]
    if (quote) {
      if (character === '\\') {
        index += 1
      } else if (character === quote) {
        quote = ''
      }
      continue
    }
    if (character === "'" || character === '"') {
      quote = character
      continue
    }
    if (character === '(') {
      depth += 1
      continue
    }
    if (character === ')') {
      depth -= 1
      continue
    }
    if (depth === 0 && expression.startsWith(operator, index)) {
      parts.push(expression.slice(start, index).trim())
      index += operator.length - 1
      start = index + 1
    }
  }
  if (!parts.length) return [expression.trim()]
  parts.push(expression.slice(start).trim())
  return parts.filter(Boolean)
}
