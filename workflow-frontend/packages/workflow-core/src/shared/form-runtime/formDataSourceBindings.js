import { safeParseConfig } from '../config-runtime/index.js'

export const FORM_DATA_SOURCE_USAGE_OPTIONS = Object.freeze([
  { label: '初始化数据', summaryLabel: '初始化', value: 'FORM_INIT' },
  { label: '加载后处理', summaryLabel: '加载后', value: 'AFTER_LOAD' },
  { label: '提交前处理', summaryLabel: '提交前', value: 'BEFORE_SUBMIT' }
])

const usageByValue = new Map(
  FORM_DATA_SOURCE_USAGE_OPTIONS.map(option => [option.value, option])
)

function normalizeBindings(value) {
  if (typeof value === 'string') return safeParseConfig(value)
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value
    : {}
}

function configuredCount(value) {
  if (Array.isArray(value)) return value.filter(Boolean).length
  return value ? 1 : 0
}

/**
 * 统计表单三个生命周期位置的绑定数量，供列表摘要与设计器使用同一口径。
 */
export function countFormDataSourceBindings(value) {
  const bindings = normalizeBindings(value)
  return Object.fromEntries(
    FORM_DATA_SOURCE_USAGE_OPTIONS.map(option => [
      option.value,
      configuredCount(bindings[option.value])
    ])
  )
}

export function totalFormDataSourceBindings(value) {
  return Object.values(countFormDataSourceBindings(value))
    .reduce((total, count) => total + count, 0)
}

export function formatFormDataSourceBindingSummary(value) {
  const counts = countFormDataSourceBindings(value)
  const total = Object.values(counts).reduce((sum, count) => sum + count, 0)
  if (total === 0) return '未配置'
  return FORM_DATA_SOURCE_USAGE_OPTIONS
    .map(option => `${option.summaryLabel} ${counts[option.value]}`)
    .join(' · ')
}

export function getFormDataSourceBindingStepLabel(rows, index) {
  const usage = rows[index]?.usage || 'FORM_INIT'
  const step = rows.slice(0, index + 1)
    .filter(row => row?.usage === usage)
    .length
  return `${usageByValue.get(usage)?.label || usage} · 步骤 ${step}`
}

/**
 * 同一处理时机按顺序合并结果；显式输出映射命中同一路径会产生静默覆盖，因此保存前直接阻止。
 */
export function assertUniqueFormDataSourceOutputTargets(rows) {
  const seenByUsage = new Map()
  const stepByUsage = new Map()
  rows.forEach((row) => {
    const usage = row?.usage || 'FORM_INIT'
    const step = (stepByUsage.get(usage) || 0) + 1
    stepByUsage.set(usage, step)
    const seen = seenByUsage.get(usage) || new Map()
    seenByUsage.set(usage, seen)
    const mapping = row?.outputMapping
    if (!mapping || typeof mapping !== 'object' || Array.isArray(mapping)) return
    Object.keys(mapping).forEach((targetPath) => {
      // 与运行时 setPath 保持一致：空段和纯空白段不会形成独立目标。
      const normalizedPath = String(targetPath || '')
        .split('.')
        .filter(segment => segment.trim().length > 0)
        .join('.')
      if (!normalizedPath) return
      const previousStep = seen.get(normalizedPath)
      if (previousStep) {
        const label = usageByValue.get(usage)?.label || usage
        throw new Error(
          `${label}的步骤 ${previousStep} 与步骤 ${step} 都写入目标路径“${normalizedPath}”，请调整输出映射`
        )
      }
      seen.set(normalizedPath, step)
    })
  })
}
