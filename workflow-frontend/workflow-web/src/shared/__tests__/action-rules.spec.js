import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  ACTION_RULE_VERSION,
  createActionRulePreset,
  createEmptyActionRule,
  hasActionRuleComparisonValue,
  hasActionRuleConditions,
  normalizeActionRuleSelectValue,
  summarizeActionRule,
  toEditableActionRuleRoot
} from '../action-rules.js'

assert.deepEqual(createEmptyActionRule(), {
  version: 2,
  visibleWhen: null,
  enabledWhen: null,
  disabledMessage: ''
})
assert.equal(ACTION_RULE_VERSION, 2)

for (const preset of [
  'OWN_DATA',
  'OWN_DRAFT',
  'OWN_DRAFT_OR_WITHDRAWN',
  'CURRENT_ASSIGNEE',
  'RUNNING',
  'SAME_DEPT',
  'STATUS'
]) {
  const definition = createActionRulePreset(preset)
  assert.equal(definition?.root?.type, 'GROUP', `${preset} 预设必须使用条件组根节点`)
  assert.ok(definition.root.children.length > 0, `${preset} 预设不得生成空条件组`)
}
assert.deepEqual(createActionRulePreset('ALWAYS'), { root: null, message: '' })
assert.equal(createActionRulePreset('UNKNOWN'), null)
assert.deepEqual(normalizeActionRuleSelectValue('DRAFT', 'IN'), ['DRAFT'])
assert.equal(normalizeActionRuleSelectValue(['DRAFT'], 'EQ'), 'DRAFT')
assert.equal(normalizeActionRuleSelectValue([], 'NE'), '')
assert.equal(hasActionRuleComparisonValue(['DRAFT'], 'IN'), true)
assert.equal(hasActionRuleComparisonValue([], 'IN'), false)
assert.equal(hasActionRuleComparisonValue([''], 'NOT_IN'), false)
assert.equal(hasActionRuleComparisonValue(['DRAFT'], 'EQ'), false)
assert.equal(hasActionRuleComparisonValue(',', 'IN'), false)
assert.equal(hasActionRuleComparisonValue('DRAFT,APPROVED', 'IN'), true)
assert.equal(hasActionRuleComparisonValue('', 'EMPTY'), true)
const leafRoot = { type: 'RELATION', relation: 'CURRENT_USER_IS_CREATOR' }
assert.deepEqual(toEditableActionRuleRoot(leafRoot), {
  type: 'GROUP',
  logic: 'AND',
  children: [leafRoot]
})
const groupRoot = { type: 'GROUP', logic: 'OR', children: [leafRoot] }
assert.equal(toEditableActionRuleRoot(groupRoot), groupRoot)
assert.equal(hasActionRuleConditions(createEmptyActionRule()), false)
assert.equal(
  summarizeActionRule({ version: 2, visibleWhen: { type: 'RELATION' } }),
  '显示条件'
)
assert.equal(
  summarizeActionRule({ version: 2, enabledWhen: { type: 'STATUS_CODE' } }),
  '启用条件'
)
assert.equal(
  summarizeActionRule({
    version: 2,
    visibleWhen: { type: 'RELATION' },
    enabledWhen: { type: 'STATUS_CODE' }
  }),
  '显示条件 + 启用条件'
)

const dialogSource = readFileSync(
  new URL('../../components/ActionRuleEditorDialog.vue', import.meta.url),
  'utf8'
)
const rulePanelSource = readFileSync(
  new URL('../../components/ActionRuleEditorPanel.vue', import.meta.url),
  'utf8'
)
const formPanelSource = readFileSync(
  new URL('../../components/FormButtonConfigPanel.vue', import.meta.url),
  'utf8'
)
const groupEditorSource = readFileSync(
  new URL('../../components/ActionRuleGroupEditor.vue', import.meta.url),
  'utf8'
)
for (const marker of [
  'visibleWhen',
  'enabledWhen',
  'disabledMessage',
  '显示条件',
  '启用条件',
  '不满足则隐藏',
  '不满足则禁用'
]) {
  assert.ok(rulePanelSource.includes(marker), `按钮条件编辑面板缺少独立配置：${marker}`)
}
assert.equal(rulePanelSource.includes('unavailableBehavior'), false)
assert.match(
  rulePanelSource,
  /visiblePreset\.value\s*=\s*''[\s\S]*enabledPreset\.value\s*=\s*''/,
  '预设应用后应清空下拉，允许再次应用同一个预设'
)
assert.match(dialogSource, /existingRule\.version !== ACTION_RULE_VERSION/)
assert.match(dialogSource, /检测到旧版按钮条件/)
assert.match(dialogSource, /ActionRuleEditorPanel/)
assert.match(rulePanelSource, /\$\{branchLabel\}中存在空条件组/)
assert.match(rulePanelSource, /存在未选择的状态值/)
assert.match(rulePanelSource, /hasActionRuleComparisonValue/)
assert.match(groupEditorSource, /normalizeActionRuleSelectValue/)
assert.match(rulePanelSource, /显示后始终启用/)
assert.match(formPanelSource, /:allow-custom-conditions="false"/)
assert.match(dialogSource, /width="1180px"/)

// 表单自定义按钮的条件由“更多”统一承载；内置按钮仍保留独立入口。
assert.equal(formPanelSource.includes('configureCustomRule(row)'), false,
  '自定义按钮行不应再保留独立“条件”入口')
assert.match(formPanelSource, /configureBuiltInRule\(row\.key\)/,
  '内置按钮仍需保留独立条件配置入口')
const advancedDialogStart = formPanelSource.indexOf('title="自定义按钮设置"')
const advancedDialogFooter = formPanelSource.indexOf('<template #footer>', advancedDialogStart)
const embeddedRulePanel = formPanelSource.indexOf('<ActionRuleEditorPanel', advancedDialogStart)
assert.ok(advancedDialogStart >= 0, '应保留自定义按钮“更多”弹框')
assert.ok(embeddedRulePanel > advancedDialogStart && embeddedRulePanel < advancedDialogFooter,
  '显示与启用条件面板应嵌入自定义按钮“更多”弹框的底部')
const advancedDialogBody = formPanelSource.slice(advancedDialogStart, advancedDialogFooter)
assert.equal(advancedDialogBody.includes('label="适用条件"'), false,
  '更多弹框不应再显示跳转到条件弹框的“适用条件”表单项')

console.log('action rule v2 tests passed')
