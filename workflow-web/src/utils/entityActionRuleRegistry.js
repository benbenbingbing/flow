const conditionDefinitions = new Map()
const permissionOptionProviders = []

export function registerEntityActionRuleCondition(definition) {
  if (!definition?.type) {
    throw new Error('自定义操作条件必须提供 type')
  }
  conditionDefinitions.set(String(definition.type).toUpperCase(), definition)
}

export function getEntityActionRuleConditions() {
  return Array.from(conditionDefinitions.values())
}

export function getEntityActionRuleCondition(type) {
  return conditionDefinitions.get(String(type || '').toUpperCase())
}

export function registerEntityPermissionOptionProvider(provider) {
  if (typeof provider !== 'function') {
    throw new Error('自定义权限选项提供器必须是函数')
  }
  permissionOptionProviders.push(provider)
}

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
