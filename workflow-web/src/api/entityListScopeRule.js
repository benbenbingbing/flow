import { entityListScopeApi } from './entityListScope'
import { entityListRuntimeApi } from './entityListRuntime'

function combine(configuration) {
  const policies = new Map((configuration?.policies || []).map(policy => [policy.id, policy]))
  return (configuration?.bindings || []).map(binding => {
    const policy = policies.get(binding.policyId) || {}
    return {
      ...binding,
      id: binding.id,
      bindingId: binding.id,
      policyId: policy.id,
      policyKey: policy.policyKey,
      ruleName: policy.policyName,
      description: policy.description,
      presetCode: policy.presetCode,
      enabled: binding.enabled,
      listKey: binding.listKey || '',
      ruleEffect: binding.ruleEffect || 'ALLOW',
      matchConfig: JSON.stringify(binding.matchConfig || {}),
      filterConfig: JSON.stringify(policy.filterConfig || {}),
      status: policy.status,
      version: policy.version,
      reviewRequired: policy.reviewRequired
    }
  })
}

function parse(value, fallback) {
  if (!value) return fallback
  if (typeof value === 'object') return value
  try {
    return JSON.parse(value)
  } catch {
    return fallback
  }
}

export const entityListScopeRuleApi = {
  async getByEntityCode(entityCode) {
    return combine(await entityListScopeApi.getConfiguration(entityCode))
  },

  async create(data) {
    const policy = await entityListScopeApi.createPolicy({
      entityCode: data.entityCode,
      policyKey: data.policyKey || `scope_${Date.now()}`,
      policyName: data.ruleName,
      description: data.description || '',
      presetCode: data.filterType || parse(data.filterConfig, {}).type,
      filterConfig: parse(data.filterConfig, { version: 1, type: 'PERSONAL' }),
      enabled: data.enabled
    })
    return entityListScopeApi.createBinding({
      entityCode: data.entityCode,
      policyId: policy.id,
      listKey: data.listKey || null,
      matchConfig: parse(data.matchConfig, {}),
      ruleEffect: data.ruleEffect || 'ALLOW',
      enabled: data.enabled
    })
  },

  async update(id, data) {
    await entityListScopeApi.updatePolicy(data.policyId, {
      id: data.policyId,
      entityCode: data.entityCode,
      policyKey: data.policyKey,
      policyName: data.ruleName,
      description: data.description || '',
      presetCode: data.filterType || parse(data.filterConfig, {}).type,
      filterConfig: parse(data.filterConfig, { version: 1, type: 'PERSONAL' }),
      enabled: data.enabled
    })
    return entityListScopeApi.updateBinding(id, {
      id,
      entityCode: data.entityCode,
      policyId: data.policyId,
      listKey: data.listKey || null,
      matchConfig: parse(data.matchConfig, {}),
      ruleEffect: data.ruleEffect || 'ALLOW',
      enabled: data.enabled
    })
  },

  async delete(row) {
    const bindingId = typeof row === 'string' ? row : row.bindingId || row.id
    const policyId = typeof row === 'string' ? null : row.policyId
    await entityListScopeApi.deleteBinding(bindingId)
    if (policyId) {
      await entityListScopeApi.deletePolicy(policyId)
    }
  },

  updateEnabled(row) {
    return entityListScopeApi.updateBinding(row.bindingId || row.id, {
      id: row.bindingId || row.id,
      entityCode: row.entityCode,
      policyId: row.policyId,
      listKey: row.listKey || null,
      matchConfig: parse(row.matchConfig, {}),
      ruleEffect: row.ruleEffect,
      enabled: row.enabled
    })
  },

  previewSql(entityCode, listKey, data = {}) {
    return entityListRuntimeApi.simulate(entityCode, listKey, data)
      .then(result => result.preview)
  },

  publish(entityCode, description) {
    return entityListScopeApi.publish(entityCode, description)
  }
}
