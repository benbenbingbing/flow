import assert from 'node:assert/strict'
import {
  enforceSubListParameterUsage,
  enforceSubListParentReferenceMapping,
  isParentEntityReferenceTarget,
  isPublishedSubListOption,
  isSubListTargetFieldWritable,
  normalizeListScenes,
  normalizeSubListDisplayConfig,
  normalizeSubListParameterContract,
  resolveDefaultSubListParameterSource,
  resolveSubListParameterContract,
  resolveSubListTargetSelection,
  supportsSubListEmbedding
} from '../sub-list.js'

assert.deepEqual(
  normalizeSubListDisplayConfig({
    showToolbar: false,
    showRowActions: false
  }),
  {
    actionDisplayVersion: 0,
    showSearch: true,
    showPagination: true,
    showToolbar: true,
    showRowActions: true,
    pageSize: 10,
    maxHeight: 420
  },
  'legacy sub-list configs must expose list actions after the action UI upgrade'
)

assert.deepEqual(
  normalizeSubListDisplayConfig({
    actionDisplayVersion: 2,
    showToolbar: false,
    showRowActions: false,
    pageSize: 20,
    maxHeight: 500
  }),
  {
    actionDisplayVersion: 2,
    showSearch: true,
    showPagination: true,
    showToolbar: false,
    showRowActions: false,
    pageSize: 20,
    maxHeight: 500
  },
  'current sub-list configs must preserve explicit action visibility settings'
)

assert.deepEqual(
  normalizeListScenes('["PAGE","embedded"]'),
  ['PAGE', 'EMBEDDED']
)
assert.equal(supportsSubListEmbedding({ allowedScenes: [] }), true)
assert.equal(
  supportsSubListEmbedding({ allowedScenes: ['PAGE'] }),
  false
)
assert.equal(
  supportsSubListEmbedding({ allowedScenes: ['PAGE', 'EMBEDDED'] }),
  true
)
assert.equal(
  isPublishedSubListOption({
    listKey: 'default',
    activeReleaseId: 'release-1',
    publishedVersion: 1,
    allowedScenes: ['EMBEDDED']
  }),
  true
)
assert.equal(
  isPublishedSubListOption({
    listKey: 'default',
    activeReleaseId: 'release-1',
    publishedVersion: 1,
    allowedScenes: ['PAGE']
  }),
  false
)
assert.equal(
  isPublishedSubListOption({
    listKey: 'default',
    activeReleaseId: 'release-1',
    publishedVersion: 0,
    allowedScenes: ['EMBEDDED']
  }),
  false
)

const parentReferenceField = {
  fieldCode: 'reqId',
  fieldType: 'REFERENCE',
  refEntityId: 'parent-entity'
}
assert.equal(
  isParentEntityReferenceTarget(
    parentReferenceField,
    'parent-entity'
  ),
  true,
  'a scalar reference to the parent entity must be recognized'
)
assert.equal(
  isParentEntityReferenceTarget(
    parentReferenceField,
    'another-entity'
  ),
  false,
  'a reference to another entity must not be treated as the parent relation'
)
assert.equal(
  resolveDefaultSubListParameterSource(
    parentReferenceField,
    [{ fieldCode: 'name' }],
    'parent-entity'
  ),
  'parent.recordId',
  'a child reference to the parent entity must default to the parent record ID'
)
assert.equal(
  resolveDefaultSubListParameterSource(
    { fieldCode: 'project_id', fieldType: 'STRING' },
    [
      { fieldCode: 'name' },
      { fieldCode: 'project_id' }
    ],
    'parent-entity'
  ),
  'parent.data.project_id',
  'ordinary fields must prefer a parent field with the same code'
)

assert.deepEqual(
  enforceSubListParentReferenceMapping(
    {
      targetField: 'reqId',
      source: 'parent.data.name',
      operator: 'LIKE',
      required: false,
      useForQuery: false,
      useForCreate: true
    },
    {
      ...parentReferenceField,
      queryable: true,
      writable: true
    },
    'parent-entity'
  ),
  {
    targetField: 'reqId',
    source: 'parent.recordId',
    operator: 'EQ',
    required: true,
    useForQuery: true,
    useForCreate: true
  },
  'a reference to the current parent must always filter by parent ID'
)

assert.deepEqual(
  enforceSubListParentReferenceMapping(
    {
      targetField: 'project_id',
      source: 'parent.data.project_id',
      operator: 'EQ',
      required: false,
      useForQuery: false,
      useForCreate: true
    },
    {
      fieldCode: 'project_id',
      fieldType: 'STRING'
    },
    'parent-entity'
  ),
  {
    targetField: 'project_id',
    source: 'parent.data.project_id',
    operator: 'EQ',
    required: false,
    useForQuery: false,
    useForCreate: true
  },
  'ordinary parameters must keep independently configurable query usage'
)

assert.equal(
  isSubListTargetFieldWritable({
    fieldCode: 'name',
    isSystem: true
  }),
  true,
  'the built-in data name is writable when creating a child record'
)
assert.equal(
  isSubListTargetFieldWritable({
    fieldCode: 'code',
    isSystem: true
  }),
  false,
  'system-managed fields other than name must remain unavailable'
)
assert.equal(
  isSubListTargetFieldWritable({
    fieldCode: 'remark',
    isReadonly: true
  }),
  false,
  'readonly entity fields must not be used as create initial values'
)

assert.deepEqual(
  enforceSubListParameterUsage(
    {
      targetField: 'name',
      required: true,
      useForQuery: false,
      useForCreate: false
    },
    {
      queryable: false,
      writable: true
    }
  ),
  {
    targetField: 'name',
    required: true,
    useForQuery: false,
    useForCreate: true
  },
  'an invalid empty usage must select the only available create usage'
)
assert.deepEqual(
  enforceSubListParameterUsage(
    {
      targetField: 'id',
      required: true,
      useForQuery: true,
      useForCreate: true
    },
    {
      queryable: false,
      writable: false
    }
  ),
  {
    targetField: 'id',
    required: false,
    useForQuery: false,
    useForCreate: false
  },
  'an unavailable target must not retain an impossible required state'
)

assert.deepEqual(
  resolveSubListTargetSelection(
    {
      refEntityId: 'target-1',
      refListKey: 'list001'
    },
    {
      id: 'target-1'
    }
  ),
  {
    refEntityId: 'target-1',
    refEntityType: 'CUSTOM',
    refListKey: 'list001'
  },
  'resolving a saved target entity must preserve its saved list'
)

assert.deepEqual(
  resolveSubListTargetSelection(
    {
      refEntityId: 'target-1',
      refListKey: 'list001'
    },
    {
      id: 'target-2'
    },
    {
      resetListKey: true
    }
  ),
  {
    refEntityId: 'target-2',
    refEntityType: 'CUSTOM',
    refListKey: ''
  },
  'explicitly selecting another target entity must reset the old list'
)

assert.deepEqual(
  normalizeSubListParameterContract({
    mappings: [{
      targetField: 'project_id',
      source: 'parent.data.project_id'
    }]
  }),
  {
    version: 1,
    mappings: [{
      targetField: 'project_id',
      targetFieldName: '',
      source: 'parent.data.project_id',
      operator: 'EQ',
      required: true,
      useForQuery: true,
      useForCreate: true
    }]
  }
)

assert.deepEqual(
  resolveSubListParameterContract(
    {
      version: 1,
      mappings: [
        {
          targetField: 'project_id',
          targetFieldName: '所属项目',
          source: 'parent.data.project_id',
          operator: 'EQ',
          required: true,
          useForQuery: true,
          useForCreate: true
        },
        {
          targetField: 'source_id',
          source: 'parent.recordId',
          required: true,
          useForQuery: true,
          useForCreate: false
        },
        {
          targetField: 'source_type',
          source: { literal: 'FORM' },
          required: false,
          useForQuery: false,
          useForCreate: true
        }
      ]
    },
    {
      entityId: 'parent-entity',
      recordId: 'parent-record',
      record: {
        id: 'parent-record',
        data: {
          project_id: 'project-1'
        }
      }
    }
  ),
  {
    parameters: {
      project_id: 'project-1',
      source_id: 'parent-record',
      source_type: 'FORM'
    },
    queryFilters: {
      project_id: 'project-1',
      project_id_op: 'EQ',
      source_id: 'parent-record',
      source_id_op: 'EQ'
    },
    createValues: {
      project_id: 'project-1',
      source_type: 'FORM'
    },
    missingRequired: []
  }
)

assert.deepEqual(
  resolveSubListParameterContract(
    {
      mappings: [{
        targetField: 'project_id',
        targetFieldName: '所属项目',
        source: 'parent.data.project_id',
        required: true
      }]
    },
    {
      record: { data: {} }
    }
  ).missingRequired,
  [{
    targetField: 'project_id',
    targetFieldName: '所属项目'
  }],
  'missing required parent values must block the embedded list'
)

console.log('sub-list tests passed')
