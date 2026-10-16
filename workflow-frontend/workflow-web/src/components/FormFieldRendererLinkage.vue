/**
 * 支持字段联动的字段渲染器
 * 统一组件渲染入口外的字段事件、联动和校验宿主
 */

<template>
  <div class="form-field-renderer-linkage" :class="{ 'has-custom-validation-error': customValidationError }">
    <FormFieldRenderer
      ref="fieldComponentRef"
      v-bind="$props"
      @update:model-value="$emit('update:modelValue', $event)"
      @patch-form="handleFormPatch"
      @change="handleRuntimeChange"
      @blur="handleRuntimeBlur"
      @focus="$emit('focus', $event)"
    />
    <div v-if="customValidationError" class="custom-validation-error el-form-item__error" role="alert">{{ customValidationError }}</div>
  </div>
</template>

<script setup>
import FormFieldRenderer from './FormFieldRenderer.vue'
import { formFieldProps, formFieldEmits } from '@flow/workflow-core/extensions/contracts/form-field'
import { resolveComponentFormPatch } from '@flow/workflow-core/form-runtime/formModel'
import { provideFieldScriptContext } from '@/composables/provideFieldScriptContext'
import { CUSTOM_VALIDATION_CONTEXT_KEY } from '@flow/workflow-core/form-custom-validation'
import { computed, inject, ref, nextTick } from 'vue'
import { showRequestError } from '@/shared/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { uiEventBindingApi } from '@/api/uiConfig'
import { buildFieldEventPayload, applyRuntimeFieldEffects } from '@flow/workflow-core/form-runtime/fieldEvents'
import { isEntitySelectionEventField } from '@/components/ui-config/uiFieldEventCapabilities'
import {
  FORM_UNIQUE_PRECHECK_CONTEXT_KEY,
  resolveFormUniqueValidationTrigger
} from '@flow/workflow-core/form-runtime/uniquePrecheckContext'

const props = defineProps({ ...formFieldProps, linkageState: { type: Object, default: () => ({}) } })
const emit = defineEmits(formFieldEmits)
provideFieldScriptContext(() => props.context)
const fieldComponentRef = ref(null)
const uniquePrecheckContext = inject(FORM_UNIQUE_PRECHECK_CONTEXT_KEY, null)
const customValidationContext = inject(CUSTOM_VALIDATION_CONTEXT_KEY, null)
const customValidationError = computed(() => customValidationContext?.errorFor(props.field) || '')

async function handleRuntimeBlur(event) {
  emit('blur', event)
  await customValidationContext?.onFieldBlur(props.field)
}

async function handleRuntimeChange(value, field = props.field) {
  emit('change', value)
  await executeRuntimeEvent('FIELD_CHANGE', value, null, field)
  // 与设计器的事件能力判断一致，系统用户/部门引用也必须触发已开放的选择事件。
  if (isEntitySelectionEventField(field)) {
    await executeRuntimeEvent('ENTITY_SELECTED', value, value, field)
  }
  // 事件回填可能修改条件字段，必须用全部 effect 落地后的最终数据预检。
  await checkChangeOnlyUniqueField(field)
  await customValidationContext?.checkField(field, 'CHANGE')
}

/**
 * EntitySelector/Radio/Switch 等复合控件没有可靠 blur 语义，也不会像
 * Element Plus 原生输入组件那样自动触发 FormItem change 校验。因此在
 * 运行字段的 change 链路中显式执行用户配置的 BLUR 唯一预检。
 */
async function checkChangeOnlyUniqueField(field) {
  const rule = uniquePrecheckContext?.resolveRule?.(field)
  if (!rule?.precheck?.enabled
      || rule.precheck.trigger !== 'BLUR'
      || resolveFormUniqueValidationTrigger(field) !== 'change') {
    return
  }
  await uniquePrecheckContext.check(field, 'BLUR')
}

async function executeRuntimeEvent(eventCode, value, selection, field) {
  const payload = buildFieldEventPayload(field, value, {
    ...props.context, getFormData: currentFormData
  }, selection)
  if (!payload) return
  try {
    const result = await uiEventBindingApi.execute(eventCode, payload)
    await applyRuntimeFieldEffects(result, {
      getRecord: currentFormData,
      setField: (code, next) => { currentFormData()[code] = next },
      async confirmOverwrite() {
        try {
          await ElMessageBox.confirm('将更新已有字段内容，是否继续？', '确认回填', { type: 'warning' })
          return true
        } catch { return false }
      }
    })
    if (result?.message) ElMessage.success(result.message)
  } catch (error) { showRequestError(error, '字段事件处理失败') }
}

function currentFormData() {
  // formData 始终由当前宿主显式传入；空子表行也不能退回根表 record。
  return props.formData
}

/** 跨字段补丁先整体落地，再逐字段进入标准 change 链，事件读取的都是合并后的当前作用域。 */
async function handleFormPatch(patch) {
  if (props.disabled || props.readonly) return
  const changes = resolveComponentFormPatch(patch, props.context.scriptFields || [], currentFormData(), {
    form: props.context.form, context: props.context, mode: props.mode, readonly: props.readonly, rootParentId: props.context.rootParentId
  }, props.linkageState)
  if (!changes.length) return
  emit('patch-form', Object.fromEntries(changes.map(item => [item.code, item.value])))
  await nextTick()
  for (const { field, value } of changes) await handleRuntimeChange(value, field)
}

/** 将子表单等复合字段的校验能力透传给外层 FormItem。 */
async function validate() {
  if (!fieldComponentRef.value?.validate) return true
  return (await fieldComponentRef.value.validate()) !== false
}

defineExpose({ validate })
</script>

<style scoped>
.form-field-renderer-linkage {
  position: relative;
  width: 100%;
}

/* 复用 FormItem 的错误文案样式，并在统一字段入口补齐同样的控件红框。
   仅影响展示，不修改 FormItem 的校验状态，避免普通 change 校验清空失焦错误。 */
.has-custom-validation-error :deep(.el-input__wrapper),
.has-custom-validation-error :deep(.el-textarea__inner),
.has-custom-validation-error :deep(.el-select__wrapper),
.has-custom-validation-error :deep(.el-input-tag__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}

/* 复合输入框的前后附加控件沿用 FormItem 的处理，不额外绘制内层边框。 */
.has-custom-validation-error :deep(.el-input-group__append .el-input__wrapper),
.has-custom-validation-error :deep(.el-input-group__prepend .el-input__wrapper) {
  box-shadow: 0 0 0 1px transparent inset;
}

/* 同一字段只展示一条提示；自定义错误消失后，普通/跨字段错误仍由原表单展示。 */
.has-custom-validation-error ~ :deep(.el-form-item__error) {
  display: none;
}
</style>
