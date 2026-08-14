import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  buildChangedNextApproverSelections,
  createNextApproverDraftMap,
  createNextApproverOptionsRequestSignature,
  createNextApproverSelectionConfig,
  hasNextApproverPresentation,
  normalizeNextApproverPreview,
  normalizeNextApproverScope,
  normalizeUserKeys,
  reconcileNextApproverDraftState,
  reorderNextApproverValues,
  validateNextApproverDraft,
  validateNextApproverSelectionConfig
} from '../next-approver.js'
import {
  buildAssigneeConfig,
  buildNodeScopedMultiInstanceCollection
} from '../process-config/index.js'

const approvalDecisionPanelSource = readFileSync(new URL(
  '../../views/entity/components/approval/ApprovalDecisionPanel.vue',
  import.meta.url
), 'utf8')
const controlledUserSelectorSource = readFileSync(new URL(
  '../../components/ControlledUserSelector.vue',
  import.meta.url
), 'utf8')
const nextApproverSectionSource = readFileSync(new URL(
  '../../components/NextApproverSection.vue',
  import.meta.url
), 'utf8')
const nextApproverConfigEditorSource = readFileSync(new URL(
  '../../components/NextApproverConfigEditor.vue',
  import.meta.url
), 'utf8')
const personScopeRuleEditorSource = readFileSync(new URL(
  '../../components/PersonScopeRuleEditor.vue',
  import.meta.url
), 'utf8')
const nextApproverPreviewComposableSource = readFileSync(new URL(
  '../../composables/useNextApproverPreview.js',
  import.meta.url
), 'utf8')

const nextApproverSectionIndex = approvalDecisionPanelSource.indexOf(
  '<NextApproverSection'
)
const approvalOpinionIndex = approvalDecisionPanelSource.indexOf(
  'class="approval-opinion-section"'
)
assert.ok(
  nextApproverSectionIndex >= 0
    && approvalOpinionIndex >= 0
    && approvalOpinionIndex < nextApproverSectionIndex
    && nextApproverSectionIndex
      < approvalDecisionPanelSource.indexOf('</el-form>'),
  '下一节点审批人必须放在审批意见的同一个表单区域内'
)
assert.ok(
  approvalDecisionPanelSource.indexOf(
    '@update:model-value="emit(\'update:comment\', $event)"'
  ) < nextApproverSectionIndex,
  '下一节点审批人必须显示在审批意见字段下方'
)
assert.doesNotMatch(
  nextApproverSectionSource,
  /next-approver-section__title/,
  '下一节点审批人不应继续渲染独立区块标题'
)
assert.match(
  approvalDecisionPanelSource,
  /<NextApproverSection[\s\S]*?:comment="comment"/,
  '审批决策面板必须把审批备注传入下一审批人区域'
)
assert.match(
  nextApproverSectionSource,
  /<ControlledUserSelector[\s\S]*?:comment="comment"/,
  '下一审批人区域必须把审批备注继续传给受控人员选择器'
)
assert.match(
  controlledUserSelectorSource,
  /comment:\s*props\.comment\s*\?\?\s*''/,
  '候选人员 options 请求必须显式携带审批备注，空备注也不能沿用旧流程变量'
)
assert.match(
  nextApproverPreviewComposableSource,
  /comment:\s*payload\.comment/,
  '审批备注必须参与预览防抖签名，避免复用旧 scopeKey'
)
assert.match(
  nextApproverConfigEditorSource,
  /<PersonScopeRuleEditor[\s\S]*?v-model="localConfig\.source\.rules"/,
  '节点配置必须复用统一的人员范围规则编辑器'
)
assert.doesNotMatch(
  nextApproverConfigEditorSource,
  /v-for="\(rule, index\) in localConfig\.source\.rules"/,
  '下一审批人配置不应继续维护私有的重复范围规则表单'
)
assert.match(
  personScopeRuleEditorSource,
  /value-key="code"/,
  '指定用户范围必须持久化可跨环境迁移的 username/code'
)
assert.match(
  personScopeRuleEditorSource,
  /value="ALL_USERS"/,
  '全员范围只能通过编辑器显式添加'
)
assert.match(
  personScopeRuleEditorSource,
  /v-model="rule\.includeChildren"/,
  '组织范围编辑器必须保留包含下级配置'
)
assert.match(
  nextApproverSectionSource,
  /:ordered="node\.assignmentMode === 'MULTI_INSTANCE'"/,
  '只有多实例下一节点需要启用显式办理顺序'
)
assert.match(
  controlledUserSelectorSource,
  /多实例参与人顺序[\s\S]*?上移[\s\S]*?下移[\s\S]*?移除/,
  '有序人员选择器必须提供序号调整与移除操作'
)
assert.match(
  controlledUserSelectorSource,
  /optionsRequestGeneration[\s\S]*?activeOptionsRequestSignature/,
  '候选请求必须同时使用代次与上下文签名拒绝晚到响应'
)
assert.match(
  controlledUserSelectorSource,
  /props\.action[\s\S]*?props\.comment[\s\S]*?props\.formData[\s\S]*?void load\(\)/,
  '选择弹窗打开时，审批上下文变化必须重新加载候选人员'
)

assert.equal(
  buildNodeScopedMultiInstanceCollection('finance-review', ''),
  '${_wfMultiInstanceUsers_finance_review}',
  '新建多实例节点必须使用节点隔离的人员集合变量'
)
assert.equal(
  buildNodeScopedMultiInstanceCollection(
    'finance-review',
    '${_wfMultiInstanceUsers_}'
  ),
  '${_wfMultiInstanceUsers_finance_review}',
  '历史共享集合变量必须迁移为节点隔离变量'
)
assert.equal(
  buildNodeScopedMultiInstanceCollection(
    'finance-review',
    '${customApprovers}'
  ),
  '${customApprovers}',
  '用户显式配置的自定义集合变量不得被覆盖'
)
assert.notEqual(
  buildNodeScopedMultiInstanceCollection('finance-review'),
  buildNodeScopedMultiInstanceCollection('legal-review'),
  '不同多实例节点不得复用同一个自动生成集合变量'
)

assert.deepEqual(
  normalizeUserKeys([' alice ', '', 'bob', 'alice', null]),
  ['alice', 'bob'],
  '人员键应去空、去重并保持首次出现顺序'
)

const optionsRequestContext = {
  taskId: 'task-1',
  targetNodeId: 'review-1',
  scopeKey: 'scope-1',
  action: 'APPROVE',
  actionLabel: '同意',
  comment: 'looks good',
  formData: { amount: 10, applicant: { code: 'alice' } },
  keyword: 'bob',
  pageNum: 2,
  pageSize: 20
}
assert.equal(
  createNextApproverOptionsRequestSignature(optionsRequestContext),
  createNextApproverOptionsRequestSignature({
    pageSize: 20,
    pageNum: 2,
    keyword: 'bob',
    formData: { applicant: { code: 'alice' }, amount: 10 },
    comment: 'looks good',
    actionLabel: '同意',
    action: 'APPROVE',
    scopeKey: 'scope-1',
    targetNodeId: 'review-1',
    taskId: 'task-1'
  }),
  '候选请求签名必须不受对象属性排列影响'
)
for (const [field, value] of [
  ['scopeKey', 'scope-2'],
  ['action', 'REJECT'],
  ['comment', 'changed'],
  ['formData', { amount: 11 }],
  ['keyword', 'carol'],
  ['pageNum', 3],
  ['pageSize', 50]
]) {
  assert.notEqual(
    createNextApproverOptionsRequestSignature(optionsRequestContext),
    createNextApproverOptionsRequestSignature({
      ...optionsRequestContext,
      [field]: value
    }),
    `${field} 变化时旧候选请求签名必须立即失效`
  )
}

const originalOrder = ['first', 'second', 'third']
assert.deepEqual(
  reorderNextApproverValues(originalOrder, 2, 1),
  ['first', 'third', 'second'],
  '多实例人员应支持显式调整办理次序'
)
assert.deepEqual(
  originalOrder,
  ['first', 'second', 'third'],
  '顺序调整 helper 不得原地修改调用方草稿'
)

assert.deepEqual(
  normalizeNextApproverScope({
    type: 'DEPARTMENT',
    values: ['dept-a', 'dept-a'],
    includeChildren: true
  }),
  {
    type: 'ORGANIZATION',
    values: ['dept-a'],
    includeChildren: true
  },
  '历史部门范围应兼容为组织范围'
)

assert.notEqual(
  normalizeNextApproverScope({ type: 'UNTRUSTED_SCOPE' }).type,
  'ALL_USERS',
  '未知范围类型不得静默扩大为全员'
)

assert.deepEqual(
  createNextApproverSelectionConfig({
    visible: false,
    editable: true,
    source: {
      type: 'RESOLVER',
      resolverCode: 'managerResolver',
      extraParams: { level: 2 }
    }
  }),
  {
    version: 1,
    visible: false,
    editable: false,
    source: {
      type: 'RESOLVER',
      resolverCode: 'managerResolver',
      extraParams: { level: 2 }
    }
  },
  '隐藏配置必须同时关闭编辑能力，但保留受控解析器配置'
)

assert.deepEqual(
  createNextApproverSelectionConfig({
    visible: true,
    editable: true,
    source: {
      type: 'SCOPE',
      rules: [{
        type: 'ROLE',
        values: ['finance-manager']
      }]
    }
  }),
  {
    version: 1,
    visible: true,
    editable: true,
    source: {
      type: 'SCOPE',
      rules: [{
        type: 'ROLE',
        values: ['finance-manager'],
        includeChildren: false
      }]
    }
  },
  'BPMN 持久化契约必须使用 nextApproverSelection.source.type/rules'
)
assert.equal(
  createNextApproverSelectionConfig({}).version,
  1,
  '所有新建或兼容归一化配置必须显式输出 version: 1'
)

const persistedAssigneeConfig = buildAssigneeConfig({
  assigneeType: 'user',
  assignee: 'alice',
  nextApproverSelection: {
    visible: true,
    editable: true,
    source: {
      type: 'SCOPE',
      rules: [{ type: 'USER', values: ['bob'] }]
    }
  }
})
assert.deepEqual(
  persistedAssigneeConfig.nextApproverSelection,
  {
    version: 1,
    visible: true,
    editable: true,
    source: {
      type: 'SCOPE',
      rules: [{
        type: 'USER',
        values: ['bob'],
        includeChildren: false
      }]
    }
  },
  'assigneeConfig 必须原样持久化版本化的嵌套人员来源契约'
)
assert.equal(
  Object.hasOwn(
    persistedAssigneeConfig.nextApproverSelection,
    'multiple'
  ),
  false,
  'multiple 不属于 BPMN 持久化契约，应由运行时 assignmentMode 派生'
)
assert.equal(
  validateNextApproverSelectionConfig({
    visible: true,
    editable: true,
    source: { type: 'SCOPE', rules: [] }
  }).valid,
  false,
  '展示人员选择时不得把缺失范围隐式扩大为全员'
)
assert.equal(
  validateNextApproverSelectionConfig({
    visible: false,
    editable: false,
    source: { type: 'UNTRUSTED_SOURCE', rules: [] }
  }).valid,
  false,
  '隐藏配置中的未知数据源也不得静默进入 BPMN'
)

const multiNodePreview = normalizeNextApproverPreview({
  taskId: 'task-current',
  processDefinitionId: 'definition-7',
  status: 'READY',
  scopeKey: 'scope-route-7',
  nodes: [
    {
      nodeId: 'finance-review',
      nodeName: '财务审批',
      visible: true,
      editable: true,
      assignmentMode: 'DIRECT',
      sourceType: 'SCOPE',
      assignees: [
        { userId: '1', username: 'alice', displayName: 'Alice' }
      ]
    },
    {
      nodeId: 'security-review',
      nodeName: '安全审批',
      visible: true,
      editable: true,
      assignmentMode: 'CANDIDATE',
      sourceType: 'RESOLVER',
      assignees: [
        { username: 'bob', displayName: 'Bob' },
        { username: 'carol', displayName: 'Carol' }
      ]
    },
    {
      nodeId: 'hidden-review',
      nodeName: '隐藏审批',
      visible: false,
      editable: true,
      assignmentMode: 'DIRECT',
      assignees: [{ username: 'dave' }]
    }
  ]
})

assert.deepEqual(
  multiNodePreview.nextNodes.map(node => node.nodeId),
  ['finance-review', 'security-review', 'hidden-review'],
  '并行或包容路由命中的多个人工节点必须全部保留'
)
assert.equal(
  multiNodePreview.scopeKey,
  'scope-route-7',
  '整组路径与范围必须使用 preview 顶层 scopeKey'
)
assert.equal(
  multiNodePreview.nextNodes[2].editable,
  false,
  '隐藏节点不得保留可编辑状态'
)
assert.deepEqual(
  createNextApproverDraftMap(multiNodePreview),
  {
    'finance-review': ['alice'],
    'security-review': ['bob', 'carol'],
    'hidden-review': ['dave']
  },
  '每个下一节点应维护独立草稿，不能只取第一条分支'
)

const refreshedSameScopePreview = {
  status: 'READY',
  scopeKey: 'scope-route-7',
  nodes: multiNodePreview.nextNodes.map(node => ({
    ...node,
    assignees: node.nodeId === 'finance-review'
      ? [{ username: 'fresh-finance' }]
      : node.nodeId === 'security-review'
        ? [{ username: 'fresh-security' }]
        : [{ username: 'fresh-hidden' }]
  }))
}
assert.deepEqual(
  reconcileNextApproverDraftState(
    multiNodePreview,
    refreshedSameScopePreview,
    {
      'finance-review': ['manually-selected'],
      'security-review': ['stale-default'],
      'hidden-review': ['stale-hidden']
    },
    ['finance-review']
  ),
  {
    draftMap: {
      'finance-review': ['manually-selected'],
      'security-review': ['fresh-security'],
      'hidden-review': ['fresh-hidden']
    },
    touchedNodeIds: ['finance-review']
  },
  '同一 scopeKey 与节点集合刷新时，应保留已人工修改节点并更新未触碰默认值'
)
assert.deepEqual(
  reconcileNextApproverDraftState(
    multiNodePreview,
    { ...refreshedSameScopePreview, scopeKey: 'scope-route-new' },
    { 'finance-review': ['stale-manual'] },
    ['finance-review']
  ).draftMap['finance-review'],
  ['fresh-finance'],
  'scopeKey 变化时必须清理旧路由的人工选择'
)
assert.deepEqual(
  reconcileNextApproverDraftState(
    multiNodePreview,
    {
      ...refreshedSameScopePreview,
      nodes: refreshedSameScopePreview.nodes.filter(node =>
        node.nodeId !== 'hidden-review')
    },
    { 'finance-review': ['stale-manual'] },
    ['finance-review']
  ).draftMap['finance-review'],
  ['fresh-finance'],
  '即使 scopeKey 未变，节点集合变化也必须清理旧草稿'
)

assert.deepEqual(
  buildChangedNextApproverSelections(multiNodePreview, {
    'finance-review': ['eve'],
    'security-review': ['carol', 'bob'],
    'hidden-review': ['mallory']
  }),
  [{
    nodeId: 'finance-review',
    userKeys: ['eve']
  }],
  'selection 仅包含 nodeId/userKeys；候选人员顺序变化不应伪造修改'
)

assert.deepEqual(
  buildChangedNextApproverSelections({
    status: 'READY',
    scopeKey: 'scope-multi-instance',
    nodes: [{
      nodeId: 'serial-review',
      nodeName: '串行审批',
      visible: true,
      editable: true,
      assignmentMode: 'MULTI_INSTANCE',
      sourceType: 'SCOPE',
      assignees: [
        { username: 'first' },
        { username: 'second' }
      ]
    }]
  }, {
    'serial-review': ['second', 'first']
  }),
  [{
    nodeId: 'serial-review',
    userKeys: ['second', 'first']
  }],
  '多实例人员顺序变化会改变执行语义，必须作为真实修改提交'
)

assert.deepEqual(
  buildChangedNextApproverSelections({
    status: 'READY',
    scopeKey: 'scope-new-route',
    nodes: [{
      nodeId: 'new-route-review',
      nodeName: '新路由审批',
      visible: true,
      editable: true,
      assignmentMode: 'DIRECT',
      sourceType: 'SCOPE',
      assignees: [{ username: 'new-owner' }]
    }]
  }, {
    'finance-review': ['stale-selection'],
    'new-route-review': ['new-owner']
  }),
  [],
  '路由切换后旧节点草稿不得进入提交载荷'
)

assert.deepEqual(
  validateNextApproverDraft(multiNodePreview, {
    'finance-review': ['alice', 'eve'],
    'security-review': ['bob'],
    'hidden-review': []
  }),
  {
    valid: false,
    message: '节点“财务审批”只能选择一名审批人'
  },
  '单选节点不得提交多个人员'
)

assert.deepEqual(
  validateNextApproverDraft({
    status: 'READY',
    nodes: [{
      nodeId: 'review-without-scope',
      nodeName: '缺少范围',
      visible: true,
      editable: true,
      assignmentMode: 'DIRECT',
      sourceType: 'SCOPE',
      assignees: [{ username: 'alice' }]
    }]
  }),
  {
    valid: false,
    message: '节点“缺少范围”缺少有效的人员选择范围'
  },
  '可编辑节点缺失 scopeKey 时必须阻止提交'
)

assert.equal(
  validateNextApproverDraft({
    status: 'BLOCKED',
    message: '条件表达式无法安全预览'
  }).valid,
  false,
  '后端明确阻断时前端不得继续提交'
)
assert.equal(
  validateNextApproverDraft({ status: 'DEFERRED' }).valid,
  true,
  '无需提前选择的延迟解析状态不应阻断原审批流程'
)
assert.equal(
  normalizeNextApproverPreview({ status: 'UNRECOGNIZED' }).status,
  'BLOCKED',
  '未知非空预览状态必须按协议错误阻断，不能降级 READY'
)
assert.equal(
  normalizeNextApproverPreview({
    status: 'READY',
    scopeKey: 'scope-invalid-mode',
    nodes: [{
      nodeId: 'invalid-mode',
      visible: true,
      editable: true,
      assignmentMode: 'UNRECOGNIZED',
      sourceType: 'SCOPE'
    }]
  }).status,
  'BLOCKED',
  '未知非空 assignmentMode 必须阻断，不能静默按 DIRECT 处理'
)
assert.equal(
  normalizeNextApproverPreview({
    status: 'READY',
    scopeKey: 'scope-invalid-source',
    nodes: [{
      nodeId: 'invalid-source',
      visible: true,
      editable: true,
      assignmentMode: 'DIRECT',
      sourceType: 'UNTRUSTED_SOURCE'
    }]
  }).status,
  'BLOCKED',
  '可编辑节点的未知 sourceType 必须阻断，不能展示不受控选择器'
)
assert.equal(
  hasNextApproverPresentation({
    status: 'READY',
    nodes: [{ nodeId: 'hidden', visible: false }]
  }),
  false,
  '仅存在隐藏节点时不应渲染下一审批人区域'
)
assert.equal(
  hasNextApproverPresentation({ status: 'READY', nodes: [] }, true),
  false,
  '旧流程或未配置流程首次预览时不应短暂显示加载区域'
)
assert.equal(
  hasNextApproverPresentation({
    status: 'READY',
    nodes: [{ nodeId: 'visible', visible: true }]
  }, true),
  true,
  '已展示下一审批人时，刷新期间应保留加载状态'
)

console.log('next-approver contract tests passed')
