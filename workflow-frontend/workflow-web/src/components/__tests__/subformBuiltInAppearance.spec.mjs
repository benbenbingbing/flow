import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { computed, reactive, ref, nextTick } from 'vue'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import { extractSfcFunctions } from '../../../scripts/test-sfc-functions.mjs'
import * as appearance from '../../../../packages/workflow-core/src/shared/builtin-button-appearance.js'
import { resolveFormContainerAppearance } from '../../../../packages/workflow-core/src/shared/form-container-appearance.js'

const webFile = new URL('../SubFormRenderer.vue', import.meta.url)
const mobileFile = new URL('../../../../packages/workflow-mobile-ui/src/form/MobileSubForm.vue', import.meta.url)
const mobileButtonFile = new URL('../../../../packages/workflow-mobile-ui/src/form/MobileBuiltInActionButton.vue', import.meta.url)

function webState(actionBar = {}) {
  const props = reactive({ config: { actionBar, minRows: 1, maxRows: 3, repeatable: true } })
  const rowData = ref([{ name: '第一行' }, { name: '第二行' }]), canEdit = ref(true)
  const state = extractSfcFunctions(webFile, [
    'maxRows', 'minRows', 'canAddRow', 'canRemoveRow', 'actionBar', 'hasCustomToolbarPlacement',
    'addDefaults', 'removeDefaults', 'actionBarStyle', 'showTopActions', 'showBottomActions', 'tableRemoveDefaults', 'tableRemoveWidth', 'addRow', 'removeRow'
  ], {
    ...appearance, computed, props, rowData, canEdit, isRepeatable: ref(true),
    appendBlankRow: () => rowData.value.push({}), clearRowErrors() {}
  })
  return { ...state, props, rowData, canEdit }
}

test('PC 多行子表可上下显示新增入口，自定义样式不能改变原有行数限制和删除对象', () => {
  const state = webState({ position: 'BOTH', align: 'CENTER', gap: 0, buttons: {
    add: { label: '新增验收项', icon: 'Plus', buttonType: 'success', hidden: true, handler: 'remove' },
    remove: { label: '移除此项', icon: 'Delete', buttonAppearance: 'PLAIN' }
  } })
  assert.equal(state.hasCustomToolbarPlacement.value, true)
  assert.equal(state.showTopActions.value, true); assert.equal(state.showBottomActions.value, true)
  assert.deepEqual(state.actionBarStyle.value, { justifyContent: 'center', gap: '0px' })
  assert.equal(state.actionBar.value.buttons.add.handler, undefined)
  assert.equal(state.actionBar.value.buttons.add.hidden, undefined)
  assert.ok(state.tableRemoveWidth.value > 64)
  state.addRow(); state.addRow()
  assert.equal(state.rowData.value.length, 3); assert.equal(state.canAddRow.value, false)
  state.removeRow(0)
  assert.equal(state.rowData.value[0].name, '第二行', '删除仍作用于点击的行索引')
  state.removeRow(1); state.removeRow(0)
  assert.equal(state.rowData.value.length, 1); assert.equal(state.canRemoveRow.value, false)
  state.canEdit.value = false
  assert.equal(state.canAddRow.value, false); assert.equal(state.canRemoveRow.value, false)
})

test('PC 历史配置保持标题/空态入口和纯图标操作列，显式底部布局可以恢复默认', () => {
  const state = webState()
  assert.equal(Boolean(state.hasCustomToolbarPlacement.value), false)
  assert.equal(state.tableRemoveWidth.value, 64)
  state.props.config.actionBar = { position: 'BOTTOM', align: 'RIGHT', buttons: { add: { label: '新建一行' } } }
  assert.equal(state.showTopActions.value, false); assert.equal(state.showBottomActions.value, true)
  assert.deepEqual(state.actionBarStyle.value, { justifyContent: 'flex-end', gap: '8px' })
  state.props.config.actionBar = {}
  assert.equal(Boolean(state.hasCustomToolbarPlacement.value), false)
})

test('表格删除清空图标后补回动作名，圆形外观回退也保留可见入口', () => {
  const state = webState()
  const button = () => appearance.resolveBuiltInButton(state.actionBar.value, 'remove', state.tableRemoveDefaults.value)
  assert.equal(button().icon, 'Delete'); assert.equal(button().label, '')
  assert.equal(state.tableRemoveWidth.value, 64)
  for (const buttonAppearance of ['TEXT', 'CIRCLE']) {
    state.props.config.actionBar = { buttons: { remove: { icon: '', buttonAppearance } } }
    assert.equal(button().icon, ''); assert.equal(button().label, '删除')
    assert.notEqual(button().buttonAppearance, 'CIRCLE')
    assert.equal(state.tableRemoveWidth.value, 76)
  }
  state.props.config.actionBar = { buttons: { remove: { icon: '', label: '移除此项' } } }
  assert.equal(button().label, '移除此项')
  assert.equal(state.tableRemoveWidth.value, 104)
})

test('SubFormField 将当前引用的按钮配置传给渲染器，同时保留关系必填性和容器外观', () => {
  const actionBar = { position: 'BOTTOM', buttons: { add: { label: '新增验收项' } } }
  const { subFormConfig } = extractSfcFunctions(new URL('../../extensions/builtin/fields/components/SubFormField.vue', import.meta.url), ['subFormConfig'], {
    computed, props: { field: { nodeType: 'REPEATER', fieldCode: 'items', isRequired: 1 } },
    parsedComponentProps: ref({ showPadding: false, showBorder: true, subFormConfig: { actionBar, layout: 'table' } }),
    externalFormFields: ref([]), getSubFieldsFromField: () => [], resolveFormContainerAppearance,
    subFormMeta: ref({ relationType: 'ONE_TO_MANY' }), hasNodeTree: ref(false), runtimeFields: ref([]), refFormLayoutType: ref('vertical')
  })
  assert.deepEqual(subFormConfig.value.actionBar, actionBar)
  assert.equal(subFormConfig.value.minRows, 1)
  assert.equal(subFormConfig.value.autoAddMinRows, false)
  assert.equal(subFormConfig.value.layout, 'table')
  assert.equal(subFormConfig.value.showBorder, true)
})

test('移动端缺省新增保持底部占满宽度；显式上下操作栏共用原新增和确认删除逻辑', async () => {
  const config = ref({}), props = reactive({ disabled: false }), rows = ref([{ name: '甲' }, { name: '乙' }])
  const loading = ref(false), confirmations = [], opened = ref(''), runtimes = new Map(), rowRefs = new Map()
  let accept = false
  const state = extractSfcFunctions(mobileFile, [
    'actionBar', 'addDefaults', 'removeDefaults', 'useDefaultAlignment', 'actionBarStyle', 'showTopActions', 'showBottomActions', 'canAddRow', 'addRow', 'removeRow'
  ], {
    ...appearance, computed, props, config, rows, loading, maxRows: ref(3), nextTick, opened,
    publish: value => { rows.value = value }, rowKey: row => row.name || 'new', runtimes, rowRefs,
    showConfirmDialog: async options => { confirmations.push(options); if (!accept) throw new Error('cancel') }
  })
  assert.equal(state.showTopActions.value, false); assert.equal(state.showBottomActions.value, true)
  assert.equal(state.useDefaultAlignment.value, true)
  config.value = { actionBar: { position: 'BOTH', align: 'RIGHT', buttons: { add: { label: '增加项目' }, remove: { label: '删除项目' } } } }
  assert.equal(state.showTopActions.value, true); assert.equal(state.showBottomActions.value, true)
  assert.equal(state.useDefaultAlignment.value, false)
  await state.addRow(); assert.equal(rows.value.length, 3); assert.equal(state.canAddRow.value, false)
  const first = rows.value[0]
  await state.removeRow(first); assert.equal(rows.value.length, 3)
  accept = true; await state.removeRow(first)
  assert.equal(rows.value[0].name, '乙'); assert.equal(confirmations.length, 2)
  props.disabled = true; assert.equal(state.canAddRow.value, false)
})

test('移动按钮保持原生删除默认外观，并将受限颜色、图标和圆形样式映射给 Vant', () => {
  const props = reactive({ actionKey: 'remove', nativeDefault: true, config: {}, defaults: { label: '删除', icon: '', buttonType: 'danger', buttonAppearance: 'TEXT', size: 'small' } })
  const state = extractSfcFunctions(mobileButtonFile, ['icons', 'button', 'override', 'useNative', 'icon', 'buttonType', 'buttonSize', 'appearanceClasses', 'buttonStyle'], { ...appearance, computed, props })
  assert.equal(state.useNative.value, true)
  props.config = { buttons: { remove: { label: '移除项目' } } }
  assert.equal(state.useNative.value, true); assert.equal(state.button.value.label, '移除项目')
  props.config = { buttons: { remove: { icon: 'Delete', buttonAppearance: 'CIRCLE', size: 'large', textColor: '#fff', backgroundColor: '#123456', borderColor: '#abc', borderRadius: 12, sort: 999 } } }
  assert.equal(state.useNative.value, false); assert.equal(state.icon.value, 'delete-o')
  assert.equal(state.appearanceClasses.value['mobile-built-in-action--circle'], true)
  assert.equal(state.buttonSize.value, 'large')
  assert.deepEqual(state.buttonStyle.value, { color: '#fff', backgroundColor: '#123456', borderColor: '#abc', borderRadius: '12px' })
  props.config = { buttons: { remove: { buttonAppearance: 'CIRCLE', icon: '' } } }
  assert.equal(state.appearanceClasses.value['mobile-built-in-action--circle'], false)
})

for (const file of [webFile, mobileFile, mobileButtonFile, new URL('../form-designer/FormNodeDataSettings.vue', import.meta.url)]) {
  test(`${file.pathname.split('/').at(-1)} 模板和 setup 可编译`, () => {
    const { descriptor, errors } = parse(readFileSync(file, 'utf8'))
    assert.deepEqual(errors, [])
    compileScript(descriptor, { id: 'subform-actions' })
    assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: file.pathname, id: 'subform-actions' }).errors, [])
  })
}
