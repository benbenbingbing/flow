<template>
  <div class="data-picker-settings">
    <el-alert type="info" :closable="false" title="在下方数据源绑定中分别配置字段描述、分页查询、按键解析三个只读接口。保存字段后获取字段描述，再配置回填并保存、发布。PC 与移动端共用这些配置。" />
    <div class="picker-schema-toolbar">
      <span>{{ schema ? `${schema.fields.length} 个字段 · 版本 ${schema.schemaVersion}` : '尚未获取字段描述' }}</span>
      <el-button :loading="loading" :disabled="!formId" @click="loadSchema">获取字段描述</el-button>
    </div>
    <el-form-item label="测试表单数据">
      <el-input v-model="sampleText" type="textarea" :rows="2" placeholder='{"company_code":"C001"}' />
      <div class="form-tip">仅用于按输入映射获取字段描述，不保存为业务数据；例如描述接口需要公司参数时填写。</div>
    </el-form-item>
    <p v-if="schema" class="form-tip">主值：{{ schema.valueField }}；显示名称：{{ schema.labelField }}。非展示列也可用于回填。</p>
    <div class="picker-schema-toolbar">
      <strong>选择后回填</strong>
      <el-button :disabled="!schema" @click="addMapping">添加映射</el-button>
    </div>
    <div v-for="(mapping, index) in mappings" :key="index" class="picker-mapping">
      <el-select :model-value="mapping.sourcePath" placeholder="来源字段" @update:model-value="value => updateMapping(index, 'sourcePath', value)">
        <el-option v-for="source in schema?.fields || []" :key="source.code" :label="source.label" :value="`selection.data.${source.code}`" />
      </el-select>
      <el-select :model-value="mapping.targetPath" placeholder="当前表单目标字段" @update:model-value="value => updateMapping(index, 'targetPath', value)">
        <el-option v-for="target in targetFields" :key="target.fieldCode" :label="target.fieldLabel || target.fieldName || target.fieldCode" :value="`form.${target.fieldCode}`" :disabled="!compatible(mapping, target)" />
      </el-select>
      <el-select :model-value="mapping.overwrite" @update:model-value="value => updateMapping(index, 'overwrite', value)">
        <el-option label="始终覆盖" value="ALWAYS" /><el-option label="仅空值覆盖" value="IF_EMPTY" /><el-option label="覆盖前确认" value="CONFIRM" />
      </el-select>
      <el-select :model-value="mapping.clearOnEmpty" @update:model-value="value => updateMapping(index, 'clearOnEmpty', value)">
        <el-option label="来源为空时清空" :value="true" /><el-option label="来源为空时保留" :value="false" />
      </el-select>
      <el-button link type="danger" @click="update({ pickerMappings: mappings.filter((_item, position) => position !== index) })">删除</el-button>
    </div>
    <p class="form-tip">回填规则属于当前字段，随表单发布。清空选择默认保留其它字段，可在组件基础配置中开启清空处理。</p>
  </div>
</template>
<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { safeParseConfig, stringifyConfig } from '@flow/workflow-core/config-runtime'
import { normalizePickerSchema } from '@flow/workflow-core/data-picker'
import { createFormDataSourceRuntime } from '@flow/workflow-core/form-runtime'
import { areEntitySelectionTypesCompatible } from '@flow/workflow-core/entity-selection-mapping'
import { uiExtensionApi } from '@/api/uiConfig'
import { parseJsonConfig } from '@/utils/jsonConfig'

const props = defineProps({ field: { type: Object, required: true }, formId: [String, Number], fields: { type: Array, default: () => [] } })
const loading = ref(false), sampleText = ref('{}')
const config = computed(() => safeParseConfig(props.field.componentProps))
const schema = computed(() => config.value.pickerSchema)
const mappings = computed(() => config.value.pickerMappings || [])
const targetFields = computed(() => props.fields.filter(field => field.fieldCode && field.fieldCode !== props.field.fieldCode
  && (field.fieldId || field.bindingType === 'ENTITY_FIELD') && !['SUB_FORM', 'SUB_LIST', 'REPEATER'].includes(String(field.fieldType).toUpperCase())))
function update(patch) { props.field.componentProps = stringifyConfig({ ...config.value, ...patch }) }
function addMapping() { update({ pickerMappings: [...mappings.value, { sourcePath: '', targetPath: '', overwrite: 'IF_EMPTY', clearOnEmpty: false }] }) }
function updateMapping(index, key, value) { update({ pickerMappings: mappings.value.map((mapping, position) => position === index ? { ...mapping, [key]: value } : mapping) }) }
function compatible(mapping, target) {
  const source = schema.value?.fields.find(field => `selection.data.${field.code}` === mapping.sourcePath)
  return !source || areEntitySelectionTypesCompatible(source.type, target.fieldType)
}

/** 预览也走字段自己的草稿绑定鉴权；请求期间切换字段或修改接口时不把旧结构写回新节点。 */
async function loadSchema() {
  const field = props.field
  const configured = safeParseConfig(field.dataSourceBindings).FIELD_PICKER_SCHEMA
  const binding = Array.isArray(configured) ? configured[0] : configured
  if (!binding?.extensionId) { ElMessage.warning('请先绑定字段描述接口并保存当前字段'); return }
  loading.value = true
  try {
    const data = parseJsonConfig(sampleText.value, { fieldName: '测试表单数据' })
    // 与两端运行时使用同一输入映射，嵌套业务参数和 literal 不需要在设计器另写一套解释器。
    const runtime = createFormDataSourceRuntime({ getForm: () => ({ id: String(props.formId) }),
      executeDataSource: request => uiExtensionApi.preview(request.extensionId, {
        usage: request.bindingCode, configType: 'FORM', configId: request.ownerId, targetType: 'FIELD', targetKey: request.targetKey, input: request.input
      }) })
    const [description] = await runtime.executeOwnerUsage(field, 'FIELD_PICKER_SCHEMA', { record: data, input: { picker: {} } })
    const result = normalizePickerSchema(description)
    const current = safeParseConfig(field.dataSourceBindings).FIELD_PICKER_SCHEMA
    const currentBinding = Array.isArray(current) ? current[0] : current
    if (props.field !== field || currentBinding?.extensionId !== binding.extensionId) return
    update({ pickerSchema: result })
    ElMessage.success('字段描述已获取，请检查回填映射后保存当前字段并发布')
  } catch (error) { ElMessage.error(error.message || '获取字段描述失败') }
  finally { loading.value = false }
}
</script>
<style scoped>
.picker-schema-toolbar { display: flex; align-items: center; justify-content: space-between; margin: 12px 0; gap: 8px; }
.picker-mapping { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 8px; padding: 10px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.picker-mapping :deep(.el-select) { min-width: 0; }
.form-tip { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
</style>
