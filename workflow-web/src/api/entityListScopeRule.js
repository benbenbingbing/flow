import { entityListScopeApi } from './entityListScope'
import { entityListRuntimeApi } from './entityListRuntime'

function parse(value, fallback) {
  if (!value) return fallback
  if (typeof value === 'object') return value
  try {
    return JSON.parse(value)
  } catch {
    return fallback
  }
}

function toPolicyRow(policy, bindings = []) {
  const filter = parse(policy.filterConfig, { type: 'PERSONAL' })
  const boundListKeys = Array.isArray(policy.boundListKeys)
    ? policy.boundListKeys
    : bindings
      .filter(binding => binding.policyId === policy.id && binding.listKey)
      .map(binding => binding.listKey)
  return {
    id: policy.id,
    policyId: policy.id,
    policyKey: policy.policyKey,
    entityCode: policy.entityCode,
    ruleName: policy.policyName,
    description: policy.description,
    presetCode: policy.presetCode || filter.type,
    enabled: policy.enabled,
    status: policy.status,
    version: policy.version,
    reviewRequired: policy.reviewRequired,
    boundListKeys,
    ruleEffect: filter.ruleEffect || 'ALLOW',
    matchConfig: JSON.stringify(filter.audience || { logic: 'OR', conditions: [] }),
    filterConfig: JSON.stringify(filter)
  }
}

export const entityListScopeRuleApi = {
  async getConfiguration(entityCode) {
    return entityListScopeApi.getConfiguration(entityCode)
  },

  async getByEntityCode(entityCode) {
    const configuration = await entityListScopeApi.getConfiguration(entityCode)
    return (configuration?.policies || []).map(policy =>
      toPolicyRow(policy, configuration?.bindings || []))
  },

  async create(data) {
    const filter = parse(data.filterConfig, { version: 1, type: 'PERSONAL' })
    filter.ruleEffect = data.ruleEffect || filter.ruleEffect || 'ALLOW'
    filter.audience = parse(data.matchConfig, filter.audience || {})
    return entityListScopeApi.createPolicy({
      entityCode: data.entityCode,
      policyKey: data.policyKey || `scope_${Date.now()}`,
      policyName: data.ruleName,
      description: data.description || '',
      presetCode: data.filterType || filter.type,
      filterConfig: filter,
      enabled: data.enabled
    })
  },

  async update(id, data) {
    const filter = parse(data.filterConfig, { version: 1, type: 'PERSONAL' })
    filter.ruleEffect = data.ruleEffect || filter.ruleEffect || 'ALLOW'
    filter.audience = parse(data.matchConfig, filter.audience || {})
    return entityListScopeApi.updatePolicy(data.policyId || id, {
      id: data.policyId || id,
      entityCode: data.entityCode,
      policyKey: data.policyKey,
      policyName: data.ruleName,
      description: data.description || '',
      presetCode: data.filterType || filter.type,
      filterConfig: filter,
      enabled: data.enabled
    })
  },

  async delete(row) {
    const policyId = typeof row === 'string' ? row : row.policyId || row.id
    if (policyId) {
      await entityListScopeApi.deletePolicy(policyId)
    }
  },

  updateEnabled(row) {
    const filter = parse(row.filterConfig, { version: 1, type: row.filterType || 'PERSONAL' })
    filter.ruleEffect = row.ruleEffect || filter.ruleEffect || 'ALLOW'
    filter.audience = parse(row.matchConfig, filter.audience || {})
    return entityListScopeApi.updatePolicy(row.policyId || row.id, {
      id: row.policyId || row.id,
      entityCode: row.entityCode,
      policyKey: row.policyKey,
      policyName: row.ruleName,
      description: row.description || '',
      presetCode: row.filterType || filter.type,
      filterConfig: filter,
      enabled: row.enabled
    })
  },

  replaceListBindings(entityCode, listKey, policyIds = []) {
    return entityListScopeApi.replaceListBindings(
      entityCode,
      listKey,
      policyIds.filter(Boolean).map(policyId => ({ policyId, enabled: 1 }))
    )
  },

  previewSql(entityCode, listKey, data = {}) {
    return entityListRuntimeApi.simulate(entityCode, listKey, data)
      .then(result => result.preview)
  },

  publish(entityCode, description) {
    return entityListScopeApi.publish(entityCode, description)
  }
}
