/**
 * 表单字段联动引擎
 * 支持显隐控制、值联动、选项联动、计算字段
 */

export const LinkageEngine = {
  /**
   * 从字段中提取联动规则（兼容多种存储方式）
   * @param {Object} field - 字段配置
   * @returns {Object} 合并后的规则对象
   */
  getFieldLinkageRules(field) {
    if (!field || typeof field !== 'object') return {}
    const rules = {}
    const ruleKeys = ['visibilityRule', 'disabledRule', 'requiredRule', 'calculationFormula',
      'calculationPrecision', 'calculationEditable', 'optionsLinkage', 'valueFormula']

    // 1. 优先读取直接挂在字段根属性上的规则
    for (let i = 0; i < ruleKeys.length; i++) {
      const key = ruleKeys[i]
      if (field[key] !== undefined) rules[key] = field[key]
    }

    // 2. 从 linkageRules 对象读取（内存中临时保存）
    if (field.linkageRules && typeof field.linkageRules === 'object') {
      Object.assign(rules, field.linkageRules)
    }

    // 3. 从 componentProps JSON 解析
    if (field.componentProps) {
      try {
        const compProps = typeof field.componentProps === 'string'
          ? JSON.parse(field.componentProps)
          : field.componentProps
        if (compProps && compProps.linkageRules && typeof compProps.linkageRules === 'object') {
          Object.assign(rules, compProps.linkageRules)
        }
      } catch (e) {
        // 忽略解析错误
      }
    }

    return rules
  },

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

    if (!Array.isArray(fields)) return result

    for (let i = 0; i < fields.length; i++) {
      const field = fields[i]
      const fieldKey = field.fieldCode || field.fieldKey || field.fieldId || field.id
      const rules = this.getFieldLinkageRules(field)

      // 处理显隐
      result.visibility[fieldKey] = rules.visibilityRule
        ? this.evaluateCondition(rules.visibilityRule, formData)
        : true

      // 处理禁用
      result.disabled[fieldKey] = rules.disabledRule
        ? this.evaluateCondition(rules.disabledRule, formData)
        : false

      // 处理必填
      if (rules.requiredRule) {
        result.required[fieldKey] = this.evaluateCondition(rules.requiredRule, formData)
      } else {
        result.required[fieldKey] = field.isRequired === 1
      }

      // 处理计算字段
      if (rules.calculationFormula) {
        result.values[fieldKey] = this.calculate(rules.calculationFormula, formData)
      }

      // 处理选项联动
      if (rules.optionsLinkage) {
        const opts = field.options || (field.optionsJson ? JSON.parse(field.optionsJson) : null)
        if (opts) {
          result.options[fieldKey] = this.getLinkedOptions(
            opts,
            rules.optionsLinkage,
            formData
          )
        }
      }
    }

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
      const rules = this.getFieldLinkageRules(field)
      // 检查是否有依赖此字段的联动
      if (rules.visibilityRule && rules.visibilityRule.includes(changedField)) {
        return true
      }
      if (rules.disabledRule && rules.disabledRule.includes(changedField)) {
        return true
      }
      if (rules.requiredRule && rules.requiredRule.includes(changedField)) {
        return true
      }
      if (rules.calculationFormula && rules.calculationFormula.includes(changedField)) {
        return true
      }
      if (rules.valueFormula && rules.valueFormula.includes(changedField)) {
        return true
      }
      if (rules.optionsLinkage && rules.optionsLinkage.dependsOn === changedField) {
        return true
      }
      return false
    })
  }
}

export default LinkageEngine
