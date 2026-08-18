import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  ASSIGNMENT_CONFIG_VERSION,
  buildAssigneeConfig,
  buildUserTaskReferenceOptions,
  MAX_NODE_REFERENCE_DEPTH,
  NODE_REFERENCE_ASSIGNEE_TYPE,
  normalizeDesignerAssigneeConfig,
  normalizeNodeReferenceAssigneeConfig,
  validateNodeReferenceChain,
  wouldCreateNodeReferenceCycle
} from '../process-config/index.js'

const nodeConfigPanelSource = readFileSync(new URL(
  '../../components/NodeConfigPanel.vue',
  import.meta.url
), 'utf8')
const nextApproverConfigEditorSource = readFileSync(new URL(
  '../../components/NextApproverConfigEditor.vue',
  import.meta.url
), 'utf8')

const versionTwo = buildAssigneeConfig({
  assigneeType: 'user',
  assignee: 'alice',
  candidateUsers: 'alice,bob',
  nextApproverSelection: {
    visible: true,
    editable: true,
    source: { type: 'NODE_ASSIGNMENT' }
  }
})
assert.equal(
  versionTwo.assignmentConfigVersion,
  ASSIGNMENT_CONFIG_VERSION,
  '新保存的基础审批人配置必须显式标记 assignmentConfigVersion: 2'
)
assert.equal(versionTwo.assigneeValue, 'alice')
assert.equal(versionTwo.candidateUsers, 'alice,bob')
assert.deepEqual(
  versionTwo.nextApproverSelection.source,
  { type: 'NODE_ASSIGNMENT' },
  '下一审批人必须能显式复用目标节点自身的基础审批人配置'
)
for (const legacyKey of [
  'multiInstanceUsers',
  'multiInstanceUserIds',
  'multiInstanceUsernames',
  'multiInstanceGroupIds',
  'multiInstanceGroupCodes',
  'multiInstanceRoleIds',
  'multiInstanceRoleCodes',
  'collectionSource',
  'collectionInterface',
  'collectionResolverCode',
  'collectionResolverDisplayName',
  'collectionExtraParams'
]) {
  assert.equal(
    Object.hasOwn(versionTwo, legacyKey),
    false,
    `v2 新配置不得继续写入独立会签人员字段: ${legacyKey}`
  )
}

const orderedMultiInstance = buildAssigneeConfig({
  isMultiInstance: true,
  assigneeType: 'user',
  assignee: 'stale-first',
  candidateUserIds: ['bob', 'alice', 'bob'],
  nextApproverSelection: {}
})
assert.equal(orderedMultiInstance.assigneeValue, 'bob')
assert.equal(
  orderedMultiInstance.candidateUsers,
  'bob,alice',
  '多实例固定人员必须去重、保序，并把列表首人同步为 assigneeValue'
)
assert.equal(
  orderedMultiInstance.multiInstanceDecision,
  'countersign',
  '未指定办理模式时默认会签'
)

const orSignConfig = buildAssigneeConfig({
  isMultiInstance: true,
  multiInstanceDecision: 'orsign',
  multiInstanceCompletionRate: 0,
  assigneeType: 'user',
  candidateUserIds: ['alice']
})
assert.equal(orSignConfig.multiInstanceDecision, 'orsign')
assert.equal(
  orSignConfig.multiInstanceCompletionRate,
  1,
  '通过率 0 必须抬到最小值 1'
)

for (const [legacyType, legacyFields] of [
  ['nodeReference', {
    sourceNodeId: 'Task_ManagerReview',
    sourceNodeName: '经理审批'
  }],
  ['NODE_REFERENCE', {
    referencedNodeId: 'Task_ManagerReview',
    referencedNodeName: '经理审批'
  }]
]) {
  const normalizedReference = normalizeNodeReferenceAssigneeConfig({
    assignmentConfigVersion: 2,
    assigneeType: legacyType,
    ...legacyFields
  })
  assert.equal(normalizedReference.assigneeType, NODE_REFERENCE_ASSIGNEE_TYPE)
  assert.equal(normalizedReference.referencedNodeId, 'Task_ManagerReview')
  assert.equal(normalizedReference.referencedNodeName, '经理审批')
  assert.equal(Object.hasOwn(normalizedReference, 'sourceNodeId'), false)
  assert.equal(Object.hasOwn(normalizedReference, 'sourceNodeName'), false)

  const persistedReference = buildAssigneeConfig({
    ...normalizedReference,
    nextApproverSelection: {}
  })
  assert.equal(persistedReference.assigneeType, 'node_reference')
  assert.equal(persistedReference.referencedNodeId, 'Task_ManagerReview')
  assert.equal(persistedReference.referencedNodeName, '经理审批')
  assert.equal(Object.hasOwn(persistedReference, 'sourceNodeId'), false)
  assert.equal(Object.hasOwn(persistedReference, 'sourceNodeName'), false)

  const loadedAgain = normalizeDesignerAssigneeConfig(
    persistedReference,
    {},
    false
  )
  assert.equal(
    buildAssigneeConfig(loadedAgain).referencedNodeId,
    'Task_ManagerReview',
    `${legacyType} 加载、回显并无修改保存时不得丢失引用节点`
  )
}

assert.deepEqual(
  normalizeNodeReferenceAssigneeConfig({
    assigneeType: 'node_reference',
    referencedNodeId: '   ',
    referencedNodeName: '',
    sourceNodeId: 'Task_LegacyFallback',
    sourceNodeName: '历史审批'
  }),
  {
    assigneeType: 'node_reference',
    referencedNodeId: 'Task_LegacyFallback',
    referencedNodeName: '历史审批'
  },
  'canonical 空字符串时必须回退到后端同样支持的首个非空旧字段'
)

const mainProcess = { $type: 'bpmn:Process', id: 'Process_Main' }
const otherProcess = { $type: 'bpmn:Process', id: 'Process_Other' }
assert.deepEqual(
  buildUserTaskReferenceOptions([
    {
      id: 'Task_Current',
      type: 'bpmn:UserTask',
      businessObject: {
        id: 'Task_Current',
        name: '当前审批',
        $parent: mainProcess
      }
    },
    {
      id: 'Task_ManagerReview',
      type: 'bpmn:UserTask',
      businessObject: {
        id: 'Task_ManagerReview',
        name: '经理审批',
        $parent: mainProcess
      }
    },
    {
      id: 'Task_ManagerReview_label',
      type: 'label',
      businessObject: {
        id: 'Task_ManagerReview',
        name: '经理审批',
        $parent: mainProcess
      }
    },
    {
      id: 'Task_Unnamed',
      $type: 'bpmn:UserTask',
      $parent: mainProcess
    },
    {
      id: 'Task_OtherPool',
      type: 'bpmn:UserTask',
      businessObject: {
        id: 'Task_OtherPool',
        name: '其他池审批',
        $parent: otherProcess
      }
    },
    {
      id: 'Service_Notify',
      type: 'bpmn:ServiceTask',
      businessObject: { id: 'Service_Notify', name: '发送通知' }
    }
  ], 'Task_Current'),
  [
    {
      value: 'Task_ManagerReview',
      label: '经理审批',
      nodeId: 'Task_ManagerReview',
      nodeName: '经理审批'
    },
    {
      value: 'Task_Unnamed',
      label: '未命名节点（Task_Unnamed）',
      nodeId: 'Task_Unnamed',
      nodeName: ''
    }
  ],
  '节点选择器只应枚举同一流程的其他 UserTask，并去掉 label 重复项'
)

assert.equal(
  wouldCreateNodeReferenceCycle('Task_A', 'Task_A', {}),
  true,
  '必须拒绝直接自引用'
)
assert.equal(
  wouldCreateNodeReferenceCycle(
    'Task_A',
    'Task_B',
    new Map([['Task_B', 'Task_C'], ['Task_C', 'Task_A']])
  ),
  true,
  '必须拒绝经其他节点回到当前节点的间接引用环'
)
assert.equal(
  wouldCreateNodeReferenceCycle(
    'Task_A',
    'Task_B',
    { Task_B: 'Task_C', Task_C: 'Task_D' }
  ),
  false,
  '不形成环的引用链必须允许保存'
)
assert.equal(
  wouldCreateNodeReferenceCycle(
    'Task_A',
    'Task_B',
    { Task_B: 'Task_C', Task_C: 'Task_B' }
  ),
  true,
  '不得接入自身已经存在循环的引用链'
)
const maximumDepthReferences = Object.fromEntries(
  Array.from({ length: MAX_NODE_REFERENCE_DEPTH - 1 }, (_, index) => [
    `Task_${index + 1}`,
    `Task_${index + 2}`
  ])
)
assert.equal(
  validateNodeReferenceChain(
    'Task_0',
    'Task_1',
    maximumDepthReferences
  ).valid,
  true,
  '引用链必须允许达到后端支持的最大 16 层'
)
assert.deepEqual(
  validateNodeReferenceChain(
    'Task_0',
    'Task_1',
    {
      ...maximumDepthReferences,
      [`Task_${MAX_NODE_REFERENCE_DEPTH}`]:
        `Task_${MAX_NODE_REFERENCE_DEPTH + 1}`
    }
  ),
  { valid: false, reason: 'depth' },
  '超过后端最大深度的引用链必须在设计器保存前阻止'
)
assert.deepEqual(
  validateNodeReferenceChain(
    'Task_A',
    'Task_B',
    { Task_B: 'Task_OtherPool' },
    MAX_NODE_REFERENCE_DEPTH,
    new Set(['Task_A', 'Task_B'])
  ),
  { valid: false, reason: 'invalid_target' },
  '引用链后续节点跨流程或已删除时也必须拒绝保存'
)

const normalizedVersionTwo = normalizeDesignerAssigneeConfig(
  versionTwo,
  {
    collectionSource: 'resolver',
    collectionResolverCode: 'staleLegacyResolver'
  },
  true
)
assert.equal(normalizedVersionTwo.assignmentConfigVersion, 2)
assert.equal(normalizedVersionTwo.legacyAssigneeConfig, null)
assert.equal(normalizedVersionTwo.legacyMultiInstanceConfig, null)
assert.equal(
  normalizedVersionTwo.legacyMultiInstanceMixed,
  false,
  'v2 必须忽略残留的旧 multiInstanceConfig，不能重新进入 legacy 分支'
)

const legacyAssigneeConfig = {
  assigneeType: 'user',
  assigneeValue: 'stale-normal-owner',
  multiInstanceUsernames: 'alice,bob',
  multiInstanceGroupCodes: 'finance',
  multiInstanceRoleCodes: 'ROLE_MANAGER',
  collectionSource: 'variable',
  nextApproverSelection: {
    version: 1,
    visible: true,
    editable: true,
    source: { type: 'NODE_ASSIGNMENT' }
  }
}
const legacyMultiInstanceConfig = {
  collection: '${legacyReviewers}',
  elementVariable: 'reviewer',
  completionCondition: '${nrOfCompletedInstances > 0}'
}
const normalizedLegacy = normalizeDesignerAssigneeConfig(
  legacyAssigneeConfig,
  legacyMultiInstanceConfig,
  true
)
assert.equal(normalizedLegacy.legacyMultiInstanceMixed, true)
assert.equal(normalizedLegacy.assigneeType, 'user')
assert.equal(normalizedLegacy.assigneeValue, 'alice')
assert.equal(normalizedLegacy.candidateUsers, 'alice,bob')
assert.deepEqual(
  normalizedLegacy.legacyAssigneeConfig,
  legacyAssigneeConfig,
  '旧 mixed 会签必须保留原始三类人员来源，不能只投影后覆盖保存'
)
assert.deepEqual(
  normalizedLegacy.legacyMultiInstanceConfig,
  legacyMultiInstanceConfig,
  '旧会签技术参数也必须参与无修改往返'
)

const unionedLegacyUsers = normalizeDesignerAssigneeConfig({
  collectionSource: 'variable',
  collectionResolverCode: 'stale-resolver-must-not-win',
  multiInstanceUsernames: 'alice,bob',
  multiInstanceUserIds: ['bob', 'carol'],
  multiInstanceUsers: 'dave,ROLE_AUDITOR'
}, {
  multiInstanceUsernames: ['carol', 'erin'],
  multiInstanceUserIds: 'frank,alice',
  multiInstanceUsers: 'grace,ROLE_REVIEWER'
}, true)
assert.equal(
  unionedLegacyUsers.assigneeType,
  'user',
  '显式 variable 必须优先，残留 resolverCode 不能把历史静态人员误判成接口'
)
assert.equal(
  unionedLegacyUsers.candidateUsers,
  'alice,bob,carol,erin,frank,dave,grace',
  'username、userId、multiInstanceUsers 与 multiInstanceConfig 必须保序并集'
)
const upgradedUnionedUsers = buildAssigneeConfig({
  ...unionedLegacyUsers,
  isMultiInstance: true,
  assignmentConfigDirty: true,
  assignee: unionedLegacyUsers.assigneeValue
})
assert.equal(upgradedUnionedUsers.assignmentConfigVersion, 2)
assert.equal(upgradedUnionedUsers.assigneeValue, 'alice')
assert.equal(
  upgradedUnionedUsers.candidateUsers,
  'alice,bob,carol,erin,frank,dave,grace',
  '用户确认编辑并升级 v2 时不得丢失任一代历史固定人员字段'
)

const unionedLegacyGroups = normalizeDesignerAssigneeConfig({
  multiInstanceGroupCodes: 'finance,legal',
  multiInstanceGroupIds: ['legal', 'operations']
}, {
  multiInstanceGroupCodes: 'audit,finance',
  multiInstanceGroupIds: 'risk'
}, true)
assert.equal(unionedLegacyGroups.assigneeType, 'group')
assert.equal(
  unionedLegacyGroups.assigneeValue,
  'finance,legal,operations,audit,risk',
  'groupCode/groupId 及 multiInstanceConfig 对应字段必须保序并集'
)
assert.equal(
  buildAssigneeConfig({
    ...unionedLegacyGroups,
    assignmentConfigDirty: true,
    assigneeType: 'group',
    candidateGroups: unionedLegacyGroups.assigneeValue,
    nextApproverSelection: {}
  }).assigneeValue,
  'finance,legal,operations,audit,risk',
  '用户确认编辑组来源并升级 v2 时不得丢失旧 code/id 并集'
)

const unionedLegacyRoles = normalizeDesignerAssigneeConfig({
  multiInstanceRoleCodes: 'ROLE_MANAGER,AUDITOR',
  multiInstanceRoleIds: ['AUDITOR', 'RISK']
}, {
  multiInstanceRoleCodes: 'LEGAL,ROLE_MANAGER',
  multiInstanceRoleIds: 'OPS'
}, true)
assert.equal(unionedLegacyRoles.assigneeType, 'role')
assert.equal(
  unionedLegacyRoles.assigneeValue,
  'ROLE_MANAGER,ROLE_AUDITOR,ROLE_RISK,ROLE_LEGAL,ROLE_OPS',
  'roleCode/roleId 及 ROLE_ 前缀必须归一后保序并集'
)
assert.equal(
  buildAssigneeConfig({
    ...unionedLegacyRoles,
    assignmentConfigDirty: true,
    assigneeType: 'role',
    candidateGroups: unionedLegacyRoles.assigneeValue,
    nextApproverSelection: {}
  }).assigneeValue,
  'ROLE_MANAGER,ROLE_AUDITOR,ROLE_RISK,ROLE_LEGAL,ROLE_OPS',
  '用户确认编辑角色来源并升级 v2 时不得丢失旧 code/id 并集'
)

const unchangedLegacySave = buildAssigneeConfig(normalizedLegacy)
assert.equal(
  Object.hasOwn(unchangedLegacySave, 'assignmentConfigVersion'),
  false,
  '用户未修改旧 mixed 人员时必须保持 legacy 语义，不能伪装成有损 v2'
)
assert.equal(unchangedLegacySave.multiInstanceUsernames, 'alice,bob')
assert.equal(unchangedLegacySave.multiInstanceGroupCodes, 'finance')
assert.equal(unchangedLegacySave.multiInstanceRoleCodes, 'ROLE_MANAGER')

const upgradedLegacySave = buildAssigneeConfig({
  ...normalizedLegacy,
  assignmentConfigDirty: true,
  assigneeType: 'user',
  assignee: 'carol',
  candidateUsers: 'carol,dave'
})
assert.equal(upgradedLegacySave.assignmentConfigVersion, 2)
assert.equal(upgradedLegacySave.assigneeValue, 'carol')
assert.equal(upgradedLegacySave.candidateUsers, 'carol,dave')
for (const legacyKey of [
  'multiInstanceUsernames',
  'multiInstanceGroupCodes',
  'multiInstanceRoleCodes',
  'collectionSource'
]) {
  assert.equal(
    Object.hasOwn(upgradedLegacySave, legacyKey),
    false,
    `用户明确修改基础审批人后必须升级为纯 v2: ${legacyKey}`
  )
}

assert.doesNotMatch(
  nodeConfigPanelSource,
  /<template v-if="!assigneeForm\.isMultiInstance">/,
  '开启多人办理后仍必须展示同一个基础审批人入口'
)
for (const removedBinding of [
  'v-model="assigneeForm.collectionSource"',
  'v-model="assigneeForm.multiInstanceUserIds"',
  'v-model="assigneeForm.multiInstanceGroupIds"',
  'v-model="assigneeForm.multiInstanceRoleIds"',
  'v-model="assigneeForm.collectionResolverCode"'
]) {
  assert.equal(
    nodeConfigPanelSource.includes(removedBinding),
    false,
    `多人办理区不得继续渲染独立人员配置: ${removedBinding}`
  )
}
assert.match(
  nodeConfigPanelSource,
  /const assigneeResolverContext = computed\(\(\) => \(\{[\s\S]*?isMultiInstance[\s\S]*?'MULTI_INSTANCE'[\s\S]*?'ASSIGNEE'/,
  '统一人员接口选择器必须随普通/多实例模式切换受控 resolver usage'
)
assert.match(
  nodeConfigPanelSource,
  /type === 'expression'[\s\S]*?isMultiInstance[\s\S]*?多人办理不支持表达式人员来源/,
  '保存 v2 多实例时必须明确拒绝无法枚举的表达式人员来源'
)
assert.match(
  nodeConfigPanelSource,
  /enabled && assigneeForm\.value\.assigneeType === 'expression'[\s\S]*?isMultiInstance = false[\s\S]*?return/,
  '表达式模式开启多人办理时必须立即回退开关，不能停留在无效状态'
)
assert.match(
  nodeConfigPanelSource,
  /组内启用用户会展开为多人办理参与人并分别生成任务/,
  '用户组在多人办理模式下必须说明展开任务语义'
)
assert.match(
  nodeConfigPanelSource,
  /拥有该角色的启用用户会展开为多人办理参与人并分别生成任务/,
  '角色在多人办理模式下必须说明展开任务语义'
)
assert.match(
  nextApproverConfigEditorSource,
  /value="NODE_ASSIGNMENT">使用本节点审批人/,
  '下一审批人来源必须提供复用基础审批人配置的显式选项'
)
assert.match(
  nextApproverConfigEditorSource,
  /直接引用本节点已配置的审批人规则；规则修改后同步生效，不复制人员名单/,
  '复用本节点审批人时必须明确引用语义，避免被误解为复制名单'
)
assert.match(
  nodeConfigPanelSource,
  /!users\.length && !canLeaveMultiInstanceAssignmentEmpty\.value/,
  '固定人员为空时仅允许由有效的独立下一审批人来源补齐'
)
assert.match(
  nodeConfigPanelSource,
  /!groupCodes\.length[\s\S]*?!canLeaveMultiInstanceAssignmentEmpty\.value/,
  '用户组为空时仅允许由有效的独立下一审批人来源补齐'
)
assert.match(
  nodeConfigPanelSource,
  /!roleCodes\.length[\s\S]*?!canLeaveMultiInstanceAssignmentEmpty\.value/,
  '角色为空时仅允许由有效的独立下一审批人来源补齐'
)
assert.match(
  nodeConfigPanelSource,
  /label="使用其他节点审批人" value="node_reference"/,
  '当前节点指定方式必须提供稳定的其他节点审批人类型'
)
assert.match(
  nodeConfigPanelSource,
  /v-model="assigneeForm\.referencedNodeId"[\s\S]*?filterable[\s\S]*?onReferencedNodeChange/,
  '其他节点审批人必须使用可搜索的受控 UserTask 选择器'
)
assert.match(
  nodeConfigPanelSource,
  /直接引用所选节点的审批人规则[\s\S]*?多人办理时按所选节点规则展开参与人/,
  '节点引用及多实例展开语义必须放在问号提示中说明'
)
assert.match(
  nodeConfigPanelSource,
  /validateNodeReferenceChain\([\s\S]*?节点审批人引用不能形成循环/,
  '节点引用保存前必须执行基础环和链深度检测'
)
assert.match(
  nodeConfigPanelSource,
  /referenceOptionsRevision[\s\S]*?commandStack\.changed/,
  '节点选择列表必须随画布新增、删除、改名及撤销命令刷新'
)
assert.match(
  nodeConfigPanelSource,
  /type === NODE_REFERENCE_ASSIGNEE_TYPE[\s\S]*?updates\.assignee = null[\s\S]*?updates\.candidateUsers = null[\s\S]*?updates\.candidateGroups = null/,
  '普通节点引用保存时必须清理旧 BPMN 静态办理人和候选属性'
)
assert.match(
  nodeConfigPanelSource,
  /function updateMultiInstance\(\)[\s\S]*?assignee: '\$\{'[\s\S]*?candidateGroups: undefined[\s\S]*?candidateUsers: undefined/,
  '多实例节点引用必须只保留元素变量 assignee，并清理旧候选属性'
)

console.log('assignment config v2 contract tests passed')
