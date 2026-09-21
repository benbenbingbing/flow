export const ADD_SIGN_TYPE_OPTIONS = Object.freeze([
  Object.freeze({ value: 'BEFORE', label: '前加签' }),
  Object.freeze({ value: 'PARALLEL', label: '并行加签' }),
  Object.freeze({ value: 'AFTER', label: '后加签' })
])

const SUPPORTED_ADD_SIGN_TYPES = new Set(
  ADD_SIGN_TYPE_OPTIONS.map(option => option.value)
)

// approve/reject 是流程结果的默认值，其余系统操作保留专用语义，
// 不允许被自定义审批结果占用。
const RESERVED_APPROVAL_ACTION_CODES = new Set([
  'transfer',
  'transferred',
  'addsign',
  'addsignbefore',
  'addsignparallel',
  'addsignafter',
  'canceladdsign',
  'terminate',
  'terminated',
  'withdraw',
  'manualcc',
  'cc'
])

function normalizedSystemActionCode(value) {
  return String(value ?? '')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '')
}

/**
 * 仅从服务端明确返回的加签能力中解析可用类型。
 *
 * 早期接口使用 addSignTypes，新能力模型可使用 allowedAddSignTypes；
 * 字段缺失、加签未明确开放或类型非法时均失败关闭。
 */
export function resolveAllowedAddSignTypes(operations = {}) {
  if (operations?.addSign !== true) return []
  const hasCanonicalTypes = Object.prototype.hasOwnProperty.call(
    operations,
    'allowedAddSignTypes'
  )
  const configuredTypes = hasCanonicalTypes
    ? (Array.isArray(operations.allowedAddSignTypes)
        ? operations.allowedAddSignTypes
        : [])
    : (Array.isArray(operations.addSignTypes) ? operations.addSignTypes : [])
  const allowed = new Set(configuredTypes
    .map(value => String(value ?? '').trim().toUpperCase())
    .filter(value => SUPPORTED_ADD_SIGN_TYPES.has(value)))
  return ADD_SIGN_TYPE_OPTIONS
    .map(option => option.value)
    .filter(value => allowed.has(value))
}

/** 保留当前合法选择，否则回退到服务端白名单中的第一项。 */
export function selectAllowedAddSignType(operations = {}, currentType = '') {
  const allowedTypes = resolveAllowedAddSignTypes(operations)
  const normalizedCurrent = String(currentType || '').trim().toUpperCase()
  return allowedTypes.includes(normalizedCurrent)
    ? normalizedCurrent
    : (allowedTypes[0] || '')
}

/** 权限值只接受服务端显式 boolean true，不对 truthy 值做放大解释。 */
export function isTaskOperationExplicitlyAllowed(operations, operation) {
  return operations?.[operation] === true
}

export function isReservedApprovalActionCode(value) {
  return RESERVED_APPROVAL_ACTION_CODES.has(normalizedSystemActionCode(value))
}

/**
 * 校验节点审批结果是否占用转办、加签、终止等系统操作编码。
 */
export function validateApprovalOptionActionCodes(options = []) {
  const conflicts = (Array.isArray(options) ? options : [])
    .map(option => String(option?.value ?? '').trim())
    .filter(value => value && isReservedApprovalActionCode(value))
  if (!conflicts.length) {
    return { valid: true, message: '' }
  }
  const values = [...new Set(conflicts)].map(value => `“${value}”`).join('、')
  return {
    valid: false,
    message: `审批选项值 ${values} 是系统操作保留编码，请改用业务结果编码`
  }
}
