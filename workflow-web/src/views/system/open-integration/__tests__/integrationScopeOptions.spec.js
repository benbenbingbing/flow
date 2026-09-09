import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import {
  INTEGRATION_SCOPE_OPTIONS,
  integrationScopeLabel
} from '../integrationScopeOptions.js'

const expectedOptions = [
  { value: 'embed.launch', label: '启动嵌入会话' },
  { value: 'process.definition.read', label: '读取流程定义' },
  { value: 'process.instance.start', label: '启动流程实例' },
  { value: 'process.instance.read', label: '读取流程实例' },
  { value: 'process.task.read', label: '读取流程任务' },
  { value: 'process.message.correlate', label: '关联流程消息' },
  { value: 'process.instance.cancel', label: '取消流程实例' }
]

assert.deepEqual(
  INTEGRATION_SCOPE_OPTIONS,
  expectedOptions,
  '下拉选项应显示中文，同时保留后端约定的 Scope 技术值'
)
for (const option of expectedOptions) {
  assert.equal(integrationScopeLabel(option.value), option.label)
}
assert.equal(
  integrationScopeLabel('future.scope'),
  'future.scope',
  '未知 Scope 应回显原值，便于识别新增或历史数据'
)

const createPageSource = await readFile(new URL('../../OpenIntegration.vue', import.meta.url), 'utf8')
const detailPanelSource = await readFile(new URL('../IntegrationApplicationPanel.vue', import.meta.url), 'utf8')

assert.match(
  createPageSource,
  /<el-form-item label="权限范围" prop="scopes">/,
  '新建应用弹窗应使用中文字段名'
)
assert.match(
  detailPanelSource,
  /<span class="policy-label">权限范围<\/span>/,
  '应用详情摘要应使用中文字段名'
)
assert.match(
  detailPanelSource,
  /<el-form-item label="权限范围" required>/,
  '访问策略弹窗应使用中文字段名'
)
for (const source of [createPageSource, detailPanelSource]) {
  assert.match(source, /:label="scope\.label"/, 'Scope 下拉应使用中文标签')
  assert.match(source, /:value="scope\.value"/, 'Scope 下拉应继续提交技术值')
}
assert.match(
  detailPanelSource,
  /\{\{ integrationScopeLabel\(scope\) \}\}/,
  '应用详情中已保存的 Scope 也应显示中文'
)

console.log('open integration scope localization tests passed')
