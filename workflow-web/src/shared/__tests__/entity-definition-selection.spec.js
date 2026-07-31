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
assert.match(
  entityDesignSource,
  /title="选择目标实体"\s+:query="\{ status: 'PUBLISHED' \}"/,
  '实体记录引用应允许选择全部已发布实体'
)
assert.match(
  entityDesignSource,
  /title="选择子实体"[\s\S]{0,160}:query="\{ storageMode: 'DYNAMIC' \}"/,
  '子表单目标仍应限制为动态实体'
)

console.log('entity definition selection tests passed')
