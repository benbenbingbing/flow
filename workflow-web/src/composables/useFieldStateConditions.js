import { ref, watch } from 'vue'
import {
  fieldStateConditionSignature,
  readFieldStateConditions,
  updateFieldStateCondition
} from '../shared/form-field-state-conditions.js'
import { createFlowConditionGroup } from '@flow/workflow-core/utils/flowConditionGroups'

/** 管理抽屉内三个条件的编辑副本，外部回填时刷新，本地输入时保留组件状态。 */
export function useFieldStateConditions(getField, getFieldType) {
  const states = ref({})
  let currentField
  let currentSignature
  watch([getField, () => fieldStateConditionSignature(getField())], ([field, signature]) => {
    if (field === currentField && signature === currentSignature) return
    currentField = field
    currentSignature = signature
    states.value = readFieldStateConditions(field)
  }, { immediate: true })

  function persist(name) {
    updateFieldStateCondition(getField(), name, states.value[name], getFieldType)
    // watcher 在下一轮执行；标记本次自身写入，防止每次输入后重建整棵条件树。
    currentSignature = fieldStateConditionSignature(getField())
  }

  function setEnabled(name, enabled) {
    states.value[name].enabled = enabled
    persist(name)
  }

  function resetCondition(name) {
    states.value[name].root = createFlowConditionGroup()
    states.value[name].parseWarning = ''
    states.value[name].original = {}
    persist(name)
  }

  return { states, persist, setEnabled, resetCondition }
}
