import { computed, ref, watch } from 'vue'
import { safeParseConfig } from '@/shared/config-runtime'
import { normalizeRecordSelection } from '@/shared/entity-record-selection'

export function useEntityDataSelectionState(props, runtimeScene, listConfig) {
  const effectiveSelectionMode = computed(() => {
    if (props.selectionMode !== 'NONE') return props.selectionMode
    return safeParseConfig(listConfig.value?.selectionConfig)?.selectionMode || 'NONE'
  })
  const selectionScene = computed(() =>
    ['FORM_PICKER', 'SUB_TABLE'].includes(runtimeScene.value)
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
