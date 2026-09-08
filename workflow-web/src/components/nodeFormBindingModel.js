export const DEFAULT_NODE_FORM_VALUE = '__DEFAULT__'

const LEGACY_FORM_KEY_PREFIX = '__LEGACY_FORM_KEY__:'
const MISSING_ENTITY_FORM_PREFIX = '__MISSING_ENTITY_FORM__:'

/**
 * 统一表单 ID 为字符串，避免后端 Long 与 el-select 字符串值造成回显失败。
 */
export function normalizeNodeFormIds(value) {
  const values = Array.isArray(value) ? value : (value ? [value] : [])
  return [...new Set(values.map(item => String(item || '').trim()).filter(Boolean))]
}

/**
 * 兼容历史 entityFormIds 的 JSON 数组、数组值和逗号字符串格式。
 */
export function parseNodeFormIds(value) {
  if (!value) return []
  if (Array.isArray(value)) return normalizeNodeFormIds(value)
  const raw = String(value).trim()
  if (!raw) return []
  if (raw.startsWith('[')) {
    try {
      return normalizeNodeFormIds(JSON.parse(raw))
    } catch {
      // 历史数据可能是以“[”开头的普通字符串，继续按逗号格式兜底。
    }
  }
  return normalizeNodeFormIds(raw.split(','))
}

function parsePortableFormReference(value) {
  const normalized = String(value || '').trim()
  if (!normalized.startsWith('wf-form://')) return null
  const reference = normalized.slice('wf-form://'.length)
  const separatorIndex = reference.indexOf('/')
  if (separatorIndex <= 0 || separatorIndex === reference.length - 1) return null
  return {
    entityCode: reference.slice(0, separatorIndex),
    formKey: reference.slice(separatorIndex + 1)
  }
}

function findEntityForm(forms, value, { matchFormKey = false, boundEntityCode = '' } = {}) {
  const normalized = String(value || '').trim()
  if (!normalized) return null
  const portableReference = matchFormKey ? parsePortableFormReference(normalized) : null
  return (Array.isArray(forms) ? forms : []).find(form => {
    if (String(form?.id || '') === normalized) return true
    if (!matchFormKey) return false
    if (String(form?.formKey || '') === normalized) return true
    if (!portableReference) return false

    // 可移植引用仅允许在当前绑定实体内按 formKey 解析，禁止跨实体尾段模糊匹配。
    const scopedEntityCode = String(form?.entityCode || boundEntityCode || '')
    return scopedEntityCode === portableReference.entityCode
      && String(form?.formKey || '') === portableReference.formKey
  }) || null
}

function legacySelectionValue(prefix, value) {
  return `${prefix}${encodeURIComponent(String(value || ''))}`
}

/**
 * 将节点已有绑定解析为统一选择器状态。
 * 未识别的历史值必须保留为独立哨兵，防止加载或普通“应用”操作静默清空 BPMN。
 */
export function resolveNodeFormSelection({
  entityFormIds,
  entityFormId,
  legacyFormKey,
  entityForms,
  entityFormBindingMode,
  entityFormReadonly = false,
  entityCode = '',
  boundEntityCode = ''
} = {}) {
  const bindingMode = String(entityFormBindingMode || '').trim().toUpperCase()
  if (bindingMode === 'DEFAULT') {
    return {
      selectionValue: DEFAULT_NODE_FORM_VALUE,
      entityFormId: '',
      entityFormIds: [],
      isReadonly: Boolean(entityFormReadonly),
      entityCode,
      legacyFormKey: String(legacyFormKey || ''),
      unresolvedBinding: null,
      resolvedFromLegacy: false
    }
  }

  const parsedEntityFormIds = parseNodeFormIds(entityFormIds)
  const explicitId = (
    parsedEntityFormIds.length
      ? parsedEntityFormIds
      : normalizeNodeFormIds(entityFormId)
  )[0] || ''

  if (explicitId) {
    const form = findEntityForm(entityForms, explicitId)
    if (form) {
      return {
        selectionValue: String(form.id),
        entityFormId: String(form.id),
        entityFormIds: [String(form.id)],
        isReadonly: Boolean(entityFormReadonly),
        entityCode: entityCode || form.entityCode || '',
        legacyFormKey: String(legacyFormKey || ''),
        unresolvedBinding: null,
        resolvedFromLegacy: false
      }
    }
    const selectionValue = legacySelectionValue(MISSING_ENTITY_FORM_PREFIX, explicitId)
    return {
      selectionValue,
      entityFormId: '',
      entityFormIds: [],
      isReadonly: false,
      entityCode: '',
      legacyFormKey: String(legacyFormKey || ''),
      unresolvedBinding: {
        kind: 'entityFormId',
        value: explicitId,
        selectionValue
      },
      resolvedFromLegacy: false
    }
  }

  const normalizedLegacyKey = String(legacyFormKey || '').trim()
  if (normalizedLegacyKey) {
    const form = findEntityForm(entityForms, normalizedLegacyKey, {
      matchFormKey: true,
      boundEntityCode
    })
    if (form) {
      return {
        selectionValue: String(form.id),
        entityFormId: String(form.id),
        entityFormIds: [String(form.id)],
        isReadonly: Boolean(entityFormReadonly),
        entityCode: entityCode || form.entityCode || '',
        legacyFormKey: normalizedLegacyKey,
        unresolvedBinding: null,
        resolvedFromLegacy: true
      }
    }
    const selectionValue = legacySelectionValue(LEGACY_FORM_KEY_PREFIX, normalizedLegacyKey)
    return {
      selectionValue,
      entityFormId: '',
      entityFormIds: [],
      isReadonly: false,
      entityCode: '',
      legacyFormKey: normalizedLegacyKey,
      unresolvedBinding: {
        kind: 'formKey',
        value: normalizedLegacyKey,
        selectionValue
      },
      resolvedFromLegacy: false
    }
  }

  return {
    selectionValue: DEFAULT_NODE_FORM_VALUE,
    entityFormId: '',
    entityFormIds: [],
    isReadonly: false,
    entityCode: '',
    legacyFormKey: '',
    unresolvedBinding: null,
    resolvedFromLegacy: false
  }
}

/**
 * 将选择器状态转换为持久化意图；未操作的未知历史绑定只能原样保留。
 */
export function buildNodeFormPersistencePlan({
  selectionValue,
  entityForms,
  unresolvedBinding,
  selectionDirty = false,
  boundEntityCode = ''
} = {}) {
  if (unresolvedBinding && !selectionDirty) {
    return { mode: 'PRESERVE' }
  }
  if (!selectionValue || selectionValue === DEFAULT_NODE_FORM_VALUE) {
    const defaultForm = (Array.isArray(entityForms) ? entityForms : []).find(form =>
      form?.isDefault === true || form?.isDefault === 1 || form?.isDefault === '1')
    if (!defaultForm) {
      return { mode: 'MISSING_DEFAULT' }
    }
    return {
      mode: 'DEFAULT',
      entityFormId: String(defaultForm.id),
      entityCode: defaultForm.entityCode || boundEntityCode || ''
    }
  }
  const form = findEntityForm(entityForms, selectionValue)
  if (!form) {
    return { mode: 'INVALID' }
  }
  return {
    mode: 'SPECIFIC',
    entityFormId: String(form.id),
    entityCode: form.entityCode || boundEntityCode || ''
  }
}

/**
 * 异步表单列表返回后再次核对初始化上下文，避免旧节点响应覆盖当前节点。
 */
export function isNodeFormInitializationCurrent({
  sequence,
  activeSequence,
  elementId,
  currentElementId,
  processId,
  currentProcessId
} = {}) {
  return sequence === activeSequence
    && String(elementId || '') === String(currentElementId || '')
    && String(processId || '') === String(currentProcessId || '')
}
