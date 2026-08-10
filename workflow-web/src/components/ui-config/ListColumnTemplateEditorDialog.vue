<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="min(1160px, 96vw)"
    destroy-on-close
  >
    <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="basic">
          <div class="form-grid">
            <el-form-item label="模板编码" prop="templateKey">
              <el-input
                v-model="form.templateKey"
                :disabled="mode === 'EDIT'"
                maxlength="100"
                placeholder="例如 COMMON_STATUS_COLUMN"
                @input="normalizeTemplateKey"
              />
              <div class="field-help">使用大写字母、数字和下划线，创建后保持不变。</div>
            </el-form-item>
            <el-form-item label="模板名称" prop="templateName">
              <el-input v-model="form.templateName" maxlength="200" placeholder="例如 通用状态列" />
            </el-form-item>
          </div>

          <section class="editor-section">
            <SectionHeading
              title="模板边界"
              description="模板只用于初始化。应用时复制配置，后续修改模板不会改变已经初始化的列表列。"
            />
            <div class="boundary-list">
              <div><el-icon><CircleCheck /></el-icon>数据来源与来源参数</div>
              <div><el-icon><CircleCheck /></el-icon>查询方式与查询控件</div>
              <div><el-icon><CircleCheck /></el-icon>列宽、对齐和渲染样式</div>
              <div><el-icon><CircleClose /></el-icon>不保存字段身份、列顺序和模板关联</div>
            </div>
          </section>
        </el-tab-pane>

        <el-tab-pane label="数据与查询" name="data">
          <section class="editor-section first-section">
            <SectionHeading
              title="字段数据来源"
              description="普通字段选择“实体字段”；虚拟列可使用字段组合或已注册的数据提供者。"
            />
            <div class="form-grid">
              <el-form-item label="数据来源" required>
                <el-select
                  v-model="form.dataSourceType"
                  style="width: 100%"
                  @change="handleDataSourceChange"
                >
                  <el-option
                    v-for="option in dataSourceOptions"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                  >
                    <div class="select-option">
                      <span>{{ option.label }}</span>
                      <small>{{ option.value }}</small>
                    </div>
                  </el-option>
                </el-select>
                <div v-if="selectedDataSource?.description" class="field-help">
                  {{ selectedDataSource.description }}
                </div>
              </el-form-item>
              <el-form-item label="接口数据源">
                <el-select
                  v-model="form.dataSourceId"
                  clearable
                  filterable
                  placeholder="可选：绑定 LIST_COLUMN 接口数据源"
                  style="width: 100%"
                  @change="handleInterfaceServiceChange"
                >
                  <el-option
                    v-for="source in listInterfaceServices"
                    :key="source.id"
                    :label="`${source.sourceName} (${source.sourceCode})`"
                    :value="source.id"
                  />
                </el-select>
                <div class="field-help">只有需要统一接口服务取值时才选择；普通实体字段留空。</div>
              </el-form-item>
              <el-form-item v-if="form.dataSourceId" label="接口操作" required>
                <el-select
                  v-model="form.dataSourceOperationCode"
                  filterable
                  placeholder="选择 LIST 上下文只读操作"
                  style="width: 100%"
                >
                  <el-option
                    v-for="operation in interfaceOperationOptions"
                    :key="operation.code"
                    :label="`${operation.name} (${operation.code})`"
                    :value="operation.code"
                  />
                </el-select>
              </el-form-item>
            </div>

            <ConfigSchemaEditor
              v-if="sourceSimpleSchema.length"
              v-model="form.dataSourceConfig"
              :schema="sourceSimpleSchema"
            />
            <JsonSchemaField
              v-for="item in sourceJsonSchema"
              :key="`source-${item.key}`"
              :item="item"
              :model-value="form.dataSourceConfig[item.key]"
              @update:model-value="updateObjectConfig('dataSourceConfig', item.key, $event)"
            />
          </section>

          <section class="editor-section">
            <SectionHeading
              title="查询条件"
              description="决定字段应用模板后是否进入列表查询区，以及默认使用什么查询方式。"
            />
            <div class="form-grid">
              <el-form-item label="作为查询条件">
                <el-switch
                  v-model="form.isQuery"
                  :disabled="selectedDataSource?.supportsQuery === false"
                />
              </el-form-item>
              <el-form-item label="查询方式">
                <el-select v-model="form.queryType" :disabled="!form.isQuery" style="width: 100%">
                  <el-option
                    v-for="option in queryTypeOptions"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="查询控件">
                <el-select
                  v-model="form.queryConfig.componentType"
                  :disabled="!form.isQuery"
                  clearable
                  placeholder="自动匹配字段类型"
                  style="width: 100%"
                >
                  <el-option
                    v-for="option in queryComponentOptions"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="占位提示">
                <el-input
                  v-model="form.queryConfig.placeholder"
                  :disabled="!form.isQuery"
                  placeholder="例如 请选择状态"
                />
              </el-form-item>
              <el-form-item label="默认查询值">
                <el-input
                  v-model="form.queryConfig.defaultValue"
                  :disabled="!form.isQuery"
                  placeholder="留空表示不设置默认条件"
                />
              </el-form-item>
            </div>
          </section>
        </el-tab-pane>

        <el-tab-pane label="显示与预览" name="display">
          <section class="editor-section first-section">
            <SectionHeading
              title="列布局"
              description="控制列是否显示、默认宽度、对齐方式、固定位置和溢出提示。"
            />
            <div class="form-grid">
              <el-form-item label="显示列"><el-switch v-model="form.showInList" /></el-form-item>
              <el-form-item label="列宽">
                <el-input-number
                  v-model="form.width"
                  :min="0"
                  :max="2000"
                  :step="10"
                  controls-position="right"
                  style="width: 100%"
                />
                <div class="field-help">填写 0 时使用最小宽度并自动伸展。</div>
              </el-form-item>
              <el-form-item label="对齐方式">
                <el-segmented v-model="form.align" :options="alignOptions" />
              </el-form-item>
              <el-form-item label="固定位置">
                <el-select
                  v-model="form.columnConfig.fixed"
                  clearable
                  placeholder="不固定"
                  style="width: 100%"
                >
                  <el-option label="固定在左侧" value="left" />
                  <el-option label="固定在右侧" value="right" />
                </el-select>
              </el-form-item>
              <el-form-item label="最小宽度">
                <el-input-number
                  v-model="form.columnConfig.minWidth"
                  :min="60"
                  :max="2000"
                  :step="10"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-form-item>
              <el-form-item label="溢出提示">
                <el-switch v-model="form.columnConfig.showOverflowTooltip" />
              </el-form-item>
            </div>
          </section>

          <section class="editor-section">
            <SectionHeading
              title="单元格显示"
              description="选择渲染组件后，下面只展示该组件真正需要的参数。"
            />
            <div class="form-grid">
              <el-form-item label="渲染组件" required>
                <el-select
                  v-model="form.renderComponent"
                  style="width: 100%"
                  @change="handleRendererChange"
                >
                  <el-option
                    v-for="option in cellComponentOptions"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                  />
                </el-select>
                <div v-if="selectedRenderer?.description" class="field-help">
                  {{ selectedRenderer.description }}
                </div>
              </el-form-item>
            </div>

            <ConfigSchemaEditor
              v-if="renderSimpleSchema.length"
              v-model="form.renderConfig"
              :schema="renderSimpleSchema"
            />
            <JsonSchemaField
              v-for="item in renderJsonSchema"
              :key="`render-${item.key}`"
              :item="item"
              :model-value="form.renderConfig[item.key]"
              :status-map="item.key === 'statusMap'"
              @update:model-value="updateObjectConfig('renderConfig', item.key, $event)"
            />
          </section>

          <section class="editor-section">
            <SectionHeading
              title="效果预览"
              description="预览只验证单元格显示；真实字段值、引用名称和权限仍由列表运行时提供。"
            />
            <div class="preview-layout">
              <el-form-item label="示例原始值" class="preview-input">
                <el-input
                  v-model="form.sampleValue"
                  placeholder="例如 ACTIVE 或 2026-08-10 09:30:00"
                />
              </el-form-item>
              <div class="preview-cell">
                <span>单元格预览</span>
                <ListCellRenderer
                  :value="form.sampleValue"
                  :row="previewRow"
                  :field="previewField"
                />
              </div>
            </div>
          </section>
        </el-tab-pane>
      </el-tabs>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveTemplate">
        {{ mode === 'EDIT' ? '保存模板' : '创建模板' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, defineComponent, h, reactive, ref } from 'vue'
import { CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { ElInput, ElMessage } from 'element-plus'
import ConfigSchemaEditor from '@/components/ConfigSchemaEditor.vue'
import ListCellRenderer from '@/components/ListCellRenderer.vue'
import ObjectMappingEditor from '@/components/ui-config/ObjectMappingEditor.vue'
import { uiComponentTemplateApi } from '@/api/uiConfig'
import { applySchemaDefaults } from '@/shared/config-runtime'
import {
  buildListColumnTemplateSnapshot,
  createListColumnTemplateEditor,
  LIST_COLUMN_TEMPLATE_TYPE
} from '@/shared/list-column-template'
import { serviceOperations } from './interfaceServiceModel'
import { getCellComponentOptions, getCellDescriptor } from '@/utils/listCellRegistry'

const props = defineProps({
  dataSourceOptions: { type: Array, default: () => [] },
  unifiedDataSources: { type: Array, default: () => [] }
})
const emit = defineEmits(['saved'])

const SectionHeading = defineComponent({
  props: { title: String, description: String },
  setup: componentProps => () => h('div', { class: 'section-heading' }, [
    h('h3', componentProps.title),
    h('p', componentProps.description)
  ])
})
const JsonSchemaField = defineComponent({
  props: {
    item: { type: Object, required: true },
    modelValue: { default: null },
    statusMap: Boolean
  },
  emits: ['update:modelValue'],
  setup(componentProps, { emit: componentEmit }) {
    const updateRaw = (value) => {
      if (!value) return componentEmit('update:modelValue', null)
      try {
        const parsed = JSON.parse(value)
        const expectsArray = componentProps.item.jsonShape === 'array'
        if (expectsArray !== Array.isArray(parsed)) {
          throw new Error(expectsArray ? '这里必须填写 JSON 数组' : '这里必须填写 JSON 对象')
        }
        componentEmit('update:modelValue', parsed)
      } catch (error) {
        ElMessage.warning(`${componentProps.item.label}格式错误：${error.message}`)
      }
    }
    return () => {
      const item = componentProps.item
      const isObject = !item.jsonShape || item.jsonShape === 'object'
      if (isObject) {
        return h(ObjectMappingEditor, {
          class: 'visual-json-field',
          modelValue: componentProps.modelValue || {},
          title: item.label,
          description: mappingDescription(item),
          valueLabel: componentProps.statusMap ? '标签颜色' : '显示文字',
          valueOptions: componentProps.statusMap ? statusTypeOptions : [],
          'onUpdate:modelValue': value => componentEmit('update:modelValue', value)
        })
      }
      const example = JSON.stringify(
        item.example ?? (item.jsonShape === 'array' ? ['示例值'] : { key: 'value' }),
        null,
        2
      )
      const current = componentProps.modelValue == null
        ? ''
        : JSON.stringify(componentProps.modelValue, null, 2)
      return h('div', { class: 'raw-json-field visual-json-field' }, [
        h('div', { class: 'json-guide' },
          `该扩展要求数组或嵌套 JSON。必须使用英文双引号和英文逗号，不得写注释。示例：${example}`),
        h(ElInput, {
          modelValue: current,
          type: 'textarea',
          rows: 5,
          placeholder: example,
          onChange: updateRaw
        })
      ])
    }
  }
})

const visible = ref(false)
const saving = ref(false)
const mode = ref('CREATE')
const activeTab = ref('basic')
const formRef = ref()
const form = reactive(createListColumnTemplateEditor())
const cellComponentOptions = getCellComponentOptions()

const alignOptions = [
  { label: '左对齐', value: 'left' },
  { label: '居中', value: 'center' },
  { label: '右对齐', value: 'right' }
]
const queryTypeOptions = [
  ['等于', 'EQ'], ['不等于', 'NE'], ['包含', 'LIKE'], ['不包含', 'NOT_LIKE'],
  ['大于', 'GT'], ['大于等于', 'GE'], ['小于', 'LT'], ['小于等于', 'LE'],
  ['范围', 'BETWEEN'], ['包含于', 'IN'], ['不包含于', 'NOT_IN'],
  ['为空', 'EMPTY'], ['非空', 'NOT_EMPTY']
].map(([label, value]) => ({ label, value }))
const queryComponentOptions = [
  ['文本输入', 'text'], ['数字输入', 'number'], ['单选下拉', 'select'],
  ['多选下拉', 'select_multiple'], ['日期', 'date'], ['日期时间', 'datetime'],
  ['用户选择', 'user'], ['部门选择', 'department']
].map(([label, value]) => ({ label, value }))
const statusTypeOptions = [
  ['成功（绿色）', 'success'], ['提醒（黄色）', 'warning'],
  ['危险（红色）', 'danger'], ['信息（灰色）', 'info'],
  ['主要（蓝色）', 'primary'], ['默认', '']
].map(([label, value]) => ({ label, value }))
const formRules = {
  templateKey: [
    { required: true, message: '请输入模板编码', trigger: 'blur' },
    {
      pattern: /^[A-Z][A-Z0-9_]{2,99}$/,
      message: '模板编码需以大写字母开头，仅使用大写字母、数字和下划线',
      trigger: 'blur'
    }
  ],
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }]
}

const dialogTitle = computed(() => ({
  CREATE: '新建列表列模板',
  EDIT: '编辑列表列模板',
  COPY: '复制列表列模板'
}[mode.value]))
const selectedDataSource = computed(() =>
  props.dataSourceOptions.find(item => item.value === form.dataSourceType)
)
const selectedInterfaceService = computed(() =>
  props.unifiedDataSources.find(item =>
    String(item.id) === String(form.dataSourceId))
)
const listInterfaceServices = computed(() =>
  props.unifiedDataSources.filter(source =>
    serviceOperations(source).some(operation =>
      String(operation.contextType).toUpperCase() === 'LIST'
      && String(operation.kind || 'READ').toUpperCase() === 'READ'))
)
const interfaceOperationOptions = computed(() =>
  selectedInterfaceService.value
    ? serviceOperations(selectedInterfaceService.value).filter(operation =>
        String(operation.contextType).toUpperCase() === 'LIST'
        && String(operation.kind || 'READ').toUpperCase() === 'READ')
    : []
)
const selectedRenderer = computed(() =>
  getCellDescriptor(form.renderComponent || 'DefaultText')
)
const sourceSimpleSchema = computed(() =>
  (selectedDataSource.value?.configSchema || []).filter(item => item.type !== 'json')
)
const sourceJsonSchema = computed(() =>
  (selectedDataSource.value?.configSchema || []).filter(item => item.type === 'json')
)
const renderSimpleSchema = computed(() =>
  (selectedRenderer.value?.configSchema || []).filter(item => item.type !== 'json')
)
const renderJsonSchema = computed(() =>
  (selectedRenderer.value?.configSchema || []).filter(item => item.type === 'json')
)
const previewField = computed(() => ({
  fieldCode: '__template_preview',
  fieldName: '模板预览',
  fieldType: 'STRING',
  dataSourceType: 'ENTITY_FIELD',
  renderComponent: form.renderComponent,
  renderConfig: JSON.stringify(form.renderConfig || {})
}))
const previewRow = computed(() => ({
  id: 'preview',
  data: { __template_preview: form.sampleValue },
  extData: {}
}))

function openCreate() {
  mode.value = 'CREATE'
  resetForm(createListColumnTemplateEditor())
  open()
}

function openEdit(row) {
  mode.value = 'EDIT'
  resetForm(editorFromRow(row))
  form.id = row.id
  form.templateKey = row.templateKey
  form.templateName = row.templateName
  open()
}

function openCopy(row) {
  mode.value = 'COPY'
  resetForm(editorFromRow(row))
  form.id = ''
  form.templateKey = `${row.templateKey}_COPY`
  form.templateName = `${row.templateName} 副本`
  open()
}

function editorFromRow(row) {
  return createListColumnTemplateEditor({
    ...row.editor,
    field: row.editor.baseField,
    metadata: {
      sampleValue: row.editor.sampleValue
    }
  })
}

function open() {
  activeTab.value = 'basic'
  visible.value = true
}

function resetForm(next) {
  Object.keys(form).forEach(key => delete form[key])
  Object.assign(form, next)
}

function normalizeTemplateKey(value) {
  form.templateKey = String(value || '').toUpperCase().replace(/[^A-Z0-9_]/g, '')
}

function handleDataSourceChange() {
  form.dataSourceConfig = applySchemaDefaults(selectedDataSource.value?.configSchema || [], {})
  if (selectedDataSource.value?.supportsQuery === false) form.isQuery = false
}

function handleInterfaceServiceChange(serviceId) {
  if (!serviceId) {
    form.dataSourceOperationCode = ''
    return
  }
  const operations = interfaceOperationOptions.value
  form.dataSourceOperationCode = operations.length === 1
    ? operations[0].code
    : ''
}

function handleRendererChange() {
  form.renderConfig = applySchemaDefaults(selectedRenderer.value?.configSchema || [], {})
}

function updateObjectConfig(target, key, value) {
  form[target] = { ...(form[target] || {}), [key]: value }
}

async function saveTemplate() {
  try {
    await formRef.value?.validate()
    if (form.dataSourceId && !form.dataSourceOperationCode) {
      throw new Error('请选择列表列模板使用的接口操作')
    }
    validateRequiredSchema(selectedDataSource.value?.configSchema, form.dataSourceConfig)
    validateRequiredSchema(selectedRenderer.value?.configSchema, form.renderConfig)
  } catch (error) {
    if (error instanceof Error) ElMessage.warning(error.message)
    return
  }
  saving.value = true
  try {
    await uiComponentTemplateApi.save({
      id: mode.value === 'EDIT' ? form.id : undefined,
      templateKey: form.templateKey,
      templateName: form.templateName,
      templateType: LIST_COLUMN_TEMPLATE_TYPE,
      description: '',
      snapshot: buildListColumnTemplateSnapshot(form)
    })
    ElMessage.success(mode.value === 'EDIT'
      ? '模板已保存；已初始化的列表列不会发生变化'
      : '列表列模板已创建')
    visible.value = false
    emit('saved')
  } catch (error) {
    ElMessage.error(error?.message || '保存列表列模板失败')
  } finally {
    saving.value = false
  }
}

function validateRequiredSchema(schema = [], config = {}) {
  const missing = schema.find(item =>
    item.required === true
    && [undefined, null, ''].includes(config?.[item.key])
  )
  if (missing) throw new Error(`请填写${missing.label}`)
}

function mappingDescription(item) {
  if (item.key === 'labelMap') {
    return '左侧填写数据库中的原始值，右侧填写列表中展示的中文文字。'
  }
  if (item.key === 'statusMap') {
    return '左侧填写原始状态值，右侧选择标签颜色。未配置的状态使用默认颜色。'
  }
  return item.description || '逐行填写后由系统自动生成 JSON 对象。'
}

defineExpose({ openCreate, openEdit, openCopy })
</script>

<style scoped>
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 28px;
}
.field-help {
  width: 100%;
  margin-top: 5px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}
.editor-section {
  padding: 18px 0 6px;
  border-top: 1px solid #ebeef5;
}
.first-section {
  padding-top: 4px;
  border-top: 0;
}
:deep(.section-heading) {
  margin-bottom: 16px;
}
:deep(.section-heading h3) {
  margin: 0;
  color: #303133;
  font-size: 16px;
}
:deep(.section-heading p) {
  margin: 5px 0 0;
  color: #909399;
  font-size: 13px;
  line-height: 1.5;
}
.boundary-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 24px;
  color: #606266;
}
.boundary-list > div {
  display: flex;
  align-items: center;
  gap: 8px;
}
.boundary-list > div:nth-child(-n + 3) .el-icon { color: #67c23a; }
.boundary-list > div:last-child .el-icon { color: #909399; }
.select-option {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  width: 100%;
}
.select-option small { color: #909399; }
:deep(.visual-json-field) { margin-top: 14px; }
:deep(.raw-json-field) { display: grid; gap: 10px; }
:deep(.json-guide) {
  padding: 10px 12px;
  color: #606266;
  font-size: 12px;
  line-height: 1.6;
  background: #f4f4f5;
  border-radius: 6px;
  white-space: pre-wrap;
}
.preview-layout {
  display: grid;
  grid-template-columns: minmax(280px, 0.9fr) minmax(300px, 1.1fr);
  gap: 24px;
  align-items: end;
}
.preview-input { margin-bottom: 0; }
.preview-cell {
  min-height: 40px;
  padding: 10px 14px;
  color: #303133;
  background: #f7f8fa;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
}
.preview-cell > span {
  display: block;
  margin-bottom: 7px;
  color: #909399;
  font-size: 12px;
}
@media (max-width: 900px) {
  .form-grid,
  .preview-layout { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .boundary-list { grid-template-columns: 1fr; }
}
</style>
