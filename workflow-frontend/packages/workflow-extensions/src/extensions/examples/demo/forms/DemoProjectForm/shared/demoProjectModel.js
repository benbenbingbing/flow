/** 示例表单专属初始值；示例可以独立启用，不依赖任何业务模块。 */
export function createDemoProjectValue(value = {}) {
  return { projectName: value.projectName || value.name || '', code: value.code || '', ownerName: value.ownerName || '', budget: value.budget ?? 0, riskScore: value.riskScore ?? 20, description: value.description || '' }
}
