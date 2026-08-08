import { safeParseConfig } from './config-runtime/index.js'

const DOCUMENT_KEY_ALIASES = {
  propsDocument: 'props',
  rulesDocument: 'rules',
  dataSourceBindingsDocument: 'dataSourceBindings',
  legacyPropsDocument: 'legacyProps',
  localOverridesDocument: 'localOverrides',
  actionParamsDocument: 'actionParams',
  availabilityRuleDocument: 'availabilityRule',
  stepsDocument: 'steps',
  snapshotDocument: 'snapshot'
}

const STRUCTURED_KEYS = new Set([
  'actionBar',
  'actionParams',
  'availabilityRule',
  'columnConfig',
  'componentProps',
  'contextBindingConfig',
  'dataSourceBindings',
  'dataSourceConfig',
  'extensionConfig',
  'fixedFilterConfig',
  'initConfig',
  'legacyProps',
  'localOverrides',
  'queryConfig',
  'renderConfig',
  'rules',
  'selectionConfig',
  'steps',
  'validationRules',
  'viewConfig'
])

export function normalizeRuntimeSnapshot(value) {
  return normalizeValue(value)
}

export function selectRuntimeRelease(releases = [], activeReleaseId = '') {
  const items = Array.isArray(releases) ? releases : []
  return items.find(item =>
    activeReleaseId
      && String(item?.id) === String(activeReleaseId)
  ) || items.find(item =>
    String(item?.status || '').toUpperCase() === 'ACTIVE'
  ) || [...items].sort((left, right) =>
    Number(right?.version || 0) - Number(left?.version || 0)
  )[0] || null
}

export function buildFormDraftRuntimeSnapshot({
  form = {},
  nodes = [],
  legacyFields = [],
  eventBindings = []
} = {}) {
  const normalizedForm = normalizeValue({
    ...withoutKeys(form, ['fields', 'nodes']),
    initConfig: safeParseConfig(form.initConfig),
    viewConfig: safeParseConfig(form.viewConfig)
  })
  return normalizeValue({
    schemaVersion: 1,
    configType: 'FORM',
    form: normalizedForm,
    legacyFields,
    nodes,
    eventBindings
  })
}

export function buildListDraftRuntimeSnapshot({
  list = {},
  viewConfig = {},
  fields = [],
  toolbarActions = [],
  rowActions = [],
  scenes = [],
  eventBindings = []
} = {}) {
  const allowedScenes = scenes.length > 0
    ? scenes
        .filter(scene => scene?.enabled !== false)
        .map(scene => scene.sceneCode || scene.code || scene)
        .filter(Boolean)
    : normalizeSceneValues(
        list.allowedSceneValues || list.allowedScenes
      )
  const normalizedList = {
    ...withoutKeys(list, [
      'allowedSceneValues',
      'fields',
      'rowActionConfig',
      'selectionMode',
      'selectionReturnMappingsText',
      'selectionValueField',
      'toolbarConfig',
      'viewConfig'
    ]),
    allowedScenes,
    contextBindingConfig: parseStructured(
      list.contextBindingConfig
    ),
    fields,
    fixedFilterConfig: parseStructured(
      list.fixedFilterConfig
    ),
    rowActionConfig: rowActions,
    selectionConfig: resolveSelectionConfig(list),
    toolbarConfig: toolbarActions,
    viewConfig
  }
  return normalizeValue({
    schemaVersion: 1,
    configType: 'LIST',
    list: normalizedList,
    eventBindings
  })
}

export function buildRuntimeCodeArtifact({
  configType,
  configLabel = '',
  source = 'DRAFT',
  version = null,
  snapshot = {}
} = {}) {
  const normalizedType = String(configType || '').toUpperCase()
  const definition = normalizeRuntimeSnapshot(snapshot)
  const logicItems = normalizedType === 'FORM'
    ? collectFormLogic(definition)
    : collectListLogic(definition)
  return {
    configType: normalizedType,
    configLabel,
    source,
    version,
    definition,
    json: JSON.stringify(definition, null, 2),
    code: toEquivalentVueSfc(
      normalizedType,
      configLabel,
      source,
      version,
      definition
    ),
    logicItems,
    stats: buildStats(normalizedType, definition, logicItems)
  }
}

function normalizeValue(value, key = '') {
  if (value === undefined || typeof value === 'function') {
    return undefined
  }
  if (value === null || typeof value !== 'object') {
    if (typeof value === 'string' && shouldParseKey(key)) {
      const parsed = parseStructured(value)
      return parsed === value ? value : normalizeValue(parsed, key)
    }
    return value
  }
  if (Array.isArray(value)) {
    return value
      .map(item => normalizeValue(item))
      .filter(item => item !== undefined)
  }
  const result = {}
  Object.entries(value).forEach(([entryKey, entryValue]) => {
    if (entryKey.startsWith('_')
        || entryValue === undefined
        || typeof entryValue === 'function') {
      return
    }
    const normalizedKey =
      DOCUMENT_KEY_ALIASES[entryKey] || entryKey
    const normalized = normalizeValue(entryValue, entryKey)
    if (normalized !== undefined) {
      result[normalizedKey] = normalized
    }
  })
  return result
}

function shouldParseKey(key) {
  return Boolean(DOCUMENT_KEY_ALIASES[key])
    || STRUCTURED_KEYS.has(key)
}

function parseStructured(value) {
  if (value == null || value === '') {
    return value === '' ? {} : value
  }
  if (typeof value !== 'string') return value
  const text = value.trim()
  if (!text || !['{', '['].includes(text[0])) return value
  try {
    return JSON.parse(text)
  } catch {
    return value
  }
}

function normalizeSceneValues(value) {
  if (Array.isArray(value)) return value.filter(Boolean)
  const parsed = parseStructured(value)
  if (Array.isArray(parsed)) return parsed.filter(Boolean)
  return String(value || '')
    .split(',')
    .map(item => item.trim())
    .filter(Boolean)
}

function resolveSelectionConfig(list) {
  const configured = parseStructured(list.selectionConfig)
  if (configured && typeof configured === 'object'
      && !Array.isArray(configured)
      && Object.keys(configured).length > 0) {
    return configured
  }
  return {
    selectionMode: list.selectionMode || 'NONE',
    valueField: list.selectionValueField || 'id',
    returnMappings: parseStructured(
      list.selectionReturnMappingsText
    ) || []
  }
}

function withoutKeys(value, keys) {
  const excluded = new Set(keys)
  return Object.fromEntries(
    Object.entries(value || {})
      .filter(([key]) => !excluded.has(key))
  )
}

function toEquivalentVueSfc(
  configType,
  configLabel,
  source,
  version,
  definition
) {
  const sourceLabel = source === 'PUBLISHED'
    ? `当前激活发布版本${version == null ? '' : ` v${version}`}`
    : '当前设计器草稿'
  if (configType === 'FORM') {
    return toEquivalentFormVue(
      configLabel,
      sourceLabel,
      definition
    )
  }
  return toEquivalentListVue(
    configLabel,
    sourceLabel,
    definition
  )
}

function toEquivalentFormVue(configLabel, sourceLabel, definition) {
  return [
    '<!--',
    '  平台等价 Vue 单文件组件，仅用于技术审查。',
    '  平台不会生成或执行此物理文件，实际运行仍解释发布快照。',
    `  配置：${configLabel || '表单'}`,
    `  来源：${sourceLabel}`,
    '-->',
    '<template>',
    '  <div class="generated-form-runtime">',
    '    <h3 v-if="formDefinition.formName">',
    '      {{ formDefinition.formName }}',
    '    </h3>',
    '',
    '    <!-- 节点表单：容器、字段、规则、数据源和子表单均由节点树驱动。 -->',
    '    <FormNodeRenderer',
    '      v-if="formNodes.length"',
    '      ref="nodeFormRef"',
    '      :nodes="formNodes"',
    '      :fields="formFields"',
    '      :model-value="formData"',
    '      :readonly="readonly"',
    '      :mode="mode"',
    '      :context="runtimeContext"',
    '      :label-width="labelWidth"',
    '      :label-position="labelPosition"',
    '      :layout-type="formDefinition.layoutType || \'vertical\'"',
    '      @update:model-value="updateFormData"',
    '    />',
    '',
    '    <!-- 兼容旧字段表单：没有节点树时按字段顺序渲染。 -->',
    '    <el-form',
    '      v-else',
    '      ref="legacyFormRef"',
    '      :model="formData"',
    '      :label-width="labelWidth"',
    '      :label-position="labelPosition"',
    '    >',
    '      <el-form-item',
    '        v-for="field in formFields"',
    '        :key="field.id || field.fieldCode"',
    '        :label="field.fieldLabel || field.fieldName"',
    '        :prop="fieldKey(field)"',
    '      >',
    '        <FormFieldRendererLinkage',
    '          v-model="formData[fieldKey(field)]"',
    '          :field="field"',
    '          :disabled="readonly"',
    '          :context="{ ...runtimeContext, field }"',
    '        />',
    '      </el-form-item>',
    '    </el-form>',
    '',
    '    <FormActionBar',
    '      :actions="formActions"',
    '      :loading-key="actionLoadingKey"',
    '      @action="handleFormAction"',
    '    />',
    '  </div>',
    '</template>',
    '',
    '<script setup>',
    'import { computed, reactive, ref } from \'vue\'',
    'import FormNodeRenderer from \'@/components/FormNodeRenderer.vue\'',
    'import FormFieldRendererLinkage from \'@/components/FormFieldRendererLinkage.vue\'',
    'import FormActionBar from \'@/components/FormActionBar.vue\'',
    'import { uiEventBindingApi } from \'@/api/uiConfig\'',
    '',
    '// 真实草稿或发布快照，设计器中的配置会完整落在这里。',
    `const runtimeSnapshot = ${serializeForVue(definition)}`,
    '',
    'const formDefinition = runtimeSnapshot.form || {}',
    'const formNodes = runtimeSnapshot.nodes || []',
    'const formFields = runtimeSnapshot.legacyFields || []',
    'const eventBindings = runtimeSnapshot.eventBindings || []',
    '',
    'const nodeFormRef = ref()',
    'const legacyFormRef = ref()',
    'const formData = reactive({})',
    'const mode = ref(\'edit\')',
    'const actionLoadingKey = ref(\'\')',
    '',
    'const readonly = computed(() => mode.value === \'view\')',
    'const labelPosition = computed(() =>',
    '  formDefinition.viewConfig?.labelPosition || \'right\'',
    ')',
    'const labelWidth = computed(() => {',
    '  const width = formDefinition.viewConfig?.labelWidth || 100',
    '  return typeof width === \'number\' ? String(width) + \'px\' : String(width)',
    '})',
    'const runtimeContext = computed(() => ({',
    '  entityCode: formDefinition.entityCode || \'\',',
    '  formId: String(formDefinition.id || \'\'),',
    '  mode: mode.value',
    '}))',
    'const formActions = computed(() => {',
    '  const actionBar = formDefinition.viewConfig?.actionBar || {}',
    '  if (Array.isArray(actionBar)) return actionBar',
    '  return actionBar.actions || actionBar.customButtons || actionBar.buttons || []',
    '})',
    '',
    'function fieldKey(field) {',
    '  return field.fieldCode || field.fieldKey || String(field.id || \'\')',
    '}',
    '',
    'function updateFormData(nextValue) {',
    '  Object.keys(formData).forEach(key => delete formData[key])',
    '  Object.assign(formData, nextValue || {})',
    '}',
    '',
    'async function handleFormAction(action) {',
    '  const actionKey = action.runtimeKey || action.key || action.buttonKey || \'\'',
    '  actionLoadingKey.value = actionKey',
    '  try {',
    '    // 内置保存、提交等动作由页面运行时分流；自定义按钮进入事件执行链。',
    '    await uiEventBindingApi.execute(\'FORM_BUTTON_CLICK\', {',
    '      configType: \'FORM\',',
    '      configId: String(formDefinition.id || \'\'),',
    '      entityCode: formDefinition.entityCode || \'\',',
    '      targetType: \'BUTTON\',',
    '      targetKey: String(actionKey),',
    '      input: { button: action, form: { ...formData } },',
    '      context: runtimeContext.value',
    '    })',
    '  } finally {',
    '    actionLoadingKey.value = \'\'',
    '  }',
    '}',
    '',
    '// 保留事件绑定常量，便于审查每个按钮、字段和表单事件的执行步骤。',
    'void eventBindings',
    '</script>',
    '',
    '<style scoped>',
    '.generated-form-runtime {',
    '  display: flex;',
    '  flex-direction: column;',
    '  gap: 16px;',
    '}',
    '</style>',
    ''
  ].join('\n')
}

function toEquivalentListVue(configLabel, sourceLabel, definition) {
  return [
    '<!--',
    '  平台等价 Vue 单文件组件，仅用于技术审查。',
    '  平台不会生成或执行此物理文件，实际运行仍解释发布快照。',
    `  配置：${configLabel || '列表'}`,
    `  来源：${sourceLabel}`,
    '-->',
    '<template>',
    '  <div class="generated-list-runtime">',
    '    <EntityDataSearchForm',
    '      v-if="queryFields.length"',
    '      v-model:form="queryForm"',
    '      :fields="queryFields"',
    '      :use-list-config="true"',
    '      :view-config="viewConfig"',
    '      @search="handleSearch"',
    '      @reset="handleReset"',
    '    />',
    '',
    '    <div v-if="toolbarActions.length" class="list-toolbar">',
    '      <el-button',
    '        v-for="button in toolbarActions"',
    '        :key="actionKey(button)"',
    '        :type="button.buttonType || \'default\'"',
    '        :disabled="button.enabled === false"',
    '        @click="executeAction(button)"',
    '      >',
    '        {{ actionLabel(button) }}',
    '      </el-button>',
    '    </div>',
    '',
    '    <el-table',
    '      v-loading="loading"',
    '      :data="records"',
    '      row-key="id"',
    '      :stripe="viewConfig.table?.stripe !== false"',
    '      :border="viewConfig.table?.border === true"',
    '      @selection-change="selectedRows = $event"',
    '    >',
    '      <el-table-column',
    '        v-if="selectionMode !== \'NONE\'"',
    '        type="selection"',
    '        width="50"',
    '      />',
    '      <el-table-column',
    '        v-for="field in displayFields"',
    '        :key="field.fieldCode"',
    '        :label="field.fieldName"',
    '        :prop="field.fieldCode"',
    '        :width="field.width > 0 ? field.width : undefined"',
    '        :min-width="field.width > 0 ? undefined : 120"',
    '      >',
    '        <template #default="{ row }">',
    '          <ListCellRenderer',
    '            :row="row"',
    '            :field="field"',
    '            :context="{ entityCode }"',
    '          />',
    '        </template>',
    '      </el-table-column>',
    '',
    '      <el-table-column',
    '        v-if="rowActions.length"',
    '        label="操作"',
    '        min-width="160"',
    '        fixed="right"',
    '      >',
    '        <template #default="{ row }">',
    '          <el-button',
    '            v-for="button in rowActions"',
    '            :key="actionKey(button)"',
    '            link',
    '            type="primary"',
    '            :disabled="button.enabled === false"',
    '            @click="executeAction(button, row)"',
    '          >',
    '            {{ actionLabel(button) }}',
    '          </el-button>',
    '        </template>',
    '      </el-table-column>',
    '    </el-table>',
    '',
    '    <el-pagination',
    '      v-model:current-page="pageNum"',
    '      v-model:page-size="pageSize"',
    '      :total="total"',
    '      :page-sizes="viewConfig.pagination?.pageSizes || [10, 20, 50, 100]"',
    '      layout="total, sizes, prev, pager, next"',
    '      @size-change="loadRecords"',
    '      @current-change="loadRecords"',
    '    />',
    '  </div>',
    '</template>',
    '',
    '<script setup>',
    'import { computed, onMounted, reactive, ref } from \'vue\'',
    'import EntityDataSearchForm from \'@/views/entity/components/EntityDataSearchForm.vue\'',
    'import ListCellRenderer from \'@/components/ListCellRenderer.vue\'',
    'import { entityListRuntimeApi } from \'@/api/entityListRuntime\'',
    'import { uiEventBindingApi } from \'@/api/uiConfig\'',
    'import { buildListRequestFilters } from \'@/shared/list-runtime\'',
    '',
    '// 真实草稿或发布快照，字段、查询、按钮、权限和事件配置均来自这里。',
    `const runtimeSnapshot = ${serializeForVue(definition)}`,
    '',
    'const listDefinition = runtimeSnapshot.list || {}',
    'const eventBindings = runtimeSnapshot.eventBindings || []',
    'const entityCode = listDefinition.entityCode || \'\'',
    'const listKey = listDefinition.listKey || \'\'',
    'const viewConfig = listDefinition.viewConfig || {}',
    'const runtimeContext = {}',
    '',
    'const records = ref([])',
    'const loading = ref(false)',
    'const total = ref(0)',
    'const pageNum = ref(1)',
    'const pageSize = ref(Number(viewConfig.pagination?.pageSize) || 10)',
    'const selectedRows = ref([])',
    'const queryForm = reactive({})',
    '',
    'const allFields = computed(() => listDefinition.fields || [])',
    'const displayFields = computed(() =>',
    '  allFields.value.filter(field => field.showInList !== false)',
    ')',
    'const queryFields = computed(() =>',
    '  allFields.value.filter(field => field.isQuery === true)',
    ')',
    'const toolbarActions = computed(() =>',
    '  (listDefinition.toolbarConfig || []).filter(button => button.enabled !== false)',
    ')',
    'const rowActions = computed(() =>',
    '  (listDefinition.rowActionConfig || []).filter(button => button.enabled !== false)',
    ')',
    'const selectionMode = computed(() =>',
    '  listDefinition.selectionConfig?.selectionMode || \'NONE\'',
    ')',
    '',
    'queryFields.value.forEach(field => {',
    '  queryForm[field.fieldCode] = field.defaultValue ?? \'\'',
    '})',
    '',
    'async function loadRecords() {',
    '  loading.value = true',
    '  try {',
    '    // 固定条件、数据范围和基础权限由服务端按发布配置继续追加。',
    '    const result = await entityListRuntimeApi.query(entityCode, listKey, {',
    '      pageNum: pageNum.value,',
    '      pageSize: pageSize.value,',
    '      scene: \'PAGE\',',
    '      filters: buildListRequestFilters(queryForm, queryFields.value),',
    '      context: runtimeContext',
    '    })',
    '    const rows = Array.isArray(result)',
    '      ? result',
    '      : result?.list || result?.records || result?.rows || []',
    '    records.value = rows',
    '    total.value = Number(result?.total ?? rows.length)',
    '  } finally {',
    '    loading.value = false',
    '  }',
    '}',
    '',
    'function handleSearch() {',
    '  pageNum.value = 1',
    '  loadRecords()',
    '}',
    '',
    'function handleReset() {',
    '  Object.keys(queryForm).forEach(key => delete queryForm[key])',
    '  queryFields.value.forEach(field => {',
    '    queryForm[field.fieldCode] = field.defaultValue ?? \'\'',
    '  })',
    '  handleSearch()',
    '}',
    '',
    'function actionKey(button) {',
    '  return button.key || button.buttonKey || button.runtimeKey || \'\'',
    '}',
    '',
    'function actionLabel(button) {',
    '  return button.label || button.buttonLabel || actionKey(button)',
    '}',
    '',
    'async function executeAction(button, row = null) {',
    '  const eventCode = row ? \'ROW_BUTTON_CLICK\' : \'TOOLBAR_BUTTON_CLICK\'',
    '  await uiEventBindingApi.execute(eventCode, {',
    '    configType: \'LIST\',',
    '    configId: String(listDefinition.id || \'\'),',
    '    entityCode,',
    '    listKey,',
    '    targetType: \'BUTTON\',',
    '    targetKey: String(actionKey(button)),',
    '    recordId: row?.id,',
    '    input: { button, row, selectedRows: selectedRows.value },',
    '    context: runtimeContext',
    '  })',
    '}',
    '',
    '// 保留事件绑定常量，便于审查工具栏和行按钮的执行步骤。',
    'void eventBindings',
    'onMounted(loadRecords)',
    '</script>',
    '',
    '<style scoped>',
    '.generated-list-runtime {',
    '  display: flex;',
    '  flex-direction: column;',
    '  gap: 12px;',
    '}',
    '',
    '.list-toolbar {',
    '  display: flex;',
    '  flex-wrap: wrap;',
    '  gap: 8px;',
    '}',
    '</style>',
    ''
  ].join('\n')
}

function serializeForVue(value) {
  return JSON.stringify(value, null, 2)
    .replace(/<\/script/gi, '<\\/script')
}

function collectFormLogic(snapshot) {
  const items = []
  const form = snapshot.form || {}
  const viewConfig = form.viewConfig || {}
  if (hasContent(form.initConfig)) {
    items.push(logicItem(
      '初始化',
      'form.initConfig',
      '表单初始化',
      summarizeValue(form.initConfig)
    ))
  }
  if (hasContent(viewConfig.actionBar)) {
    items.push(logicItem(
      '动作',
      'form.viewConfig.actionBar',
      '表单动作栏',
      summarizeValue(viewConfig.actionBar)
    ))
  }
  ;(snapshot.nodes || []).forEach((node, index) => {
    const label =
      node.props?.label
      || node.props?.fieldName
      || node.nodeKey
      || `节点 ${index + 1}`
    const path = `nodes[${index}]`
    if (hasContent(node.rules)) {
      items.push(logicItem(
        '规则',
        `${path}.rules`,
        label,
        summarizeValue(node.rules)
      ))
    }
    if (hasContent(node.dataSourceBindings)) {
      items.push(logicItem(
        '数据源',
        `${path}.dataSourceBindings`,
        label,
        summarizeValue(node.dataSourceBindings)
      ))
    }
    const componentProps = node.props?.componentProps || {}
    ;[
      ['subFormConfig', '子表单'],
      ['subListConfig', '子列表'],
      ['refConfig', '实体引用']
    ].forEach(([key, name]) => {
      if (hasContent(componentProps[key])) {
        items.push(logicItem(
          '关系',
          `${path}.props.componentProps.${key}`,
          `${label} · ${name}`,
          summarizeValue(componentProps[key])
        ))
      }
    })
  })
  appendEventLogic(items, snapshot.eventBindings)
  return items
}

function collectListLogic(snapshot) {
  const items = []
  const list = snapshot.list || {}
  if (hasContent(list.fixedFilterConfig)) {
    items.push(logicItem(
      '查询',
      'list.fixedFilterConfig',
      '固定查询条件',
      summarizeValue(list.fixedFilterConfig)
    ))
  }
  if (hasContent(list.contextBindingConfig)) {
    items.push(logicItem(
      '上下文',
      'list.contextBindingConfig',
      '上下文绑定',
      summarizeValue(list.contextBindingConfig)
    ))
  }
  if (list.dataScopeMode || list.accessPermissionCode) {
    items.push(logicItem(
      '权限',
      'list',
      '访问与数据范围',
      [
        list.accessPermissionCode
          ? `权限 ${list.accessPermissionCode}`
          : '',
        list.dataScopeMode
          ? `数据范围 ${list.dataScopeMode}`
          : ''
      ].filter(Boolean).join(' · ')
    ))
  }
  if (hasContent(list.selectionConfig)) {
    items.push(logicItem(
      '选择',
      'list.selectionConfig',
      '选择与返回映射',
      summarizeValue(list.selectionConfig)
    ))
  }
  if ((list.allowedScenes || []).length > 0) {
    items.push(logicItem(
      '场景',
      'list.allowedScenes',
      '允许场景',
      list.allowedScenes.join('、')
    ))
  }
  ;(list.fields || []).forEach((field, index) => {
    const label = field.fieldName || field.fieldCode || `字段 ${index + 1}`
    if (field.isQuery) {
      items.push(logicItem(
        '查询字段',
        `list.fields[${index}]`,
        label,
        `${field.fieldCode} · ${field.queryType || '默认运算符'}`
      ))
    }
    if (field.dataSourceType
        && field.dataSourceType !== 'ENTITY_FIELD') {
      items.push(logicItem(
        '数据源',
        `list.fields[${index}].dataSourceConfig`,
        label,
        `${field.dataSourceType} · ${summarizeValue(
          field.dataSourceConfig
        )}`
      ))
    }
  })
  appendActionLogic(items, list.toolbarConfig, '工具栏')
  appendActionLogic(items, list.rowActionConfig, '操作列')
  appendEventLogic(items, snapshot.eventBindings)
  return items
}

function appendActionLogic(items, actions = [], position) {
  actions.forEach((action, index) => {
    items.push(logicItem(
      '动作',
      `list.${position === '工具栏'
        ? 'toolbarConfig'
        : 'rowActionConfig'}[${index}]`,
      `${position} · ${
        action.buttonLabel
        || action.label
        || action.buttonKey
        || action.key
        || index + 1
      }`,
      [
        action.enabled === false ? '已停用' : '已启用',
        action.permissionCode || action.perm
          ? `权限 ${action.permissionCode || action.perm}`
          : '',
        action.customMode || action.buttonType || action.type || '',
        hasContent(action.availabilityRule)
          ? `可用性 ${summarizeValue(action.availabilityRule)}`
          : ''
      ].filter(Boolean).join(' · ')
    ))
  })
}

function appendEventLogic(items, bindings = []) {
  ;(bindings || []).forEach((binding, index) => {
    const steps = binding.steps || []
    items.push(logicItem(
      '事件',
      `eventBindings[${index}]`,
      binding.eventCode || `事件 ${index + 1}`,
      [
        binding.targetType || 'OWNER',
        binding.targetKey || '',
        `${Array.isArray(steps) ? steps.length : 0} 个步骤`
      ].filter(Boolean).join(' · ')
    ))
  })
}

function buildStats(configType, snapshot, logicItems) {
  if (configType === 'FORM') {
    const nodes = snapshot.nodes || []
    return [
      { label: '节点', value: nodes.length },
      {
        label: '规则',
        value: logicItems.filter(item => item.category === '规则').length
      },
      {
        label: '数据源',
        value: logicItems.filter(item => item.category === '数据源').length
      },
      {
        label: '关系',
        value: logicItems.filter(item => item.category === '关系').length
      },
      {
        label: '事件',
        value: (snapshot.eventBindings || []).length
      }
    ]
  }
  const list = snapshot.list || {}
  const fields = list.fields || []
  return [
    { label: '字段', value: fields.length },
    {
      label: '显示列',
      value: fields.filter(field => field.showInList).length
    },
    {
      label: '查询项',
      value: fields.filter(field => field.isQuery).length
    },
    {
      label: '动作',
      value:
        (list.toolbarConfig || []).length
        + (list.rowActionConfig || []).length
    },
    {
      label: '事件',
      value: (snapshot.eventBindings || []).length
    }
  ]
}

function logicItem(category, path, name, summary) {
  return { category, path, name, summary }
}

function hasContent(value) {
  if (value == null || value === '') return false
  if (Array.isArray(value)) return value.length > 0
  if (typeof value === 'object') return Object.keys(value).length > 0
  return true
}

function summarizeValue(value) {
  if (!hasContent(value)) return '-'
  if (Array.isArray(value)) return `${value.length} 项`
  if (typeof value !== 'object') return String(value)
  const keys = Object.keys(value)
  return keys.length <= 5
    ? keys.join('、')
    : `${keys.slice(0, 5).join('、')} 等 ${keys.length} 项`
}
