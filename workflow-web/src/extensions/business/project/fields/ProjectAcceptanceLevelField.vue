<template>
  <div class="acceptance-level-field">
    <el-select
      v-model="localValue"
      :disabled="disabled"
      :loading="loading"
      placeholder="请选择复核级别"
      @change="handleChange"
    >
      <el-option
        v-for="item in effectiveOptions"
        :key="item.value"
        :label="item.label"
        :value="item.value"
        :disabled="item.disabled === true"
      />
    </el-select>
    <el-tooltip content="重新加载字段数据源" placement="top">
      <el-button
        circle
        :disabled="disabled"
        :loading="loading"
        aria-label="重新加载字段数据源"
        @click="loadRuntimeData"
      >
        <el-icon><Refresh /></el-icon>
      </el-button>
    </el-tooltip>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getFormId } from '@/shared/form-action-runtime'

const props = defineProps({
  field: { type: Object, default: () => ({}) },
  modelValue: { type: [String, Number], default: '' },
  disabled: Boolean,
  options: { type: Array, default: null },
  context: { type: Object, default: () => ({}) },
  dataSourceRuntime: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'change'])
const localValue = ref(props.modelValue ?? '')
const runtimeOptions = ref([])
const loading = ref(false)
let loadSequence = 0

const effectiveOptions = computed(() =>
  runtimeOptions.value.length
    ? runtimeOptions.value
    : (props.options || [])
)

watch(
  () => props.modelValue,
  value => {
    localValue.value = value ?? ''
  }
)

watch(
  () => [
    getFormId(props.context?.form),
    props.context?.node?.id,
    props.context?.form?.runtimeReleaseId,
    props.context?.form?.activeReleaseId
  ],
  loadRuntimeData,
  { immediate: true }
)

function runtimeOwner() {
  return props.context?.node || props.field
}

function runtimeRecord() {
  const record = props.context?.record
  return record?.data && typeof record.data === 'object'
    ? record.data
    : record || {}
}

async function loadRuntimeData() {
  const runtime = props.dataSourceRuntime
  const owner = runtimeOwner()
  if (!runtime?.executeOwnerUsage || !owner) return
  const sequence = ++loadSequence
  loading.value = true
  try {
    const context = {
      form: props.context?.form,
      record: runtimeRecord(),
      recordId: props.context?.record?.id,
      input: {
        fieldCode:
          props.field?.fieldCode
          || owner.nodeKey
          || owner.bindingRef,
        value: localValue.value
      }
    }
    if (isEmpty(localValue.value)) {
      const [defaultResult] = await runtime.executeOwnerUsage(
        owner,
        'FIELD_DEFAULT',
        context
      )
      const payload = defaultResult?.data ?? defaultResult
      const defaultValue = payload?.value ?? payload
      if (!isEmpty(defaultValue)) {
        localValue.value = defaultValue
        emit('update:modelValue', defaultValue)
      }
    }
    const [optionsResult] = await runtime.executeOwnerUsage(
      owner,
      'FIELD_OPTIONS',
      context
    )
    const options = optionsResult?.data ?? optionsResult
    if (sequence !== loadSequence) return
    runtimeOptions.value = Array.isArray(options)
      ? options
      : []
    console.info(
      '[ProjectExtensionAcceptance] 复核级别字段数据源加载完成',
      {
        fieldCode: props.field?.fieldCode,
        optionCount: runtimeOptions.value.length,
        defaultApplied: !isEmpty(localValue.value)
      }
    )
  } catch (error) {
    if (sequence !== loadSequence) return
    console.error(
      '[ProjectExtensionAcceptance] 复核级别字段数据源加载失败',
      error
    )
    ElMessage.error(error.message || '复核级别数据源加载失败')
  } finally {
    if (sequence === loadSequence) {
      loading.value = false
    }
  }
}

function handleChange(value) {
  console.info(
    '[ProjectExtensionAcceptance] 复核级别字段变化',
    {
      fieldCode: props.field?.fieldCode,
      value,
      mode: props.context?.mode
    }
  )
  emit('update:modelValue', value)
  emit('change', value)
}

function isEmpty(value) {
  return value === null
    || value === undefined
    || value === ''
}
</script>

<style scoped>
.acceptance-level-field {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) 32px;
  gap: 10px;
  align-items: center;
  width: 100%;
}
</style>
