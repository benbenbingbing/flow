/**
 * 表单计算引擎
 * 支持字段间计算、日期计算等
 */

// 计算表达式求值
export function evaluateExpression(expression, formData) {
  if (!expression || !formData) return null
  
  try {
    let expr = expression
    
    // 匹配 ${fieldKey} 或 #{fieldKey} 格式的变量
    const variableRegex = /\$\{(\w+)\}|#\{(\w+)\}/g
    expr = expr.replace(variableRegex, (match, p1, p2) => {
      const fieldKey = p1 || p2
      const value = formData[fieldKey]
      
      // 处理数字
      if (typeof value === 'number') {
        return value
      }
      
      // 处理字符串数字
      if (typeof value === 'string' && !isNaN(value) && value !== '') {
        return parseFloat(value)
      }
      
      // 处理日期（转换为时间戳）
      if (value instanceof Date) {
        return value.getTime()
      }
      
      return typeof value === 'string' ? JSON.stringify(value) : 0
    })
    
    return evaluateSafeExpression(expr)
  } catch (e) {
    console.warn('计算表达式求值失败:', expression, e)
    return null
  }
}

function evaluateSafeExpression(expression) {
  const parser = new ExpressionParser(tokenizeExpression(expression))
  const result = parser.parse()
  return Number.isNaN(result) ? null : result
}

function tokenizeExpression(expression) {
  const tokens = []
  let index = 0

  while (index < expression.length) {
    const char = expression[index]
    if (/\s/.test(char)) {
      index++
      continue
    }

    if (/[+\-*/%(),]/.test(char)) {
      tokens.push({ type: char, value: char })
      index++
      continue
    }

    if (char === '"' || char === "'") {
      const quote = char
      let value = ''
      index++
      while (index < expression.length && expression[index] !== quote) {
        if (expression[index] === '\\' && index + 1 < expression.length) {
          value += expression[index + 1]
          index += 2
        } else {
          value += expression[index]
          index++
        }
      }
      if (expression[index] !== quote) {
        throw new Error('字符串未闭合')
      }
      tokens.push({ type: 'string', value })
      index++
      continue
    }

    if (/\d|\./.test(char)) {
      let value = ''
      while (index < expression.length && /[\d.]/.test(expression[index])) {
        value += expression[index]
        index++
      }
      if (!/^\d+(\.\d+)?$|^\.\d+$/.test(value)) {
        throw new Error('数字格式错误')
      }
      tokens.push({ type: 'number', value: Number(value) })
      continue
    }

    if (/[a-zA-Z_]/.test(char)) {
      let value = ''
      while (index < expression.length && /\w/.test(expression[index])) {
        value += expression[index]
        index++
      }
      tokens.push({ type: 'identifier', value })
      continue
    }

    throw new Error(`不支持的字符: ${char}`)
  }

  tokens.push({ type: 'eof', value: null })
  return tokens
}

class ExpressionParser {
  constructor(tokens) {
    this.tokens = tokens
    this.position = 0
  }

  parse() {
    const value = this.parseExpression()
    this.expect('eof')
    return value
  }

  parseExpression() {
    let value = this.parseTerm()
    while (this.match('+') || this.match('-')) {
      const operator = this.previous().type
      const right = this.parseTerm()
      value = operator === '+' ? value + right : value - right
    }
    return value
  }

  parseTerm() {
    let value = this.parseUnary()
    while (this.match('*') || this.match('/') || this.match('%')) {
      const operator = this.previous().type
      const right = this.parseUnary()
      if (operator === '*') value *= right
      if (operator === '/') value /= right
      if (operator === '%') value %= right
    }
    return value
  }

  parseUnary() {
    if (this.match('-')) return -this.parseUnary()
    if (this.match('+')) return this.parseUnary()
    return this.parsePrimary()
  }

  parsePrimary() {
    if (this.match('number') || this.match('string')) {
      return this.previous().value
    }

    if (this.match('identifier')) {
      const functionName = this.previous().value
      this.expect('(')
      const args = []
      if (!this.check(')')) {
        do {
          args.push(this.parseExpression())
        } while (this.match(','))
      }
      this.expect(')')
      return this.callFunction(functionName, args)
    }

    if (this.match('(')) {
      const value = this.parseExpression()
      this.expect(')')
      return value
    }

    throw new Error('表达式格式错误')
  }

  callFunction(functionName, args) {
    const functions = {
      datediff,
      workdaydiff
    }
    const fn = functions[functionName]
    if (!fn) {
      throw new Error(`不支持的函数: ${functionName}`)
    }
    return fn(...args)
  }

  match(type) {
    if (!this.check(type)) return false
    this.position++
    return true
  }

  expect(type) {
    if (this.match(type)) return this.previous()
    throw new Error(`期望 ${type}`)
  }

  check(type) {
    return this.peek().type === type
  }

  peek() {
    return this.tokens[this.position]
  }

  previous() {
    return this.tokens[this.position - 1]
  }
}

// 计算日期差（天数）
export function calculateDateDiff(startDate, endDate) {
  if (!startDate || !endDate) return null
  
  try {
    const start = new Date(startDate)
    const end = new Date(endDate)
    
    if (isNaN(start.getTime()) || isNaN(end.getTime())) {
      return null
    }
    
    const diffTime = end.getTime() - start.getTime()
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24))
    
    return diffDays
  } catch (e) {
    console.warn('日期差计算失败:', e)
    return null
  }
}

// 计算工作日差（简单实现，不考虑节假日）
export function calculateWorkDayDiff(startDate, endDate) {
  if (!startDate || !endDate) return null
  
  try {
    const start = new Date(startDate)
    const end = new Date(endDate)
    
    if (isNaN(start.getTime()) || isNaN(end.getTime())) {
      return null
    }
    
    let workDays = 0
    const current = new Date(start)
    
    while (current <= end) {
      const dayOfWeek = current.getDay()
      // 0 是周日，6 是周六
      if (dayOfWeek !== 0 && dayOfWeek !== 6) {
        workDays++
      }
      current.setDate(current.getDate() + 1)
    }
    
    return workDays
  } catch (e) {
    console.warn('工作日差计算失败:', e)
    return null
  }
}

// 格式化计算结果
export function formatCalcResult(value, fieldType, precision = 2) {
  if (value == null || value === '') return ''
  
  switch (fieldType) {
    case 'NUMBER':
    case 'number':
      return parseFloat(value).toFixed(precision)
    case 'DATE':
    case 'date':
      return new Date(value).toISOString().split('T')[0]
    case 'DATETIME':
    case 'datetime':
      return new Date(value).toISOString().replace('T', ' ').substring(0, 19)
    default:
      return String(value)
  }
}

// 常用计算表达式模板
export const calcExpressionTemplates = [
  { label: '数量 × 单价', value: '${amount} * ${price}', desc: '计算总价' },
  { label: '数值相加', value: '${field1} + ${field2}', desc: '两个字段相加' },
  { label: '数值相减', value: '${field1} - ${field2}', desc: '两个字段相减' },
  { label: '数值相乘', value: '${field1} * ${field2}', desc: '两个字段相乘' },
  { label: '数值相除', value: '${field1} / ${field2}', desc: '两个字段相除' },
  { label: '日期差（天）', value: 'datediff(${startDate}, ${endDate})', desc: '计算两个日期相差天数' },
  { label: '百分比', value: '(${part} / ${total}) * 100', desc: '计算百分比' },
  { label: '折扣后金额', value: '${amount} * ${discount} / 100', desc: '计算折扣价格' }
]

// 日期计算函数（用于表达式中）
export function datediff(start, end) {
  return calculateDateDiff(start, end)
}

// 工作日计算函数
export function workdaydiff(start, end) {
  return calculateWorkDayDiff(start, end)
}
