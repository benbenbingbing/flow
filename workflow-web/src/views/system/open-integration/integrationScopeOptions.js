export const INTEGRATION_SCOPE_OPTIONS = [
  { value: 'embed.launch', label: '启动嵌入会话' },
  { value: 'process.definition.read', label: '读取流程定义' },
  { value: 'process.instance.start', label: '启动流程实例' },
  { value: 'process.instance.read', label: '读取流程实例' },
  { value: 'process.task.read', label: '读取流程任务' },
  { value: 'process.message.correlate', label: '关联流程消息' },
  { value: 'process.instance.cancel', label: '取消流程实例' }
]

const scopeLabelByValue = new Map(
  INTEGRATION_SCOPE_OPTIONS.map(option => [option.value, option.label])
)

/**
 * 将 Scope 技术值转换为页面上的中文名称。
 * 未知值保持原样回显，避免新增或历史 Scope 在管理页面上不可见。
 */
export function integrationScopeLabel(value) {
  return scopeLabelByValue.get(value) || value
}
