/**
 * 表单字段联动引擎
 * 支持显隐控制、值联动、选项联动、计算字段
 */
import { evaluateExpression, evaluateLinkageExpression, hasFormContextReference } from './calcEngine.js'
import { isFieldReadonlyForMode, isFieldVisibleForMode } from '../shared/config-runtime/index.js'

/** 将上下文临时加入条件求值输入，业务模型始终只包含真实字段。 */
export function withFormContext(record, contextValues = {}) {
  return Object.fromEntries([
    ...Object.entries(record || {}).filter(([key]) => !key.startsWith('ctx.')),
    ...Object.entries(contextValues).map(([key, value]) => ['ctx.' + key, value])
  ])
}

export function formRuleContext(form, mode, readonly = false, context = {}) {
  const contextValues = context.row ? {} : { ...form?.contextValues, 'form.currentMode': mode }
  return { ...context, mode, readonly, contextValues }
}

function usesContext(source) {
  if (!/\$\{ctx\./.test(source || '')) return false
  try { return hasFormContextReference(source) } catch { return true }
}
import {
  collectFlowConditionProperties,
  evaluateFlowConditionExpression,
  evaluateFlowConditionGroup,
  isFlowConditionGroupComplete,
  parseFlowConditionConfig
} from './flowConditionGroups.js'
import {
  getAttachmentItemRequiredState,
  resolveAttachmentItems
} from '../shared/file-attachment.js'

const CONDITION_RULE_DEFINITIONS = [
  {
    configKey: 'visibilityConditionConfig',
    expressionKey: 'visibilityRule'
  },
  {
    configKey: 'disabledConditionConfig',
    expressionKey: 'disabledRule'
  },
  {
    configKey: 'requiredConditionConfig',
    expressionKey: 'requiredRule'
  }
]

export function formatLinkageConditionLiteral(field, value) {
  const fieldType = String(field?.fieldType || '').toUpperCase()
  const componentType = String(field?.componentType || '').toLowerCase()
  const normalized = String(value ?? '').trim().toLowerCase()

  if (fieldType === 'BOOLEAN' || componentType === 'switch') {
    if (['true', '1'].includes(normalized)) return 'true'
    if (['false', '0'].includes(normalized)) return 'false'
  }
  return JSON.stringify(String(value ?? ''))
}

export function normalizeLegacyBooleanComparisons(expression) {
  return String(expression || '')
    .replace(/\b(true|false)\s*(==|!=)\s*(['"])(true|false)\3/gi, '$1 $2 $4')
    .replace(/(['"])(true|false)\1\s*(==|!=)\s*\b(true|false)\b/gi, '$2 $3 $4')
}

export const LinkageEngine = {
  /**
   * 从字段中提取联动规则（兼容多种存储方式）
   * @param {Object} field - 字段配置
   * @returns {Object} 合并后的规则对象
   */
  getFieldLinkageRules(field) {
    if (!field || typeof field !== 'object') return {}
    const rules = {}
    const ruleKeys = [
      'visibilityConditionConfig', 'visibilityRule',
      'disabledConditionConfig', 'disabledRule',
      'enabledConditionConfig',
      'requiredConditionConfig', 'requiredRule',
      'calculationFormula', 'calculationPrecision', 'calculationEditable',
      'contextCalculation',
      'optionsLinkage', 'valueFormula', 'valueFormulaMode', 'valueMapping', 'attachmentItemRequiredRules'
    ]

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
        if (compProps?.attachmentItemRequiredRules) {
          rules.attachmentItemRequiredRules =
            compProps.attachmentItemRequiredRules
        }
      } catch (e) {
        // 忽略解析错误
      }
    }

    // 自由接口值联动已下线，旧嵌套配置也不能再透传到编辑器和保存载荷。
    delete rules.valueApi
    return rules
  },

  getFieldAttachmentItems(field) {
    return resolveAttachmentItems(field)
  },

  evaluateStructuredCondition(config, formData) {
    const conditionRoot = parseFlowConditionConfig(config)
    return Boolean(
      conditionRoot
      && isFlowConditionGroupComplete(conditionRoot)
      && evaluateFlowConditionGroup(conditionRoot, formData)
    )
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
      return evaluateFlowConditionExpression(
        normalizeLegacyBooleanComparisons(condition),
        formData)
    } catch (e) {
      console.error('评估条件失败:', condition, e)
      return false
    }
  },

  /**
   * 优先评估结构化条件组，配置不可用时回退到旧表达式。
   */
  evaluateLinkageRule(rules, configKey, expressionKey, formData, defaultValue) {
    const conditionRoot = parseFlowConditionConfig(rules?.[configKey])
    if (conditionRoot && isFlowConditionGroupComplete(conditionRoot)) {
      return evaluateFlowConditionGroup(conditionRoot, formData)
    }
    if (rules?.[expressionKey]) {
      return this.evaluateCondition(rules[expressionKey], formData)
    }
    return defaultValue
  },

  /**
   * 安全评估表达式
   * @param {string} expr - 表达式
   * @returns {boolean} 评估结果
   */
  safeEvaluate(expr) {
    return evaluateFlowConditionExpression(expr, {})
  },

  /**
   * 计算字段值
   * @param {string} formula - 计算公式
   * @param {Object} formData - 表单数据
   * @returns {number} 计算结果
   */
  calculate(formula, formData) {
    if (!formula) return null

    // 已有算术公式沿用数值转换；函数、比较和新式字段引用共用值联动解析器。
    if (/\b[A-Za-z_]\w*\s*\(|[$#]\{field\.|#\{|[<>=!"']/.test(formula)) {
      return evaluateLinkageExpression(formula, formData)
    }

    try {
      let expr = formula
      // 兼容裸字段名（如 desc1 + desc2）
      if (!expr.includes('${')) {
        expr = expr.replace(/\b([a-zA-Z_]\w*)\b/g, (match, key) => {
          const value = parseFloat(formData[key])
          return isNaN(value) ? 0 : value
        })
      } else {
        expr = expr.replace(/\$\{(\w+)\}/g, (match, key) => {
          const value = parseFloat(formData[key])
          return isNaN(value) ? 0 : value
        })
      }

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
    
    return evaluateExpression(expr, {})
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
    const rules = this.getFieldLinkageRules(field)
    return this.evaluateLinkageRule(
      rules,
      'visibilityConditionConfig',
      'visibilityRule',
      formData,
      true
    )
  },

  /**
   * 处理字段禁用
   * @param {Object} field - 字段配置
   * @param {Object} formData - 表单数据
   * @returns {boolean} 是否禁用
   */
  shouldDisableField(field, formData) {
    const rules = this.getFieldLinkageRules(field)
    return this.evaluateLinkageRule(
      rules,
      'disabledConditionConfig',
      'disabledRule',
      formData,
      false
    ) || !this.evaluateLinkageRule(rules, 'enabledConditionConfig', '', formData, true)
  },

  /**
   * 处理字段必填
   * @param {Object} field - 字段配置
   * @param {Object} formData - 表单数据
   * @returns {boolean} 是否必填
   */
  shouldRequireField(field, formData) {
    const rules = this.getFieldLinkageRules(field)
    const staticRequired = field.isRequired === 1
      || field.isRequired === true
    return staticRequired || this.evaluateLinkageRule(
      rules,
      'requiredConditionConfig',
      'requiredRule',
      formData,
      false
    )
  },

  /**
   * 处理所有联动
   * @param {Array} fields - 所有字段配置
   * @param {Object} formData - 表单数据
   * @returns {Object} 处理后的字段状态和值
   */
  processAllLinkages(fields, formData, context = {}) {
    const result = {
      visibility: {},  // 显隐状态
      disabled: {},    // 禁用状态
      required: {},    // 必填状态
      attachmentItemRequired: {}, // 附件项必填状态
      values: {},      // 联动值
      contextComputed: {},
      options: {}      // 联动选项
    }

    if (!Array.isArray(fields)) return result
    const conditionData = withFormContext(formData, context.contextValues)



    for (let i = 0; i < fields.length; i++) {
      const field = fields[i]
      const fieldKey = field.fieldCode || field.fieldKey || field.fieldId || field.id
      const rules = this.getFieldLinkageRules(field)

      // 处理显隐
      result.visibility[fieldKey] = this.evaluateLinkageRule(
        rules,
        'visibilityConditionConfig',
        'visibilityRule',
        conditionData,
        true
      )

      // 可编辑条件未满足时与显式禁用采用同一套运行结果。
      result.disabled[fieldKey] = this.shouldDisableField(field, conditionData)

      // 处理必填
      const staticRequired = field.isRequired === 1
        || field.isRequired === true
      const conditionalRequired = this.evaluateLinkageRule(
        rules,
        'requiredConditionConfig',
        'requiredRule',
        conditionData,
        false
      )
      result.required[fieldKey] = staticRequired || conditionalRequired

      const fileItems = this.getFieldAttachmentItems(field)
      result.attachmentItemRequired[fieldKey] =
        getAttachmentItemRequiredState(
          fileItems,
          rules.attachmentItemRequiredRules,
          conditionData,
          (config, data) => result.visibility[fieldKey] !== false
            && this.evaluateStructuredCondition(config, data)
        )

      // 处理值联动（字段映射）
      if (rules.valueMapping) {
        const { sourceField, rules: mappings = [], fallback } = rules.valueMapping
        const sourceValue = formData[sourceField]
        const sourceText = String(sourceValue)
        const mapping = mappings.find(r => String(r.sourceValue) === sourceText)
        if (mapping) {
          result.values[fieldKey] = mapping.targetValue
        } else if (sourceField && fallback?.targetValue != null) {
          // 先保留显式映射的优先级。默认跳过未填写或已清空的来源，避免初始化覆盖目标字段。
          const hasSourceValue = sourceValue != null && sourceText.trim() !== ''
          if (hasSourceValue || fallback.includeEmpty === true) {
            result.values[fieldKey] = fallback.targetValue
          }
        }
      }

      // 初值公式由新增生命周期执行；联动刷新不能覆盖手工修改或重新填入已清空的值。
      const formula = rules.valueFormula || rules.calculationFormula
      if (usesContext(formula) && rules.valueFormulaMode !== 'initial') {
        if (rules.contextCalculation?.modes?.includes(context.mode) && !context.readonly
            && result.visibility[fieldKey] && !result.disabled[fieldKey]
            && isFieldVisibleForMode(field, context.mode) && !isFieldReadonlyForMode(field, context.mode)) {
          result.contextComputed[fieldKey] = true
          result.disabled[fieldKey] = true
          const value = evaluateLinkageExpression(formula, formData, new Date(), context)
          result.values[fieldKey] = value ?? ''
        }
      } else if (rules.valueFormulaMode !== 'initial' && rules.valueFormula && result.values[fieldKey] === undefined) {
        result.values[fieldKey] = evaluateLinkageExpression(rules.valueFormula, formData)
      }

      // 处理计算字段
      if (rules.valueFormulaMode !== 'initial' && rules.calculationFormula && !usesContext(formula) && result.values[fieldKey] === undefined) {
        result.values[fieldKey] = this.calculate(rules.calculationFormula, formData)
      }

      // 处理选项联动
      if (rules.optionsLinkage && rules.optionsLinkage.mode !== 'query') {
        const opts = Array.isArray(field.options) ? field.options : null
        if (opts) {
          result.options[fieldKey] = this.getLinkedOptions(
            opts,
            rules.optionsLinkage,
            conditionData
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
      for (const definition of CONDITION_RULE_DEFINITIONS) {
        const conditionRoot = parseFlowConditionConfig(
          rules[definition.configKey]
        )
        if (conditionRoot && isFlowConditionGroupComplete(conditionRoot)) {
          if (collectFlowConditionProperties(conditionRoot)
            .includes(changedField)) {
            return true
          }
          continue
        }
        if (rules[definition.expressionKey]
          && rules[definition.expressionKey].includes(changedField)) {
          return true
        }
      }
      if (rules.valueFormulaMode !== 'initial'
          && [rules.calculationFormula, rules.valueFormula].some(formula => formula?.includes(changedField))) {
        return true
      }
      if (rules.optionsLinkage && rules.optionsLinkage.dependsOn === changedField) {
        return true
      }
      const attachmentRules = Array.isArray(
        rules.attachmentItemRequiredRules?.items)
        ? rules.attachmentItemRequiredRules.items
        : []
      if (attachmentRules.some(rule => {
        const root = parseFlowConditionConfig(
          rule?.requiredConditionConfig
        )
        return root
          && isFlowConditionGroupComplete(root)
          && collectFlowConditionProperties(root).includes(changedField)
      })) {
        return true
      }
      return false
    })
  }
}

/**
 * 为新增记录补齐空字段的公式初值。由宿主在初始化时调用，常规联动与编辑不会运行。
 * 支持引用同表单的其他初值，按可求值的顺序填充；缺值和无法求值的公式保留原值。
 */
export function applyInitialFormulaDefaults(fields, record, context = {}) {
  const pending = fields.flatMap(field => {
    const key = field.fieldCode || field.fieldKey
    if (field.isDisplayOnly || !key || ['__proto__', 'constructor', 'prototype'].includes(key)) return []
    const rules = LinkageEngine.getFieldLinkageRules(field)
    // 历史版本把初值存在联动规则中；新版本允许初值公式和实时公式各自配置。
    if (field.defaultValueMode && field.defaultValueMode !== 'formula') return []
    const formula = field.defaultFormula
      || (rules.valueFormulaMode === 'initial' ? rules.calculationFormula || rules.valueFormula : '')
    if (!formula || (usesContext(formula) && context.contextProtectedFields?.has(key))) return []
    return [{ key, formula }]
  })
  if (!pending.length) return
  const now = new Date()
  // 布局顺序与公式依赖无关；只有确实写入新值时才继续尝试尚未解析的字段。
  let progressed
  do {
    progressed = false
    for (let index = pending.length - 1; index >= 0; index--) {
      const { key, formula } = pending[index]
      if (record[key] != null && record[key] !== '') {
        pending.splice(index, 1)
        continue
      }
      const value = evaluateLinkageExpression(formula, record, now, context)
      if (value == null || value === '') continue
      record[key] = value
      pending.splice(index, 1)
      progressed = true
    }
  } while (progressed && pending.length)
}

export default LinkageEngine
