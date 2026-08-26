import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'

const root = process.cwd()
const read = file => readFileSync(path.join(root, file), 'utf8')
const formDesigner = read('src/views/EntityFormDesignByEntity.vue')
const listDesigner = read('src/views/EntityListConfigDesign.vue')
const panel = read('src/components/related-content/RelatedContentPanel.vue')
const dialog = read('src/components/related-content/RelatedContentConfigDialog.vue')
const api = read('src/api/uiComposition.js')
const runtime = read('src/components/related-content/RelatedContentRuntime.vue')
const runtimeApi = read('src/api/uiCompositionRuntime.js')
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
  '数据怎么关联',
  '允许做什么',
  '特殊情况怎么处理',
  '使用一条真实数据测试',
  'sourceTestRecordId',
  '实际筛选条件',
  '目标记录示例',
  '当前配置可由平台默认能力完成，无需特殊处理。',
  'ConfigHelpLabel',
  '选择记录后怎么处理',
  '选择结果回填',
  '新增后自动建立关联',
  'actionSettings',
  '接口服务',
  '自定义组件',
  '组件版本',
  'failurePolicy'
].forEach(marker => {
  assert.ok(dialog.includes(marker), `四步关联内容编辑器缺少：${marker}`)
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
assert.ok(runtimeApi.includes('/ui-runtime/view-compositions/actions/link-candidates'), '缺少建立关联专用候选列表 API')
assert.ok(runtimeApi.includes('/ui-runtime/view-compositions/actions'), '缺少关联内容权威动作执行 API')
;[
  'viewCompositionContextToken',
  'traversalContextToken',
  'targetReleaseResolutionToken',
  'failurePolicy',
  '未找到关联数据',
  '请先保存当前记录'
].forEach(marker => {
  assert.ok(runtime.includes(marker), `关联内容运行时缺少安全语义：${marker}`)
})
assert.ok(formRuntime.includes('ownerRelatedContents'), '表单运行时未渲染宿主级关联内容')
assert.ok(formRuntime.includes('runtimeTraversalContextToken'), '表单嵌套关联内容未透传安全导航链')
assert.ok(formRuntime.includes(':release-resolution-token="runtimeReleaseResolutionToken"'), '表单关联内容未透传历史钉定发布令牌')
assert.ok(nodeRuntime.includes('relatedContentsForNode'), '表单节点未渲染节点级关联内容')
assert.ok(nodeRuntime.includes('runtimeTraversalContextToken'), '表单节点关联内容未透传安全导航链')
assert.ok(nodeRuntime.includes(':release-resolution-token="runtimeReleaseResolutionToken"'), '表单节点关联内容未透传历史钉定发布令牌')
assert.ok(listRuntime.includes('rowExpandCompositions'), '列表运行时未接入行展开关联内容')
assert.ok(listRuntime.includes('viewCompositionContextToken'), '列表查询未携带可信关联上下文')
assert.ok(listRuntime.includes('viewCompositionTraversalToken'), '列表嵌套关联内容未透传安全导航链')
assert.ok(listRuntime.includes('runtimeSelectionMode'), '列表页级关联内容未启用单选主从联动')
assert.ok(listRuntime.includes('selectedRows.value[0]?.id'), '列表当前选择未成为关联内容来源记录')
assert.ok(listTable.includes('rowActionCompositions'), '列表行操作未接入关联内容')
assert.ok(listTable.includes('viewCompositionTraversalToken'), '列表行级关联内容未透传安全导航链')
assert.ok(runtime.includes('createLatestRequestGate'), '来源切换时未防止旧解析请求覆盖新上下文')
assert.ok(runtime.includes('buildRelatedContentResolveInput'), '关联内容解析未使用统一的发布令牌请求契约')
assert.ok(runtime.includes('props.releaseResolutionToken'), '关联内容解析请求未携带宿主发布令牌')
assert.ok(runtime.includes('assertRelatedContentResolveContract'), '关联列表缺少可信令牌时未失败关闭')
assert.ok(runtime.includes('relatedContentActions'), '关联动作未传递给目标列表做精确限制')
assert.ok(runtime.includes('actionContextToken'), '关联动作未使用服务端签名上下文')
assert.ok(runtime.includes('initialValues'), '关联新增未使用服务端解析的可信初值')
assert.ok(formDialog.includes('viewCompositionActionContextToken'), '目标表单提交未携带关联内容动作上下文')
assert.ok(dialog.includes('当前版本不支持新增并自动关联在同一事务完成'), '设计器未明确禁用非原子的新增后关联')
assert.ok(dialog.includes('已有组成型子表单或重复器'), '设计器未引导组成型数据使用现有能力')
assert.ok(runtime.includes('selectionActionOptions'), '目标列表未展示权威选择/关联动作')
assert.ok(runtime.includes('candidateListContextToken'), '建立关联没有使用服务端候选范围令牌')
assert.ok(runtime.includes('openLinkCandidates'), '目标列表缺少“选择并建立关联”入口')
assert.ok(runtime.includes('executeCandidateSelectionAction'), '候选记录未通过专用动作桥提交目标 ID')
assert.ok(runtime.includes('!props.hostReadonly'), '只读宿主仍可能展示字段回填动作')
assert.ok(formRuntime.includes(':host-readonly="readonly"'), '表单没有把只读状态传给关联内容')
assert.ok(nodeRuntime.includes(':host-readonly="readonly"'), '表单节点没有把只读状态传给关联内容')
assert.ok(runtime.includes('当前已关联列表'), '候选范围与当前已关联范围缺少用途隔离说明')
assert.ok(runtime.includes('function renderFailureState'), '运行时失败策略没有统一处理入口')
assert.ok(runtime.includes("failurePolicy.value === 'HIDE'"), '自定义组件缺失时不支持隐藏内容')
assert.ok(runtime.includes("failurePolicy.value === 'PLACEHOLDER'"), '自定义组件缺失时不支持显示占位')
assert.ok(runtime.includes("renderFailureState(\n          '自定义组件不可用'"), '自定义组件缺失没有遵循显式失败策略')
assert.ok(runtime.includes('function handleActionFailure'), '动作接口失败没有统一应用已发布失败策略')
assert.ok(runtime.includes("actionFailure.value?.policy !== 'HIDE'"), '动作接口失败选择隐藏时仍会显示关联内容')
assert.ok(runtime.includes("actionFailure.value?.policy === 'PLACEHOLDER'"), '动作接口失败选择占位时未显示占位状态')
assert.ok(runtime.includes('throw error'), '隐藏或占位策略不能把动作失败伪装成成功')
assert.ok(runtime.includes("emit('source-patch'"), '选择结果没有向宿主表单发送服务端字段补丁')
assert.ok(formRuntime.includes('@source-patch="applyRelatedContentPatch"'), '宿主表单未接收关联选择回填')
assert.ok(nodeRuntime.includes('@source-patch="applyRelatedContentPatch"'), '表单节点未接收关联选择回填')
assert.ok(listRuntime.includes('filterRelatedContentButtons'), '目标列表按钮未按关联动作逐项过滤')
assert.ok(listRuntime.includes("'selection-action'"), '目标列表未把选择动作交给权威动作桥')
assert.ok(listSelectionState.includes('explicitSelectionScene'), '内嵌关联列表未识别显式选择动作')
assert.ok(listSelectionState.includes('props.selectionActionOptions.length > 0'), '选择动作不能驱动内嵌列表操作区')
assert.doesNotMatch(listSelectionState, /runtimeScene\.value[^\n]*EMBEDDED/, '不能把所有内嵌列表全局变成选择场景')
assert.ok(publishDialog.includes('关联内容发布依赖'), '发布前未展示关联内容依赖')
assert.ok(publishDialog.includes("ENTITY_SCHEMA: '实体定义'"), '发布前未解释实体定义依赖')
assert.ok(publishDialog.includes('dependencyVersionLabel'), '发布依赖缺少业务化版本说明')

console.log('related content page config tests passed')
