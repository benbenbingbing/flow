import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'

const root = process.cwd()
const read = file => readFileSync(path.join(root, file), 'utf8')
const formDesigner = read('src/views/EntityFormDesignByEntity.vue')
const listDesigner = read('src/views/EntityListConfigDesign.vue')
const panel = read('src/components/related-content/RelatedContentPanel.vue')
const dialog = read('src/components/related-content/RelatedContentConfigDialog.vue')
  + read('src/components/related-content/RelatedContentTargetStep.vue')
const api = read('src/api/uiComposition.js')
const runtime = read('src/components/related-content/RelatedContentRuntime.vue')
const runtimeApi = read('../packages/workflow-api/src/uiCompositionRuntime.js')
const formRuntime = read('src/components/FormPreviewLinkage.vue')
const nodeRuntime = read('src/components/FormNodeRuntimeItem.vue')
const listRuntime = read('src/views/entity/EntityDataList.vue')
const listSelectionState = read('src/views/entity/composables/useEntityDataSelectionState.js')
const listTable = read('src/views/entity/components/EntityDataTable.vue')
const formDialog = read('src/views/entity/components/EntityDataFormDialog.vue')
const publishDialog = read('src/components/UiConfigPublishDialog.vue')

;[formDesigner, listDesigner].forEach((source, index) => {
  assert.ok(source.includes('RelatedContentPanel'), `${index ? '列表' : '表单'}设计器缺少关联内容管理入口`)
  assert.ok(source.includes('relatedContentCount'), `${index ? '列表' : '表单'}设计器缺少关联内容数量徽标`)
  assert.ok(source.includes('handleRelatedContentChanged'), `${index ? '列表' : '表单'}设计器未刷新草稿差异`)
})

;[
  '显示什么',
  '数据来源',
  '匹配规则',
  '选择已定义的实体关系',
  '允许做什么',
  '特殊情况怎么处理',
  '使用一条真实数据测试',
  'sourceTestRecordId',
  '实际筛选条件',
  '目标记录示例',
  '当前配置可由平台默认能力完成，无需特殊处理。',
  'ConfigHelpLabel',
  'actionSettings',
  '数据或动作扩展接口',
  '自定义组件',
  '组件版本',
  'failurePolicy'
].forEach(marker => {
  assert.ok(dialog.includes(marker), `关系展示／扩展页面编辑器缺少：${marker}`)
})

;[
  '新增关联内容',
  '编辑',
  '删除',
  'describeRelatedContent',
  'describeRelatedContentRelation'
].forEach(marker => {
  assert.ok(panel.includes(marker), `关联内容管理面板缺少：${marker}`)
})

;['/ui-view-compositions/${ownerType}/${ownerId}', '/validate', '/test'].forEach(marker => {
  assert.ok(api.includes(marker), `关联内容 API 契约缺少：${marker}`)
})
assert.doesNotMatch(api, /request\.(?:put|patch|delete)\s*\(/, '关联内容 API 只能使用 GET 或 POST')
assert.doesNotMatch(dialog, /<textarea|type="textarea"|v-model="[^\"]*(?:script|sql|expression)/i, '关联内容配置不得暴露自由脚本或自由文本技术配置')

assert.ok(runtimeApi.includes('/ui-runtime/view-compositions/resolve'), '缺少关联内容可信运行时解析 API')
assert.ok(runtimeApi.includes('/ui-runtime/view-compositions/actions/capabilities'), '缺少关联内容权威动作能力 API')
assert.ok(runtimeApi.includes('/ui-runtime/view-compositions/actions'), '缺少关联内容权威动作执行 API')
;[
  'compositionContext',
  'targetContentHash',
  'failurePolicy',
  '未找到关联数据',
  '请先保存当前记录'
].forEach(marker => {
  assert.ok(runtime.includes(marker), `关联内容运行时缺少安全语义：${marker}`)
})
assert.ok(formRuntime.includes('ownerRelatedContents'), '表单运行时未渲染宿主级关联内容')
assert.ok(formRuntime.includes('runtimeCompositionContext'), '表单嵌套关联内容未透传安全导航链')
assert.ok(formRuntime.includes(':config-hash="runtimeConfigHash"'), '表单关联内容未透传配置摘要')
assert.ok(nodeRuntime.includes('relatedContentsForNode'), '表单节点未渲染节点级关联内容')
assert.ok(nodeRuntime.includes('runtimeCompositionContext'), '表单节点关联内容未透传安全导航链')
assert.ok(nodeRuntime.includes(':config-hash="runtimeConfigHash"'), '表单节点关联内容未透传配置摘要')
assert.ok(listRuntime.includes('rowExpandCompositions'), '列表运行时未接入行展开关联内容')
assert.ok(listRuntime.includes('compositionContext'), '列表查询未携带可信关联上下文')
assert.ok(listRuntime.includes('compositionContext'), '列表嵌套关联内容未透传安全导航链')
assert.ok(listRuntime.includes('runtimeSelectionMode'), '列表页级关联内容未启用单选主从联动')
assert.ok(listRuntime.includes('selectedRows.value[0]?.id'), '列表当前选择未成为关联内容来源记录')
assert.ok(listTable.includes('openRelatedContent(btn, row)'), '列表行按钮未接入关联内容')
assert.ok(listTable.includes('compositionContext'), '列表行级关联内容未透传安全导航链')
assert.ok(runtime.includes('createLatestRequestGate'), '来源切换时未防止旧解析请求覆盖新上下文')
assert.ok(runtime.includes('buildRelatedContentResolveInput'), '关联内容解析未使用统一的配置摘要请求契约')
assert.ok(runtime.includes('props.configHash'), '关联内容解析请求未携带宿主配置摘要')
assert.ok(runtime.includes('assertRelatedContentResolveContract'), '关联列表缺少来源上下文时未失败关闭')
assert.ok(runtime.includes('relatedContentActions'), '关联动作未传递给目标列表做精确限制')
assert.ok(runtime.includes('compositionContext'), '关联动作未使用业务来源上下文')
assert.ok(runtime.includes('initialValues'), '关联新增未使用服务端解析的可信初值')
assert.ok(formDialog.includes('compositionContext'), '目标表单提交未携带关联内容动作上下文')
assert.ok(runtime.includes('function renderFailureState'), '运行时失败策略没有统一处理入口')
assert.ok(runtime.includes("failurePolicy.value === 'HIDE'"), '自定义组件缺失时不支持隐藏内容')
assert.ok(runtime.includes("failurePolicy.value === 'PLACEHOLDER'"), '自定义组件缺失时不支持显示占位')
assert.ok(runtime.includes("renderFailureState(\n          '自定义组件不可用'"), '自定义组件缺失没有遵循显式失败策略')
assert.ok(runtime.includes('function handleActionFailure'), '动作接口失败没有统一应用已发布失败策略')
assert.ok(runtime.includes("actionFailure.value?.policy !== 'HIDE'"), '动作接口失败选择隐藏时仍会显示关联内容')
assert.ok(runtime.includes("actionFailure.value?.policy === 'PLACEHOLDER'"), '动作接口失败选择占位时未显示占位状态')
assert.ok(runtime.includes('throw error'), '隐藏或占位策略不能把动作失败伪装成成功')
assert.ok(listRuntime.includes('filterRelatedContentButtons'), '目标列表按钮未按关联动作逐项过滤')
assert.ok(listSelectionState.includes('explicitSelectionScene'), '列表选择器未识别显式选择模式')
assert.doesNotMatch(listSelectionState, /runtimeScene\.value[^\n]*EMBEDDED/, '不能把所有内嵌列表全局变成选择场景')
assert.ok(publishDialog.includes('关联内容与发布依赖'), '发布前未展示关联内容依赖')
assert.ok(publishDialog.includes(':data="preview.dependencies"'), '折叠依赖区域必须保留真实预检依赖明细')
assert.ok(publishDialog.includes("ENTITY_SCHEMA: '实体定义'"), '发布前未解释实体定义依赖')
assert.ok(publishDialog.includes('dependencyVersionLabel'), '发布依赖缺少业务化版本说明')

assert.ok(dialog.includes('需要随主表单一起保存？'), '设计器未保留组成型数据的主从编辑引导')
assert.ok(formRuntime.includes('@patch-form="applyFieldPatch"'), '实体引用字段的回填仍需经过表单联动')

console.log('related content page config tests passed')
