import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolveEntityFileUploadContext } from '@flow/workflow-core/entity-file-upload-context'

const field = { fieldCode: 'requirementsFile' }

assert.deepEqual(
  resolveEntityFileUploadContext(
    { entityCode: 'ZDWREQ', mode: 'create', record: { data: {} } },
    field
  ),
  {
    entityCode: 'ZDWREQ',
    action: 'create',
    fieldCode: 'requirementsFile'
  }
)

assert.equal(
  resolveEntityFileUploadContext(
    { entityCode: 'ZDWREQ', mode: 'edit', record: { id: 'record-1' } },
    field
  )?.action,
  'update'
)

assert.equal(
  resolveEntityFileUploadContext(
    { entityCode: 'ZDWREQ', mode: 'approve', record: { id: 'record-1' } },
    field
  )?.action,
  'approve'
)

assert.equal(
  resolveEntityFileUploadContext(
    {
      entityCode: 'parent_entity',
      form: { entityCode: 'child_entity' },
      mode: 'create',
      record: { data: {} },
      parentField: { fieldCode: 'details' }
    },
    field
  ),
  null,
  '子表文件不能冒用根实体动作权限'
)

assert.equal(
  resolveEntityFileUploadContext(
    { entityCode: 'ZDWREQ', mode: 'view', record: { id: 'record-1' } },
    field
  ),
  null
)

assert.equal(
  resolveEntityFileUploadContext(
    { entityCode: 'ZDWREQ', mode: 'create', record: { data: {} } },
    {}
  ),
  null
)

assert.equal(
  resolveEntityFileUploadContext(
    { entityCode: 'ZDWREQ', mode: 'create' },
    field
  ),
  null,
  '没有 record 的设计预览不能冒充真实实体新增操作'
)

const fileApiSource = readFileSync(
  new URL(import.meta.resolve('@flow/workflow-api/file')),
  'utf8'
)
assert.match(fileApiSource, /formData\.append\('action', context\.action\)/)
assert.match(fileApiSource, /formData\.append\('fieldCode', context\.fieldCode\)/)
assert.match(fileApiSource, /\/file\/entity\/\$\{entityCode\}\/upload/)
assert.match(fileApiSource, /'Idempotency-Key': options\.idempotencyKey/)

const fileFieldSource = readFileSync(
  new URL('../../extensions/builtin/fields/components/FileField.vue', import.meta.url),
  'utf8'
)
assert.match(fileFieldSource, /:upload-context="uploadContext"/)
assert.match(fileFieldSource, /resolveEntityFileUploadContext\(props\.context, enrichedField\.value\)/)

const uploaderSource = readFileSync(
  new URL('../../components/FileUploader.vue', import.meta.url),
  'utf8'
)
assert.match(uploaderSource, /props\.uploadContext\s*\?\s*await fileApi\.uploadForEntity/)
assert.match(uploaderSource, /silentError:\s*true/)

console.log('entity file upload context tests passed')
