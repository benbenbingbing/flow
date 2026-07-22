export const NODE_TYPE_DESCRIPTIONS = {
  'bpmn:UserTask': {
    title: '用户任务',
    desc: '人工处理任务，支持审批、会签、或签等操作',
    scene: '审批、审核、确认等需要人工判断的场景'
  },
  'bpmn:ServiceTask': {
    title: '服务任务',
    desc: '自动执行 Java 代码或外部服务调用',
    scene: '状态更新、通知发送、第三方接口调用'
  },
  'bpmn:SendTask': {
    title: '发送任务',
    desc: '向外部系统或用户发送消息',
    scene: '邮件、短信、站内信、消息队列'
  },
  'bpmn:ReceiveTask': {
    title: '接收任务',
    desc: '等待外部事件触发后继续执行',
    scene: '回调等待、异步结果确认'
  },
  'bpmn:ManualTask': {
    title: '手动任务',
    desc: '记录流程外完成的线下工作',
    scene: '纸质文件、现场处理、人工登记'
  },
  'bpmn:BusinessRuleTask': {
    title: '业务规则任务',
    desc: '执行规则表并返回决策结果',
    scene: '审批层级、风险等级、自动分支'
  },
  'bpmn:ScriptTask': {
    title: '脚本任务',
    desc: '执行脚本处理轻量数据逻辑',
    scene: '计算、转换、流程变量赋值'
  },
  'bpmn:CallActivity': {
    title: '调用活动',
    desc: '调用独立子流程',
    scene: '跨流程复用、标准流程编排'
  },
  'bpmn:SubProcess': {
    title: '子流程',
    desc: '封装一组相关任务',
    scene: '复杂流程分段、局部折叠'
  },
  'bpmn:ExclusiveGateway': {
    title: '排他网关',
    desc: '根据条件只选择一条可用分支',
    scene: '互斥条件判断、审批结果分流'
  },
  'bpmn:ParallelGateway': {
    title: '并行网关',
    desc: '同时开启或汇聚多条并行分支',
    scene: '并行办理、并行汇聚'
  },
  'bpmn:InclusiveGateway': {
    title: '包容网关',
    desc: '根据条件选择一条或多条分支',
    scene: '多条件可同时成立的分流与汇聚'
  },
  'bpmn:EventBasedGateway': {
    title: '事件网关',
    desc: '等待多个事件中的首个事件决定后续分支',
    scene: '消息、信号或定时事件竞争'
  },
  'bpmn:SequenceFlow': {
    title: '顺序流',
    desc: '连接节点并控制流转条件',
    scene: '分支条件、默认流、连线动作'
  },
  'bpmn:StartEvent': {
    title: '开始事件',
    desc: '流程实例的起点',
    scene: '流程入口'
  },
  'bpmn:EndEvent': {
    title: '结束事件',
    desc: '流程实例的终点',
    scene: '流程结束'
  }
}

export const NODE_TYPE_TEXT = {
  'bpmn:StartEvent': '开始事件',
  'bpmn:EndEvent': '结束事件',
  'bpmn:UserTask': '用户任务',
  'bpmn:ServiceTask': '服务任务',
  'bpmn:ManualTask': '手动任务',
  'bpmn:ScriptTask': '脚本任务',
  'bpmn:BusinessRuleTask': '业务规则任务',
  'bpmn:SendTask': '发送任务',
  'bpmn:ReceiveTask': '接收任务',
  'bpmn:CallActivity': '调用活动',
  'bpmn:SubProcess': '子流程',
  'bpmn:ExclusiveGateway': '排他网关',
  'bpmn:ParallelGateway': '并行网关',
  'bpmn:InclusiveGateway': '包容网关',
  'bpmn:EventBasedGateway': '事件网关',
  'bpmn:SequenceFlow': '顺序流'
}

export function getNodeTypeDescription(type) {
  return NODE_TYPE_DESCRIPTIONS[type] || { title: '未知节点', desc: '', scene: '' }
}

export function getNodeTypeText(type) {
  return NODE_TYPE_TEXT[type] || type || '未知'
}

export function getNodeTypeTag(type) {
  if (type?.includes('StartEvent')) return 'success'
  if (type?.includes('EndEvent')) return 'danger'
  if (type?.includes('UserTask')) return 'primary'
  if (type?.includes('ServiceTask') || type?.includes('Script') || type?.includes('BusinessRule')) return 'warning'
  if (type?.includes('SendTask') || type?.includes('ReceiveTask')) return 'info'
  if (type?.includes('Gateway')) return 'warning'
  return ''
}

export function buildAssigneeConfig(form) {
  const type = form.assigneeType
  let assigneeValue = ''
  let candidateUsers = ''
  if (type === 'user') {
    assigneeValue = form.assignee || ''
    // 多实例模式下 BPMN 的 candidateUsers 会被清空，额外保存以便回显
    candidateUsers = form.candidateUsers || ''
  } else if (type === 'group' || type === 'role') {
    assigneeValue = form.candidateGroups || ''
  } else if (type === 'expression') {
    assigneeValue = form.candidateUsers || form.candidateGroups || ''
  }

  return {
    assigneeType: form.assigneeType,
    assigneeValue,
    candidateUsers,
    // 会签人员集合（多实例节点使用）
    multiInstanceUsers: form.multiInstanceUsers || '',
    multiInstanceUserIds: form.multiInstanceUserIds || [],
    multiInstanceUsernames: form.multiInstanceUsernames || '',
    multiInstanceGroupIds: form.multiInstanceGroupIds || [],
    multiInstanceGroupCodes: form.multiInstanceGroupCodes || '',
    multiInstanceRoleIds: form.multiInstanceRoleIds || [],
    multiInstanceRoleCodes: form.multiInstanceRoleCodes || '',
    interfaceType: form.interfaceType,
    interfaceName: form.interfaceName,
    interfaceMethod: form.interfaceMethod,
    interfaceParams: form.interfaceParams,
    restMethod: form.restMethod,
    resultMapping: form.resultMapping,
    collectionSource: form.collectionSource,
    collectionInterface: form.collectionInterface
  }
}
