import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import * as vue from 'vue'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import * as appearance from '../../../../packages/workflow-core/src/shared/builtin-button-appearance.js'
import * as relatedRuntime from '../../../../packages/workflow-core/src/shared/related-content-runtime.js'
import { buildRuntimeContext } from '../../../../packages/workflow-core/src/shared/form-runtime/context.js'

/** 执行实际 setup 和自定义组件 render，只隔离网络/注册表/生命周期，验证真实 bridge 的范围与请求载荷。 */
async function setupRuntime(relationType, targetContentType = 'FORM') {
  const file = new URL('../../components/related-content/RelatedContentRuntime.vue', import.meta.url)
  const { descriptor, errors } = parse(await readFile(file, 'utf8'))
  assert.deepEqual(errors, [])
  const compiled = compileScript(descriptor, { id: 'related-action-scope' })
  assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: file.pathname, id: 'related-action-scope', compilerOptions: { bindingMetadata: compiled.bindings } }).errors, [])
  const props = vue.reactive({
    composition: { compositionKey: 'child-page', config: {
      relation: { type: relationType }, target: { contentType: targetContentType }, presentation: {},
      specialHandling: {
        mode: 'BOTH', customComponent: { name: 'scope-test-component', version: 3, artifactDigest: 'published-component-digest', props: {} },
        actionServices: [
          { actionKey: 'AUDIT', extensionId: 'published-audit-service' },
          { actionKey: 'EDIT', extensionId: 'legacy-edit-service' }
        ]
      }
    } },
    ownerType: 'FORM', ownerId: 'source-form', sourceRecordId: 'source-record', sourceData: {},
    sourceParameters: {}, runtimeContext: {}, configHash: 'source-published-hash', mode: 'edit'
  })
  const calls = [], messages = []
  const component = vue.markRaw({ name: 'ScopeTestComponent' })
  const result = { actionCapabilities: { AUDIT: { available: false }, EDIT: { available: true } }, receipt: 'saved' }
  const script = descriptor.scriptSetup.content.replace(/^import[\s\S]*?from ['"][^'"]+['"]\s*;?\n/gm, '')
  const values = {
    ...vue, ...appearance, ...relatedRuntime, buildRuntimeContext,
    defineProps: () => props, defineEmits: () => () => {}, defineOptions() {}, defineExpose() {},
    watch() {}, inject: () => null, onBeforeUnmount() {}, useUnsavedChangesGuard() {},
    getEffectiveEntityStatusOptions: () => [], buildEntityStatusMap: () => ({}), relatedFormDraftGuardKey: Symbol('draft'),
    hasCustomFormComponent: () => true, hasCustomListComponent: () => true,
    getCustomFormComponent: () => component, getCustomListComponent: () => component,
    uiCompositionRuntimeApi: { executeAction: async payload => { calls.push(payload); return result } },
    ElMessage: { error: message => messages.push(message), success() {}, warning() {} }
  }
  const state = new Function(...Object.keys(values), `${script}\nreturn { resolved, targetForm, targetRecord, actionCapabilities, actionLoading, allowsActionExtensions, actionServices, actions, executeBoundAction, RuntimeBody }`)(...Object.values(values))
  state.resolved.value = {
    targetContentType, targetRecordId: 'child-1', targetEntityCode: 'child_entity', compositionKey: 'child-page',
    compositionContext: 'server-issued-published-composition-context'
  }
  state.targetForm.value = { id: 'child-form', configHash: 'target-published-hash', fields: [] }
  state.targetRecord.value = { id: 'child-1', name: '子记录' }
  state.actionCapabilities.value = {
    VIEW: { available: true }, EDIT: { available: true }, AUDIT: { available: true }, UNDECLARED: { available: true }
  }
  return { props, state, calls, messages, result, render: () => state.RuntimeBody.setup()() }
}

for (const targetContentType of ['FORM', 'LIST']) {
  test(`普通 ENTITY_RELATION ${targetContentType} 忽略旧动作接口，即使能力可用也不提供或执行 dispatch`, async () => {
    const { state, calls, messages, render } = await setupRuntime('ENTITY_RELATION', targetContentType)
    assert.equal(state.allowsActionExtensions.value, false)
    assert.deepEqual(state.actionServices.value, [])
    assert.equal(state.actions.value.has('AUDIT'), false, '旧发布 actionServices 不再把扩展动作加入能力集合')
    assert.equal(state.actions.value.has('EDIT'), true, '原有内置编辑能力应继续存在')
    assert.equal(Object.hasOwn(render().props.runtime, 'dispatch'), false)
    for (const action of ['AUDIT', 'EDIT']) {
      assert.equal(await state.executeBoundAction(action, { targetRecordIds: ['child-1'] }), null)
    }
    assert.equal(calls.length, 0, '包括标准 EDIT 在内的直接 bridge 调用也必须被入口守卫拦截')
    assert.deepEqual(messages, ['当前发布版本未开放该操作', '当前发布版本未开放该操作'])
    assert.equal(state.actionLoading.value, false)
  })
}

for (const relationType of ['SAME_RECORD', 'INTERFACE_SERVICE']) {
  test(`独立扩展页 ${relationType} 保留 dispatch，并只转发解析得到的发布上下文和受限载荷`, async () => {
    const { state, calls, result, render } = await setupRuntime(relationType)
    const runtime = render().props.runtime
    assert.equal(state.allowsActionExtensions.value, true)
    assert.equal(state.actionServices.value.length, 2)
    assert.equal(runtime.dispatch, state.executeBoundAction)
    assert.equal(runtime.capabilities.includes('AUDIT'), true)
    assert.equal(runtime.capabilities.includes('UNDECLARED'), false)
    assert.equal(await runtime.dispatch('UNDECLARED'), null)
    assert.equal(calls.length, 0)
    const response = await runtime.dispatch(' audit ', {
      targetRecordIds: [' child-1 ', 'child-1', '', 'child-2'],
      compositionContext: 'forged-context', extensionId: 'client-selected-service', input: { overwrite: true }
    })
    assert.equal(response, result)
    assert.equal(calls.length, 1)
    const { operationId, ...request } = calls[0]
    assert.deepEqual(request, {
      compositionContext: 'server-issued-published-composition-context', action: 'AUDIT', targetRecordIds: ['child-1', 'child-2'], input: {}
    })
    assert.match(operationId, /^related-content_audit_/)
    assert.equal(state.actionLoading.value, false)
    assert.equal(state.actions.value.has('AUDIT'), false, '服务端执行后的能力结果继续约束下一次调用')
    assert.equal(await runtime.dispatch('AUDIT'), null)
    assert.equal(calls.length, 1)
  })
}

test('引用实例切回普通关联后，组件保留的旧 dispatch 闭包也不能继续调用接口', async () => {
  const { props, state, calls, render } = await setupRuntime('SAME_RECORD')
  const previousDispatch = render().props.runtime.dispatch
  assert.equal(typeof previousDispatch, 'function')
  props.composition.config.relation.type = 'ENTITY_RELATION'
  assert.equal(Object.hasOwn(render().props.runtime, 'dispatch'), false)
  assert.equal(await previousDispatch('EDIT'), null)
  assert.equal(calls.length, 0)
  assert.equal(state.actionCapabilities.value.EDIT.available, true, '测试保留旧能力，确认由关系类型守卫拒绝')
})
