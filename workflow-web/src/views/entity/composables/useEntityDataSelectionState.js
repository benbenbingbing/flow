import { computed, ref, watch } from 'vue'
import { safeParseConfig } from '@/shared/config-runtime'
import { normalizeRecordSelection } from '@/shared/entity-record-selection'

export function useEntityDataSelectionState(props, runtimeScene, listConfig) {
  const effectiveSelectionMode = computed(() => {
    if (props.selectionMode !== 'NONE') return props.selectionMode
    return safeParseConfig(listConfig.value?.selectionConfig)?.selectionMode || 'NONE'
  })
  const explicitSelectionScene = computed(() =>
    ['SINGLE', 'MULTIPLE'].includes(props.selectionMode)
      || (Array.isArray(props.selectionActionOptions)
        && props.selectionActionOptions.length > 0)
  )
  const selectionScene = computed(() =>
    ['FORM_PICKER', 'SUB_TABLE'].includes(runtimeScene.value)
    // EMBEDDED 本身仍是普通浏览场景；只有调用方显式传入选择模式或动作时，
    // 才展示选择列和底部动作，避免把所有内嵌列表误变成选择器。
    || explicitSelectionScene.value
    || (
      ['DIALOG', 'DRAWER'].includes(runtimeScene.value)
      && effectiveSelectionMode.value !== 'NONE'
    )
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
