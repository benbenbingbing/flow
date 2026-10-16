import { ref, toRaw, watch } from 'vue'
import { fieldValueLinkageSignature, getFieldValueLinkageError, readFieldValueLinkage, updateFieldValueLinkage } from '../shared/form-field-linkage.js'

/**
 * 内存缓存保留尚未完成的行（如两个空依赖值），不把编辑器专用结构写入发布数据。
 * 外部回填或撤销改变规则签名后重新读取；其他属性变更不打断当前输入。
 */
const drafts = new WeakMap()
function readDraft(field) {
  if (!field) return { model: readFieldValueLinkage(null) }
  const key = toRaw(field)
  const signature = fieldValueLinkageSignature(field)
  let draft = drafts.get(key)
  if (!draft || draft.signature !== signature) {
    draft = { signature, model: readFieldValueLinkage(field) }
    drafts.set(key, draft)
  }
  return draft
}

export function getFieldLinkageDraftError(field) {
  return getFieldValueLinkageError(readDraft(field).model)
}

/** 配置随节点草稿保存，切换字段和页签不会重置未完成的联动输入。 */
export function useFieldValueLinkage(getField, isDisabled = () => false) {
  const model = ref(readFieldValueLinkage(null))
  watch([getField, () => fieldValueLinkageSignature(getField())], ([field]) => {
    model.value = readDraft(field).model
  }, { immediate: true })

  function persist(group) {
    const field = getField()
    if (!field || isDisabled()) return
    updateFieldValueLinkage(field, model.value, group)
    drafts.set(toRaw(field), { signature: fieldValueLinkageSignature(field), model: model.value })
  }
  return { model, persist }
}
