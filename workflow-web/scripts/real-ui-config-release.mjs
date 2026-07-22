const apiBase = process.env.API_BASE || 'http://localhost:8081/api'
const username = process.env.TEST_USERNAME || 'admin'
const password = process.env.TEST_PASSWORD || 'admin'
const preferredFormId = process.env.FORM_ID || ''
const preferredListId = process.env.LIST_ID || ''

let token = ''

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

async function request(path, options = {}) {
  const response = await fetch(`${apiBase}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {})
    }
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok || !payload || ![0, 200, '0', '200'].includes(payload.code)) {
    const error = new Error(payload?.message || `HTTP ${response.status}`)
    error.status = response.status
    error.payload = payload
    throw error
  }
  return payload.data
}

async function requestConflict(path, body) {
  const response = await fetch(`${apiBase}${path}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify(body)
  })
  const payload = await response.json()
  assert(response.status === 409, `过期修订号应返回 409，实际为 ${response.status}`)
  assert(
    payload.errorCode === 'CONFIG_REVISION_CONFLICT',
    `409 应返回 CONFIG_REVISION_CONFLICT，实际为 ${payload.errorCode}`
  )
  assert(payload.data?.revision, '409 响应应携带服务器当前节点')
}

function parseProps(node) {
  return node?.propsDocument ? JSON.parse(node.propsDocument) : {}
}

function nodeLabel(node) {
  return parseProps(node).label || node.nodeKey
}

function runtimeLabel(form, nodeId, fieldCode) {
  const node = (form?.nodes || []).find(item => item.id === nodeId)
  if (node) return nodeLabel(node)
  const field = (form?.fields || []).find(item =>
    item.id === nodeId || item.fieldCode === fieldCode
  )
  return field?.fieldLabel || field?.fieldName
}

function stableOtherNodes(nodes, selectedId) {
  return JSON.stringify(nodes.filter(node => node.id !== selectedId))
}

async function acceptForm(form) {
  const nodesBefore = await request(`/entity-forms/${form.id}/nodes`)
  console.log(
    '[real-ui-config] nodes='
      + JSON.stringify(nodesBefore.map(node => ({
        id: node.id,
        nodeKey: node.nodeKey,
        nodeType: node.nodeType,
        parentId: node.parentId || null
      })))
  )
  const selected = nodesBefore.find(node =>
    node.nodeType === 'FIELD' && node.propsDocument
  )
  assert(selected, '验收表单没有可修改的 FIELD 节点')

  const releasesBefore = await request(`/entity-forms/${form.id}/releases`)
  const baselineRelease = releasesBefore.find(release => release.status === 'ACTIVE')
  assert(baselineRelease, '验收表单没有激活发布版本')

  const entityCode = form.entity?.entityCode
  const originalProps = parseProps(selected)
  const originalLabel = nodeLabel(selected)
  const testLabel = `${originalLabel}-单项验收-${Date.now()}`
  const otherNodesBefore = stableOtherNodes(nodesBefore, selected.id)
  let draftChanged = false

  try {
    const initialDiff = await request(`/entity-forms/${form.id}/diff`)
    assert(!initialDiff.changed, `初始草稿不应存在差异: ${JSON.stringify(initialDiff)}`)

    const runtimeBefore = await request(`/entity-form-resolve/new-data/${entityCode}`)
    assert(
      runtimeLabel(runtimeBefore, selected.id, originalProps.fieldCode) === originalLabel,
      '初始运行时标签与激活版本不一致'
    )

    const patched = await request(`/entity-forms/${form.id}/nodes/${selected.id}`, {
      method: 'PATCH',
      body: JSON.stringify({
        expectedRevision: selected.revision,
        props: { ...originalProps, label: testLabel }
      })
    })
    draftChanged = true
    assert(patched.revision === selected.revision + 1, '单节点保存未递增 revision')

    const nodesAfterPatch = await request(`/entity-forms/${form.id}/nodes`)
    assert(
      stableOtherNodes(nodesAfterPatch, selected.id) === otherNodesBefore,
      '单节点保存修改了其他节点'
    )

    await requestConflict(`/entity-forms/${form.id}/nodes/${selected.id}`, {
      expectedRevision: selected.revision,
      props: { ...originalProps, label: `${testLabel}-冲突写入` }
    })

    const runtimeDraft = await request(`/entity-form-resolve/new-data/${entityCode}`)
    assert(
      runtimeLabel(runtimeDraft, selected.id, originalProps.fieldCode) === originalLabel,
      '仅保存草稿后运行时不应发生变化'
    )

    const draftDiff = await request(`/entity-forms/${form.id}/diff`)
    assert(draftDiff.changed, '修改节点后 diff 应标记为 changed')
    assert(draftDiff.changedSections.includes('nodes'), '修改节点后 diff 应包含 nodes')

    const published = await request(`/entity-forms/${form.id}/publish`, {
      method: 'POST',
      body: JSON.stringify({ description: '自动化真实单节点发布验收' })
    })
    assert(published.status === 'ACTIVE', '新发布版本未激活')
    assert(published.version > baselineRelease.version, '发布版本号未递增')

    const runtimePublished = await request(`/entity-form-resolve/new-data/${entityCode}`)
    assert(
      runtimeLabel(runtimePublished, selected.id, originalProps.fieldCode) === testLabel,
      '发布后运行时未读取新节点标签'
    )

    await request(
      `/entity-forms/${form.id}/releases/${baselineRelease.id}/activate`,
      { method: 'POST' }
    )
    const runtimeRolledBack = await request(`/entity-form-resolve/new-data/${entityCode}`)
    assert(
      runtimeLabel(runtimeRolledBack, selected.id, originalProps.fieldCode) === originalLabel,
      '激活历史版本后运行时未恢复旧标签'
    )

    const latestNodes = await request(`/entity-forms/${form.id}/nodes`)
    const latestSelected = latestNodes.find(node => node.id === selected.id)
    await request(`/entity-forms/${form.id}/nodes/${selected.id}`, {
      method: 'PATCH',
      body: JSON.stringify({
        expectedRevision: latestSelected.revision,
        props: originalProps
      })
    })
    draftChanged = false

    const restoredDiff = await request(`/entity-forms/${form.id}/diff`)
    assert(!restoredDiff.changed, '恢复草稿并回滚后 diff 应重新为 false')

    return {
      formId: form.id,
      entityCode,
      nodeId: selected.id,
      baselineReleaseId: baselineRelease.id,
      publishedReleaseId: published.id,
      checks: [
        'initial-diff-clean',
        'single-node-isolation',
        'stale-revision-409',
        'draft-does-not-affect-runtime',
        'publish-affects-runtime',
        'historical-activate-rolls-back-runtime',
        'draft-restored'
      ]
    }
  } finally {
    const releases = await request(`/entity-forms/${form.id}/releases`).catch(() => [])
    if (!releases.some(release =>
      release.id === baselineRelease.id && release.status === 'ACTIVE'
    )) {
      await request(
        `/entity-forms/${form.id}/releases/${baselineRelease.id}/activate`,
        { method: 'POST' }
      ).catch(() => {})
    }
    if (draftChanged) {
      const nodes = await request(`/entity-forms/${form.id}/nodes`).catch(() => [])
      const current = nodes.find(node => node.id === selected.id)
      if (current) {
        await request(`/entity-forms/${form.id}/nodes/${selected.id}`, {
          method: 'PATCH',
          body: JSON.stringify({
            expectedRevision: current.revision,
            props: originalProps
          })
        }).catch(() => {})
      }
    }
  }
}

function stableOtherFields(fields, selectedId) {
  return JSON.stringify((fields || []).filter(field => field.id !== selectedId))
}

function stableOtherActions(actions, selectedId) {
  return JSON.stringify((actions || []).filter(action => action.id !== selectedId))
}

function runtimeListFieldName(schema, selected) {
  const field = (schema?.fields || []).find(item =>
    item.id === selected.id || item.fieldCode === selected.fieldCode
  )
  return field?.fieldName
}

function runtimeActionLabel(schema, selected) {
  const action = (schema?.toolbarConfig || []).find(item =>
    item.id === selected.id || item.key === selected.key
  )
  return action?.label
}

async function patchListField(listId, field, expectedRevision, fieldName) {
  return request(`/entity-list-config/${listId}/fields/${field.id}`, {
    method: 'PATCH',
    body: JSON.stringify({
      expectedRevision,
      field: { fieldName }
    })
  })
}

async function patchListAction(listId, action, expectedRevision, buttonLabel) {
  return request(`/entity-list-config/${listId}/actions/${action.id}`, {
    method: 'PATCH',
    body: JSON.stringify({
      expectedRevision,
      buttonLabel
    })
  })
}

async function patchListScene(listId, scene, expectedRevision, sortOrder) {
  return request(`/entity-list-config/${listId}/scenes/${scene.id}`, {
    method: 'PATCH',
    body: JSON.stringify({
      expectedRevision,
      sortOrder
    })
  })
}

async function acceptList(form) {
  const lists = await request(`/entity-list-config/entity/${form.entity.id}`)
  const list = preferredListId
    ? lists.find(item => item.id === preferredListId)
    : lists.find(item => item.activeReleaseId)
  assert(list, '未找到可用于真实验收的已发布列表')

  const configBefore = await request(`/entity-list-config/${list.id}`)
  const field = configBefore.fields?.find(item => item.id && item.revision)
  const action = configBefore.toolbarConfig?.find(item => item.id && item.revision)
  const scenesBefore = await request(`/entity-list-config/${list.id}/scenes`)
  const scene = scenesBefore.find(item => item.sceneCode === 'DIALOG') || scenesBefore[0]
  assert(field, '验收列表没有可修改的列')
  assert(action, '验收列表没有可修改的工具栏按钮')
  assert(scene, '验收列表没有可修改的允许场景')

  const releasesBefore = await request(`/entity-list-config/${list.id}/releases`)
  const baselineRelease = releasesBefore.find(release => release.status === 'ACTIVE')
  assert(baselineRelease, '验收列表没有激活发布版本')

  const originalFieldName = field.fieldName
  const originalActionLabel = action.label
  const originalSceneSort = scene.sortOrder
  const testSuffix = `单项验收-${Date.now()}`
  const testFieldName = `${originalFieldName}-${testSuffix}`
  const testActionLabel = `${originalActionLabel}-${testSuffix}`
  const testSceneSort = originalSceneSort + 100
  const otherFieldsBefore = stableOtherFields(configBefore.fields, field.id)
  const otherActionsBefore = stableOtherActions(configBefore.toolbarConfig, action.id)
  let draftChanged = false
  let publishedRelease = null

  try {
    const initialDiff = await request(`/entity-list-config/${list.id}/diff`)
    assert(!initialDiff.changed, `列表初始草稿不应存在差异: ${JSON.stringify(initialDiff)}`)

    const runtimeBefore = await request(
      `/entity-lists/${configBefore.entityCode}/${configBefore.listKey}/schema?scene=PAGE`
    )
    assert(
      runtimeListFieldName(runtimeBefore, field) === originalFieldName,
      '列表初始运行时列名与激活版本不一致'
    )
    assert(
      runtimeActionLabel(runtimeBefore, action) === originalActionLabel,
      '列表初始运行时按钮名与激活版本不一致'
    )

    const patchedField = await patchListField(
      list.id,
      field,
      field.revision,
      testFieldName
    )
    draftChanged = true
    assert(patchedField.revision === field.revision + 1, '单列保存未递增 revision')

    const patchedAction = await patchListAction(
      list.id,
      action,
      action.revision,
      testActionLabel
    )
    assert(patchedAction.revision === action.revision + 1, '单按钮保存未递增 revision')

    const patchedScene = await patchListScene(
      list.id,
      scene,
      scene.revision,
      testSceneSort
    )
    assert(patchedScene.revision === scene.revision + 1, '单场景保存未递增 revision')

    const configAfterPatch = await request(`/entity-list-config/${list.id}`)
    const scenesAfterPatch = await request(`/entity-list-config/${list.id}/scenes`)
    assert(
      stableOtherFields(configAfterPatch.fields, field.id) === otherFieldsBefore,
      '单列保存修改了其他列'
    )
    assert(
      stableOtherActions(configAfterPatch.toolbarConfig, action.id) === otherActionsBefore,
      '单按钮保存修改了其他按钮'
    )
    assert(
      scenesAfterPatch
        .filter(item => item.id !== scene.id)
        .every(item => {
          const before = scenesBefore.find(original => original.id === item.id)
          return before
            && before.sceneCode === item.sceneCode
            && before.sortOrder === item.sortOrder
            && before.revision === item.revision
        }),
      '单场景保存修改了其他场景'
    )

    await requestConflict(`/entity-list-config/${list.id}/fields/${field.id}`, {
      expectedRevision: field.revision,
      field: { fieldName: `${testFieldName}-冲突写入` }
    })
    await requestConflict(`/entity-list-config/${list.id}/actions/${action.id}`, {
      expectedRevision: action.revision,
      buttonLabel: `${testActionLabel}-冲突写入`
    })
    await requestConflict(`/entity-list-config/${list.id}/scenes/${scene.id}`, {
      expectedRevision: scene.revision,
      sortOrder: testSceneSort + 1
    })

    const runtimeDraft = await request(
      `/entity-lists/${configBefore.entityCode}/${configBefore.listKey}/schema?scene=PAGE`
    )
    assert(
      runtimeListFieldName(runtimeDraft, field) === originalFieldName,
      '列表仅保存草稿后运行时列名不应变化'
    )
    assert(
      runtimeActionLabel(runtimeDraft, action) === originalActionLabel,
      '列表仅保存草稿后运行时按钮名不应变化'
    )

    const draftDiff = await request(`/entity-list-config/${list.id}/diff`)
    assert(draftDiff.changed, '列表单项修改后 diff 应标记为 changed')
    assert(draftDiff.changedSections.includes('list'), '列表 diff 应包含 list')

    publishedRelease = await request(`/entity-list-config/${list.id}/publish`, {
      method: 'POST',
      body: JSON.stringify({ description: '自动化真实列表单项发布验收' })
    })
    assert(publishedRelease.status === 'ACTIVE', '列表新发布版本未激活')
    assert(
      publishedRelease.version > baselineRelease.version,
      '列表发布版本号未递增'
    )
    const publishedSnapshot = JSON.parse(publishedRelease.snapshotDocument)
    const publishedScenes = publishedSnapshot.list?.allowedScenes || []
    assert(
      publishedScenes.at(-1) === scene.sceneCode,
      '发布快照未包含场景单项排序修改'
    )

    const runtimePublished = await request(
      `/entity-lists/${configBefore.entityCode}/${configBefore.listKey}/schema?scene=PAGE`
    )
    assert(
      runtimeListFieldName(runtimePublished, field) === testFieldName,
      '列表发布后运行时未读取新列名'
    )
    assert(
      runtimeActionLabel(runtimePublished, action) === testActionLabel,
      '列表发布后运行时未读取新按钮名'
    )

    await request(
      `/entity-list-config/${list.id}/releases/${baselineRelease.id}/activate`,
      { method: 'POST' }
    )
    const runtimeRolledBack = await request(
      `/entity-lists/${configBefore.entityCode}/${configBefore.listKey}/schema?scene=PAGE`
    )
    assert(
      runtimeListFieldName(runtimeRolledBack, field) === originalFieldName,
      '列表激活历史版本后未恢复旧列名'
    )
    assert(
      runtimeActionLabel(runtimeRolledBack, action) === originalActionLabel,
      '列表激活历史版本后未恢复旧按钮名'
    )

    const latestConfig = await request(`/entity-list-config/${list.id}`)
    const latestField = latestConfig.fields.find(item => item.id === field.id)
    const latestAction = latestConfig.toolbarConfig.find(item => item.id === action.id)
    const latestScenes = await request(`/entity-list-config/${list.id}/scenes`)
    const latestScene = latestScenes.find(item => item.id === scene.id)
    await patchListField(list.id, latestField, latestField.revision, originalFieldName)
    await patchListAction(list.id, latestAction, latestAction.revision, originalActionLabel)
    await patchListScene(list.id, latestScene, latestScene.revision, originalSceneSort)
    draftChanged = false

    const restoredDiff = await request(`/entity-list-config/${list.id}/diff`)
    assert(!restoredDiff.changed, '列表恢复草稿并回滚后 diff 应重新为 false')

    return {
      listId: list.id,
      entityCode: configBefore.entityCode,
      listKey: configBefore.listKey,
      fieldId: field.id,
      actionId: action.id,
      sceneId: scene.id,
      baselineReleaseId: baselineRelease.id,
      publishedReleaseId: publishedRelease.id,
      checks: [
        'initial-diff-clean',
        'single-field-isolation',
        'single-action-isolation',
        'single-scene-isolation',
        'field-action-scene-stale-revision-409',
        'draft-does-not-affect-runtime',
        'publish-affects-runtime',
        'published-snapshot-includes-scene-change',
        'historical-activate-rolls-back-runtime',
        'draft-restored'
      ]
    }
  } finally {
    const releases = await request(`/entity-list-config/${list.id}/releases`).catch(() => [])
    if (!releases.some(release =>
      release.id === baselineRelease.id && release.status === 'ACTIVE'
    )) {
      await request(
        `/entity-list-config/${list.id}/releases/${baselineRelease.id}/activate`,
        { method: 'POST' }
      ).catch(() => {})
    }
    if (draftChanged) {
      const latestConfig = await request(`/entity-list-config/${list.id}`).catch(() => null)
      const latestScenes = await request(`/entity-list-config/${list.id}/scenes`).catch(() => [])
      const latestField = latestConfig?.fields?.find(item => item.id === field.id)
      const latestAction = latestConfig?.toolbarConfig?.find(item => item.id === action.id)
      const latestScene = latestScenes.find(item => item.id === scene.id)
      if (latestField) {
        await patchListField(
          list.id,
          latestField,
          latestField.revision,
          originalFieldName
        ).catch(() => {})
      }
      if (latestAction) {
        await patchListAction(
          list.id,
          latestAction,
          latestAction.revision,
          originalActionLabel
        ).catch(() => {})
      }
      if (latestScene) {
        await patchListScene(
          list.id,
          latestScene,
          latestScene.revision,
          originalSceneSort
        ).catch(() => {})
      }
    }
  }
}

async function main() {
  const login = await request('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password })
  })
  token = login.token

  const forms = await request('/entity-form/list')
  const form = preferredFormId
    ? forms.find(item => item.id === preferredFormId)
    : forms.find(item =>
        item.isDefault &&
        item.activeReleaseId &&
        item.entity?.lifecycleMode === 'STANDALONE'
      )
  assert(form, '未找到可用于真实验收的已发布独立实体默认表单')
  console.log(
    `[real-ui-config] form=${form.id} entity=${form.entity?.entityCode || form.entityCode || '-'}`
  )

  const formAcceptance = await acceptForm(form)
  const listAcceptance = await acceptList(form)
  console.log(JSON.stringify({
    status: 'passed',
    form: formAcceptance,
    list: listAcceptance
  }, null, 2))
}

main().catch(error => {
  console.error(error.payload || error)
  process.exitCode = 1
})
