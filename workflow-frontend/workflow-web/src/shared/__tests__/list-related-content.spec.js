import assert from 'node:assert/strict'
import { normalizeListActionForSave } from '../list-config-design.js'
import { createEmptyRelatedContent, updateRelatedContentAnchor } from '../related-content.js'
import { findButtonRelatedContent, isButtonRelatedContent, relatedContentSelectionReason } from '../list-related-content.js'

const content = createEmptyRelatedContent({ ownerType: 'LIST' })
const button = { key: 'requirements', type: 'custom', customMode: 'open-related-content', compositionKey: content.compositionKey, label: '需求', buttonType: 'success', link: false }
for (const position of ['TOOLBAR', 'ROW']) {
  const saved = normalizeListActionForSave(button, position)
  const restored = { type: saved.buttonType, customMode: saved.customMode, ...JSON.parse(JSON.stringify(saved.actionParams)) }
  assert.equal(findButtonRelatedContent(restored, [content]), content, '保存后两种位置都能恢复同一关联内容')
  assert.equal(saved.styleType, 'success')
  assert.equal(saved.linkMode, false)
}
for (const anchorType of ['LIST_ACTION', 'ROW_ACTION', 'TOOLBAR_ACTION']) {
  content.anchorType = anchorType
  assert.equal(findButtonRelatedContent(button, [content]), content, '历史入口仍可供标准按钮选择')
}
content.config.enabled = false
assert.equal(findButtonRelatedContent(button, [content]), null)
content.config.enabled = true
assert.equal(findButtonRelatedContent(button, []), null)
assert.equal(findButtonRelatedContent({ ...button, compositionKey: 'other-owner' }, [content]), null)
content.config.presentation.position = 'INLINE'
updateRelatedContentAnchor(content, 'LIST')
assert.equal(isButtonRelatedContent(content), false, '内嵌内容不可作为打开按钮目标')
content.config.presentation.position = 'DRAWER'
updateRelatedContentAnchor(content, 'LIST')
assert.equal(content.anchorType, 'LIST_ACTION')
assert.equal(findButtonRelatedContent(button, [content]), content)
assert.ok(relatedContentSelectionReason([]))
assert.ok(relatedContentSelectionReason([{ id: 'a' }, { id: 'b' }]))
assert.ok(relatedContentSelectionReason([{}]))
assert.equal(relatedContentSelectionReason([{ id: 'a' }]), '')
console.log('list related content tests passed: save/reload, legacy targets, unavailable bindings, selection')
