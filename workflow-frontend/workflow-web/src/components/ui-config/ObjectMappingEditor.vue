<template>
  <div class="mapping-editor">
    <div class="mapping-editor__toolbar">
      <div>
        <div class="mapping-editor__title">{{ title }}</div>
        <div v-if="description" class="mapping-editor__description">
          {{ description }}
        </div>
      </div>
      <el-button :icon="Plus" @click="addRow">{{ addText }}</el-button>
    </div>

    <div v-if="rows.length" class="mapping-editor__table">
      <div class="mapping-editor__head">
        <span>{{ keyLabel }}</span>
        <span>{{ valueLabel }}</span>
        <span>操作</span>
      </div>
      <div
        v-for="(row, index) in rows"
        :key="row.rowKey"
        class="mapping-editor__row"
      >
        <el-input
          v-model="row.key"
          :placeholder="keyPlaceholder"
          @input="emitValue"
        />
        <el-select
          v-if="valueOptions.length"
          v-model="row.value"
          :placeholder="valuePlaceholder"
          style="width: 100%"
          @change="emitValue"
        >
          <el-option
            v-for="option in valueOptions"
            :key="String(option.value)"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-input
          v-else
          v-model="row.value"
          :placeholder="valuePlaceholder"
          @input="emitValue"
        />
        <el-button
          text
          circle
          :icon="Delete"
          :aria-label="`删除第 ${index + 1} 条映射`"
          :title="`删除第 ${index + 1} 条映射`"
          @click="removeRow(index)"
        />
      </div>
    </div>
    <el-empty
      v-else
      :description="emptyText"
      :image-size="46"
      class="mapping-editor__empty"
    />

    <div v-if="duplicateKeys.length" class="mapping-editor__error">
      原始值不能重复：{{ duplicateKeys.join('、') }}
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import {
  mappingObjectToRows,
  mappingRowsToObject
} from '@/shared/list-column-template'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  title: { type: String, default: '映射配置' },
  description: { type: String, default: '' },
  keyLabel: { type: String, default: '原始值' },
  valueLabel: { type: String, default: '显示值' },
  keyPlaceholder: { type: String, default: '例如 ACTIVE' },
  valuePlaceholder: { type: String, default: '例如 启用' },
  addText: { type: String, default: '添加映射' },
  emptyText: { type: String, default: '暂无映射，未配置的值将按原值显示' },
  valueOptions: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue'])
const rows = ref([])
let nextRowKey = 1
let syncing = false

const duplicateKeys = computed(() => {
  const counts = new Map()
  rows.value.forEach((row) => {
    const key = String(row.key || '').trim()
    if (key) counts.set(key, (counts.get(key) || 0) + 1)
  })
  return Array.from(counts.entries())
    .filter(([, count]) => count > 1)
    .map(([key]) => key)
})

watch(
  () => props.modelValue,
  (value) => {
    const next = mappingObjectToRows(value)
    const currentObject = mappingRowsToObject(rows.value)
    const nextObject = mappingRowsToObject(next)
    if (JSON.stringify(currentObject) === JSON.stringify(nextObject)) return
    syncing = true
    rows.value = next.map(row => ({ ...row, rowKey: nextRowKey++ }))
    syncing = false
  },
  { immediate: true, deep: true }
)

function addRow() {
  rows.value.push({
    rowKey: nextRowKey++,
    key: '',
    value: props.valueOptions[0]?.value ?? ''
  })
}

function removeRow(index) {
  rows.value.splice(index, 1)
  emitValue()
}

function emitValue() {
  if (syncing) return
  emit('update:modelValue', mappingRowsToObject(rows.value))
}
</script>

<style scoped>
.mapping-editor {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  overflow: hidden;
}

.mapping-editor__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  background: #f7f8fa;
  border-bottom: 1px solid #ebeef5;
}

.mapping-editor__title {
  color: #303133;
  font-weight: 600;
}

.mapping-editor__description {
  margin-top: 3px;
  color: #909399;
  font-size: 12px;
  line-height: 1.45;
}

.mapping-editor__table {
  width: 100%;
}

.mapping-editor__head,
.mapping-editor__row {
  display: grid;
  grid-template-columns: minmax(150px, 0.9fr) minmax(180px, 1.1fr) 52px;
  gap: 12px;
  align-items: center;
  padding: 9px 12px;
}

.mapping-editor__head {
  color: #606266;
  font-size: 12px;
  font-weight: 600;
  background: #fafafa;
}

.mapping-editor__row {
  border-top: 1px solid #ebeef5;
}

.mapping-editor__empty {
  padding: 12px 0 18px;
}

.mapping-editor__error {
  padding: 8px 12px;
  color: #f56c6c;
  font-size: 12px;
  background: #fef0f0;
  border-top: 1px solid #fde2e2;
}

@media (max-width: 720px) {
  .mapping-editor__head {
    display: none;
  }

  .mapping-editor__row {
    grid-template-columns: 1fr 44px;
  }

  .mapping-editor__row > :first-child {
    grid-column: 1 / 2;
  }

  .mapping-editor__row > :nth-child(2) {
    grid-column: 1 / 2;
  }

  .mapping-editor__row > :last-child {
    grid-column: 2;
    grid-row: 1 / 3;
  }
}
</style>
