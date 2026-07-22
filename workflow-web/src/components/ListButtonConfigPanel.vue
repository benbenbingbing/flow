<template>
  <div class="list-button-config-panel">
    <div class="toolbar-actions">
      <el-dropdown split-button type="primary" size="small" @click="addCustom">
        添加自定义按钮
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="opt in builtinOptions"
              :key="opt.key"
              :disabled="isBuiltinAdded(opt.key)"
              @click="addBuiltin(opt.key)"
            >
              {{ opt.label }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <el-table :data="sortedButtons" size="small" border>
      <el-table-column label="排序" width="80" align="center">
        <template #default="{ row }">
          <el-input-number v-model="row.sort" :min="0" :max="999" controls-position="right" size="small" style="width: 70px" />
        </template>
      </el-table-column>
      <el-table-column label="启用" width="60" align="center">
        <template #default="{ row }">
          <el-checkbox v-model="row.enabled" />
        </template>
      </el-table-column>
      <el-table-column label="按钮名称" width="130">
        <template #default="{ row }">
          <el-input v-model="row.label" size="small" placeholder="按钮名称" />
        </template>
      </el-table-column>
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-select v-model="row.type" size="small" style="width: 100px">
            <el-option label="内置" value="built-in" />
            <el-option label="自定义" value="custom" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="内置类型 / 执行器" width="180">
        <template #default="{ row }">
          <el-select v-if="row.type === 'built-in'" v-model="row.key" size="small" style="width: 150px">
            <el-option v-for="opt in builtinOptions" :key="opt.key" :label="opt.label" :value="opt.key" />
          </el-select>
          <template v-else-if="row.customMode === 'open-list'">
            <el-button size="small" type="primary" text @click="configureOpenList(row)">
              {{ openListSummary(row) }}
            </el-button>
          </template>
          <template v-else>
            <el-input v-model="row.customHandler" size="small" placeholder="执行器/组件名" style="width: 150px" />
          </template>
        </template>
      </el-table-column>
      <el-table-column label="自定义模式" width="120">
        <template #default="{ row }">
          <el-select v-if="row.type === 'custom'" v-model="row.customMode" size="small" style="width: 110px">
            <el-option label="函数" value="handler" />
            <el-option label="组件" value="component" />
            <el-option label="打开列表" value="open-list" />
          </el-select>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="图标" width="110">
        <template #default="{ row }">
          <el-input v-model="row.icon" size="small" placeholder="Element 图标名" />
        </template>
      </el-table-column>
      <el-table-column label="样式" width="110">
        <template #default="{ row }">
          <el-select v-model="row.buttonType" size="small" style="width: 100px">
            <el-option label="默认" value="default" />
            <el-option label="主要" value="primary" />
            <el-option label="成功" value="success" />
            <el-option label="警告" value="warning" />
            <el-option label="危险" value="danger" />
            <el-option label="信息" value="info" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="Link" width="70" align="center" v-if="type === 'row'">
        <template #default="{ row }">
          <el-checkbox v-model="row.link" />
        </template>
      </el-table-column>
      <el-table-column label="权限码" min-width="180">
        <template #default="{ row }">
          <el-select
            v-model="row.perm"
            size="small"
            filterable
            allow-create
            clearable
            placeholder="选择或输入权限码"
            style="width: 100%"
          >
            <el-option-group label="标准权限">
              <el-option
                v-for="option in standardPermOptions"
                :key="option.code"
                :label="`${option.label} · ${option.code}`"
                :value="option.code"
              >
                <div class="permission-option">
                  <span>{{ option.label }}</span>
                  <small>{{ option.code }}</small>
                </div>
              </el-option>
            </el-option-group>
            <el-option-group v-if="customPermOptions.length" label="自定义权限">
              <el-option
                v-for="option in customPermOptions"
                :key="option.code"
                :label="`${option.label || '自定义'} · ${option.code}`"
                :value="option.code"
              />
            </el-option-group>
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="适用条件" min-width="170">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="configureRule(row)">
            {{ ruleSummary(row) }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="模板" min-width="200">
        <template #default="{ row }">
          <el-select
            v-model="row.templateId"
            clearable
            filterable
            placeholder="复制后独立"
            size="small"
            style="width: 100%"
            @change="handleTemplateChange(row, $event)"
          >
            <el-option
              v-for="template in templates"
              :key="template.id"
              :label="`${template.templateName} (v${template.currentVersion})`"
              :value="template.id"
            />
          </el-select>
          <el-button
            v-if="row.templateId"
            link
            type="primary"
            size="small"
            @click="$emit('upgrade-template', row)"
          >
            升级 v{{ row.templateVersion || 1 }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="success"
            size="small"
            :loading="row._saving"
            @click="$emit('save', row)"
          >保存</el-button>
          <el-button link type="danger" size="small" @click="$emit('remove', row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <ActionRuleEditorDialog
      ref="ruleEditorRef"
      :entity-fields="entityFields"
      :statuses="statuses"
      @save="saveRule"
    />

    <el-dialog v-model="openListDialogVisible" title="打开实体列表" width="620px">
      <el-form label-width="110px">
        <el-form-item label="目标实体" required>
          <el-select
            v-model="openListForm.targetEntityCode"
            filterable
            placeholder="选择实体"
            style="width: 100%"
            @change="loadTargetLists"
          >
            <el-option
              v-for="entity in entityOptions"
              :key="entity.entityCode"
              :label="`${entity.entityName} (${entity.entityCode})`"
              :value="entity.entityCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标列表" required>
          <el-select v-model="openListForm.targetListKey" placeholder="选择 listKey" style="width: 100%">
            <el-option
              v-for="list in targetListOptions"
              :key="list.listKey"
              :label="`${list.listName || list.listKey} (${list.listKey})`"
              :value="list.listKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="打开方式">
          <el-radio-group v-model="openListForm.presentation">
            <el-radio-button value="DIALOG">弹窗</el-radio-button>
            <el-radio-button value="DRAWER">抽屉</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="选择方式">
          <el-radio-group v-model="openListForm.selectionMode">
            <el-radio-button value="NONE">仅查看</el-radio-button>
            <el-radio-button value="SINGLE">单选</el-radio-button>
            <el-radio-button value="MULTIPLE">多选</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="openListForm.openListTitle" placeholder="留空使用“选择数据”" />
        </el-form-item>
        <el-form-item label="上下文关系">
          <el-input v-model="openListForm.relationKey" placeholder="可选：服务端注册的 relationKey" />
        </el-form-item>
        <el-form-item label="选择回调">
          <el-input v-model="openListForm.selectionHandler" placeholder="可选：已注册的前端选择结果处理器" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          title="行按钮会自动传递来源实体、来源数据 ID 和 relationKey；后端重新加载来源数据生成可信上下文。"
        />
      </el-form>
      <template #footer>
        <el-button @click="openListDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveOpenListConfig">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getEntityPermissionOptions } from '@/api/system/menu'
import { getEntityStatusList } from '@/api/entityStatus'
import { entityApi } from '@/api/entity'
import { entityListConfigApi } from '@/api/entityListConfig'
import { uiComponentTemplateApi } from '@/api/uiConfig'
import ActionRuleEditorDialog from '@/components/ActionRuleEditorDialog.vue'
import { resolveEntityPermissionOptions } from '@/utils/entityActionRuleRegistry'
import { safeParseConfig } from '@/shared/config-runtime'

const props = defineProps({
  type: {
    type: String,
    default: 'toolbar' // 'toolbar' | 'row'
  },
  entityCode: {
    type: String,
    default: ''
  },
  entityFields: {
    type: Array,
    default: () => []
  },
  templates: {
    type: Array,
    default: () => []
  },
  modelValue: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits([
  'update:modelValue',
  'save',
  'remove',
  'upgrade-template'
])

const buttons = computed({
  get: () => props.modelValue || [],
  set: (val) => emit('update:modelValue', val)
})

const sortedButtons = computed(() => {
  return [...buttons.value].sort((a, b) => (a.sort || 0) - (b.sort || 0))
})

const permissionOptions = ref([])
const statuses = ref([])
const ruleEditorRef = ref()
const openListDialogVisible = ref(false)
const openListTargetButton = ref(null)
const entityOptions = ref([])
const targetListOptions = ref([])
const openListForm = ref(createOpenListForm())

const standardPermOptions = computed(() => permissionOptions.value.filter(option => option.category === 'STANDARD'))
const customPermOptions = computed(() => permissionOptions.value.filter(option => option.category !== 'STANDARD'))

const TOOLBAR_BUILTIN = {
  create: { key: 'create', type: 'built-in', label: '新增数据', icon: 'Plus', buttonType: 'primary', sort: 1, enabled: true },
  exportSelected: { key: 'exportSelected', type: 'built-in', label: '导出选中', icon: 'Download', buttonType: 'default', sort: 2, enabled: true },
  exportAll: { key: 'exportAll', type: 'built-in', label: '导出全部', icon: 'Download', buttonType: 'default', sort: 3, enabled: true },
  batchDelete: { key: 'batchDelete', type: 'built-in', label: '批量删除', icon: 'Delete', buttonType: 'danger', sort: 4, enabled: true }
}

const ROW_BUILTIN = {
  view: { key: 'view', type: 'built-in', label: '查看', buttonType: 'primary', link: true, sort: 1, enabled: true },
  edit: { key: 'edit', type: 'built-in', label: '编辑', buttonType: 'primary', link: true, sort: 2, enabled: true },
  approve: { key: 'approve', type: 'built-in', label: '审批', buttonType: 'warning', link: true, sort: 3, enabled: true },
  delete: { key: 'delete', type: 'built-in', label: '删除', buttonType: 'danger', link: true, sort: 4, enabled: true }
}

const builtinPresets = computed(() => props.type === 'toolbar' ? TOOLBAR_BUILTIN : ROW_BUILTIN)
const builtinOptions = computed(() => Object.values(builtinPresets.value))

function isBuiltinAdded(key) {
  return buttons.value.some(b => b.type === 'built-in' && b.key === key)
}

function addBuiltin(key) {
  if (isBuiltinAdded(key)) {
    ElMessage.warning('该内置按钮已添加')
    return
  }
  const preset = builtinPresets.value[key]
  if (!preset) return
  buttons.value.push(withDefaults({ ...preset }))
}

function addCustom() {
  buttons.value.push({
    key: 'custom_' + Date.now(),
    type: 'custom',
    customMode: 'handler',
    label: '自定义按钮',
    icon: '',
    buttonType: 'default',
    sort: buttons.value.length + 1,
    enabled: true,
    perm: '',
    customHandler: '',
    link: props.type === 'row'
  })
}

async function handleTemplateChange(row, templateId) {
  if (!templateId) {
    row.templateVersion = null
    row.localOverridesDocument = null
    return
  }
  const template = props.templates.find(item => item.id === templateId)
  if (!template) return
  try {
    const versions = await uiComponentTemplateApi.versions(templateId)
    const latest = versions.find(item => item.version === template.currentVersion)
      || versions[0]
    if (!latest) return
    const snapshot = safeParseConfig(latest.snapshotDocument)
    Object.assign(row, snapshot.button || snapshot)
    row.templateId = templateId
    row.templateVersion = latest.version
    row.localOverridesDocument = {}
    ElMessage.success(`已锁定按钮模板 v${latest.version}`)
  } catch (error) {
    ElMessage.error(error?.message || '加载按钮模板失败')
  }
}

function createOpenListForm(button = {}) {
  return {
    targetEntityCode: button.targetEntityCode || '',
    targetListKey: button.targetListKey || '',
    presentation: button.presentation || 'DIALOG',
    selectionMode: button.selectionMode || 'NONE',
    openListTitle: button.openListTitle || '',
    relationKey: button.relationKey || '',
    selectionHandler: button.selectionHandler || ''
  }
}

async function configureOpenList(row) {
  openListTargetButton.value = row
  openListForm.value = createOpenListForm(row)
  if (entityOptions.value.length === 0) {
    entityOptions.value = await entityApi.getAll()
  }
  await loadTargetLists(openListForm.value.targetEntityCode, false)
  openListDialogVisible.value = true
}

async function loadTargetLists(entityCode, reset = true) {
  if (reset) {
    openListForm.value.targetListKey = ''
  }
  const entity = entityOptions.value.find(item => item.entityCode === entityCode)
  if (!entity?.id) {
    targetListOptions.value = []
    return
  }
  try {
    const response = await entityListConfigApi.getByEntityId(entity.id)
    targetListOptions.value = Array.isArray(response)
      ? response
      : response?.records || response?.list || response?.data || []
    if (!openListForm.value.targetListKey) {
      openListForm.value.targetListKey =
        targetListOptions.value.find(item => item.isDefault)?.listKey
        || targetListOptions.value[0]?.listKey
        || ''
    }
  } catch (error) {
    console.error('加载目标列表失败:', error)
    targetListOptions.value = []
  }
}

function saveOpenListConfig() {
  if (!openListForm.value.targetEntityCode || !openListForm.value.targetListKey) {
    ElMessage.warning('请选择目标实体和目标列表')
    return
  }
  Object.assign(openListTargetButton.value, openListForm.value)
  openListDialogVisible.value = false
}

function openListSummary(row) {
  if (!row.targetEntityCode || !row.targetListKey) return '配置列表'
  return `${row.targetEntityCode}/${row.targetListKey}`
}

async function loadPermOptions() {
  if (!props.entityCode) return
  try {
    const [serverOptions, extensionOptions, statusList] = await Promise.all([
      getEntityPermissionOptions(props.entityCode),
      resolveEntityPermissionOptions({ entityCode: props.entityCode, type: props.type }),
      getEntityStatusList(props.entityCode)
    ])
    const merged = [...(serverOptions || []), ...(extensionOptions || [])]
    permissionOptions.value = merged.filter((option, index) =>
      option?.code && merged.findIndex(item => item?.code === option.code) === index
    )
    statuses.value = statusList || []
    normalizeButtons()
  } catch (e) {
    console.error('加载权限码失败:', e)
    permissionOptions.value = []
    statuses.value = []
  }
}

onMounted(() => {
  loadPermOptions()
})

watch(() => props.entityCode, () => {
  loadPermOptions()
})

watch(() => props.modelValue, () => {
  normalizeButtons()
}, { deep: true })

function standardPermission(key) {
  const actionMap = {
    create: 'create',
    exportSelected: 'export',
    exportAll: 'export-all',
    batchDelete: 'batch-delete',
    view: 'view',
    edit: 'update',
    approve: 'approve',
    delete: 'delete'
  }
  const action = actionMap[key]
  return action && props.entityCode ? `entity:${props.entityCode.toLowerCase()}:${action}` : ''
}

function withDefaults(button) {
  const normalized = { ...button }
  if (normalized.type === 'built-in') {
    normalized.perm = standardPermission(normalized.key)
  }
  if (!normalized.availabilityRule) {
    normalized.availabilityRule = defaultRule(normalized.key)
  }
  return normalized
}

function normalizeButtons() {
  for (const button of buttons.value) {
    if (button.type === 'built-in') {
      const permission = standardPermission(button.key)
      if (permission && button.perm !== permission) {
        button.perm = permission
      }
      if (!button.availabilityRule) {
        const rule = defaultRule(button.key)
        if (rule) button.availabilityRule = rule
      }
    }
  }
}

function defaultRule(key) {
  if (key === 'delete' || key === 'batchDelete') {
    return {
      version: 1,
      unavailableBehavior: key === 'batchDelete' ? 'DISABLE' : 'HIDE',
      message: key === 'batchDelete' ? '选中数据中存在不可删除的数据' : '仅本人未流转草稿或已撤回数据可以删除',
      root: {
        type: 'GROUP',
        logic: 'AND',
        children: [
          {
            type: 'GROUP',
            logic: 'OR',
            children: [
              { type: 'RELATION', relation: 'CURRENT_USER_IS_CREATOR' },
              { type: 'RELATION', relation: 'CURRENT_USER_IS_SUBMITTER' }
            ]
          },
          {
            type: 'GROUP',
            logic: 'OR',
            children: [
              {
                type: 'GROUP',
                logic: 'AND',
                children: [
                  { type: 'PROCESS_STATE', operator: 'EQ', value: 'NOT_STARTED' },
                  { type: 'STATUS_CATEGORY', operator: 'EQ', value: 'NEW' }
                ]
              },
              { type: 'STATUS_CATEGORY', operator: 'EQ', value: 'WITHDRAWN' }
            ]
          }
        ]
      }
    }
  }
  if (key === 'approve') {
    return {
      version: 1,
      unavailableBehavior: 'HIDE',
      message: '仅当前任务办理人可以审批',
      root: {
        type: 'GROUP',
        logic: 'AND',
        children: [
          { type: 'RELATION', relation: 'CURRENT_USER_IS_ASSIGNEE' },
          { type: 'PROCESS_STATE', operator: 'EQ', value: 'RUNNING' }
        ]
      }
    }
  }
  return null
}

function configureRule(row) {
  ruleEditorRef.value?.open(row, props.type === 'toolbar' ? 'DISABLE' : 'HIDE')
}

function saveRule({ button, rule }) {
  button.availabilityRule = rule
}

function ruleSummary(row) {
  if (!row.availabilityRule?.root) return '始终可操作'
  return row.availabilityRule.message || '已配置条件'
}
</script>

<style scoped>
.list-button-config-panel {
  .toolbar-actions {
    margin-bottom: 12px;
  }
}
.permission-option {
  display: flex;
  flex-direction: column;
}
.permission-option small {
  color: var(--el-text-color-secondary);
}
</style>
