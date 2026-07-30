/**
 * 支持字段联动的字段渲染器
 * 基于 formFieldComponentMap 动态渲染对应字段组件
 */

<template>
  <div class="form-field-renderer-linkage">
    <component
      :is="resolvedComponent"
      :field="field"
      :modelValue="modelValue"
      @update:modelValue="$emit('update:modelValue', $event)"
      :disabled="disabled"
      :options="options"
      :context="context"
      :data-source-runtime="dataSourceRuntime"
      @change="handleRuntimeChange"
      @blur="$emit('blur', $event)"
      @focus="$emit('focus', $event)"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resolveFieldComponent, TextField } from '@/components/form-fields'
import { uiEventBindingApi } from '@/api/uiConfig'

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
  }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])

const resolvedComponent = computed(() => {
  const component = resolveFieldComponent(props.field)
  return component || TextField
})

function isEntityReferenceField() {
  const type = String(
    props.field?.fieldType || props.field?.componentType || ''
  ).toUpperCase()
  return [
    'REFERENCE',
    'MULTI_REFERENCE',
    'ENTITY',
    'ENTITY_SELECTOR'
  ].includes(type) || Boolean(props.field?.refEntityId)
}

async function handleRuntimeChange(value) {
  emit('change', value)
  await executeRuntimeEvent('FIELD_CHANGE', value, null)
  if (!isEntityReferenceField()) return
  await executeRuntimeEvent('ENTITY_SELECTED', value, value)
}

async function executeRuntimeEvent(eventCode, value, selection) {
  const form = props.context?.form
  if (!form?.id) return
  try {
    const result = await uiEventBindingApi.execute(eventCode, {
      configType: 'FORM',
      configId: String(form.id),
      releaseId: form.runtimeReleaseId || form.activeReleaseId || undefined,
      releaseVersion: form.runtimeReleaseVersion || undefined,
      releaseResolutionToken:
        form.releaseResolutionToken
        || props.context?.releaseResolutionToken
        || undefined,
      entityCode: props.context?.entityCode,
      targetType: 'FIELD',
      targetKey: String(
        props.field?.fieldCode
        || props.field?.fieldKey
        || props.field?.id
      ),
      recordId: props.context?.record?.id,
      selection,
      input: {
        value: props.modelValue,
        form: currentFormData(),
        selection: value
      },
      context: {
        formId: String(form.id),
        mode: props.context?.mode || ''
      }
    })
    await applyEventEffects(result)
    if (result?.message) {
      ElMessage.success(result.message)
    }
  } catch (error) {
    ElMessage.error(error.message || '字段事件处理失败')
  }
}

function currentFormData() {
  const record = props.context?.record
  return record?.data && typeof record.data === 'object'
    ? record.data
    : record || {}
}

async function applyEventEffects(result) {
  const effects = Array.isArray(result?.effects) ? result.effects : []
  for (const effect of effects) {
    if (effect?.type !== 'FIELD_MAPPING') continue
    await applyFieldMappings(effect)
  }
  if (!effects.length && result?.data && typeof result.data === 'object') {
    const patch = result.data.form || result.data.data || result.data
    Object.entries(patch || {}).forEach(([key, value]) => {
      currentFormData()[key] = value
    })
  }
}

async function applyFieldMappings(effect) {
  const data = effect?.data || {}
  const mappings = Array.isArray(effect?.mappings) ? effect.mappings : []
  for (const mapping of mappings) {
    const targetPath = String(mapping.targetPath || '')
    if (!targetPath) continue
    const value = resolvePath(data, targetPath)
    const formPath = targetPath.replace(/^form\./, '').replace(/^data\./, '')
    const current = resolvePath(currentFormData(), formPath)
    if (mapping.clearOnEmpty === false && isEmpty(value)) {
      continue
    }
    const overwrite = String(mapping.overwrite || 'ALWAYS').toUpperCase()
    if (overwrite === 'IF_EMPTY' && !isEmpty(current)) {
      continue
    }
    if (overwrite === 'CONFIRM' && !isEmpty(current) && current !== value) {
      try {
        await ElMessageBox.confirm(
          `“${props.field?.fieldName || props.field?.fieldLabel || '当前选择'}”将更新表单字段，是否覆盖已有值？`,
          '确认回填',
          { type: 'warning' }
        )
      } catch {
        continue
      }
    }
    setPath(currentFormData(), formPath, value)
  }
}

function resolvePath(source, path) {
  if (!path) return source
  return String(path)
    .split('.')
    .reduce((value, key) => value?.[key], source)
}

function setPath(target, path, value) {
  const parts = String(path).split('.')
  let current = target
  parts.forEach((part, index) => {
    if (index === parts.length - 1) {
      current[part] = value
      return
    }
    if (!current[part] || typeof current[part] !== 'object') {
      current[part] = {}
    }
    current = current[part]
  })
}

function isEmpty(value) {
  return value === null
    || value === undefined
    || value === ''
    || (Array.isArray(value) && value.length === 0)
}
</script>

<style scoped>
.form-field-renderer-linkage {
  width: 100%;
}
</style>
