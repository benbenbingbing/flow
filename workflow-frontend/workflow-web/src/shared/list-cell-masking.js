import { safeParseConfig } from '@flow/workflow-core/config-runtime'
import { normalizeClipboardText } from './clipboard.js'
import { formatDateValue, formatListFieldValue, getCellValue, isDateFieldCode } from './list-runtime/index.js'

// 列级显示策略独立于扩展 Schema，切换渲染组件后仍使用同一份 renderConfig。
export const listCellMaskingSchema = [
  {
    key: 'maskingEnabled', label: '是否脱敏', type: 'boolean', defaultValue: false,
    description: '开启后将本列按文本脱敏显示，仅影响列表展示和快捷复制，不修改原始数据。'
  },
  {
    key: 'maskPrefixLength', label: '前保留位数', type: 'number', defaultValue: 3, min: 0, precision: 0,
    visibleWhen: { maskingEnabled: true },
    description: '保留开头的字符数，0 表示不保留。'
  },
  {
    key: 'maskSuffixLength', label: '后保留位数', type: 'number', defaultValue: 4, min: 0, precision: 0,
    visibleWhen: { maskingEnabled: true },
    description: '保留末尾的字符数，中间用 * 替换；内容长度不超过前后保留位数之和时全部隐藏。空值仍显示空值文本。'
  }
]

/** 兼容历史数据源中的显示参数；显式 renderConfig 与单元格组件保持相同优先级。 */
export function getListCellRenderConfig(field = {}) {
  return safeParseConfig(field.renderConfig || field.dataSourceConfig)
}

export function isListCellMaskingEnabled(field) {
  return getListCellRenderConfig(field).maskingEnabled === true
}

function normalizeKeepLength(value, fallback) {
  if (value === null || value === undefined || value === '') return fallback
  const number = Number(value)
  return Number.isFinite(number) ? Math.max(0, Math.trunc(number)) : fallback
}

/**
 * 对展示文本按 Unicode 码点保留首尾，返回新文本，不更改记录值。
 * 短文本不能因保留区间重叠而完整露出，因此此时全部隐藏；空值由调用方展示占位符。
 */
export function maskListCellValue(value, config = {}) {
  if (config.maskingEnabled !== true || value === null || value === undefined || value === '') return value
  const chars = Array.from(normalizeClipboardText(value))
  const prefix = normalizeKeepLength(config.maskPrefixLength, 3)
  const suffix = normalizeKeepLength(config.maskSuffixLength, 4)
  if (chars.length <= prefix + suffix) return '*'.repeat(chars.length)
  return chars.slice(0, prefix).join('')
    + '*'.repeat(chars.length - prefix - suffix)
    + (suffix ? chars.slice(-suffix).join('') : '')
}

/** 默认文本组件的标签和数值格式化；脱敏须在此转换之后执行，避免重新映射出原文。 */
export function formatDefaultCellValue(value, field = {}, config = {}) {
  if (value === null || value === undefined || value === '') return config?.emptyText || '-'
  const labelMap = config?.labelMap || {}
  if (labelMap[String(value)]) return labelMap[String(value)]
  const formatter = field?.formatter
  if (formatter && typeof value === 'number') {
    try {
      return value.toFixed(parseInt(formatter.replace(/[^0-9]/g, '')) || 2)
    } catch {
      return value
    }
  }
  return value
}

/** 与日期组件共用格式化逻辑，让脱敏前后的保留位数均针对用户看到的日期文本。 */
export function formatDateCellValue(value, field = {}, config = {}) {
  if (!value) return '-'
  const pattern = config?.pattern || field?.formatter || 'yyyy-MM-dd HH:mm:ss'
  try {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    const pad = number => String(number).padStart(2, '0')
    return pattern
      .replace('yyyy', date.getFullYear())
      .replace('MM', pad(date.getMonth() + 1))
      .replace('dd', pad(date.getDate()))
      .replace('HH', pad(date.getHours()))
      .replace('mm', pad(date.getMinutes()))
      .replace('ss', pad(date.getSeconds()))
  } catch {
    return value
  }
}

/**
 * 统一生成脱敏单元格、复制兜底和无障碍标签的文本；引用名称、状态和标准格式先解析再隐藏。
 * 启用后不执行自定义组件，以免组件从 row 中重读原值或把星号再次解析成其他内容。
 */
export function formatMaskedListCellValue(row, field, refNameMap = {}, entityStatusMap = {}) {
  const config = getListCellRenderConfig(field)
  const rawValue = getCellValue(row, field, undefined)
  if (rawValue === null || rawValue === undefined || rawValue === '') return config.emptyText || '-'
  let value = formatListFieldValue(row, field, refNameMap, entityStatusMap)
  if (field?.renderComponent === 'DateFormatter') {
    value = formatDateCellValue(value, field, config)
  } else if (field?.renderComponent === 'StatusBadge') {
    value = config.labelMap?.[String(value)] || value
  } else if (!field?.renderComponent || field.renderComponent === 'DefaultText') {
    if (!field?.renderComponent && isDateFieldCode(field?.fieldCode)) value = formatDateValue(rawValue)
    value = formatDefaultCellValue(value, field, config)
  }
  return maskListCellValue(value, config)
}
