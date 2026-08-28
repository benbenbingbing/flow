<template>
  <div v-loading="loading" class="draft-panel">
    <div class="panel-toolbar">
      <div class="revision-info">
        <el-tag effect="plain">Draft r{{ draftState.draftRevision || '-' }}</el-tag>
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
          保存草稿
        </el-button>
        <el-button
          v-if="canManage"
          :disabled="dirty"
          :loading="validating"
          @click="validateSavedDraft"
        >
          校验已保存版本
        </el-button>
        <el-button
          v-if="canPublish"
          type="success"
          :disabled="dirty"
          :loading="publishing"
          @click="publishDraft"
        >
          发布
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
    <el-alert
      v-else-if="validationPassed"
      type="success"
      title="已保存的草稿通过服务端发布校验"
      show-icon
      class="panel-alert"
      @close="validationPassed = false"
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
          <el-form-item label="Entity Code" required>
            <template #label>
              <ConfigHelpLabel label="Entity Code" help-key="embed.view.entityCode" />
            </template>
            <el-input v-model="editor.entityCode" maxlength="100" />
          </el-form-item>
          <el-form-item v-if="isList" label="List Key" required>
            <template #label>
              <ConfigHelpLabel label="List Key" help-key="embed.view.listKey" />
            </template>
            <el-input v-model="editor.listKey" maxlength="100" />
          </el-form-item>
          <el-form-item label="Default Form ID" :required="!isList">
            <template #label>
              <ConfigHelpLabel
                label="Default Form ID"
                help-key="embed.view.defaultFormId"
              />
            </template>
            <el-input v-model="editor.defaultFormId" maxlength="64" />
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

      <section class="form-section">
        <h4>资源 Release 策略</h4>
        <el-form-item label="Strategy">
          <template #label>
            <ConfigHelpLabel
              label="Strategy"
              help-key="embed.view.resourceReleaseStrategy"
            />
          </template>
          <el-radio-group v-model="editor.releaseStrategy">
            <el-radio value="PINNED">PINNED</el-radio>
            <el-radio value="FOLLOW_ACTIVE">FOLLOW_ACTIVE</el-radio>
          </el-radio-group>
        </el-form-item>
        <div v-if="editor.releaseStrategy === 'PINNED'" class="form-grid">
          <el-form-item v-if="isList" label="List Release ID" required>
            <template #label>
              <ConfigHelpLabel
                label="List Release ID"
                help-key="embed.view.resourceReleaseId"
              />
            </template>
            <el-input v-model="editor.listReleaseId" maxlength="64" />
          </el-form-item>
          <el-form-item
            v-if="editor.defaultFormId"
            label="Form Release ID"
            required
          >
            <template #label>
              <ConfigHelpLabel
                label="Form Release ID"
                help-key="embed.view.resourceReleaseId"
              />
            </template>
            <el-input v-model="editor.formReleaseId" maxlength="64" />
          </el-form-item>
        </div>
      </section>

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
        <p class="section-help">每行一个字段代码，发布时会与已发布的 List / Form Release 做交叉校验。</p>
        <div class="form-grid four-columns">
          <el-form-item label="Visible">
            <template #label>
              <ConfigHelpLabel label="Visible" help-key="embed.view.visibleFields" />
            </template>
            <el-input
              v-model="editor.visibleFieldsText"
              type="textarea"
              :rows="6"
            />
          </el-form-item>
          <el-form-item label="Queryable">
            <template #label>
              <ConfigHelpLabel label="Queryable" help-key="embed.view.queryableFields" />
            </template>
            <el-input
              v-model="editor.queryableFieldsText"
              type="textarea"
              :rows="6"
            />
          </el-form-item>
          <el-form-item label="Writable">
            <template #label>
              <ConfigHelpLabel label="Writable" help-key="embed.view.writableFields" />
            </template>
            <el-input
              v-model="editor.writableFieldsText"
              type="textarea"
              :rows="6"
            />
          </el-form-item>
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
          保留 contextSchema、contextBindings、actionPolicy 和 ui 等完整配置。
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { embedManagementApi } from '@/api/system/embedManagement'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
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
const validating = ref(false)
const publishing = ref(false)
const editor = ref(null)
const savedDraftJson = ref('')
const draftState = ref({ draftRevision: null, version: null })
const localError = ref('')
const violations = ref([])
const validationPassed = ref(false)

const capabilities = EMBED_CAPABILITIES
const blockedCapabilities = EMBED_V1_BLOCKED_CAPABILITIES
const isList = computed(() => props.view.surfaceType === 'LIST')
const entryModeOptions = computed(() =>
  isList.value
    ? ['LIST', 'CREATE', 'VIEW']
    : ['CREATE', 'VIEW']
)
const canManage = computed(() =>
  props.view.status !== 'RETIRED'
  && permission(EMBED_PERMISSIONS.manage)
)
const canPublish = computed(() =>
  props.view.status !== 'RETIRED'
  && permission(EMBED_PERMISSIONS.publish)
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
  validationPassed.value = false
  try {
    const result = await embedManagementApi.views.draft(props.view.id)
    applyDraft(result)
  } catch (error) {
    localError.value = describeEmbedManagementError(error)
  } finally {
    loading.value = false
  }
}

function applyDraft(result) {
  const draft = result?.draft || {}
  draftState.value = {
    draftRevision: result?.draftRevision,
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
    ElMessage.success('草稿已保存')
    emit('refresh')
  } catch (error) {
    localError.value = describeEmbedManagementError(error)
  } finally {
    saving.value = false
  }
}

async function validateSavedDraft() {
  validating.value = true
  localError.value = ''
  violations.value = []
  validationPassed.value = false
  try {
    await embedManagementApi.views.validate(
      props.view.id,
      buildExpectedVersionPayload(draftState.value.version)
    )
    validationPassed.value = true
  } catch (error) {
    violations.value = extractViolations(error)
    localError.value = violations.value.length
      ? '发布校验未通过，请按下表修复'
      : describeEmbedManagementError(error)
  } finally {
    validating.value = false
  }
}

async function publishDraft() {
  let releaseNote = ''
  try {
    const result = await ElMessageBox.prompt(
      '发布将生成不可变 Release，请填写发布说明（可选）。',
      '发布 Embed View',
      {
        confirmButtonText: '确认发布',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputValidator: value =>
          !value || value.length <= 500 || '发布说明不能超过 500 字'
      }
    )
    releaseNote = result.value || ''
  } catch {
    return
  }
  publishing.value = true
  localError.value = ''
  violations.value = []
  try {
    const result = await embedManagementApi.views.publish(
      props.view.id,
      buildExpectedVersionPayload(draftState.value.version, { releaseNote })
    )
    ElMessage.success(`已发布 Release r${result.revision}`)
    await loadDraft()
    emit('refresh')
  } catch (error) {
    violations.value = extractViolations(error)
    localError.value = violations.value.length
      ? '发布校验未通过，请按下表修复'
      : describeEmbedManagementError(error)
  } finally {
    publishing.value = false
  }
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
