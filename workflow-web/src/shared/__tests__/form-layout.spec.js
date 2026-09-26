import assert from 'node:assert/strict'
import { resolveFormLabelPosition, resolveFormLabelWidth, resolveNewFormFieldGridSpan } from '../form-layout.js'
import { resolveFormNodeLayoutSpan } from '@flow/workflow-core/form-node-property-schema'
import { buildEntityConfigKey } from '../entity-config-key.js'
import { extractSfcFunctions } from '../../../scripts/test-sfc-functions.mjs'

// 历史快照中被布局模式覆盖的 gridSpan 不能因升级而突然生效。
for (const [layoutType, labelPosition, span] of [
  ['vertical', 'top', 24], ['horizontal', 'right', 12], ['grid', 'right', 8]
]) {
  const form = Object.freeze({ layoutType, viewConfig: Object.freeze({ labelWidth: 160 }) })
  assert.equal(resolveFormLabelPosition(form), labelPosition)
  assert.equal(resolveFormLabelWidth(form), labelPosition === 'top' ? 'auto' : '160px')
  assert.equal(resolveFormNodeLayoutSpan({ nodeType: 'FIELD', gridSpan: 8 }, layoutType), span)
  assert.equal(form.viewConfig.labelPosition, undefined, '读取历史表单不能写入默认值')
  // 修改标签位置不改变旧表单的列数；新表单也可以把多列与顶部标签组合。
  for (const position of ['top', 'left', 'right']) {
    const saved = { ...form, viewConfig: JSON.stringify({ labelPosition: position, labelWidth: 180 }) }
    assert.equal(resolveFormLabelPosition(saved), position)
    assert.equal(resolveFormLabelWidth(saved), position === 'top' ? 'auto' : '180px')
    assert.equal(resolveFormNodeLayoutSpan({ nodeType: 'FIELD', gridSpan: 8 }, saved.layoutType), span)
  }
}

for (const value of [null, '', '{invalid}', { labelPosition: 'invalid', labelWidth: -1 }]) {
  assert.equal(resolveFormLabelPosition({ layoutType: 'vertical', viewConfig: value }), 'top')
  assert.equal(resolveFormLabelWidth({ layoutType: 'grid', viewConfig: value }), '120px')
}
assert.equal(resolveFormLabelPosition(null), 'right')
assert.equal(resolveFormLabelWidth({ viewConfig: { labelWidth: '150' } }), '150px')
const form = { layoutType: 'grid', viewConfig: { labelPosition: 'right', labelWidth: 120 } }
const draft = { labelPosition: 'left', labelWidth: 200 }
assert.equal(resolveFormLabelPosition(form, draft), 'left', '设计器使用尚未保存的标签设置')
assert.equal(resolveFormLabelWidth(form, draft), '200px')
assert.equal(form.viewConfig.labelWidth, 120, '草稿不能覆盖已保存配置')

for (const span of [24, 12, 8, 16]) {
  assert.equal(resolveFormNodeLayoutSpan({ nodeType: 'FIELD', gridSpan: span }, 'grid'), span)
}
for (const layout of ['vertical', 'horizontal']) {
  assert.equal(resolveFormNodeLayoutSpan({ nodeType: 'ACTION_SLOT', gridSpan: 8 }, layout), 24)
}
assert.equal(resolveFormNodeLayoutSpan({ nodeType: 'FIELD', props: { gridSpan: 8 } }, 'grid', 12), 8)
assert.equal(resolveFormNodeLayoutSpan({ nodeType: 'FIELD' }, 'grid', 12), 12, '显式 GRID 继续支持容器默认宽度')

// 从真实创建请求的 JSON 回填设计器，再执行实际添加方法，验证列数跨页面保存后才生效。
for (const [columns, expectedSpan] of [[1, 24], [2, 12], [3, 8]]) {
  const form = { formName: '测试表单', formKey: 'detail', layoutType: 'grid', status: 1, defaultColumnCount: columns }
  const isEdit = { value: false }
  let created, patched
  const { handleSubmit } = extractSfcFunctions(new URL('../../views/EntityFormList.vue', import.meta.url), ['handleSubmit'], {
    form, isEdit, entityId: 'entity-test', entityInfo: { value: { entityCode: 'TEST' } },
    formRef: { value: { validate: async () => true } }, submitLoading: { value: false },
    isCustomRendererMode: { value: false }, dialogVisible: { value: true },
    createForm: async payload => { created = payload },
    patchFormMetadata: async (_id, payload) => { patched = payload },
    buildEntityConfigKey, loadForms() {}, ElMessage: { success() {}, error(message) { assert.fail(message) } }
  })
  await handleSubmit()
  assert.equal(JSON.parse(created.viewConfig).defaultColumnCount, columns)
  assert.equal(created.formKey, 'TEST_detail')

  const existing = { id: 'existing', fieldCode: 'existing', gridSpan: 16 }
  const formFields = { value: [existing] }
  const viewConfig = { value: JSON.parse(created.viewConfig) }
  const { addField } = extractSfcFunctions(new URL('../../views/EntityFormDesignByEntity.vue', import.meta.url), ['addField'], {
    formFields, viewConfig, formId: 'form-test', isSystemEntity: { value: false },
    isFieldInForm: field => formFields.value.some(item => item.fieldId === field.id),
    resolveFormNodeBinding: field => ({ bindingType: 'ENTITY_FIELD', bindingRef: field.fieldCode }),
    resolveDefaultParentId: () => '', nextNodePlacement: () => ({ orderKey: 1000000, sortOrder: 1 }),
    getDefaultComponentType: () => 'input', stringifyConfig: JSON.stringify,
    resolveNewFormFieldGridSpan, isSubFormField: () => false, isSubListField: () => false,
    selectField() {}, ElMessage: { success() {}, warning() {} }
  })
  addField({ id: 'first', fieldCode: 'first', fieldName: '首个属性', fieldType: 'VARCHAR' })
  assert.equal(formFields.value[1].gridSpan, expectedSpan)
  formFields.value[1].gridSpan = 18
  addField({ id: 'second', fieldCode: 'second', fieldName: '第二属性', fieldType: 'VARCHAR' })
  assert.deepEqual(formFields.value.map(field => field.gridSpan), [16, 18, expectedSpan],
    '新增属性只能初始化自身，不能重排历史节点或覆盖手动宽度')

  // 外层编辑基本信息不提交 viewConfig，避免清掉设计器维护的列数及其他视图配置。
  isEdit.value = true
  form.id = 'form-test'
  form.revision = 2
  await handleSubmit()
  assert.equal(Object.hasOwn(patched, 'viewConfig'), false)
}
for (const viewConfig of [undefined, null, '', '{invalid}', {}, { defaultColumnCount: 0 }, { defaultColumnCount: 4 }, { defaultColumnCount: 1.5 }]) {
  assert.equal(resolveNewFormFieldGridSpan(viewConfig), 24, '旧表单或无效列数保持单列默认宽度')
}
console.log('form-layout tests passed')
