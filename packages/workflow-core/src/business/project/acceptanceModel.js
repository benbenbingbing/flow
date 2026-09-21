/** 两端定制表单使用同一初始模型，避免移动端回填覆盖已有的 0 分和未展示字段。 */
export function createAcceptanceValue(value = {}) {
  return { ...value, name: value.name || '', acceptance_scene: value.acceptance_scene || 'FULL_EXTENSION', owner_name: value.owner_name || '', planned_date: value.planned_date || '', acceptance_score: Number(value.acceptance_score ?? 65), description: value.description || '', provider_trace: value.provider_trace || '', extension_result: value.extension_result || '' }
}

export function createDemoProjectValue(value = {}) {
  return { projectName: value.projectName || value.name || '', code: value.code || '', ownerName: value.ownerName || '', budget: value.budget ?? 0, riskScore: value.riskScore ?? 20, description: value.description || '' }
}
