import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { CONFIG_FIELD_HELP } from '../config-field-help.js'

const processDesignSource = readFileSync(new URL(
  '../../views/ProcessDesign.vue',
  import.meta.url
), 'utf8')
const nodeConfigPanelSource = readFileSync(new URL(
  '../../components/NodeConfigPanel.vue',
  import.meta.url
), 'utf8')

const desktopPanelStyle = processDesignSource.match(/\.node-config-panel\s*\{([\s\S]*?)\}/)?.[1] || ''
assert.match(
  desktopPanelStyle,
  /width:\s*33\.333vw\s*;/,
  '桌面端节点配置侧栏应约占视口宽度的三分之一'
)
assert.match(
  processDesignSource,
  /@media \(max-width:\s*900px\)[\s\S]*?\.node-config-panel\s*\{[\s\S]*?width:\s*100%\s*;[\s\S]*?min-width:\s*0\s*;/,
  '窄屏节点配置侧栏应继续使用全屏覆盖，避免三分之一宽度挤压配置项'
)

const multiInstanceStart = nodeConfigPanelSource.indexOf('title="多人办理（会签/或签）"')
const multiInstanceEnd = nodeConfigPanelSource.indexOf('<NextApproverConfigEditor', multiInstanceStart)
assert.ok(multiInstanceStart >= 0 && multiInstanceEnd > multiInstanceStart, '节点配置应保留多人办理外层分组')
const multiInstanceSource = nodeConfigPanelSource.slice(multiInstanceStart, multiInstanceEnd)

for (const nestedTitle of ['办理方式', '完成规则', '技术参数']) {
  assert.doesNotMatch(
    multiInstanceSource,
    new RegExp(`<SettingsSection\\b[^>]*\\btitle="${nestedTitle}"`),
    `多人办理不应继续嵌套“${nestedTitle}”分组`
  )
}

const fieldContracts = [
  {
    label: '办理模式',
    model: 'assigneeForm.multiInstanceDecision',
    helpKey: 'process.multiInstanceDecision',
    legacyTip: ''
  },
  {
    label: '执行方式',
    model: 'assigneeForm.multiInstanceType',
    helpKey: 'process.multiInstanceType',
    legacyTip: '并行：多人同时审批；串行：按顺序审批'
  },
  {
    label: '通过率阈值（%）',
    model: 'assigneeForm.multiInstanceCompletionRate',
    helpKey: 'process.multiInstanceCompletionCondition',
    legacyTip: ''
  },
  {
    label: '是否需要所有人审批',
    model: 'assigneeForm.multiInstanceNeedAllApprovers',
    helpKey: 'process.multiInstanceCompletionCondition',
    legacyTip: ''
  },
  {
    label: '集合变量',
    model: 'assigneeForm.collection',
    helpKey: 'process.multiInstanceCollection',
    legacyTip: '系统生成的用户ID集合变量，只读展示'
  },
  {
    label: '元素变量',
    model: 'assigneeForm.elementVariable',
    helpKey: 'process.multiInstanceElementVariable',
    legacyTip: '集合中单个用户ID在任务内使用的变量名'
  }
]

for (const { label, model, helpKey, legacyTip } of fieldContracts) {
  const formItemSource = multiInstanceSource.match(
    new RegExp(`<el-form-item\\s+label="${label}"[^>]*>([\\s\\S]*?)<\\/el-form-item>`)
  )?.[1] || ''
  assert.ok(formItemSource, `多人办理缺少“${label}”配置项`)
  assert.match(formItemSource, /<ConfigHelpLabel\b/, `“${label}”说明应收进问号帮助`)
  assert.ok(formItemSource.includes(`help-key="${helpKey}"`), `“${label}”问号帮助键不正确`)
  assert.ok(formItemSource.includes(`v-model="${model}"`), `“${label}”不应因扁平化丢失数据绑定`)
  assert.ok(CONFIG_FIELD_HELP[helpKey], `“${label}”问号帮助缺少帮助字典内容`)
  if (legacyTip) {
    assert.equal(multiInstanceSource.includes(legacyTip), false, `“${label}”不应继续内联展示说明文字`)
  }
}

console.log('node config layout contract passed')
