<template>
  <component
    :is="embedded ? 'section' : ElDialog"
    v-model="visible"
    :title="dialogTitle"
    width="min(1120px, 96vw)"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    class="related-content-config-dialog"
  >
    <template v-if="!embedded" #header>
      <div class="dialog-heading">
        <div>
          <strong>{{ dialogTitle }}</strong>
          <span>{{ isRelationBound ? '已引用实体关系，只需设置展示和操作' : '按引导完成配置，不需要编写脚本或查询语句' }}</span>
        </div>
        <el-tag type="primary" effect="plain">第 {{ stepNumber(activeStep) }} 步，共 {{ stepIds.length }} 步</el-tag>
      </div>
    </template>

    <div v-loading="catalogLoading" class="related-content-editor">
      <el-steps v-if="!embedded" :active="stepNumber(activeStep) - 1" align-center finish-status="success">
        <el-step description="目标和位置" />
        <el-step v-if="!isRelationBound" description="数据来源" />
        <el-step description="查看或操作范围" />
      </el-steps>

      <div class="step-content">
        <RelatedContentTargetStep
          v-for="targetSection in (embedded ? [{ section: 'basic', tab: 'basic' }, { section: 'target', tab: 'child-pages' }] : [{ section: 'all' }])"
          :key="targetSection.section" :section="targetSection.section" v-show="!embedded ? activeStep === 1 : settingsTab === targetSection.tab"
          :editor="editor"
          :isRelationBound="isRelationBound"
          :relation-match-text="boundEntityRelation && !relationBindingError ? relationMatchLabel(boundEntityRelation) : ''"
          :contentTypeOptions="contentTypeOptions"
          :targetCatalogLoading="targetCatalogLoading"
          :targetContentOptions="targetContentOptions"
          :positionOptions="positionOptions"
          :ownerType="ownerType"
          :availableAnchorOptions="availableAnchorOptions"
          :target-parameter-schema="targetParameterSchema"
          :source-readable-field-options="sourceReadableFieldOptions"
          @handleContentTypeChange="handleContentTypeChange"
          @handleTargetContentChange="handleTargetContentChange"
          @handlePositionChange="handlePositionChange"
          @handleAnchorChange="handleAnchorChange"
        >
          <template #relation-binding>
            <template v-if="isRelationBound">
              <el-form-item v-if="canSelectBinding" label="实体关系" required>
                <el-select v-model="boundRelationKey" class="relation-binding-select" filterable placeholder="选择已定义的实体关系" style="width: 100%" @change="selectBinding">
                  <el-option v-for="relation in sourceRelations" :key="relationUsageKey(relation)" :value="relationUsageKey(relation)" :disabled="relation.enabled === false || relation.enabled === 0" :label="`${relation.relationName} · ${relationTarget(relation).entityName} · ${relation.direction === 'REVERSE' ? '所属记录' : relation.relationType === 'ONE_TO_ONE' ? '一对一' : '一对多'}`" />
                </el-select>
              </el-form-item>
              <el-alert v-if="canSelectBinding && !catalogLoading && !sourceRelations.length && !relationBindingError" title="请先在实体设计中定义关系，再返回这里选择展示页面。" type="info" :closable="false" />
              <el-alert v-if="relationBindingError" class="relation-binding-error" :title="relationBindingError" type="error" :closable="false" show-icon />
            </template>
          </template>
        </RelatedContentTargetStep>
        <RelatedContentPageSettings v-if="embedded" v-show="settingsTab === 'child-pages'" :editor="editor" :parameter-schema="targetParameterSchema" :source-fields="sourceReadableFieldOptions.map(item => item.raw)" />
        <el-empty v-if="embedded && settingsTab === 'rules'" description="子表单内部字段校验在目标表单中维护；当前关联区域不重复配置字段规则。" :image-size="70" />
        <el-empty v-if="embedded && settingsTab === 'interaction'" description="引用字段的选择与快捷回填请在字段属性中配置。" :image-size="70" />


        <component :is="embedded ? SettingsSection : 'section'" title="数据来源" :default-expanded="true" v-if="!isRelationBound" v-show="!embedded ? activeStep === 2 : settingsTab === 'child-pages'">
          <StepHeading
            v-if="!embedded"
            number="2"
            title="数据来源"
            description="目标页面按自己的参数声明接收来源页面传入的值。"
          />
          <el-alert
            v-if="hasLegacyExtension"
            title="此处已有旧版页面扩展配置。可在目标页面维护数据源、动作或组件，如需替换，请重新添加关联内容。"
            type="info"
            :closable="false"
            show-icon
          />
          <div class="relation-preview">
            <div>
              <strong>关联结果说明</strong>
              <span>{{ relationSummary }}</span>
            </div>
            <div class="real-record-test">
              <EntitySelector
                v-if="sourceEntity.entityCode"
                v-model="sourceTestRecordId"
                entity-type="CUSTOM"
                :entity-code="sourceEntity.entityCode"
                title="选择用于测试的来源记录"
                placeholder="自动选择一条有权查看的记录"
                @change="testResult = null"
              />
              <el-button
                :loading="testing"
                :disabled="!canTest"
                @click="testWithRecord"
              >
                使用一条真实数据测试
              </el-button>
            </div>
          </div>
          <div v-if="testResult" class="real-record-test-result">
            <el-alert
              :title="testResult.title"
              :description="testResult.description"
              :type="testResult.type"
              :closable="true"
              show-icon
              @close="testResult = null"
            />
            <el-descriptions
              v-if="testResult.details"
              :column="2"
              border
              size="small"
            >
              <el-descriptions-item label="来源记录">
                {{ testSourceLabel(testResult.details.sourceRecord) }}
              </el-descriptions-item>
              <el-descriptions-item label="匹配结果">
                {{ Number(testResult.details.matchedCount || 0) }} 条有权查看的数据
              </el-descriptions-item>
              <el-descriptions-item label="实际筛选条件" :span="2">
                {{ testFilterSummary(testResult.details.filters) }}
              </el-descriptions-item>
              <el-descriptions-item label="权限检查">
                {{ testResult.details.authorized === false ? '未通过' : '已通过' }}
              </el-descriptions-item>
              <el-descriptions-item label="目标记录示例">
                {{ testTargetSummary(testResult.details.targetRecordIds) }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </component>

        <component :is="embedded ? SettingsSection : 'section'" title="关联操作" :default-expanded="true" v-show="!embedded ? activeStep === 3 : settingsTab === 'child-pages'">
          <StepHeading
            v-if="!embedded"
            :number="String(stepNumber(3))"
            title="允许做什么"
            description="选择用户可以执行的动作。目标实体的权限和数据范围仍会独立校验。"
          />
          <div class="action-options" role="group" aria-label="关联操作">
            <div
              v-for="option in actionOptions"
              :key="option.value"
              class="action-option"
            >
              <el-checkbox
                :model-value="editor.config.actions.includes(option.value)"
                :disabled="isActionDisabled(option.value)"
                @change="toggleAction(option.value, $event)"
              >
                {{ option.label }}
              </el-checkbox>
              <ConfigHelpLabel
                :label="option.label"
                :show-label="false"
                :content="option.description"
              />
            </div>
          </div>

        </component>

        <div v-if="!embedded && activeStep === stepIds[stepIds.length - 1]" class="review-card">
          <div class="review-card__heading">
            <strong>保存前确认</strong>
            <el-tag :type="localValidation.valid ? 'success' : 'danger'" effect="plain">
              {{ localValidation.valid ? '配置完整' : `${localValidation.errors.length} 项待完善` }}
            </el-tag>
          </div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="显示效果">{{ naturalSummary }}</el-descriptions-item>
            <el-descriptions-item label="数据关联">{{ relationSummary }}</el-descriptions-item>
            <el-descriptions-item label="允许操作">{{ actionSummary }}</el-descriptions-item>
          </el-descriptions>
          <div v-if="!localValidation.valid" class="validation-errors">
            <button
              v-for="error in localValidation.errors"
              :key="`${error.step}-${error.field}-${error.message}`"
              type="button"
              @click="activeStep = visibleStep(error.step)"
            >
              第 {{ stepNumber(visibleStep(error.step)) }} 步：{{ error.message }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <template v-if="!embedded" #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">取消</el-button>
        <div>
          <el-button v-if="activeStep > 1" @click="goPrevious">上一步</el-button>
          <el-button v-if="stepNumber(activeStep) < stepIds.length" type="primary" @click="goNext">下一步</el-button>
          <el-button
            v-if="isRelationBound || activeStep === 3"
            type="primary"
            :loading="saving"
            :disabled="catalogLoading || !!relationBindingError || (isRelationBound && !boundEntityRelation)"
            @click="save"
          >{{ isRelationBound ? '保存显示配置' : '保存关联内容' }}</el-button>
        </div>
      </div>
    </template>
  </component>
</template>

<script setup>
import { showRequestError } from '@/shared/request'

import StepHeading from './RelatedContentStepHeading.vue'

import RelatedContentTargetStep from './RelatedContentTargetStep.vue'
import { fieldOptions, contentScopedFieldOptions, isPublishedAsset, normalizeRows } from './relatedContentOptions.js'

import { computed, reactive, ref, watch } from 'vue'
import { ElDialog, ElMessage } from 'element-plus'
import RelatedContentPageSettings from './RelatedContentPageSettings.vue'
import SettingsSection from '@/components/SettingsSection.vue'
import { validatePageParameterMappings } from '@flow/workflow-core/page-parameters'
import { safeParseConfig } from '@flow/workflow-core/config-runtime'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import EntitySelector from '@/components/EntitySelector.vue'
import { getFormsByEntity, getEntityFields, getFormRuntimeRelease } from '@/api/entityForm'
import { entityListConfigApi } from '@/api/entityListConfig'
import { entityRelationApi } from '@/api/entityRelation'
import { relationContentType, relationTarget, relationUsageKey, relationMatchLabel } from '@/shared/entity-relation'
import { applyEntityRelationBinding } from '@/shared/relation-content'
import { normalizeRelatedContentActionBar } from '@/shared/related-content-presentation'
import { uiCompositionApi } from '@/api/uiComposition'
import {
  getCustomFormDescriptor,
  getCustomListDescriptor
} from '@/extensions/core/registries/customComponentRegistry.js'
import {
  RELATED_CONTENT_ACTION_OPTIONS,
  RELATED_CONTENT_TYPE_OPTIONS,
  buildRelatedContentPayload,
  createEmptyRelatedContent,
  describeRelatedContent,
  describeRelatedContentActions,
  describeRelatedContentRelation,
  normalizeRelatedContent,
  relatedContentPositionOptions,
  updateRelatedContentAnchor,
  validateRelatedContent
} from '@/shared/related-content'

const props = defineProps({
  embedded: { type: Boolean, default: false },
  modelValue: { type: Object, default: null },
  settingsTab: { type: String, default: 'basic' },
  ownerType: { type: String, required: true },
  ownerId: { type: [String, Number], required: true },
  sourceEntity: { type: Object, default: () => ({}) },
  sourceFields: { type: Array, default: () => [] },
  sourceContentFields: { type: Array, default: () => [] },
  anchorOptions: { type: Array, default: () => [] },
  existingCount: { type: Number, default: 0 }
})

const emit = defineEmits(['saved', 'draft-change'])
const visible = ref(false)
const saving = ref(false)
const testing = ref(false)
const catalogLoading = ref(false)
const targetCatalogLoading = ref(false)
const activeStep = ref(1)
const targetForms = ref([])
const targetLists = ref([])
const targetFields = ref([])
const targetParameterSchema = ref({})
let targetSchemaSequence = 0
const sourceRelations = ref([])
const isRelationBound = ref(false)
const boundRelationCode = ref('')
const boundRelationKey = ref('')
const canSelectBinding = ref(false)
const relationBindingError = ref('')
const testResult = ref(null)
const sourceTestRecordId = ref('')
const editor = reactive(createEmptyRelatedContent({
  ownerType: props.ownerType,
  sourceEntity: props.sourceEntity
}))

// 编辑已保存的实体关系展示时固定关系身份，不能通过通用编辑器另建一套关联条件。
const boundEntityRelation = computed(() => sourceRelations.value.find(item => boundRelationKey.value
  ? relationUsageKey(item) === boundRelationKey.value
  : item.relationCode === boundRelationCode.value
    && (item.direction || 'FORWARD') === (editor.config.relation.direction || 'FORWARD')
    && (item.direction === 'REVERSE'
      ? String(item.parentEntityId) === String(editor.config.target.entityId)
      : !item.parentEntityId || String(item.parentEntityId) === String(props.sourceEntity.id))))
const dialogTitle = computed(() => isRelationBound.value
  ? '关系展示设置'
  : editor.config.relation.type === 'SAME_RECORD' ? '当前记录页面' : '关联内容设置')
const hasLegacyExtension = computed(() => editor.config.relation.type === 'INTERFACE_SERVICE'
  || editor.config.specialHandling.mode !== 'NONE')
// 普通关系统一沿用目标页面渲染，仅保留展示位置与操作范围两个步骤。
const stepIds = computed(() => isRelationBound.value ? [1, 3] : [1, 2, 3])
const visibleStep = step => isRelationBound.value && step <= 2 ? 1 : Math.min(step, 3)
const stepNumber = step => stepIds.value.indexOf(step) + 1

const selectedEntityRelation = computed(() => isRelationBound.value ? boundEntityRelation.value : null)
const contentTypeOptions = computed(() => RELATED_CONTENT_TYPE_OPTIONS.map(option => ({
  label: option.label,
  value: option.value,
  disabled: !!selectedEntityRelation.value && option.value !== relationContentType(selectedEntityRelation.value)
})))
const actionOptions = RELATED_CONTENT_ACTION_OPTIONS
const positionOptions = computed(() => relatedContentPositionOptions(props.ownerType))
const availableAnchorOptions = computed(() => {
  if (editor.config.presentation.position !== 'TAB') return props.anchorOptions
  return props.anchorOptions.filter(option =>
    String(option.nodeType || '').toUpperCase() === 'TAB')
})
const targetContentOptions = computed(() => {
  const rows = editor.config.target.contentType === 'FORM'
    ? targetForms.value
    : targetLists.value
  // 关联目录沿用固定目标实体，并排除当前页面，避免配置出直接循环引用。
  return rows.filter(item =>
    (!item.entityId || String(item.entityId) === String(editor.config.target.entityId))
    && !(props.ownerType === editor.config.target.contentType && String(item.id) === String(props.ownerId))
  ).map(item => {
    const isForm = editor.config.target.contentType === 'FORM'
    return {
      ...item,
      name: isForm ? (item.formName || item.formKey) : (item.listName || item.listKey),
      key: isForm ? item.formKey : item.listKey,
      displayName: isForm
        ? `${item.formName || item.formKey}${item.formKey ? ` (${item.formKey})` : ''}`
        : `${item.listName || item.listKey}${item.listKey ? ` (${item.listKey})` : ''}`,
      published: isPublishedAsset(item)
    }
  })
})
const sourceFieldOptions = computed(() => fieldOptions(props.sourceFields))
const targetFieldOptions = computed(() => fieldOptions(targetFields.value))
const sourceReadableFieldOptions = computed(() => contentScopedFieldOptions(
  sourceFieldOptions.value,
  props.sourceContentFields,
  props.ownerType,
  false
))
const naturalSummary = computed(() => describeRelatedContent(editor, props.sourceEntity.entityName || '当前页面'))
const relationSummary = computed(() => describeRelatedContentRelation(editor))
const actionSummary = computed(() => describeRelatedContentActions(editor))
const localValidation = computed(() => {
  const result = validateRelatedContent(editor, props.ownerType)
  const parameterError = validatePageParameterMappings(editor.config.parameterMappings, targetParameterSchema.value, sourceReadableFieldOptions.value.map(item => item.raw))
  if (parameterError) result.errors.push({ step: 1, field: 'parameterMappings', message: parameterError })
  if (isRelationBound.value && !boundEntityRelation.value) result.errors.unshift({ step: 1, field: 'relation', message: '请选择有效的实体关系' })
  if (boundEntityRelation.value?.ownershipType === 'COMPOSITION' && editor.config.actions.some(action => action !== 'VIEW')) {
    result.errors.push({ step: 3, field: 'actions', message: '组成关系在这里仅供查看；需要编辑时请从表单左侧实体关系添加主从编辑组件' })
  }
  if (relationBindingError.value) result.errors.unshift({ step: 1, field: 'relation', message: relationBindingError.value })
  return { ...result, valid: !result.errors.length, firstStep: visibleStep(result.errors[0]?.step || 1) }
})
const canTest = computed(() => Boolean(
  props.ownerId
  && editor.config.target.entityId
  && editor.config.target.contentId
  && validateRelatedContent(editor, props.ownerType).errors.every(error => error.step !== 2)
))
async function loadRelationCatalog() {
  if (!isRelationBound.value || !props.sourceEntity.id) return
  catalogLoading.value = true
  try {
    sourceRelations.value = normalizeRows(await entityRelationApi.available(props.sourceEntity.id))
  } catch (error) {
    relationBindingError.value = error?.message || '实体关系加载失败，请关闭后重试'
  } finally {
    catalogLoading.value = false
  }
}

async function loadTargetCatalog(entityId) {
  targetForms.value = []
  targetLists.value = []
  targetFields.value = []
  if (!entityId) return
  targetCatalogLoading.value = true
  try {
    const [formRows, listRows, fieldRows] = await Promise.all([
      getFormsByEntity(entityId).catch(() => []),
      entityListConfigApi.getByEntityId(entityId).catch(() => []),
      getEntityFields(entityId).catch(() => [])
    ])
    targetForms.value = normalizeRows(formRows)
    targetLists.value = normalizeRows(listRows)
    targetFields.value = normalizeRows(fieldRows)
  } finally {
    targetCatalogLoading.value = false
  }
}

async function loadTargetParameterSchema(contentId = editor.config.target.contentId) {
  const sequence = ++targetSchemaSequence
  targetParameterSchema.value = {}
  if (!contentId) return
  try {
    // 映射只消费目标已发布的参数声明，避免草稿参数在运行时不存在。
    const isForm = editor.config.target.contentType === 'FORM'
    const release = isForm
      ? await getFormRuntimeRelease(contentId)
      : (await entityListConfigApi.getReleases(contentId)).find(item => item.status === 'ACTIVE')
    if (sequence !== targetSchemaSequence) return
    const snapshot = safeParseConfig(release?.snapshotDocument)
    const content = isForm ? snapshot.form : (snapshot.config || snapshot.list)
    targetParameterSchema.value = safeParseConfig(content?.viewConfig).inputParameterSchema || {}
  } catch {
    // 无法取得参数声明时保持空目录，由发布校验检查实际目标参数。
  }
}

/** 新建展示只引用关系身份，不复制字段匹配条件；正反向使用也共享同一条定义。 */
async function selectBinding() {
  const relation = boundEntityRelation.value
  if (!relation) return
  boundRelationCode.value = relation.relationCode
  relationBindingError.value = ''
  editor.config.relation = { type: 'ENTITY_RELATION', relationCode: relation.relationCode, direction: relation.direction || 'FORWARD' }
  delete editor.config.presentation.listOptions
  loadTargetParameterSchema('')
  editor.config.target = { ...relationTarget(relation), contentType: relationContentType(relation), contentId: '', contentKey: '', contentName: '' }
  resetIncompatibleButtonAppearance()
  editor.config.actions = ['VIEW']
  if (!editor.config.name) editor.config.name = relation.relationName
  applyEntityRelationBinding(editor, relation)
  await loadTargetCatalog(editor.config.target.entityId)
}

function handleContentTypeChange() {
  resetIncompatibleButtonAppearance()
  if (editor.config.target.contentType !== 'LIST') delete editor.config.presentation.listOptions
  Object.assign(editor.config.target, {
    contentId: '',
    contentKey: '',
    contentName: ''
  })
  loadTargetParameterSchema('')
  updateRelatedContentAnchor(editor, props.ownerType)
}

/** 目标类型切换会改变系统按钮集合；共用的打开入口和位置设置继续沿用。 */
function resetIncompatibleButtonAppearance() {
  if (editor.config.presentation.actionBar === undefined) return
  editor.config.presentation.actionBar = normalizeRelatedContentActionBar(
    editor.config.presentation.actionBar, editor.config.target.contentType
  )
}

/** 关系基数被外部修改时，重新绑定也可能把表单变为列表；同类型回显不清洗用户草稿。 */
function applyBoundRelationWithButtonAppearance(relation) {
  const previousType = editor.config.target.contentType
  applyEntityRelationBinding(editor, relation)
  if (previousType !== editor.config.target.contentType) resetIncompatibleButtonAppearance()
}

async function handleTargetContentChange(id) {
  const option = targetContentOptions.value.find(item => String(item.id) === String(id))
  editor.config.target.contentName = option?.name || ''
  editor.config.target.contentKey = option?.key || ''
  if (!editor.config.name && option?.name) {
    editor.config.name = `${option.name}关联内容`
  }
  await loadTargetParameterSchema(id)
}

function handlePositionChange() {
  if (editor.config.presentation.position === 'TAB') {
    const selected = props.anchorOptions.find(option =>
      String(option.value) === String(editor.anchorKey || ''))
    if (String(selected?.nodeType || '').toUpperCase() !== 'TAB') {
      editor.anchorKey = ''
    }
  }
  updateRelatedContentAnchor(editor, props.ownerType)
}

function handleAnchorChange(value) {
  editor.anchorKey = value || ''
  delete editor.config.presentation.anchorSide
  editor.anchorType = value ? 'FORM_NODE' : 'OWNER'
}

function isActionDisabled(action) {
  // 实体关系的数量可能已变化，历史勾选项即使不再兼容也必须允许取消。
  if (isRelationBound.value && editor.config.actions.includes(action)) return false
  if (boundEntityRelation.value?.ownershipType === 'COMPOSITION') return action !== 'VIEW'
  if (['CREATE', 'EDIT'].includes(action)) {
    // 表单目标走发布表单提交桥；列表目标表示开放目标列表自带的新增/编辑按钮（仍受列表配置与实体权限过滤）。
    return !['FORM', 'LIST'].includes(editor.config.target.contentType)
  }
  return false
}

function toggleAction(action, checked) {
  const values = new Set(editor.config.actions)
  if (checked) values.add(action)
  else values.delete(action)
  if (action !== 'VIEW' && checked) values.delete('VIEW')
  if (action === 'VIEW' && checked) {
    values.clear()
    values.add('VIEW')
  }
  editor.config.actions = Array.from(values)
}

function resolveCustomComponentDescriptor(name, version) {
  const descriptor = editor.config.target.contentType === 'FORM'
    ? getCustomFormDescriptor(name, version)
    : getCustomListDescriptor(name, version)
  return descriptor?.usageContexts?.includes('RELATED_CONTENT') ? descriptor : undefined
}

/** 历史草稿未保存制品摘要时，只从当前精确 name/version 注册项补齐。 */
function syncCustomComponentArtifactDigest() {
  const customComponent = editor.config.specialHandling.customComponent
  if (!customComponent?.name || customComponent.artifactDigest) return
  const descriptor = resolveCustomComponentDescriptor(
    customComponent.name,
    customComponent.version
  )
  customComponent.artifactDigest = descriptor?.artifactDigest || ''
}

function goNext() {
  const error = localValidation.value.errors.find(item => item.step === activeStep.value)
  if (error) {
    ElMessage.warning(error.message)
    return
  }
  activeStep.value = stepIds.value[Math.min(stepNumber(activeStep.value), stepIds.value.length - 1)]
}

function goPrevious() {
  activeStep.value = stepIds.value[Math.max(stepNumber(activeStep.value) - 2, 0)]
}

async function testWithRecord() {
  testing.value = true
  testResult.value = null
  try {
    const payload = buildRelatedContentPayload(editor, props.ownerType, props.ownerId)
    const result = await uiCompositionApi.test(
      props.ownerType,
      props.ownerId,
      payload.config,
      sourceTestRecordId.value
    )
    testResult.value = {
      type: result?.authorized === false ? 'warning' : 'success',
      title: result?.summary || '测试完成',
      description: result?.description
        || `已解析 ${Number(result?.matchedCount || 0)} 条目标数据；目标权限已校验。`,
      details: result || {}
    }
  } catch (error) {
    testResult.value = {
      type: 'error',
      title: '测试未通过',
      description: error?.message || '请检查关联字段、目标内容和当前用户权限。'
    }
  } finally {
    testing.value = false
  }
}

function testSourceLabel(source = {}) {
  if (!source?.id) return '当前权限范围内没有可用来源记录'
  const title = source.name || source.code || '来源记录'
  return `${title}（记录标识：${source.id}）`
}

function testFilterSummary(filters = {}) {
  const entries = Object.entries(filters || {})
  if (!entries.length) return '无需额外筛选，或已明确没有匹配数据'
  return entries.map(([key, value]) => {
    const base = key.replace(/_(op|start|end)$/i, '')
    const field = targetFieldOptions.value.find(option => option.value === base)
    const label = field?.label || base
    const suffix = key.endsWith('_op') ? '（匹配方式）' : ''
    const displayValue = Array.isArray(value) ? value.join('、') : String(value)
    return `${label}${suffix}：${displayValue}`
  }).join('；')
}

function testTargetSummary(ids = []) {
  if (!Array.isArray(ids) || !ids.length) return '无可展示记录'
  const shown = ids.slice(0, 10).join('、')
  return ids.length > 10 ? `${shown} 等 ${ids.length} 条` : shown
}

async function save() {
  if (catalogLoading.value) return
  if (isRelationBound.value) {
    if (relationBindingError.value) return
    try {
      applyBoundRelationWithButtonAppearance(boundEntityRelation.value)
    } catch (error) {
      relationBindingError.value = error.message
      activeStep.value = 1
      return
    }
  }
  if (selectedEntityRelation.value
    && editor.config.target.contentType !== relationContentType(selectedEntityRelation.value)) {
    activeStep.value = 1
    ElMessage.warning('一对一关系请选择表单，一对多关系请选择列表')
    return
  }
  const validation = localValidation.value
  if (!validation.valid) {
    activeStep.value = validation.firstStep
    ElMessage.warning(validation.errors[0].message)
    return
  }
  saving.value = true
  try {
    updateRelatedContentAnchor(editor, props.ownerType)
    const payload = buildRelatedContentPayload(editor, props.ownerType, props.ownerId)
    const checked = await uiCompositionApi.validate(
      props.ownerType,
      props.ownerId,
      payload.config
    )
    if (checked?.normalizedConfig) payload.config = checked.normalizedConfig
    const saved = editor.id
      ? await uiCompositionApi.update(
          props.ownerType,
          props.ownerId,
          editor.id,
          payload
        )
      : await uiCompositionApi.create(props.ownerType, props.ownerId, payload)
    visible.value = false
    ElMessage.success('关联内容已保存，发布页面配置后生效')
    emit('saved', saved)
  } catch (error) {
    showRequestError(error, error?.message || '保存关联内容失败')
  } finally {
    saving.value = false
  }
}

async function open(value = null, { sameRecord = false } = {}) {
  hydrating = true
  value = value ? JSON.parse(JSON.stringify(value)) : value
  const next = value
    ? normalizeRelatedContent(value, { ownerType: props.ownerType, sourceEntity: props.sourceEntity })
    : createEmptyRelatedContent({ ownerType: props.ownerType, sourceEntity: props.sourceEntity, sameRecord })
  // 打开历史草稿时先按当前呈现方式修正挂载语义，避免旧版组合在界面上出现
  // “未选择入口”，并确保不修改任何业务字段。
  updateRelatedContentAnchor(next, props.ownerType)
  if (!value) next.orderKey = (props.existingCount + 1) * 1000000
  Object.keys(editor).forEach(key => delete editor[key])
  Object.assign(editor, next)
  isRelationBound.value = !['SAME_RECORD', 'INTERFACE_SERVICE'].includes(next.config.relation.type)
  canSelectBinding.value = isRelationBound.value && (!value || next.config.relation.type !== 'ENTITY_RELATION')
  if (canSelectBinding.value) editor.config.relation = { type: 'ENTITY_RELATION', relationCode: '' }
  boundRelationKey.value = ''
  boundRelationCode.value = isRelationBound.value ? next.config.relation.relationCode : ''
  sourceRelations.value = []
  relationBindingError.value = ''
  syncCustomComponentArtifactDigest()
  activeStep.value = 1
  testResult.value = null
  sourceTestRecordId.value = ''
  loadTargetParameterSchema('')
  visible.value = true
  await loadRelationCatalog()
  if (isRelationBound.value && !canSelectBinding.value && !relationBindingError.value) {
    try {
      applyBoundRelationWithButtonAppearance(boundEntityRelation.value)
    } catch (error) {
      relationBindingError.value = error.message
    }
  }
  if (editor.config.target.entityId) {
    await loadTargetCatalog(editor.config.target.entityId)
    await loadTargetParameterSchema(editor.config.target.contentId)
  }
  hydrating = false
  if (props.embedded) emit('draft-change', JSON.parse(JSON.stringify(editor)))
}

// 嵌入设计器时只编辑本地草稿；入口切换不重新读取服务端，不在输入时独立保存。
let hydrating = false
watch(editor, () => {
  if (props.embedded && !hydrating) emit('draft-change', JSON.parse(JSON.stringify(editor)))
}, { deep: true, flush: 'sync' })
// 服务端确认后同步新修订号和规范化结果，防止下一次输入把旧 revision 写回草稿。
watch(() => props.modelValue?.revision, () => {
  if (!props.embedded || hydrating || !props.modelValue) return
  hydrating = true
  Object.assign(editor, normalizeRelatedContent(props.modelValue, { ownerType: props.ownerType, sourceEntity: props.sourceEntity }))
  hydrating = false
})
watch(() => props.modelValue?.compositionKey, () => {
  if (props.embedded && props.modelValue) open(props.modelValue)
}, { immediate: true })
defineExpose({ open })
</script>

<style scoped>
.dialog-heading,
.dialog-footer,
.relation-preview,
.review-card__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.dialog-heading > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.dialog-heading span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.55;
}

.related-content-editor {
  min-height: 550px;
}

.step-content {
  min-height: 380px;
  margin-top: 20px;
  padding: 20px 22px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.relation-preview {
  margin-top: 18px;
  padding: 14px 16px;
  border-radius: 7px;
  background: var(--el-fill-color-light);
}

.relation-preview > div {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.relation-preview span {
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.relation-preview .real-record-test {
  width: min(460px, 100%);
  flex-direction: row;
  align-items: center;
  gap: 10px;
}

.real-record-test .entity-selector {
  min-width: 0;
  flex: 1;
}

.real-record-test-result {
  display: grid;
  gap: 10px;
  margin-top: 10px;
}

.action-options {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 24px;
  margin-bottom: 16px;
}

.action-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.action-option :deep(.el-checkbox) {
  margin-right: 0;
}

.review-card {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 7px;
}

.review-card__heading {
  margin-bottom: 12px;
}

.validation-errors {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.validation-errors button {
  border: 0;
  background: transparent;
  color: var(--el-color-danger);
  cursor: pointer;
  font-size: 12px;
  text-decoration: underline;
}

.dialog-footer > div {
  display: flex;
  gap: 8px;
}

@media (max-width: 820px) {
  .dialog-footer {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
