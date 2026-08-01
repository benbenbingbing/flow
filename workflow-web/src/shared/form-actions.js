export const FORM_ACTION_MODES = Object.freeze([
  { value: 'create', label: '新增' },
  { value: 'edit', label: '编辑' },
  { value: 'view', label: '查看' },
  { value: 'approve', label: '审批' }
])

export const FORM_BUILT_IN_ACTIONS = Object.freeze({
  close: {
    key: 'close',
    type: 'built-in',
    icon: '',
    buttonType: 'default',
    sort: 10,
    placement: 'FOOTER',
    modes: ['create', 'edit', 'view', 'approve']
  },
  reset: {
    key: 'reset',
    type: 'built-in',
    label: '重置',
    icon: 'RefreshLeft',
    buttonType: 'default',
    sort: 20,
    placement: 'FOOTER',
    modes: ['create', 'edit']
  },
  save: {
    key: 'save',
    type: 'built-in',
    icon: 'Check',
    buttonType: 'primary',
    sort: 30,
    placement: 'FOOTER',
    validateBeforeExecute: true,
    modes: ['create', 'edit']
  },
  saveAndStart: {
    key: 'saveAndStart',
    type: 'built-in',
    label: '保存并发起流程',
    icon: 'Promotion',
    buttonType: 'primary',
    sort: 40,
    placement: 'FOOTER',
    validateBeforeExecute: true,
    modes: ['create', 'edit']
  },
  submitApproval: {
    key: 'submitApproval',
    type: 'built-in',
    label: '提交审批',
    icon: 'Select',
    buttonType: 'primary',
    sort: 40,
    placement: 'FOOTER',
    validateBeforeExecute: true,
    modes: ['approve']
  }
})

export function emptyFormActionBar() {
  return {
    version: 1,
    builtInOverrides: {},
    customButtons: []
  }
}

export function normalizeFormActionBar(value) {
  const source = objectValue(value)
  return {
    version: 1,
    builtInOverrides: {
      ...objectValue(source.builtInOverrides)
    },
    customButtons: arrayValue(source.customButtons)
      .map(button => normalizeCustomButton(button))
  }
}

export function normalizeCustomButton(value = {}, index = 0) {
  const source = objectValue(value)
  return {
    key: source.key || `custom_${Date.now()}_${index}`,
    type: 'custom',
    label: source.label || '自定义按钮',
    icon: source.icon || '',
    buttonType: source.buttonType || 'default',
    sort: finiteNumber(source.sort, 50 + index),
    enabled: source.enabled !== false,
    modes: normalizeModes(source.modes, ['edit']),
    placement: String(source.placement || 'FOOTER').toUpperCase(),
    slotKey: source.slotKey || '',
    perm: source.perm || '',
    availabilityRule: source.availabilityRule || null,
    confirm: {
      enabled: source.confirm?.enabled === true,
      message: source.confirm?.message || ''
    },
    validateBeforeExecute: source.validateBeforeExecute === true
  }
}

export function readFormActionBar(form) {
  const viewConfig = parseObject(form?.viewConfig)
  return normalizeFormActionBar(viewConfig.actionBar)
}

export function resolveLocalFormActions(form, context = {}) {
  const mode = normalizeMode(context.mode)
  const actionBar = readFormActionBar(form)
  const overrides = actionBar.builtInOverrides
  const systemEntity = context.systemEntity === true
  const result = []

  for (const key of Object.keys(FORM_BUILT_IN_ACTIONS)) {
    if (systemEntity && key !== 'close') continue
    const base = defaultBuiltIn(key, mode)
    if (!base || !base.modes.includes(mode)) continue
    const action = applyBuiltInOverride(base, overrides[key], mode)
    if (action.enabled === false || !action.modes.includes(mode)) continue
    if (key === 'saveAndStart' && (
      context.workflowReady !== true
      || context.hasProcessInstance === true
    )) {
      continue
    }
    if (key === 'submitApproval' && context.canApprove === false) {
      continue
    }
    result.push(withRuntimeState(action, form?.id || form?.formId, false))
  }

  if (!systemEntity) {
    actionBar.customButtons
      .filter(button => button.enabled !== false && button.modes.includes(mode))
      .forEach(button => {
        result.push(withRuntimeState(
          normalizeCustomButton(button),
          form?.id || form?.formId || form?.entityFormId,
          true
        ))
      })
  }

  return result.sort(actionSort)
}

export function mergeResolvedFormActions(actionGroups = []) {
  const builtIns = new Map()
  const customs = []
  actionGroups.flat().forEach(action => {
    if (!action) return
    if (action.type === 'built-in') {
      if (!builtIns.has(action.key)) builtIns.set(action.key, action)
      return
    }
    customs.push({
      ...action,
      runtimeKey: action.runtimeKey
        || `${action.ownerFormId || 'form'}:${action.key}`
    })
  })
  return [...builtIns.values(), ...customs].sort(actionSort)
}

export function footerFormActions(actions = []) {
  return actions.filter(action =>
    action.visible !== false
    && String(action.placement || 'FOOTER').toUpperCase() === 'FOOTER'
  )
}

export function slotFormActions(actions = [], slotKey = '') {
  return actions.filter(action =>
    action.visible !== false
    && String(action.placement || '').toUpperCase() === 'ACTION_SLOT'
    && String(action.slotKey || '') === String(slotKey || '')
  )
}

function defaultBuiltIn(key, mode) {
  const preset = FORM_BUILT_IN_ACTIONS[key]
  if (!preset) return null
  const label = key === 'close'
    ? (['create', 'edit'].includes(mode) ? '取消' : '关闭')
    : key === 'save'
      ? (mode === 'create' ? '保存' : '保存修改')
      : preset.label
  return {
    ...cloneValue(preset),
    label,
    enabled: true,
    enabledModes: [...preset.modes]
  }
}

function applyBuiltInOverride(base, value, mode) {
  const override = objectValue(value)
  const modes = normalizeModes(
    override.enabledModes,
    base.enabledModes || base.modes
  ).filter(item => base.modes.includes(item))
  return {
    ...base,
    enabled: override.enabled !== false,
    label: override.labelByMode?.[mode] || base.label,
    icon: override.icon ?? base.icon,
    buttonType: override.buttonType || base.buttonType,
    sort: finiteNumber(override.sort, base.sort),
    modes,
    availabilityRule: override.availabilityRule || null
  }
}

function withRuntimeState(action, formId, custom) {
  return {
    ...action,
    ownerFormId: formId || '',
    runtimeKey: custom
      ? `${formId || 'form'}:${action.key}`
      : action.key,
    visible: action.visible !== false,
    enabled: action.enabled !== false,
    reason: action.reason || ''
  }
}

function normalizeMode(value) {
  const mode = String(value || 'view').toLowerCase()
  return FORM_ACTION_MODES.some(item => item.value === mode)
    ? mode
    : 'view'
}

function normalizeModes(value, fallback) {
  const source = Array.isArray(value) && value.length ? value : fallback
  return [...new Set(source
    .map(item => String(item || '').toLowerCase())
    .filter(mode => FORM_ACTION_MODES.some(item => item.value === mode)))]
}

function actionSort(left, right) {
  return finiteNumber(left?.sort, 0) - finiteNumber(right?.sort, 0)
}

function parseObject(value) {
  if (!value) return {}
  if (typeof value === 'object' && !Array.isArray(value)) return value
  try {
    const parsed = JSON.parse(value)
    return objectValue(parsed)
  } catch {
    return {}
  }
}

function objectValue(value) {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value
    : {}
}

function arrayValue(value) {
  return Array.isArray(value) ? value : []
}

function finiteNumber(value, fallback) {
  const number = Number(value)
  return Number.isFinite(number) ? number : fallback
}

function cloneValue(value) {
  return JSON.parse(JSON.stringify(value))
}
