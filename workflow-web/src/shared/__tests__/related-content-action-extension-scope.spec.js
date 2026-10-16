import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import * as vue from 'vue'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import * as appearance from '../../../../packages/workflow-core/src/shared/builtin-button-appearance.js'
import * as relatedRuntime from '../../../../packages/workflow-core/src/shared/related-content-runtime.js'
import { buildRuntimeContext } from '../../../../packages/workflow-core/src/shared/form-runtime/context.js'
import { filterRuntimeFormSubmissionData } from '../../../../packages/workflow-core/src/shared/form-runtime/index.js'

/** 执行实际 setup/render，只隔离网络/注册表/生命周期，验证 PAGE 回退、保存和独立扩展 bridge。 */
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
  const calls = [], messages = [], registryCalls = [], saves = [], events = [], refreshes = []
  const component = vue.markRaw({ name: 'ScopeTestComponent' })
  const result = { actionCapabilities: { AUDIT: { available: false }, EDIT: { available: true } }, receipt: 'saved' }
  const script = descriptor.scriptSetup.content.replace(/^import[\s\S]*?from ['"][^'"]+['"]\s*;?\n/gm, '')
  const values = {
    ...vue, ...appearance, ...relatedRuntime, buildRuntimeContext, filterRuntimeFormSubmissionData,
    defineProps: () => props, defineEmits: () => (...event) => events.push(event), defineOptions() {}, defineExpose() {},
    watch() {}, inject: () => null, onBeforeUnmount() {}, useUnsavedChangesGuard() {},
    getEffectiveEntityStatusOptions: () => [], buildEntityStatusMap: () => ({}), relatedFormDraftGuardKey: Symbol('draft'),
    hasCustomFormComponent: (...args) => { registryCalls.push(args); return true },
    hasCustomListComponent: (...args) => { registryCalls.push(args); return true },
    getCustomFormComponent: () => component, getCustomListComponent: () => component,
    relatedContentListProps: () => ({}), relatedContentIsVisible: () => true,
    ElAlert: { name: 'ElAlert' }, ElEmpty: { name: 'ElEmpty' },
    entityDataApi: {
      update: async (...args) => { saves.push(['update', ...args]); return { id: 'child-1' } },
      save: async (...args) => { saves.push(['save', ...args]); return { id: 'created-child' } }
    },
    uiCompositionRuntimeApi: { executeAction: async payload => { calls.push(payload); return result } },
    ElMessage: { error: message => messages.push(message), success() {}, warning() {} }
  }
  const state = new Function(...Object.keys(values), `${script}\nreturn {
    resolved, targetForm, targetRecord, targetEntity, targetRendererRef, targetDataSourceRuntime, targetSaveLoading,
    canSaveTarget, saveTarget, actionCapabilities, actionLoading, allowsActionExtensions, actionServices, actions,
    executeBoundAction, failurePolicy, errorMessage, shouldRender, RuntimeBody, AsyncFormPreviewLinkage, AsyncEntityDataList,
    setRefresh: handler => { resolveComposition = handler }
  }`)(...Object.values(values))
  state.setRefresh(async options => { refreshes.push(options); return true })
  state.resolved.value = {
    targetContentType, targetRecordId: 'child-1', targetEntityCode: 'child_entity', targetContentKey: 'child-list', compositionKey: 'child-page',
    compositionContext: 'server-issued-published-composition-context'
  }
  state.targetForm.value = { id: 'child-form', configHash: 'target-published-hash', fields: [],
    customComponent: 'TargetPageForm', viewConfig: { customComponentProps: { title: '目标页面参数' } } }
  state.targetRecord.value = { id: 'child-1', name: '子记录' }
  state.actionCapabilities.value = {
    VIEW: { available: true }, EDIT: { available: true }, AUDIT: { available: true }, UNDECLARED: { available: true }
  }
  return { props, state, calls, messages, registryCalls, saves, events, refreshes, result, render: () => state.RuntimeBody.setup()() }
}

for (const targetContentType of ['FORM', 'LIST']) {
  test(`普通 ENTITY_RELATION ${targetContentType} 忽略旧动作接口，即使能力可用也不提供或执行 dispatch`, async () => {
    const { state, calls, messages, registryCalls, render } = await setupRuntime('ENTITY_RELATION', targetContentType)
    assert.equal(state.allowsActionExtensions.value, false)
    assert.deepEqual(state.actionServices.value, [])
    assert.equal(state.actions.value.has('AUDIT'), false, '旧发布 actionServices 不再把扩展动作加入能力集合')
    assert.equal(state.actions.value.has('EDIT'), true, '原有内置编辑能力应继续存在')
    const rendered = render()
    assert.equal(rendered.type, targetContentType === 'FORM' ? state.AsyncFormPreviewLinkage : state.AsyncEntityDataList)
    assert.equal(Object.hasOwn(rendered.props, 'runtime'), false)
    assert.deepEqual(registryCalls, [], '旧覆盖不应触发 RELATED_CONTENT 注册或版本摘要校验')
    if (targetContentType === 'FORM') {
      assert.equal(rendered.props.form, state.targetForm.value)
      assert.equal(rendered.props.form.customComponent, 'TargetPageForm')
      assert.deepEqual(rendered.props.form.viewConfig.customComponentProps, { title: '目标页面参数' })
      assert.equal(rendered.props.readonly, false)
      assert.equal(rendered.props.context.compositionContext, 'server-issued-published-composition-context')
    } else {
      assert.equal(rendered.props.listKey, 'child-list')
      assert.equal(rendered.props.scene, 'EMBEDDED')
      assert.equal(rendered.props.showTargetListToolbarActions, true)
      assert.equal(rendered.props.compositionContext, 'server-issued-published-composition-context')
      assert.deepEqual(rendered.props.relatedContentActions, ['VIEW', 'EDIT'])
    }
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
  assert.equal(render().type, state.AsyncFormPreviewLinkage)
  assert.equal(Object.hasOwn(render().props, 'runtime'), false)
  assert.equal(await previousDispatch('EDIT'), null)
  assert.equal(calls.length, 0)
  assert.equal(state.actionCapabilities.value.EDIT.available, true, '测试保留旧能力，确认由关系类型守卫拒绝')
})

for (const relationType of ['ENTITY_RELATION', 'REFERENCE_FIELD', 'REVERSE_REFERENCE', 'FIELD_MATCH']) {
  test(`旧 ${relationType} 覆盖即使没有版本摘要也回退 PAGE，隐藏失败策略失效`, async () => {
    const { props, state, registryCalls, render } = await setupRuntime(relationType)
    props.composition.config.specialHandling = { mode: 'CUSTOM_COMPONENT', customComponent: { name: 'removed-component' }, failurePolicy: 'HIDE' }
    assert.equal(render().type, state.AsyncFormPreviewLinkage)
    assert.deepEqual(registryCalls, [])
    assert.equal(state.failurePolicy.value, 'ERROR')
    state.errorMessage.value = '目标页面加载失败'
    assert.equal(state.shouldRender.value, true)
    assert.equal(render().props.type, 'error')
    assert.equal(render().props.description, '目标页面加载失败')
  })
}

test('独立扩展仍遵守组件发布版本、摘要及失败策略', async () => {
  const { props, state, registryCalls, render } = await setupRuntime('SAME_RECORD')
  assert.equal(render().type.name, 'ScopeTestComponent')
  assert.deepEqual(registryCalls, [['scope-test-component', 3, 'published-component-digest', 'RELATED_CONTENT']])
  props.composition.config.specialHandling.failurePolicy = 'HIDE'
  state.errorMessage.value = '扩展加载失败'
  assert.equal(state.shouldRender.value, false)
  assert.equal(render(), null)
  props.composition.config.specialHandling.failurePolicy = 'PLACEHOLDER'
  assert.equal(state.shouldRender.value, true)
  assert.equal(render().type.name, 'ElEmpty')
})

for (const creating of [false, true]) {
  test(`PAGE 表单回退保留校验、字段过滤、${creating ? '新增' : '更新'}载荷和刷新权限`, async () => {
    const { state, saves, events, refreshes, render } = await setupRuntime('ENTITY_RELATION')
    if (creating) {
      state.resolved.value.targetRecordId = ''
      state.actionCapabilities.value.CREATE = { available: true }
    }
    state.targetEntity.value = { fields: [{ fieldCode: 'unboundChildren', fieldType: 'SUB_FORM' }] }
    const draft = { name: '编辑目标页面', unboundChildren: [{ id: 1 }] }
    render().props['onUpdate:modelValue'](draft)
    let valid = false, validationCalls = 0, prevalidationCalls = 0
    state.targetRendererRef.value = {
      validate: async () => { validationCalls++; return valid },
      getData: () => state.targetRecord.value
    }
    state.targetDataSourceRuntime.value = { prevalidateBeforeSubmit: async ({ form }) => {
      assert.equal(form, state.targetForm.value); prevalidationCalls++
    } }
    await state.saveTarget()
    assert.equal(saves.length, 0)
    assert.equal(prevalidationCalls, 0)
    assert.deepEqual(state.targetRecord.value, draft, '校验失败保留本地 PAGE 草稿')
    assert.equal(state.targetSaveLoading.value, false)
    valid = true
    await state.saveTarget()
    assert.equal(validationCalls, 2)
    assert.equal(prevalidationCalls, 1)
    const payload = {
      data: { name: '编辑目标页面' }, formId: 'child-form', formConfigHash: 'target-published-hash',
      compositionContext: 'server-issued-published-composition-context', startProcess: false
    }
    assert.deepEqual(saves, creating
      ? [['save', { ...payload, entityCode: 'child_entity', name: '编辑目标页面' }]]
      : [['update', 'child_entity', 'child-1', payload]])
    assert.equal(events[0][0], 'target-saved')
    assert.deepEqual(refreshes, [{ force: true }])
    state.actionCapabilities.value[creating ? 'CREATE' : 'EDIT'].available = false
    assert.equal(render().props.readonly, true)
    render().props['onUpdate:modelValue']({ name: '禁止更新' })
    await state.saveTarget()
    assert.deepEqual(state.targetRecord.value, draft)
    assert.equal(saves.length, 1, '能力撤销后不能继续编辑或提交')
  })
}
