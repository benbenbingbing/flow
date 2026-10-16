import { FORM_BUTTON_ICONS } from './form-actions.js'

export const BUILT_IN_BUTTON_ICONS = Object.freeze([...FORM_BUTTON_ICONS, 'Delete'])
export const BUILT_IN_BUTTON_KEYS = Object.freeze(['save', 'open', 'refresh', 'link', 'add', 'remove'])
export const BUILT_IN_BUTTON_APPEARANCES = Object.freeze(['DEFAULT', 'PLAIN', 'TEXT', 'LINK', 'ROUND', 'CIRCLE'])
export const BUILT_IN_BUTTON_TYPES = Object.freeze(['default', 'primary', 'success', 'warning', 'danger', 'info'])
const positions = ['DEFAULT', 'TOP', 'BOTTOM', 'BOTH']
const aligns = ['DEFAULT', 'LEFT', 'CENTER', 'RIGHT']
const sizes = ['small', 'default', 'large']
const colorPattern = /^#(?:[\da-f]{3}|[\da-f]{4}|[\da-f]{6}|[\da-f]{8})$/i
const isObject = value => !!value && typeof value === 'object' && !Array.isArray(value)
const integerIn = (value, max) => Number.isInteger(value) && value >= 0 && value <= max
const buttonValidators = {
  label: value => typeof value === 'string' && !!value.trim() && value.length <= 80,
  icon: value => value === '' || BUILT_IN_BUTTON_ICONS.includes(value),
  buttonType: value => BUILT_IN_BUTTON_TYPES.includes(value),
  buttonAppearance: value => BUILT_IN_BUTTON_APPEARANCES.includes(value),
  size: value => sizes.includes(value),
  textColor: value => typeof value === 'string' && colorPattern.test(value),
  backgroundColor: value => typeof value === 'string' && colorPattern.test(value),
  borderColor: value => typeof value === 'string' && colorPattern.test(value),
  borderRadius: value => integerIn(value, 40),
  sort: value => integerIn(value, 1000)
}

/**
 * 外观配置只能覆盖白名单字段。运行态忽略历史异常值，尤其不能把 enabled、visible、
 * handler 等字段传入按钮，避免展示配置覆盖宿主的权限、只读及固定执行逻辑。
 */
export function normalizeBuiltInActionBar(value, allowedKeys = BUILT_IN_BUTTON_KEYS) {
  const source = isObject(value) ? value : {}
  const result = {}
  if (positions.includes(source.position)) result.position = source.position
  if (aligns.includes(source.align)) result.align = source.align
  if (integerIn(source.gap, 40)) result.gap = source.gap
  if (isObject(source.buttons)) {
    const buttons = {}
    for (const [key, button] of Object.entries(source.buttons)) {
      if (!allowedKeys.includes(key) || !isObject(button)) continue
      const appearance = {}
      for (const [field, validate] of Object.entries(buttonValidators)) {
        if (Object.hasOwn(button, field) && validate(button[field])) appearance[field] = button[field]
      }
      if (Object.keys(appearance).length) buttons[key] = appearance
    }
    if (Object.keys(buttons).length) result.buttons = buttons
  }
  return result
}

/** 保存时拒绝未知/行为字段和无效样式；allowedKeys 由子页面类型确定，不来自用户输入。 */
export function builtInActionBarError(value, allowedKeys = BUILT_IN_BUTTON_KEYS) {
  if (value === undefined) return ''
  if (!isObject(value)) return '内置按钮外观必须是对象'
  for (const [key, field] of Object.entries(value)) {
    if (!['position', 'align', 'gap', 'buttons'].includes(key)) return `内置按钮外观不支持字段：${key}`
    if (key === 'position' && !positions.includes(field)) return '操作栏位置无效'
    if (key === 'align' && !aligns.includes(field)) return '操作栏对齐方式无效'
    if (key === 'gap' && !integerIn(field, 40)) return '按钮间距须为 0–40 的整数'
  }
  if (value.buttons === undefined) return ''
  if (!isObject(value.buttons)) return '内置按钮列表必须是对象'
  for (const [key, button] of Object.entries(value.buttons)) {
    if (!allowedKeys.includes(key)) return `当前子页面不支持按钮：${key}`
    if (!isObject(button)) return `按钮 ${key} 的外观必须是对象`
    for (const [field, setting] of Object.entries(button)) {
      if (!Object.hasOwn(buttonValidators, field)) return `内置按钮不支持字段：${field}`
      if (!buttonValidators[field](setting)) return `按钮 ${key} 的 ${field} 配置无效`
    }
  }
  return ''
}

/** 缺省值由具体使用位置提供，保留空状态、表格行和移动端原有的文案、尺寸及图标。 */
export function resolveBuiltInButton(actionBar, key, defaults = {}) {
  const resolved = { ...defaults, ...normalizeBuiltInActionBar(actionBar, [key]).buttons?.[key] }
  // 圆形按钮必须有图标，清空图标后回退普通外观，保证入口始终可辨认。
  if (resolved.buttonAppearance === 'CIRCLE' && !BUILT_IN_BUTTON_ICONS.includes(resolved.icon)) {
    resolved.buttonAppearance = 'DEFAULT'
  }
  return resolved
}

/** 只生成受限颜色及数值 CSS；禁用样式仍由组件库负责，不能被普通状态的色值覆盖。 */
export function builtInButtonStyle(button = {}) {
  const style = {}
  const colors = { textColor: 'text-color', backgroundColor: 'bg-color', borderColor: 'border-color' }
  for (const [key, suffix] of Object.entries(colors)) {
    if (!buttonValidators[key](button[key])) continue
    for (const state of ['', 'hover-', 'active-']) style[`--el-button-${state}${suffix}`] = button[key]
  }
  if (integerIn(button.borderRadius, 40)) style.borderRadius = `${button.borderRadius}px`
  if (integerIn(button.sort, 1000)) style.order = button.sort
  return style
}

/** DEFAULT 保持各宿主的历史位置；删除配置即可完整恢复默认外观。 */
export function builtInActionBarHasPosition(actionBar, position, defaultPosition = 'TOP') {
  const configured = normalizeBuiltInActionBar(actionBar).position
  const resolved = !configured || configured === 'DEFAULT' ? defaultPosition : configured
  return resolved === 'BOTH' || resolved === position
}

/** 使用宿主默认对齐和间距；显式设置才覆盖，以兼容标题栏及移动端原有布局。 */
export function builtInActionBarStyle(actionBar, defaultAlign = 'RIGHT', defaultGap = 4) {
  const config = normalizeBuiltInActionBar(actionBar)
  const align = !config.align || config.align === 'DEFAULT' ? defaultAlign : config.align
  return { justifyContent: { LEFT: 'flex-start', CENTER: 'center', RIGHT: 'flex-end' }[align], gap: `${config.gap ?? defaultGap}px` }
}
