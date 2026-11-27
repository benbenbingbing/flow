<template>
  <div class="mapping-editor">
    <div
      v-for="(row, index) in modelValue"
      :key="row.rowKey || index"
      class="mapping-row"
      :class="{ 'is-path-mapping': pathMapping }"
    >
      <el-select
        v-if="sourceOptions"
        :model-value="row.sourcePath"
        filterable
        placeholder="选择查询结果字段"
        @update:model-value="update(index, 'sourcePath', $event)"
      >
        <el-option v-for="option in sourceOptions" :key="option.value" :label="option.label" :value="option.value" />
      </el-select>
      <el-select
        v-else-if="sourceGroups.length"
        :model-value="row.sourcePath"
        filterable allow-create default-first-option
        placeholder="选择来源或输入路径"
        @update:model-value="update(index, 'sourcePath', $event)"
      >
        <el-option-group v-for="group in sourceGroups" :key="group.label" :label="group.label">
          <el-option v-for="option in group.options" :key="option.value"
            :label="parameterOptionLabel(option.label, option.value)" :value="option.value">
            <ParameterOptionLabel :label="option.label" :code="option.value" />
          </el-option>
        </el-option-group>
      </el-select>
      <el-input
        v-else
        :model-value="row.sourcePath"
        :placeholder="sourcePlaceholder"
        @update:model-value="update(index, 'sourcePath', $event)"
      />
      <el-select
        v-if="mode === 'output' && (sourceOptions || fieldOptions.length)"
        :model-value="row.targetPath"
        filterable
        :allow-create="!sourceOptions"
        placeholder="选择回填字段"
        @update:model-value="update(index, 'targetPath', $event)"
      >
        <el-option
          v-for="option in fieldOptions"
          :key="option.value"
          :label="`${option.label} (${option.value})`"
          :value="pathMapping ? option.value : `form.${option.value}`"
        />
      </el-select>
      <el-input
        v-else
        :model-value="row.targetPath"
        :placeholder="targetPlaceholder"
        @update:model-value="update(index, 'targetPath', $event)"
      />
      <el-select
        v-if="!pathMapping"
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
        v-if="mode === 'output' && !pathMapping"
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
import ParameterOptionLabel, { parameterOptionLabel } from '@/components/page-parameters/ParameterOptionLabel.vue'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  mode: { type: String, default: 'output' },
  pathMapping: { type: Boolean, default: false },
  fieldOptions: { type: Array, default: () => [] },
  sourceOptions: { type: Array, default: null },
  sourceGroups: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue'])

let rowSequence = 0

const sourcePlaceholder = computed(() =>
  props.pathMapping
    ? (props.mode === 'input' ? '来源路径，如 data.customerId' : '来源路径，如 data.contactPhone')
    : props.mode === 'input'
    ? '来源路径，如 input.customerId'
    : '来源路径，如 selection.data.phone')

const targetPlaceholder = computed(() =>
  props.mode === 'input'
    ? '接口参数，如 customerId'
    : props.pathMapping ? '字段编码，如 contactPhone' : '目标路径，如 form.contactPhone')

function add() {
  emit('update:modelValue', [
    ...props.modelValue,
    {
      rowKey: `mapping_${++rowSequence}`,
      sourcePath: '',
      targetPath: '',
      overwrite: props.sourceOptions ? 'IF_EMPTY' : 'ALWAYS',
      clearOnEmpty: !props.sourceOptions,
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

.mapping-row.is-path-mapping {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 34px;
}

@media (max-width: 900px) {
  .mapping-row,
  .mapping-row.is-path-mapping {
    grid-template-columns: 1fr;
  }
}
</style>
