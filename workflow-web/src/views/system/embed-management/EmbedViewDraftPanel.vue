<template>
  <div v-loading="loading" class="draft-panel">
    <div class="panel-toolbar">
      <div class="revision-info">
        <span>
          CAS 版本 {{ draftState.version ?? '-' }}
          <ConfigHelpLabel
            label="CAS 版本"
            :show-label="false"
            help-key="embed.version.cas"
          />
        </span>
        <el-tag v-if="dirty" type="warning" effect="plain">未保存</el-tag>
      </div>
      <div class="panel-actions">
        <el-button :loading="loading" @click="loadDraft">刷新</el-button>
        <el-button
          v-if="canManage"
          type="primary"
          :loading="saving"
          @click="saveDraft"
        >
          保存配置
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="localError"
      type="error"
      :title="localError"
      show-icon
      :closable="false"
      class="panel-alert"
    />

    <el-table
      v-if="violations.length"
      :data="violations"
      border
      size="small"
      class="violation-table"
    >
      <el-table-column prop="path" label="路径" min-width="180" />
      <el-table-column prop="code" label="错误码" min-width="190" />
      <el-table-column prop="message" label="说明" min-width="260" />
    </el-table>

    <el-form
      v-if="editor"
      label-position="top"
      class="draft-form"
      :disabled="!canManage"
    >
      <section class="form-section">
        <h4>目标与入口</h4>
        <div class="form-grid three-columns">
          <el-form-item label="实体" required>
            <template #label>
              <ConfigHelpLabel label="实体" help-key="embed.view.entityCode" />
            </template>
            <EntityDefinitionPicker
              v-model="editor.entityCode"
              value-key="entityCode"
              :show-code="false"
              placeholder="搜索并选择已发布实体"
              title="选择嵌入目标实体"
              :query="{ status: 'PUBLISHED' }"
              @selected="handleEntitySelected"
            />
          </el-form-item>
          <el-form-item v-if="isList" label="列表" required>
            <template #label>
              <ConfigHelpLabel label="列表" help-key="embed.view.listKey" />
            </template>
            <el-select
              v-model="editor.listKey"
              filterable
              :loading="targetOptionsLoading"
              placeholder="选择该实体的已发布列表"
              style="width: 100%"
              @change="loadPublishedContextTargets"
            >
              <el-option
                v-for="item in activeLists"
                :key="item.listKey"
                :label="item.listName || '未命名列表'"
                :value="item.listKey"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            v-if="!isList"
            label="目标表单"
            required
          >
            <template #label>
              <ConfigHelpLabel
                label="目标表单"
                help-key="embed.view.defaultFormId"
              />
            </template>
            <el-select
              v-model="editor.defaultFormId"
              filterable
              clearable
              :loading="targetOptionsLoading"
              placeholder="选择该实体的已发布表单"
              style="width: 100%"
              @change="loadPublishedContextTargets"
            >
              <el-option
                v-for="item in activeForms"
                :key="item.id"
                :label="item.formName || '未命名表单'"
                :value="String(item.id)"
              />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="Entry Modes">
          <template #label>
            <ConfigHelpLabel label="Entry Modes" help-key="embed.view.entryModes" />
          </template>
          <el-checkbox-group v-model="editor.entryModes">
            <el-checkbox
              v-for="mode in entryModeOptions"
              :key="mode"
              :value="mode"
            >
              {{ mode }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </section>
      <el-alert
        v-if="targetOptionsError"
        type="warning"
        :closable="false"
        show-icon
        class="panel-alert"
        :title="targetOptionsError"
      />
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="panel-alert"
        title="这里只保存实体、表单或列表的稳定标识。每次新 Launch 使用目标资源最新 ACTIVE 版本；已打开 Session 固定启动时版本，不会中途漂移。"
      />

      <section class="form-section">
        <h4>
          <ConfigHelpLabel label="能力白名单" help-key="embed.view.capabilities" />
        </h4>
        <el-checkbox-group v-model="editor.capabilities" class="capability-grid">
          <el-checkbox
            v-for="capability in capabilities"
            :key="capability"
            :value="capability"
            :disabled="blockedCapabilities.includes(capability)
              && !editor.capabilities.includes(capability)"
          >
            {{ capability }}
            <small v-if="blockedCapabilities.includes(capability)">V1 强制关闭</small>
          </el-checkbox>
        </el-checkbox-group>
      </section>

      <section class="form-section">
        <h4>字段策略</h4>
        <el-form-item label="字段来源">
          <template #label>
            <ConfigHelpLabel
              label="字段来源"
              help-key="embed.view.fieldPolicyMode"
            />
          </template>
          <el-tag type="success" effect="plain">
            Flow 原生{{ isList ? '列表' : '表单' }}（新 Launch 取最新 ACTIVE）
          </el-tag>
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="field-policy-alert"
          :title="`iframe 直接运行 Flow 原生${isList ? '列表' : '表单'}；字段、布局、渲染器、数据源、联动、按钮和弹窗都来自同一发布态页面。页内按钮显隐与可用状态完全取映射 Flow 用户的普通权限、对象权限和 DataScope；View/Grant 能力只约束嵌入入口与宿主 Bridge。`"
        />
        <div class="form-grid">
          <el-form-item label="Returnable">
            <template #label>
              <ConfigHelpLabel label="Returnable" help-key="embed.view.returnableFields" />
            </template>
            <el-input
              v-model="editor.returnableFieldsText"
              type="textarea"
              :rows="6"
            />
          </el-form-item>
        </div>
      </section>

      <section class="form-section context-section">
        <div class="section-heading">
          <div>
            <h4>宿主上下文</h4>
            <p class="section-help">
              定义宿主在 Launch 时必须传入的字段，并把它们映射为固定过滤或表单强制值。
            </p>
          </div>
          <el-button @click="addContextField">新增上下文字段</el-button>
        </div>

        <el-form-item label="未定义字段处理">
          <el-select
            v-model="editor.contextEditor.additionalProperties"
            style="width: min(360px, 100%)"
          >
            <el-option label="沿用默认行为（允许）" value="unset" />
            <el-option label="允许额外字段" value="allow" />
            <el-option label="拒绝额外字段" value="deny" />
          </el-select>
        </el-form-item>

        <el-table
          :data="editor.contextEditor.fields"
          row-key="id"
          border
          size="small"
          empty-text="无需宿主上下文；如需租户隔离或预填字段，请新增"
          class="context-table"
        >
          <el-table-column label="字段名" min-width="170">
            <template #default="{ row }">
              <el-input
                v-model="row.name"
                maxlength="100"
                placeholder="例如 supplierId"
              />
            </template>
          </el-table-column>
          <el-table-column label="类型" min-width="135">
            <template #default="{ row }">
              <el-select v-model="row.type" style="width: 100%">
                <el-option
                  v-for="type in contextFieldTypes"
                  :key="type.value"
                  :label="type.label"
                  :value="type.value"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="必传" width="80" align="center">
            <template #default="{ row }">
              <el-checkbox v-model="row.required" />
            </template>
          </el-table-column>
          <el-table-column label="显示名称" min-width="150">
            <template #default="{ row }">
              <el-input v-model="row.title" maxlength="128" placeholder="可选" />
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="180">
            <template #default="{ row }">
              <el-input v-model="row.description" maxlength="500" placeholder="可选" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="76" fixed="right">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeContextField($index)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="section-heading binding-heading">
          <div>
            <h4>上下文映射</h4>
            <p class="section-help">
              固定过滤始终使用 EQ；表单强制值会覆盖提交数据中的同名字段。
            </p>
          </div>
          <el-button @click="addContextBinding">新增映射</el-button>
        </div>
        <el-alert
          v-if="editor.contextEditor.fields.length && !eligibleContextSourceOptions.length"
          type="warning"
          show-icon
          :closable="false"
          class="panel-alert"
          title="可映射的来源必须设为必传，并使用 string、integer、number 或 boolean 类型。"
        />
        <el-alert
          v-if="contextTargetError"
          type="warning"
          show-icon
          :closable="false"
          class="panel-alert"
          :title="contextTargetError"
        />
        <el-table
          :data="editor.contextEditor.bindings"
          row-key="id"
          border
          size="small"
          empty-text="暂无上下文映射"
          class="context-table"
        >
          <el-table-column label="宿主字段" min-width="190">
            <template #default="{ row }">
              <el-select
                v-model="row.sourceFieldId"
                filterable
                placeholder="选择必传的标量字段"
                style="width: 100%"
              >
                <el-option
                  v-for="field in contextSourceOptions"
                  :key="field.id || field.name"
                  :label="contextFieldLabel(field)"
                  :value="field.id"
                  :disabled="!isContextSourceField(field)"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="用途" min-width="190">
            <template #default="{ row }">
              <el-select v-model="row.usage" style="width: 100%">
                <el-option label="固定过滤（EQ）" value="FIXED_FILTER" />
                <el-option label="表单强制值" value="FORCED_FORM_VALUE" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="Flow 目标字段" min-width="230">
            <template #default="{ row }">
              <el-select
                v-model="row.target"
                filterable
                :loading="targetOptionsLoading || contextTargetLoading"
                placeholder="按字段名称选择"
                style="width: 100%"
              >
                <el-option
                  v-for="field in contextTargetOptions(row.target)"
                  :key="field.fieldCode"
                  :label="targetFieldLabel(field, row.usage)"
                  :value="field.fieldCode"
                  :disabled="!isContextTargetField(field, row.usage)"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="76" fixed="right">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeContextBinding($index)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="form-section advanced-section">
        <el-collapse>
          <el-collapse-item name="json">
            <template #title>
              <ConfigHelpLabel label="高级 JSON（可选）" help-key="embed.view.advancedJson" />
            </template>
            <p class="section-help">
              仅用于维护可视化表单尚未覆盖的 Schema 约束或 ui 扩展。修改上下文 JSON 后，
              请先重新载入到上方表单，避免两处改动互相覆盖。
            </p>
            <el-button class="sync-json-button" @click="syncContextFromAdvancedJson">
              从 JSON 重新载入上下文
            </el-button>
            <el-input
              v-model="editor.advancedJson"
              type="textarea"
              :rows="18"
              spellcheck="false"
              class="json-editor"
            />
          </el-collapse-item>
        </el-collapse>
      </section>
    </el-form>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { embedManagementApi } from '@/api/system/embedManagement'
import { entityApi } from '@/api/entity'
import { entityListConfigApi } from '@/api/entityListConfig'
import { getFormReleases, getFormsByEntity } from '@/api/entityForm'
import { getFormForNewData } from '@/api/entityFormResolve'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import EntityDefinitionPicker from '@/components/EntityDefinitionPicker.vue'
import { useUserStore } from '@/stores/user'
import {
  EMBED_CAPABILITIES,
  EMBED_PERMISSIONS,
  EMBED_V1_BLOCKED_CAPABILITIES,
  buildExpectedVersionPayload,
  contextToEditor,
  createContextBinding,
  createContextField,
  describeEmbedManagementError,
  draftToEditor,
  editorToDraft,
  extractViolations,
  hasContextEditorChanges,
  hasEmbedPermission,
  parseJsonObject
} from './embedManagementModel'
import {
  buildContextTargetFields,
  contextTargetIssue,
  resolveContextFormRelease,
  validateContextBindingTargets
} from './embedContextTargetModel'

const props = defineProps({
  view: { type: Object, required: true }
})
const emit = defineEmits(['refresh'])

const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const targetOptionsLoading = ref(false)
const targetOptionsError = ref('')
const activeForms = ref([])
const activeLists = ref([])
const entityFields = ref([])
const targetFields = ref([])
const contextTargetLoading = ref(false)
const contextTargetError = ref('')
const contextTargetVerification = ref({
  FIXED_FILTER: false,
  FORCED_FORM_VALUE: false
})
const editor = ref(null)
const savedDraftJson = ref('')
const draftState = ref({ version: null })
const localError = ref('')
const violations = ref([])

const capabilities = EMBED_CAPABILITIES
const scalarContextTypes = new Set(['string', 'integer', 'number', 'boolean'])
const contextFieldTypes = [
  { label: '未指定', value: '' },
  { label: '文本', value: 'string' },
  { label: '整数', value: 'integer' },
  { label: '数字', value: 'number' },
  { label: '布尔值', value: 'boolean' },
  { label: '对象', value: 'object' },
  { label: '数组', value: 'array' },
  { label: '空值', value: 'null' }
]
const isList = computed(() => props.view.surfaceType === 'LIST')
const blockedCapabilities = computed(() => [
  ...EMBED_V1_BLOCKED_CAPABILITIES
])
const entryModeOptions = computed(() =>
  isList.value
    ? ['LIST', 'CREATE', 'VIEW']
    : ['CREATE', 'VIEW']
)
const canManage = computed(() =>
  props.view.status !== 'RETIRED'
  && permission(EMBED_PERMISSIONS.manage)
)
const contextSourceOptions = computed(() =>
  (editor.value?.contextEditor?.fields || []).filter(field => field.name)
)
const eligibleContextSourceOptions = computed(() =>
  contextSourceOptions.value.filter(isContextSourceField)
)
const dirty = computed(() => {
  if (!editor.value || !savedDraftJson.value) return false
  try {
    return JSON.stringify(
      editorToDraft(editor.value, props.view.surfaceType)
    ) !== savedDraftJson.value
  } catch {
    return true
  }
})

watch(() => props.view.id, loadDraft, { immediate: true })

function permission(value) {
  return hasEmbedPermission(
    userStore.permissions,
    value,
    userStore.isSuperAdmin
  )
}

async function loadDraft() {
  loading.value = true
  localError.value = ''
  violations.value = []
  try {
    const result = await embedManagementApi.views.draft(props.view.id)
    applyDraft(result)
    await loadTargetOptionsByCode(editor.value.entityCode)
  } catch (error) {
    localError.value = describeEmbedManagementError(error)
  } finally {
    loading.value = false
  }
}

function applyDraft(result) {
  const draft = result?.draft || {}
  draftState.value = {
    version: result?.version
  }
  editor.value = draftToEditor(draft, props.view.surfaceType)
  savedDraftJson.value = JSON.stringify(draft)
}

function buildDraft() {
  localError.value = ''
  violations.value = []
  const draft = editorToDraft(editor.value, props.view.surfaceType)
  validateContextBindingTargets(
    draft.contextBindings,
    targetFields.value,
    contextTargetVerification.value
  )
  return draft
}

async function saveDraft() {
  let draft
  try {
    draft = buildDraft()
  } catch (error) {
    localError.value = error.message
    return
  }
  saving.value = true
  try {
    const result = await embedManagementApi.views.updateDraft(
      props.view.id,
      buildExpectedVersionPayload(draftState.value.version, { draft })
    )
    applyDraft(result)
    ElMessage.success('配置已保存，新 Launch 将立即使用；已打开 Session 不受影响')
    emit('refresh')
  } catch (error) {
    violations.value = extractViolations(error)
    localError.value = violations.value.length
      ? '配置校验未通过，请按下表修复'
      : describeEmbedManagementError(error)
  } finally {
    saving.value = false
  }
}

async function handleEntitySelected(entity) {
  editor.value.entityCode = entity?.entityCode || ''
  editor.value.listKey = ''
  editor.value.defaultFormId = ''
  await loadTargetOptions(entity?.id, entity)
}

async function loadTargetOptionsByCode(entityCode) {
  if (!entityCode) return loadTargetOptions(null)
  try {
    const entity = await entityApi.getByCode(entityCode)
    await loadTargetOptions(entity?.id, entity)
  } catch {
    await loadTargetOptions(null)
  }
}

async function loadTargetOptions(entityId, knownEntity = null) {
  activeForms.value = []
  activeLists.value = []
  entityFields.value = []
  targetFields.value = []
  resetContextTargetVerification()
  targetOptionsError.value = ''
  if (!entityId) return
  targetOptionsLoading.value = true
  try {
    const [entity, forms, lists] = await Promise.all([
      Array.isArray(knownEntity?.fields)
        ? Promise.resolve(knownEntity)
        : entityApi.getById(entityId),
      getFormsByEntity(String(entityId)),
      entityListConfigApi.getByEntityId(entityId)
    ])
    entityFields.value = (entity?.fields || [])
      .filter(field => field?.fieldCode)
    activeForms.value = (forms || []).filter(hasActiveRelease)
    activeLists.value = (lists || []).filter(hasActiveRelease)
    const missing = isList.value
      ? (!activeLists.value.length ? '该实体没有可用的 ACTIVE 列表发布版本。' : '')
      : (!activeForms.value.length ? '该实体没有可用的 ACTIVE 表单发布版本。' : '')
    targetOptionsError.value = missing
    await loadPublishedContextTargets()
  } catch (error) {
    targetOptionsError.value = `目标资源加载失败：${describeEmbedManagementError(error)}`
  } finally {
    targetOptionsLoading.value = false
  }
}

function addContextField() {
  if (editor.value.contextEditor.fields.length >= 32) {
    ElMessage.warning('上下文字段最多允许 32 个')
    return
  }
  const field = createContextField('', nextContextRowId('field'))
  editor.value.contextEditor.fields.push(field)
}

function removeContextField(index) {
  const field = editor.value.contextEditor.fields[index]
  const names = new Set([field?.name, field?.originalName].filter(Boolean))
  if (editor.value.contextEditor.bindings.some(binding =>
    binding.sourceFieldId
      ? binding.sourceFieldId === field?.id
      : names.has(binding.source)
  )) {
    ElMessage.warning('该上下文字段仍被映射使用，请先删除对应映射')
    return
  }
  editor.value.contextEditor.fields.splice(index, 1)
}

function addContextBinding() {
  const source = eligibleContextSourceOptions.value[0]
  if (!source) {
    ElMessage.warning('请先新增一个必传的标量上下文字段')
    return
  }
  const binding = createContextBinding(nextContextRowId('binding'))
  binding.sourceFieldId = source.id
  binding.source = source.name
  editor.value.contextEditor.bindings.push(binding)
}

function removeContextBinding(index) {
  editor.value.contextEditor.bindings.splice(index, 1)
}

/** 高级 JSON 是覆盖式兼容入口；存在表单改动时必须先明确确认丢弃。 */
async function syncContextFromAdvancedJson() {
  let config
  try {
    config = parseJsonObject(editor.value.advancedJson, '高级配置 JSON')
  } catch (error) {
    localError.value = error.message
    return
  }
  if (hasContextEditorChanges(editor.value.contextEditor)) {
    try {
      await ElMessageBox.confirm(
        '重新载入会丢弃上方上下文表单中尚未保存的修改，是否继续？',
        '确认重新载入',
        {
          type: 'warning',
          confirmButtonText: '丢弃并重新载入',
          cancelButtonText: '取消'
        }
      )
    } catch {
      return
    }
  }
  editor.value.contextEditor = contextToEditor(
    config.contextSchema ?? {},
    config.contextBindings ?? []
  )
  localError.value = ''
  ElMessage.success('已从高级 JSON 重新载入上下文表单')
}

function contextFieldLabel(field) {
  const label = field.title ? `${field.title}（${field.name}）` : field.name
  if (!field.required) return `${label} · 需设为必传`
  if (!scalarContextTypes.has(field.type)) return `${label} · 需使用标量类型`
  return label
}

function isContextSourceField(field) {
  return Boolean(field?.name && field.required && scalarContextTypes.has(field.type))
}

function contextTargetOptions(selectedCode) {
  if (!selectedCode || targetFields.value.some(field => field.fieldCode === selectedCode)) {
    return targetFields.value
  }
  // 历史目标已从实体中移除时仍展示其代码，用户可明确改选而不会无声清空。
  return [...targetFields.value, {
    fieldCode: selectedCode,
    fieldName: '已失效字段',
    published: false,
    queryable: false,
    writable: false
  }]
}

function targetFieldLabel(field, usage) {
  const name = field.fieldName || field.fieldLabel || field.fieldCode
  const label = name === field.fieldCode ? name : `${name}（${field.fieldCode}）`
  const issue = contextTargetIssue(field, usage)
  return issue ? `${label} · ${issue}` : label
}

function isContextTargetField(field, usage) {
  return contextTargetVerification.value[usage] === true
    && !contextTargetIssue(field, usage)
}

function resetContextTargetVerification() {
  contextTargetError.value = ''
  contextTargetVerification.value = {
    FIXED_FILTER: false,
    FORCED_FORM_VALUE: false
  }
}

function pageRows(result) {
  return Array.isArray(result) ? result
    : result?.records || result?.list || result?.items || []
}

function activeReleaseSnapshot(releases, activeReleaseId, label) {
  const release = pageRows(releases).find(item =>
    String(item?.id || '') === String(activeReleaseId || '')
  )
  if (!release?.snapshotDocument) {
    throw new Error(`${label}的 ACTIVE 发布快照不可用`)
  }
  return typeof release.snapshotDocument === 'string'
    ? parseJsonObject(release.snapshotDocument, `${label}发布快照`)
    : release.snapshotDocument
}

/** 按 Embed 最终固定的基础发布坐标读取新增表单字段，避免误用流程热修复投影。 */
async function loadContextFormFields(entityCode) {
  const resolvedForm = await getFormForNewData(entityCode, { silentError: true })
  if (!resolvedForm) return []
  const releases = await getFormReleases(String(resolvedForm.id))
  const release = resolveContextFormRelease(resolvedForm, releases)
  const snapshot = activeReleaseSnapshot(
    [release], release.id, '新增表单固定版本'
  )
  return snapshot?.legacyFields || []
}

let contextTargetSequence = 0

/**
 * 目标字段能力必须来自当前 ACTIVE UI Release，不能使用正在编辑的实体、列表或表单草稿。
 * LIST 的强制值与服务端一致，复用新增数据表单解析结果。
 */
async function loadPublishedContextTargets() {
  const sequence = ++contextTargetSequence
  targetFields.value = []
  resetContextTargetVerification()
  const entityCode = String(editor.value?.entityCode || '').trim()
  const listKey = String(editor.value?.listKey || '').trim()
  const formId = String(editor.value?.defaultFormId || '').trim()
  if (!entityCode || (isList.value ? !listKey : !formId)) return
  contextTargetLoading.value = true
  try {
    if (isList.value) {
      const list = activeLists.value.find(item => item.listKey === listKey)
      if (!list?.id || !list.activeReleaseId) {
        throw new Error('所选列表没有 ACTIVE 发布版本')
      }
      const [releaseResult, formResult] = await Promise.allSettled([
        entityListConfigApi.getReleases(list.id),
        loadContextFormFields(entityCode)
      ])
      if (sequence !== contextTargetSequence) return
      if (releaseResult.status === 'rejected') throw releaseResult.reason
      const snapshot = activeReleaseSnapshot(
        releaseResult.value,
        list.activeReleaseId,
        '所选列表'
      )
      const formFields = formResult.status === 'fulfilled'
        ? formResult.value
        : []
      targetFields.value = buildContextTargetFields(
        'LIST',
        entityFields.value,
        snapshot?.list?.fields || [],
        formFields
      )
      contextTargetVerification.value = {
        FIXED_FILTER: true,
        FORCED_FORM_VALUE: formResult.status === 'fulfilled'
      }
      if (formResult.status === 'rejected') {
        contextTargetError.value = `新增表单字段无法检查：${describeEmbedManagementError(formResult.reason)}`
      }
    } else {
      const form = activeForms.value.find(item => String(item.id) === formId)
      if (!form?.activeReleaseId) throw new Error('所选表单没有 ACTIVE 发布版本')
      const releases = await getFormReleases(formId)
      if (sequence !== contextTargetSequence) return
      const snapshot = activeReleaseSnapshot(releases, form.activeReleaseId, '所选表单')
      targetFields.value = buildContextTargetFields(
        'FORM',
        entityFields.value,
        [],
        snapshot?.legacyFields || []
      )
      contextTargetVerification.value = {
        FIXED_FILTER: true,
        FORCED_FORM_VALUE: true
      }
    }
  } catch (error) {
    if (sequence !== contextTargetSequence) return
    targetFields.value = buildContextTargetFields(
      props.view.surfaceType,
      entityFields.value
    )
    contextTargetError.value = `ACTIVE 页面字段检查失败：${describeEmbedManagementError(error)}`
  } finally {
    if (sequence === contextTargetSequence) contextTargetLoading.value = false
  }
}

let contextRowSequence = 0
function nextContextRowId(kind) {
  contextRowSequence += 1
  return `context-${kind}-${Date.now()}-${contextRowSequence}`
}

function hasActiveRelease(item) {
  return item?.status === 'ACTIVE'
    || item?.status === 'PUBLISHED'
    || Boolean(item?.activeReleaseId || item?.activeRevision || item?.publishedRevision)
}
</script>

<style scoped>
.draft-panel {
  min-height: 280px;
}

.panel-toolbar,
.revision-info,
.panel-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.panel-toolbar {
  justify-content: space-between;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.revision-info {
  color: #909399;
  font-size: 13px;
}

.panel-alert,
.violation-table {
  margin-bottom: 12px;
}

.form-section {
  padding: 14px 16px 2px;
  margin-bottom: 12px;
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}

.form-section h4 {
  margin: 0 0 12px;
  color: #303133;
}

.section-help {
  margin: -4px 0 12px;
  color: #909399;
  font-size: 12px;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.section-heading h4 {
  margin-bottom: 8px;
}

.binding-heading {
  margin-top: 18px;
}

.context-table {
  margin-bottom: 14px;
}

.sync-json-button {
  margin-bottom: 10px;
}

.field-policy-alert {
  margin-bottom: 14px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.three-columns {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.four-columns {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.capability-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px 12px;
  margin-bottom: 14px;
}

.capability-grid small {
  margin-left: 4px;
  color: #f56c6c;
}

.draft-form :deep(.el-checkbox small) {
  margin-left: 4px;
  color: #f56c6c;
}

.json-editor :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 980px) {
  .three-columns,
  .four-columns,
  .capability-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .form-grid,
  .three-columns,
  .four-columns,
  .capability-grid {
    grid-template-columns: 1fr;
  }
}
</style>
