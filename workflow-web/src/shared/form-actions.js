export const FORM_ACTION_MODES = Object.freeze([
  { value: 'create', label: '新增' },
  { value: 'edit', label: '编辑' },
  { value: 'view', label: '查看' },
  { value: 'approve', label: '审批' }
])

export const FORM_ACTION_KEY_PATTERN = /^[a-z][a-z0-9_-]{0,63}$/
export const FORM_ACTION_PERMISSION_PATTERN = /^[A-Za-z0-9_.-]+(?::[A-Za-z0-9_.-]+)+$/
export const FORM_BUTTON_APPEARANCES = Object.freeze([
  'DEFAULT',
  'PLAIN',
  'ROUND',
  'CIRCLE'
])
export const FORM_BUTTON_ICONS = Object.freeze([
  'Check',
  'Close',
  'Document',
  'Download',
  'Edit',
  'Link',
  'Message',
  'Plus',
  'Printer',
  'Promotion',
  'Refresh',
  'RefreshLeft',
  'Select',
  'Setting',
  'Upload',
  'View'
])

const FORM_BUTTON_APPEARANCE_SET = new Set(FORM_BUTTON_APPEARANCES)
const FORM_BUTTON_ICON_SET = new Set(FORM_BUTTON_ICONS)

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

/**
 * 把持久化按钮外观收敛为平台支持的互斥枚举。缺省或非法值回退 DEFAULT，
 * 确保已有配置继续保持当前 Element Plus 默认按钮效果。
 */
export function normalizeFormButtonAppearance(value) {
  const appearance = String(value || 'DEFAULT').trim().toUpperCase()
  return FORM_BUTTON_APPEARANCE_SET.has(appearance)
    ? appearance
    : 'DEFAULT'
}

/** 将单值外观枚举映射成 Element Plus Button 的三个互斥布尔属性。 */
export function resolveFormButtonAppearanceProps(value) {
  const appearance = normalizeFormButtonAppearance(value)
  return {
    plain: appearance === 'PLAIN',
    round: appearance === 'ROUND',
    circle: appearance === 'CIRCLE'
  }
}

/** 判断图标是否能由平台统一按钮渲染器稳定解析。 */
export function isRegisteredFormButtonIcon(value) {
  return FORM_BUTTON_ICON_SET.has(String(value || '').trim())
}

export function normalizeCustomButton(value = {}, index = 0) {
  const source = objectValue(value)
  return {
    key: source.key || `custom_${Date.now()}_${index}`,
    type: 'custom',
    label: source.label || '自定义按钮',
    icon: source.icon || '',
    buttonType: source.buttonType || 'default',
    buttonAppearance: normalizeFormButtonAppearance(source.buttonAppearance),
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

/**
 * 将合并后的运行时动作收窄到当前表单。审批页可能同时装载多个表单，整页
 * 自定义组件不能看到或触发其他表单的按钮；缺少所属身份的自定义动作失败关闭。
 */
export function formActionsForOwner(actions = [], form = {}) {
  const formId = String(
    form?.id || form?.formId || form?.entityFormId || ''
  )
  return (Array.isArray(actions) ? actions : []).filter(action =>
    action?.type === 'built-in'
    || (formId
      && action?.ownerFormId
      && String(action.ownerFormId) === formId)
  )
}

/**
 * 为整页自定义表单提供受控的动作插槽契约。自定义组件只能触发当前运行时
 * 已解析且可操作的 ACTION_SLOT 按钮，最终动作对象仍由宿主传回统一执行链。
 */
export function createCustomFormActionSlotContract(
  actions = [],
  onAction = () => {}
) {
  const eligibleActions = (Array.isArray(actions) ? actions : [])
    .filter(action =>
      action?.visible !== false
      && String(action?.placement || '').toUpperCase() === 'ACTION_SLOT'
      && String(action?.slotKey || '').trim()
    )
  // nodeKey 允许普通英文单词，使用无原型字典可避免 constructor 等合法键名
  // 与 Object.prototype 冲突并导致动作插槽初始化失败。
  const slots = Object.create(null)
  eligibleActions.forEach(action => {
    const slotKey = String(action.slotKey)
    if (!slots[slotKey]) slots[slotKey] = []
    // 暴露只读副本，避免自定义组件修改 enabled/visible 后再影响闭包中的
    // 宿主权威动作；trigger 始终回查未暴露的 eligibleActions。
    slots[slotKey].push(deepReadonlyClone(action))
  })
  Object.keys(slots).forEach(slotKey => {
    slots[slotKey] = Object.freeze([...slots[slotKey]])
  })

  return Object.freeze({
    version: 1,
    slots: Object.freeze(slots),
    trigger(actionOrKey) {
      const requestedKey = typeof actionOrKey === 'object'
        ? String(actionOrKey?.runtimeKey || actionOrKey?.key || '')
        : String(actionOrKey || '')
      const action = eligibleActions.find(item =>
        String(item.runtimeKey || item.key || '') === requestedKey
        || String(item.key || '') === requestedKey
      )
      if (!action || action.enabled === false) return false
      onAction(action)
      return true
    }
  })
}

/**
 * 提取当前生效发布快照中已有的自定义按钮编码，用于防止设计器静默修改
 * 已进入历史契约的 targetKey。兼容运行时快照和早期直接表单快照两种结构。
 */
export function publishedFormButtonKeys(releases = [], activeReleaseId = '') {
  const items = Array.isArray(releases) ? releases : []
  const release = items.find(item =>
    activeReleaseId && String(item?.id) === String(activeReleaseId)
  ) || items.find(item =>
    String(item?.status || '').toUpperCase() === 'ACTIVE'
  ) || [...items].sort((left, right) =>
    Number(right?.version || 0) - Number(left?.version || 0)
  )[0]
  if (!release) return []

  const snapshot = parseObject(release.snapshotDocument || release.snapshot)
  const snapshotForm = parseObject(snapshot.form)
  const viewConfig = parseObject(
    snapshotForm.viewConfig
    || snapshot.viewConfig
    || release.viewConfig
  )
  return normalizeFormActionBar(viewConfig.actionBar).customButtons
    .map(button => String(button.key || '').trim())
    .filter(Boolean)
}

/**
 * 在保存和发布前统一验证所有按钮的结构；权限与事件链只要求启用按钮。
 * 同时拒绝指向不存在按钮的遗留绑定，避免旧 targetKey 被静默保留。
 */
export function validateFormActionConfiguration({
  actionBar,
  nodes = [],
  eventBindings = [],
  requireEventBindings = true
} = {}) {
  const source = parseObject(actionBar)
  const rawButtons = Array.isArray(source.customButtons)
    ? source.customButtons
    : []
  // 运行时归一化会为缺失 modes/key 自动补兼容默认值；设计态校验必须保留
  // 用户实际输入，否则空模式或空编码会在保存前被意外掩盖。
  const buttons = rawButtons.map((button, index) => ({
    ...normalizeCustomButton(button, index),
    key: button?.key ?? '',
    label: button?.label ?? '',
    perm: button?.perm ?? '',
    modes: Array.isArray(button?.modes) ? button.modes : [],
    placement: String(button?.placement || 'FOOTER').toUpperCase(),
    slotKey: button?.slotKey ?? '',
    buttonAppearance: button?.buttonAppearance ?? 'DEFAULT'
  }))
  const validModes = new Set(FORM_ACTION_MODES.map(mode => mode.value))
  const slotKeys = new Set((Array.isArray(nodes) ? nodes : [])
    .filter(node => String(node?.nodeType || '').toUpperCase() === 'ACTION_SLOT')
    .map(node => String(node?.nodeKey || '').trim())
    .filter(Boolean))
  const bindings = Array.isArray(eventBindings) ? eventBindings : []
  const errors = []
  const keyCounts = new Map()

  buttons.forEach(button => {
    const key = String(button.key || '').trim()
    keyCounts.set(key, (keyCounts.get(key) || 0) + 1)
  })

  if (buttons.length > 50) {
    errors.push(validationError(
      'TOO_MANY_BUTTONS',
      '',
      '单个表单最多配置 50 个自定义按钮'
    ))
  }

  buttons.forEach(button => {
    const key = String(button.key || '').trim()
    const label = String(button.label || key || '未命名按钮')
    if (!FORM_ACTION_KEY_PATTERN.test(key)) {
      errors.push(validationError(
        'INVALID_KEY',
        key,
        `按钮“${label}”的稳定编码格式不正确`
      ))
    } else if (Object.hasOwn(FORM_BUILT_IN_ACTIONS, key)) {
      errors.push(validationError(
        'RESERVED_KEY',
        key,
        `按钮稳定编码“${key}”已被平台按钮占用`
      ))
    } else if ((keyCounts.get(key) || 0) > 1) {
      errors.push(validationError(
        'DUPLICATE_KEY',
        key,
        `按钮稳定编码“${key}”重复`
      ))
    }
    const configuredLabel = String(button.label || '').trim()
    if (!configuredLabel || configuredLabel.length > 60) {
      errors.push(validationError(
        'INVALID_LABEL',
        key,
        `按钮“${label}”的名称不能为空且不能超过 60 个字符`
      ))
    }
    const buttonAppearance = String(
      button.buttonAppearance || 'DEFAULT'
    ).trim().toUpperCase()
    if (!FORM_BUTTON_APPEARANCE_SET.has(buttonAppearance)) {
      errors.push(validationError(
        'INVALID_BUTTON_APPEARANCE',
        key,
        `按钮“${label}”的外观无效`
      ))
    } else if (buttonAppearance === 'CIRCLE'
        && !isRegisteredFormButtonIcon(button.icon)) {
      errors.push(validationError(
        'MISSING_CIRCLE_ICON',
        key,
        `圆形按钮“${label}”必须选择平台支持的图标`
      ))
    }
    const permission = String(button.perm || '').trim()
    if (button.enabled !== false && (
      !permission
      || permission.length > 200
      || !FORM_ACTION_PERMISSION_PATTERN.test(permission)
    )) {
      errors.push(validationError(
        'MISSING_PERMISSION',
        key,
        `按钮“${label}”必须配置合法权限码`
      ))
    }
    if (!Array.isArray(button.modes)
        || button.modes.length === 0
        || button.modes.some(mode => !validModes.has(String(mode).toLowerCase()))) {
      errors.push(validationError(
        'INVALID_MODES',
        key,
        `按钮“${label}”必须选择有效的适用模式`
      ))
    }

    const placement = String(button.placement || 'FOOTER').toUpperCase()
    if (!['FOOTER', 'ACTION_SLOT'].includes(placement)) {
      errors.push(validationError(
        'INVALID_PLACEMENT',
        key,
        `按钮“${label}”的位置无效`
      ))
    } else if (placement === 'ACTION_SLOT') {
      const slotKey = String(button.slotKey || '').trim()
      if (!slotKey || !slotKeys.has(slotKey)) {
        errors.push(validationError(
          'INVALID_SLOT',
          key,
          `按钮“${label}”必须关联当前表单中的动作插槽`
        ))
      }
    }

    if (button.confirm?.enabled === true) {
      const confirmMessage = String(button.confirm?.message || '').trim()
      if (!confirmMessage || confirmMessage.length > 300) {
        errors.push(validationError(
          'INVALID_CONFIRM_MESSAGE',
          key,
          `按钮“${label}”启用二次确认时必须填写不超过 300 个字符的提示`
        ))
      }
    }

    if (button.enabled !== false
        && requireEventBindings
        && key
        && !bindings.some(binding =>
      String(binding?.targetType || '').toUpperCase() === 'BUTTON'
      && String(binding?.targetKey || '') === key
      && String(binding?.eventCode || '').toUpperCase() === 'FORM_BUTTON_CLICK'
      && binding?.enabled !== false
      && String(binding?.inheritanceMode || 'INHERIT').toUpperCase() !== 'DISABLE'
    )) {
      errors.push(validationError(
        'MISSING_EVENT_BINDING',
        key,
        `按钮“${label}”必须配置启用的点击事件链`
      ))
    }
  })

  const knownButtonKeys = new Set(
    buttons.map(button => String(button.key || '').trim()).filter(Boolean)
  )
  bindings.forEach(binding => {
    const targetKey = String(binding?.targetKey || '').trim()
    if (String(binding?.targetType || '').toUpperCase() !== 'BUTTON'
        || String(binding?.eventCode || '').toUpperCase() !== 'FORM_BUTTON_CLICK'
        || !targetKey
        || knownButtonKeys.has(targetKey)) {
      return
    }
    errors.push(validationError(
      'ORPHAN_EVENT_BINDING',
      targetKey,
      `事件绑定仍指向不存在的按钮“${targetKey}”，请先清理该绑定`
    ))
  })

  return {
    valid: errors.length === 0,
    errors
  }
}

function validationError(code, buttonKey, message) {
  return { code, buttonKey, message }
}

function deepReadonlyClone(value, seen = new WeakMap()) {
  if (value === null || typeof value !== 'object') return value
  if (seen.has(value)) return seen.get(value)
  const result = Array.isArray(value) ? [] : Object.create(null)
  seen.set(value, result)
  Object.entries(value).forEach(([key, item]) => {
    result[key] = deepReadonlyClone(item, seen)
  })
  return Object.freeze(result)
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
