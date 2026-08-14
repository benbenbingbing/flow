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

const legacy = createVersionDraft({
  entityCode: 'ORDER',
  entityName: '订单',
  scenarios: [{
    scenarioCode: 'APPROVED',
    scenarioName: '审批通过',
    sourceTypes: ['APPROVAL_TASK'],
    operationTypes: ['UPDATE']
  }],
  steps: [{ stepName: '旧步骤' }],
  targetBindings: [{ bindingCode: 'LEGACY' }]
})
assert.equal(legacy.triggers[0].triggerCode, 'APPROVED')
assert.equal(legacy.triggers[0].triggerType, 'ROOT_MUTATION')
assert.equal(legacy.snapshotScope.limits.maxRowsPerRelation, 500)
const serialized = serializeVersionDraft(legacy)
assert.equal(serialized.triggers[0].triggerName, '审批通过')
assert.equal('scenarios' in serialized, false)
assert.equal('steps' in serialized, false)
assert.equal('targetBindings' in serialized, false)

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
assert.ok(drawerSource.includes('loadSnapshotRelationPage(node, 1, true)'))
assert.ok(drawerSource.includes('changedOnly: changedOnly.value'))
assert.ok(drawerSource.includes('comparison.value?.diffPolicy?.changedOnlyDefault !== false'))
assert.match(
  drawerSource,
  /watch\(changedOnly,[\s\S]{0,500}loadRelationPage\(node, 1, true\)/,
  '切换仅看变化时必须重新读取关系行首屏'
)
assert.ok(versionApiSource.includes("...(String(recordId || '').trim()"))
assert.ok(managementSource.includes('previewResult?.datasets || previewResult?.relations'))
assert.ok(managementSource.includes("previewResult.totalRows ?? '-') : '未计算'"))

console.log('entity-version-model tests passed')
