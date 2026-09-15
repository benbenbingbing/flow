import assert from 'node:assert/strict'
import { collectCrossFieldRuntimeFields, createCrossFieldController, resolveCrossFieldRuntimeState } from '../form-cross-field-runtime.js'
import { normalizeFormFieldValidation } from '../form-node-property-schema.js'

const config = { version: 1, rules: [{ id: 'range', operator: 'GE', targetFieldCode: 'start', message: '结束不得早于开始' }] }
const start = { id: 's', fieldCode: 'start', fieldType: 'INTEGER', isHidden: 1 }
const end = { id: 'e', fieldCode: 'end', fieldType: 'INTEGER', validationRules: { crossField: config } }
let fields = [start, end]
let record = { start: 10, end: 5 }
let mode = 'edit'
let readonly = false
let form = { fields }
const state = field => resolveCrossFieldRuntimeState(field, { form, record, mode, readonly })
const controller = createCrossFieldController({ getFields: () => fields, getRecord: () => record, getState: state })

assert.equal(controller.refresh().valid, true, '初始化不提示')
controller.touch('start')
assert.equal(controller.refresh().valid, false, '改变比较来源也校验所属字段，来源隐藏不影响比较')
record.end = 10
assert.equal(controller.refresh().valid, true, '相等允许')
record.start = 11
assert.equal(controller.refresh().valid, false, '程序更新引用值刷新已有错误')
fields[1].isReadonly = 1
assert.equal(controller.refresh().valid, true)
fields[1].isReadonly = 0
assert.equal(controller.refresh().valid, false)
readonly = true
assert.equal(controller.validate().valid, true)
readonly = false
mode = 'view'
assert.equal(controller.validate().valid, true)
mode = 'approve'
fields[1].componentProps = { linkageRules: { visibilityRule: 'show == true', disabledRule: 'locked == true' } }
record.show = false
assert.equal(controller.refresh().valid, true)
record.show = true; record.locked = true
assert.equal(controller.refresh().valid, true)
record.locked = false
assert.equal(controller.refresh().valid, false)

record.end = 20
const serverError = { fieldCode: 'end', ruleId: 'range', targetFieldCode: 'start', message: '提交处理后的结束值过早' }
assert.equal(controller.applyServerErrors([serverError]).valid, false)
assert.equal(controller.refresh().valid, false, '未改变值时保留服务端错误')
record.end = 21
assert.equal(controller.refresh().valid, true, '程序或用户修正后清除过期服务端错误')
controller.applyServerErrors([serverError])
fields[1].validationRules = { crossField: { version: 1, rules: [] } }
assert.equal(controller.refresh().valid, true, '删除规则后不残留错误')
fields[1].validationRules = { crossField: config }
record.end = 1
controller.reset()
assert.equal(controller.refresh().valid, true)
assert.equal(controller.validate().valid, false, '未触碰字段仍在提交时校验')
record.end = null
assert.equal(controller.validate().valid, true, '空值比较不产生必填限制')
delete record.start
assert.equal(controller.validate().deferred, true, '未加载的比较来源留给服务端终检')

form = { fields, nodes: [
  { id: 'tab', nodeType: 'TAB_PANE', propsDocument: { active: false, collapsed: true } },
  { id: 's', nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', bindingRef: 'start' },
  { id: 'e', parentId: 'tab', nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', bindingRef: 'end', rulesDocument: { validation: { crossField: { version: 1, rules: [] } } } },
  { id: 'sub', nodeType: 'SUB_FORM' },
  { id: 'child', parentId: 'sub', nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', bindingRef: 'child' }
] }
const projected = collectCrossFieldRuntimeFields(form)
assert.equal(projected.length, 2, '子表字段不进入主记录')
assert.deepEqual(projected[1].validationRules.crossField.rules, [], '节点显式清空覆盖旧投影')
assert.equal(state(end).visible, true, '未激活页签/折叠面板仍校验')
form.nodes[0].propsDocument.hidden = true
assert.equal(state(end).visible, false, '业务隐藏的父容器跳过')
form.nodes[0].propsDocument = { modeAccess: { approve: 'READONLY' } }
assert.equal(state(end).editable, false)
form.nodes[0].propsDocument = { permissionCode: 'admin' }
assert.equal(state(end).visible, false)
assert.equal(resolveCrossFieldRuntimeState(end, { form, mode, record, context: { permissions: ['admin'] } }).visible, true)
console.log('cross-field runtime: lifecycle, dependencies, field state and node scope passed')

for (const crossField of [config, { version: 1, rules: [] }]) {
  const validation = normalizeFormFieldValidation('INTEGER', { crossField, min: 0 })
  assert.deepEqual(validation.crossField, crossField, '保存与显式清空跨字段配置应保留')
}
