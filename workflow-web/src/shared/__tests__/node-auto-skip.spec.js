import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import BpmnModdle from 'bpmn-moddle'
import {
  AUTO_SKIP_MODE,
  AUTO_SKIP_OPERATOR_OPTIONS,
  createAutoSkipForm,
  isAlwaysSkipExpression,
  normalizeSkipExpression,
  resolveAutoSkipConditionRoot,
  serializeAutoSkipForm,
  transitionAutoSkipMode,
  validateAutoSkipExpression
} from '../node-auto-skip.js'

assert.deepEqual(createAutoSkipForm(undefined, undefined), {
  skipMode: AUTO_SKIP_MODE.OFF,
  skipExpression: ''
})
assert.deepEqual(createAutoSkipForm('true', '${ignored}'), {
  skipMode: AUTO_SKIP_MODE.ALWAYS,
  skipExpression: ''
}, '始终跳过不得把冲突残留表达式带入编辑态')
assert.deepEqual(createAutoSkipForm(undefined, '${true}'), {
  skipMode: AUTO_SKIP_MODE.ALWAYS,
  skipExpression: ''
}, '发布端恒真表达式应回显为始终跳过')
assert.deepEqual(createAutoSkipForm(undefined, '${ skipNodeEnabled }'), {
  skipMode: AUTO_SKIP_MODE.ALWAYS,
  skipExpression: ''
}, '旧版全局跳过开关应回显为始终跳过')
assert.equal(isAlwaysSkipExpression('#{TRUE}'), true)
assert.equal(isAlwaysSkipExpression('${skipFinance}'), false)
assert.deepEqual(createAutoSkipForm('false', '${skipFinance}'), {
  skipMode: AUTO_SKIP_MODE.CONDITIONAL,
  skipExpression: '${skipFinance}'
})
assert.equal(
  createAutoSkipForm('false', '${amount >= 100}').skipExpression,
  '${amount >= 100}',
  '加载条件组前必须保留原数值表达式，不能在字段类型异步加载前重建'
)
const expressionRoot = { type: 'GROUP', source: 'expression' }
const staleMetadataRoot = { type: 'GROUP', source: 'metadata' }
assert.equal(
  resolveAutoSkipConditionRoot('${amount >= 100}', expressionRoot, staleMetadataRoot),
  expressionRoot,
  'XML 表达式与分组元数据冲突时必须使用表达式解析结果'
)
assert.equal(
  resolveAutoSkipConditionRoot('${legacyCustomCheck}', null, staleMetadataRoot),
  null,
  'XML 原表达式无法解析时也不能回退到陈旧元数据'
)
assert.equal(resolveAutoSkipConditionRoot('', null, staleMetadataRoot), staleMetadataRoot)

assert.equal(
  normalizeSkipExpression({ body: '  ${legacySkip}  ' }),
  '${legacySkip}',
  '历史 FormalExpression 对象应兼容回显'
)
assert.equal(
  normalizeSkipExpression({ get: key => key === 'body' ? '${getterBody}' : undefined }),
  '${getterBody}',
  'bpmn-moddle getter 对象应兼容回显'
)

assert.deepEqual(serializeAutoSkipForm({ skipMode: AUTO_SKIP_MODE.OFF }), {
  skipNode: null,
  skipExpression: null
})
assert.deepEqual(serializeAutoSkipForm({
  skipMode: AUTO_SKIP_MODE.ALWAYS,
  skipExpression: '${stale}'
}), {
  skipNode: 'true',
  skipExpression: null
})
assert.deepEqual(serializeAutoSkipForm({
  skipMode: AUTO_SKIP_MODE.CONDITIONAL,
  skipExpression: '  ${skipFinance == true}  '
}), {
  skipNode: 'false',
  skipExpression: '${skipFinance == true}'
})

const alwaysWithHistoricalConflict = createAutoSkipForm(
  'true',
  '${hiddenLegacyCondition == true}'
)
const switchedToConditional = transitionAutoSkipMode(
  alwaysWithHistoricalConflict,
  AUTO_SKIP_MODE.CONDITIONAL
)
assert.deepEqual(switchedToConditional, {
  skipMode: AUTO_SKIP_MODE.CONDITIONAL,
  skipExpression: ''
}, '从始终跳过切换为条件跳过时不得复活隐藏的旧表达式')
assert.deepEqual(
  transitionAutoSkipMode(
    { skipMode: AUTO_SKIP_MODE.CONDITIONAL, skipExpression: '${amount > 0}' },
    AUTO_SKIP_MODE.CONDITIONAL
  ),
  { skipMode: AUTO_SKIP_MODE.CONDITIONAL, skipExpression: '${amount > 0}' },
  '同模式更新不应清理当前条件'
)

assert.deepEqual(
  AUTO_SKIP_OPERATOR_OPTIONS.map(option => option.value),
  ['==', '!=', '>', '<', '>=', '<='],
  '自动跳过构建器不能提供会生成方法调用的操作符'
)
assert.equal(validateAutoSkipExpression('${amount >= 100 && urgent == true}').valid, true)
assert.equal(validateAutoSkipExpression("${remark == 'a.b'}").valid, true)
assert.equal(validateAutoSkipExpression('${customer.level == 2}').valid, false)
assert.equal(validateAutoSkipExpression("${remark.contains('vip')}").valid, false)
assert.equal(validateAutoSkipExpression('${items[0] == 1}').valid, false)
assert.equal(validateAutoSkipExpression('${approved = true}').valid, false)
assert.equal(validateAutoSkipExpression('${approved === true}').valid, false)
assert.equal(validateAutoSkipExpression('${(value->value)(true)}').valid, false)
assert.equal(validateAutoSkipExpression('${(condition)(true)}').valid, false)
assert.equal(validateAutoSkipExpression('${}').valid, false)

const flowableDescriptor = JSON.parse(readFileSync(new URL(
  '../../assets/flowable.json',
  import.meta.url
), 'utf8'))
const moddle = new BpmnModdle({ flowable: flowableDescriptor })
const definitions = moddle.create('bpmn:Definitions', { targetNamespace: 'test' })
const process = moddle.create('bpmn:Process', { id: 'Process_AutoSkip' })
const userTask = moddle.create('bpmn:UserTask', {
  id: 'Task_Conditional',
  skipExpression: '${amount >= 100}'
})
process.flowElements = [userTask]
definitions.rootElements = [process]
const { xml } = await moddle.toXML(definitions)
assert.match(
  xml,
  /flowable:skipExpression="\$\{amount (?:&#62;|&gt;|>)= 100\}"/,
  'skipExpression 应作为 Flowable String attr 序列化'
)
assert.doesNotMatch(xml, /\[object Object\]/)

const panelSource = readFileSync(new URL(
  '../../components/NodeConfigPanel.vue',
  import.meta.url
), 'utf8')
assert.doesNotMatch(panelSource, /advancedForm\.(?:async|asyncBefore|asyncAfter)\b/)
assert.doesNotMatch(panelSource, /function (?:onAsyncChange|updateAsync)\b/)
assert.doesNotMatch(panelSource, /bpmn:FormalExpression[\s\S]{0,160}skipExpression/)
assert.match(
  panelSource,
  /<SettingsSection\s+v-if="isUserTask"[\s\S]*?title="自动跳过"/,
  '自动跳过只能对受支持的用户任务显示'
)
assert.match(panelSource, /:group="skipConditionRoot"/)
assert.match(panelSource, /:include-approval-property="false"/)
assert.match(panelSource, /:operator-options="AUTO_SKIP_OPERATOR_OPTIONS"/)
assert.match(panelSource, /@update:model-value="onAutoSkipModeChange"/)
assert.match(panelSource, /transitionAutoSkipMode\(/)
assert.match(panelSource, /'skipConditionGroupConfig'/)
assert.doesNotMatch(
  panelSource,
  /v-model="advancedForm\.skipExpression"/,
  '条件表达式必须由受控条件组生成，不能保留任意原始输入框'
)
assert.doesNotMatch(
  panelSource,
  /savedRoot\s*\?\s*buildFlowConditionExpression/,
  '加载已保存条件组时不得提前重建并覆盖原表达式'
)
assert.match(
  panelSource,
  /resolveAutoSkipConditionRoot\(/,
  '跳过表达式与条件元数据冲突时必须以运行时表达式为准'
)

const mockE2eSource = readFileSync(new URL(
  '../../../scripts/e2e-mock-pages.mjs',
  import.meta.url
), 'utf8')
assert.match(mockE2eSource, /advancedTab = \{ label: '高级', marker: '标识与备注' \}/)
assert.doesNotMatch(mockE2eSource, /marker: '异步执行'/)

console.log('node auto skip contract passed')
