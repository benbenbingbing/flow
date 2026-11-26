<template>
  <div class="node-value-linkage">
    <el-form-item label="值联动方式">
      <el-radio-group v-model="valueLinkageMode" :disabled="disabled" aria-label="值联动方式">
        <el-radio value="none">不启用</el-radio>
        <el-radio value="field">字段映射</el-radio>
        <el-radio value="formula">公式计算</el-radio>
      </el-radio-group>
    </el-form-item>
    <template v-if="valueLinkageMode === 'field'">
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
          <template v-if="model.fallback">
            <div class="mapping-row">
              <span class="fallback-source">其他值（未匹配时）</span>
              <span>→</span>
              <el-input
                v-model="model.fallback.targetValue"
                :disabled="disabled"
                aria-label="其他值的回填值"
                placeholder="当前字段的值，留空则清空"
                @input="persist('value')"
              />
              <el-button :disabled="disabled" type="danger" text aria-label="删除其他值映射" @click="setFallback(null)"><el-icon><Delete /></el-icon></el-button>
            </div>
            <el-checkbox v-model="model.fallback.includeEmpty" :disabled="disabled" @change="persist('value')">空值也按其他处理</el-checkbox>
          </template>
          <div class="mapping-actions">
            <el-button :disabled="disabled" type="primary" text @click="addRow('value')"><el-icon><Plus /></el-icon>添加映射</el-button>
            <el-button v-if="!model.fallback" :disabled="disabled" type="primary" text @click="setFallback({ targetValue: '', includeEmpty: false })"><el-icon><Plus /></el-icon>添加其他值映射</el-button>
          </div>
        </div>
      </el-form-item>
    </template>
    <el-form-item v-else-if="valueLinkageMode === 'formula'" label="公式">
      <div class="linkage-description">随依赖字段变化实时计算</div>
      <FormNodeFormulaInput v-model="model.formula" :fields="availableFields" :disabled="disabled"
        :placeholder="formulaPlaceholder" @update:model-value="persist('value')" />
    </el-form-item>

    <SettingsCapability :disabled="disabled || !supportsOptions" reason="仅选择类组件支持选项联动">
      <div class="options-linkage">
        <div class="linkage-heading">
          <h4>选项联动</h4>
          <el-switch v-model="model.optionsEnabled" :disabled="disabled || !supportsOptions" aria-label="启用选项联动" @change="toggleOptions" />
        </div>
        <p class="linkage-description">{{ inheritsOptionsInterface ? '依赖字段变化时，按参数映射重新查询实体只读接口。' : '根据依赖字段的值过滤当前字段的可选项。' }}</p>
        <el-form-item v-if="model.optionsEnabled && model.optionsMode === 'query'" label="参数映射">
          <div class="linkage-rows option-query-mappings">
            <div v-for="(row, index) in model.parameterMappings" :key="index" class="mapping-row">
              <el-select v-model="row.fieldCode" filterable aria-label="依赖字段" placeholder="选择依赖字段" @change="persist('options')">
                <el-option v-for="item in availableFields" :key="fieldKey(item)" :label="item.fieldName || item.fieldLabel || fieldKey(item)" :value="fieldKey(item)" />
              </el-select>
              <el-input v-model.trim="row.parameter" aria-label="接口参数名" placeholder="接口参数名" @input="persist('options')" />
              <el-button type="danger" text aria-label="删除参数映射" @click="removeOptionParameter(index)"><el-icon><Delete /></el-icon></el-button>
            </div>
            <div class="mapping-actions">
              <el-button type="primary" text @click="addOptionParameter"><el-icon><Plus /></el-icon>添加参数映射</el-button>
            </div>
            <div class="linkage-description">接口从 input.optionParams 读取映射值。</div>
          </div>
        </el-form-item>
        <template v-else-if="model.optionsEnabled">
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
import FormNodeFormulaInput from './FormNodeFormulaInput.vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import SettingsCapability from '@/components/SettingsCapability.vue'
import { useFieldValueLinkage } from '@/composables/useFieldValueLinkage'
import { useFieldDictionaryOptions } from '@/composables/useFieldDictionaryOptions'
import { getDefaultFormFieldComponentType } from '@flow/workflow-core/extensions/core/fieldPolicy'

const props = defineProps({
  field: { type: Object, required: true },
  fields: { type: Array, default: () => [] },
  inheritsOptionsInterface: Boolean,
  disabled: { type: Boolean, default: false }
})
const fieldKey = field => field.fieldCode || field.fieldKey
const availableFields = computed(() => props.fields.filter(item => item.uiConfigurable !== false && fieldKey(item) !== fieldKey(props.field)))
const supportsOptions = computed(() => ['select', 'select_multiple', 'radio', 'checkbox', 'cascader'].includes(
  String(props.field.componentType || getDefaultFormFieldComponentType(props.field.fieldType)).toLowerCase()))
const dictionaryOptions = useFieldDictionaryOptions(() => [props.field])
const currentOptions = computed(() => dictionaryOptions.value[props.field.dictType] || [])
const { model, persist: persistDraft } = useFieldValueLinkage(() => props.field, () => props.disabled)
// 选中项决定节点草稿中的取值规则，暂未选中的输入保留在编辑器草稿中，方便保存前切回。
const valueLinkageMode = computed({
  get: () => model.value.valueEnabled ? model.value.sourceType : 'none',
  set(mode) {
    if (props.disabled) return
    model.value.valueEnabled = mode !== 'none'
    if (model.value.valueEnabled) model.value.sourceType = mode
    persist('value')
  }
})
const formulaPlaceholder = computed(() => String(props.field.fieldType).toUpperCase() === 'DATE'
  ? '例如 ADD_DAYS(TODAY(), 3)'
  : '例如 ${quantity} * ${price}')

function persist(group) {
  if (props.disabled || group === 'options' && !supportsOptions.value) return
  persistDraft(group)
}
function toggleOptions(enabled) {
  if (enabled) model.value.optionsMode = props.inheritsOptionsInterface ? 'query' : 'filter'
  persist('options')
}
function addOptionParameter() {
  model.value.parameterMappings.push({ fieldCode: '', parameter: '' })
  persist('options')
}
function removeOptionParameter(index) {
  model.value.parameterMappings.splice(index, 1)
  persist('options')
}
function setFallback(fallback) {
  if (props.disabled) return
  model.value.fallback = fallback
  persist('value')
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
.mapping-row > .el-input, .fallback-source { flex: 1; min-width: 0; }
.option-query-mappings .mapping-row > .el-select { flex: 1; min-width: 0; }
.fallback-source { box-sizing: border-box; padding: 0 11px; border-radius: 4px; background: var(--el-fill-color-light); color: var(--el-text-color-regular); }
.mapping-actions { display: flex; flex-wrap: wrap; }
.linkage-rows > .el-button { align-self: flex-start; }
.node-value-linkage :deep(.el-select) { width: 100%; }
</style>
