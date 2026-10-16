/** 虚拟列在分页后计算，只能展示；此规则同时用于设计器、模板复制和运行页。 */
export function supportsEntityFieldQuery(field) {
  return !!field
    && String(field.dataSourceType || 'ENTITY_FIELD').trim().toUpperCase() === 'ENTITY_FIELD'
    && !field.interfaceExtensionId
    && !String(field.fieldId || '').startsWith('virtual_')
}
