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
  if (type === 'user') {
    assigneeValue = form.assignee || ''
  } else if (type === 'group' || type === 'role') {
    assigneeValue = form.candidateGroups || ''
  } else if (type === 'expression') {
    assigneeValue = form.candidateUsers || form.candidateGroups || ''
  }

  // 基础配置：指定方式与对应的执行人值
  const config = {
    assigneeType: form.assigneeType,
    assigneeValue
  }

  // 仅在指定方式为 interface 时保存接口配置，避免冗余
  if (type === 'interface') {
    config.interfaceType = form.interfaceType
    config.interfaceName = form.interfaceName
    config.interfaceMethod = form.interfaceMethod
    config.interfaceParams = form.interfaceParams
    config.restMethod = form.restMethod
    config.resultMapping = form.resultMapping
  }

  // 仅在启用多实例且集合来源为 interface 时保存集合接口配置，避免冗余
  if (form.isMultiInstance && form.collectionSource === 'interface') {
    config.collectionSource = form.collectionSource
    config.collectionInterface = form.collectionInterface
  }

  return config
}
