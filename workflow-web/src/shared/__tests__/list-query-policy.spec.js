import assert from 'node:assert/strict'
import { supportsEntityFieldQuery } from '../list-query-policy.js'
import { applyListColumnTemplateSnapshot, buildListColumnTemplateSnapshot } from '../list-column-template.js'

assert.equal(supportsEntityFieldQuery({ dataSourceType: 'ENTITY_FIELD', fieldId: 'real' }), true)
assert.equal(supportsEntityFieldQuery({ dataSourceType: 'FIELD_TEMPLATE' }), false)
assert.equal(supportsEntityFieldQuery({ dataSourceType: 'ENTITY_FIELD', fieldId: 'virtual_1' }), false)
assert.equal(supportsEntityFieldQuery({ dataSourceType: 'ENTITY_FIELD', interfaceExtensionId: 'interface-1' }), false)

const template = { field: { dataSourceType: 'ENTITY_FIELD', isQuery: true } }
assert.equal(applyListColumnTemplateSnapshot({ fieldId: 'virtual_1' }, template).isQuery, false)
assert.equal(applyListColumnTemplateSnapshot({ fieldId: 'real' }, template).isQuery, true)
assert.equal(buildListColumnTemplateSnapshot({ dataSourceType: 'FIELD_TEMPLATE', isQuery: true }).field.isQuery, false)
console.log('list-query-policy tests passed')
