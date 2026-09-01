import { normalizeFormNodeType } from './form-node-hierarchy.js'

const freezeAppearance = (showPadding, showBorder) => Object.freeze({
  showPadding,
  showBorder
})

/**
 * 容器外观缺省值必须与引入可配置项之前的渲染结果一致，历史表单缺少字段时据此兼容。
 */
export const FORM_CONTAINER_APPEARANCE_DEFAULTS = Object.freeze({
  SECTION: freezeAppearance(true, true),
  GRID: freezeAppearance(false, false),
  TAB_SET: freezeAppearance(true, true),
  TAB: freezeAppearance(false, false),
  COLLAPSE: freezeAppearance(true, true),
  SUB_FORM: freezeAppearance(true, true),
  REPEATER: freezeAppearance(true, true)
})

const UNSUPPORTED_APPEARANCE = freezeAppearance(false, false)

const parseObject = value => {
  if (!value) return {}
  if (typeof value === 'object' && !Array.isArray(value)) return value
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed
      : {}
  } catch {
    return {}
  }
}

const normalizeBoolean = (value, fallback) => {
  if (value === true || value === false) return value
  if (value === 1 || value === '1' || value === 'true') return true
  if (value === 0 || value === '0' || value === 'false') return false
  return fallback
}

/**
 * 判断节点类型是否允许单独控制容器内边距和边框。
 */
export function supportsFormContainerAppearance(value) {
  return Object.hasOwn(
    FORM_CONTAINER_APPEARANCE_DEFAULTS,
    normalizeFormNodeType(value)
  )
}

/**
 * 返回节点类型的兼容缺省值。返回副本，避免调用方修改共享配置。
 */
export function getDefaultFormContainerAppearance(value) {
  const defaults = FORM_CONTAINER_APPEARANCE_DEFAULTS[
    normalizeFormNodeType(value)
  ] || UNSUPPORTED_APPEARANCE
  return { ...defaults }
}

/**
 * 解析容器外观配置。新格式的顶层字段优先，缺键时兼容旧 componentProps 并最终回退旧版视觉。
 */
export function resolveFormContainerAppearance(value, propsValue) {
  const defaults = getDefaultFormContainerAppearance(value)
  if (!supportsFormContainerAppearance(value)) return defaults

  const props = parseObject(propsValue)
  const nested = parseObject(props.componentProps)
  return {
    showPadding: normalizeBoolean(
      props.showPadding ?? nested.showPadding,
      defaults.showPadding
    ),
    showBorder: normalizeBoolean(
      props.showBorder ?? nested.showBorder,
      defaults.showBorder
    )
  }
}
