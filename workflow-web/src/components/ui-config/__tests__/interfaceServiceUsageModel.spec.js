import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'

import {
  buildEffectiveChainItems,
  buildInterfaceServiceReferenceRoute,
  effectiveChainUnavailableReason,
  effectiveStateLabel,
  effectiveStateTagType,
  extractInterfaceServiceReferences,
  filterInterfaceServiceReferenceGroups,
  formatEffectiveContexts,
  groupInterfaceServiceReferences,
  inheritanceSourceLabel,
  interfaceServiceReferenceLifecycleOptions,
  isEffectiveChainAvailable,
  normalizeInterfaceServiceReference,
  omitInterfaceServiceReferenceCache
} from '../interfaceServiceUsageModel.js'

const services = [
  { id: 11, sourceName: '客户查询', sourceCode: 'CUSTOMER_QUERY' },
  { id: 12, sourceName: '未使用服务', sourceCode: 'UNUSED_SERVICE' }
]
const referencesByService = {
  11: [{
    bindingId: 101,
    serviceId: 11,
    operationCode: 'getDetail',
    operationName: '获取客户详情',
    ownerType: 'form',
    ownerId: 21,
    ownerName: '客户表单',
    entityId: 31,
    targetType: 'field',
    targetKey: 'customerId',
    eventCode: 'entity_selected',
    lifecycleStatus: 'published_changed',
    stepIndex: 2
  }]
}

test('规范化每个服务的事件步骤引用', () => {
  const groups = groupInterfaceServiceReferences(services, referencesByService)
  assert.equal(groups.length, 2)
  assert.equal(groups[0].references[0].ownerType, 'FORM')
  assert.equal(groups[0].references[0].targetType, 'FIELD')
  assert.equal(groups[0].references[0].referenceId, '101:2')
  assert.equal(groups[1].referenceCount, 0)
  assert.deepEqual(extractInterfaceServiceReferences({ references: [{ id: 1 }] }), [{ id: 1 }])
  const aliasedReference = normalizeInterfaceServiceReference({
    strategy: 'after',
    publicationStatus: 'published_match',
    effectiveStatus: 'effective',
    contexts: [{ configType: 'FORM', configName: '客户表单', releaseVersion: 3 }]
  })
  assert.equal(aliasedReference.stepStrategy, 'AFTER')
  assert.equal(aliasedReference.lifecycleStatus, 'PUBLISHED_MATCH')
  assert.equal(aliasedReference.effectiveStatus, 'EFFECTIVE')
  assert.equal(formatEffectiveContexts(aliasedReference.contexts), '表单覆盖·客户表单·v3')
  assert.equal(formatEffectiveContexts([
    { ownerType: 'FORM', ownerName: '客户表单' },
    '列表默认链'
  ]), '表单覆盖·客户表单、列表默认链')
  assert.ok(interfaceServiceReferenceLifecycleOptions.some(option =>
    option.value === 'PUBLISHED_ONLY' && option.label === '仅发布'))
  assert.deepEqual(omitInterfaceServiceReferenceCache({
    11: referencesByService[11],
    12: []
  }, 11), { 12: [] })
})

test('可按服务、引用内容、层级和发布状态筛选', () => {
  const groups = groupInterfaceServiceReferences(services, referencesByService)
  assert.equal(filterInterfaceServiceReferenceGroups(groups, {
    keyword: '客户',
    referenceState: 'REFERENCED'
  }).length, 1)
  assert.equal(filterInterfaceServiceReferenceGroups(groups, {
    serviceId: '11',
    keyword: 'getDetail',
    ownerType: 'FORM',
    lifecycleStatus: 'PUBLISHED_CHANGED',
    referenceState: 'REFERENCED'
  }).length, 1)
  assert.deepEqual(filterInterfaceServiceReferenceGroups(groups, {
    referenceState: 'UNUSED'
  }).map(group => group.serviceId), ['12'])

  const publishedOnlyGroups = groupInterfaceServiceReferences([services[0]], {
    11: [{ ...referencesByService[11][0], lifecycleStatus: 'PUBLISHED_ONLY' }]
  })
  assert.equal(filterInterfaceServiceReferenceGroups(publishedOnlyGroups, {
    lifecycleStatus: 'PUBLISHED_ONLY',
    referenceState: 'REFERENCED'
  }).length, 1)
})

test('表单引用深链直达表单事件设置', () => {
  const reference = normalizeInterfaceServiceReference(referencesByService[11][0])
  assert.deepEqual(buildInterfaceServiceReferenceRoute(reference), {
    name: 'EntityFormDesign',
    params: { id: '21' },
    query: {
      entityId: '31',
      settings: 'data-events',
      section: 'events',
      bindingId: '101',
      eventCode: 'ENTITY_SELECTED',
      targetType: 'FIELD',
      targetKey: 'customerId'
    }
  })
})

test('最终生效链按执行位置排列并解释继承来源', () => {
  const inheritedChain = buildEffectiveChainItems({
    activeReleasePresent: true,
    publicationStatus: 'PUBLISHED_MATCH',
    effectiveChain: [
      { stepName: '回填', stepStrategy: 'AFTER', stepOrder: 30, ownerType: 'FORM' },
      { stepName: '校验', stepStrategy: 'BEFORE', stepOrder: 10, inheritanceSource: 'ENTITY_DEFAULT' }
    ]
  })
  assert.deepEqual(inheritedChain.map(item => item.label), [
    '校验', '平台默认处理', '回填'
  ])
  assert.equal(inheritedChain[0].sourceLabel, '实体默认')
  assert.equal(inheritanceSourceLabel('FORM_OVERRIDE'), '表单覆盖')

  const replacedChain = buildEffectiveChainItems({
    activeReleasePresent: true,
    publicationStatus: 'PUBLISHED_CHANGED',
    effectiveChain: [{ stepName: '完全接管', stepStrategy: 'REPLACE' }]
  })
  assert.deepEqual(replacedChain.map(item => item.label), ['完全接管'])
  assert.equal(effectiveStateLabel({ bindingEnabled: true, effectiveStatus: 'ACTIVE' }), '已进入最终有效链')
  assert.equal(effectiveStateLabel({ bindingEnabled: true, effectiveStatus: 'INVALID_CHAIN' }), '最终有效链无效')
  assert.equal(effectiveStateTagType({ bindingEnabled: true, effectiveStatus: 'INVALID_CHAIN' }), 'danger')
  assert.equal(
    effectiveStateLabel({ bindingEnabled: false, effectiveStatus: 'ACTIVE' }),
    '已进入最终有效链（草稿绑定已停用）'
  )
  assert.equal(
    effectiveStateTagType({ bindingEnabled: false, effectiveStatus: 'ACTIVE' }),
    'success'
  )

  const unavailableContext = {
    activeReleasePresent: false,
    publicationStatus: 'NOT_PUBLISHED',
    effectiveChain: []
  }
  assert.equal(isEffectiveChainAvailable(unavailableContext), false)
  assert.deepEqual(buildEffectiveChainItems(unavailableContext), [])
  assert.match(effectiveChainUnavailableReason(unavailableContext), /尚无可用的已发布执行链/)
  assert.equal(isEffectiveChainAvailable({
    activeReleasePresent: true,
    publicationStatus: 'PUBLISH_STATE_UNAVAILABLE'
  }), false)
})

test('列表和实体引用深链保留定位参数', () => {
  assert.equal(buildInterfaceServiceReferenceRoute({
    ownerType: 'LIST', ownerId: 41, bindingId: 201, eventCode: 'LIST_LOAD'
  }).query.events, '1')
  assert.equal(buildInterfaceServiceReferenceRoute({
    ownerType: 'ENTITY', ownerId: 51, eventCode: 'FORM_OPEN'
  }).query.tab, 'events')
  assert.equal(buildInterfaceServiceReferenceRoute({ ownerType: 'UNKNOWN' }), null)
})

test('接口服务页只读展示使用情况，不再嵌入事件编辑器', () => {
  const pageSource = readFileSync(
    new URL('../../../views/system/InterfaceServices.vue', import.meta.url),
    'utf8'
  )
  const apiSource = readFileSync(
    new URL('../../../api/uiConfig.js', import.meta.url),
    'utf8'
  )
  const panelSource = readFileSync(
    new URL('../InterfaceServiceUsagePanel.vue', import.meta.url),
    'utf8'
  )
  const editorSource = readFileSync(
    new URL('../InterfaceServiceEditorDialog.vue', import.meta.url),
    'utf8'
  )
  const manualSource = readFileSync(
    new URL('../../../data/user-manual/interfaceService.js', import.meta.url),
    'utf8'
  )
  const formDesignerSource = readFileSync(
    new URL('../../../views/EntityFormDesignByEntity.vue', import.meta.url),
    'utf8'
  )
  const listDesignerSource = readFileSync(
    new URL('../../../views/EntityListConfigDesign.vue', import.meta.url),
    'utf8'
  )
  const entityDesignerSource = readFileSync(
    new URL('../../../views/EntityDesign.vue', import.meta.url),
    'utf8'
  )
  assert.match(pageSource, /InterfaceServiceUsagePanel/)
  assert.match(pageSource, /name="usage"\s+lazy/)
  assert.doesNotMatch(pageSource, /EventBindingEditor/)
  assert.doesNotMatch(pageSource, /EntityDefinitionPicker/)
  assert.match(pageSource, /canUpdateServices/)
  assert.match(pageSource, /canTestServices/)
  assert.match(pageSource, /canConfigureEntityEvents/)
  assert.match(pageSource, /仍可固定访问的历史版本/)
  assert.match(pageSource, /流程或 Embed 仍有可执行引用/)
  assert.match(pageSource, /仅解除并重新发布可能不足/)
  assert.match(pageSource, /冲突提示会给出具体版本/)
  assert.match(pageSource, /error\?\.message \|\| '删除接口服务失败'/)
  assert.match(apiSource, /`\/ui-data-sources\/\$\{id\}\/references`/)
  assert.match(panelSource, /watch\(\(\) => filters\.serviceId, load\)/)
  assert.match(panelSource, /const service = selectedService\.value\s+if \(!service\)/)
  assert.doesNotMatch(panelSource, /mapWithConcurrency|REFERENCE_LOAD_CONCURRENCY/)
  assert.match(panelSource, /selectedServiceFailed/)
  assert.match(panelSource, /无法判断该服务是否存在引用/)
  assert.match(panelSource, /omitInterfaceServiceReferenceCache/)
  assert.doesNotMatch(panelSource, /\[String\(service\.id\)\]: \[\]/)
  assert.match(panelSource, /具体事件仍需在设计器中选择/)
  assert.match(editorSource, /v-if="editor\.id"/)
  assert.match(editorSource, /修改已引用服务会即时影响运行链/)
  assert.match(editorSource, /请先在“事件使用情况”确认引用范围/)
  assert.match(manualSource, /新建接口服务不会自动绑定任何页面/)
  assert.match(manualSource, /修改后会即时影响运行/)
  assert.doesNotMatch(manualSource, /服务保存后不会自动改变任何页面/)

  // 三类路由都能落到事件入口；FIELD/BUTTON 目标由表单、列表设计器消费。
  assert.match(formDesignerSource, /route\.query\.settings/)
  assert.match(formDesignerSource, /route\.query\.targetType/)
  assert.match(formDesignerSource, /openField/)
  assert.match(formDesignerSource, /openButton/)
  assert.match(listDesignerSource, /route\.query\.events/)
  assert.match(listDesignerSource, /route\.query\.targetType/)
  assert.match(listDesignerSource, /openButton/)
  assert.match(entityDesignerSource, /normalizeEntityDesignTab\(route\.query\.tab\)/)
  assert.match(entityDesignerSource, /EntityDefaultEventPanel/)
})
