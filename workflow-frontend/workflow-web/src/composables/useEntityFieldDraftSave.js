import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { entityApi } from '@/api/entity'
import { showRequestError } from '@/shared/request'
import {
  attachmentFileTypesToString,
  normalizeAttachmentFileTypes
} from '@flow/workflow-core/file-attachment'

const isTemporaryField = (field) =>
  !field?.id || String(field.id).startsWith('temp_')

/** 返回可编辑字段编码的格式错误，供输入提示和两种保存入口共用；空值由必填校验处理。 */
function getEntityFieldCodeError(field) {
  // 系统字段和已发布字段的编码不可编辑，保留历史编码，避免阻塞其他属性的保存。
  if (!field?.fieldCode || field.isSystem || field.isPublished) return ''
  return /^[a-z][a-zA-Z0-9]*$/.test(field.fieldCode) && field.fieldCode.trim() === field.fieldCode
    ? ''
    : '字段编码须为小驼峰：小写字母开头，仅含英文字母和数字，如 endTime'
}

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
      nameAliases: normalizeFileItemAliases(item.nameAliases),
      fileTypes: normalizeAttachmentFileTypes(item.fileTypes)
    }))
  }
  return field
}

export function normalizeEntityFieldForSave(source = {}) {
  const {
    optionsJson,
    options,
    optionSource,
    uiConfigurable,
    runtimeReadable,
    childEntityCode,
    ...field
  } = source
  const selection = ['SELECT', 'MULTI_SELECT', 'RADIO', 'CHECKBOX'].includes(field.fieldType)
  return {
    ...field,
    optionsInterfaceId: selection ? field.optionsInterfaceId || null : null,
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
      nameAliases: JSON.stringify(normalizeFileItemAliases(item.nameAliases)),
      fileTypes: attachmentFileTypesToString(item.fileTypes)
    })) || []
  }
}

function normalizeFileItemAliases(value) {
  if (Array.isArray(value)) {
    return [...new Set(value.map(item => String(item || '').trim()).filter(Boolean))]
  }
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed)
      ? [...new Set(parsed.map(item => String(item || '').trim()).filter(Boolean))]
      : []
  } catch {
    return []
  }
}

export function useEntityFieldDraftSave({
  entityId,
  fields,
  selectedField,
  entityBaseline,
  isSystemEntity,
  savingAllFields,
  validateFieldRules,
  onSaved,
  onDeleted
}) {
  const fieldBaselines = ref(new Map())
  const savingSelectedField = ref(false)
  const deletingField = ref(false)
  // 避免全量保存用旧列表写回刚删除的字段，也避免单字段保存与删除交错。
  const isFieldWritePending = computed(() =>
    savingAllFields.value || savingSelectedField.value || deletingField.value
  )
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

  const selectedFieldCodeError = computed(() => getEntityFieldCodeError(selectedField.value))

  const validateEntityField = (field, checkDuplicate = false) => {
    if (!field?.fieldName || !field?.fieldCode) {
      ElMessage.warning('请完善字段名称和编码')
      return false
    }
    const fieldCodeError = getEntityFieldCodeError(field)
    if (fieldCodeError) {
      ElMessage.warning(`${field.fieldName}：${fieldCodeError}`)
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
    // SUB_FORM 仅保留为旧版展示字段；实体关系由独立关系资源校验和维护，
    // 新字段不再被迫携带子实体、外键或级联配置。
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
        && (field.optionSource === 'INTERFACE' || field.optionsInterfaceId
          ? !field.optionsInterfaceId || !!field.dictType
          : !field.dictType)) {
      ElMessage.warning(`请选择一种选项来源（代码表或接口）：${field.fieldName}`)
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
    if (isFieldWritePending.value) return
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

  /** 确认后立即删除指定草稿字段，保留列表中其他字段的编辑和排序。 */
  const handleDeleteField = async (field) => {
    if (isSystemEntity.value || isFieldWritePending.value || !fields.value.includes(field)) return
    if (field.isSystem) return
    if (field.isPublished) {
      ElMessage.warning('已发布的字段不能删除')
      return
    }

    deletingField.value = true
    try {
      await ElMessageBox.confirm(
        `确认删除字段「${field.fieldName || field.fieldCode || '未命名字段'}」？`,
        '删除字段',
        { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
      )

      if (!isTemporaryField(field)) {
        await entityApi.deleteField(entityId, field.id)
        // 只从已保存基线移除目标字段，避免把其他未保存修改标记为已保存。
        if (entityBaseline.value) {
          const baseline = JSON.parse(entityBaseline.value)
          baseline.fields = baseline.fields.filter(item => item.id !== field.id)
          entityBaseline.value = JSON.stringify(baseline)
        }
        fieldBaselines.value.delete(fieldIdentity(field))
        onDeleted?.(field.id)
      }
      fields.value = fields.value.filter(item => item !== field)
      if (selectedField.value === field) selectedField.value = null
      ElMessage.success('字段已删除')
    } catch (error) {
      if (error === 'cancel' || error === 'close') return
      showRequestError(error, '删除字段失败')
    } finally {
      deletingField.value = false
    }
  }

  return {
    handleDeleteField,
    handleSaveSelectedField,
    isFieldWritePending,
    isSelectedFieldDirty,
    normalizeFieldForEditing: normalizeEntityFieldForEditing,
    normalizeFieldForSave: normalizeEntityFieldForSave,
    rememberAllFieldBaselines,
    savingSelectedField,
    selectedFieldCodeError,
    validateEntityField
  }
}
