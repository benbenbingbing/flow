import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import {
  normalizeEntitySelectionValues,
  reconcileEntitySelection,
  serializeEntitySelection,
  toggleEntitySelection
} from '../entity-definition-selection.js'

const resolved = [
  { id: '1', entityCode: 'project', entityName: '项目' },
  { id: '2', entityCode: 'contract', entityName: '合同' }
]

assert.deepEqual(
  normalizeEntitySelectionValues(['project', 'contract', 'project'], true),
  ['project', 'contract']
)

const hydrated = reconcileEntitySelection(
  ['project', 'missing_entity'],
  resolved,
  'entityCode'
)
assert.equal(hydrated[0].entityName, '项目')
assert.equal(hydrated[1].missing, true)

const afterAnotherPage = toggleEntitySelection(
  hydrated,
  { id: '3', entityCode: 'invoice', entityName: '发票' },
  { multiple: true, valueKey: 'entityCode' }
)
assert.deepEqual(
  serializeEntitySelection(afterAnotherPage, {
    multiple: true,
    valueKey: 'entityCode',
    valueCase: 'lower'
  }),
  ['project', 'missing_entity', 'invoice']
)

const removedAcrossPages = toggleEntitySelection(
  afterAnotherPage,
  resolved[0],
  { multiple: true, valueKey: 'entityCode' }
)
assert.deepEqual(
  serializeEntitySelection(removedAcrossPages, {
    multiple: true,
    valueKey: 'entityCode'
  }),
  ['missing_entity', 'invoice']
)

assert.equal(
  serializeEntitySelection([resolved[1]], {
    multiple: false,
    valueKey: 'id'
  }),
  '2'
)

const entityDesignSource = readFileSync(
  fileURLToPath(new URL('../../views/EntityDesign.vue', import.meta.url)),
  'utf8'
)
const entityRelationSource = readFileSync(
  fileURLToPath(new URL('../../views/entity/components/EntityRelationManagement.vue', import.meta.url)),
  'utf8'
)
const entityDefaultEventSource = readFileSync(
  fileURLToPath(new URL('../../views/entity/components/EntityDefaultEventPanel.vue', import.meta.url)),
  'utf8'
)
assert.match(
  entityDesignSource,
  /title="选择目标实体"\s+:query="\{ status: 'PUBLISHED' \}"/,
  '实体记录引用应允许选择全部已发布实体'
)
assert.match(
  entityRelationSource,
  /title="选择关系子实体"[\s\S]{0,200}:query="\{ storageMode: 'DYNAMIC', status: 'PUBLISHED' \}"/,
  '独立实体关系的子实体应限制为已发布动态实体'
)
assert.equal(
  entityDesignSource.includes('v-model="selectedField.childEntityId"'),
  false,
  '子实体选择不得继续依附 SUB_FORM 字段'
)

;[
  'label="默认事件"',
  'name="events"',
  'v-if="canConfigureEntityDefaultEvents"',
  '<EntityDefaultEventPanel',
  ':entity-id="String(entityData.id || entityId)"',
  'normalizeEntityDesignTab(route.query.tab)',
  "watch(() => route.query.tab"
].forEach(marker => assert.ok(
  entityDesignSource.includes(marker),
  `实体设计器缺少实体默认事件入口契约: ${marker}`
))

assert.match(
  entityDesignSource,
  /const canConfigureEntityDefaultEvents = computed\(\(\) => Boolean\(entityData\.value\?\.id\)[\s\S]{0,160}canManageEntityDefinition\.value[\s\S]{0,80}!isSystemEntity\.value\)/,
  '默认事件入口必须同时限制实体已加载、实体维护权限和非系统实体'
)

;[
  '<EventBindingEditor',
  'owner-type="ENTITY"',
  'target-type="OWNER"',
  'target-key=""',
  ':owner-id="String(entityId || \'\')"',
  '实体默认事件可作为该实体表单和列表的上级事件链',
  '替换上级',
  '禁用自定义',
  '保存后需重新发布相关表单和列表',
  '已发布页面继续使用原有事件快照'
].forEach(marker => assert.ok(
  entityDefaultEventSource.includes(marker),
  `实体默认事件面板缺少契约: ${marker}`
))

console.log('entity definition selection tests passed')
