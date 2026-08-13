export const FORM_RENDERER_MODE_DEFAULT = 'DEFAULT'
export const FORM_RENDERER_MODE_CUSTOM = 'CUSTOM'

export const FORM_RENDERER_MODE_OPTIONS = Object.freeze([
  { value: FORM_RENDERER_MODE_DEFAULT, label: '默认动态表单' },
  { value: FORM_RENDERER_MODE_CUSTOM, label: '自定义表单' }
])

export function resolveFormRendererMode(customComponent) {
  return customComponent
    ? FORM_RENDERER_MODE_CUSTOM
    : FORM_RENDERER_MODE_DEFAULT
}

export function changeFormRendererMode({
  mode,
  customComponent = '',
  lastCustomComponent = ''
}) {
  if (mode === FORM_RENDERER_MODE_CUSTOM) {
    const restoredComponent = customComponent || lastCustomComponent || ''
    return {
      mode: FORM_RENDERER_MODE_CUSTOM,
      customComponent: restoredComponent,
      lastCustomComponent: restoredComponent || lastCustomComponent || ''
    }
  }

  return {
    mode: FORM_RENDERER_MODE_DEFAULT,
    customComponent: '',
    lastCustomComponent: customComponent || lastCustomComponent || ''
  }
}

export function shouldPersistFormNodes(mode) {
  return mode !== FORM_RENDERER_MODE_CUSTOM
}
