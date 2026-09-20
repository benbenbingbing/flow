import assert from 'node:assert/strict'
import { effectScope, reactive, ref } from 'vue'
import { useEntityDataSelectionState } from '../../views/entity/composables/useEntityDataSelectionState.js'

const scope = effectScope()
scope.run(() => {
  const props = reactive({ selectionMode: 'NONE', selectionActionOptions: [], initialSelectedRows: [] })
  const scene = ref('PAGE')
  const config = ref({ selectionConfig: { selectionMode: 'SINGLE' } })
  const state = useEntityDataSelectionState(props, scene, config)
  assert.equal(state.effectiveSelectionMode.value, 'MULTIPLE', '旧单选设置在普通列表中映射成允许勾选')
  for (const mode of ['PAGE', 'EMBEDDED', 'DIALOG', 'DRAWER']) {
    scene.value = mode
    assert.equal(state.selectionScene.value, false, '普通列表的勾选不能使工具栏消失: ' + mode)
  }
  props.selectionMode = 'SINGLE'
  assert.equal(state.effectiveSelectionMode.value, 'SINGLE', '调用方单选约束必须保留')
  assert.equal(state.selectionScene.value, true)
  props.selectionMode = 'MULTIPLE'
  assert.equal(state.effectiveSelectionMode.value, 'MULTIPLE')
  assert.equal(state.selectionScene.value, true)
  props.selectionMode = 'NONE'
  scene.value = 'FORM_PICKER'
  assert.equal(state.effectiveSelectionMode.value, 'SINGLE', '旧表单选择器仍可使用列表单选配置')
  assert.equal(state.selectionScene.value, true)
  scene.value = 'PAGE'
  config.value = { selectionConfig: { selectionMode: 'NONE' } }
  assert.equal(state.effectiveSelectionMode.value, 'NONE')
})
scope.stop()
console.log('list selection state passed: ordinary list, dialog, embed and picker modes')
