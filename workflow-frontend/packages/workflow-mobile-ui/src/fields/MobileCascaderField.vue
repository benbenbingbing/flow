<template>
  <VanField
    class="mobile-cascader-field" :border="false" :label="label" :model-value="displayLabel"
    :placeholder="disabled ? '—' : `请选择${label}`" :label-align="labelPosition" :input-align="inputAlign"
    :required="required && !disabled" readonly :is-link="!disabled" :clickable="!disabled" @click="openPicker"
  />
  <p v-if="!pickerOpen && loading" class="cascader-status" role="status">正在加载选项…</p>
  <p v-if="!pickerOpen && error" class="cascader-error" role="alert">{{ error }}<button type="button" :disabled="loading" @click="retryCurrent">重试</button></p>
  <VanPopup v-model:show="pickerOpen" position="bottom" round safe-area-inset-bottom @closed="cancelPending">
    <VanCascader
      :key="pickerKey" :title="label" :model-value="draftKey" :options="pickerOptions"
      @change="selectBranch" @finish="finishSelection" @close="closePicker"
    >
      <template #options-top>
        <p v-if="loading" class="cascader-status" role="status">正在加载选项…</p>
        <p v-else-if="error || selectionError" class="cascader-error" role="alert">{{ error || selectionError }}<button type="button" @click="retryCurrent">重试</button></p>
        <p v-else-if="emptyBranch" class="cascader-status" role="status">当前节点暂无下级数据，请返回上一级重新选择。</p>
      </template>
    </VanCascader>
    <div class="cascader-actions">
      <button type="button" @click="closePicker">取消</button>
      <button type="button" :disabled="!valuePath.length" @click="clearSelection">清空</button>
    </div>
  </VanPopup>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { Field as VanField, Popup as VanPopup, Cascader as VanCascader } from 'vant'
import { formFieldProps } from '@flow/workflow-core/extensions/contracts/form-field'
import { useCascaderFieldRuntime } from '@flow/workflow-core/vue/useCascaderFieldRuntime'
import { resolveMobileLabelPosition } from './mobileFieldLayout.js'

const props = defineProps({ ...formFieldProps, required: Boolean })
const emit = defineEmits(['update:modelValue', 'focus', 'blur'])
const { options, loading, error, revision, isLazy, loadChildren, ensurePath, retry, resolvePath, clearError } = useCascaderFieldRuntime(props)
const label = computed(() => props.field.fieldLabel || props.field.fieldName || props.field.fieldCode || '')
const labelPosition = computed(() => resolveMobileLabelPosition(props.context.form))
const inputAlign = computed(() => labelPosition.value === 'top' ? 'left' : 'right')
const valuePath = computed(() => Array.isArray(props.modelValue) ? props.modelValue : props.modelValue == null || props.modelValue === '' ? [] : [props.modelValue])
const displayLabel = computed(() => {
  // 逐级回显尚未完成或接口失败时仍显示已保存的编码，绝不为显示标签而改写业务值。
  const resolved = resolvePath(valuePath.value)
  return valuePath.value.map((value, index) => resolved.labels[index] ?? String(value)).join(' / ')
})
const pickerOpen = ref(false), pickerKey = ref(0), draftPath = ref([]), selectionError = ref('')
let interaction = 0
const draftKey = computed(() => draftPath.value.length ? JSON.stringify(draftPath.value) : undefined)
/** Vant 只接收末级 value；以整条路径作为视图标识，避免不同分支的同名编码选错节点。 */
function toPickerOptions(nodes, parent = []) {
  return nodes.map(node => {
    const path = [...parent, node.value]
    const option = { text: node.label, value: JSON.stringify(path), disabled: node.disabled, path }
    // Vant 把缺少 children 的节点直接视为完成。尚未加载或空分支必须保留空数组，不能误提交父级。
    if (node.leaf === false || node.children?.length) option.children = toPickerOptions(node.children || [], path)
    return option
  })
}
const pickerOptions = computed(() => toPickerOptions(options.value))
const emptyBranch = computed(() => {
  if (!draftPath.value.length) return !options.value.length
  const node = resolvePath(draftPath.value).nodes.at(-1)
  return node?.leaf === false && Array.isArray(node.children) && node.children.length === 0
})
/** 打开只操作选择草稿；恢复路径时也不能把原有值当作一次用户变更提交。 */
async function openPicker() {
  if (props.disabled) return
  const operation = ++interaction
  draftPath.value = [...valuePath.value]; selectionError.value = ''; pickerKey.value++
  pickerOpen.value = true; emit('focus')
  try { await ensurePath(draftPath.value) }
  catch (cause) { if (operation === interaction) selectionError.value = cause.message || '选项加载失败' }
}
function closePicker() { pickerOpen.value = false; cancelPending(); emit('blur') }
function cancelPending() { interaction++; selectionError.value = '' }
/** 切换父节点仅加载其直接子项；迟到响应可以进入共享缓存，但不得改变当前选择草稿。 */
async function selectBranch({ selectedOptions }) {
  if (!pickerOpen.value || props.disabled) return
  const path = selectedOptions.at(-1)?.path
  if (!Array.isArray(path)) return
  const resolved = resolvePath(path)
  if (!resolved.complete || resolved.nodes.some(node => node.disabled)) return
  const operation = ++interaction
  clearError()
  draftPath.value = [...path]; selectionError.value = ''
  if (isLazy.value && resolved.nodes.at(-1)?.leaf === false) {
    try { await loadChildren(path) }
    catch (cause) { if (operation === interaction) selectionError.value = cause.message || '下级选项加载失败' }
  }
}
/** 完成事件也要核验真实树状态，防止空分支、加载中或过期的 Vant 事件提前提交。 */
function finishSelection({ selectedOptions }) {
  if (!pickerOpen.value || props.disabled || loading.value || error.value || selectionError.value) return
  const path = selectedOptions.at(-1)?.path
  if (!Array.isArray(path) || JSON.stringify(path) !== JSON.stringify(draftPath.value)) return
  const resolved = resolvePath(path)
  if (!resolved.complete || resolved.nodes.at(-1)?.leaf !== true || resolved.nodes.some(node => node.disabled)) return
  emit('update:modelValue', [...path]); closePicker()
}
function clearSelection() {
  if (props.disabled) return
  emit('update:modelValue', []); closePicker()
}
async function retryCurrent() {
  const operation = ++interaction
  selectionError.value = ''
  try {
    // 弹层按当前分支重试，详情/只读态按已保存路径恢复；不重放选择完成事件。
    await retry()
    if (pickerOpen.value && draftPath.value.length) {
      await ensurePath(draftPath.value)
      if (isLazy.value && resolvePath(draftPath.value).nodes.at(-1)?.leaf === false) await loadChildren(draftPath.value)
    }
  } catch (cause) { if (operation === interaction) selectionError.value = cause.message || '选项加载失败' }
}
// 配置、接口参数或所属记录变化时关闭旧草稿；同一配置下加载子节点不改变 revision。
watch(revision, () => { pickerOpen.value = false; cancelPending(); pickerKey.value++ })
watch(() => props.disabled, disabled => { if (disabled) { pickerOpen.value = false; cancelPending() } })
onBeforeUnmount(cancelPending)
defineExpose({ validate: () => props.disabled || (!loading.value && !error.value) })
</script>
<style scoped>
.mobile-cascader-field {
  --van-field-label-width: 92px;
  --van-field-label-margin-right: 16px;
  padding: 14px 0; background: transparent; font-size: 14px; line-height: 1.65;
  border-bottom: 1px solid var(--flow-mobile-border);
}
.mobile-cascader-field :deep(.van-field__label) { color: var(--flow-mobile-muted); font-size: 13px; }
.mobile-cascader-field.van-field--label-top { display: grid; grid-template-columns: minmax(0, 1fr) auto; }
.mobile-cascader-field :deep(.van-field__label--top) { grid-column: 1 / -1; margin-right: 0; }
.cascader-status, .cascader-error { padding: 8px 16px; font-size: 13px; line-height: 1.6; }
.cascader-status { color: var(--flow-mobile-muted); }.cascader-error { color: #a94833; }
.cascader-error button, .cascader-actions button { background: none; border: 0; color: var(--flow-mobile-accent-text); min-height: 44px; padding: 8px 16px; font-size: 14px; }
.cascader-actions { display: flex; justify-content: space-between; border-top: 1px solid var(--flow-mobile-border); }
.cascader-actions button:disabled { color: var(--flow-mobile-muted); }
</style>
