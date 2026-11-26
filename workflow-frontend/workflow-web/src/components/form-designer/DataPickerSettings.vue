<template>
  <div class="data-picker-settings">
    <section class="picker-settings-group" aria-label="数据来源">
      <h4 class="picker-settings-heading">数据来源</h4>
      <el-form-item label="来源类型">
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
        <DataPickerInterfaceBinding v-for="usage in recordInterfaceUsages" :key="usage.code"
          :label="usage.label" :help="usage.help" :placeholder="usage.placeholder" :binding="binding(usage.code)" :options="interfaces[usage.code]"
          :input-draft="inputDrafts[usage.code]" :input-error="inputErrors[usage.code]"
          @select="changeBinding(usage.code, $event)" @edit-input="updateInput(usage.code, $event)" />
      </template>
    </section>

    <section v-if="sourceMode === 'INTERFACE'" class="picker-settings-group" aria-label="返回字段">
      <h4 class="picker-settings-heading">返回字段</h4>
      <el-form-item label="定义方式">
        <el-select :model-value="schemaMode" @update:model-value="changeSchemaMode">
          <el-option label="通用默认（id / name）" value="DEFAULT" />
          <el-option label="手动输入" value="MANUAL" />
          <el-option label="从分页接口选择字段" value="QUERY_SCHEMA" />
          <el-option label="通过字段描述接口获取" value="INTERFACE" />
        </el-select>
      </el-form-item>
      <template v-if="schemaMode === 'INTERFACE'">
        <DataPickerInterfaceBinding label="字段描述接口" :help="descriptionHelp"
          :binding="binding('FIELD_PICKER_SCHEMA')" :options="interfaces.FIELD_PICKER_SCHEMA"
          :input-draft="inputDrafts.FIELD_PICKER_SCHEMA" :input-error="inputErrors.FIELD_PICKER_SCHEMA"
          @select="changeBinding('FIELD_PICKER_SCHEMA', $event)" @edit-input="updateInput('FIELD_PICKER_SCHEMA', $event)" />
        <el-form-item label="测试表单数据" class="picker-help-item">
          <template #label>
            <ConfigHelpLabel label="测试表单数据" :content="testDataHelp" />
          </template>
          <div class="picker-fetch">
            <el-input v-model="sampleText" type="textarea" :rows="2" aria-label="测试表单数据" placeholder="可选" />
            <el-button :loading="loading" :disabled="!formId" @click="loadSchema">获取字段描述</el-button>
            <span v-if="schema" class="picker-field-count">{{ schema.fields.length }} 个字段</span>
          </div>
        </el-form-item>
      </template>
      <template v-if="editsRecordSchema">
        <el-form-item v-if="schemaMode === 'MANUAL'" label="字段列表">
          <div class="picker-fields">
            <div v-if="editableSchema.fields.length" class="picker-field-row picker-grid-head" aria-hidden="true">
              <span>字段编码</span><span>字段名称</span><span>字段类型</span>
            </div>
            <div v-for="(item, index) in editableSchema.fields" :key="index" class="picker-field-row picker-grid-row">
              <el-input :model-value="item.code" aria-label="字段编码" placeholder="字段编码" @change="value => updateField(index, 'code', value)" />
              <el-input :model-value="item.label" aria-label="字段名称" placeholder="字段名称" @change="value => updateField(index, 'label', value)" />
              <el-select :model-value="item.type" aria-label="字段类型" @update:model-value="value => updateField(index, 'type', value)">
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
        <el-form-item label="记录标识">
          <el-select :model-value="editableSchema.valueField" placeholder="选择用于定位记录的字符串字段" @update:model-value="value => setSchema({ valueField: value })">
            <el-option v-for="item in editableFields.filter(field => field.type === 'STRING')" :key="item.code" :value="item.code" :label="fieldOptionLabel(item)" />
          </el-select>
        </el-form-item>
      </template>
    </section>

    <section v-if="sourceMode === 'INTERFACE' && editsRecordSchema" class="picker-settings-group" aria-label="弹窗与回显">
      <h4 class="picker-settings-heading">弹窗与回显</h4>
      <el-form-item label="显示名称字段">
        <el-select :model-value="editableSchema.labelField" placeholder="选择显示名称字段" @update:model-value="value => setSchema({ labelField: value })">
          <el-option v-for="item in editableFields" :key="item.code" :value="item.code" :label="fieldOptionLabel(item)" />
        </el-select>
      </el-form-item>
      <el-form-item label="弹窗展示列">
        <el-select :model-value="editableSchema.columns.map(column => column.field)" multiple placeholder="选择并排列展示字段"
          @update:model-value="value => setSchema({ columns: value.map(field => ({ field })) })">
          <el-option v-for="item in editableFields" :key="item.code" :value="item.code" :label="fieldOptionLabel(item)" />
        </el-select>
      </el-form-item>
    </section>

    <section class="picker-settings-group" aria-label="选择后写入">
      <h4 class="picker-settings-heading">选择后写入</h4>
      <el-form-item v-if="fieldType === 'STRING'" label="写入方式">
        <el-select :model-value="writeMode" @update:model-value="value => update({ pickerValueMode: value, pickerWriteField: undefined })">
          <el-option label="记录标识" value="KEY" />
          <el-option label="指定字段值" value="FIELD" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="writeMode === 'FIELD'" label="写入当前字段">
        <el-select :model-value="writeFields.some(item => item.code === config.pickerWriteField) ? config.pickerWriteField : undefined"
          :placeholder="writeFields.length ? '选择记录中的来源字段' : '数据源尚无兼容字段'" :disabled="!writeFields.length" filterable
          @update:model-value="value => update({ pickerValueMode: 'FIELD', pickerWriteField: value })">
          <el-option v-for="item in writeFields" :key="item.code" :label="fieldOptionLabel(item)" :value="item.code" />
        </el-select>
      </el-form-item>
      <el-form-item label="其他字段回填">
        <div class="picker-fields">
          <div v-if="mappings.length" class="picker-mapping-row picker-grid-head" aria-hidden="true">
            <span>记录来源字段</span><span></span><span>表单目标字段</span><span>覆盖策略</span><span>来源为空时</span>
          </div>
          <div v-for="(mapping, index) in mappings" :key="index" class="picker-mapping-row picker-grid-row">
            <el-select :model-value="mapping.sourcePath" aria-label="记录来源字段" placeholder="记录来源字段" filterable
              @update:model-value="value => updateMapping(index, 'sourcePath', value)">
              <el-option v-for="source in schema?.fields || []" :key="source.code" :label="fieldOptionLabel(source)" :value="`selection.data.${source.code}`" />
            </el-select>
            <span class="picker-mapping-arrow" aria-hidden="true">→</span>
            <el-select :model-value="mapping.targetPath" aria-label="表单目标字段" placeholder="表单目标字段" filterable
              @update:model-value="value => updateMapping(index, 'targetPath', value)">
              <el-option v-for="target in targetFields" :key="target.fieldCode" :label="targetOptionLabel(target)" :value="`form.${target.fieldCode}`" :disabled="!compatible(mapping, target)" />
            </el-select>
            <div class="picker-overwrite">
              <span class="picker-compact-label" aria-hidden="true">覆盖策略</span>
              <el-select :model-value="mapping.overwrite" aria-label="覆盖策略" @update:model-value="value => updateMapping(index, 'overwrite', value)">
                <el-option label="始终覆盖" value="ALWAYS" /><el-option label="仅空值覆盖" value="IF_EMPTY" /><el-option label="覆盖前确认" value="CONFIRM" />
              </el-select>
            </div>
            <div class="picker-empty-policy">
              <span class="picker-compact-label" aria-hidden="true">来源为空时</span>
              <el-select :model-value="mapping.clearOnEmpty" aria-label="来源为空时" @update:model-value="value => updateMapping(index, 'clearOnEmpty', value)">
                <el-option label="清空目标" :value="true" /><el-option label="保留目标" :value="false" />
              </el-select>
            </div>
            <el-button link type="danger" @click="update({ pickerMappings: mappings.filter((_item, position) => position !== index) })">删除</el-button>
          </div>
          <el-button :disabled="!schema" @click="addMapping">添加映射</el-button>
        </div>
      </el-form-item>
    </section>
  </div>
</template>
<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
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
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import EntityDefinitionPicker from '@/components/EntityDefinitionPicker.vue'
import PageParameterMappingEditor from '@/components/page-parameters/PageParameterMappingEditor.vue'
import DataPickerInterfaceBinding from './DataPickerInterfaceBinding.vue'

const props = defineProps({
  field: { type: Object, required: true }, formId: [String, Number], fields: { type: Array, default: () => [] },
  interfaces: { type: Object, default: () => ({}) }, sourceFields: { type: Array, default: () => [] },
  sourceEntityFields: { type: Array, default: () => [] }
})
const loading = ref(false), sampleText = ref('{}'), listLoading = ref(false), listError = ref('')
const lists = ref([]), publishedList = ref(null), listEntityFields = ref([])
const inputDrafts = ref({}), inputErrors = ref({})
let settingsActive = true
onBeforeUnmount(() => { settingsActive = false })
const config = computed(() => safeParseConfig(props.field.componentProps))
const sourceMode = computed(() => isEntityListPicker(config.value) ? 'ENTITY_LIST' : 'INTERFACE')
const listConfig = computed(() => config.value.pickerList || {})
const listParameterSchema = computed(() => listFilterParameterSchema(publishedList.value?.viewConfig))
const schemaMode = computed(() => pickerSchemaSource(config.value, Boolean(binding('FIELD_PICKER_SCHEMA')?.extensionId)))
const editsRecordSchema = computed(() => ['MANUAL', 'QUERY_SCHEMA'].includes(schemaMode.value))
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
const targetOptionLabel = item => fieldOptionLabel({ code: item.fieldCode, label: item.fieldLabel || item.fieldName })
const recordInterfaceUsages = [
  {
    code: 'FIELD_PICKER_QUERY',
    label: '分页查询接口',
    help: [
      '打开弹窗、搜索或翻页时调用。读取 input.picker 中的 pageNum（从 1 开始）、pageSize 和 keyword，按关键词筛选后分页。',
      '返回示例',
      '{ "records": [{ "id": "C001", "name": "华东客户" }], "total": 1 }',
      'records 是当前页，total 是筛选后的总条数。输出 Schema 将两者分别声明为 array 和 integer。'
    ].join('\n')
  },
  {
    code: 'FIELD_PICKER_RESOLVE',
    label: '按标识查询',
    placeholder: '选择根据记录标识查单条记录的接口',
    help: [
      '确认选择时按标识查最新记录用于回填。字段按「记录标识」保存时，打开已有表单也用它查名称。',
      '读取 input.picker.values，例如 ["C001"]，精确查找对应记录。输出 Schema 根类型为 array，查不到时返回 []。',
      '返回示例',
      '[{ "id": "C001", "name": "华东客户" }]'
    ].join('\n')
  }
]
const descriptionHelp = [
  '返回字段定义、记录标识、显示名称和弹窗列。字段编码与查询接口返回的记录一致，标识字段使用 STRING。',
  '最小返回示例',
  '{ "schemaVersion": "1", "valueField": "id", "labelField": "name",',
  '"fields": [{ "code": "id", "type": "STRING" }, { "code": "name", "label": "名称", "type": "STRING" }],',
  '"columns": [{ "field": "name" }] }'
].join('\n')
const testDataHelp = [
  '这里填预览时模拟的表单字段值，例如 {"project_id": "P001"}。字段描述接口的「输入映射」填 {"projectId": "data.project_id"}，接口即可从 input.projectId 读取。无需表单参数时保留 {}。',
  '绑定接口后点「确定」，再点「保存当前属性」。重新打开「组件配置」后点「获取字段描述」。获取后可配置写入和回填，并保存、发布表单。'
].join('\n')
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
  if (!requestBinding?.extensionId) { ElMessage.warning('请选择字段描述接口，确定后保存当前属性'); return }
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
    if (!settingsActive || props.field !== field || sourceMode.value !== 'INTERFACE' || schemaMode.value !== 'INTERFACE'
        || JSON.stringify(binding('FIELD_PICKER_SCHEMA')) !== fingerprint) return
    update({ pickerSchema: result })
    ElMessage.success('字段描述已获取，请检查回填映射后点确定，再保存当前属性')
  } catch (error) { if (settingsActive) ElMessage.error(error.message || '获取字段描述失败') }
  finally { loading.value = false }
}
</script>
<style scoped>
.data-picker-settings { display: grid; gap: 12px; container-type: inline-size; }
.picker-settings-group { min-width: 0; padding: 0 12px 12px; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; }
.picker-settings-heading {
  margin: 0 -12px 12px;
  padding: 7px 12px;
  border-radius: 5px 5px 0 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
}
.picker-settings-group > :deep(.el-form-item) { margin-bottom: 12px; }
.picker-settings-group > :deep(.el-form-item:last-child) { margin-bottom: 0; }
/* 问号使用标签右侧的留白，让文字与问号同排，同时保持输入控件对齐。 */
.data-picker-settings :deep(.picker-help-item .el-form-item__label) { padding-right: 8px; }
.data-picker-settings :deep(.picker-help-item .config-help-label) { flex-shrink: 0; max-width: none; white-space: nowrap; }
.data-picker-settings :deep(.el-select), .data-picker-settings :deep(.entity-definition-picker) { width: 100%; }
.picker-fields { display: grid; gap: 6px; width: 100%; min-width: 0; }
.picker-fields > .el-button { justify-self: start; }
.picker-field-row, .picker-mapping-row { display: grid; align-items: center; gap: 6px; min-width: 0; }
.picker-field-row { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(0, .7fr) 2.5em; }
.picker-mapping-row { grid-template-columns: minmax(0, 1.3fr) 14px minmax(0, 1.3fr) minmax(0, 1fr) minmax(0, .9fr) 2.5em; }
.picker-grid-row > * { min-width: 0; }
.picker-grid-row > .el-button { margin: 0; }
.picker-grid-head, .picker-compact-label, .picker-field-count { color: var(--el-text-color-secondary); font-size: 12px; line-height: 18px; }
.picker-grid-head { padding-top: 3px; }
.picker-mapping-arrow { color: var(--el-text-color-secondary); text-align: center; }
.picker-compact-label { display: none; }
.picker-fetch { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; width: 100%; }
.picker-fetch :deep(.el-textarea) { flex: 1 1 150px; }

/* 窄抽屉也让来源和目标保持同排，策略移到对应映射的下一行。 */
@container (max-width: 610px) {
  .picker-settings-group :deep(.el-form-item) { display: block; }
  .picker-settings-group :deep(.el-form-item__label) { width: auto !important; justify-content: flex-start; }
  .picker-settings-group :deep(.el-form-item__content) { margin-left: 0 !important; }
  .picker-mapping-row { grid-template-columns: minmax(0, 1fr) 14px minmax(0, 1fr) 2.5em; }
  .picker-mapping-row.picker-grid-head > :nth-child(n + 4) { display: none; }
  .picker-mapping-row.picker-grid-row { align-items: end; padding-bottom: 8px; border-bottom: 1px solid var(--el-border-color-lighter); }
  .picker-overwrite { grid-column: 1; }
  .picker-empty-policy { grid-column: 3; }
  .picker-compact-label { display: block; margin-bottom: 2px; }
}
</style>
