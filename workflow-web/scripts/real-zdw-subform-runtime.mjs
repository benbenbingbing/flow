import assert from 'node:assert/strict'
import {
  existsSync,
  mkdirSync,
  readFileSync,
  unlinkSync,
  writeFileSync
} from 'node:fs'
import path from 'node:path'

const apiBase = process.env.WORKFLOW_API_BASE || 'http://127.0.0.1:8080/api'
const credentialFile = process.env.TEST_CREDENTIAL_FILE
  || '/private/tmp/workflow-zdw-subform-runtime.credentials.json'
const evidenceDir = path.resolve('docs/zdw-subform-runtime-e2e')

assert.ok(existsSync(credentialFile), `缺少一次性凭据文件: ${credentialFile}`)
mkdirSync(evidenceDir, { recursive: true })

let token = ''
let createdParentId = null
let failureContext = {}

async function request(method, url, body) {
  const response = await fetch(`${apiBase}${url}`, {
    method,
    signal: AbortSignal.timeout(30000),
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body == null ? undefined : JSON.stringify(body)
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok
      || !payload
      || ![0, 200, '0', '200'].includes(payload.code)) {
    const error = new Error(
      payload?.message || `${method} ${url} failed: HTTP ${response.status}`
    )
    error.status = response.status
    error.errorCode = payload?.errorCode
    error.payload = payload
    throw error
  }
  return payload.data
}

function pageRecords(page) {
  return page?.records || page?.list || page?.rows || []
}

function recordData(record) {
  return {
    ...(record?.data || {}),
    id: record?.id,
    name: record?.name,
    code: record?.code,
    status: record?.status
  }
}

function relationRows(value) {
  if (Array.isArray(value)) return value
  return value && typeof value === 'object' ? [value] : []
}

function childById(rows, id) {
  return rows.find(row => String(row.id) === String(id))
}

async function detail(id) {
  return request('GET', `/entity-data/entity/ZDWREQ/detail/${id}`)
}

async function childRowsBy(fieldCode, parentId) {
  const page = await request(
    'GET',
    `/entity-data/entity/ZDWITEM/list-with-config`
      + `?listKey=list001&pageNum=1&pageSize=100`
      + `&${encodeURIComponent(fieldCode)}=${encodeURIComponent(parentId)}`
      + `&${encodeURIComponent(`${fieldCode}_op`)}=EQ`
  )
  return pageRecords(page)
}

async function deleteParent() {
  if (!createdParentId) return
  await request(
    'POST',
    `/entity-data/entity/ZDWREQ/detail/${createdParentId}/delete`
  )
}

async function main() {
  const credentials = JSON.parse(readFileSync(credentialFile, 'utf8'))
  unlinkSync(credentialFile)
  const login = await request('POST', '/auth/login', credentials)
  token = login.token

  const parent = await request('GET', '/entity/code/ZDWREQ')
  const child = await request('GET', '/entity/code/ZDWITEM')
  const formsResponse = await request(
    'GET',
    `/entity-form/entity/${parent.id}`
  )
  const forms = Array.isArray(formsResponse)
    ? formsResponse
    : pageRecords(formsResponse)
  const parentForm = forms.find(form => form.formKey === 'form001')
  assert.ok(parentForm?.id, '未找到 ZDWREQ 的 form001')

  const marker = `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`
  const initialParentName = `子表单回归-${marker}`
  const explicitItemName = `显式子行-${marker}`
  const created = await request('POST', '/entity-data', {
    entityCode: parent.entityCode,
    name: initialParentName,
    formId: parentForm.id,
    startProcess: false,
    data: {
      name: initialParentName,
      contactEmail: `subform-${marker}@example.com`,
      expectedDate: '2026-08-28',
      reqSingleForm: {
        name: ''
      },
      reqItemForm: [
        {
          name: ''
        },
        {
          name: explicitItemName
        }
      ]
    }
  })
  createdParentId = created.id
  assert.ok(createdParentId, '新增父记录未返回 ID')

  const createdDetail = await detail(createdParentId)
  const createdData = recordData(createdDetail)
  const createdSingleRows = relationRows(createdData.reqSingleForm)
  const createdItemRows = relationRows(createdData.reqItemForm)
  failureContext.created = {
    parent: createdData,
    singleRows: createdSingleRows,
    itemRows: createdItemRows
  }

  assert.equal(createdSingleRows.length, 1, '一对一子表必须保存一条记录')
  assert.equal(createdItemRows.length, 2, '一对多子表必须保存两条记录')
  assert.equal(
    createdSingleRows[0].reqSingleId,
    createdParentId,
    '一对一子记录未回填父记录外键'
  )
  assert.equal(
    createdSingleRows[0].name,
    initialParentName,
    '一对一子表字段初始化未从 parent.data.name 取值'
  )
  assert.ok(
    createdItemRows.every(row => row.reqId === createdParentId),
    '一对多子记录未全部回填父记录外键'
  )
  const initializedItem = createdItemRows.find(
    row => row.name === initialParentName
  )
  const explicitItem = createdItemRows.find(
    row => row.name === explicitItemName
  )
  assert.ok(initializedItem, '未找到按父表参数初始化的一对多子记录')
  assert.ok(explicitItem, '未找到保留显式名称的一对多子记录')
  assert.equal(
    initializedItem.name,
    initialParentName,
    '一对多空名称未按参数契约初始化'
  )
  assert.equal(
    explicitItem.name,
    explicitItemName,
    '一对多显式值被字段初始化错误覆盖'
  )
  assert.ok(
    createdSingleRows[0].id
      && createdItemRows.every(row => row.id),
    '子记录未返回持久化 ID'
  )

  const singleListRows = await childRowsBy(
    'reqSingleId',
    createdParentId
  )
  const itemListRows = await childRowsBy(
    'reqId',
    createdParentId
  )
  assert.equal(singleListRows.length, 1, '一对一子记录列表查询数量不正确')
  assert.equal(itemListRows.length, 2, '一对多子记录列表查询数量不正确')

  const originalSingleId = createdSingleRows[0].id
  const keptItemId = initializedItem.id
  const removedItemId = explicitItem.id
  const updatedParentName = `子表单回归更新-${marker}`
  const updatedSingleName = `单条更新-${marker}`
  const updatedItemName = `保留子行更新-${marker}`
  const updated = await request(
    'POST',
    `/entity-data/entity/ZDWREQ/detail/${createdParentId}/update`,
    {
      formId: parentForm.id,
      startProcess: false,
      data: {
        name: updatedParentName,
        reqSingleForm: {
          ...createdSingleRows[0],
          name: updatedSingleName
        },
        reqItemForm: [
          {
            ...initializedItem,
            name: updatedItemName
          },
          {
            name: ''
          }
        ]
      }
    }
  )
  assert.equal(updated.id, createdParentId, '更新父记录返回了错误 ID')

  const updatedDetail = await detail(createdParentId)
  const updatedData = recordData(updatedDetail)
  const updatedSingleRows = relationRows(updatedData.reqSingleForm)
  const updatedItemRows = relationRows(updatedData.reqItemForm)
  failureContext.updated = {
    parent: updatedData,
    singleRows: updatedSingleRows,
    itemRows: updatedItemRows
  }

  assert.equal(updatedData.name, updatedParentName, '父记录名称未更新')
  assert.equal(updatedSingleRows.length, 1, '更新后一对一记录数量错误')
  assert.equal(
    updatedSingleRows[0].id,
    originalSingleId,
    '一对一更新错误地新建了记录'
  )
  assert.equal(
    updatedSingleRows[0].name,
    updatedSingleName,
    '一对一子记录字段未更新'
  )
  assert.equal(updatedItemRows.length, 2, '一对多增删改后数量错误')
  assert.equal(
    childById(updatedItemRows, keptItemId)?.name,
    updatedItemName,
    '保留的一对多子记录未按 ID 更新'
  )
  assert.equal(
    childById(updatedItemRows, removedItemId),
    undefined,
    '提交时移除的一对多子记录未逻辑删除'
  )
  const newItem = updatedItemRows.find(row => row.id !== keptItemId)
  assert.ok(newItem?.id, '一对多新增子记录未生成 ID')
  assert.equal(
    newItem.name,
    updatedParentName,
    '新增一对多子行未使用更新后的父记录参数初始化'
  )

  await request(
    'POST',
    `/entity-data/entity/ZDWREQ/detail/${createdParentId}/update`,
    {
      formId: parentForm.id,
      startProcess: false,
      data: {
        reqItemForm: []
      }
    }
  )
  const clearedDetail = await detail(createdParentId)
  const clearedData = recordData(clearedDetail)
  assert.deepEqual(
    relationRows(clearedData.reqItemForm),
    [],
    '提交空数组后，一对多子记录未清空'
  )
  assert.equal(
    (await childRowsBy('reqId', createdParentId)).length,
    0,
    '一对多清空后列表仍查询到有效子记录'
  )

  const singleIdBeforeDelete =
    relationRows(clearedData.reqSingleForm)[0]?.id
  assert.ok(singleIdBeforeDelete, '级联删除前一对一子记录不存在')
  await deleteParent()
  createdParentId = null

  const remainingSingle = await childRowsBy(
    'reqSingleId',
    created.id
  )
  assert.equal(
    remainingSingle.length,
    0,
    '删除父记录后，一对一子记录未按关系配置级联逻辑删除'
  )

  const evidence = {
    generatedAt: new Date().toISOString(),
    apiBase,
    result: 'PASS',
    entities: {
      parent: {
        id: parent.id,
        entityCode: parent.entityCode,
        entityName: parent.entityName
      },
      child: {
        id: child.id,
        entityCode: child.entityCode,
        entityName: child.entityName
      }
    },
    form: {
      id: parentForm.id,
      formKey: parentForm.formKey,
      activeReleaseId: parentForm.activeReleaseId
    },
    assertions: {
      oneToOneCreateAndForeignKey: true,
      oneToManyCreateAndForeignKey: true,
      emptyOnlyFieldInitialization: true,
      explicitValueNotOverwritten: true,
      detailRelationHydration: true,
      configuredListRelationQuery: true,
      childUpdateKeepsIdentity: true,
      omittedChildSoftDeleted: true,
      newChildInserted: true,
      emptyArrayClearsOneToMany: true,
      parentDeleteCascades: true
    },
    runtime: {
      createdParentId: created.id,
      originalSingleId,
      keptItemId,
      removedItemId,
      insertedItemId: newItem.id
    }
  }
  const evidencePath = path.join(evidenceDir, 'latest.json')
  writeFileSync(evidencePath, JSON.stringify(evidence, null, 2), {
    mode: 0o600
  })
  console.log(`PASS ${evidencePath}`)
}

main().catch(async error => {
  let cleanupError = null
  try {
    await deleteParent()
    createdParentId = null
  } catch (cleanupFailure) {
    cleanupError = {
      message: cleanupFailure.message,
      status: cleanupFailure.status,
      errorCode: cleanupFailure.errorCode
    }
  }
  const evidencePath = path.join(evidenceDir, 'latest-failed.json')
  writeFileSync(evidencePath, JSON.stringify({
    generatedAt: new Date().toISOString(),
    apiBase,
    result: 'FAIL',
    error: {
      message: error.message,
      status: error.status,
      errorCode: error.errorCode
    },
    cleanupError,
    failureContext
  }, null, 2), {
    mode: 0o600
  })
  console.error(error)
  process.exitCode = 1
})
