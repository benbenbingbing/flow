import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import * as vue from 'vue'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import * as appearance from '../../../../packages/workflow-core/src/shared/builtin-button-appearance.js'
import { buildRuntimeContext } from '../../../../packages/workflow-core/src/shared/form-runtime/context.js'
import * as relatedRuntime from '../../../../packages/workflow-core/src/shared/related-content-runtime.js'

/** 执行真实 setup 和 Vue 响应式计算，仅隔离生命周期、网络与子组件，验证按钮仍绑定原业务方法。 */
async function setupFile(file, bindings, exposed) {
  const { descriptor, errors } = parse(await readFile(new URL(file, import.meta.url), 'utf8'))
  assert.deepEqual(errors, [])
  const compiled = compileScript(descriptor, { id: 'related-buttons' })
  assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: file, id: 'related-buttons', compilerOptions: { bindingMetadata: compiled.bindings } }).errors, [])
  const script = descriptor.scriptSetup.content.replace(/^import[\s\S]*?from ['"][^'"]+['"]\s*;?\n/gm, '')
  const values = { ...vue, ...appearance, ...relatedRuntime, buildRuntimeContext, ...bindings }
  return new Function(...Object.keys(values), `${script}\nreturn { ${exposed} }`)(...Object.values(values))
}

async function setupRuntime(presentation = {}) {
  const props = vue.reactive({
    composition: { compositionKey: 'acceptance', config: { presentation } },
    ownerType: 'FORM', ownerId: 'parent-form', sourceRecordId: 'parent-1', sourceData: {},
    sourceParameters: {}, runtimeContext: {}, mode: 'edit'
  })
  const state = await setupFile('../../components/related-content/RelatedContentRuntime.vue', {
    defineProps: () => props, defineEmits: () => () => {}, defineOptions() {}, defineExpose() {},
    watch() {}, inject: () => null, onBeforeUnmount() {}, useUnsavedChangesGuard() {},
    getEffectiveEntityStatusOptions: () => [], relatedFormDraftGuardKey: Symbol('draft'),
    BuiltInActionButton: { name: 'BuiltInActionButton' },
    ElMessage: { error(message) { throw new Error(message) }, success() {}, warning() {} }
  }, 'resolved, targetForm, targetRecord, targetRendererRef, actionCapabilities, targetSaveLoading, loading, actionLoading, canSaveTarget, toolbarActions, showTopActions, showBottomActions, RuntimeActions, saveTarget, refresh')
  state.resolved.value = { targetContentType: 'FORM', targetRecordId: 'child-1', compositionContext: 'trusted-context' }
  state.targetForm.value = { id: 'child-form' }
  state.actionCapabilities.value = { EDIT: { available: true } }
  return { props, state, render: (inline = true, bottom = false) => state.RuntimeActions.setup({ inline, bottom })() }
}

test('历史配置保持顶部保存入口，权限和只读仍决定是否提供保存动作', async () => {
  const { state, render } = await setupRuntime({ showTitle: false })
  assert.equal(state.showTopActions.value, true)
  assert.equal(state.showBottomActions.value, false)
  const bar = render()
  assert.equal(bar.props.style.justifyContent, 'flex-start')
  assert.equal(bar.children.length, 1)
  assert.equal(bar.children[0].props.defaults.label, '保存子表单')
  assert.equal(bar.children[0].props.defaults.buttonAppearance, 'LINK')
  assert.equal(bar.children[0].props.onClick, state.saveTarget)
  state.actionCapabilities.value.EDIT.available = false
  assert.equal(state.toolbarActions.value.length, 0)
  assert.equal(state.showTopActions.value, false)
  state.actionCapabilities.value.EDIT.available = true
  state.targetForm.value.isReadonly = 1
  assert.equal(state.toolbarActions.value.length, 0)
})

test('自定义外观及上下双栏共享原保存动作和加载状态，无法通过配置替换、隐藏或新增动作', async () => {
  const { state, render } = await setupRuntime({ actionBar: {
    position: 'BOTH', align: 'CENTER', gap: 20,
    buttons: { save: { label: '保存验收信息', buttonType: 'success', buttonAppearance: 'PLAIN', visible: false, onClick: 'other' }, other: { label: '额外功能' } }
  } })
  assert.equal(state.showTopActions.value, true)
  assert.equal(state.showBottomActions.value, true)
  state.targetSaveLoading.value = true
  state.actionLoading.value = true
  for (const bar of [render(), render(true, true)]) {
    assert.equal(bar.children.length, 1)
    assert.equal(bar.props.style.justifyContent, 'center')
    assert.equal(bar.props.style.gap, '20px')
    const { config, defaults, ...button } = bar.children[0].props
    assert.equal(button.onClick, state.saveTarget)
    assert.equal(button.loading, true)
    assert.equal(button.disabled, true)
    const resolved = appearance.resolveBuiltInButton(config, 'save', defaults)
    assert.equal(resolved.label, '保存验收信息')
    assert.equal(resolved.buttonType, 'success')
    assert.equal(resolved.visible, undefined)
    assert.equal(resolved.onClick, undefined)
  }
})

test('关联列表内嵌时提供刷新入口，目标列表继续使用自己的新增编辑按钮', async () => {
  const { props, state, render } = await setupRuntime({ actionBar: { buttons: { refresh: { label: '重新加载', sort: 100 } } } })
  state.resolved.value.targetContentType = 'LIST'
  state.actionCapabilities.value = { VIEW: { available: true }, CREATE: { available: true }, EDIT: { available: true } }
  assert.deepEqual(state.toolbarActions.value.map(action => action.key), ['refresh'])
  assert.deepEqual(render().children.map(button => button.props.onClick), [state.refresh])
  for (const position of ['DIALOG', 'DRAWER', 'PAGE']) {
    props.composition.config.presentation.position = position
    assert.deepEqual(state.toolbarActions.value.map(action => action.key), [])
  }
})

test('内嵌、Tab、弹窗和抽屉都能定位底部操作栏，入口是否存在仍受原有能力限制', async () => {
  const { props, state } = await setupRuntime({ actionBar: { position: 'BOTTOM', align: 'RIGHT' } })
  for (const position of ['INLINE', 'TAB', 'ROW_EXPAND', 'DIALOG', 'DRAWER', 'PAGE']) {
    props.composition.config.presentation.position = position
    assert.equal(state.showTopActions.value, false)
    assert.equal(state.showBottomActions.value, true)
  }
  state.actionCapabilities.value = {}
  assert.equal(state.showBottomActions.value, false)
})

test('上下按钮并发点击仍只校验一次，校验失败保留草稿并释放加载状态', async () => {
  const { state, render } = await setupRuntime({ actionBar: { position: 'BOTH' } })
  state.targetRecord.value = { name: '未保存草稿' }
  let finishValidation
  let calls = 0
  state.targetRendererRef.value = { validate: () => { calls += 1; return new Promise(resolve => { finishValidation = resolve }) } }
  const first = render().children[0].props.onClick()
  assert.equal(state.targetSaveLoading.value, true)
  await render(true, true).children[0].props.onClick()
  assert.equal(calls, 1)
  finishValidation(false)
  await first
  assert.deepEqual(state.targetRecord.value, { name: '未保存草稿' })
  assert.equal(state.targetSaveLoading.value, false)
})

test('设计器按目标类型和展示方式提供固定外观入口，切换展示方式不删除已配置外观', async () => {
  const editor = vue.reactive({ config: { target: { contentType: 'FORM', contentName: '验收' }, presentation: { position: 'INLINE', actionBar: { buttons: { open: { label: '打开验收' } } } }, actions: ['VIEW'] } })
  const state = await setupFile('../../components/related-content/RelatedContentPageSettings.vue', {
    defineProps: () => ({ editor })
  }, 'buttonActions')
  assert.deepEqual(state.buttonActions.value.map(action => action.key), ['save'])
  editor.config.presentation.position = 'DIALOG'
  assert.deepEqual(state.buttonActions.value.map(action => action.key), ['save', 'open'])
  assert.equal(state.buttonActions.value.find(action => action.key === 'open').scope, 'trigger')
  editor.config.target.contentType = 'LIST'
  assert.deepEqual(state.buttonActions.value.map(action => action.key), ['open'])
  editor.config.presentation.position = 'INLINE'
  assert.deepEqual(state.buttonActions.value.map(action => action.key), ['refresh'])
  assert.equal(editor.config.presentation.actionBar.buttons.open.label, '打开验收')
})
