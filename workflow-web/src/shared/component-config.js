import { sanitizeConfigObject } from '@flow/workflow-core/config-runtime'
import { normalizeCascaderOptions } from '@flow/workflow-core/cascader-runtime'

/**
 * 级联树使用自己的节点白名单和规模校验。通用配置的深度/数组裁剪会截断合法树，
 * 因此仅对清单声明的级联选项单独规范化；编辑其他参数时也要完整保留已有树。
 */
export function sanitizeComponentConfig(schema, value) {
  if (!schema.some(item => item.key === 'cascaderOptions' && item.type === 'json')) {
    return sanitizeConfigObject(value)
  }
  const { cascaderOptions, ...rest } = value
  const result = sanitizeConfigObject(rest)
  if (cascaderOptions != null) result.cascaderOptions = normalizeCascaderOptions(cascaderOptions)
  return result
}
