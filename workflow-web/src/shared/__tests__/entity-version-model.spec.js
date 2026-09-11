import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import {
  createVersionDraft,
  frozenValueText,
  normalizeComparison,
  normalizePage,
  normalizeSnapshot,
  serializeVersionDraft
} from '../entity-version-model.js'
import {
  canCaptureEntityRecordVersion,
  canShowEntityVersionAction,
  normalizeEntityVersionCapabilities
} from '../entity-version-capabilities.js'
import {
  loadEntityVersionConfigPageWithLegacyFallback,
  paginateLegacyEntityVersionConfigs
} from '../entity-version-config-list.js'

const legacy = createVersionDraft({
  entityCode: 'ORDER',
  entityName: '订单',
  status: 'PUBLISHED',
  migrationState: 'LEGACY',
  activeReleaseId: 'release-1',
  activeReleaseVersion: 3,
  scenarios: [{
    scenarioCode: 'APPROVED',
    scenarioName: '审批通过',
    sourceTypes: ['APPROVAL_TASK'],
    operationTypes: ['UPDATE']
  }]
})
assert.equal(legacy.triggers[0].triggerCode, 'APPROVED')
assert.equal(legacy.triggers[0].triggerType, 'ROOT_MUTATION')
assert.equal(legacy.snapshotScope.limits.maxRowsPerRelation, 500)
const serialized = serializeVersionDraft(legacy)
assert.equal(serialized.triggers[0].triggerName, '审批通过')
assert.equal('scenarios' in serialized, false)
assert.equal('status' in serialized, false)
assert.equal('migrationState' in serialized, false)
assert.equal('activeReleaseId' in serialized, false)
assert.equal('activeReleaseVersion' in serialized, false)

const bounded = serializeVersionDraft(createVersionDraft({
  snapshotScope: {
    limits: {
      maxRowsPerRelation: 5000,
      maxRowsPerVersion: 20000,
      maxBytesPerVersion: 100 * 1024 * 1024
    },
    relations: [{
      relationCode: 'LINES',
      maxRows: 5000,
      filter: {
        logic: 'ALL',
        conditions: [
          { fieldCode: 'amount', operator: 'GE', value: 10 },
          { fieldCode: 'memo', operator: 'IS_NOT_NULL' }
        ]
      }
    }]
  }
}))
assert.equal(bounded.snapshotScope.limits.maxRowsPerRelation, 500)
assert.equal(bounded.snapshotScope.limits.maxRowsPerVersion, 2000)
assert.equal(bounded.snapshotScope.limits.maxBytesPerVersion, 5 * 1024 * 1024)
assert.equal(bounded.snapshotScope.relations[0].maxRows, 500)
assert.deepEqual(
  bounded.snapshotScope.relations[0].filter.conditions.map(item => item.operator),
  ['GTE', 'NOT_EMPTY']
)

const v1Comparison = normalizeComparison({
  groups: [{
    code: 'BUSINESS',
    name: '业务字段',
    fields: [{
      fieldCode: 'name',
      fieldName: '名称',
      oldValue: '旧名称',
      newValue: '新名称',
      changeType: 'MODIFIED'
    }]
  }]
})
assert.equal(v1Comparison.compatibilityMode, 'LEGACY')
assert.equal(v1Comparison.nodes[0].formSections[0].fields[0].label, '名称')
assert.equal(v1Comparison.summary.dataChangedCount, 1)

const v2Comparison = normalizeComparison({
  diffPolicy: {
    changedOnlyDefault: false,
    trackOrder: true,
    ignoredFieldCodes: ['updateTime']
  },
  nodes: [{
    relationCode: 'LINES',
    oldRelationName: '旧明细',
    newRelationName: '订单明细',
    formSections: [{
      fields: [{
        fieldCode: 'productName',
        oldFieldName: '产品',
        newFieldName: '商品名称',
        oldValue: { rawValue: 'A', displayText: '服务器 A' },
        newValue: { rawValue: 'B', displayText: '服务器 B' },
        changeType: 'MODIFIED'
      }]
    }]
  }]
})
const renamed = v2Comparison.nodes[0].formSections[0].fields[0]
assert.equal(renamed.label, '商品名称（原：产品）')
assert.equal(frozenValueText(renamed.oldValue), '服务器 A')
assert.equal(v2Comparison.diffPolicy.changedOnlyDefault, false)
assert.deepEqual(v2Comparison.diffPolicy.ignoredFieldCodes, ['updateTime'])

const movedAndModified = normalizeComparison({
  nodes: [{
    relationCode: 'LINES',
    rowChanges: [{
      recordId: 'line-1',
      changeType: 'MODIFIED',
      moved: true,
      oldOrder: 0,
      newOrder: 1
    }]
  }]
}).nodes[0].rowChanges[0]
assert.equal(movedAndModified.changeType, 'MODIFIED')
assert.equal(movedAndModified.moved, true)
const movedCounts = normalizeComparison({
  nodes: [{
    relationCode: 'LINES',
    rowChanges: [{ recordId: 'line-1', changeType: 'MODIFIED', moved: true }]
  }]
}).nodes[0].counts
assert.equal(movedCounts.modified, 1)
assert.equal(movedCounts.moved, 1)

assert.equal(frozenValueText([{ label: '张三' }, { label: '李四' }]), '张三、李四')
assert.equal(frozenValueText({ arbitrary: true }), '结构化数据')
assert.deepEqual(normalizePage({ records: [1], total: 5, pageNum: 2 }), {
  records: [1], total: 5, pageNum: 2, pageSize: 20, counts: undefined
})

const legacyConfigRows = [
  { entityId: '1', entityCode: 'ORDER', entityName: '订单', enabled: true },
  { entityId: '2', entityCode: 'CONTRACT', entityName: '合同', enabled: false },
  {
    entityId: '3', entityCode: 'ASSET', entityName: '资产', revision: 7,
    enabled: true, runtimeEnabled: false
  },
  { entityId: '4', entityCode: 'CUSTOMER', entityName: '客户', enabled: true }
]
assert.deepEqual(
  paginateLegacyEntityVersionConfigs(legacyConfigRows, {
    keyword: 'con', enabled: false, pageNum: 1, pageSize: 20
  }).records,
  [legacyConfigRows[1]],
  '旧数组回退应按实体名称或编码忽略大小写筛选'
)
assert.deepEqual(
  paginateLegacyEntityVersionConfigs(legacyConfigRows, {
    enabled: false, pageNum: 2, pageSize: 1
  }),
  {
    records: [legacyConfigRows[2]],
    total: 2,
    pageNum: 2,
    pageSize: 1
  },
  '旧数组回退的未启用筛选应包含已停用和未配置，并返回真实分页总数'
)
assert.equal(
  paginateLegacyEntityVersionConfigs(legacyConfigRows, {
    pageNum: 0, pageSize: 500
  }).pageSize,
  100,
  '旧数组回退应与服务端一致地归一页码并限制每页最多 100 条'
)
let legacyListCalls = 0
for (const routeMiss of [
  { status: 404, message: 'not found' },
  { status: 405, message: 'method not allowed' },
  { status: 400, message: '实体不存在: page' }
]) {
  const fallbackPage = await loadEntityVersionConfigPageWithLegacyFallback({
    loadPage: async () => { throw Object.assign(new Error(routeMiss.message), routeMiss) },
    loadLegacy: async () => { legacyListCalls += 1; return legacyConfigRows }
  }, { enabled: true, pageNum: 1, pageSize: 1 })
  assert.deepEqual(fallbackPage.records, [legacyConfigRows[0]])
}
assert.equal(legacyListCalls, 3)
for (const realError of [
  { status: 400, message: '分页参数错误' },
  { status: 500, message: 'server error' }
]) {
  await assert.rejects(
    loadEntityVersionConfigPageWithLegacyFallback({
      loadPage: async () => { throw Object.assign(new Error(realError.message), realError) },
      loadLegacy: async () => { legacyListCalls += 1; return legacyConfigRows }
    }),
    new RegExp(realError.message),
    '非路由缺失错误必须原样抛出，不能误读旧列表'
  )
}
assert.equal(legacyListCalls, 3, '非兼容错误不应调用旧列表接口')

const v2Detail = normalizeSnapshot({
  snapshot: {
    entity: { entityCode: 'ORDER', entityName: '订单' },
    presentation: {
      sections: [{
        sectionCode: 'BASIC',
        sectionName: '基本信息',
        fields: [{ fieldCode: 'customerId', fieldName: '客户' }]
      }]
    },
    values: {
      customerId: { rawValue: 'C1', displayText: '华东客户' }
    }
  },
  datasets: [{
    nodeCode: 'REL_LINES',
    relationCode: 'LINES',
    relationName: '订单明细',
    rowCount: 35,
    presentation: { sections: [] },
  }]
})
assert.equal(v2Detail.nodes.length, 2)
assert.equal(v2Detail.nodes[0].nodeKind, 'ROOT')
assert.equal(v2Detail.nodes[0].name, '订单')
assert.equal(v2Detail.nodes[1].nodeCode, 'REL_LINES')
assert.equal(v2Detail.nodes[1].rowPage.total, 35)
assert.equal(
  frozenValueText(v2Detail.nodes[0].formSections[0].fields[0].value),
  '华东客户'
)

const drawerSource = readFileSync(
  fileURLToPath(new URL('../../views/entity/components/EntityRecordVersionDrawer.vue', import.meta.url)),
  'utf8'
)
const versionApiSource = readFileSync(
  fileURLToPath(new URL('../../api/entityVersion.js', import.meta.url)),
  'utf8'
)
const managementSource = readFileSync(
  fileURLToPath(new URL('../../views/system/EntityVersionManagement.vue', import.meta.url)),
  'utf8'
)
const entityDataListSource = readFileSync(
  fileURLToPath(new URL('../../views/entity/EntityDataList.vue', import.meta.url)),
  'utf8'
)
assert.ok(drawerSource.includes('loadSnapshotRelationPage(node, 1, true)'))
assert.ok(drawerSource.includes('changedOnly: changedOnly.value'))
assert.ok(drawerSource.includes('comparison.value?.diffPolicy?.changedOnlyDefault !== false'))
assert.match(
  drawerSource,
  /watch\(changedOnly,[\s\S]{0,500}loadRelationPage\(node, 1, true\)/,
  '切换仅看变化时必须重新读取关系行首屏'
)
assert.ok(versionApiSource.includes("...(String(recordId || '').trim()"))
assert.match(
  versionApiSource,
  /recordCapabilities\(entityCode\)[\s\S]{0,180}`\/entity-versions\/records\/\$\{entityCode\}\/capabilities`/,
  '实体版本 API 应提供实体级运行能力查询'
)
const configReadMethod = versionApiSource.match(
  /getConfig\(entityCode\) \{[\s\S]*?\n\s*},/
)?.[0] || ''
assert.ok(
  configReadMethod.includes('`/entity-versions/configs/${entityCode}/current`'),
  '混部读取必须使用只返回当前生效配置的 /current 路径'
)
assert.equal(
  configReadMethod.includes('LEGACY_SAVE_FALLBACK_STATUSES') || configReadMethod.includes('catch'),
  false,
  '当前配置读取失败不得回退旧 root draft 路径'
)
assert.deepEqual(normalizeEntityVersionCapabilities({
  runtimeEnabled: true,
  manualCaptureEnabled: true,
  historyReadable: true
}), {
  runtimeEnabled: true,
  manualCaptureEnabled: true,
  historyReadable: true
})
assert.deepEqual(normalizeEntityVersionCapabilities({
  runtimeEnabled: false,
  manualCaptureEnabled: true
}), {
  runtimeEnabled: false,
  manualCaptureEnabled: false,
  historyReadable: false
})
assert.equal(canShowEntityVersionAction({
  canViewVersions: true,
  runtimeEnabled: true
}), true)
for (const blocked of [
  { selectionScene: true, canViewVersions: true, runtimeEnabled: true },
  { isSystemEntity: true, canViewVersions: true, runtimeEnabled: true },
  { canViewVersions: false, runtimeEnabled: true },
  { canViewVersions: true, runtimeEnabled: false }
]) {
  assert.equal(canShowEntityVersionAction(blocked), false)
}
assert.equal(canShowEntityVersionAction({
  canViewVersions: true,
  runtimeEnabled: false,
  historyReadable: true
}), true)
assert.equal(canCaptureEntityRecordVersion({
  hasCapturePermission: true,
  runtimeEnabled: true,
  manualCaptureEnabled: true
}), true)
assert.equal(canCaptureEntityRecordVersion({
  hasCapturePermission: true,
  runtimeEnabled: true,
  manualCaptureEnabled: false
}), false)
assert.equal(canCaptureEntityRecordVersion({
  hasCapturePermission: false,
  runtimeEnabled: true,
  manualCaptureEnabled: true
}), false)
assert.equal(canCaptureEntityRecordVersion({
  hasCapturePermission: true,
  runtimeEnabled: false,
  manualCaptureEnabled: true
}), false)
assert.ok(drawerSource.includes('manualCaptureEnabled: props.manualCaptureEnabled'))
assert.ok(drawerSource.includes('runtimeEnabled: props.runtimeEnabled'))
assert.ok(drawerSource.includes('仅可查看和比较停用前生成的历史版本'))
assert.ok(drawerSource.includes('当前数据没有可查看的历史版本'))
assert.ok(drawerSource.includes('drawerContextGeneration'))
assert.ok(drawerSource.includes("watch(() => props.entityCode"))
assert.ok(drawerSource.includes('if (!canCapture.value || !isCurrentDrawerContext(context))'))
assert.ok(drawerSource.includes('captureRecordVersion(context.entityCode, context.recordId'))
assert.ok(entityDataListSource.includes('historyReadable: versionCapabilities.value.historyReadable'))
assert.ok(entityDataListSource.includes(':runtimeEnabled="versionCapabilities.runtimeEnabled"'))
assert.match(
  versionApiSource,
  /saveConfig\(entityCode,[\s\S]{0,500}method: 'PUT'[\s\S]{0,160}'If-Match'/,
  '实体版本配置保存应使用 PUT 与 revision 乐观锁'
)
assert.match(
  versionApiSource,
  /saveConfig\(entityCode,[\s\S]{0,400}`\/entity-versions\/configs\/\$\{entityCode\}\/current`[\s\S]{0,100}method: 'PUT'/,
  '当前配置保存必须使用语义明确的 /current 路径'
)
assert.ok(versionApiSource.includes('const LEGACY_SAVE_FALLBACK_STATUSES = new Set([404, 405])'))
assert.ok(versionApiSource.includes('if (!LEGACY_SAVE_FALLBACK_STATUSES.has(Number(error?.status)))'))
assert.match(
  versionApiSource,
  /`\/entity-versions\/configs\/\$\{entityCode\}\/draft`[\s\S]{0,100}method: 'POST'[\s\S]{0,160}'If-Match': String\(expectedRevision\)/,
  '旧 Controller 兼容分支应先按原 revision 保存草稿'
)
assert.match(
  versionApiSource,
  /`\/entity-versions\/configs\/\$\{entityCode\}\/releases`[\s\S]{0,140}method: 'POST'[\s\S]{0,160}'If-Match': String\(saved\.revision\)/,
  '旧 Controller 兼容分支应使用保存后 revision 切换运行配置'
)
assert.ok(versionApiSource.includes('应在 N+1 删除此前端 fallback'))
assert.ok(versionApiSource.includes('N+3 才物理 contract'))
;[
  '/publish',
  'getDraft(', 'saveDraft(', 'publishConfig(', 'validateDraft('
].forEach(marker => assert.equal(
  versionApiSource.includes(marker),
  false,
  `实体版本 API 不应对业务调用暴露旧契约: ${marker}`
))
;[
  '保存并生效',
  'entityVersionApi.getConfig(',
  'entityVersionApi.saveConfig(',
  'entityVersionApi.validateConfig(',
  '停用前的历史版本仍可查看'
].forEach(marker => assert.ok(
  managementSource.includes(marker),
  `单配置即时生效页面缺少契约: ${marker}`
))
assert.ok(managementSource.includes(':size="drawerSize"'))
assert.match(
  managementSource,
  /drawerSize = computed\(\(\) => viewportWidth\.value <= 768 \? '100%' : '66\.6667%'\)/,
  '数据版本配置抽屉应在桌面端约占三分之二，小屏使用全宽'
)
;[
  'entityVersion.enabled',
  'entityVersion.triggerType',
  'entityVersion.sourceTypes',
  'entityVersion.operationTypes',
  'entityVersion.businessIntents',
  'entityVersion.triggerCondition',
  'entityVersion.triggerPriority',
  'entityVersion.scopeFields',
  'entityVersion.scopeRelation',
  'entityVersion.scopeFilter',
  'entityVersion.scopeMaxRows',
  'entityVersion.scopeLimits',
  'entityVersion.diffChangedOnly',
  'entityVersion.diffTrackOrder',
  'entityVersion.diffIgnoredFields'
].forEach(key => assert.ok(
  managementSource.includes(`help-key="${key}"`),
  `数据版本关键配置缺少问号帮助: ${key}`
))
;[
  '保存草稿', 'publishDraft', 'loadReleases', '固化策略发布历史',
  'canPublish', 'publishing', 'activeReleaseVersion',
  'entityVersionApi.getDraft(', 'entityVersionApi.saveDraft(',
  'entityVersionApi.publishConfig(', 'entityVersionApi.releases('
].forEach(marker => assert.equal(
  managementSource.includes(marker),
  false,
  `单配置即时生效页面不应保留草稿或发布入口: ${marker}`
))
assert.ok(managementSource.includes('previewResult?.datasets || previewResult?.relations'))
assert.ok(managementSource.includes("previewResult.totalRows ?? '-') : '未计算'"))
assert.match(
  versionApiSource,
  /listConfigPage\(params = \{\}\)[\s\S]{0,260}\/entity-versions\/configs\/page[\s\S]{0,160}params: normalizedParams/,
  '数据版本配置列表 API 应统一携带服务端页码和每页条数'
)
assert.match(
  versionApiSource,
  /listConfigs\(params = \{\}\)[\s\S]{0,160}\/entity-versions\/configs/,
  '数据版本配置 API 应保留旧列表方法供滚动兼容'
)
assert.ok(
  versionApiSource.includes('loadEntityVersionConfigPageWithLegacyFallback({')
    && versionApiSource.includes("loadLegacy: () => request.get('/entity-versions/configs'")
    && /\/entity-versions\/configs\/page[\s\S]{0,120}silentError: true/.test(versionApiSource),
  '分页端点只能在确认新路由不存在时回退旧数组，并在前端完成同语义筛选分页'
)
assert.ok(
  managementSource.includes('entityVersionApi.listConfigPage({'),
  '数据版本设置页面应优先调用服务端分页端点'
)
;[
  'label="全部" value="ALL"',
  'label="已启用" value="ENABLED"',
  'label="未启用" value="DISABLED"',
  "enabledFilter.value === 'DISABLED' ? false",
  'pageNum: pageInfo.pageNum',
  'pageSize: pageInfo.pageSize',
  ':total="pageInfo.total"',
  '@current-change="loadConfigs"',
  '@size-change="handlePageSizeChange"'
].forEach(marker => assert.ok(
  managementSource.includes(marker),
  `数据版本配置列表缺少筛选或服务端分页契约: ${marker}`
))
assert.match(
  managementSource,
  /function handleQuery\(\) \{\s*pageInfo\.pageNum = 1\s*loadConfigs\(\)/,
  '搜索和状态筛选必须先回到第一页'
)
assert.match(
  managementSource,
  /function handlePageSizeChange\(\) \{\s*pageInfo\.pageNum = 1\s*loadConfigs\(\)/,
  '修改每页条数必须先回到第一页'
)
assert.equal(managementSource.includes('<Refresh />'), false, '页面右上角不应保留刷新按钮')

console.log('entity-version-model tests passed')
