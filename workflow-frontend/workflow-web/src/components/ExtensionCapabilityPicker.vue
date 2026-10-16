<template>
  <el-select
    :model-value="modelValue"
    :placeholder="placeholder"
    :loading="loading"
    filterable
    remote
    reserve-keyword
    clearable
    style="width: 100%"
    :remote-method="search"
    @visible-change="handleVisibleChange"
    @change="handleChange"
  >
    <el-option
      v-for="option in displayOptions"
      :key="optionKey(option)"
      :label="optionLabel(option)"
      :value="option[valueField]"
    >
      <div class="capability-option">
        <span>{{ optionLabel(option) }}</span>
        <small>
          {{ option.key || option.sourceName || option[valueField] }}
          <template v-if="option.description"> · {{ option.description }}</template>
        </small>
      </div>
    </el-option>
  </el-select>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { extensionCatalogApi } from '@/api/system/extension'

const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: ''
  },
  capabilityType: {
    type: String,
    required: true
  },
  valueField: {
    type: String,
    default: 'key'
  },
  placeholder: {
    type: String,
    default: '搜索并选择'
  },
  contextParams: {
    type: Object,
    default: () => ({})
  },
  currentOption: {
    type: Object,
    default: null
  },
  localOptions: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue', 'selected', 'loaded'])
const loading = ref(false)
const options = ref([])
const lastKeyword = ref('')

const displayOptions = computed(() => {
  const result = [...options.value]
  if (props.modelValue && !result.some(item =>
    String(item[props.valueField]) === String(props.modelValue))) {
    result.unshift(props.currentOption || {
      [props.valueField]: props.modelValue,
      key: props.modelValue,
      displayName: String(props.modelValue),
      legacy: true
    })
  }
  return result
})

watch(
  () => [props.capabilityType, props.contextParams],
  () => search(''),
  { deep: true }
)

async function search(keyword = '') {
  lastKeyword.value = keyword
  loading.value = true
  try {
    const result = await extensionCatalogApi.options({
      capabilityType: props.capabilityType,
      keyword: keyword?.trim() || undefined,
      limit: keyword?.trim() ? 20 : 6,
      ...props.contextParams
    })
    const selectable = filterByLocalImplementation(result || [])
    options.value = sortRecentFirst(selectable)
    emit('loaded', options.value)
  } catch {
    options.value = []
    emit('loaded', options.value)
  } finally {
    loading.value = false
  }
}

function filterByLocalImplementation(items) {
  if (!props.capabilityType.startsWith('UI_')) {
    return items
  }
  const localKeys = new Set(props.localOptions.map(item =>
    String(item.key || item.name || item.value || '')))
  return items.filter(item => localKeys.has(String(item.key || '')))
}

function handleVisibleChange(visible) {
  if (visible && !options.value.length) search(lastKeyword.value)
}

function handleChange(value) {
  emit('update:modelValue', value || '')
  const selected = displayOptions.value.find(item =>
    String(item[props.valueField]) === String(value))
  if (selected) remember(selected)
  emit('selected', selected || null)
}

function optionLabel(option) {
  return option.displayName || option.label || option.key || option[props.valueField]
}

function optionKey(option) {
  return `${option[props.valueField]}:${option.implementationVersion || 1}`
}

function recentStorageKey() {
  return `extension_recent_${props.capabilityType}`
}

function recentValues() {
  try {
    return JSON.parse(localStorage.getItem(recentStorageKey()) || '[]')
  } catch {
    return []
  }
}

function remember(option) {
  const value = String(option[props.valueField] || '')
  if (!value) return
  const values = [value, ...recentValues().filter(item => item !== value)].slice(0, 5)
  localStorage.setItem(recentStorageKey(), JSON.stringify(values))
}

function sortRecentFirst(items) {
  const recent = recentValues()
  const index = new Map(recent.map((value, position) => [String(value), position]))
  return [...items].sort((left, right) => {
    const leftIndex = index.get(String(left[props.valueField]))
    const rightIndex = index.get(String(right[props.valueField]))
    if (leftIndex !== undefined || rightIndex !== undefined) {
      return (leftIndex ?? 999) - (rightIndex ?? 999)
    }
    return optionLabel(left).localeCompare(optionLabel(right), 'zh-CN')
  })
}

onMounted(() => search(''))
</script>

<style scoped>
.capability-option {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.35;
}

.capability-option small {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
