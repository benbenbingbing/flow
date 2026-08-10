import assert from 'node:assert/strict'
import {
  applyListColumnTemplateSnapshot,
  buildListColumnTemplateSnapshot,
  createListColumnTemplateEditor,
  mappingObjectToRows,
  mappingRowsToObject,
  parseListColumnTemplateSnapshot,
  sanitizeTemplateFieldConfig
} from '../list-column-template.js'

const editor = createListColumnTemplateEditor({
  templateKey: 'COMMON_STATUS_COLUMN',
  templateName: '通用状态列',
  metadata: {
    sampleValue: 'ACTIVE'
  },
  field: {
    fieldCode: 'status',
    fieldName: '状态',
    sortOrder: 3,
    width: 140,
    align: 'center',
    isQuery: true,
    queryType: 'EQ',
    renderComponent: 'StatusBadge',
    renderConfig: JSON.stringify({
      emptyText: '-',
      labelMap: { ACTIVE: '启用' },
      statusMap: { ACTIVE: 'success' }
    })
  }
})

assert.equal(editor.renderConfig.labelMap.ACTIVE, '启用')

const snapshot = buildListColumnTemplateSnapshot(editor)
assert.equal(snapshot.metadata.sampleValue, 'ACTIVE')
assert.equal(snapshot.metadata.applicableFieldKind, undefined)
assert.equal(snapshot.field.width, 140)
assert.equal(snapshot.field.fieldCode, undefined)
assert.equal(snapshot.field.fieldName, undefined)
assert.equal(snapshot.field.sortOrder, undefined)
assert.deepEqual(
  JSON.parse(snapshot.field.renderConfig).statusMap,
  { ACTIVE: 'success' }
)

const parsed = parseListColumnTemplateSnapshot(JSON.stringify(snapshot))
assert.equal(parsed.field.renderComponent, 'StatusBadge')

const initializedField = applyListColumnTemplateSnapshot({
  id: 'list-field-1',
  fieldCode: 'status',
  fieldName: '流程状态',
  templateId: 'old-template',
  templateVersion: 3,
  localOverridesDocument: '{"width":120}'
}, JSON.stringify(snapshot))
assert.equal(initializedField.id, 'list-field-1')
assert.equal(initializedField.fieldCode, 'status')
assert.equal(initializedField.fieldName, '流程状态')
assert.equal(initializedField.width, 140)
assert.equal(initializedField.templateId, null)
assert.equal(initializedField.templateVersion, null)
assert.equal(initializedField.localOverridesDocument, '')

const editorWithEmptyMapping = createListColumnTemplateEditor({
  field: {
    renderConfig: JSON.stringify({
      labelMap: { ACTIVE: '启用', EMPTY: '' },
      statusMap: { ACTIVE: 'success', EMPTY: '' }
    })
  }
})
assert.deepEqual(editorWithEmptyMapping.renderConfig.labelMap, { ACTIVE: '启用' })
assert.deepEqual(editorWithEmptyMapping.renderConfig.statusMap, { ACTIVE: 'success' })

assert.deepEqual(
  sanitizeTemplateFieldConfig({
    fieldCode: 'status',
    id: 'field-1',
    width: 160
  }),
  { width: 160 }
)

const rows = mappingObjectToRows({
  ACTIVE: '启用',
  DISABLED: '停用'
})
assert.deepEqual(mappingRowsToObject(rows), {
  ACTIVE: '启用',
  DISABLED: '停用'
})
assert.deepEqual(
  mappingRowsToObject([
    { key: ' ACTIVE ', value: '启用' },
    { key: 'EMPTY', value: '' },
    { key: '', value: '忽略' }
  ]),
  { ACTIVE: '启用' }
)
