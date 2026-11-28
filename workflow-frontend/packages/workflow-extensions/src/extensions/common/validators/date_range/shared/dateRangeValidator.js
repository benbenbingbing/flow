import { CustomValidator } from '@flow/workflow-core/extensions/contracts/validation'

const DAY_MS = 86_400_000
const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/
const DATETIME_PATTERN = /^(\d{4})-(\d{2})-(\d{2})[ T](?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.0{1,9})?$/
const isBlank = value => value == null || (typeof value === 'string' && value.trim() === '')
const MIN_DAY = calendarDay(1, 1, 1)
const MAX_DAY = calendarDay(9999, 12, 31)

/** 用当地日期分量构造 UTC 日序，避免按毫秒加减当地时间跨夏令时后偏一天。 */
function calendarDay(year, month, day) {
  const utc = new Date(0)
  utc.setUTCFullYear(year, month - 1, day)
  // Date 会自动进位，回读分量以拒绝不存在的日期，例如 2026-02-29。
  return year >= 1 && year <= 9999 && utc.getUTCFullYear() === year
    && utc.getUTCMonth() === month - 1 && utc.getUTCDate() === day
    ? utc.getTime() / DAY_MS
    : NaN
}

function localCalendarDay(value) {
  return calendarDay(value.getFullYear(), value.getMonth() + 1, value.getDate())
}

function parseCalendarDay(value, allowTime = false) {
  if (typeof value !== 'string') return NaN
  const match = DATE_PATTERN.exec(value) || (allowTime && DATETIME_PATTERN.exec(value))
  return match ? calendarDay(Number(match[1]), Number(match[2]), Number(match[3])) : NaN
}

function formatCalendarDay(day) {
  return new Date(day * DAY_MS).toISOString().slice(0, 10)
}

/** 将某端的固定日期或相对天数转换为同一日序，配置错误由表单运行时展示。 */
function resolveBoundary(label, fixedDate, offset, today) {
  const hasOffset = !isBlank(offset)
  if (!isBlank(fixedDate)) {
    if (hasOffset) throw new TypeError(`${label}只能配置固定日期或相对今天的天数`)
    const day = parseCalendarDay(fixedDate)
    if (!Number.isFinite(day)) throw new TypeError(`${label}必须是有效日期，格式为 YYYY-MM-DD`)
    return day
  }
  if (!hasOffset) return undefined
  if (!Number.isSafeInteger(offset)) throw new TypeError(`${label}偏移必须为整数`)
  const day = today + offset
  if (day < MIN_DAY || day > MAX_DAY) throw new RangeError(`${label}偏移超出日期范围`)
  return day
}

export class DateRangeValidator extends CustomValidator {
  /**
   * 校验字段的日历日期。固定边界用 YYYY-MM-DD，相对边界按设备当天计算。
   * @param {*} value 当前字段值，日期时间按当地日期部分比较，空值交给必填规则。
   * @param {import('@flow/workflow-core/extensions/contracts/validation').ValidationContext} [context={}] params 保存该字段的上下限和包含端点开关。
   * @returns {true|string} 通过返回 true，超出范围或日期无效时返回提示。
   * @throws {TypeError|RangeError} 边界缺失、格式无效、重复配置或范围内无可选日期。
   */
  validate(value, context = {}) {
    const {
      minDate, maxDate, minDaysFromToday, maxDaysFromToday, includeMin, includeMax
    } = context.params || {}
    // 同次校验共享今天，跨午夜时两个相对边界仍以同一天为基准。
    const today = localCalendarDay(new Date())
    const min = resolveBoundary('最早日期', minDate, minDaysFromToday, today)
    const max = resolveBoundary('最晚日期', maxDate, maxDaysFromToday, today)
    if (min === undefined && max === undefined) throw new TypeError('请至少配置一个日期边界')
    const first = min === undefined ? MIN_DAY : min + (includeMin === false ? 1 : 0)
    const last = max === undefined ? MAX_DAY : max - (includeMax === false ? 1 : 0)
    // 排除边界后可能没有任何可选日期，例如两个相邻日期均不包含。
    if (first > last) throw new RangeError('日期范围内没有可选日期，请检查上下限和包含选项')
    if (isBlank(value)) return true

    const isDateTime = String(context.field?.fieldType || '').toUpperCase() === 'DATETIME'
    const day = value instanceof Date
      ? localCalendarDay(value)
      : parseCalendarDay(value, isDateTime)
    if (!Number.isFinite(day)) return '请输入有效日期'
    if (day < first) return `日期${includeMin === false ? '必须晚于' : '不能早于'} ${formatCalendarDay(min)}`
    if (day > last) return `日期${includeMax === false ? '必须早于' : '不能晚于'} ${formatCalendarDay(max)}`
    return true
  }
}
