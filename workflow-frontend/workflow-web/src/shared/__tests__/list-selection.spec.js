import assert from 'node:assert/strict'
import {
  normalizeListSelectionMode,
  isSelectionToolbarButton,
  toolbarSelectionRequirement,
  toolbarSelectionReason
} from '../list-selection.js'
import { normalizeListActionForSave, listActionFingerprint } from '../list-config-design.js'

const rows = [{ id: 'a' }, { id: 'b' }]
for (const mode of ['handler', 'component', 'event', 'open-list', 'open-form']) {
  const button = { key: 'custom_action', type: 'custom', customMode: mode }
  for (const requirement of ['NONE', 'SINGLE', 'AT_LEAST_ONE']) {
    const configured = { ...button, selectionRequirement: requirement }
    const saved = normalizeListActionForSave(configured, 'TOOLBAR')
    const restored = { type: saved.buttonType, customMode: saved.customMode, ...saved.actionParams }
    assert.equal(toolbarSelectionRequirement(restored), requirement, '保存重载不能丢失选择要求')
    assert.equal(isSelectionToolbarButton(restored), requirement !== 'NONE')
    for (const count of [0, 1, 2]) {
      const allowed = requirement === 'NONE' || (requirement === 'SINGLE' ? count === 1 : count >= 1)
      assert.equal(toolbarSelectionReason(restored, rows.slice(0, count)) === '', allowed, `${mode}/${requirement}/${count}`)
    }
    assert.equal(normalizeListActionForSave(configured, 'ROW').actionParams.selectionRequirement, undefined)
  }
  assert.notEqual(listActionFingerprint(button, 'TOOLBAR'), listActionFingerprint({ ...button, selectionRequirement: 'SINGLE' }, 'TOOLBAR'))
}
for (const key of ['batchDelete', 'exportSelected']) {
  assert.equal(toolbarSelectionRequirement({ key, selectionRequirement: 'NONE' }), 'AT_LEAST_ONE')
}
assert.equal(toolbarSelectionRequirement({ type: 'custom', customMode: 'open-related-content', selectionRequirement: 'NONE' }), 'SINGLE')
for (const customMode of ['open-list', 'open-form']) {
  for (const sourceType of ['FIELD', 'RECORD_ID']) {
    assert.equal(toolbarSelectionRequirement({ type: 'custom', customMode, selectionRequirement: 'AT_LEAST_ONE', parameterMappings: [{ sourceType }] }), 'SINGLE')
  }
}
assert.equal(toolbarSelectionRequirement({ type: 'custom', customMode: 'open-list', selectionMode: 'SINGLE' }), 'NONE', '目标列表单选不能成为来源列表的选择要求')
assert.ok(toolbarSelectionReason({ type: 'custom', selectionRequirement: 'INVALID' }, rows))
assert.equal(normalizeListSelectionMode('SINGLE'), 'MULTIPLE')
assert.equal(normalizeListSelectionMode('MULTIPLE'), 'MULTIPLE')
assert.equal(normalizeListSelectionMode('NONE'), 'NONE')
console.log('list selection requirements passed: persistence, zero/one/many, fixed actions, picker separation')
