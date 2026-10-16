<template>
  <EntitySelector
    :key="selectionRevision"
    :entity-type="entityType"
    :model-value="displayValue"
    value-key="code"
    :title="title"
    :placeholder="unavailable ? '原选择已不可用，请重新选择' : placeholder"
    @change="selectIdentity"
  />
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import EntitySelector from '@/components/EntitySelector.vue'
import request from '@/utils/request'

const props = defineProps({
  modelValue: { type: String, default: '' },
  entityType: { type: String, required: true },
  title: { type: String, default: '' },
  placeholder: { type: String, default: '请选择' }
})
const emit = defineEmits(['update:modelValue'])
const displayValue = ref('')
const unavailable = ref(false)
const selectionRevision = ref(0)

// 老配置允许手输用户 ID，新选择保存 username/groupCode。仅转换回显值，
// 未操作的旧快照保持原值，避免打开配置即改变身份；迟到查询不能覆盖新选择。
watch(() => [props.modelValue, props.entityType], async ([value, type], _, onCleanup) => {
  let stale = false
  onCleanup(() => { stale = true })
  displayValue.value = ''
  unavailable.value = false
  if (!value) return
  try {
    for (const valueKey of ['code', 'id']) {
      const params = new URLSearchParams({ ids: value, valueKey })
      const records = await request.get(`/entity-selector/${type}/batch?${params}`)
      if (stale) return
      if (records?.[0]?.code) {
        displayValue.value = records[0].code
        return
      }
    }
    unavailable.value = true
  } catch {
    if (!stale) unavailable.value = true
  }
}, { immediate: true })

/** 仅接受目录中选择的身份；禁用用户不能被选作兜底或责任人。 */
function selectIdentity(row) {
  if (row && props.entityType === 'USER' && String(row.status) === '1') {
    ElMessage.warning('该用户已禁用，请选择启用用户')
    selectionRevision.value++
    return
  }
  displayValue.value = row?.code || ''
  unavailable.value = false
  emit('update:modelValue', displayValue.value)
}
</script>
