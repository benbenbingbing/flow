import assert from 'node:assert/strict'
import { relatedContentIsVisible, relatedContentLayoutStyle, relatedContentListProps, relatedContentPresentationError } from '../related-content-presentation.js'
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
console.log('related content presentation tests passed')
