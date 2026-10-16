import test from 'node:test'
import assert from 'node:assert/strict'
import { columnCodecOptions, createScheme, createColumn, excelColumnLabel, importOptionDefaults, reorderColumns, schemePayload, splitSchemeForEditor, supportsOperation, validateScheme } from '../excelModel.js'

test('实体状态转换只开放给系统 status 导出，新选字段默认导出名称', () => {
  const status = { fieldCode: 'status', fieldName: '状态', fieldType: 'STRING', isSystem: true, editable: false }
  assert.deepEqual(createColumn(status, 'EXPORT').codec, { type: 'ENTITY_STATUS', exportMode: 'LABEL' })
  assert.ok(columnCodecOptions(status, 'EXPORT').some(([type, label]) => type === 'ENTITY_STATUS' && label === '实体状态'))
  for (const [field, operation] of [
    [status, 'IMPORT'],
    [{ ...status, fieldCode: 'processStatus' }, 'EXPORT'],
    [{ ...status, isSystem: false }, 'EXPORT'],
    [{ fieldCode: 'status', dictCode: 'business_status' }, 'EXPORT'],
    [{ fieldCode: 'name', isSystem: true }, 'EXPORT'],
    [undefined, 'EXPORT']
  ]) {
    assert.equal(columnCodecOptions(field, operation).some(([type]) => type === 'ENTITY_STATUS'), false)
    assert.notEqual(createColumn(field, operation).codec.type, 'ENTITY_STATUS')
  }
  assert.equal(createColumn({ fieldCode: 'status', dictCode: 'business_status' }, 'EXPORT').codec.type, 'DICT')
})

test('状态导出保存回显保留名称或代码选项，不自动修改旧文本规则', () => {
  const status = { fieldCode: 'status', fieldName: '状态', fieldType: 'STRING', isSystem: true }
  for (const codec of [
    { type: 'ENTITY_STATUS', exportMode: 'LABEL' },
    { type: 'ENTITY_STATUS', exportMode: 'CODE' },
    { type: 'STRING', trim: false, emptyToNull: false }
  ]) {
    const scheme = createScheme()
    scheme.schemeCode = 'status_export'; scheme.name = '状态导出'; scheme.importConfig.enabled = false
    scheme.columns = [{ ...createColumn(status, 'EXPORT'), codec }]
    const before = structuredClone(scheme)
    const editor = splitSchemeForEditor(scheme)
    assert.deepEqual(editor.exportColumns[0].codec, codec)
    assert.deepEqual(editor.importColumns, [])
    assert.equal(validateScheme(editor), '')
    const payload = schemePayload(editor)
    assert.deepEqual(payload.columns[0].codec, codec)
    assert.deepEqual(splitSchemeForEditor(payload).exportColumns[0].codec, codec)
    assert.deepEqual(scheme, before)
  }
})

test('字段生成不带内部系统及复杂关系字段，编码保持文本、金额保精度', () => {
  const scheme = createScheme([{ fieldCode: 'id', isSystem: true }, { fieldCode: 'file', fieldType: 'FILE' }, { fieldCode: 'code', fieldName: '编码', fieldType: 'STRING', isRequired: true }, { fieldCode: 'amount', fieldName: '金额', fieldType: 'DECIMAL' }, { fieldCode: 'active', fieldType: 'BOOLEAN' }])
  assert.deepEqual(scheme.columns.map(c => c.fieldCode), ['code','amount','active'])
  assert.equal(scheme.columns[0].codec.type, 'STRING')
  assert.equal(scheme.columns[1].codec.roundingMode, 'UNNECESSARY')
  assert.equal(scheme.columns[1].codec.writeAsText, true)
  assert.equal(scheme.columns[2].codec.type, 'BOOLEAN')
})
test('只有默认系统业务字段的实体仍预填名称和编码，导入导出分别保留预置', () => {
  const fields = [
    { fieldCode: 'id', fieldName: 'ID', fieldType: 'STRING', isSystem: true, editable: false },
    { fieldCode: 'name', fieldName: '数据名称', fieldType: 'STRING', isSystem: true, editable: true, isRequired: true },
    { fieldCode: 'code', fieldName: '数据编码', fieldType: 'STRING', isSystem: true, editable: true, isUnique: true },
    { fieldCode: 'status', fieldName: '状态', fieldType: 'STRING', isSystem: true, editable: false },
    { fieldCode: 'details', fieldType: 'SUB_FORM' }
  ]
  const editor = splitSchemeForEditor(createScheme(fields))
  for (const columns of [editor.importColumns, editor.exportColumns]) {
    assert.deepEqual(columns.map(c => c.fieldCode), ['name', 'code'])
    assert.deepEqual(columns.map(c => c.header), ['数据名称', '数据编码'])
    assert.ok(columns.every(c => c.codec.type === 'STRING' && c.codec.trim))
  }
  assert.equal(editor.importColumns[0].requiredHeader, true)
  assert.equal(editor.importColumns[0].validation.required, true)
  assert.equal(editor.importColumns[1].validation.uniqueInFile, true)
  assert.equal(editor.exportColumns[0].validation.required, undefined)
})
test('预置遵循只读属性并按字段类型填充日期、金额精度和字典转换', () => {
  const editor = splitSchemeForEditor(createScheme([
    { fieldCode: 'readonly', fieldType: 'STRING', editable: false },
    { fieldCode: 'hidden', fieldType: 'STRING', runtimeReadable: false },
    { fieldCode: 'date', fieldType: 'DATE' },
    { fieldCode: 'at', fieldType: 'DATETIME' },
    { fieldCode: 'amount', fieldType: 'DECIMAL', fieldPrecision: 4 },
    { fieldCode: 'whole', fieldType: 'DECIMAL', fieldPrecision: 0 },
    { fieldCode: 'category', fieldType: 'SELECT', dictType: 'categories' }
  ]))
  assert.deepEqual(editor.importColumns.map(c => c.fieldCode), ['date', 'at', 'amount', 'whole', 'category'])
  assert.deepEqual(editor.exportColumns.map(c => c.fieldCode), ['readonly', 'date', 'at', 'amount', 'whole', 'category'])
  const input = Object.fromEntries(editor.importColumns.map(c => [c.fieldCode, c.codec]))
  const output = Object.fromEntries(editor.exportColumns.map(c => [c.fieldCode, c.codec]))
  assert.equal(input.date.format, 'yyyy-MM-dd')
  assert.equal(input.at.format, 'yyyy-MM-dd HH:mm:ss')
  assert.equal(output.at.zoneId, 'Asia/Shanghai')
  assert.equal(input.amount.scale, 4)
  assert.equal(output.amount.scale, 4)
  assert.equal(input.whole.scale, 0)
  assert.equal(output.amount.writeAsText, true)
  assert.equal(input.amount.roundingMode, 'UNNECESSARY')
  assert.equal(input.category.importMode, 'LABEL_OR_CODE')
  assert.equal(output.category.exportMode, 'LABEL')
})
test('默认选项只使用支持方向的已发布方案，不混入草稿能力', () => {
  const scheme = { status: 'ACTIVE', draftConfig: { importConfig: { enabled: true } }, publishedConfig: { exportConfig: { enabled: true } } }
  assert.equal(supportsOperation(scheme, 'IMPORT'), false)
  assert.equal(supportsOperation(scheme, 'EXPORT'), true)
  assert.equal(supportsOperation({ ...scheme, status: 'DISABLED' }, 'EXPORT'), false)
})
test('新增和按字段生成方案默认直接插入表单未配置字段，额外表头仍拒绝', () => {
  for (const fields of [[], [{ fieldCode: 'name', fieldName: '数据名称', fieldType: 'STRING' }]]) {
    const scheme = createScheme(fields)
    assert.equal(scheme.importConfig.unknownColumnPolicy, 'DIRECT')
    assert.equal(scheme.file.unknownColumnPolicy, 'REJECT')
    assert.equal(splitSchemeForEditor(scheme).importConfig.unknownColumnPolicy, 'DIRECT')
  }
})
test('历史方案缺少表单未知列策略时默认直接插入，保留文件策略及其他业务配置', () => {
  for (const filePolicy of ['REJECT', 'IGNORE']) {
    const source = completeScheme()
    delete source.importConfig.unknownColumnPolicy
    source.file.unknownColumnPolicy = filePolicy
    source.importConfig.maxRows = 200
    source.importConfig.futureOption = { values: ['keep'] }
    const before = structuredClone(source)
    const editor = splitSchemeForEditor(JSON.stringify(source))
    assert.equal(editor.importConfig.unknownColumnPolicy, 'DIRECT')
    const payload = schemePayload(editor)
    assert.deepEqual(payload.importConfig, { ...source.importConfig, unknownColumnPolicy: 'DIRECT' })
    assert.deepEqual(payload.file, source.file)
    assert.deepEqual(payload.exportConfig, source.exportConfig)
    assert.equal(payload.schemaVersion, source.schemaVersion)
    assert.deepEqual(source, before)
  }
})
test('表单未知列三种选择分别保存并回显，不改动文件额外表头规则', () => {
  const editor = splitSchemeForEditor(completeScheme())
  editor.file.unknownColumnPolicy = 'IGNORE'
  for (const policy of ['DIRECT', 'REJECT', 'IGNORE']) {
    editor.importConfig.unknownColumnPolicy = policy
    const payload = schemePayload(editor)
    assert.equal(payload.importConfig.unknownColumnPolicy, policy)
    assert.equal(payload.file.unknownColumnPolicy, 'IGNORE')
    const reopened = splitSchemeForEditor(payload)
    assert.equal(reopened.importConfig.unknownColumnPolicy, policy)
    assert.deepEqual(schemePayload(reopened), payload)
  }
})
test('历史整批配置仍拒绝标题别名歧义和字段重复', () => {
  const scheme = createScheme([{ fieldCode: 'code', fieldName: '编码' }, { fieldCode: 'name', fieldName: '名称' }])
  scheme.schemeCode = 'basic'; scheme.name = '基础'; scheme.importConfig.writeFormKey = 'create'; scheme.importConfig.transactionPolicy = 'ATOMIC_ALL'
  assert.equal(validateScheme(scheme), '')
  scheme.columns[1].headerAliases = ['编码']
  assert.match(validateScheme(scheme), /重复/)
  scheme.columns[1].headerAliases = []; scheme.columns[1].fieldCode = 'code'
  assert.match(validateScheme(scheme), /重复映射/)
})

test('日期时间保留时间类型，清空可选范围不发送空数字，草稿允许待完善', () => {
  assert.equal(createColumn({ fieldCode: 'at', fieldType: 'DATETIME' }).codec.type, 'DATETIME')
  const draft = createScheme(); draft.schemeCode = 'draft'; draft.name = '草稿'
  assert.equal(validateScheme(draft, { draft: true }), '')
  assert.match(validateScheme(draft), /写入表单/)
  draft.columns = [{ validation: { min: '', max: '0' } }]
  assert.deepEqual(schemePayload(draft).columns[0].validation, { max: '0' })
})

function completeScheme() {
  const scheme = createScheme([
    { fieldCode: 'amount', fieldName: '金额', fieldType: 'DECIMAL' },
    { fieldCode: 'status', fieldName: '状态', dictCode: 'status' }
  ])
  scheme.schemeCode = 'separate'; scheme.name = '独立配置'; scheme.importConfig.writeFormKey = 'create'
  return scheme
}

test('历史双向列拆分后标题、转换规则、校验和顺序各自独立，原配置保持不变', () => {
  const source = completeScheme()
  const before = structuredClone(source)
  const editor = splitSchemeForEditor(source)
  assert.equal('columns' in editor, false)
  assert.deepEqual(editor.importColumns.map(c => c.fieldCode), ['amount', 'status'])
  assert.deepEqual(editor.exportColumns.map(c => c.fieldCode), ['amount', 'status'])
  assert.notStrictEqual(editor.importColumns[0], editor.exportColumns[0])
  assert.notStrictEqual(editor.importColumns[0].codec, editor.exportColumns[0].codec)
  editor.importColumns[0].header = '输入金额'
  editor.importColumns[0].headerAliases.push('历史金额')
  editor.importColumns[0].validation.required = true
  editor.importColumns[1].codec.importMode = 'CODE'
  editor.exportColumns[0].codec.scale = 4
  editor.exportColumns.reverse()
  assert.equal(editor.exportColumns[1].header, '金额')
  assert.deepEqual(editor.exportColumns[1].headerAliases, [])
  assert.equal(editor.exportColumns[1].validation.required, undefined)
  assert.equal(editor.exportColumns[0].codec.importMode, undefined)
  assert.equal(editor.importColumns[0].codec.scale, 2)
  assert.deepEqual(editor.importColumns.map(c => c.fieldCode), ['amount', 'status'])
  assert.deepEqual(source, before)
})

test('合回v1时保留两边顺序、生成唯一key并在反复编辑保存后保持身份', () => {
  const source = completeScheme()
  // 旧方案中已有类似派生名称，不能被拆分列抢占。
  source.columns.push({ ...createColumn({ fieldCode: 'note', fieldName: '说明' }), key: 'amount_export', importEnabled: false })
  const editor = splitSchemeForEditor(source)
  editor.exportColumns.reverse()
  const before = structuredClone(editor)
  const payload = schemePayload(editor)
  assert.equal(payload.schemaVersion, 1)
  assert.equal('importColumns' in payload, false)
  assert.equal('exportColumns' in payload, false)
  assert.equal('inactiveColumns' in payload, false)
  assert.equal(payload.columns.length, 5)
  assert.equal(new Set(payload.columns.map(c => c.key)).size, payload.columns.length)
  assert.ok(payload.columns.every(c => c.key))
  assert.deepEqual(payload.columns.filter(c => c.importEnabled).map(c => c.fieldCode), ['amount', 'status'])
  assert.deepEqual(payload.columns.filter(c => c.exportEnabled).map(c => c.fieldCode), ['note', 'status', 'amount'])
  assert.equal(payload.columns.find(c => c.fieldCode === 'note').key, 'amount_export')
  assert.deepEqual(payload.columns.map(c => [c.importEnabled, c.exportEnabled]), [[true, false], [true, false], [false, true], [false, true], [false, true]])
  assert.deepEqual(schemePayload(splitSchemeForEditor(payload)), payload)
  assert.deepEqual(schemePayload(editor), payload)
  assert.deepEqual(editor, before)
})

test('分向数组定义列方向，空白及重复key修复不会覆盖已有身份', () => {
  const editor = splitSchemeForEditor(completeScheme())
  const added = createColumn({ fieldCode: 'note', fieldName: '说明' })
  added.key = ''
  editor.importColumns.push(added)
  editor.exportColumns.push(structuredClone(added))
  // 字段选择仍可产生默认双向标志，传输边界须以所在页签为准。
  editor.importColumns[0].importEnabled = false
  editor.importColumns[0].exportEnabled = true
  const payload = schemePayload(editor)
  assert.equal(new Set(payload.columns.map(c => c.key)).size, payload.columns.length)
  assert.ok(payload.columns.every(c => c.key.trim()))
  assert.ok(payload.columns.slice(0, 3).every(c => c.importEnabled && !c.exportEnabled))
  assert.ok(payload.columns.slice(3).every(c => !c.importEnabled && c.exportEnabled))
  assert.equal(editor.importColumns.at(-1).key, '')
  assert.deepEqual(schemePayload(splitSchemeForEditor(payload)), payload)
})

test('历史单向列保留当前方向设置，双禁用草稿列及未知设置完整保留', () => {
  const source = completeScheme()
  source.columns[0].exportEnabled = false
  source.columns[1].importEnabled = false
  const inactive = { ...createColumn({ fieldCode: 'paused', fieldName: '待完善' }), importEnabled: false, exportEnabled: false, futureRule: { values: ['keep'] } }
  source.columns.push(inactive)
  source.futureConfig = { mode: ['preserve'] }
  source.file.futureOption = 'supported-later'
  const before = structuredClone(source)
  const expected = structuredClone(source)
  delete expected.columns[0].codec.writeAsText
  delete expected.columns[1].codec.importMode
  expected.columns[1].validation = {}
  const editor = splitSchemeForEditor(JSON.stringify(source))
  assert.deepEqual(editor.importColumns.map(c => c.fieldCode), ['amount'])
  assert.deepEqual(editor.exportColumns.map(c => c.fieldCode), ['status'])
  assert.deepEqual(editor.inactiveColumns, [inactive])
  assert.notStrictEqual(editor.inactiveColumns[0], inactive)
  assert.deepEqual(schemePayload(editor), expected)
  const secondEditor = splitSchemeForEditor(editor)
  secondEditor.inactiveColumns[0].futureRule.values.push('changed')
  secondEditor.futureConfig.mode.push('changed')
  assert.deepEqual(schemePayload(editor), expected)
  assert.deepEqual(source, before)
})

test('发布校验允许跨方向复用字段和标题，方向内仍拒绝重复', () => {
  const editor = splitSchemeForEditor(completeScheme())
  assert.equal(validateScheme(editor), '')
  assert.equal(validateScheme(schemePayload(editor)), '')
  editor.importColumns[1].headerAliases = ['金额']
  assert.equal(validateScheme(editor), '', '新表头模式不再使用历史别名')
  editor.importColumns[1].header = '金额'
  assert.match(validateScheme(editor), /导入标题.*重复/)
  editor.importColumns[1].header = '状态'
  editor.importColumns[1].headerAliases = []
  editor.importColumns[1].fieldCode = 'amount'
  assert.match(validateScheme(editor), /导入字段.*重复映射/)
  editor.importColumns[1].fieldCode = 'status'
  editor.exportColumns[1].fieldCode = 'amount'
  assert.match(validateScheme(editor), /导出字段.*重复映射/)
  editor.exportColumns[1].fieldCode = 'status'
  editor.exportColumns[1].header = '金额'
  assert.match(validateScheme(editor), /导出标题.*重复/)
  editor.exportColumns[1].header = '导出状态'
  editor.exportColumns[1].headerAliases = ['金额']
  assert.equal(validateScheme(editor), '')
})

test('发布时已启用方向必须有映射，清空一个方向不恢复旧列且可保存草稿', () => {
  const editor = splitSchemeForEditor(completeScheme())
  editor.exportColumns = []
  assert.match(validateScheme(editor), /导出列映射/)
  assert.equal(validateScheme(editor, { draft: true }), '')
  editor.exportConfig.enabled = false
  assert.equal(validateScheme(editor), '')
  const payload = schemePayload(editor)
  assert.equal(payload.columns.length, 2)
  assert.ok(payload.columns.every(c => !c.exportEnabled))
  assert.deepEqual(splitSchemeForEditor(payload).exportColumns, [])
})

test('旧columns调用方式保留双向标志和key，清空校验规则只影响返回副本', () => {
  const source = completeScheme()
  source.columns[0].validation = { min: '', max: '0', pattern: '', required: false }
  source.futureConfig = { option: ['preserve'] }
  const before = structuredClone(source)
  const payload = schemePayload(source)
  assert.deepEqual(payload.columns.map(c => c.key), ['amount', 'status'])
  assert.ok(payload.columns.every(c => c.importEnabled && c.exportEnabled))
  assert.deepEqual(payload.columns[0].validation, { max: '0', required: false })
  payload.futureConfig.option.push('changed')
  assert.deepEqual(source, before)
  const splitPayload = schemePayload(splitSchemeForEditor(source))
  assert.deepEqual(splitPayload.columns.filter(c => c.fieldCode === 'amount').map(c => c.validation), [{ max: '0', required: false }, {}])
})

test('旧双向草稿的坏导入规则仅留在导入编辑区，修复后导出不残留隐藏错误', () => {
  const source = completeScheme()
  const column = source.columns[0]
  column.requiredHeader = true
  column.headerAliases = ['历史金额']
  column.validation = { required: true, uniqueInFile: true, min: 10, max: 1, minLength: 3, maxLength: 1, pattern: '[', customCheck: { option: ['keep'] } }
  column.codec = { ...column.codec, readPatterns: ["invalid'"], importMode: 'INVALID', allowThousands: false, currencySymbol: '¥', customCodec: { option: ['keep'] } }
  const before = structuredClone(source)
  const editor = splitSchemeForEditor(source)
  assert.deepEqual(editor.importColumns[0].validation, column.validation)
  assert.deepEqual(editor.importColumns[0].headerAliases, ['历史金额'])
  assert.equal(editor.importColumns[0].requiredHeader, true)
  assert.deepEqual(editor.exportColumns[0].headerAliases, [])
  assert.equal(editor.exportColumns[0].requiredHeader, false)
  assert.deepEqual(editor.exportColumns[0].validation, { customCheck: { option: ['keep'] } })
  assert.deepEqual(editor.exportColumns[0].codec, { type: 'AMOUNT', scale: 2, roundingMode: 'UNNECESSARY', writeAsText: true, customCodec: { option: ['keep'] } })

  // 修复导入区中的规则后，发送的导出副本无需再处理任何隐藏的导入错误。
  editor.importColumns[0].validation = { required: true, min: 1, max: 10 }
  editor.importColumns[0].codec = { type: 'AMOUNT', scale: 2, roundingMode: 'UNNECESSARY', allowThousands: false, currencySymbol: '¥' }
  const payload = schemePayload(editor)
  const imported = payload.columns.find(c => c.importEnabled && c.fieldCode === 'amount')
  const exported = payload.columns.find(c => c.exportEnabled && c.fieldCode === 'amount')
  assert.deepEqual(imported.validation, { required: true, min: 1, max: 10 })
  assert.deepEqual(exported.validation, { customCheck: { option: ['keep'] } })
  assert.equal(exported.codec.importMode, undefined)
  assert.equal(exported.codec.readPatterns, undefined)
  assert.deepEqual(schemePayload(splitSchemeForEditor(payload)), payload)
  assert.deepEqual(source, before)
})

test('导出专用选项不进入导入副本，公共格式与未知扩展保持独立', () => {
  const source = completeScheme()
  source.columns[0].codec = {
    type: 'AMOUNT', format: 'shared-format', scale: 4, roundingMode: 'HALF_UP', trim: false, emptyToNull: false,
    exportMode: 'INVALID', writeAsText: false, zoneId: 'invalid-zone', customCodec: { options: ['keep'] }
  }
  const before = structuredClone(source)
  const editor = splitSchemeForEditor(source)
  assert.deepEqual(editor.importColumns[0].codec, {
    type: 'AMOUNT', format: 'shared-format', scale: 4, roundingMode: 'HALF_UP', trim: false, emptyToNull: false,
    customCodec: { options: ['keep'] }
  })
  assert.deepEqual(editor.exportColumns[0].codec, source.columns[0].codec)
  editor.exportColumns[0].codec.exportMode = 'CODE'
  editor.exportColumns[0].codec.zoneId = 'Asia/Shanghai'
  const payload = schemePayload(editor)
  assert.equal(payload.columns.find(c => c.importEnabled && c.fieldCode === 'amount').codec.zoneId, undefined)
  assert.deepEqual(schemePayload(splitSchemeForEditor(payload)), payload)
  editor.importColumns[0].codec.customCodec.options.push('changed')
  assert.deepEqual(editor.exportColumns[0].codec.customCodec.options, ['keep'])
  assert.deepEqual(source, before)
})

test('双禁用旧列不会按方向清除规则，已拆分编辑态新增列也会清理无关选项', () => {
  const source = completeScheme()
  source.columns = [{
    ...source.columns[0], importEnabled: false, exportEnabled: false,
    codec: { type: 'AMOUNT', importMode: 'INVALID', exportMode: 'INVALID', zoneId: 'invalid-zone', readPatterns: ['invalid'] },
    validation: { min: 10, max: 1, pattern: '[' }
  }]
  const editor = splitSchemeForEditor(source)
  assert.deepEqual(editor.inactiveColumns, source.columns)
  assert.deepEqual(schemePayload(editor), source)
  const added = createColumn({ fieldCode: 'amount', fieldName: '金额', fieldType: 'DECIMAL', isRequired: true })
  editor.importColumns.push(added)
  editor.exportColumns.push(structuredClone(added))
  const payload = schemePayload(editor)
  assert.equal(payload.columns[0].codec.writeAsText, undefined)
  assert.deepEqual(payload.columns[1].validation, {})
  assert.equal(payload.columns[1].requiredHeader, false)
  assert.deepEqual(schemePayload(splitSchemeForEditor(payload)), payload)
})


test('Excel 序号跨过 Z、AZ、ZZ 后按 Excel 列名连续排列', () => {
  for (const [index, label] of [[0, 'A'], [25, 'Z'], [26, 'AA'], [27, 'AB'], [51, 'AZ'], [52, 'BA'], [701, 'ZZ'], [702, 'AAA'], [16383, 'XFD']]) {
    assert.equal(excelColumnLabel(index), label)
  }
  for (const index of [-1, 0.5, NaN]) assert.equal(excelColumnLabel(index), '')
})

test('拖拽移动整列并保持另一方向不变，保存重开保留两边顺序与列身份', () => {
  const editor = splitSchemeForEditor(createScheme(Array.from({ length: 28 }, (_, index) => ({ fieldCode: `field${index}`, fieldName: `字段${index}` }))))
  const source = editor.importColumns[0]
  editor.importColumns = reorderColumns(editor.importColumns, 0, 27)
  assert.strictEqual(editor.importColumns[27], source)
  assert.equal(editor.importColumns[0].fieldCode, 'field1')
  assert.equal(editor.exportColumns[0].fieldCode, 'field0')
  editor.exportColumns = reorderColumns(editor.exportColumns, 27, 0)
  const payload = schemePayload(editor)
  const reopened = splitSchemeForEditor(payload)
  assert.deepEqual(reopened.importColumns.map(c => c.key), editor.importColumns.map(c => c.key))
  assert.deepEqual(reopened.exportColumns.map(c => c.key), editor.exportColumns.map(c => c.key))
  assert.equal(reopened.importColumns.at(-1).header, '字段0')
  assert.equal(reopened.exportColumns[0].header, '字段27')
  for (const [from, to] of [[-1, 0], [0, 28], [0, 0]]) assert.strictEqual(reorderColumns(editor.importColumns, from, to), editor.importColumns)
})

test('新增导入选项有明确默认，历史草稿升级为分批提交且不会把显式关闭重置', () => {
  const config = completeScheme()
  for (const [key, value] of Object.entries(importOptionDefaults)) assert.equal(config.importConfig[key], value)
  const legacy = structuredClone(config)
  for (const key of Object.keys(importOptionDefaults)) delete legacy.importConfig[key]
  legacy.importConfig.transactionPolicy = 'ATOMIC_ALL'
  const before = structuredClone(legacy)
  const editor = splitSchemeForEditor(legacy)
  assert.equal(editor.importConfig.transactionPolicy, 'BATCH')
  for (const [key, value] of Object.entries(importOptionDefaults)) assert.equal(editor.importConfig[key], value)
  assert.deepEqual(legacy, before)
  Object.assign(editor.importConfig, { validateHeader: false, stopAtEmptyRow: false, batchSize: 7, returnUnimported: false, precheckEnabled: false, failurePolicy: 'CONTINUE' })
  const payload = schemePayload(editor)
  assert.deepEqual(splitSchemeForEditor(payload).importConfig, editor.importConfig)
  assert.equal(payload.importConfig.batchSize, 7)
  assert.equal(payload.importConfig.failurePolicy, 'CONTINUE')
})

test('导入文件大小默认 10 MB，历史配置补齐默认且自定义值保存后保留', () => {
  const config = completeScheme()
  assert.equal(config.importConfig.maxFileSizeMb, 10)
  delete config.importConfig.maxFileSizeMb
  const editor = splitSchemeForEditor(config)
  assert.equal(editor.importConfig.maxFileSizeMb, 10)
  assert.equal(schemePayload(config).importConfig.maxFileSizeMb, 10)
  editor.importConfig.maxFileSizeMb = 20
  assert.equal(splitSchemeForEditor(schemePayload(editor)).importConfig.maxFileSizeMb, 20)
})

test('导入文件大小在保存草稿与发布时均校验整数及范围', () => {
  const config = completeScheme()
  for (const value of [0, -1, 51, 1.5, '10']) {
    config.importConfig.maxFileSizeMb = value
    for (const draft of [true, false]) assert.match(validateScheme(config, { draft }), /文件大小限制/)
  }
  for (const value of [1, 10, 50]) {
    config.importConfig.maxFileSizeMb = value
    assert.equal(validateScheme(config), '')
  }
})


test('历史坏别名在升级编辑后不阻止严格或按位置模式发布，保存仍保留原配置', () => {
  const source = completeScheme()
  source.importConfig.transactionPolicy = 'ATOMIC_ALL'
  source.columns[1].headerAliases = ['金额', '金额']
  assert.match(validateScheme(source), /重复/)
  const editor = splitSchemeForEditor(source)
  for (const enabled of [true, false]) {
    editor.importConfig.validateHeader = enabled
    assert.equal(validateScheme(editor), '')
    const payload = schemePayload(editor)
    assert.equal(payload.importConfig.transactionPolicy, 'BATCH')
    assert.deepEqual(payload.columns.find(c => c.importEnabled && c.fieldCode === 'status').headerAliases, ['金额', '金额'])
  }
  assert.equal(source.importConfig.transactionPolicy, 'ATOMIC_ALL')
})

test('单选多选实体默认匹配编码名称，独立保存自定义匹配属性和导出内容', () => {
  const fields = [{ fieldCode: 'customer', fieldName: '客户', fieldType: 'REFERENCE' }, { fieldCode: 'partners', fieldName: '合作方', fieldType: 'MULTI_REFERENCE' }]
  const scheme = createScheme(fields)
  scheme.schemeCode = 'references'; scheme.name = '关联实体'; scheme.importConfig.writeFormKey = 'create'
  const editor = splitSchemeForEditor(scheme)
  assert.deepEqual(editor.importColumns.map(column => column.codec.matchFields), [['code', 'name'], ['code', 'name']])
  assert.deepEqual(editor.exportColumns.map(column => column.codec.exportField), ['name', 'name'])
  assert.equal(editor.importColumns[1].codec.separator, ';')
  assert.deepEqual(columnCodecOptions(fields[0], 'IMPORT'), [['ENTITY_REFERENCE', '实体关联']])
  editor.importColumns[0].codec.matchFields = ['email', 'id']
  editor.exportColumns[0].codec.exportField = 'email'
  editor.importColumns[1].codec.separator = '\n'
  const saved = splitSchemeForEditor(schemePayload(editor))
  assert.deepEqual(saved.importColumns[0].codec.matchFields, ['email', 'id'])
  assert.equal(saved.exportColumns[0].codec.exportField, 'email')
  assert.equal(saved.importColumns[1].codec.separator, '\n')
  assert.equal(saved.exportColumns[1].codec.separator, ';')
  assert.equal(saved.importColumns[0].codec.exportField, undefined)
  assert.equal(saved.exportColumns[0].codec.matchFields, undefined)
  assert.equal(validateScheme(saved), '')
  saved.importColumns[0].codec.matchFields = []
  assert.match(validateScheme(saved), /请选择关联实体的导入匹配属性/)
})
