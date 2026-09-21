/** 移动主题的纯配置协议；后台编辑器与移动端共用，不包含 DOM 或组件库依赖。 */
export const MOBILE_THEME_SETTING_KEY = 'ui.mobile.theme'
export const DEFAULT_MOBILE_THEME = Object.freeze({ version: 1, preset: 'green', primaryColor: '#196B62', backgroundColor: '#F4F7F6', surfaceColor: '#FFFFFF' })
export const MOBILE_THEME_PRESETS = Object.freeze([
  { id: 'green', name: '青绿', primaryColor: '#196B62', backgroundColor: '#F4F7F6', surfaceColor: '#FFFFFF' },
  { id: 'blue', name: '经典蓝', primaryColor: '#2563EB', backgroundColor: '#F7F8FA', surfaceColor: '#FFFFFF' },
  { id: 'purple', name: '雅紫', primaryColor: '#722ED1', backgroundColor: '#F8F7FB', surfaceColor: '#FFFFFF' },
  { id: 'orange', name: '暖橙', primaryColor: '#B45309', backgroundColor: '#FAF8F5', surfaceColor: '#FFFFFF' },
  { id: 'rose', name: '玫瑰红', primaryColor: '#B82F61', backgroundColor: '#FCF7F9', surfaceColor: '#FFFFFF' },
  { id: 'cyan', name: '湖青', primaryColor: '#087E8B', backgroundColor: '#F3F9FA', surfaceColor: '#FFFFFF' },
  { id: 'slate', name: '石墨灰', primaryColor: '#475569', backgroundColor: '#F6F7F9', surfaceColor: '#FFFFFF' }
].map(item => Object.freeze({ version: 1, ...item })))
const fields = ['version', 'preset', 'primaryColor', 'backgroundColor', 'surfaceColor']
const rgb = color => [1, 3, 5].map(offset => parseInt(color.slice(offset, offset + 2), 16))
const hex = channels => '#' + channels.map(value => Math.round(value).toString(16).padStart(2, '0')).join('').toUpperCase()
const mix = (base, tint, weight) => hex(rgb(base).map((value, index) => value * (1 - weight) + rgb(tint)[index] * weight))
function luminance(color) {
  const values = rgb(color).map(value => { const channel = value / 255; return channel <= .04045 ? channel / 12.92 : ((channel + .055) / 1.055) ** 2.4 })
  return values[0] * .2126 + values[1] * .7152 + values[2] * .0722
}
/** 返回两个六位十六进制颜色的相对亮度对比度，用于派生可读的前景色。 */
export function colorContrast(first, second) {
  const values = [luminance(first), luminance(second)].sort((a, b) => b - a)
  return (values[0] + .05) / (values[1] + .05)
}

/** 严格校验完整配置；拒绝 CSS 表达式、未知属性和深色背景，第一版仅提供浅色主题。 */
export function normalizeMobileTheme(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).some(key => !fields.includes(key)) || fields.some(key => !Object.hasOwn(input, key))) throw new Error('主题配置须包含完整的颜色配置')
  if (input.version !== 1 || ![...MOBILE_THEME_PRESETS.map(item => item.id), 'custom'].includes(input.preset)) throw new Error('不支持的主题版本或预设')
  const value = { version: 1, preset: input.preset }
  for (const key of fields.slice(2)) {
    if (typeof input[key] !== 'string' || !/^#[0-9a-f]{6}$/i.test(input[key])) throw new Error('颜色须使用 #RRGGBB 格式')
    value[key] = input[key].toUpperCase()
  }
  if (luminance(value.backgroundColor) < .6 || luminance(value.surfaceColor) < .6) throw new Error('当前仅支持浅色主题，请选择较浅的页面背景和内容背景')
  // 预设名称仅是编辑提示，实际效果始终由持久化的完整颜色值决定。
  const preset = MOBILE_THEME_PRESETS.find(item => item.id === value.preset)
  if (preset && fields.slice(2).some(key => preset[key] !== value[key])) value.preset = 'custom'
  return value
}

/** 从一个主色派生全部外观变量；文字单独保证对比度，不改变用户选择的按钮底色。 */
export function mobileThemeVariables(input) {
  const theme = normalizeMobileTheme(input)
  const primary = theme.primaryColor, surface = theme.surfaceColor, background = theme.backgroundColor
  const soft = mix(surface, primary, .09)
  const readable = color => {
    for (let step = 0; step <= 20; step++) {
      const candidate = mix(color, '#000000', step / 20)
      if (Math.min(colorContrast(candidate, surface), colorContrast(candidate, background), colorContrast(candidate, soft)) >= 4.5) return candidate
    }
    return '#000000'
  }
  const foreground = colorContrast(primary, '#FFFFFF') >= 4.5 ? '#FFFFFF' : '#000000'
  const text = '#1F2937', accentText = readable(primary), muted = readable('#74817D')
  const border = mix(surface, text, .12), inset = mix(surface, text, .035)
  return {
    '--flow-mobile-accent': primary, '--flow-mobile-accent-text': accentText, '--flow-mobile-on-accent': foreground,
    '--flow-mobile-accent-soft': soft, '--flow-mobile-accent-border': mix(surface, primary, .25),
    '--flow-mobile-active': mix(primary, '#000000', .12), '--flow-mobile-text': text, '--flow-mobile-muted': muted,
    '--flow-mobile-surface': surface, '--flow-mobile-background': background, '--flow-mobile-border': border,
    '--flow-mobile-inset': inset, '--flow-mobile-success': '#196B62', '--flow-mobile-success-soft': '#E8F4F0',
    '--van-primary-color': primary, '--van-text-color': text, '--van-text-color-2': muted, '--van-text-color-3': muted,
    '--van-background': background, '--van-background-2': surface, '--van-background-3': inset,
    '--van-border-color': border, '--van-active-color': inset, '--van-button-primary-color': foreground,
    '--van-button-primary-border-color': accentText, '--van-button-primary-background': primary,
    '--van-tabbar-background': surface, '--van-tabbar-item-active-background': surface,
    '--van-tabbar-item-active-color': accentText, '--van-nav-bar-icon-color': accentText,
    '--van-nav-bar-text-color': accentText, '--van-cell-background': surface,
    '--van-field-input-text-color': text, '--van-field-placeholder-text-color': muted,
    '--van-popup-background': surface, '--van-action-sheet-item-background': surface,
    '--van-action-sheet-cancel-button-background': surface, '--van-dialog-background': surface,
    '--van-button-default-color': text, '--van-button-default-background': surface,
    '--van-button-default-border-color': border, '--van-button-plain-background': surface,
    '--van-dialog-confirm-button-text-color': accentText, '--van-picker-confirm-action-color': accentText,
    '--van-checkbox-checked-icon-color': accentText, '--van-radio-checked-icon-color': accentText,
    '--van-switch-on-background': accentText, '--van-tag-primary-color': accentText,
    '--van-tabs-nav-background': surface, '--van-tab-active-text-color': accentText
  }
}
