import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const read = relativePath => readFileSync(
  fileURLToPath(new URL(relativePath, import.meta.url)),
  'utf8'
)

const api = read('../../api/entityRelation.js')
const designer = read('../../views/EntityDesign.vue')
const management = read('../../views/entity/components/EntityRelationManagement.vue')
const router = read('../../router/index.js')

;[
  '`/entity/${entityId}/relations`',
  '`/entity/${entityId}/relations/${relationId}`',
  "method: 'PUT'",
  "method: 'DELETE'"
].forEach(marker => assert.ok(api.includes(marker), `实体关系 API 缺少契约: ${marker}`))

;[
  'name="relations"',
  '<EntityRelationManagement',
  ':can-manage="canManageEntityDefinition"',
  '实体关系已从 SUB_FORM 字段中拆分'
].forEach(marker => assert.ok(designer.includes(marker), `实体设计器缺少独立关系入口: ${marker}`))

assert.equal(
  designer.includes('v-model="selectedField.childEntityId"'),
  false,
  'SUB_FORM 字段属性不得继续创建或编辑实体关系'
)

;[
  'v-model="editor.relationCode"',
  'v-model="editor.dataKey"',
  ':disabled="isEditing"',
  'v-model="editor.childEntityId"',
  'v-model="editor.childRefFieldCode"',
  'value="ONE_TO_ONE"',
  'value="ONE_TO_MANY"',
  'value="COMPOSITION"',
  'value="ASSOCIATION"',
  'v-model="editor.cascadeDelete"',
  'v-model="editor.required"',
  'v-model="editor.sortOrder"',
  'v-model="editor.enabled"',
  '删除后编码不能复用',
  'entity:definition:manage'
].forEach(marker => assert.ok(management.includes(marker), `实体关系管理缺少能力: ${marker}`))

assert.match(
  router,
  /path:\s*'\/entity\/design\/:id'[\s\S]{0,300}requiredPermissions:\s*\['entity:definition:view'\]/,
  '实体设计路由必须要求 entity:definition:view 权限'
)

console.log('entity-relation-ui-contract tests passed')
