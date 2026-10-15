import assert from 'node:assert/strict'
import { relatedContentIsVisible, relatedContentLayoutStyle, relatedContentListProps, relatedContentPresentationError, normalizeRelatedContentActionBar } from '../related-content-presentation.js'
const presentation = { layout: { height: 600, width: 75, padding: 0 }, listOptions: { showSearch: false, showPagination: false, showToolbar: false, showRowActions: true, pageSize: 37, maxHeight: 240 }, visibleModes: { create: false }, visibilityCondition: { version: 1, root: { type: 'GROUP', logic: 'AND', children: [{ type: 'CONDITION', property: 'name', operator: '==', value: '可见' }] } } }
assert.equal(relatedContentPresentationError(presentation, 'LIST'), '')
assert.equal(relatedContentIsVisible(presentation, { name: '可见' }, 'view'), true)
assert.equal(relatedContentIsVisible(presentation, { name: '其他' }, 'view'), false)
assert.equal(relatedContentIsVisible(presentation, { name: '可见' }, 'create'), false)
assert.equal(relatedContentIsVisible({ visibilityCondition: {} }), false, '不完整显示条件不能意外显示')
assert.deepEqual(relatedContentLayoutStyle(presentation), { width: '75%', height: '600px', overflow: 'auto', padding: '0px', boxSizing: 'border-box' })
assert.deepEqual(relatedContentListProps(presentation, ['VIEW']), { showSearch: false, showPagination: false, showToolbar: false, showRowActions: true, pageSize: 37, maxHeight: 240 })
assert.equal(relatedContentListProps(presentation, []).showRowActions, false, '显示开关不能扩大动作能力')
assert.equal(relatedContentListProps({}).showSearch, true, '历史配置保持默认值')
for (const [key, value] of [['pageSize', 0], ['pageSize', 201], ['pageSize', 1.5], ['maxHeight', 2001], ['showToolbar', 'false'], ['script', true]]) {
  assert.notEqual(relatedContentPresentationError({ listOptions: { [key]: value } }, 'LIST'), '')
}
assert.notEqual(relatedContentPresentationError(presentation, 'FORM'), '')
assert.notEqual(relatedContentPresentationError({ layout: { height: 6000 } }, 'FORM'), '')
const actionBar = {
  position: 'BOTH', align: 'RIGHT', gap: 12,
  buttons: { save: { label: '保存验收信息', icon: 'Check', buttonType: 'primary', buttonAppearance: 'DEFAULT', size: 'large', textColor: '#fff', backgroundColor: '#1677ff', borderColor: '#1677ff', borderRadius: 6, sort: 10 } }
}
assert.equal(relatedContentPresentationError({ actionBar }, 'FORM'), '')
assert.notEqual(relatedContentPresentationError({ actionBar }, 'LIST'), '', '表单保存按钮不能混入列表外观配置')
assert.equal(relatedContentPresentationError({ actionBar: { buttons: { refresh: { label: '重新加载' }, open: { label: '查看记录' } } } }, 'LIST'), '')
for (const invalid of [
  null,
  { position: 'HIDDEN' },
  { gap: 41 },
  { buttons: { save: { visible: false } } },
  { buttons: { save: { enabled: false } } },
  { buttons: { save: { onClick: 'custom-action' } } },
  { buttons: { save: { backgroundColor: 'url(example.com)' } } },
  { buttons: { remove: { label: '删除' } } }
]) {
  assert.notEqual(relatedContentPresentationError({ actionBar: invalid }, 'FORM'), '', '展示配置不能接受行为开关、额外动作或任意 CSS')
}
const switchedToList = normalizeRelatedContentActionBar({ ...actionBar, buttons: { ...actionBar.buttons, open: { label: '查看验收' } } }, 'LIST')
assert.deepEqual(switchedToList, { position: 'BOTH', align: 'RIGHT', gap: 12, buttons: { open: { label: '查看验收' } } }, '表单改为列表仅移除表单专属按钮')
assert.deepEqual(normalizeRelatedContentActionBar({ ...switchedToList, buttons: { ...switchedToList.buttons, refresh: { label: '刷新' } } }, 'FORM'), switchedToList, '列表改为表单保留打开入口和布局')
console.log('related content presentation tests passed')
