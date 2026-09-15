export const SETTING_VALUE_TYPE_LABELS = Object.freeze({ BOOLEAN: '布尔', NUMBER: '数字', STRING: '字符串', JSON: 'JSON' })

/** 字符串输入框展示原文；数字和 JSON 使用序列化文本，布尔由开关展示。 */
export function settingInputText(setting) {
  // 敏感设置只允许输入新值，不能将掩码或服务端意外返回的值写回数据库。
  if (setting.sensitive) return ''
  return setting.settingValueType === 'STRING' ? setting.value : JSON.stringify(setting.value)
}

/**
 * 按设置类型校验用户输入并返回持久化文本。字符串自动编码，用户无需输入引号；
 * JSON 只允许对象/数组，避免与布尔、数字、字符串的专用输入语义混淆。
 */
export function serializeSettingInput(type, input) {
  let value
  if (type === 'BOOLEAN') {
    if (typeof input !== 'boolean') throw new Error('请输入布尔值')
    value = input
  } else if (type === 'STRING') {
    if (typeof input !== 'string') throw new Error('请输入字符串')
    value = input
  } else if (type === 'NUMBER' || type === 'JSON') {
    try { value = JSON.parse(input) } catch {
      throw new Error(type === 'NUMBER' ? '请输入有效数字' : '请输入有效的 JSON 对象或数组')
    }
    if (type === 'NUMBER' && (typeof value !== 'number' || !Number.isFinite(value))) {
      throw new Error('请输入有限数字')
    }
    if (type === 'JSON' && (value === null || typeof value !== 'object')) {
      throw new Error('JSON 设置值必须是对象或数组')
    }
  } else {
    throw new Error('不支持的设置值类型')
  }
  const text = JSON.stringify(value)
  if (new TextEncoder().encode(text).length > 16 * 1024) throw new Error('设置值不能超过 16 KiB')
  return text
}
