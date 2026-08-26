import assert from 'node:assert/strict'

import { normalizeRuntimeFormRelease } from '../list-button-form-runtime.js'

function releaseWithSnapshot(snapshotDocument) {
  return {
    id: 'release-form-v5',
    version: 5,
    effectiveReleaseId: 'release-form-v5-hotfix',
    hotfixApplied: true,
    snapshotDocument
  }
}

{
  const viewCompositions = [
    {
      id: 'composition-1',
      compositionKey: 'related_project',
      anchorType: 'OWNER',
      config: { name: '查看所属项目' }
    }
  ]
  const runtimeForm = normalizeRuntimeFormRelease(
    releaseWithSnapshot({
      form: { id: 'form-1', formName: '需求表单' },
      legacyFields: [{ fieldCode: 'name' }],
      nodes: [{ id: 'node-1' }],
      viewCompositions
    }),
    'fallback-form-id',
    'resolution-token'
  )

  assert.deepEqual(runtimeForm.viewCompositions, viewCompositions)
  assert.equal(runtimeForm.runtimeReleaseId, 'release-form-v5')
  assert.equal(runtimeForm.runtimeReleaseVersion, 5)
  assert.equal(runtimeForm.releaseResolutionToken, 'resolution-token')
}

{
  const runtimeForm = normalizeRuntimeFormRelease(
    releaseWithSnapshot(JSON.stringify({
      form: { formName: '兼容旧发布表单' },
      legacyFields: [],
      nodes: []
    })),
    'fallback-form-id'
  )

  assert.equal(runtimeForm.id, 'fallback-form-id')
  assert.deepEqual(runtimeForm.viewCompositions, [])
}

assert.throws(
  () => normalizeRuntimeFormRelease(releaseWithSnapshot({ nodes: [] }), 'form-1'),
  /目标表单发布快照缺少表单定义/
)

console.log('list-button-form-runtime.spec.js passed')
