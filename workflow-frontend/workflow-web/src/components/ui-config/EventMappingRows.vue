<template>
  <div class="mapping-editor">
    <div
      v-for="(row, index) in modelValue"
      :key="row.rowKey || index"
      class="mapping-row"
    >
      <el-input
        :model-value="row.sourcePath"
        :placeholder="sourcePlaceholder"
        @update:model-value="update(index, 'sourcePath', $event)"
      />
      <el-select
        v-if="mode === 'output' && fieldOptions.length"
        :model-value="row.targetPath"
        filterable
        allow-create
        placeholder="选择回填字段"
        @update:model-value="update(index, 'targetPath', $event)"
      >
        <el-option
          v-for="option in fieldOptions"
          :key="option.value"
          :label="`${option.label} (${option.value})`"
          :value="`form.${option.value}`"
        />
      </el-select>
      <el-input
        v-else
        :model-value="row.targetPath"
        :placeholder="targetPlaceholder"
        @update:model-value="update(index, 'targetPath', $event)"
      />
      <el-select
        :model-value="row.transform || 'IDENTITY'"
        title="值转换"
        @update:model-value="update(index, 'transform', $event)"
      >
        <el-option label="原值" value="IDENTITY" />
        <el-option label="取第一项" value="FIRST" />
        <el-option label="转数组" value="ARRAY" />
        <el-option label="文本拼接" value="JOIN" />
      </el-select>
      <el-select
        v-if="mode === 'output'"
        :model-value="row.overwrite || 'ALWAYS'"
        title="覆盖策略"
        @update:model-value="update(index, 'overwrite', $event)"
      >
        <el-option label="始终覆盖" value="ALWAYS" />
        <el-option label="仅空值覆盖" value="IF_EMPTY" />
        <el-option label="覆盖前确认" value="CONFIRM" />
      </el-select>
      <el-button
        circle
        type="danger"
        title="删除映射"
        @click="remove(index)"
      >
        <el-icon><Delete /></el-icon>
      </el-button>
    </div>
    <el-button type="primary" plain @click="add">
      {{ mode === 'input' ? '增加参数映射' : '增加字段回填' }}
    </el-button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Delete } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  mode: { type: String, default: 'output' },
  fieldOptions: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue'])

let rowSequence = 0

const sourcePlaceholder = computed(() =>
  props.mode === 'input'
    ? '来源路径，如 input.customerId'
    : '来源路径，如 selection.data.phone')

const targetPlaceholder = computed(() =>
  props.mode === 'input'
    ? '接口参数，如 customerId'
    : '目标路径，如 form.contactPhone')

function add() {
  emit('update:modelValue', [
    ...props.modelValue,
    {
      rowKey: `mapping_${++rowSequence}`,
      sourcePath: '',
      targetPath: '',
      overwrite: 'ALWAYS',
      clearOnEmpty: true,
      transform: 'IDENTITY',
      separator: ','
    }
  ])
}

function remove(index) {
  emit(
    'update:modelValue',
    props.modelValue.filter((_, rowIndex) => rowIndex !== index)
  )
}

function update(index, key, value) {
  emit(
    'update:modelValue',
    props.modelValue.map((row, rowIndex) =>
      rowIndex === index ? { ...row, [key]: value } : row)
  )
}
</script>

<style scoped>
.mapping-row {
  display: grid;
  grid-template-columns: minmax(180px, 2fr) minmax(180px, 2fr) minmax(110px, 1fr) minmax(120px, 1fr) 34px;
  gap: 8px;
  margin-bottom: 8px;
}

.mapping-editor > .el-button {
  margin-top: 4px;
}

@media (max-width: 900px) {
  .mapping-row {
    grid-template-columns: 1fr;
  }
}
</style>
