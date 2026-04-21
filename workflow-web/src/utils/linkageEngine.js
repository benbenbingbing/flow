/**
 * 表单字段联动引擎
 * 支持显隐控制、值联动、选项联动、计算字段
 */

export const LinkageEngine = {
  /**
   * 解析联动规则
   * @param {string} rule - 联动规则表达式
   * @returns {Object} 解析后的规则对象
   */
  parseRule(rule) {
    if (!rule) return null
    try {
      return JSON.parse(rule)
    } catch (e) {
      console.error('解析联动规则失败:', rule, e)
      return null
    }
  },

  /**
   * 评估条件表达式
   * @param {string} condition - 条件表达式
   * @param {Object} formData - 表单数据
   * @returns {boolean} 条件是否满足
   */
  evaluateCondition(condition, formData) {
    if (!condition) return true
    
    try {
      // 替换变量为实际值
      const expr = condition.replace(/\$\{(\w+)\}/g, (match, key) => {
        const value = formData[key]
        if (typeof value === 'string') {
          return `'${value}'`
        }
        return value ?? 'null'
      })
      
      // 安全评估表达式
      return this.safeEvaluate(expr)
    } catch (e) {
      console.error('评估条件失败:', condition, e)
      return false
    }
  },

  /**
   * 安全评估表达式
   * @param {string} expr - 表达式
   * @returns {boolean} 评估结果
   */
  safeEvaluate(expr) {
    // 白名单：只允许基本的比较和逻辑运算符
    const allowedPattern = /^[\w\s'"\d<>!=&|().+\-*/]+$/
    if (!allowedPattern.test(expr)) {
      console.warn('表达式包含非法字符:', expr)
      return false
    }
    
    try {
      // 使用 Function 构造器安全执行
      return new Function('return ' + expr)()
    } catch (e) {
      console.error('表达式执行失败:', expr, e)
      return false
    }
  },

  /**
   * 计算字段值
   * @param {string} formula - 计算公式
   * @param {Object} formData - 表单数据
   * @returns {number} 计算结果
   */
  calculate(formula, formData) {
    if (!formula) return null
    
    try {
      // 替换变量为实际值
      const expr = formula.replace(/\$\{(\w+)\}/g, (match, key) => {
        const value = parseFloat(formData[key])
        return isNaN(value) ? 0 : value
      })
      
      // 安全计算
      return this.safeCalculate(expr)
    } catch (e) {
      console.error('计算失败:', formula, e)
      return null
    }
  },

  /**
   * 安全计算
   * @param {string} expr - 计算表达式
   * @returns {number} 计算结果
   */
  safeCalculate(expr) {
    // 只允许数字和基本运算符
    const allowedPattern = /^[\d\s.+\-*/()]+$/
    if (!allowedPattern.test(expr)) {
      console.warn('计算公式包含非法字符:', expr)
      return null
    }
    
    try {
      return new Function('return ' + expr)()
    } catch (e) {
      console.error('计算执行失败:', expr, e)
      return null
    }
  },

  /**
   * 获取联动后的选项
   * @param {Array} options - 原始选项
   * @param {Object} linkageConfig - 联动配置
   * @param {Object} formData - 表单数据
   * @returns {Array} 过滤后的选项
   */
  getLinkedOptions(options, linkageConfig, formData) {
    if (!linkageConfig || !options) return options
    
    const { dependsOn, filterRules } = linkageConfig
    const dependValue = formData[dependsOn]
    
    if (!filterRules || !filterRules[dependValue]) {
      return options
    }
    
    const allowedValues = filterRules[dependValue]
    return options.filter(opt => allowedValues.includes(opt.value))
  },

  /**
   * 处理字段显隐
   * @param {Object} field - 字段配置
   * @param {Object} formData - 表单数据
   * @returns {boolean} 是否显示
   */
  shouldShowField(field, formData) {
    if (!field.visibilityRule) return true
    return this.evaluateCondition(field.visibilityRule, formData)
  },

  /**
   * 处理字段禁用
   * @param {Object} field - 字段配置
   * @param {Object} formData - 表单数据
   * @returns {boolean} 是否禁用
   */
  shouldDisableField(field, formData) {
    if (!field.disabledRule) return false
    return this.evaluateCondition(field.disabledRule, formData)
  },

  /**
   * 处理字段必填
   * @param {Object} field - 字段配置
   * @param {Object} formData - 表单数据
   * @returns {boolean} 是否必填
   */
  shouldRequireField(field, formData) {
    if (!field.requiredRule) return field.isRequired === 1
    return this.evaluateCondition(field.requiredRule, formData)
  },

  /**
   * 处理所有联动
   * @param {Array} fields - 所有字段配置
   * @param {Object} formData - 表单数据
   * @returns {Object} 处理后的字段状态和值
   */
  processAllLinkages(fields, formData) {
    const result = {
      visibility: {},  // 显隐状态
      disabled: {},    // 禁用状态
      required: {},    // 必填状态
      values: {},      // 联动值
      options: {}      // 联动选项
    }

    fields.forEach(field => {
      const fieldKey = field.fieldCode || field.fieldKey
      
      // 处理显隐
      result.visibility[fieldKey] = this.shouldShowField(field, formData)
      
      // 处理禁用
      result.disabled[fieldKey] = this.shouldDisableField(field, formData)
      
      // 处理必填
      result.required[fieldKey] = this.shouldRequireField(field, formData)
      
      // 处理计算字段
      if (field.calculationFormula) {
        result.values[fieldKey] = this.calculate(field.calculationFormula, formData)
      }
      
      // 处理选项联动
      if (field.optionsLinkage && field.options) {
        result.options[fieldKey] = this.getLinkedOptions(
          field.options,
          field.optionsLinkage,
          formData
        )
      }
    })

    return result
  },

  /**
   * 获取触发的联动
   * @param {string} changedField - 变化的字段
   * @param {Array} fields - 所有字段
   * @returns {Array} 被触发的字段列表
   */
  getTriggeredLinkages(changedField, fields) {
    return fields.filter(field => {
      // 检查是否有依赖此字段的联动
      if (field.visibilityRule && field.visibilityRule.includes(changedField)) {
        return true
      }
      if (field.disabledRule && field.disabledRule.includes(changedField)) {
        return true
      }
      if (field.requiredRule && field.requiredRule.includes(changedField)) {
        return true
      }
      if (field.calculationFormula && field.calculationFormula.includes(changedField)) {
        return true
      }
      if (field.optionsLinkage && field.optionsLinkage.dependsOn === changedField) {
        return true
      }
      return false
    })
  }
}

export default LinkageEngine
