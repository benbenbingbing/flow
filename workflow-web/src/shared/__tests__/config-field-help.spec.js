import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import {
  CONFIG_FIELD_HELP,
  getConfigFieldHelp
} from '../config-field-help.js'

const root = process.cwd()
const files = [
  'src/views/EntityDesign.vue',
  'src/views/EntityListConfigDesign.vue',
  'src/components/UiConfigPublishDialog.vue',
  'src/components/NodeConfigPanel.vue',
  'src/components/FlowActionConfigPanel.vue',
  'src/components/ui-config/EventBindingEditor.vue',
  'src/views/system/components/EntityVersionConfigDialogs.vue',
  'src/views/system/WorkCalendarManagement.vue',
  'src/components/form-designer/FormNodeDataSettings.vue',
  'src/components/ui-config/FormDataSourceCompatDialog.vue',
  'src/components/ui-config/EntitySelectionMappingEditor.vue',
  'src/components/ActionRuleEditorDialog.vue'
]

for (const [key, content] of Object.entries(CONFIG_FIELD_HELP)) {
  assert.ok(content.length >= 20, `配置帮助过短: ${key}`)
  assert.equal(getConfigFieldHelp(key), content)
}

const usedKeys = new Set()
for (const file of files) {
  const source = readFileSync(path.join(root, file), 'utf8')
  for (const match of source.matchAll(/<ConfigHelpLabel[\s\S]*?help-key="([^"]+)"[\s\S]*?\/>/g)) {
    usedKeys.add(match[1])
  }
}

for (const key of usedKeys) {
  assert.ok(CONFIG_FIELD_HELP[key], `页面引用了不存在的配置帮助: ${key}`)
}

for (const required of [
  'entityList.dataScopeMode',
  'uiConfig.releaseMode',
  'process.allowManualCc',
  'uiEvent.inheritanceMode',
  'entityVersion.applyStrategy'
]) {
  assert.ok(usedKeys.has(required), `关键复杂配置缺少问号帮助: ${required}`)
}

console.log(`config field help audit passed: ${usedKeys.size} usages, ${Object.keys(CONFIG_FIELD_HELP).length} definitions`)
