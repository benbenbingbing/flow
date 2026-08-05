import assert from 'node:assert/strict'
import {
  applySubFormFieldInitialization,
  buildInputParameterSchema,
  buildSubFormParentContext,
  getInputParameterDefinitions,
  normalizeSubFormParameterContract,
  resolveSubFormParameters,
  validateSubFormParameters
} from '../subform-parameter-contract.js'

const schema = buildInputParameterSchema([
  {
    code: 'projectId',
    name: '项目ID',
    type: 'string',
    required: true,
    description: '当前业务所属项目'
  },
  {
    code: 'sourceDeptId',
    name: '来源部门',
    type: 'string',
    required: false,
    defaultValue: 'DEFAULT_DEPT'
  }
])

assert.deepEqual(
  getInputParameterDefinitions(schema).map(item => item.code),
  ['projectId', 'sourceDeptId']
)

const contract = normalizeSubFormParameterContract({
  version: 1,
  parameterMapping: {
    projectId: 'parent.data.project_id',
    sourceDeptId: 'context.departmentId'
  },
  fieldInitializationMapping: {
    source_dept_id: 'parent.data.dept_id',
    fixed_flag: { literal: 'Y' }
  }
})

const parent = buildSubFormParentContext({
  record: {
    id: 'parent-1',
    data: {
      project_id: 'project-1',
      dept_id: 'dept-1'
    }
  }
})
const source = {
  parent,
  context: {
    departmentId: 'dept-context'
  }
}
const params = resolveSubFormParameters(contract, source, schema)

assert.deepEqual(params, {
  projectId: 'project-1',
  sourceDeptId: 'dept-context'
})
assert.deepEqual(validateSubFormParameters(params, schema), [])
assert.match(
  validateSubFormParameters({}, schema)[0],
  /项目ID/
)

const row = {
  source_dept_id: 'manual-dept',
  fixed_flag: ''
}
const initialized = applySubFormFieldInitialization(
  row,
  contract,
  source,
  ['id', 'parent_id']
)

assert.equal(initialized, true)
assert.deepEqual(row, {
  source_dept_id: 'manual-dept',
  fixed_flag: 'Y'
})
assert.equal(
  applySubFormFieldInitialization(row, contract, source),
  false
)

const typedDefaults = buildInputParameterSchema([
  {
    code: 'enabled',
    name: '是否启用',
    type: 'boolean',
    defaultValue: false
  },
  {
    code: 'limit',
    name: '数量',
    type: 'integer',
    defaultValue: 0
  },
  {
    code: 'filters',
    name: '过滤条件',
    type: 'object',
    defaultValue: '{"status":"ACTIVE"}'
  },
  {
    code: 'roles',
    name: '角色',
    type: 'array',
    defaultValue: '["OWNER","MEMBER"]'
  }
])

assert.deepEqual(typedDefaults.properties.enabled.default, false)
assert.deepEqual(typedDefaults.properties.limit.default, 0)
assert.deepEqual(typedDefaults.properties.filters.default, {
  status: 'ACTIVE'
})
assert.deepEqual(typedDefaults.properties.roles.default, [
  'OWNER',
  'MEMBER'
])

console.log('subform parameter contract tests passed')
