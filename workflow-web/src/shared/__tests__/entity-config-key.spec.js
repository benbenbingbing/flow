import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  buildEntityConfigKey,
  getEntityConfigKeyPrefix,
  getEntityConfigKeySuffixMaxLength
} from '../entity-config-key.js'

assert.equal(getEntityConfigKeyPrefix('project'), 'project_')
assert.equal(getEntityConfigKeyPrefix(' project_request '), 'project_request_')
assert.equal(getEntityConfigKeyPrefix(''), '')

assert.equal(buildEntityConfigKey('project', 'detail'), 'project_detail')
assert.equal(buildEntityConfigKey('project', ' detail '), 'project_detail')
assert.equal(getEntityConfigKeySuffixMaxLength('project'), 92)

assert.throws(
  () => buildEntityConfigKey('', 'detail'),
  /实体编码不能为空/
)
assert.throws(
  () => buildEntityConfigKey('project', ''),
  /配置标识不能为空/
)
assert.throws(
  () => buildEntityConfigKey('project', 'a'.repeat(93)),
  /最长 100 个字符/
)

const formListSource = readFileSync(
  new URL('../../views/EntityFormList.vue', import.meta.url),
  'utf8'
)
const listConfigSource = readFileSync(
  new URL('../../views/EntityListConfig.vue', import.meta.url),
  'utf8'
)

assert.ok(
  formListSource.includes('<template #prepend>{{ formKeyPrefix }}</template>')
    && formListSource.includes(
      'formKey: buildEntityConfigKey(entityInfo.value.entityCode, form.formKey)'
    ),
  '新建表单必须展示实体编码前缀并提交合成后的完整 formKey'
)
assert.ok(
  formListSource.includes('await updateForm(form.id, form)')
    && !formListSource.includes(
      'form.formKey = buildEntityConfigKey'
    ),
  '编辑表单必须继续提交原 formKey，新增失败重试也不得污染后缀模型'
)
assert.ok(
  listConfigSource.includes('<template #prepend>{{ listKeyPrefix }}</template>')
    && listConfigSource.includes(
      'listKey: buildEntityConfigKey(entityCode.value, formData.value.listKey)'
    ),
  '新建列表必须展示实体编码前缀并提交合成后的完整 listKey'
)
assert.ok(
  listConfigSource.includes('await entityListConfigApi.patchMetadata(')
    && !listConfigSource.includes(
      'formData.value.listKey = buildEntityConfigKey'
    ),
  '编辑列表必须继续使用原 listKey，新增失败重试也不得污染后缀模型'
)

console.log('entity config key tests passed')
