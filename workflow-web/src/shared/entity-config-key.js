export const ENTITY_CONFIG_KEY_MAX_LENGTH = 100

/**
 * 生成实体表单、列表配置共用的固定标识前缀。
 * 前缀保留实体编码原始大小写，并用下划线隔开用户填写的后缀。
 */
export function getEntityConfigKeyPrefix(entityCode) {
  const normalizedEntityCode = String(entityCode ?? '').trim()
  return normalizedEntityCode ? `${normalizedEntityCode}_` : ''
}

/**
 * 将实体编码前缀和新增时填写的标识后缀合成为最终持久化标识。
 * 该方法只用于新增请求；编辑请求必须继续使用服务器返回的原标识。
 */
export function buildEntityConfigKey(entityCode, keySuffix) {
  const prefix = getEntityConfigKeyPrefix(entityCode)
  const normalizedSuffix = String(keySuffix ?? '').trim()
  if (!prefix) {
    throw new Error('实体编码不能为空，无法生成配置标识')
  }
  if (!normalizedSuffix) {
    throw new Error('配置标识不能为空')
  }

  const key = `${prefix}${normalizedSuffix}`
  if (key.length > ENTITY_CONFIG_KEY_MAX_LENGTH) {
    throw new Error(`配置标识最长 ${ENTITY_CONFIG_KEY_MAX_LENGTH} 个字符`)
  }
  return key
}

/**
 * 根据实体编码前缀计算用户可填写的最大后缀长度，保证最终标识不超过后端字段上限。
 */
export function getEntityConfigKeySuffixMaxLength(entityCode) {
  return Math.max(
    0,
    ENTITY_CONFIG_KEY_MAX_LENGTH - getEntityConfigKeyPrefix(entityCode).length
  )
}
