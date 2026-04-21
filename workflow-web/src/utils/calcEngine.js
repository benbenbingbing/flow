/**
 * 表单计算引擎
 * 支持字段间计算、日期计算等
 */

// 计算表达式求值
export function evaluateExpression(expression, formData) {
  if (!expression || !formData) return null
  
  try {
    // 替换变量为实际值
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
      
      // 其他情况返回 0 或空字符串
      return typeof value === 'string' ? `"${value}"` : 0
    })
    
    // 安全的求值
    // eslint-disable-next-line no-eval
    const result = eval(expr)
    
    return result
  } catch (e) {
    console.warn('计算表达式求值失败:', expression, e)
    return null
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
