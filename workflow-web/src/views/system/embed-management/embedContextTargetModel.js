const SENSITIVE_FIELD_CODE = /(^password$|password_hash|token_version|(^|_)(secret|token|private_key|credential|salt|otp|mfa_secret)(_|$))/i
const SENSITIVE_FIELD_TYPES = new Set(['PASSWORD', 'SECRET', 'ENCRYPTED'])

function fieldCode(field) {
  return String(field?.fieldCode || '').trim()
}

function enabledFlag(value) {
  return value === true || value === 1 || value === '1'
    || String(value).toLowerCase() === 'true'
}

function disabledFlag(value) {
  return enabledFlag(value)
}

function sensitiveField(code, type) {
  return SENSITIVE_FIELD_CODE.test(code)
    || SENSITIVE_FIELD_TYPES.has(String(type || '').toUpperCase())
}

function indexFields(fields) {
  return new Map((Array.isArray(fields) ? fields : [])
    .map(field => [fieldCode(field), field])
    .filter(([code]) => code))
}

function releaseRows(result) {
  return Array.isArray(result) ? result
    : result?.records || result?.list || result?.items || []
}

/**
 * 新增数据表单可能渲染了流程专属热修复，但 Embed Session 固定并独立解析的是
 * runtimeReleaseId/runtimeReleaseVersion 指向的基础发布；这里必须选择同一份快照。
 */
export function resolveContextFormRelease(resolvedForm, releases) {
  if (!resolvedForm) return null
  const releaseId = String(resolvedForm.runtimeReleaseId || '').trim()
  const releaseVersion = Number(resolvedForm.runtimeReleaseVersion)
  if (!String(resolvedForm.id || '').trim()
      || !releaseId
      || !Number.isInteger(releaseVersion)
      || releaseVersion < 1) {
    throw new Error('新增表单缺少精确发布坐标')
  }
  const release = releaseRows(releases).find(item =>
    String(item?.id || '') === releaseId
      && Number(item?.version) === releaseVersion)
  if (!release?.snapshotDocument) {
    throw new Error('新增表单的固定发布快照不可用')
  }
  return release
}

/**
 * 按 Embed 后端解析 ACTIVE UI Release 的规则生成目标字段能力。
 * LIST 的可查询性来自发布列表，强制值可写性来自同一次新增表单解析；
 * FORM 的两类能力都来自所选表单发布快照。
 */
export function buildContextTargetFields(
  surfaceType,
  entityFields = [],
  listFields = [],
  formFields = []
) {
  const currentByCode = indexFields(entityFields)
  const listByCode = indexFields(listFields)
  const formByCode = indexFields(formFields)
  const orderedCodes = surfaceType === 'FORM'
    ? [...new Set([...formByCode.keys(), ...currentByCode.keys()])]
    : [...currentByCode.keys()]

  return orderedCodes.map(code => {
    const current = currentByCode.get(code)
    const list = listByCode.get(code)
    const form = formByCode.get(code)
    const published = surfaceType === 'FORM'
      ? Boolean(form)
      : Boolean(current && (list || form))
    // 服务端会同时检查实体定义类型与表单发布快照类型；任一侧敏感都不能固定过滤。
    const sensitive = sensitiveField(code, current?.fieldType)
      || sensitiveField(code, form?.fieldType)
    const queryable = published && !sensitive
      && (surfaceType === 'FORM' ? Boolean(form) : Boolean(list && enabledFlag(list.isQuery)))
    const writable = published && Boolean(form) && !disabledFlag(form.isReadonly)
      && (surfaceType === 'FORM' || enabledFlag(current?.editable))
    return {
      ...(current || list || form || {}),
      fieldCode: code,
      fieldName: form?.fieldLabel || form?.fieldName || list?.fieldName
        || current?.fieldName || code,
      published,
      queryable,
      writable
    }
  })
}

export function contextTargetIssue(field, usage) {
  if (!field?.published) return '不在当前 ACTIVE 页面发布版本中'
  if (usage === 'FIXED_FILTER' && !field.queryable) return '当前发布列表中不可查询'
  if (usage === 'FORCED_FORM_VALUE' && !field.writable) return '当前新增表单中不可写'
  return ''
}

/** 在提交前拒绝界面已能确定的无效目标，服务端仍执行最终发布快照校验。 */
export function validateContextBindingTargets(bindings, fields, verified = false) {
  if (!Array.isArray(bindings) || !bindings.length) return
  const byCode = new Map((fields || []).map(field => [field.fieldCode, field]))
  for (const binding of bindings) {
    const checked = typeof verified === 'object'
      ? verified?.[binding.usage] === true
      : verified === true
    if (!checked) throw new Error('尚未完成 ACTIVE 页面字段检查，请刷新目标资源后重试')
    const target = String(binding?.target || '').trim()
    if (!target) throw new Error('上下文映射的 Flow 目标字段不能为空')
    const issue = contextTargetIssue(byCode.get(target), binding.usage)
    if (issue) throw new Error(`目标字段“${target}”${issue}，请重新选择`)
  }
}
