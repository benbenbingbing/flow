/** 固定参数目录。ID 和日期的原值保持字符串，由使用位置决定后续处理。 */
export const formContextParameters = [
  { key: 'currentUser.userId', label: '当前用户 ID', type: 'USER', sample: '10001' },
  { key: 'currentUser.nickname', label: '当前用户昵称', type: 'STRING', sample: '张三' },
  { key: 'currentUser.departmentId', label: '当前用户部门 ID', type: 'DEPT', sample: '20010' },
  { key: 'currentUser.departmentName', label: '当前用户部门名称', type: 'STRING', sample: '财务部' },
  { key: 'currentUser.organizationId', label: '当前用户组织 ID', type: 'DEPT', sample: '30001' },
  { key: 'currentUser.organizationName', label: '当前用户组织名称', type: 'STRING', sample: '华东分公司' },
  { key: 'processDefinition.key', label: '流程定义编码', type: 'STRING', sample: 'expense_approval' },
  { key: 'processDefinition.name', label: '流程定义名称', type: 'STRING', sample: '费用审批' },
  { key: 'currentProcessInstance.id', label: '当前流程实例 ID', type: 'STRING', sample: '56001', taskOnly: true },
  { key: 'currentProcessInstance.starterUserId', label: '当前流程发起人 ID', type: 'USER', sample: '10008', taskOnly: true },
  { key: 'currentProcessInstance.starterName', label: '当前流程发起人名称', type: 'STRING', sample: '李四', taskOnly: true },
  { key: 'currentProcessNode.key', label: '当前流程节点 ID', type: 'STRING', sample: 'Activity_12yea1s' },
  { key: 'currentProcessNode.code', label: '当前流程节点编码', type: 'STRING', sample: 'finance_review' },
  { key: 'currentProcessNode.name', label: '当前流程节点名称', type: 'STRING', sample: '财务复核' },
  { key: 'currentTask.id', label: '当前待办 ID', type: 'STRING', sample: '78001', taskOnly: true },
  { key: 'currentTask.nodeKey', label: '当前待办节点 ID', type: 'STRING', sample: 'Activity_12yea1s', taskOnly: true },
  { key: 'currentTask.nodeName', label: '当前待办节点名称', type: 'STRING', sample: '财务复核', taskOnly: true },
  { key: 'currentTask.assigneeUserId', label: '当前待办被指派人 ID', type: 'USER', sample: '10015', taskOnly: true },
  { key: 'form.currentMode', label: '当前表单运行模式', type: 'STRING', sample: 'create', options: [
    { value: 'create', label: '新增' }, { value: 'edit', label: '编辑' },
    { value: 'approve', label: '审批' }, { value: 'view', label: '查看' }
  ] },
  { key: 'currentDate', label: '当前日期', type: 'DATE', sample: '2026-11-27' },
  { key: 'currentTime', label: '当前日期和时间', type: 'DATETIME', sample: '2026-11-27 08:30:15' }
]

export const formContextSample = Object.fromEntries(formContextParameters.map(({ key, sample }) => [key, sample]))
export const formContextDefaults = formContextParameters.filter(parameter => !parameter.taskOnly)

/** 条件控件使用参数的值类型。身份 ID 保持字符串，模式只提供固定选项。 */
export const formContextConditionFields = formContextParameters.map(parameter => ({
  fieldCode: 'ctx.' + parameter.key,
  fieldName: parameter.label,
  optionCode: parameter.key,
  fieldType: parameter.options ? 'SELECT' : ['USER', 'DEPT'].includes(parameter.type) ? 'STRING' : parameter.type,
  options: parameter.options
}))
