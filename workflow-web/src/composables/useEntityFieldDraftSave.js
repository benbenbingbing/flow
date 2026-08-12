import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { entityApi } from '@/api/entity'
import {
  attachmentFileTypesToString,
  normalizeAttachmentFileTypes
} from '@/shared/file-attachment'

const isTemporaryField = (field) =>
  !field?.id || String(field.id).startsWith('temp_')

export function normalizeEntityFieldForEditing(rawField = {}) {
  const field = {
    ...rawField,
    valueStorage: ['MULTI_SELECT', 'CHECKBOX', 'MULTI_REFERENCE'].includes(rawField.fieldType)
      ? 'MULTI_TABLE'
      : (rawField.valueStorage || 'SCALAR'),
    childEntityId: rawField.childEntityId || rawField.refEntityId || '',
    childRefFieldCode: rawField.childRefFieldCode || rawField.refFieldCode || '',
    relationType: rawField.relationType || (
      rawField.fieldType === 'SUB_FORM' ? 'ONE_TO_ONE' : undefined
    ),
    cascadeDelete: rawField.cascadeDelete !== false,
    fileTypes: rawField.fileTypes
      ? (typeof rawField.fileTypes === 'string' ? rawField.fileTypes.split(',') : rawField.fileTypes)
      : []
  }
  if (field.fileItems?.length) {
    field.fileItems = field.fileItems.map(item => ({
      ...item,
      required: item.required === true || item.required === 1 || item.required === '1',
      fileTypes: normalizeAttachmentFileTypes(item.fileTypes)
    }))
  }
  return field
}

export function normalizeEntityFieldForSave(source = {}) {
  const {
    optionSource,
    uiConfigurable,
    runtimeReadable,
    childEntityCode,
    ...field
  } = source
  return {
    ...field,
    childEntityId: field.childEntityId || field.refEntityId || '',
    childRefFieldCode: field.childRefFieldCode || field.refFieldCode || '',
    refEntityId: field.refEntityId || field.childEntityId || '',
    refFieldCode: field.refFieldCode || field.childRefFieldCode || '',
    relationType: field.relationType || (
      field.fieldType === 'SUB_FORM' ? 'ONE_TO_ONE' : undefined
    ),
    cascadeDelete: field.cascadeDelete !== false,
    id: isTemporaryField(field) ? null : field.id,
    fileTypes: Array.isArray(field.fileTypes)
      ? field.fileTypes.join(',')
      : field.fileTypes,
    fileItems: field.fileItems?.map(item => ({
      ...item,
      required: Boolean(item.required),
      fileTypes: attachmentFileTypesToString(item.fileTypes)
    })) || []
  }
}

export function useEntityFieldDraftSave({
  entityId,
  fields,
  selectedField,
  entityBaseline,
  isSystemEntity,
  validateFieldRules,
  onSaved
}) {
  const fieldBaselines = ref(new Map())
  const savingSelectedField = ref(false)
  const fieldIdentity = field => String(field?.id || '')
  const fieldFingerprint = field =>
    JSON.stringify(normalizeEntityFieldForSave(field))

  const rememberFieldBaseline = (field, previousIdentity = '') => {
    const next = new Map(fieldBaselines.value)
    if (previousIdentity) next.delete(previousIdentity)
    const identity = fieldIdentity(field)
    if (identity) next.set(identity, fieldFingerprint(field))
    fieldBaselines.value = next
  }

  const rememberAllFieldBaselines = () => {
    fieldBaselines.value = new Map(
      fields.value
        .map(field => [fieldIdentity(field), fieldFingerprint(field)])
        .filter(([identity]) => identity)
    )
  }

  const isSelectedFieldDirty = computed(() => {
    const field = selectedField.value
    if (!field || isSystemEntity.value) return false
    const baseline = fieldBaselines.value.get(fieldIdentity(field))
    return baseline == null || baseline !== fieldFingerprint(field)
  })

  const validateEntityField = (field, checkDuplicate = false) => {
    if (!field?.fieldName || !field?.fieldCode) {
      ElMessage.warning('请完善字段名称和编码')
      return false
    }
    if (checkDuplicate) {
      const duplicate = fields.value.find(item =>
        item !== field && item.fieldCode === field.fieldCode
      )
      if (duplicate) {
        ElMessage.warning(`字段编码已存在：${field.fieldCode}`)
        return false
      }
    }
    if (field.fieldType === 'SUB_FORM') {
      if (!field.childEntityId && !field.refEntityId) {
        ElMessage.warning(`请选择子实体：${field.fieldName}`)
        return false
      }
      if (!field.childRefFieldCode && !field.refFieldCode) {
        ElMessage.warning(`请选择子表外键：${field.fieldName}`)
        return false
      }
    }
    if (field.fieldType === 'SUB_LIST') {
      if (!field.refEntityId) {
        ElMessage.warning(`请选择目标实体：${field.fieldName}`)
        return false
      }
      if (!field.refListKey) {
        ElMessage.warning(`请选择已发布列表：${field.fieldName}`)
        return false
      }
    }
    if (['SELECT', 'MULTI_SELECT', 'RADIO', 'CHECKBOX'].includes(field.fieldType)
        && !field.dictType) {
      ElMessage.warning(`请选择代码表：${field.fieldName}`)
      return false
    }
    if (['REFERENCE', 'MULTI_REFERENCE'].includes(field.fieldType)
        && !field.refEntityId) {
      ElMessage.warning(`请选择目标实体：${field.fieldName}`)
      return false
    }
    return validateFieldRules(field)
  }

  const updateEntityBaselineForSavedField = (
    previousField,
    savedField,
    currentIndex
  ) => {
    if (!entityBaseline.value) return
    try {
      const baseline = JSON.parse(entityBaseline.value)
      const baselineFields = Array.isArray(baseline.fields)
        ? baseline.fields
        : []
      const previousId = isTemporaryField(previousField)
        ? null
        : previousField.id
      const baselineIndex = previousId
        ? baselineFields.findIndex(field =>
          String(field.id) === String(previousId)
        )
        : -1
      const normalized = normalizeEntityFieldForSave(savedField)
      if (baselineIndex >= 0) {
        baselineFields.splice(baselineIndex, 1, normalized)
      } else {
        const insertAt = Math.max(
          0,
          Math.min(Number(currentIndex) || 0, baselineFields.length)
        )
        baselineFields.splice(insertAt, 0, normalized)
      }
      baseline.fields = baselineFields
      entityBaseline.value = JSON.stringify(baseline)
    } catch (error) {
      console.warn('更新实体字段保存基线失败:', error)
    }
  }

  const handleSaveSelectedField = async () => {
    const field = selectedField.value
    if (!field || isSystemEntity.value) return
    if (!validateEntityField(field, true)) return

    const currentIndex = fields.value.indexOf(field)
    if (currentIndex < 0) return
    const previousField = {
      id: field.id,
      fieldCode: field.fieldCode
    }
    const previousIdentity = fieldIdentity(field)
    const payload = normalizeEntityFieldForSave(field)

    savingSelectedField.value = true
    try {
      const saved = isTemporaryField(field)
        ? await entityApi.createField(entityId, payload)
        : await entityApi.updateField(entityId, field.id, payload)
      const normalized = normalizeEntityFieldForEditing(saved)
      fields.value.splice(currentIndex, 1, normalized)
      selectedField.value = normalized
      updateEntityBaselineForSavedField(
        previousField,
        normalized,
        currentIndex
      )
      rememberFieldBaseline(normalized, previousIdentity)
      onSaved?.(normalized)
      ElMessage.success('当前属性已保存，其他未保存修改仍保留')
    } catch (error) {
      console.error(error)
      ElMessage.error(error?.message || '当前属性保存失败')
    } finally {
      savingSelectedField.value = false
    }
  }

  return {
    handleSaveSelectedField,
    isSelectedFieldDirty,
    normalizeFieldForEditing: normalizeEntityFieldForEditing,
    normalizeFieldForSave: normalizeEntityFieldForSave,
    rememberAllFieldBaselines,
    savingSelectedField,
    validateEntityField
  }
}
