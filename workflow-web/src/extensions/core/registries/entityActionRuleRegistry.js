const conditionDefinitions = new Map()
const permissionOptionProviders = []

/** 适配器安装条件编辑器，type 大写归一；默认参数函数必须返回独立对象。 */
export function registerEntityActionRuleCondition(definition) {
  if (!definition?.type) {
    throw new Error('自定义操作条件必须提供 type')
  }
  conditionDefinitions.set(String(definition.type).toUpperCase(), definition)
}

/** 返回设计器可选条件定义；实际条件判定仍由后端执行。 */
/** 按持久化 type 读取条件定义，兼容大小写。 */
export function getEntityActionRuleConditions() {
  return Array.from(conditionDefinitions.values())
}

/** 按持久化 type 读取条件定义，兼容大小写。 */
export function getEntityActionRuleCondition(type) {
  return conditionDefinitions.get(String(type || '').toUpperCase())
}

/** 适配器追加权限候选提供器；重复安装由统一安装器阻止。 */
export function registerEntityPermissionOptionProvider(provider) {
  if (typeof provider !== 'function') {
    throw new Error('自定义权限选项提供器必须是函数')
  }
  permissionOptionProviders.push(provider)
}

/** 按当前实体上下文收集候选项；提供器错误向上传递，避免显示不完整权限配置。 */
export async function resolveEntityPermissionOptions(context) {
  const result = []
  for (const provider of permissionOptionProviders) {
    const options = await provider(context)
    if (Array.isArray(options)) {
      result.push(...options)
    }
  }
  return result
}
