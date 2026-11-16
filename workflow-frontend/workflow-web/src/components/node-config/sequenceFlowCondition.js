/** 固定入口只引用连线 ID，接口编码及业务参数保存在 BPMN 扩展属性中。 */
export function buildSequenceFlowConditionExpression(flowId) {
  if (!/^[A-Za-z_][A-Za-z0-9_.-]*$/.test(String(flowId || ''))) {
    throw new Error('业务条件连线 ID 不合法')
  }
  return "${sequenceFlowConditionEvaluator.evaluate(execution, '" + flowId + "')}"
}

/** 校验编辑器参数；拒绝空接口及数组，防止保存后退化成无条件分支。 */
export function buildSequenceFlowConditionConfig(form) {
  const providerCode = String(form.providerCode || '').trim()
  if (!providerCode) throw new Error('请选择业务条件接口')
  let extraParams
  try {
    extraParams = JSON.parse(form.extraParamsText?.trim() || '{}')
  } catch {
    throw new Error('业务条件接口参数必须是合法 JSON 对象')
  }
  if (!extraParams || Array.isArray(extraParams) || typeof extraParams !== 'object') {
    throw new Error('业务条件接口参数必须是 JSON 对象')
  }
  return { providerCode, extraParams }
}
