<template>
  <div>
    <el-form-item label="目标实体">
      <EntityDefinitionPicker
        :model-value="config.entityCode"
        @update:model-value="changeEntity"
      />
    </el-form-item>
    <el-form-item label="记录 ID 来源">
      <el-select
        :model-value="config.idPath"
        filterable
        allow-create
        default-first-option
        placeholder="选择字段或输入参数路径，例如 input.params.customerId"
        style="width: 100%"
        @update:model-value="updateConfig({ idPath: $event })"
      >
        <el-option v-for="item in idSources" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
    </el-form-item>
    <el-alert v-if="loadError" :title="loadError" type="warning" :closable="false" show-icon />
    <el-form-item v-if="isList" label="返回字段">
      <el-select
        :model-value="config.fields"
        filterable
        multiple
        :loading="loadingFields"
        placeholder="供后置步骤通过 result.data 读取"
        style="width: 100%"
        @update:model-value="updateConfig({ fields: $event })"
      >
        <el-option v-for="field in queryFields" :key="field.value" :label="field.label" :value="field.value" />
      </el-select>
    </el-form-item>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import EntityDefinitionPicker from '@/components/EntityDefinitionPicker.vue'
import { entityApi } from '@/api/entity'

const props = defineProps({
  config: { type: Object, required: true },
  eventCode: { type: String, required: true },
  fieldOptions: { type: Array, default: () => [] },
  parameterOptions: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:config', 'change-entity', 'fields'])
const queryFields = ref([])
const loadingFields = ref(false)
const loadError = ref('')
const isRow = computed(() => props.eventCode === 'ROW_BUTTON_CLICK')
const isToolbar = computed(() => props.eventCode === 'TOOLBAR_BUTTON_CLICK')
const isList = computed(() => isRow.value || isToolbar.value)
const idSources = computed(() => {
  const prefix = isRow.value ? 'input.row' : 'input.selectedRows.0'
  const system = isList.value
    ? [{ value: `${prefix}.id`, label: isRow.value ? '当前行记录 ID' : '唯一选中行记录 ID' }]
    : [{ value: 'recordId', label: '当前表单记录 ID' }]
  if (['FIELD_CHANGE', 'ENTITY_SELECTED', 'FIELD_BUTTON_CLICK'].includes(props.eventCode)) {
    system.push({ value: 'input.value', label: '触发字段的值' })
  }
  if (props.eventCode === 'ENTITY_SELECTED') {
    system.push({ value: 'selection.id', label: '所选实体记录 ID' })
  }
  return [
    ...props.parameterOptions.map(item => ({ value: `input.params.${item.code}`, label: `页面参数 · ${item.name || item.code}` })),
    ...system,
    ...props.fieldOptions.map(item => ({
      value: `${isList.value ? `${prefix}.data` : 'input.form'}.${item.value}`,
      label: `${isList.value ? (isRow.value ? '当前行' : '唯一选中行') : '当前表单'} · ${item.label}`
    }))
  ]
})

function updateConfig(patch) {
  emit('update:config', { ...props.config, ...patch })
}

function changeEntity(entityCode) {
  updateConfig({ entityCode, ...(isList.value ? { fields: [] } : {}) })
  emit('change-entity')
}

// 每个步骤独立加载已选实体字段；快速切换时丢弃上一次的字段选项。
watch(() => props.config.entityCode, async (code, _, onCleanup) => {
  let current = true
  onCleanup(() => { current = false })
  queryFields.value = []
  emit('fields', [])
  loadError.value = ''
  loadingFields.value = Boolean(code)
  if (!code) return
  try {
    const definition = await entityApi.getByCode(code)
    if (!current) return
    const fields = new Map()
    if (definition.storageMode !== 'SYSTEM') {
      fields.set('name', '名称')
      fields.set('code', '编码')
    }
    for (const field of definition.fields || []) {
      if (['SUB_FORM', 'SUB_LIST', 'VIRTUAL'].includes(field.fieldType) || field.fieldCode === 'id') continue
      fields.set(field.fieldCode, field.fieldName || field.fieldCode)
    }
    queryFields.value = [...fields].map(([value, label]) => ({ value, label: `${label} (${value})` }))
    emit('fields', queryFields.value.map(field => ({ ...field, value: `data.data.${field.value}` })))
  } catch {
    if (current) loadError.value = '目标实体字段加载失败，请重新选择实体'
  } finally {
    if (current) loadingFields.value = false
  }
}, { immediate: true })
</script>
