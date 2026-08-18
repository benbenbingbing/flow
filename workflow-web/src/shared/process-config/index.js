import { createNextApproverSelectionConfig } from '../next-approver.js'

export const LEGACY_MULTI_INSTANCE_COLLECTION = '${_wfMultiInstanceUsers_}'
export const ASSIGNMENT_CONFIG_VERSION = 2
export const NODE_REFERENCE_ASSIGNEE_TYPE = 'node_reference'
export const MAX_NODE_REFERENCE_DEPTH = 16
export const MULTI_INSTANCE_DECISION_COUNTERSIGN = 'countersign'
export const MULTI_INSTANCE_DECISION_ORSIGN = 'orsign'
export const DEFAULT_MULTI_INSTANCE_COMPLETION_RATE = 100
export const MIN_MULTI_INSTANCE_COMPLETION_RATE = 1

function firstNonBlankString(...values) {
  for (const value of values) {
    const normalized = String(value ?? '').trim()
    if (normalized) return normalized
  }
  return ''
}

/**
 * 将历史节点引用字段归一为 v2 canonical 契约。
 * referencedNodeId 是运行时可信主键，名称只用于设计器回显；旧 sourceNode*
 * 和不同大小写的 nodeReference 类型仅在读取时兼容，新保存统一写 canonical 字段。
 */
export function normalizeNodeReferenceAssigneeConfig(value = {}) {
  const source = configObject(value)
  const rawType = String(source.assigneeType || '').trim()
  const normalizedTypeKey = rawType.replace(/[_\-\s]/g, '').toLowerCase()
  if (normalizedTypeKey !== 'nodereference') return { ...source }

  const normalized = {
    ...source,
    assigneeType: NODE_REFERENCE_ASSIGNEE_TYPE,
    referencedNodeId: firstNonBlankString(
      source.referencedNodeId,
      source.sourceNodeId
    ),
    referencedNodeName: firstNonBlankString(
      source.referencedNodeName,
      source.sourceNodeName
    )
  }
  delete normalized.sourceNodeId
  delete normalized.sourceNodeName
  return normalized
}

/** 从 elementRegistry 结果中提取同一流程可引用的其他 UserTask。 */
export function buildUserTaskReferenceOptions(elements, currentNodeId) {
  const currentId = String(currentNodeId || '').trim()
  const candidates = Array.isArray(elements) ? elements : []
  const owningProcessId = element => {
    let cursor = element?.businessObject || element
    const visited = new Set()
    while (cursor && !visited.has(cursor)) {
      visited.add(cursor)
      if (cursor.$type === 'bpmn:Process') {
        return String(cursor.id || '').trim()
      }
      cursor = cursor.$parent
    }
    return ''
  }
  const currentElement = candidates.find(element =>
    element?.type !== 'label'
    && String(element?.id || element?.businessObject?.id || '').trim()
      === currentId)
  const currentProcessId = owningProcessId(currentElement)
  const seen = new Set()
  const options = []
  for (const element of candidates) {
    if (element?.type === 'label') continue
    const businessObject = element?.businessObject || element
    const type = element?.type || businessObject?.$type
    const id = String(element?.id || businessObject?.id || '').trim()
    if (type !== 'bpmn:UserTask' || !id || id === currentId || seen.has(id)) {
      continue
    }
    const candidateProcessId = owningProcessId(element)
    if (currentProcessId && candidateProcessId
        && candidateProcessId !== currentProcessId) {
      continue
    }
    seen.add(id)
    const nodeName = String(businessObject?.name || element?.name || '').trim()
    options.push({
      value: id,
      label: nodeName || `未命名节点（${id}）`,
      nodeId: id,
      nodeName
    })
  }
  return options
}

/**
 * 判断新增“当前节点 -> 被引用节点”关系是否形成直接或间接环。
 * referencesByNodeId 可传 Map 或普通对象；遇到被引用链自身已有环也拒绝接入。
 */
export function wouldCreateNodeReferenceCycle(
  currentNodeId,
  referencedNodeId,
  referencesByNodeId = {}
) {
  return !validateNodeReferenceChain(
    currentNodeId,
    referencedNodeId,
    referencesByNodeId,
    Number.POSITIVE_INFINITY
  ).valid
}

/** 校验引用链的环与最大深度，深度口径与后端解析器保持一致。 */
export function validateNodeReferenceChain(
  currentNodeId,
  referencedNodeId,
  referencesByNodeId = {},
  maxDepth = MAX_NODE_REFERENCE_DEPTH,
  validNodeIds = null
) {
  const currentId = String(currentNodeId || '').trim()
  let cursor = String(referencedNodeId || '').trim()
  if (!currentId || !cursor) return { valid: true, reason: '' }

  const visited = new Set([currentId])
  const nextReference = nodeId => referencesByNodeId instanceof Map
    ? referencesByNodeId.get(nodeId)
    : referencesByNodeId?.[nodeId]
  const allowedNodeIds = validNodeIds == null
    ? null
    : validNodeIds instanceof Set
      ? validNodeIds
      : new Set(validNodeIds)
  let depth = 0
  while (cursor) {
    if (allowedNodeIds && !allowedNodeIds.has(cursor)) {
      return { valid: false, reason: 'invalid_target' }
    }
    if (visited.has(cursor)) return { valid: false, reason: 'cycle' }
    visited.add(cursor)
    depth += 1
    if (depth > maxDepth) return { valid: false, reason: 'depth' }
    cursor = String(nextReference(cursor) || '').trim()
  }
  return { valid: true, reason: '', depth }
}

/** 与后端 MultiInstanceVariableNames.sanitizeNodeId 保持同一套清洗规则。 */
export function sanitizeMultiInstanceNodeId(nodeId) {
  const sanitizedNodeId = String(nodeId || 'node')
    .trim()
    .replace(/[^A-Za-z0-9_]/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_+|_+$/g, '')
  return sanitizedNodeId || 'node'
}

export function buildNodeScopedMultiInstanceCollection(
  nodeId,
  currentCollection = ''
) {
  const current = String(currentCollection || '').trim()
  if (current && current !== LEGACY_MULTI_INSTANCE_COLLECTION) {
    return current
  }
  return '${_wfMultiInstanceUsers_' + sanitizeMultiInstanceNodeId(nodeId) + '}'
}

export function buildNodeScopedMultiInstanceApprovedCountVariable(nodeId) {
  return '${_wf_mi_approved_count_' + sanitizeMultiInstanceNodeId(nodeId) + '}'
}

export function buildNodeScopedMultiInstanceRejectedVariable(nodeId) {
  return '${_wf_mi_rejected_' + sanitizeMultiInstanceNodeId(nodeId) + '}'
}

export function normalizeMultiInstanceDecision(value) {
  const normalized = String(value || '')
    .trim()
    .toLowerCase()
    .replace(/[-\s]/g, '_')
  if (normalized === 'orsign'
    || normalized === 'or_sign'
    || normalized === 'or'
    || normalized === 'any') {
    return MULTI_INSTANCE_DECISION_ORSIGN
  }
  return MULTI_INSTANCE_DECISION_COUNTERSIGN
}

export function normalizeMultiInstanceCompletionRate(value) {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    return DEFAULT_MULTI_INSTANCE_COMPLETION_RATE
  }
  return Math.min(
    100,
    Math.max(MIN_MULTI_INSTANCE_COMPLETION_RATE, Math.round(parsed))
  )
}

export function normalizeMultiInstanceNeedAllApprovers(value) {
  if (value === true) return true
  if (typeof value === 'string') {
    return value.trim().toLowerCase() === 'true'
  }
  return false
}

function toExpressionVariable(value) {
  const raw = String(value || '').trim()
  return raw.startsWith('${') && raw.endsWith('}')
    ? raw.substring(2, raw.length - 1).trim()
    : raw
}

/**
 * 按办理模式生成 Flowable 多实例完成条件。
 * 或签：一人通过或一人驳回即结束。
 * 会签：按通过人数达标通过；剩下的人全通过也凑不够则失败。
 * 开启「全部办完」后只等全员办理，再由后端按通过率写 approved。
 */
export function buildMultiInstanceCompletionCondition({
  decision,
  completionRate,
  needAllApprovers,
  nodeId
} = {}) {
  const rejectedVariable = toExpressionVariable(
    buildNodeScopedMultiInstanceRejectedVariable(nodeId)
  )
  const approvedCountVariable = toExpressionVariable(
    buildNodeScopedMultiInstanceApprovedCountVariable(nodeId)
  )
  const normalizedDecision = normalizeMultiInstanceDecision(decision)
  if (normalizedDecision === MULTI_INSTANCE_DECISION_ORSIGN) {
    return '${' + rejectedVariable + ' || ' + approvedCountVariable + ' >= 1}'
  }
  if (normalizeMultiInstanceNeedAllApprovers(needAllApprovers)) {
    return '${nrOfCompletedInstances >= nrOfInstances}'
  }
  const safeRate = normalizeMultiInstanceCompletionRate(completionRate)
  const passCondition =
    `${approvedCountVariable} * 100 >= nrOfInstances * ${safeRate}`
  const remainingCannotMeet =
    `(${approvedCountVariable} + nrOfInstances - nrOfCompletedInstances) * 100 < nrOfInstances * ${safeRate}`
  return '${' + passCondition + ' || ' + remainingCannotMeet + '}'
}

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
    title: '脚本任务（已禁用）',
    desc: '历史节点仅供识别，生产运行时不会执行',
    scene: '请迁移为已注册的服务任务或流程动作'
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
  'bpmn:ScriptTask': '脚本任务（已禁用）',
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

export function getProcessConditionFieldCode(field) {
  return String(field?.fieldCode || field?.fieldName || '').trim()
}

export function getProcessConditionFieldLabel(field) {
  return String(
    field?.fieldLabel
    || field?.fieldName
    || field?.fieldCode
    || ''
  ).trim()
}

export function getProcessConditionFieldType(field) {
  const typeMap = {
    string: 'string',
    text: 'string',
    number: 'number',
    integer: 'number',
    decimal: 'number',
    select: 'select',
    multi_select: 'select',
    radio: 'select',
    checkbox: 'select',
    date: 'date',
    datetime: 'date',
    boolean: 'boolean',
    user: 'string',
    dept: 'string'
  }
  return typeMap[String(field?.fieldType || '').toLowerCase()] || 'string'
}

function configObject(value) {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value
    : {}
}

function configValues(value) {
  const seen = new Set()
  const result = []
  const collect = raw => {
    if (Array.isArray(raw)) {
      raw.forEach(collect)
      return
    }
    for (const item of String(raw || '').split(',')) {
      const normalized = item.trim()
      if (!normalized || seen.has(normalized)) continue
      seen.add(normalized)
      result.push(normalized)
    }
  }
  collect(value)
  return result
}

function hasLegacyMultiInstanceAssignment(config, multiInstanceConfig) {
  const values = [
    config.multiInstanceUsers,
    config.multiInstanceUserIds,
    config.multiInstanceUsernames,
    config.multiInstanceGroupIds,
    config.multiInstanceGroupCodes,
    config.multiInstanceRoleIds,
    config.multiInstanceRoleCodes,
    config.collectionResolverCode,
    config.collectionInterface,
    multiInstanceConfig.multiInstanceUsers,
    multiInstanceConfig.multiInstanceUserIds,
    multiInstanceConfig.multiInstanceUsernames,
    multiInstanceConfig.multiInstanceGroupIds,
    multiInstanceConfig.multiInstanceGroupCodes,
    multiInstanceConfig.multiInstanceRoleIds,
    multiInstanceConfig.multiInstanceRoleCodes,
    multiInstanceConfig.collectionResolverCode,
    multiInstanceConfig.collectionInterface
  ]
  return values.some(value => configValues(value).length > 0)
    || ['interface', 'resolver'].includes(String(
      config.collectionSource || multiInstanceConfig.collectionSource || ''
    ).trim().toLowerCase())
}

/**
 * 将历史多实例独立人员配置投影到统一审批人表单。
 *
 * 历史配置允许用户、组、角色混合，当前基础表单一次只表达一种指定方式；
 * 因此原始配置同时作为 passthrough 保存，只有用户实际修改审批人后才升级为 v2，
 * 避免仅打开并保存节点就缩小原有参与人集合。
 */
export function normalizeDesignerAssigneeConfig(
  assigneeConfig,
  multiInstanceConfig,
  isMultiInstance
) {
  const config = normalizeNodeReferenceAssigneeConfig(assigneeConfig)
  const loopConfig = configObject(multiInstanceConfig)
  const version = Number(config.assignmentConfigVersion || 0)
  const hasLegacyAssignment = Boolean(isMultiInstance)
    && version < ASSIGNMENT_CONFIG_VERSION
    && hasLegacyMultiInstanceAssignment(config, loopConfig)

  if (!hasLegacyAssignment) {
    return {
      ...config,
      legacyAssigneeConfig: null,
      legacyMultiInstanceConfig: null,
      legacyMultiInstanceMixed: false,
      assignmentConfigDirty: false
    }
  }

  const collectionSource = String(
    config.collectionSource || loopConfig.collectionSource || ''
  ).trim().toLowerCase()
  const resolverCode = String(
    config.collectionResolverCode
      || loopConfig.collectionResolverCode
      || config.collectionInterface
      || loopConfig.collectionInterface
      || ''
  ).trim()
  const resolverAssignment = ['interface', 'resolver'].includes(collectionSource)
    || (!collectionSource && Boolean(resolverCode))

  const mixedValues = configValues([
    config.multiInstanceUsers,
    loopConfig.multiInstanceUsers
  ])
  const mixedUsers = mixedValues.filter(value => !value.startsWith('ROLE_'))
  const mixedRoles = mixedValues
    .filter(value => value.startsWith('ROLE_'))
    .map(value => value.slice(5))

  // 各历史版本曾分别保存 username/code、ID 和 mixed CSV。这里与后端
  // LegacyMultiInstanceAssignmentParser 一致：按字段顺序取并集并保留首序。
  const users = configValues([
    config.multiInstanceUsernames,
    config.multiInstanceUserIds,
    loopConfig.multiInstanceUsernames,
    loopConfig.multiInstanceUserIds,
    mixedUsers
  ])
  const groups = configValues([
    config.multiInstanceGroupCodes,
    config.multiInstanceGroupIds,
    loopConfig.multiInstanceGroupCodes,
    loopConfig.multiInstanceGroupIds
  ])
  const roles = configValues([
    config.multiInstanceRoleCodes,
    config.multiInstanceRoleIds,
    loopConfig.multiInstanceRoleCodes,
    loopConfig.multiInstanceRoleIds,
    mixedRoles
  ].map(values => configValues(values).map(value =>
    value.startsWith('ROLE_') ? value.slice(5) : value)))

  const configuredKinds = [
    resolverAssignment,
    users.length > 0,
    groups.length > 0,
    roles.length > 0
  ].filter(Boolean).length
  const normalized = { ...config }
  if (resolverAssignment) {
    normalized.assigneeType = 'interface'
    normalized.resolverCode = resolverCode
    normalized.resolverDisplayName =
      config.collectionResolverDisplayName || resolverCode
    normalized.extraParams = configObject(
      config.collectionExtraParams || loopConfig.collectionExtraParams
    )
  } else if (users.length) {
    normalized.assigneeType = 'user'
    normalized.assigneeValue = users[0] || ''
    normalized.candidateUsers = users.join(',')
  } else if (groups.length) {
    normalized.assigneeType = 'group'
    normalized.assigneeValue = groups.join(',')
  } else if (roles.length) {
    normalized.assigneeType = 'role'
    normalized.assigneeValue = roles.map(value => `ROLE_${value}`).join(',')
  }

  return {
    ...normalized,
    legacyAssigneeConfig: JSON.parse(JSON.stringify(config)),
    legacyMultiInstanceConfig: JSON.parse(JSON.stringify(loopConfig)),
    legacyMultiInstanceMixed: configuredKinds > 1,
    assignmentConfigDirty: false
  }
}

export function buildAssigneeConfig(form) {
  const normalizedReference = normalizeNodeReferenceAssigneeConfig(form)
  const type = normalizedReference.assigneeType || form.assigneeType
  let assigneeValue = ''
  let candidateUsers = ''
  if (type === 'user') {
    const selectedUsers = configValues(
      form.candidateUserIds?.length
        ? form.candidateUserIds
        : form.candidateUsers
    )
    if (form.isMultiInstance) {
      // 多实例固定人员是一个有序集合。兼容调用方只传 assignee 的情况，
      // 并确保 assigneeValue 永远与集合首人一致。
      const participants = configValues(
        selectedUsers.length ? selectedUsers : [form.assignee]
      )
      assigneeValue = participants[0] || ''
      candidateUsers = participants.join(',')
    } else {
      assigneeValue = form.assignee || ''
      candidateUsers = form.candidateUsers || selectedUsers.join(',')
    }
  } else if (type === 'group' || type === 'role') {
    assigneeValue = form.candidateGroups || ''
  } else if (type === 'expression') {
    assigneeValue = form.candidateUsers || form.candidateGroups || ''
  }

  const nextApproverSelection = createNextApproverSelectionConfig(
    form.nextApproverSelection
  )
  if (form.legacyAssigneeConfig && !form.assignmentConfigDirty) {
    return {
      ...form.legacyAssigneeConfig,
      nextApproverSelection
    }
  }

  return {
    assignmentConfigVersion: ASSIGNMENT_CONFIG_VERSION,
    assigneeType: type,
    assigneeValue,
    candidateUsers,
    resolverCode: form.resolverCode || form.interfaceName || '',
    resolverDisplayName: form.resolverDisplayName || '',
    extraParams: normalizeJsonObject(form.extraParams, form.extraParamsText),
    interfaceType: 'resolver',
    interfaceName: form.resolverCode || form.interfaceName || '',
    interfaceMethod: form.interfaceMethod,
    interfaceParams: form.interfaceParams,
    restMethod: form.restMethod,
    resultMapping: form.resultMapping,
    ...(type === NODE_REFERENCE_ASSIGNEE_TYPE
      ? {
          referencedNodeId: normalizedReference.referencedNodeId,
          referencedNodeName: normalizedReference.referencedNodeName
        }
      : {}),
    nextApproverSelection,
    ...(form.isMultiInstance
      ? {
          multiInstanceDecision: normalizeMultiInstanceDecision(
            form.multiInstanceDecision
          ),
          multiInstanceCompletionRate: normalizeMultiInstanceCompletionRate(
            form.multiInstanceCompletionRate
          ),
          multiInstanceNeedAllApprovers: normalizeMultiInstanceNeedAllApprovers(
            form.multiInstanceNeedAllApprovers
          )
        }
      : {})
  }
}

function normalizeJsonObject(value, text) {
  if (value && !Array.isArray(value) && typeof value === 'object') {
    return value
  }
  if (!text) return {}
  try {
    const parsed = JSON.parse(text)
    return parsed && !Array.isArray(parsed) && typeof parsed === 'object'
      ? parsed
      : {}
  } catch {
    return {}
  }
}
