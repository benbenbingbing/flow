import assert from 'node:assert/strict'
import { applySchemaDefaults } from '@flow/workflow-core/config-runtime'
import {
  formatMaskedListCellValue,
  isListCellMaskingEnabled,
  listCellMaskingSchema,
  maskListCellValue
} from '../list-cell-masking.js'
import { mergeFieldConfig, normalizeFieldForSave } from '../../views/list-designer/listColumnModel.js'
import { applyListColumnTemplateSnapshot, buildListColumnTemplateSnapshot, createListColumnTemplateEditor } from '../list-column-template.js'

const config = applySchemaDefaults(listCellMaskingSchema, { maskingEnabled: true })
assert.deepEqual(config, { maskingEnabled: true, maskPrefixLength: 3, maskSuffixLength: 4 })
assert.equal(maskListCellValue('13812345678', config), '138****5678')
assert.equal(maskListCellValue('13812345678', {}), '13812345678')
assert.equal(maskListCellValue('短文本', config), '***', '首尾重叠时不能暴露全文')
assert.equal(maskListCellValue('1234567', config), '*******')
assert.equal(maskListCellValue('王😀小明', { ...config, maskPrefixLength: 1, maskSuffixLength: 1 }), '王**明')
assert.equal(maskListCellValue('abc', { ...config, maskPrefixLength: 0, maskSuffixLength: 0 }), '***')
assert.equal(maskListCellValue('abc', { ...config, maskPrefixLength: 1, maskSuffixLength: 0 }), 'a**')
assert.equal(maskListCellValue('abc', { ...config, maskPrefixLength: 0, maskSuffixLength: 1 }), '**c')
assert.equal(maskListCellValue('abcdef', { ...config, maskPrefixLength: -1, maskSuffixLength: 1.9 }), '*****f')
for (const invalid of [NaN, Infinity, 'bad', null, undefined, '']) {
  assert.equal(maskListCellValue('13812345678', { ...config, maskPrefixLength: invalid }), '138****5678')
}
assert.equal(maskListCellValue('abcdef', { ...config, maskPrefixLength: 1e300 }), '******')
assert.equal(maskListCellValue(0, config), '*')
assert.equal(maskListCellValue(false, config), '*****')
for (const empty of [null, undefined, '']) assert.equal(maskListCellValue(empty, config), empty)

const field = { fieldId: 'phone-field', fieldCode: 'phone', fieldType: 'STRING', renderConfig: JSON.stringify(config) }
const row = { data: { phone: '13812345678' } }
assert.equal(isListCellMaskingEnabled(field), true)
for (const renderConfig of ['', '{}', '{bad', '{"maskingEnabled":false}']) {
  assert.equal(isListCellMaskingEnabled({ renderConfig }), false)
}
assert.equal(formatMaskedListCellValue(row, field), '138****5678')
assert.equal(row.data.phone, '13812345678', '显示策略不能更改查询、编辑和动作使用的原值')
assert.equal(formatMaskedListCellValue({ ...row, extData: { phone: '13987654321' } }, field), '139****4321')
assert.equal(formatMaskedListCellValue({ data: { phone: 'id', phone_display: '13812345678' } }, field), '138****5678')
for (const empty of [null, undefined, '']) {
  assert.equal(formatMaskedListCellValue({ phone: empty }, { ...field, renderConfig: { ...config, emptyText: '暂无' } }), '暂无')
}
assert.equal(formatMaskedListCellValue({ phone: 'u1' }, { ...field, fieldType: 'USER' }, { 'USER::u1': '13812345678' }), '138****5678')
assert.equal(formatMaskedListCellValue({ phone: 'ACTIVE' }, {
  ...field, renderComponent: 'StatusBadge', renderConfig: { ...config, labelMap: { ACTIVE: '当前账号正常使用' } }
}), '当前账*正常使用')
assert.equal(formatMaskedListCellValue({ phone: 12345 }, {
  ...field, renderComponent: 'DefaultText', formatter: '2',
  renderConfig: { ...config, maskSuffixLength: 2 }
}), '123***00')
assert.equal(formatMaskedListCellValue({ phone: '2026-10-12T12:30:00' }, {
  ...field, renderComponent: 'DateFormatter',
  renderConfig: { ...config, pattern: 'yyyy-MM-dd', maskSuffixLength: 3 }
}), '202****-12')

// 保存协议、重新合并实体字段及模板初始化都应完整保留 renderConfig。
const saved = normalizeFieldForSave(field)
const [reloaded] = mergeFieldConfig([saved], {
  entityFields: [{ id: 'phone-field', fieldCode: 'phone', fieldType: 'STRING' }],
  availableListColumnInterfaces: [], isSystemEntity: false
})
assert.equal(formatMaskedListCellValue(row, reloaded), '138****5678')
const editor = createListColumnTemplateEditor()
editor.renderConfig = config
const snapshot = buildListColumnTemplateSnapshot(editor)
const initialized = { ...field, renderConfig: '' }
applyListColumnTemplateSnapshot(initialized, snapshot)
assert.equal(formatMaskedListCellValue(row, initialized), '138****5678')
console.log('list-cell-masking tests passed')
