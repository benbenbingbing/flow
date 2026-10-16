import { ElMessage } from 'element-plus'
import { validateEntityValidationRules } from '@/shared/entity-validation-rules'

export function useEntityValidationRules(selectedField) {
  const handleFieldTypeChange = (fieldType) => {
    const field = selectedField.value
    if (!field?.validateRules) return

    const result = validateEntityValidationRules(fieldType, field.validateRules)
    if (result.valid) {
      field.validateRules = result.normalized
      return
    }
    field.validateRules = ''
    ElMessage.info('字段类型已变化，原验证规则不再适用，已自动清空')
  }

  const validateFieldRules = (field) => {
    const result = validateEntityValidationRules(
      field.fieldType,
      field.validateRules
    )
    if (!result.valid) {
      ElMessage.warning(`${field.fieldName}：${result.error}`)
      return false
    }
    field.validateRules = result.normalized
    return true
  }

  return { handleFieldTypeChange, validateFieldRules }
}
