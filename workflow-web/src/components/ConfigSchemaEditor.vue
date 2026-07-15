<template>
  <div class="config-schema-editor">
    <el-empty v-if="!schema.length" description="该扩展无需额外配置" :image-size="48" />
    <el-form v-else label-width="110px" size="small">
      <el-form-item v-for="item in schema" :key="item.key" :label="item.label" :required="item.required">
        <el-switch
          v-if="item.type === 'boolean'"
          :model-value="currentValue[item.key]"
          @update:model-value="updateValue(item, $event)"
        />
        <el-input-number
          v-else-if="item.type === 'number'"
          :model-value="currentValue[item.key]"
          :min="item.min"
          :max="item.max"
          :step="item.step || 1"
          controls-position="right"
          style="width: 100%"
          @update:model-value="updateValue(item, $event)"
        />
        <el-select
          v-else-if="item.type === 'select'"
          :model-value="currentValue[item.key]"
          :multiple="item.multiple === true"
          :placeholder="item.placeholder || `请选择${item.label}`"
          clearable
          style="width: 100%"
          @update:model-value="updateValue(item, $event)"
        >
          <el-option
            v-for="option in item.options || []"
            :key="String(option.value)"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-input
          v-else-if="item.type === 'textarea'"
          :model-value="currentValue[item.key]"
          type="textarea"
          :rows="item.rows || 3"
          :placeholder="item.placeholder"
          @update:model-value="updateValue(item, $event)"
        />
        <el-input
          v-else-if="item.type === 'json'"
          :model-value="formatJsonValue(currentValue[item.key])"
          type="textarea"
          :rows="item.rows || 5"
          :placeholder="item.placeholder || '请输入 JSON'"
          @change="updateJsonValue(item, $event)"
        />
        <el-input
          v-else
          :model-value="currentValue[item.key]"
          :placeholder="item.placeholder"
          clearable
          @update:model-value="updateValue(item, $event)"
        />
        <div v-if="item.description" class="config-help">{{ item.description }}</div>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { applySchemaDefaults, sanitizeConfigObject } from '@/shared/config-runtime'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  schema: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue'])

const currentValue = computed(() => applySchemaDefaults(props.schema, props.modelValue))

function updateValue(item, value) {
  emit('update:modelValue', sanitizeConfigObject({
    ...currentValue.value,
    [item.key]: value
  }))
}

function formatJsonValue(value) {
  if (value === undefined || value === null || value === '') return ''
  return typeof value === 'string' ? value : JSON.stringify(value, null, 2)
}

function updateJsonValue(item, value) {
  if (!value) {
    updateValue(item, null)
    return
  }
  try {
    updateValue(item, JSON.parse(value))
  } catch {
    ElMessage.warning(`${item.label}不是合法 JSON`)
  }
}
</script>

<style scoped>
.config-schema-editor {
  width: 100%;
}

.config-help {
  width: 100%;
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}
</style>
