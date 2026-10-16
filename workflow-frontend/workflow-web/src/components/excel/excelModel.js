export const rowsOf = value => Array.isArray(value) ? value : value?.records || value?.list || []
export const configOf = value => !value ? {} : typeof value === 'string' ? JSON.parse(value) : value
/** 草稿能力不能用于判断已发布按钮是否可执行。 */
export function supportsOperation(scheme, operation) {
  return !['DISABLED', 'INACTIVE'].includes(scheme.status)
    && configOf(scheme.publishedConfig)[operation === 'EXPORT' ? 'exportConfig' : 'importConfig']?.enabled === true
}
// 缺省值也用于历史草稿回显；显式关闭的布尔值必须保留。
export const importOptionDefaults = Object.freeze({ maxFileSizeMb: 10, validateHeader: true, stopAtEmptyRow: true, batchSize: 100, returnUnimported: true, precheckEnabled: true, failurePolicy: 'STOP' })

/** 将零起始位置转换为 Excel 列序号，排序后按当前位置重新计算，不写入列身份。 */
export function excelColumnLabel(index) {
  if (!Number.isInteger(index) || index < 0) return ''
  let label = '', value = index + 1
  while (value > 0) {
    value -= 1
    label = String.fromCharCode(65 + value % 26) + label
    value = Math.floor(value / 26)
  }
  return label
}

/** 拖拽只移动本方向的同一列对象，嵌套格式与身份随列移动，不影响另一方向。 */
export function reorderColumns(columns, from, to) {
  if (!Number.isInteger(from) || !Number.isInteger(to) || from < 0 || to < 0 || from >= columns.length || to >= columns.length || from === to) return columns
  const result = [...columns]
  const [column] = result.splice(from, 1)
  result.splice(to, 0, column)
  return result
}

// 名称、编码虽然属于系统标准字段，但用于业务录入；其余系统字段由平台维护，不自动加入方案。
const businessSystemFields = new Set(['name', 'code'])

const baseCodecOptions = [['STRING', '文本'], ['DATE', '日期'], ['DATETIME', '日期时间'], ['AMOUNT', '金额'], ['DICT', '字典代码'], ['NUMBER', '数字'], ['BOOLEAN', '布尔值']]

/** 状态名称只由实体的系统 status 字段解释，避免把业务同名字段或流程状态套用到实体状态表。 */
export function supportsEntityStatusCodec(field, operation) {
  return operation === 'EXPORT' && field?.isSystem === true && field.fieldCode === 'status'
}

export const isEntityReference = field => ['REFERENCE', 'MULTI_REFERENCE'].includes(String(field?.fieldType).toUpperCase())

/** 编辑器按字段与方向提供转换选项；导入不能通过名称转换绕过系统状态维护规则。 */
export function columnCodecOptions(field, operation) {
  if (isEntityReference(field)) return [['ENTITY_REFERENCE', '实体关联']]
  return supportsEntityStatusCodec(field, operation) ? [...baseCodecOptions, ['ENTITY_STATUS', '实体状态']] : baseCodecOptions
}

/** 按实体字段预填列标题、转换规则和校验；新选中的系统状态导出名称，历史列不经过此初始化。 */
export function createColumn(field = {}, operation) {
  const type = String(field.fieldType || '').toUpperCase()
  const codec = isEntityReference(field) ? { type: 'ENTITY_REFERENCE', matchFields: ['code', 'name'], exportField: 'name', separator: ';' }
    : supportsEntityStatusCodec(field, operation) ? { type: 'ENTITY_STATUS', exportMode: 'LABEL' }
    : (field.dictType || field.dictCode) ? { type: 'DICT', importMode: 'LABEL_OR_CODE', exportMode: 'LABEL' }
    : ['DATE','DATETIME','TIMESTAMP'].includes(type) ? { type: type === 'DATE' ? 'DATE' : 'DATETIME', format: type === 'DATE' ? 'yyyy-MM-dd' : 'yyyy-MM-dd HH:mm:ss', zoneId: 'Asia/Shanghai' }
      : ['DECIMAL','MONEY','AMOUNT'].includes(type) ? { type: 'AMOUNT', scale: field.fieldPrecision ?? 2, roundingMode: 'UNNECESSARY', writeAsText: true }
        : ['INTEGER','INT','LONG','BIGINT','NUMBER','DOUBLE','FLOAT'].includes(type) ? { type: 'NUMBER' }
          : ['BOOLEAN','BOOL'].includes(type) ? { type: 'BOOLEAN' } : { type: 'STRING', trim: true }
  const writable = field.editable !== false && (!field.isSystem || businessSystemFields.has(field.fieldCode))
  return { key: field.fieldCode || '', fieldCode: field.fieldCode || '', header: field.fieldName || '', headerAliases: [], importEnabled: writable, exportEnabled: true, requiredHeader: Boolean(field.isRequired), width: 20, codec, validation: { required: Boolean(field.isRequired), uniqueInFile: Boolean(field.isUnique) } }
}
/** 生成待编辑草稿，保留业务名称/编码；内部系统列、不可读字段及附件/子表不自动映射，单选多选实体使用关联转换器。 */
export function createScheme(fields = []) {
  const columns = fields
    .filter(f => f.runtimeReadable !== false && (!f.isSystem || businessSystemFields.has(f.fieldCode)))
    .filter(f => !['FILE','IMAGE','ATTACHMENT','SUBFORM','SUB_FORM','SUB_LIST','ENTITY','USER','DEPT','DEPARTMENT','MULTI_SELECT','CHECKBOX'].includes(String(f.fieldType).toUpperCase()))
    .map(createColumn)
  return { schemaVersion: 1, schemeCode: '', name: '', description: '', file: { sheetName: '数据', headRowNumber: 1, unknownColumnPolicy: 'REJECT' }, importConfig: { enabled: true, writePolicy: 'CREATE_ONLY', transactionPolicy: 'BATCH', writeFormKey: '', unknownColumnPolicy: 'DIRECT', ...importOptionDefaults, startProcess: false, maxRows: 1000 }, exportConfig: { enabled: true, fileNamePattern: '数据_{yyyyMMddHHmmss}', sheetName: '数据' }, columns }
}

const cloneConfig = value => JSON.parse(JSON.stringify(value))
const hasSplitColumns = config => Array.isArray(config.importColumns) || Array.isArray(config.exportColumns)

/**
 * 数组归属决定列的方向；只移除已知属于另一方向的配置，保留公共格式与未知扩展。
 * 旧草稿的无效规则必须留在可编辑的方向，否则隐藏副本会继续阻止发布；停用列仍完整保留供恢复。
 */
function directionalColumns(columns, direction) {
  return (columns || []).map(column => {
    const copy = { ...column, importEnabled: direction === 'import', exportEnabled: direction === 'export' }
    if (direction === 'inactive') return copy
    copy.codec = { ...copy.codec }
    const unusedCodecOptions = direction === 'import'
      ? ['exportMode', 'writeAsText', 'zoneId', 'exportField']
      : ['readPatterns', 'importMode', 'allowThousands', 'currencySymbol', 'matchFields']
    for (const key of unusedCodecOptions) delete copy.codec[key]
    if (direction === 'export') {
      copy.headerAliases = []
      copy.requiredHeader = false
      copy.validation = { ...copy.validation }
      for (const key of ['required', 'uniqueInFile', 'min', 'max', 'minLength', 'maxLength', 'pattern']) delete copy.validation[key]
    }
    return copy
  })
}

/**
 * 为拆开的列分配全方案唯一身份，尽量保留已有 key。
 * 先保留全部旧 key，避免派生名称抢占后续列的身份；生成后写入编辑副本，使调整顺序和反复保存不改变身份。
 */
function ensureColumnKeys(config) {
  const groups = [['import', config.importColumns], ['export', config.exportColumns], ['inactive', config.inactiveColumns]]
  const validKey = column => typeof column.key === 'string' && column.key.trim() ? column.key : ''
  const reserved = new Set(groups.flatMap(([, columns]) => columns.map(validKey)).filter(Boolean))
  const used = new Set()
  for (const [direction, columns] of groups) {
    for (const column of columns) {
      let key = validKey(column)
      if (!key || used.has(key)) {
        const base = `${key || column.fieldCode || 'column'}_${direction}`
        key = base
        let suffix = 2
        while (reserved.has(key) || used.has(key)) key = `${base}_${suffix++}`
      }
      column.key = key
      used.add(key)
    }
  }
}

/**
 * 将 v1 方案或编辑态方案深拷贝为独立的导入、导出列；不修改输入或共享 codec、validation 等嵌套对象。
 * 双向旧列会分别出现在两个数组；双禁用旧列放入 inactiveColumns，保存时仍回写，避免打开旧草稿即丢配置。
 * columns 仅属于传输协议，编辑态不保留，防止保存时混入已移除的旧列。
 */
export function splitSchemeForEditor(config) {
  const copy = cloneConfig(configOf(config))
  // 表单未配置的已映射字段默认允许直接保存；旧 file 策略仍只处理文件额外表头，不能套用到此处。
  copy.importConfig = { ...copy.importConfig, unknownColumnPolicy: copy.importConfig?.unknownColumnPolicy ?? 'DIRECT' }
  for (const [key, value] of Object.entries(importOptionDefaults)) copy.importConfig[key] ??= value
  // 从此编辑器保存的新版本采用分批提交；历史发布版本仍由服务端按其原事务策略执行。
  copy.importConfig.transactionPolicy = 'BATCH'
  if (!hasSplitColumns(copy)) {
    const columns = copy.columns || []
    copy.importColumns = columns.filter(column => column.importEnabled).map(cloneConfig)
    copy.exportColumns = columns.filter(column => column.exportEnabled).map(cloneConfig)
    copy.inactiveColumns = columns.filter(column => !column.importEnabled && !column.exportEnabled).map(cloneConfig)
  }
  copy.importColumns = directionalColumns(copy.importColumns, 'import')
  copy.exportColumns = directionalColumns(copy.exportColumns, 'export')
  copy.inactiveColumns = directionalColumns(copy.inactiveColumns, 'inactive')
  delete copy.columns
  ensureColumnKeys(copy)
  return copy
}

/** 发布前按方向检查映射；同字段可分别配置导入和导出，草稿保存只要求方案身份完整。 */
export function validateScheme(config, { draft = false } = {}) {
  if (!/^[a-zA-Z][a-zA-Z0-9_]*$/.test(config.schemeCode)) return '方案编码须以字母开头，仅包含字母、数字和下划线'
  if (!config.name?.trim()) return '请填写方案名称'
  // 文件限制随方案发布并用于实际上传；草稿也拒绝无效范围，避免保存后回显与执行不一致。
  const maxFileSizeMb = config.importConfig?.maxFileSizeMb ?? importOptionDefaults.maxFileSizeMb
  if (!Number.isInteger(maxFileSizeMb) || maxFileSizeMb < 1 || maxFileSizeMb > 50) return '文件大小限制须为 1～50 MB 的整数'
  // 草稿允许暂缺写入表单或列；完整可执行性由发布入口统一核验。
  if (draft) return ''
  if (!config.importConfig.enabled && !config.exportConfig.enabled) return '请至少启用一个操作方向'
  if (config.importConfig.enabled && !config.importConfig.writeFormKey) return '请选择已发布写入表单'
  const { importColumns, exportColumns, inactiveColumns } = splitSchemeForEditor(config)
  if (!importColumns.length && !exportColumns.length && !inactiveColumns.length) return '请添加列映射'
  if (config.importConfig.enabled && !importColumns.length) return '请添加导入列映射'
  if (config.exportConfig.enabled && !exportColumns.length) return '请添加导出列映射'
  for (const c of [...importColumns, ...exportColumns, ...inactiveColumns]) {
    if (!c.fieldCode || !c.header?.trim()) return '每列必须指定字段与标题'
  }
  for (const [direction, columns] of [['导入', importColumns], ['导出', exportColumns]]) {
    const fields = new Set(), headers = new Set()
    for (const c of columns) {
      if (c.codec?.type === 'ENTITY_REFERENCE') {
        if (direction === '导入' && (!Array.isArray(c.codec.matchFields ?? ['code', 'name']) || !(c.codec.matchFields ?? ['code', 'name']).length)) return '请选择关联实体的导入匹配属性'
        if (direction === '导出' && !(c.codec.exportField ?? 'name')) return '请选择关联实体的导出内容'
      }
      if (fields.has(c.fieldCode)) return `${direction}字段 ${c.fieldCode} 重复映射`
      fields.add(c.fieldCode)
      // 新 BATCH 模式按严格标题/位置读取，不再解释历史别名；保留值但不能让隐藏旧规则阻止发布。
      // 直接校验尚未升级的 ATOMIC_ALL 文档时，仍使用原来的标题别名映射约束。
      const legacyAliases = direction === '导入' && config.importConfig.transactionPolicy === 'ATOMIC_ALL' ? c.headerAliases || [] : []
      for (const title of [c.header, ...legacyAliases]) {
        if (headers.has(title.trim())) return `${direction}标题或别名 ${title} 重复`
        headers.add(title.trim())
      }
    }
  }
  return ''
}

/**
 * 将独立方向的编辑列合回 v1 columns，保持各方向内部顺序；也兼容仍使用 columns 的调用方。
 * 可选规则输入清空表示不附加限制，不将空字符串当成数值传给服务端。返回副本并保留无关配置。
 */
export function schemePayload(config) {
  const copy = hasSplitColumns(config) ? splitSchemeForEditor(config) : cloneConfig(config)
  if (hasSplitColumns(copy)) {
    copy.columns = [...copy.importColumns, ...copy.exportColumns, ...copy.inactiveColumns]
    delete copy.importColumns
    delete copy.exportColumns
    delete copy.inactiveColumns
  }
  if (copy.importConfig) {
    copy.importConfig.transactionPolicy = 'BATCH'
    for (const [key, value] of Object.entries(importOptionDefaults)) copy.importConfig[key] ??= value
  }
  for (const column of copy.columns || []) {
    for (const key of ['min', 'max', 'pattern']) {
      if (column.validation?.[key] === '') delete column.validation[key]
    }
  }
  return copy
}
