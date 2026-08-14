export const MUTATION_PHASES = Object.freeze([
  'PREPARE',
  'BEFORE_WRITE',
  'AFTER_WRITE',
  'AFTER_COMMIT'
])

const FIELD_MAPPING_PHASES = Object.freeze(['PREPARE', 'BEFORE_WRITE'])
const MANAGED_INTERFACE_PHASES = Object.freeze(['PREPARE'])

export function normalizeSupportedPhases(capability) {
  let values = capability
  if (typeof values === 'string') {
    try {
      values = JSON.parse(values)
    } catch {
      values = values.split(',')
    }
  }
  if (!Array.isArray(values)) return []
  return [...new Set(values
    .map(value => String(value?.name || value || '').trim().toUpperCase())
    .filter(value => MUTATION_PHASES.includes(value)))]
}

export function allowedMutationStepPhases(step = {}) {
  if (step.stepType === 'FIELD_MAPPING') return [...FIELD_MAPPING_PHASES]
  if (step.stepType === 'MANAGED_INTERFACE') return [...MANAGED_INTERFACE_PHASES]
  if (step.stepType === 'JAVA_PROVIDER') {
    const supported = normalizeSupportedPhases(step.supportedPhases)
    if (supported.length) return supported
  }
  return [...MUTATION_PHASES]
}

export function alignMutationStepPhase(step = {}) {
  const allowed = allowedMutationStepPhases(step)
  if (!allowed.includes(step.phase)) step.phase = allowed[0]
  return step
}

export function validateMutationStep(step = {}) {
  if (!String(step.stepName || '').trim() || !step.stepType || !step.phase) {
    return '请填写步骤名称、阶段和类型'
  }
  const allowed = allowedMutationStepPhases(step)
  if (!allowed.includes(step.phase)) {
    return `${stepTypeLabel(step.stepType)}不支持${phaseLabel(step.phase)}阶段`
  }
  if (step.stepType === 'BUILT_IN_RULE' && !String(step.providerCode || '').trim()) {
    return '内置规则必须选择规则实现'
  }
  if (step.stepType === 'MANAGED_INTERFACE'
      && (!String(step.providerCode || '').trim()
        || !String(step.operationCode || '').trim())) {
    return '受管理接口必须选择接口服务及操作'
  }
  if (step.stepType === 'JAVA_PROVIDER' && !String(step.providerCode || '').trim()) {
    return '请选择 Java Provider'
  }
  return ''
}

function phaseLabel(value) {
  return ({
    PREPARE: '准备',
    BEFORE_WRITE: '写入前',
    AFTER_WRITE: '写入后',
    AFTER_COMMIT: '提交后'
  })[value] || value
}

function stepTypeLabel(value) {
  return ({
    FIELD_MAPPING: '字段映射',
    MANAGED_INTERFACE: '受管理接口',
    JAVA_PROVIDER: 'Java Provider'
  })[value] || value
}
