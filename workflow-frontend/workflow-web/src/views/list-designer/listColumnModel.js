import { resolveInterfaceExtensionId } from '@/components/ui-config/interfaceExtensionModel'
import { supportsEntityFieldQuery } from '@/shared/list-query-policy'
import { isProcessStatusField, isSystemEntityStatusField } from '@flow/workflow-core/entity-status-runtime'

const queryOperators = [
  { label: '等于', value: 'EQ', pseudoSql: '字段 = 值' },
  { label: '不等于', value: 'NE', pseudoSql: '字段 <> 值' },
  { label: '包含', value: 'LIKE', pseudoSql: "字段 LIKE '%关键词%'" },
  { label: '不包含', value: 'NOT_LIKE', pseudoSql: "字段 NOT LIKE '%关键词%'" },
  { label: '大于', value: 'GT', pseudoSql: '字段 > 值' },
  { label: '大于等于', value: 'GE', pseudoSql: '字段 >= 值' },
  { label: '小于', value: 'LT', pseudoSql: '字段 < 值' },
  { label: '小于等于', value: 'LE', pseudoSql: '字段 <= 值' },
  { label: '范围', value: 'BETWEEN', pseudoSql: '字段 BETWEEN 起始值 AND 结束值' },
  { label: '包含于', value: 'IN', pseudoSql: '字段 IN (值1, 值2, …)' },
  { label: '不包含于', value: 'NOT_IN', pseudoSql: '字段 NOT IN (值1, 值2, …)' },
  { label: '为空', value: 'EMPTY', pseudoSql: '字段 IS NULL' },
  { label: '非空', value: 'NOT_EMPTY', pseudoSql: '字段 IS NOT NULL' }
]
const systemQueryOperators = new Set(['EQ', 'NE', 'LIKE', 'IN', 'BETWEEN', 'GT', 'GE', 'LT', 'LE'])
const singleChoiceFieldTypes = new Set(['SELECT', 'RADIO'])

/** 单值选项字段可在查询区复用多选下拉，查询值集合用于匹配每条记录上的单个值。 */
export function supportsMultiSelectQuery(column, originField = column) {
  const fieldType = String(originField?.fieldType || '').toUpperCase()
  return supportsEntityFieldQuery(column) && (
    singleChoiceFieldTypes.has(fieldType)
    || (fieldType === 'STRING' && !!originField.dictType)
    || isSystemEntityStatusField(originField)
    || isProcessStatusField(originField)
  )
}

/** 仅放宽列表查询控件的兼容性，表单设计器继续使用扩展清单声明的字段类型。 */
export function listQueryComponentOptions(options, column, originField = column) {
  const fieldType = String(originField?.fieldType || '').toUpperCase()
  return options.filter(option => {
    const supported = option.supportedFieldTypes || []
    return supported.length === 0
      || supported.some(type => String(type).toUpperCase() === fieldType)
      || (option.value === 'select_multiple' && supportsMultiSelectQuery(column, originField))
  })
}

/** 多选筛选单值字段时只提供集合比较，系统实体仅支持其中的 IN。 */
export function listQueryOperatorOptions(systemEntity, multiSelectQuery) {
  if (multiSelectQuery) {
    return queryOperators.filter(option => option.value === 'IN' || (!systemEntity && option.value === 'NOT_IN'))
  }
  if (!systemEntity) return queryOperators
  return [
    ...queryOperators.filter(option => systemQueryOperators.has(option.value)),
    { label: '为空', value: 'IS_NULL', pseudoSql: '字段 IS NULL' }
  ]
}

/** 合并实体字段与保存过的列，保留隐藏列及稳定排序；系统实体不追加虚拟列。 */
export function mergeFieldConfig(savedFields, { entityFields, availableListColumnInterfaces, isSystemEntity }) {
  // 以实体字段为基准
  const merged = entityFields.map((ef, index) => {
    const saved = savedFields.find(sf => sf.fieldId === ef.id)
    return {
      id: saved?.id,
      revision: saved?.revision || 0,
      orderKey: saved?.orderKey || (index + 1) * 1000000,
      fieldId: ef.id,
      fieldCode: ef.fieldCode,
      fieldName: saved?.fieldName || ef.fieldName,
      fieldType: ef.fieldType,
      dictType: ef.dictType,
      showInList: saved ? saved.showInList : ef.showInList,
      isQuery: saved ? saved.isQuery : ef.isQuery,
      queryType: saved?.queryType || 'LIKE',
      width: saved?.width || 0,
      align: saved?.align || 'left',
      dataSourceType: saved?.dataSourceType || 'ENTITY_FIELD',
      dataSourceConfig: saved?.dataSourceConfig || '',
      interfaceExtensionId: resolveInterfaceExtensionId({
        extensionId: saved?.interfaceExtensionId,
        dataSourceId: saved?.dataSourceId,
        operationCode: saved?.dataSourceOperationCode
      }, availableListColumnInterfaces),
      templateId: saved?.templateId,
      templateVersion: saved?.templateVersion,
      localOverridesDocument: saved?.localOverridesDocument || '',
      renderComponent: saved?.renderComponent || '',
      formatter: saved?.formatter || '',
      columnConfig: saved?.columnConfig || '',
      queryConfig: saved?.queryConfig || '',
      renderConfig: saved?.renderConfig || '',
      sortOrder: saved?.sortOrder ?? index
    }
  })
  savedFields
    .filter(() => !isSystemEntity)
    .filter(saved => !entityFields.some(entityField => String(entityField.id) === String(saved.fieldId)))
    .forEach((saved, index) => {
      merged.push({
        ...saved,
        id: saved.id,
        revision: saved.revision || 0,
        orderKey: saved.orderKey || (entityFields.length + index + 1) * 1000000,
        fieldId: saved.fieldId || `virtual_${Date.now()}_${index}`,
        fieldCode: saved.fieldCode || `virtual_${index + 1}`,
        fieldName: saved.fieldName || '虚拟列',
        fieldType: saved.fieldType || 'STRING',
        showInList: saved.showInList !== false,
        isQuery: saved.isQuery === true,
        queryType: saved.queryType || 'EQ',
        width: saved.width || 0,
        align: saved.align || 'left',
        dataSourceType: saved.dataSourceType || 'FIELD_TEMPLATE',
        dataSourceConfig: saved.dataSourceConfig || '',
        renderComponent: saved.renderComponent || '',
        formatter: saved.formatter || '',
        columnConfig: saved.columnConfig || '',
        queryConfig: saved.queryConfig || '',
        renderConfig: saved.renderConfig || '',
        sortOrder: saved.sortOrder ?? entityFields.length + index
      })
    })
  // 按 sortOrder 排序
  merged.sort((a, b) => a.sortOrder - b.sortOrder)
  return merged
}

/** 只输出列保存协议；模板已复制为本地配置，不能再次保留模板继承坐标。 */
export function normalizeFieldForSave(field, index = 0) {
  return {
    id: field.id,
    fieldId: field.fieldId,
    fieldCode: field.fieldCode,
    fieldName: field.fieldName,
    showInList: field.showInList,
    isQuery: field.isQuery,
    queryType: field.queryType,
    width: field.width,
    align: field.align,
    dataSourceType: field.dataSourceType || 'ENTITY_FIELD',
    dataSourceConfig: field.dataSourceConfig || '',
    interfaceExtensionId: field.interfaceExtensionId || null,
    renderComponent: field.renderComponent || '',
    formatter: field.formatter || '',
    columnConfig: field.columnConfig || '',
    queryConfig: field.queryConfig || '',
    renderConfig: field.renderConfig || '',
    sortOrder: Math.max(0, index),
    orderKey: field.orderKey || (Math.max(0, index) + 1) * 1000000,
    templateId: null,
    templateVersion: null,
    localOverridesDocument: null
  }
}

export function isVirtualField(field) {
  return String(field?.fieldId || '').startsWith('virtual_')
}
