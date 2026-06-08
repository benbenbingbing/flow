export const SYSTEM_FIELD_CODES = [
  'id',
  'dataNo',
  'name',
  'code',
  'status',
  'processInstanceId',
  'processStartTime',
  'processEndTime',
  'currentTaskId',
  'currentTaskName',
  'currentTaskAssignee',
  'submitterId',
  'submitterName',
  'submitTime',
  'createdAt',
  'updatedAt',
  'createdBy',
  'updatedBy'
]

export const SYSTEM_FIELDS = new Set(SYSTEM_FIELD_CODES)

export function getFieldKey(field) {
  return String(field?.fieldCode || field?.fieldKey || field?.fieldId || field?.id || '')
}

export function isSystemField(fieldOrCode) {
  const fieldCode = typeof fieldOrCode === 'string' ? fieldOrCode : getFieldKey(fieldOrCode)
  return SYSTEM_FIELDS.has(fieldCode)
}

export function getFieldModelPath(fieldOrCode) {
  const fieldCode = typeof fieldOrCode === 'string' ? fieldOrCode : getFieldKey(fieldOrCode)
  return isSystemField(fieldCode) ? fieldCode : `data.${fieldCode}`
}

export function normalizeRuntimeFormConfigs(progressRes) {
  if (Array.isArray(progressRes?.formConfigs) && progressRes.formConfigs.length > 0) {
    return progressRes.formConfigs
  }
  return progressRes?.formConfig ? [progressRes.formConfig] : []
}

export function mergeRuntimeFormConfigs(configs) {
  if (!configs || configs.length === 0) return null
  if (configs.length === 1) return configs[0]

  const seen = new Set()
  const fields = []
  configs.forEach((config, formIndex) => {
    ;(config.fields || []).forEach((field, fieldIndex) => {
      const fieldKey = getFieldKey(field) || `${formIndex}_${fieldIndex}`
      if (seen.has(fieldKey)) return
      seen.add(fieldKey)
      fields.push({
        ...field,
        id: `${config.formId || config.entityFormId || formIndex}_${field.id || fieldKey}`,
        sortOrder: formIndex * 10000 + (field.sortOrder || fieldIndex)
      })
    })
  })

  return {
    ...configs[0],
    formName: configs.map((config) => config.formName).filter(Boolean).join(' / ') || configs[0].formName,
    fields
  }
}
