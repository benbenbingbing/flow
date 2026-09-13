import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import { normalizeRuntimeFormRelease } from '../list-button-form-runtime.js'
import {
  acquireFormActionExecution,
  buildCustomFormActionExecutionPayload,
  createFormActionRequestId,
  resolveSafeRuntimeActionFallback
} from '../form-action-runtime.js'

function releaseWithSnapshot(snapshotDocument) {
  return {
    id: 'release-form-v5',
    version: 5,
    effectiveReleaseId: 'release-form-v5-hotfix',
    hotfixApplied: true,
    snapshotDocument
  }
}

{
  const viewCompositions = [
    {
      id: 'composition-1',
      compositionKey: 'related_project',
      anchorType: 'OWNER',
      config: { name: '查看所属项目' }
    }
  ]
  const runtimeForm = normalizeRuntimeFormRelease(
    releaseWithSnapshot({
      form: { id: 'form-1', formName: '需求表单' },
      legacyFields: [{ fieldCode: 'name' }],
      nodes: [{ id: 'node-1' }],
      viewCompositions
    }),
    'fallback-form-id',
    'resolution-token'
  )

  assert.deepEqual(runtimeForm.viewCompositions, viewCompositions)
  assert.equal(runtimeForm.runtimeReleaseId, 'release-form-v5')
  assert.equal(runtimeForm.runtimeReleaseVersion, 5)
  assert.equal(runtimeForm.releaseResolutionToken, 'resolution-token')
}

{
  const runtimeForm = normalizeRuntimeFormRelease(
    releaseWithSnapshot(JSON.stringify({
      form: { formName: '兼容旧发布表单' },
      legacyFields: [],
      nodes: []
    })),
    'fallback-form-id'
  )

  assert.equal(runtimeForm.id, 'fallback-form-id')
  assert.deepEqual(runtimeForm.viewCompositions, [])
}

assert.throws(
  () => normalizeRuntimeFormRelease(releaseWithSnapshot({ nodes: [] }), 'form-1'),
  /目标表单发布快照缺少表单定义/
)

const firstRequestId = createFormActionRequestId()
const secondRequestId = createFormActionRequestId()
assert.match(firstRequestId, /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/)
assert.notEqual(firstRequestId, secondRequestId)

const customActionPayload = buildCustomFormActionExecutionPayload(
  { key: 'generate_report', ownerFormId: 'form-1' },
  [{
    id: 'form-1',
    runtimeReleaseId: 'release-1',
    runtimeReleaseVersion: 3
  }],
  {
    entityCode: 'report',
    mode: 'edit',
    formData: { name: '月报' },
    recordId: 'record-1'
  },
  firstRequestId
)
assert.equal(customActionPayload.requestId, firstRequestId)
assert.equal(customActionPayload.targetKey, 'generate_report')
assert.equal('requestId' in customActionPayload.input, false)
assert.equal('button' in customActionPayload.input, false)
assert.equal('recordId' in customActionPayload.input, false)
assert.equal('requestId' in customActionPayload.context, false)

const approvalActionPayload = buildCustomFormActionExecutionPayload(
  { key: 'recalculate_approval', ownerFormId: 'form-1' },
  [{
    id: 'form-1',
    runtimeReleaseId: 'release-1',
    runtimeReleaseVersion: 3
  }],
  {
    entityCode: 'request',
    mode: 'approve',
    recordId: 'record-1',
    taskId: 'task-1',
    processInstanceId: 'process-1',
    task: {
      taskId: 'task-1',
      processInstanceId: 'process-1',
      entityCode: 'request',
      name: '经理审批',
      startUserName: '张三',
      processName: '请假流程',
      processStatus: 'RUNNING'
    }
  },
  secondRequestId
)
assert.equal('task' in approvalActionPayload.input, false)
assert.equal(approvalActionPayload.taskId, 'task-1')
assert.deepEqual(approvalActionPayload.context, {})
assert.equal('processInstanceId' in approvalActionPayload, false)

const safeFallbackActions = resolveSafeRuntimeActionFallback({
  id: 'form-safe-fallback',
  viewConfig: {
    actionBar: {
      customButtons: [{
        key: 'admin_only',
        label: '仅管理员可见',
        enabled: true,
        modes: ['view'],
        perm: 'entity:demo:admin'
      }]
    }
  }
}, { mode: 'view' })
assert.deepEqual(
  safeFallbackActions.map(action => action.key),
  ['close'],
  '权限解析失败时不得通过本地配置暴露自定义按钮'
)

const conditionalFallbackActions = resolveSafeRuntimeActionFallback({
  id: 'form-conditional-fallback',
  viewConfig: {
    actionBar: {
      builtInOverrides: {
        close: {
          availabilityRule: {
            version: 2,
            visibleWhen: { type: 'RELATION', relation: 'CREATOR' },
            enabledWhen: null,
            disabledMessage: ''
          }
        },
        save: {
          availabilityRule: {
            version: 2,
            visibleWhen: null,
            enabledWhen: { type: 'PROCESS_STATE', operator: 'EQ', value: 'DRAFT' },
            disabledMessage: '仅草稿可保存'
          }
        }
      }
    }
  }
}, { mode: 'edit' })
const hiddenCloseFallback = conditionalFallbackActions.find(action => action.key === 'close')
const disabledSaveFallback = conditionalFallbackActions.find(action => action.key === 'save')
assert.equal(
  hiddenCloseFallback?.visible,
  false,
  '无法解析显示条件时必须隐藏按钮'
)
assert.equal(
  disabledSaveFallback?.enabled,
  false,
  '无法解析启用条件时必须禁用按钮'
)
assert.equal(
  disabledSaveFallback?.reason,
  '仅草稿可保存'
)

const loadingState = { value: '' }
const releaseFirstAction = acquireFormActionExecution(
  { key: 'generate_report', enabled: true },
  loadingState
)
assert.equal(typeof releaseFirstAction, 'function')
assert.equal(
  acquireFormActionExecution(
    { key: 'generate_report', enabled: true },
    loadingState
  ),
  null,
  '第一次确认尚未完成时，第二次触发必须被动作锁拒绝'
)
releaseFirstAction()
assert.equal(loadingState.value, '')
assert.equal(
  typeof acquireFormActionExecution(
    { key: 'generate_report', enabled: true },
    loadingState
  ),
  'function',
  '确认取消或执行完成后必须可以再次触发'
)

const formActionRuntimeSource = readFileSync(
  new URL('../form-action-runtime.js', import.meta.url),
  'utf8'
)
assert.match(
  formActionRuntimeSource,
  /const requestId = createFormActionRequestId\(\)[\s\S]*?buildCustomFormActionExecutionPayload\([\s\S]*?requestId[\s\S]*?\)/,
  '一次用户点击只生成一次 requestId，并传入同一请求 payload'
)

;[
  '../../views/entity/components/EntityDataFormDialog.vue',
  '../../views/entity/components/approval/EntityApprovalDialog.vue'
].forEach(relativePath => {
  const source = readFileSync(new URL(relativePath, import.meta.url), 'utf8')
  assert.equal(
    source.match(/executeCustomFormAction\(/g)?.length,
    1,
    `${relativePath} 必须通过统一表单动作运行时发起一次请求`
  )
  assert.match(
    source,
    /async function handleFormAction\(action[^)]*\)[\s\S]*?acquireFormActionExecution\(action, actionPendingKey\)[\s\S]*?await confirmAction\(action\)/,
    `${relativePath} 必须在等待确认前占用独立动作锁`
  )
})

console.log('list-button-form-runtime.spec.js passed')
