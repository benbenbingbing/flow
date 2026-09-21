import { computed, ref, watch } from 'vue'
import { safeParseConfig } from '@flow/workflow-core/config-runtime'
import { normalizeRecordSelection } from '@flow/workflow-core/entity-record-selection'
import { normalizeListSelectionMode } from '@/shared/list-selection'

export function useEntityDataSelectionState(props, runtimeScene, listConfig) {
  const effectiveSelectionMode = computed(() => {
    if (['SINGLE', 'MULTIPLE'].includes(props.selectionMode)) return props.selectionMode
    const configured = safeParseConfig(listConfig.value?.selectionConfig)?.selectionMode || 'NONE'
    // 选择器的单选由调用方决定；兼容旧表单选择器的配置回退，普通列表统一允许多选。
    return ['FORM_PICKER', 'SUB_TABLE'].includes(runtimeScene.value)
      ? configured : normalizeListSelectionMode(configured)
  })
  const explicitSelectionScene = computed(() =>
    ['SINGLE', 'MULTIPLE'].includes(props.selectionMode)
      || (Array.isArray(props.selectionActionOptions)
        && props.selectionActionOptions.length > 0)
  )
  const selectionScene = computed(() =>
    ['FORM_PICKER', 'SUB_TABLE'].includes(runtimeScene.value)
    // 内嵌、弹窗和抽屉仍可承载普通列表，只有调用方显式传入选择模式或动作时
    // 才进入选择器；列表勾选开关不能隐式隐藏工具栏并打开选择器底栏。
    || explicitSelectionScene.value
  )
  const selectedRows = ref(normalizeRecordSelection(props.initialSelectedRows))

  watch(
    () => props.initialSelectedRows,
    rows => {
      if (selectionScene.value) {
        selectedRows.value = normalizeRecordSelection(rows)
      }
    },
    { deep: true }
  )
  return { effectiveSelectionMode, selectionScene, selectedRows }
}
