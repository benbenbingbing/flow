<template>
  <div class="data-picker-settings">
    <el-form-item label="数据来源">
      <el-radio-group :model-value="sourceMode" @update:model-value="changeSource">
        <el-radio-button value="ENTITY_LIST">实体列表</el-radio-button>
        <el-radio-button value="INTERFACE">自定义接口</el-radio-button>
      </el-radio-group>
    </el-form-item>
    <template v-if="sourceMode === 'ENTITY_LIST'">
      <el-form-item label="目标实体">
        <EntityDefinitionPicker :model-value="listConfig.entityId" value-key="id" title="选择目标实体" clearable
          :query="{ status: 'PUBLISHED' }" @selected="selectEntity" />
      </el-form-item>
      <el-form-item label="选择列表">
        <el-select :model-value="listConfig.listKey" :loading="listLoading" :disabled="!listConfig.entityId"
          placeholder="选择已发布列表" filterable @update:model-value="changeList">
          <el-option v-for="list in lists" :key="list.listKey" :label="list.listName || list.listKey" :value="list.listKey" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="listConfig.listKey || listError" label="列表参数映射">
        <el-alert v-if="listError" :title="listError" type="warning" :closable="false" />
        <PageParameterMappingEditor v-else-if="publishedList" :model-value="listConfig.parameterMappings || []"
          :schema="listParameterSchema" :source-fields="sourceFields" :entity-fields="sourceEntityFields"
          reference-list @update:model-value="updateList({ parameterMappings: $event })" />
        <span v-else-if="listLoading">正在读取列表配置…</span>
      </el-form-item>
    </template>
    <template v-else>
      <el-form-item label="字段描述">
        <el-select :model-value="schemaMode" @update:model-value="changeSchemaMode">
          <el-option label="通用默认（id / name）" value="DEFAULT" />
          <el-option label="手动输入" value="MANUAL" />
          <el-option label="从接口字段选择" value="QUERY_SCHEMA" />
          <el-option label="通过接口获取" value="INTERFACE" />
        </el-select>
      </el-form-item>
      <template v-for="usage in interfaceUsages" :key="usage.code">
        <el-form-item :label="usage.label">
          <el-select :model-value="binding(usage.code)?.extensionId" filterable clearable placeholder="请选择接口"
            @update:model-value="value => changeBinding(usage.code, value)">
            <el-option v-for="item in interfaces[usage.code] || []" :key="item.extensionId" :label="`${item.displayName} (${item.extensionKey})`" :value="item.extensionId" />
          </el-select>
          <details v-if="binding(usage.code)?.extensionId" class="picker-binding-input">
            <summary>输入映射</summary>
            <el-input :model-value="inputDrafts[usage.code] ?? JSON.stringify(binding(usage.code).inputMapping || {}, null, 2)"
              type="textarea" :rows="2" placeholder='{"filters.projectId":"data.project_id"}'
              @update:model-value="value => updateInput(usage.code, value)" />
            <small v-if="inputErrors[usage.code]" class="picker-error" role="alert">{{ inputErrors[usage.code] }}</small>
          </details>
        </el-form-item>
      </template>
      <el-form-item v-if="schemaMode === 'INTERFACE'" label="获取字段描述">
        <div class="picker-fetch">
          <el-input v-model="sampleText" type="textarea" :rows="2" aria-label="测试表单数据" placeholder="测试表单数据（可选）" />
          <el-button :loading="loading" :disabled="!formId" @click="loadSchema">获取字段描述</el-button>
          <span v-if="schema">{{ schema.fields.length }} 个字段</span>
        </div>
      </el-form-item>
      <template v-if="['MANUAL', 'QUERY_SCHEMA'].includes(schemaMode)">
        <el-form-item v-if="schemaMode === 'MANUAL'" label="字段列表">
          <div class="picker-fields">
            <div v-for="(item, index) in editableSchema.fields" :key="index" class="picker-field-row">
              <el-input :model-value="item.code" placeholder="字段编码" @change="value => updateField(index, 'code', value)" />
              <el-input :model-value="item.label" placeholder="字段名称" @change="value => updateField(index, 'label', value)" />
              <el-select :model-value="item.type" @update:model-value="value => updateField(index, 'type', value)">
                <el-option v-for="type in fieldTypes" :key="type" :label="type" :value="type" />
              </el-select>
              <el-button link type="danger" @click="removeField(index)">移除</el-button>
            </div>
            <el-button @click="setSchema({ fields: [...editableSchema.fields, { code: '', label: '', type: 'STRING' }] })">添加字段</el-button>
          </div>
        </el-form-item>
        <el-form-item v-else label="接口字段">
          <el-select :model-value="editableSchema.fields.map(field => field.code)" multiple filterable
            :disabled="!queryFields.length" :placeholder="queryFields.length ? '选择需要使用的字段' : '请选用声明了记录字段的分页接口'"
            @update:model-value="value => setFields(queryFields.filter(field => value.includes(field.code)))">
            <el-option v-for="item in queryFields" :key="item.code" :value="item.code" :label="fieldOptionLabel(item)" />
          </el-select>
        </el-form-item>
        <el-form-item label="记录标识字段">
          <el-select :model-value="editableSchema.valueField" placeholder="选择字符串字段" @update:model-value="value => setSchema({ valueField: value })">
            <el-option v-for="item in editableFields.filter(field => field.type === 'STRING')" :key="item.code" :value="item.code" :label="fieldOptionLabel(item)" />
          </el-select>
        </el-form-item>
        <el-form-item label="显示字段">
          <el-select :model-value="editableSchema.labelField" placeholder="选择显示名称字段" @update:model-value="value => setSchema({ labelField: value })">
            <el-option v-for="item in editableFields" :key="item.code" :value="item.code" :label="fieldOptionLabel(item)" />
          </el-select>
        </el-form-item>
        <el-form-item label="弹窗列">
          <el-select :model-value="editableSchema.columns.map(column => column.field)" multiple placeholder="选择并排列展示字段"
            @update:model-value="value => setSchema({ columns: value.map(field => ({ field })) })">
            <el-option v-for="item in editableFields" :key="item.code" :value="item.code" :label="fieldOptionLabel(item)" />
          </el-select>
        </el-form-item>
      </template>
    </template>
    <el-form-item v-if="fieldType === 'STRING'" label="写入方式">
      <el-select :model-value="writeMode" @update:model-value="value => update({ pickerValueMode: value, pickerWriteField: undefined })">
        <el-option label="记录标识" value="KEY" />
        <el-option label="指定字段值" value="FIELD" />
      </el-select>
    </el-form-item>
    <el-form-item v-if="writeMode === 'FIELD'" label="写入当前字段">
      <el-select :model-value="writeFields.some(item => item.code === config.pickerWriteField) ? config.pickerWriteField : undefined"
        :placeholder="writeFields.length ? '选择取值来源字段' : '数据源尚无兼容字段'" :disabled="!writeFields.length" filterable
        @update:model-value="value => update({ pickerValueMode: 'FIELD', pickerWriteField: value })">
        <el-option v-for="item in writeFields" :key="item.code" :label="fieldOptionLabel(item)" :value="item.code" />
      </el-select>
    </el-form-item>
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
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { safeParseConfig, stringifyConfig } from '@flow/workflow-core/config-runtime'
import { DATA_PICKER_USAGES, DEFAULT_PICKER_SCHEMA, isEntityListPicker, normalizePickerSchema,
  pickerListSchema, pickerQuerySchemaFields, pickerSchemaSource, pickerWriteMode, pickerWriteTypeCompatible } from '@flow/workflow-core/data-picker'
import { createFormDataSourceRuntime } from '@flow/workflow-core/form-runtime'
import { areEntitySelectionTypesCompatible } from '@flow/workflow-core/entity-selection-mapping'
import { listFilterParameterSchema } from '@flow/workflow-core/page-parameters'
import { uiExtensionApi } from '@/api/uiConfig'
import { entityListConfigApi } from '@/api/entityListConfig'
import { getEntityFields } from '@/api/entityForm'
import { parseJsonConfig } from '@/utils/jsonConfig'
import { normalizeRows } from '@/components/related-content/relatedContentOptions.js'
import { loadPublishedListSettings } from '@/views/form-designer/useReferenceListSettings'
import EntityDefinitionPicker from '@/components/EntityDefinitionPicker.vue'
import PageParameterMappingEditor from '@/components/page-parameters/PageParameterMappingEditor.vue'

const props = defineProps({
  field: { type: Object, required: true }, formId: [String, Number], fields: { type: Array, default: () => [] },
  interfaces: { type: Object, default: () => ({}) }, sourceFields: { type: Array, default: () => [] },
  sourceEntityFields: { type: Array, default: () => [] }
})
const loading = ref(false), sampleText = ref('{}'), listLoading = ref(false), listError = ref('')
const lists = ref([]), publishedList = ref(null), listEntityFields = ref([])
const inputDrafts = ref({}), inputErrors = ref({})
const config = computed(() => safeParseConfig(props.field.componentProps))
const sourceMode = computed(() => isEntityListPicker(config.value) ? 'ENTITY_LIST' : 'INTERFACE')
const listConfig = computed(() => config.value.pickerList || {})
const listParameterSchema = computed(() => listFilterParameterSchema(publishedList.value?.viewConfig))
const schemaMode = computed(() => pickerSchemaSource(config.value, Boolean(binding('FIELD_PICKER_SCHEMA')?.extensionId)))
const schema = computed(() => sourceMode.value === 'ENTITY_LIST'
  ? (publishedList.value ? pickerListSchema(publishedList.value.fields, listEntityFields.value) : config.value.pickerSchema)
  : schemaMode.value === 'DEFAULT' ? DEFAULT_PICKER_SCHEMA : config.value.pickerSchema)
const fieldType = computed(() => String(props.field.fieldType || '').toUpperCase())
const writeMode = computed(() => pickerWriteMode(config.value, fieldType.value))
const writeFields = computed(() => (schema.value?.fields || []).filter(item => pickerWriteTypeCompatible(item.type, fieldType.value)))
const fieldTypes = ['STRING', 'TEXT', 'INTEGER', 'LONG', 'DECIMAL', 'DOUBLE', 'NUMBER', 'BOOLEAN', 'DATE', 'DATETIME', 'RICH_TEXT']
const emptySchema = { fields: [], columns: [], valueField: '', labelField: '' }
const editableSchema = computed(() => config.value.pickerSchema || emptySchema)
const editableFields = computed(() => editableSchema.value.fields.filter(field => field.code))
const fieldOptionLabel = item => item.label && item.label !== item.code ? `${item.label} (${item.code})` : item.code
const interfaceUsages = computed(() => [
  { code: 'FIELD_PICKER_QUERY', label: '分页查询接口' },
  { code: 'FIELD_PICKER_RESOLVE', label: '按键解析接口' },
  ...(schemaMode.value === 'INTERFACE' ? [{ code: 'FIELD_PICKER_SCHEMA', label: '字段描述接口' }] : [])
])
const queryFields = computed(() => {
  const id = binding('FIELD_PICKER_QUERY')?.extensionId
  return pickerQuerySchemaFields((props.interfaces.FIELD_PICKER_QUERY || []).find(item => item.extensionId === id)?.outputSchema)
})

watch(() => [props.field, sourceMode.value, listConfig.value.entityId], async ([, mode, entityId], _, onCleanup) => {
  let current = true
  onCleanup(() => { current = false })
  lists.value = []
  listEntityFields.value = []
  listError.value = ''
  listLoading.value = mode === 'ENTITY_LIST' && Boolean(entityId)
  if (!listLoading.value) return
  try {
    const [listRows, entityFields] = await Promise.all([entityListConfigApi.getByEntityId(entityId), getEntityFields(entityId)])
    if (!current) return
    listEntityFields.value = normalizeRows(entityFields)
    lists.value = normalizeRows(listRows).filter(list => list.activeReleaseId && list.publishedVersion > 0)
  } catch (error) {
    if (current) listError.value = error.message || '读取实体的已发布列表失败'
  } finally {
    if (current) listLoading.value = false
  }
})

const selectedList = computed(() => lists.value.find(list => list.listKey === listConfig.value.listKey))
watch(selectedList, async (list, _, onCleanup) => {
  let current = true
  onCleanup(() => { current = false })
  publishedList.value = null
  if (!list) return
  listError.value = ''
  listLoading.value = true
  try {
    const published = await loadPublishedListSettings(list)
    if (!current) return
    publishedList.value = published
  } catch (error) {
    if (current) listError.value = error.message || '读取列表发布配置失败'
  } finally {
    if (current) listLoading.value = false
  }
})
watch(() => props.field, field => {
  inputDrafts.value = { ...field.pickerInputDrafts }
  inputErrors.value = { ...field.pickerInputErrors }
  sampleText.value = '{}'
}, { immediate: true })

function updateList(patch) { update({ pickerList: { ...listConfig.value, ...patch } }) }
function selectEntity(entity) {
  if (String(listConfig.value.entityId || '') === String(entity?.id || '')) return
  update({ pickerList: { entityId: entity?.id, entityCode: entity?.entityCode }, pickerSchema: undefined, pickerWriteField: undefined, pickerMappings: [] })
}
function changeList(value) {
  if (listConfig.value.listKey === value) return
  update({ pickerList: { ...listConfig.value, listKey: value, parameterMappings: [] }, pickerSchema: undefined, pickerWriteField: undefined, pickerMappings: [] })
}
function binding(usage) {
  const value = safeParseConfig(props.field.dataSourceBindings)[usage]
  return Array.isArray(value) ? value[0] : value
}
function writeBinding(usage, value) {
  const bindings = { ...safeParseConfig(props.field.dataSourceBindings) }
  if (value) bindings[usage] = value
  else delete bindings[usage]
  props.field.dataSourceBindings = bindings
  // 旧版由通用绑定编辑器持有临时值；打开专用配置后，写入以当前绑定对象为准。
  if (props.field.dataSourceUsage === usage) props.field.dataSourceUsage = ''
}
function changeSource(value) {
  for (const usage of DATA_PICKER_USAGES) writeBinding(usage, null)
  inputDrafts.value = {}; inputErrors.value = {}
  rememberInputDrafts()
  update({ pickerSource: value, pickerSchemaSource: 'DEFAULT', pickerList: undefined, pickerSchema: undefined, pickerWriteField: undefined, pickerMappings: [] })
}
function changeSchemaMode(value) {
  if (value !== 'INTERFACE') writeBinding('FIELD_PICKER_SCHEMA', null)
  delete inputDrafts.value.FIELD_PICKER_SCHEMA
  delete inputErrors.value.FIELD_PICKER_SCHEMA
  rememberInputDrafts()
  update({ pickerSchemaSource: value, pickerWriteField: undefined, pickerMappings: [],
    pickerSchema: value === 'MANUAL' ? { ...DEFAULT_PICKER_SCHEMA, schemaVersion: 'manual-v1' } : undefined })
}
function changeBinding(usage, id) {
  writeBinding(usage, id ? { extensionId: id, inputMapping: binding(usage)?.inputMapping || {} } : null)
  delete inputDrafts.value[usage]
  delete inputErrors.value[usage]
  rememberInputDrafts()
  if (usage === 'FIELD_PICKER_SCHEMA' || usage === 'FIELD_PICKER_QUERY' && schemaMode.value === 'QUERY_SCHEMA') {
    update({ pickerSchema: undefined, pickerWriteField: undefined, pickerMappings: [] })
  }
}
/** 草稿绑定沿用现有输入映射格式。非法 JSON 留在控件内，节点保存会提示修正。 */
function updateInput(usage, value) {
  inputDrafts.value[usage] = value
  try {
    const inputMapping = parseJsonConfig(value, { fieldName: '数据选择器输入映射' })
    delete inputErrors.value[usage]
    writeBinding(usage, { ...binding(usage), inputMapping })
  } catch (error) {
    inputErrors.value[usage] = error.message
  }
  rememberInputDrafts()
}
function rememberInputDrafts() {
  props.field.pickerInputErrors = { ...inputErrors.value }
  props.field.pickerInputDrafts = { ...inputDrafts.value }
}
function setSchema(patch) {
  update({ pickerSchema: { ...editableSchema.value, schemaVersion: schemaMode.value === 'MANUAL' ? 'manual-v1' : 'query-schema-v1', ...patch } })
}
function setFields(fields) {
  const codes = new Set(fields.map(field => field.code))
  setSchema({ fields,
    valueField: fields.find(field => field.code === editableSchema.value.valueField && field.type === 'STRING')?.code || '',
    labelField: codes.has(editableSchema.value.labelField) ? editableSchema.value.labelField : '',
    columns: editableSchema.value.columns.filter(column => codes.has(column.field)) })
}
function updateField(index, key, value) {
  setFields(editableSchema.value.fields.map((field, position) => position === index ? { ...field, [key]: value } : field))
}
function removeField(index) { setFields(editableSchema.value.fields.filter((_field, position) => position !== index)) }
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
  const requestBinding = binding('FIELD_PICKER_SCHEMA')
  const fingerprint = JSON.stringify(requestBinding)
  if (inputErrors.value.FIELD_PICKER_SCHEMA) { ElMessage.warning(inputErrors.value.FIELD_PICKER_SCHEMA); return }
  if (!requestBinding?.extensionId) { ElMessage.warning('请先绑定字段描述接口并保存当前字段'); return }
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
    if (props.field !== field || sourceMode.value !== 'INTERFACE' || schemaMode.value !== 'INTERFACE'
        || JSON.stringify(binding('FIELD_PICKER_SCHEMA')) !== fingerprint) return
    update({ pickerSchema: result })
    ElMessage.success('字段描述已获取，请检查回填映射后保存当前字段并发布')
  } catch (error) { ElMessage.error(error.message || '获取字段描述失败') }
  finally { loading.value = false }
}
</script>
<style scoped>
.data-picker-settings :deep(.el-select), .data-picker-settings :deep(.entity-definition-picker) { width: 100%; }
.picker-fields, .picker-binding-input, .picker-fetch { width: 100%; }
.picker-field-row { display: grid; grid-template-columns: 1fr 1fr minmax(100px, .6fr) auto; gap: 8px; margin-bottom: 8px; }
.picker-binding-input { margin-top: 6px; }
.picker-binding-input summary { cursor: pointer; font-size: 13px; color: var(--el-text-color-secondary); }
.picker-fetch { display: flex; align-items: center; gap: 8px; }
.picker-error { color: var(--el-color-danger); }
.picker-schema-toolbar { display: flex; align-items: center; justify-content: space-between; margin: 12px 0; gap: 8px; }
.picker-mapping { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 8px; padding: 10px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.picker-mapping :deep(.el-select) { min-width: 0; }
.form-tip { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
</style>
