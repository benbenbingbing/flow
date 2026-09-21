/**
 * 支持字段联动的字段渲染器
 * 基于 formFieldComponentMap 动态渲染对应字段组件
 */

<template>
  <div class="form-field-renderer-linkage" :class="{ 'has-custom-validation-error': customValidationError }">
    <component
      ref="fieldComponentRef"
      :is="resolvedComponent"
      :field="field"
      :modelValue="modelValue"
      @update:modelValue="$emit('update:modelValue', $event)"
      :disabled="disabled"
      :options="options"
      :context="context"
      :data-source-runtime="dataSourceRuntime"
      :attachment-item-required-state="attachmentItemRequiredState"
      @change="handleRuntimeChange"
      @blur="handleRuntimeBlur"
      @focus="$emit('focus', $event)"
    />
    <div v-if="customValidationError" class="custom-validation-error el-form-item__error" role="alert">{{ customValidationError }}</div>
  </div>
</template>

<script setup>
import TextField from '@/extensions/builtin/fields/components/TextField.vue'
import { provideFieldScriptContext } from '@/composables/provideFieldScriptContext'
import { CUSTOM_VALIDATION_CONTEXT_KEY } from '@flow/workflow-core/form-custom-validation'
import { computed, inject, ref } from 'vue'
import { showRequestError } from '@/shared/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resolveFieldComponent } from '@/extensions/core/registries/formFieldRegistry.js'
import { uiEventBindingApi } from '@/api/uiConfig'
import { getFormId } from '@/shared/form-action-runtime'
import { buildFieldEventPayload, applyRuntimeFieldEffects } from '@flow/workflow-core/form-runtime/fieldEvents'
import { isEntitySelectionEventField } from '@/components/ui-config/uiFieldEventCapabilities'
import {
  FORM_UNIQUE_PRECHECK_CONTEXT_KEY,
  resolveFormUniqueValidationTrigger
} from '@flow/workflow-core/form-runtime/uniquePrecheckContext'

const props = defineProps({
  field: {
    type: Object,
    required: true
  },
  modelValue: {
    type: [String, Number, Array, Date, Object, Boolean],
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  },
  options: {
    type: Array,
    default: null
  },
  context: {
    type: Object,
    default: () => ({})
  },
  dataSourceRuntime: {
    type: Object,
    default: null
  },
  attachmentItemRequiredState: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])
provideFieldScriptContext(() => props.context)
const fieldComponentRef = ref(null)
const uniquePrecheckContext = inject(FORM_UNIQUE_PRECHECK_CONTEXT_KEY, null)
const customValidationContext = inject(CUSTOM_VALIDATION_CONTEXT_KEY, null)
const customValidationError = computed(() => customValidationContext?.errorFor(props.field) || '')

async function handleRuntimeBlur(event) {
  emit('blur', event)
  await customValidationContext?.onFieldBlur(props.field)
}

const resolvedComponent = computed(() => {
  const component = resolveFieldComponent(props.field)
  return component || TextField
})

async function handleRuntimeChange(value) {
  emit('change', value)
  await executeRuntimeEvent('FIELD_CHANGE', value, null)
  // 与设计器的事件能力判断一致，系统用户/部门引用也必须触发已开放的选择事件。
  if (isEntitySelectionEventField(props.field)) {
    await executeRuntimeEvent('ENTITY_SELECTED', value, value)
  }
  // 事件回填可能修改条件字段，必须用全部 effect 落地后的最终数据预检。
  await checkChangeOnlyUniqueField()
  await customValidationContext?.checkField(props.field, 'CHANGE')
}

/**
 * EntitySelector/Radio/Switch 等复合控件没有可靠 blur 语义，也不会像
 * Element Plus 原生输入组件那样自动触发 FormItem change 校验。因此在
 * 运行字段的 change 链路中显式执行用户配置的 BLUR 唯一预检。
 */
async function checkChangeOnlyUniqueField() {
  const rule = uniquePrecheckContext?.resolveRule?.(props.field)
  if (!rule?.precheck?.enabled
      || rule.precheck.trigger !== 'BLUR'
      || resolveFormUniqueValidationTrigger(props.field) !== 'change') {
    return
  }
  await uniquePrecheckContext.check(props.field, 'BLUR')
}

async function executeRuntimeEvent(eventCode, value, selection) {
  const payload = buildFieldEventPayload(props.field, value, {
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
  if (props.context?.getFormData) return props.context.getFormData()
  const record = props.context?.record
  return record?.data && typeof record.data === 'object' ? record.data : record || {}
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
