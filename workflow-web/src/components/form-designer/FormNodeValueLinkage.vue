<template>
  <div class="node-value-linkage">
    <div class="linkage-heading">
      <h4>值联动</h4>
      <el-switch v-model="model.valueEnabled" :disabled="disabled" aria-label="启用值联动" @change="persist('value')" />
    </div>
    <p class="linkage-description">根据其他字段的值映射或计算当前字段值。</p>
    <template v-if="model.valueEnabled">
      <el-form-item label="数据来源">
        <el-radio-group v-model="model.sourceType" :disabled="disabled" @change="persist('value')">
          <el-radio value="field">字段值</el-radio>
          <el-radio value="formula">计算公式</el-radio>
        </el-radio-group>
      </el-form-item>
      <template v-if="model.sourceType === 'field'">
        <el-form-item label="源字段">
          <el-select v-model="model.sourceField" :disabled="disabled" placeholder="选择源字段" @change="persist('value')">
            <el-option v-for="item in availableFields" :key="fieldKey(item)" :label="item.fieldName || item.fieldLabel || fieldKey(item)" :value="fieldKey(item)" />
          </el-select>
        </el-form-item>
        <el-form-item label="映射规则">
          <div class="linkage-rows">
            <div v-for="(row, index) in model.mappings" :key="index" class="mapping-row">
              <el-input v-model="row.sourceValue" :disabled="disabled" placeholder="源字段的值" @input="persist('value')" />
              <span>→</span>
              <el-input v-model="row.targetValue" :disabled="disabled" placeholder="当前字段的值" @input="persist('value')" />
              <el-button :disabled="disabled" type="danger" text aria-label="删除值映射" @click="removeRow('value', index)"><el-icon><Delete /></el-icon></el-button>
            </div>
            <el-button :disabled="disabled" type="primary" text @click="addRow('value')"><el-icon><Plus /></el-icon>添加映射</el-button>
          </div>
        </el-form-item>
      </template>
      <el-form-item v-else label="计算公式">
        <el-input v-model="model.formula" :disabled="disabled" type="textarea" :rows="3" placeholder="如：${quantity} * ${price}" @input="persist('value')" />
        <div class="linkage-description">支持 + - * / ( )，使用 ${字段编码} 引用字段值。</div>
      </el-form-item>
    </template>

    <SettingsCapability :disabled="disabled || !supportsOptions" reason="仅选择类组件支持选项联动">
      <div class="options-linkage">
        <div class="linkage-heading">
          <h4>选项联动</h4>
          <el-switch v-model="model.optionsEnabled" :disabled="disabled || !supportsOptions" aria-label="启用选项联动" @change="persist('options')" />
        </div>
        <p class="linkage-description">根据依赖字段的值过滤当前字段的可选项。</p>
        <template v-if="model.optionsEnabled">
          <el-form-item label="依赖字段">
            <el-select v-model="model.dependsOn" placeholder="选择依赖字段" @change="persist('options')">
              <el-option v-for="item in availableFields" :key="fieldKey(item)" :label="item.fieldName || item.fieldLabel || fieldKey(item)" :value="fieldKey(item)" />
            </el-select>
          </el-form-item>
          <el-form-item label="过滤规则">
            <div class="linkage-rows">
              <div v-for="(row, index) in model.filters" :key="index" class="filter-row">
                <div class="mapping-row">
                  <el-input v-model="row.dependValue" placeholder="依赖字段的值" @input="persist('options')" />
                  <el-button type="danger" text aria-label="删除选项过滤规则" @click="removeRow('options', index)"><el-icon><Delete /></el-icon></el-button>
                </div>
                <el-select v-model="row.allowedOptions" multiple placeholder="选择要显示的选项；留空不显示任何选项" @change="persist('options')">
                  <el-option v-for="option in currentOptions" :key="option.value" :label="option.label" :value="option.value" />
                </el-select>
              </div>
              <el-button type="primary" text @click="addRow('options')"><el-icon><Plus /></el-icon>添加过滤规则</el-button>
            </div>
          </el-form-item>
        </template>
      </div>
    </SettingsCapability>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import SettingsCapability from '@/components/SettingsCapability.vue'
import { useFieldValueLinkage } from '@/composables/useFieldValueLinkage'
import { safeParseConfig } from '@/shared/config-runtime'
import { getDefaultFormFieldComponentType } from '@/extensions/core/fieldPolicy.js'

const props = defineProps({
  field: { type: Object, required: true },
  fields: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false }
})
const fieldKey = field => field.fieldCode || field.fieldKey
const availableFields = computed(() => props.fields.filter(item => item.uiConfigurable !== false && fieldKey(item) !== fieldKey(props.field)))
const supportsOptions = computed(() => ['select', 'select_multiple', 'radio', 'checkbox', 'cascader'].includes(
  String(props.field.componentType || getDefaultFormFieldComponentType(props.field.fieldType)).toLowerCase()))
const currentOptions = computed(() => {
  const options = safeParseConfig(props.field.componentProps).options || props.field.options || props.field.optionsJson || []
  try {
    const parsed = typeof options === 'string' ? JSON.parse(options) : options
    return Array.isArray(parsed) ? parsed : []
  } catch { return [] }
})
const { model, persist: persistDraft } = useFieldValueLinkage(() => props.field, () => props.disabled)
function persist(group) {
  if (props.disabled || group === 'options' && !supportsOptions.value) return
  persistDraft(group)
}
function addRow(group) {
  if (props.disabled || group === 'options' && !supportsOptions.value) return
  if (group === 'value') model.value.mappings.push({ sourceValue: '', targetValue: '' })
  else model.value.filters.push({ dependValue: '', allowedOptions: [] })
  persist(group)
}
function removeRow(group, index) {
  if (props.disabled || group === 'options' && !supportsOptions.value) return
  ;(group === 'value' ? model.value.mappings : model.value.filters).splice(index, 1)
  persist(group)
}
</script>

<style scoped>
.linkage-heading, .mapping-row { display: flex; align-items: center; gap: 8px; }
.linkage-heading { justify-content: space-between; }
.linkage-heading h4 { margin: 0; font-size: 14px; }
.linkage-description { margin: 6px 0 12px; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
.options-linkage { border-top: 1px solid var(--el-border-color-lighter); padding-top: 16px; margin-top: 16px; }
.linkage-rows, .filter-row { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.mapping-row > .el-input { flex: 1; min-width: 0; }
.linkage-rows > .el-button { align-self: flex-start; }
.node-value-linkage :deep(.el-select) { width: 100%; }
</style>
