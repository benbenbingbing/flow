<template>
  <div class="acceptance-score-field">
    <el-slider
      v-model="localValue"
      :disabled="disabled"
      :min="0"
      :max="100"
      show-input
      @change="handleChange"
    />
    <el-tag :type="tagType" size="small">{{ levelLabel }}</el-tag>
    <el-tooltip content="执行字段按钮事件" placement="top">
      <el-button
        circle
        :disabled="disabled"
        :loading="eventLoading"
        aria-label="执行字段按钮事件"
        @click="executeFieldButtonEvent"
      >
        <el-icon><Connection /></el-icon>
      </el-button>
    </el-tooltip>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection } from '@element-plus/icons-vue'
import { uiEventBindingApi } from '@/api/uiConfig'
import { getFormId } from '@/shared/form-action-runtime'

const props = defineProps({
  field: { type: Object, default: () => ({}) },
  modelValue: { type: [String, Number], default: 0 },
  disabled: Boolean,
  options: { type: Array, default: null },
  context: { type: Object, default: () => ({}) },
  dataSourceRuntime: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'change'])
const localValue = ref(normalize(props.modelValue))
const eventLoading = ref(false)
const passScore = computed(() =>
  Number(props.field?.componentProps?.passScore ?? 60)
)
const levelLabel = computed(() => {
  if (localValue.value >= 85) return '优秀'
  if (localValue.value >= passScore.value) return '通过'
  return '待改进'
})
const tagType = computed(() => {
  if (localValue.value >= 85) return 'success'
  if (localValue.value >= passScore.value) return 'primary'
  return 'danger'
})

watch(
  () => props.modelValue,
  value => {
    localValue.value = normalize(value)
  }
)

function normalize(value) {
  const number = Number(value)
  return Number.isFinite(number)
    ? Math.max(0, Math.min(100, number))
    : 0
}

function handleChange(value) {
  const normalized = normalize(value)
  console.info('[ProjectExtensionAcceptance] 自定义评分字段变化', {
    fieldCode: props.field?.fieldCode,
    value: normalized,
    mode: props.context?.mode
  })
  emit('update:modelValue', normalized)
  emit('change', normalized)
}

async function executeFieldButtonEvent() {
  const form = props.context?.form
  const formId = getFormId(form)
  const fieldCode =
    props.field?.fieldCode
    || props.field?.fieldKey
    || props.field?.id
  if (!formId || !fieldCode) {
    ElMessage.warning('当前字段缺少已发布表单上下文')
    return
  }
  eventLoading.value = true
  try {
    const result = await uiEventBindingApi.execute(
      'FIELD_BUTTON_CLICK',
      {
        configType: 'FORM',
        configId: String(formId),
        releaseId:
          form.runtimeReleaseId
          || form.activeReleaseId
          || undefined,
        releaseVersion:
          form.runtimeReleaseVersion
          || form.activeReleaseVersion
          || undefined,
        releaseResolutionToken:
          form.releaseResolutionToken
          || props.context?.releaseResolutionToken
          || undefined,
        entityCode: props.context?.entityCode,
        targetType: 'FIELD',
        targetKey: String(fieldCode),
        recordId: props.context?.record?.id || undefined,
        input: {
          value: localValue.value,
          form: currentFormData(),
          source: 'ProjectAcceptanceScoreField'
        },
        context: {
          formId: String(formId),
          mode: props.context?.mode || ''
        }
      }
    )
    applyFieldEffects(result)
    console.info(
      '[ProjectExtensionAcceptance] 字段按钮事件执行完成',
      {
        formId,
        fieldCode,
        recordId: props.context?.record?.id,
        traceCount: result?.trace?.length || 0
      }
    )
    ElMessage.success(result?.message || '字段按钮事件已执行')
  } catch (error) {
    console.error(
      '[ProjectExtensionAcceptance] 字段按钮事件执行失败',
      error
    )
    ElMessage.error(error.message || '字段按钮事件执行失败')
  } finally {
    eventLoading.value = false
  }
}

function currentFormData() {
  const record = props.context?.record
  return record?.data && typeof record.data === 'object'
    ? record.data
    : record || {}
}

function applyFieldEffects(result) {
  const target = currentFormData()
  const effects = Array.isArray(result?.effects)
    ? result.effects
    : []
  effects
    .filter(effect => effect?.type === 'FIELD_MAPPING')
    .forEach(effect => {
      const data = effect?.data || {}
      const mappings = Array.isArray(effect?.mappings)
        ? effect.mappings
        : []
      mappings.forEach(mapping => {
        const targetPath = String(mapping?.targetPath || '')
          .replace(/^form\./, '')
          .replace(/^data\./, '')
        if (!targetPath) return
        const value = readPath(data, mapping.targetPath)
        writePath(target, targetPath, value)
      })
    })
}

function readPath(source, path) {
  return String(path || '')
    .split('.')
    .filter(Boolean)
    .reduce((value, key) => value?.[key], source)
}

function writePath(target, path, value) {
  const parts = String(path).split('.').filter(Boolean)
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
</script>

<style scoped>
.acceptance-score-field {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 64px 32px;
  gap: 14px;
  align-items: center;
  width: 100%;
}
</style>
