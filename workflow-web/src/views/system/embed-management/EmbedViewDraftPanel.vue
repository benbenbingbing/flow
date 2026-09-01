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

      <section class="form-section advanced-section">
        <h4>
          <ConfigHelpLabel label="高级 JSON" help-key="embed.view.advancedJson" />
        </h4>
        <p class="section-help">
          保留 contextSchema、contextBindings 和 ui 等完整配置；LIST 与 FORM
          的字段和操作栏都直接跟随 Flow 原生页，旧 fieldPolicy/actionPolicy
          投影覆盖会在保存时清理。
          保存时，上方基础字段会覆盖 JSON 中的同名路径。
        </p>
        <el-input
          v-model="editor.advancedJson"
          type="textarea"
          :rows="18"
          spellcheck="false"
          class="json-editor"
        />
      </section>
    </el-form>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { embedManagementApi } from '@/api/system/embedManagement'
import { entityApi } from '@/api/entity'
import { entityListConfigApi } from '@/api/entityListConfig'
import { getFormsByEntity } from '@/api/entityForm'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import EntityDefinitionPicker from '@/components/EntityDefinitionPicker.vue'
import { useUserStore } from '@/stores/user'
import {
  EMBED_CAPABILITIES,
  EMBED_PERMISSIONS,
  EMBED_V1_BLOCKED_CAPABILITIES,
  buildExpectedVersionPayload,
  describeEmbedManagementError,
  draftToEditor,
  editorToDraft,
  extractViolations,
  hasEmbedPermission
} from './embedManagementModel'

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
const editor = ref(null)
const savedDraftJson = ref('')
const draftState = ref({ version: null })
const localError = ref('')
const violations = ref([])

const capabilities = EMBED_CAPABILITIES
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
  return editorToDraft(editor.value, props.view.surfaceType)
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
  await loadTargetOptions(entity?.id)
}

async function loadTargetOptionsByCode(entityCode) {
  if (!entityCode) return loadTargetOptions(null)
  try {
    const entity = await entityApi.getByCode(entityCode)
    await loadTargetOptions(entity?.id)
  } catch {
    await loadTargetOptions(null)
  }
}

async function loadTargetOptions(entityId) {
  activeForms.value = []
  activeLists.value = []
  targetOptionsError.value = ''
  if (!entityId) return
  targetOptionsLoading.value = true
  try {
    const [forms, lists] = await Promise.all([
      getFormsByEntity(String(entityId)),
      entityListConfigApi.getByEntityId(entityId)
    ])
    activeForms.value = (forms || []).filter(hasActiveRelease)
    activeLists.value = (lists || []).filter(hasActiveRelease)
    const missing = isList.value
      ? (!activeLists.value.length ? '该实体没有可用的 ACTIVE 列表发布版本。' : '')
      : (!activeForms.value.length ? '该实体没有可用的 ACTIVE 表单发布版本。' : '')
    targetOptionsError.value = missing
  } catch (error) {
    targetOptionsError.value = `目标资源加载失败：${describeEmbedManagementError(error)}`
  } finally {
    targetOptionsLoading.value = false
  }
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
