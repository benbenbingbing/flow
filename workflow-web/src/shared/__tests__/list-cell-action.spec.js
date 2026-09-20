import assert from 'node:assert/strict'
import { buildCellActionMap, cellMappingConflict, hidesMappedRowButton, supportsCellAction } from '../list-cell-action.js'
import { normalizeListActionForSave, listActionFingerprint } from '../list-config-design.js'

const fields = [{ fieldCode: 'name', showInList: true }, { fieldCode: 'secret', showInList: false }]
const view = { key: 'view', label: '查看', type: 'built-in', mappedFieldCode: 'name', hideWhenMapped: true }
const actionMap = buildCellActionMap([view], fields)
assert.equal(actionMap.get('name'), view)
assert.equal(hidesMappedRowButton(view, actionMap), true)
assert.equal(hidesMappedRowButton({ ...view, hideWhenMapped: false }, actionMap), false)
assert.equal(buildCellActionMap([{ ...view, enabled: false }], fields).size, 0)
assert.equal(buildCellActionMap([{ ...view, mappedFieldCode: '' }], fields).size, 0)
assert.equal(buildCellActionMap([{ ...view, mappedFieldCode: 'deleted' }], fields).size, 0)
const hiddenFieldButton = { ...view, mappedFieldCode: 'secret' }
assert.equal(hidesMappedRowButton(hiddenFieldButton, buildCellActionMap([hiddenFieldButton], fields)), false,
  '隐藏字段允许配置，但不能导致原按钮入口丢失')
const edit = { ...view, key: 'edit', label: '编辑' }
assert.equal(buildCellActionMap([view, edit], fields).size, 0, '冲突映射不能按顺序猜测动作')
assert.equal(hidesMappedRowButton(view, buildCellActionMap([view, edit], fields)), false)
assert.equal(cellMappingConflict(view, [view, { ...edit, enabled: false }])?.key, 'edit')
assert.equal(cellMappingConflict(view, [view]), null)
for (const key of ['view', 'edit', 'approve', 'delete']) assert.equal(supportsCellAction({ ...view, key }), true)
for (const customMode of ['', 'handler', 'event', 'open-form', 'open-list', 'open-related-content']) {
  assert.equal(supportsCellAction({ key: 'custom', type: 'custom', customMode }), true)
}
assert.equal(supportsCellAction({ key: 'custom', type: 'custom', customMode: 'component' }), false)

// 增量保存、重载及发布快照都使用同一份 actionParams，false 与清空不能遗留旧隐藏标记。
for (const hideWhenMapped of [true, false]) {
  const saved = normalizeListActionForSave({ ...view, hideWhenMapped }, 'ROW')
  const restored = { key: saved.buttonKey, type: saved.buttonType, ...JSON.parse(JSON.stringify(saved.actionParams)) }
  const restoredMap = buildCellActionMap([restored], fields)
  assert.equal(restoredMap.get('name'), restored)
  assert.equal(hidesMappedRowButton(restored, restoredMap), hideWhenMapped)
}
assert.deepEqual(normalizeListActionForSave({ ...view, mappedFieldCode: '' }, 'ROW').actionParams, {})
assert.deepEqual(normalizeListActionForSave(view, 'TOOLBAR').actionParams, {})
assert.deepEqual(normalizeListActionForSave({ ...view, type: 'custom', customMode: 'component' }, 'ROW').actionParams, {})
assert.notEqual(listActionFingerprint(view, 'ROW'), listActionFingerprint({ ...view, hideWhenMapped: false }, 'ROW'))
assert.equal(normalizeListActionForSave(hiddenFieldButton, 'ROW').actionParams.mappedFieldCode, 'secret')
console.log('list cell action tests passed: mapping, visibility fallback, conflicts, save/reload, clear')
