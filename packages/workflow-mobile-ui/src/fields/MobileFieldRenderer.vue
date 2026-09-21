<template>
  <component v-if="custom && (disabled ? custom.readonly : custom.editable)" ref="customRef" :is="custom.component" v-bind="fieldProps" @update:model-value="$emit('update:modelValue', $event)" @change="$emit('change', $event)" @blur="$emit('blur', $event)" @focus="$emit('focus', $event)" />
  <div v-else-if="disabled ? !capability.readonly : !capability.editable" class="field-unsupported" role="alert">{{ capability.reason || '当前扩展不支持此操作' }}，请在电脑端查看或处理。</div>
  <MobileSubForm v-else-if="type === 'sub_form'" ref="subFormRef" v-bind="fieldProps" :services="services" :registry="registry" @update:model-value="handleChange" />
  <MobileSubList v-else-if="type === 'sub_list'" :field="field" :context="context" :services="services" />
  <MobileFileField v-else-if="['file', 'image'].includes(type)" ref="fileRef" v-bind="fieldProps" :services="services" @update:model-value="handleChange" />
  <MobileReadonlyField v-else-if="disabled" :field="field" :model-value="modelValue" :options="choices" :services="services" />
  <h3 v-else-if="type === 'section'" class="field-section">{{ fieldLabel }}</h3>
  <MobileRichTextEditor v-else-if="type === 'rich_text'" :label="fieldLabel" :model-value="fieldValue" @update:model-value="input" @blur="blur" @focus="handleFocus" />
  <VanField v-else-if="type === 'switch'" :label="fieldLabel" :required="required"><template #input><VanSwitch :model-value="Boolean(fieldValue)" size="24px" @update:model-value="handleChange" /></template></VanField>
  <VanField v-else-if="['radio', 'checkbox'].includes(type)" :label="fieldLabel" :required="required" label-align="top"><template #input>
    <VanRadioGroup v-if="type === 'radio'" :model-value="fieldValue" @update:model-value="handleChange"><VanRadio v-for="choice in choices" :key="String(choice.value)" :name="choice.value">{{ choice.text }}</VanRadio></VanRadioGroup>
    <VanCheckboxGroup v-else :model-value="Array.isArray(fieldValue) ? fieldValue : []" @update:model-value="handleChange"><VanCheckbox v-for="choice in choices" :key="String(choice.value)" :name="choice.value">{{ choice.text }}</VanCheckbox></VanCheckboxGroup>
  </template></VanField>
  <VanField v-else-if="selectionType || dateType" :label="fieldLabel" :model-value="selectionLabel" :placeholder="`请选择${fieldLabel}`" readonly is-link clickable :required="required" @click="openPicker" />
  <VanField v-else :label="fieldLabel" :model-value="type === 'number' ? numberDraft ?? fieldValue : fieldValue" :type="type === 'textarea' ? 'textarea' : type === 'number' ? 'number' : 'text'" :placeholder="placeholder" :maxlength="parsedComponentProps.maxlength" :autosize="type === 'textarea'" label-align="top" :required="required" @update:model-value="input" @focus="handleFocus" @blur="blur" />
  <p v-if="optionError" class="field-unsupported" role="alert">{{ optionError }}<button @click="loadOptions">重试</button></p>
  <MobileEntityPicker v-if="entityType" v-model:show="pickerOpen" :title="`选择${fieldLabel}`" :model-value="selectedRecords" :multiple="multiple" :load-options="loadEntityOptions" :identity="`${context.form?.id || ''}:${field.fieldCode}`" @confirm="selectRecords" />
  <VanPopup v-else v-model:show="pickerOpen" position="bottom" round safe-area-inset-bottom>
    <template v-if="dateType">
      <VanNavBar :title="`选择${fieldLabel}`" left-text="取消" right-text="确定" @click-left="pickerOpen = false" @click-right="confirmDate" />
      <VanDatePicker v-if="type !== 'time'" v-model="dateParts" :show-toolbar="false" :min-date="minDate" :max-date="maxDate" />
      <VanTimePicker v-if="type !== 'date'" v-model="timeParts" :show-toolbar="false" :columns-type="['hour', 'minute', 'second']" />
    </template>
    <VanCascader v-else-if="type === 'cascader'" :title="fieldLabel" :model-value="Array.isArray(fieldValue) ? fieldValue.at(-1) : fieldValue" :options="choices" @close="pickerOpen = false" @finish="finishCascade" />
    <template v-else-if="multiple"><VanNavBar :title="`选择${fieldLabel}`" left-text="取消" right-text="确定" @click-left="pickerOpen = false" @click-right="confirmMultiple" /><div class="multi-options"><VanCheckboxGroup v-model="selectionDraft"><VanCell v-for="choice in choices" :key="String(choice.value)" :title="choice.text" clickable @click="toggleChoice(choice.value)"><template #right-icon><VanCheckbox :model-value="selectionDraft.includes(choice.value)" /></template></VanCell></VanCheckboxGroup></div></template>
    <VanPicker v-else :title="fieldLabel" :columns="choices" :model-value="[fieldValue]" @cancel="pickerOpen = false" @confirm="confirmChoice" />
  </VanPopup>
</template>
<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { Field as VanField, Switch as VanSwitch, RadioGroup as VanRadioGroup, Radio as VanRadio, CheckboxGroup as VanCheckboxGroup, Checkbox as VanCheckbox, Popup as VanPopup, Picker as VanPicker, DatePicker as VanDatePicker, TimePicker as VanTimePicker, NavBar as VanNavBar, Cascader as VanCascader, Cell as VanCell } from 'vant'
import { formFieldProps } from '@flow/workflow-core/extensions/contracts/form-field'
import { useFormField } from '@flow/workflow-core/vue/useFormField'
import { getFormDataSourceBindings } from '@flow/workflow-core/form-runtime'
import MobileReadonlyField from './MobileReadonlyField.vue'
import MobileFileField from './MobileFileField.vue'
import MobileRichTextEditor from './MobileRichTextEditor.vue'
import MobileSubForm from '../form/MobileSubForm.vue'
import MobileSubList from '../form/MobileSubList.vue'
import MobileEntityPicker from '../pickers/MobileEntityPicker.vue'
import { getMobileExtension, mobileFieldCapability, mobileFieldType } from './registry.js'
const props = defineProps({ ...formFieldProps, services: { type: Object, default: () => ({}) }, registry: { type: Object, default: () => ({}) }, extensionName: { type: String, default: '' }, required: Boolean })
const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])
const { fieldValue, fieldLabel, parsedComponentProps, staticOptions, placeholder, handleChange, handleInput, handleBlur, handleFocus, handleSelectionChange } = useFormField(props, emit, { applyDefaults: () => !props.disabled })
const type = computed(() => mobileFieldType(props.field))
const custom = computed(() => getMobileExtension('FIELD', props.extensionName || type.value, props.field.componentVersion || props.field.extensionVersion || 1))
const capability = computed(() => mobileFieldCapability(props.field, props.extensionName))
const fieldProps = computed(() => ({ field: props.field, modelValue: props.modelValue, disabled: props.disabled, options: props.options, context: props.context, services: props.services, dataSourceRuntime: props.dataSourceRuntime, attachmentItemRequiredState: props.attachmentItemRequiredState }))
const entityType = computed(() => ['user', 'dept', 'role', 'group', 'entity', 'sub_list'].includes(type.value))
const dateType = computed(() => ['date', 'datetime', 'time'].includes(type.value))
const selectionType = computed(() => entityType.value || ['select', 'select_multiple', 'cascader'].includes(type.value))
const multiple = computed(() => type.value === 'select_multiple' || parsedComponentProps.value.multiple === true || ['MULTI_REFERENCE', 'MULTI_SELECT'].includes(String(props.field.fieldType).toUpperCase()))
const remoteOptions = ref(null), optionError = ref(''), pickerOpen = ref(false), selectionDraft = ref([]), selectedRecords = ref([])
const customRef = ref(), subFormRef = ref(), fileRef = ref(), numberDraft = ref(null)
function normalizeOptions(options) { return (Array.isArray(options) ? options : []).map(option => typeof option === 'object' ? { ...option, text: option.text ?? option.label ?? option.name ?? String(option.value ?? option.id), value: option.value ?? option.id, ...(option.children ? { children: normalizeOptions(option.children) } : {}) } : { text: String(option), value: option }) }
const choices = computed(() => normalizeOptions(props.options ?? remoteOptions.value ?? (entityType.value ? selectedRecords.value.map(item => ({ ...item, value: item.id, text: item.name || item.nickname || item.dataName || item.code || String(item.id) })) : staticOptions.value)))
const selectionLabel = computed(() => {
  if (dateType.value) return props.modelValue == null ? '' : String(props.modelValue).replace('T', ' ')
  const values = Array.isArray(fieldValue.value) ? fieldValue.value : fieldValue.value == null || fieldValue.value === '' ? [] : [fieldValue.value]
  return values.map(value => selectedRecords.value.find(item => String(item.id) === String(value))?.name || selectedRecords.value.find(item => String(item.id) === String(value))?.nickname || choices.value.find(item => String(item.value) === String(value))?.text || String(value)).join('、')
})
let optionGeneration = 0, committed = JSON.stringify(props.modelValue)
async function loadOptions() {
  const generation = ++optionGeneration; optionError.value = ''
  if (!props.dataSourceRuntime || !getFormDataSourceBindings(props.field, 'FIELD_OPTIONS').length) return
  try { const result = await props.dataSourceRuntime.loadOptions(props.field, { form: props.context.form, record: props.context.getFormData?.(), context: props.context }); if (generation === optionGeneration) remoteOptions.value = result }
  catch (cause) { if (generation === optionGeneration) optionError.value = cause.message }
}
watch(() => [props.field.id, props.context.form?.runtimeReleaseId], loadOptions, { immediate: true })
function input(value) {
  if (type.value === 'number') {
    numberDraft.value = value
    // 保留小数点和负号的输入中间态；业务模型只持有 number/null。
    if (value !== '' && !Number.isFinite(Number(value))) return
    value = value === '' ? null : Number(value)
  }
  fieldValue.value = value; handleInput(value)
}
// 与桌面数字控件保持相同的 number/null 契约，避免 Vant 原始字符串绕过数值校验。
function normalizeNumber(value) {
  if (value == null || value === '') return null
  const number = Number(value)
  if (!Number.isFinite(number)) return null
  const precision = parsedComponentProps.value.precision ?? props.field.precision ?? (['decimal', 'double'].includes(String(props.field.fieldType).toLowerCase()) ? 2 : 0)
  const min = parsedComponentProps.value.min ?? props.field.min ?? -Infinity, max = parsedComponentProps.value.max ?? props.field.max ?? Infinity
  return Math.min(max, Math.max(min, Number(number.toFixed(Math.min(20, Math.max(0, precision))))))
}
let selectionGeneration = 0
watch(() => [props.modelValue, props.field.refEntityId], async () => {
  const version = ++selectionGeneration
  if (!entityType.value || !props.services.resolveEntitySelection) return
  const values = (Array.isArray(props.modelValue) ? props.modelValue : [props.modelValue]).filter(value => value != null && value !== '')
  if (!values.length) { selectedRecords.value = []; return }
  try { const records = await props.services.resolveEntitySelection(props.field, values); if (version === selectionGeneration) selectedRecords.value = Array.isArray(records) ? records : [] }
  catch { /* 已保存值仍显示原 ID；查询失败不能清除记录。 */ }
}, { immediate: true })
async function blur() {
  if (type.value === 'number') { fieldValue.value = normalizeNumber(numberDraft.value ?? fieldValue.value); numberDraft.value = null; await nextTick() }
  if (committed !== JSON.stringify(fieldValue.value)) { await handleChange(fieldValue.value); committed = JSON.stringify(fieldValue.value) }
  await handleBlur()
}
function openPicker() { selectionDraft.value = Array.isArray(fieldValue.value) ? [...fieldValue.value] : []; initializeDate(); pickerOpen.value = true }
function confirmChoice({ selectedValues }) { handleChange(selectedValues[0]); pickerOpen.value = false }
function toggleChoice(value) { selectionDraft.value = selectionDraft.value.includes(value) ? selectionDraft.value.filter(item => item !== value) : [...selectionDraft.value, value] }
function confirmMultiple() { handleChange([...selectionDraft.value]); pickerOpen.value = false }
function finishCascade({ selectedOptions, value }) { handleChange(parsedComponentProps.value.emitPath === false ? value : selectedOptions.map(item => item.value)); pickerOpen.value = false }
async function loadEntityOptions(query) {
  if (!props.services.loadEntityOptions) throw new Error('当前表单未提供选择能力')
  return props.services.loadEntityOptions(props.field, query, props.context)
}
async function selectRecords(records) {
  selectedRecords.value = records
  fieldValue.value = multiple.value ? records.map(item => item.id) : records[0]?.id ?? null
  await handleSelectionChange(multiple.value ? records : records[0] || null)
}
const dateParts = ref([]), timeParts = ref([])
const minDate = computed(() => new Date(parsedComponentProps.value.minDate || '1900-01-01'))
const maxDate = computed(() => new Date(parsedComponentProps.value.maxDate || '2100-12-31'))
function initializeDate() {
  const now = new Date(), raw = String(fieldValue.value || '')
  const date = raw.match(/^(\d{4})-(\d{2})-(\d{2})/)
  dateParts.value = date ? date.slice(1) : [String(now.getFullYear()), String(now.getMonth() + 1).padStart(2, '0'), String(now.getDate()).padStart(2, '0')]
  const time = raw.match(/(\d{2}):(\d{2})(?::(\d{2}))?/)
  timeParts.value = time ? [time[1], time[2], time[3] || '00'] : ['00', '00', '00']
}
function confirmDate() { handleChange(type.value === 'date' ? dateParts.value.join('-') : type.value === 'time' ? timeParts.value.join(':') : `${dateParts.value.join('-')} ${timeParts.value.join(':')}`); pickerOpen.value = false }
defineExpose({ validate: async () => !optionError.value && (await customRef.value?.validate?.()) !== false && (await subFormRef.value?.validate?.()) !== false && (await fileRef.value?.validate?.()) !== false })
</script>
<style scoped>
:deep(.van-field) { padding: 14px 0; background: transparent; }:deep(.van-field__label) { color: var(--flow-mobile-muted); }:deep(.van-radio), :deep(.van-checkbox) { margin: 10px 0; }.field-section { font-size: 16px; padding: 12px 0; }.field-unsupported { color: #a94833; background: #fff5ed; padding: 12px; border-radius: 8px; font-size: 13px; line-height: 1.6; }.field-unsupported button { border: 0; color: inherit; text-decoration: underline; background: none; min-height: 44px; }.multi-options { max-height: 55dvh; overflow-y: auto; }.mobile-rich-editor { padding: 14px 0; }.mobile-rich-editor label { display: block; color: var(--flow-mobile-muted); font-size: 14px; margin-bottom: 8px; }.mobile-rich-editor textarea { width: 100%; min-height: 140px; border: 1px solid var(--flow-mobile-border); border-radius: 8px; padding: 10px; }.mobile-rich-editor small { color: var(--flow-mobile-muted); }
</style>
