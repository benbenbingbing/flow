<template>
  <div class="selection-return-editor">
    <div class="mapping-header">
      <span>选择要带回的字段 <span class="mapping-count">（{{ mappings.length }} 项）</span></span>
      <el-button type="primary" link :icon="Plus" :disabled="!!readError" @click="add">添加映射</el-button>
    </div>
    <div v-if="readError" class="mapping-error" role="alert">{{ readError }}</div>
    <template v-else>
      <div v-if="!mappings.length" class="mapping-empty">
        暂未配置附加返回字段。需要带回名称、电话等信息时，点击“添加映射”。
      </div>
      <template v-else>
        <div class="mapping-columns" aria-hidden="true"><span>来源字段</span><span>返回名称</span><span>操作</span></div>
        <div v-for="(mapping, index) in mappings" :key="index" class="mapping-row">
          <label class="mapping-control">
            <span class="compact-label">来源字段</span>
            <el-select
              :model-value="selectionMappingSource(mapping)"
              filterable allow-create default-first-option
              placeholder="选择来源字段"
              :aria-label="`第 ${index + 1} 条映射的来源字段`"
              @update:model-value="value => patch(index, 'sourceField', value)"
            >
              <el-option
                v-if="selectionMappingSource(mapping) && !fieldOptions.some(option => option.value === selectionMappingSource(mapping))"
                :value="selectionMappingSource(mapping)" :label="`${selectionMappingSource(mapping)}（已有路径）`"
              />
              <el-option v-for="option in fieldOptions" :key="option.value" :value="option.value" :label="`${option.label}（${option.value}）`" />
            </el-select>
          </label>
          <label class="mapping-control">
            <span class="compact-label">返回名称</span>
            <el-input
              :model-value="selectionMappingTarget(mapping)"
              placeholder="例如 customerName"
              :aria-label="`第 ${index + 1} 条映射的返回名称`"
              @update:model-value="value => patch(index, 'targetField', value)"
            />
          </label>
          <el-button type="danger" link :aria-label="`删除第 ${index + 1} 条映射`" @click="remove(index)">删除</el-button>
        </div>
      </template>
      <div v-if="validationError" class="mapping-error" role="alert">{{ validationError }}</div>
      <details v-if="mappings.length && !validationError" class="mapping-preview">
        <summary>查看返回字段</summary>
        <ul>
          <li v-for="(mapping, index) in mappings" :key="index">
            {{ sourceLabel(mapping) }} → <code>selectionData.{{ selectionMappingTarget(mapping) }}</code>
          </li>
        </ul>
      </details>
    </template>
    <div class="mapping-note">
      返回名称由接收页面使用，例如 customerName；需要分组时可填写 customer.name。来源字段也可搜索或输入完整路径。
      单选和多选均逐条返回；表单自动回填请在引用字段的“配置快捷回填”中设置。保存列表设置后还需发布生效。
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import {
  readSelectionReturnMappings,
  selectionMappingSource,
  selectionMappingTarget,
  selectionReturnFieldOptions,
  updateSelectionReturnMapping,
  validateSelectionReturnMappings
} from '@/shared/selection-return-mapping-editor'

const props = defineProps({
  modelValue: { type: String, default: '[]' },
  fields: { type: Array, default: () => [] },
  systemEntity: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])
const parsed = computed(() => {
  try { return { mappings: readSelectionReturnMappings(props.modelValue), error: '' } }
  catch (error) { return { mappings: [], error: error.message } }
})
const mappings = computed(() => parsed.value.mappings)
const readError = computed(() => parsed.value.error)
const fieldOptions = computed(() => selectionReturnFieldOptions(props.fields, props.systemEntity))
const validationError = computed(() => {
  try { validateSelectionReturnMappings(props.modelValue); return '' }
  catch (error) { return error.message }
})
const sourceLabel = mapping => fieldOptions.value.find(option => option.value === selectionMappingSource(mapping))?.label || selectionMappingSource(mapping)

/** 保留字符串模型，使草稿差异、离页提醒和现有保存协议继续记录未填完的配置行。 */
function update(mappings) { emit('update:modelValue', JSON.stringify(mappings, null, 2)) }
function add() { update([...mappings.value, { sourceField: '', targetField: '' }]) }
function remove(index) { update(mappings.value.filter((_, i) => i !== index)) }
function patch(index, key, value) {
  update(mappings.value.map((mapping, i) => i === index ? updateSelectionReturnMapping(mapping, key, value) : mapping))
}
</script>

<style scoped>
.selection-return-editor { width: 100%; max-width: 1100px; min-width: 0; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; padding: 12px 16px; box-sizing: border-box; container-type: inline-size; }
.mapping-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; font-weight: 500; }
.mapping-count, .mapping-note { color: var(--el-text-color-secondary); font-size: 12px; font-weight: normal; }
.mapping-columns, .mapping-row { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 40px; gap: 12px; align-items: center; }
.mapping-columns { color: var(--el-text-color-secondary); font-size: 12px; margin-bottom: 6px; }
.mapping-row + .mapping-row { margin-top: 10px; }
.mapping-control { min-width: 0; }
.mapping-control :deep(.el-select) { width: 100%; }
.compact-label { display: none; }
.mapping-empty { padding: 14px; background: var(--el-fill-color-light); color: var(--el-text-color-secondary); border-radius: 4px; text-align: center; }
.mapping-note { margin-top: 12px; line-height: 1.7; }
.mapping-error { margin-top: 10px; color: var(--el-color-danger); font-size: 12px; line-height: 1.6; }
.mapping-preview { margin-top: 12px; font-size: 12px; line-height: 1.8; overflow-wrap: anywhere; }
.mapping-preview summary { cursor: pointer; color: var(--el-color-primary); }
.mapping-preview ul { padding-left: 20px; margin: 6px 0 0; }
@container (max-width: 520px) {
  .mapping-columns { display: none; }
  .mapping-row { grid-template-columns: minmax(0, 1fr) 40px; align-items: end; }
  .mapping-control:nth-child(2) { grid-column: 1; grid-row: 2; }
  .mapping-row > .el-button { grid-column: 2; grid-row: 1; }
  .mapping-row + .mapping-row { margin-top: 16px; padding-top: 12px; border-top: 1px solid var(--el-border-color-lighter); }
  .compact-label { display: block; font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 4px; }
}
</style>
