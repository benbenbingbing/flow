<template>
  <el-dialog
    :model-value="modelValue"
    :title="`${configLabel}发布`"
    width="min(1040px, calc(100vw - 32px))"
    top="6vh"
    destroy-on-close
    :close-on-click-modal="!publishing"
    :close-on-press-escape="!publishing"
    :show-close="!publishing"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="publish-body">
      <el-alert :title="effectDescription" type="info" :closable="false" show-icon />

      <div v-loading="previewLoading" class="publish-preview" :aria-busy="previewLoading">
        <p v-if="previewLoading" class="preview-status" role="status">正在检查本次发布内容…</p>
        <el-alert v-else-if="previewError" title="发布检查失败" type="error" :closable="false" show-icon>
          <p>{{ previewError }}</p>
          <el-button size="small" @click="loadPreview">重新检查</el-button>
        </el-alert>

        <template v-else-if="preview">
          <section v-if="preview.blockers?.length" class="publish-blockers" aria-label="无法发布的原因">
            <el-alert title="暂时无法发布，请先处理以下问题" type="error" :closable="false" show-icon>
              <ul class="message-list">
                <li v-for="(blocker, index) in preview.blockers" :key="index">{{ blocker }}</li>
              </ul>
            </el-alert>
          </section>

          <section v-if="displayPreview.reviewItems.length" class="review-summary" aria-label="需重点检查">
            <div class="section-heading">
              <strong>需重点检查 {{ displayPreview.reviewItems.length }} 项</strong>
              <el-button v-if="displayPreview.reviewItems.length > 3" link type="warning" @click="showReviewOnly = true">
                查看全部需检查项
              </el-button>
            </div>
            <ul class="message-list">
              <li v-for="item in displayPreview.reviewItems.slice(0, 3)" :key="item.key">
                <strong>{{ item.label }}</strong>：{{ item.impact }}
              </li>
            </ul>
          </section>

          <section class="changes-section" aria-label="本次变更">
            <div class="section-heading changes-heading">
              <div>
                <h3>{{ displayPreview.firstPublish ? '首次发布' : '本次变更' }}</h3>
                <p v-if="displayPreview.firstPublish">
                  将启用以下 {{ displayPreview.total }} 个配置项。
                </p>
                <p v-else-if="displayPreview.total">
                  涉及 {{ displayPreview.total }} 个配置项：{{ countSummary }}。
                </p>
              </div>
              <el-checkbox
                v-if="displayPreview.reviewItems.length"
                v-model="showReviewOnly"
                class="review-filter"
              >只看需检查项</el-checkbox>
            </div>

            <el-empty
              v-if="!preview.changed"
              :image-size="64"
              description="当前草稿与已发布配置一致，无需发布"
            />
            <p v-else-if="displayPreview.equivalentOnly" class="preview-status">
              未发现实际配置调整，仅空值或保存格式发生变化。
            </p>
            <p v-else-if="!displayPreview.total" class="preview-status">
              配置内容已更新，请检查发布依赖后确认发布。
            </p>
            <section v-for="group in visibleGroups" :key="group.key" class="change-group">
              <h4>{{ group.label }} <span>{{ group.items.length }} 项</span></h4>
              <article v-for="item in group.items" :key="item.key" class="change-item">
                <div class="change-item__heading">
                  <div class="change-item__name">
                    <strong>{{ item.label }}</strong>
                    <el-tag :type="changeTagType(item.changeType)" size="small" effect="plain">
                      {{ displayPreview.firstPublish && item.changeType === 'ADDED' ? '启用' : item.changeLabel }}
                    </el-tag>
                    <el-tag v-if="item.reviewRequired" type="warning" size="small">需重点检查</el-tag>
                  </div>
                  <p class="change-impact" :class="{ 'change-impact--review': item.reviewRequired }">{{ item.impact }}</p>
                </div>
                <p v-if="item.previousLabel && item.previousLabel !== item.label" class="previous-name">
                  原名称：{{ item.previousLabel }}
                </p>

                <div v-if="item.details.length" class="value-comparison">
                  <div class="value-comparison__label">{{ item.details[0].label }}</div>
                  <div class="value-comparison__value">
                    <span class="value-caption">当前已发布</span>
                    <span>{{ item.details[0].before }}</span>
                  </div>
                  <span class="comparison-arrow" aria-hidden="true">→</span>
                  <div class="value-comparison__value value-comparison__value--after">
                    <span class="value-caption">{{ displayPreview.firstPublish ? '首次启用' : '本次发布' }}</span>
                    <span>{{ item.details[0].after }}</span>
                  </div>
                </div>
                <details v-if="item.details.length > 1" class="more-changes">
                  <summary>展开另外 {{ item.details.length - 1 }} 项调整</summary>
                  <div v-for="detail in item.details.slice(1)" :key="detail.key" class="additional-change">
                    <div class="value-comparison">
                      <div class="value-comparison__label">{{ detail.label }}</div>
                      <div class="value-comparison__value">
                        <span class="value-caption">当前已发布</span>
                        <span>{{ detail.before }}</span>
                      </div>
                      <span class="comparison-arrow" aria-hidden="true">→</span>
                      <div class="value-comparison__value value-comparison__value--after">
                        <span class="value-caption">{{ displayPreview.firstPublish ? '首次启用' : '本次发布' }}</span>
                        <span>{{ detail.after }}</span>
                      </div>
                    </div>
                    <p v-if="detail.impact" class="detail-impact" :class="{ 'change-impact--review': detail.reviewRequired }">
                      {{ detail.impact }}
                    </p>
                  </div>
                </details>
                <details v-if="item.technicalDetails.length" class="technical-details">
                  <summary>技术详情</summary>
                  <ul>
                    <li v-for="(detail, index) in item.technicalDetails" :key="index">
                      <span>区域：{{ detail.section || '—' }}</span>
                      <span>条目 ID：{{ detail.itemId || '—' }}</span>
                      <span>路径：{{ detail.path || '/' }}</span>
                      <span>变更类型：{{ detail.changeType || '—' }}</span>
                    </li>
                  </ul>
                </details>
              </article>
            </section>
          </section>

          <details v-if="preview.dependencies?.length" class="dependency-card">
            <summary>关联内容与发布依赖 <span>{{ preview.dependencies.length }} 项</span></summary>
            <p class="dependency-help">目标表单使用其当前已发布配置，其他依赖按各自发布规则生效。</p>
            <el-table :data="preview.dependencies" size="small" max-height="220">
              <el-table-column label="类型" width="110">
                <template #default="{ row }">{{ dependencyTypeLabel(row.type) }}</template>
              </el-table-column>
              <el-table-column prop="name" label="业务名称" min-width="170" />
              <el-table-column label="依赖状态" width="150">
                <template #default="{ row }">{{ dependencyVersionLabel(row) }}</template>
              </el-table-column>
              <el-table-column prop="usedBy" label="用于" min-width="150" />
            </el-table>
          </details>
        </template>
      </div>

      <el-form label-position="top" class="publish-description">
        <el-form-item label="发布说明（选填）">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="简要说明本次调整，方便以后查看发布记录"
            show-word-limit
            :disabled="publishing"
          />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <div class="publish-footer">
        <span class="footer-hint" role="status">{{ footerHint }}</span>
        <div class="footer-actions">
          <el-button :disabled="publishing" @click="emit('update:modelValue', false)">取消</el-button>
          <el-button type="primary" :loading="publishing" :disabled="!canSubmit" @click="submit">确认发布</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { previewFormPublish, publishForm } from '@/api/entityForm'
import { entityListConfigApi } from '@/api/entityListConfig'
import { buildPublishPreview } from '@/shared/ui-config-publish-preview'

const props = defineProps({
  modelValue: Boolean,
  configType: { type: String, required: true },
  configId: { type: String, required: true },
  configLabel: { type: String, default: 'UI 配置' }
})

const emit = defineEmits(['update:modelValue', 'published'])
const preview = ref(null)
const previewLoading = ref(false)
const previewError = ref('')
const publishing = ref(false)
const showReviewOnly = ref(false)
const form = reactive({ description: '' })
// 每次打开或切换配置都会更换会话，避免旧请求把其他配置的结果写入当前弹窗。
let sessionVersion = 0
let previewRequest = 0

const displayPreview = computed(() => buildPublishPreview(preview.value, props.configType))
const visibleGroups = computed(() => displayPreview.value.groups
  .map(group => ({ ...group, items: group.items.filter(item =>
    !showReviewOnly.value || !displayPreview.value.reviewItems.length || item.reviewRequired) }))
  .filter(group => group.items.length))
const countSummary = computed(() => [
  ['ADDED', '新增'], ['MODIFIED', '修改'], ['REMOVED', '移除'], ['MOVED', '调整顺序']
].filter(([key]) => displayPreview.value.counts[key])
  .map(([key, label]) => `${label} ${displayPreview.value.counts[key]} 项`).join('、'))
const effectDescription = computed(() => props.configType === 'FORM'
  ? '发布后，所有引用此表单的入口将使用新配置，包括运行中任务和历史详情，无需重新发布流程。'
  : '发布后，所有使用此列表的入口将使用新配置。')
const canSubmit = computed(() => Boolean(props.modelValue
  && !previewLoading.value && !previewError.value && !publishing.value
  && preview.value?.changed && preview.value?.canPublish && !preview.value?.blockers?.length))
const footerHint = computed(() => {
  if (previewLoading.value) return '正在检查发布内容'
  if (previewError.value) return '检查成功后才能发布'
  if (preview.value?.blockers?.length) return '请先处理发布检查发现的问题'
  if (preview.value && !preview.value.changed) return '无需重复发布'
  if (preview.value?.canPublish === false) return '请先处理发布检查发现的问题'
  if (displayPreview.value.equivalentOnly) return '仅配置保存格式变化，发布会同步当前草稿'
  return displayPreview.value.reviewItems.length ? '请确认需重点检查的内容后发布' : ''
})

watch(() => [props.modelValue, props.configType, props.configId], initialize, { immediate: true })
onBeforeUnmount(() => { sessionVersion++; previewRequest++ })

/** 使用同一次预检的版本与草稿指纹发布；服务端据此识别他人修改，避免覆盖未确认的内容。 */
function requestPayload(includePreviewState = false) {
  return {
    description: form.description,
    ...(includePreviewState && preview.value
      ? { expectedActiveReleaseId: preview.value.activeReleaseId, expectedDraftHash: preview.value.draftHash }
      : {})
  }
}

function dependencyTypeLabel(type) {
  return {
    ENTITY_SCHEMA: '实体定义', FORM: '目标表单', LIST: '目标列表',
    INTERFACE_SERVICE: '扩展接口', INTERFACE_EXTENSION: '扩展接口', CUSTOM_COMPONENT: '自定义组件'
  }[String(type || '').toUpperCase()] || '关联依赖'
}

function dependencyVersionLabel(row = {}) {
  if (row.type === 'FORM') return '当前已发布配置'
  const version = row.version
  if (version === null || version === undefined || version === '') return '未提供版本'
  return row.type === 'INTERFACE_SERVICE' ? `修订 ${version}` : `版本 ${version}`
}

function changeTagType(type) {
  return { ADDED: 'success', MODIFIED: 'primary', REMOVED: 'danger', MOVED: 'info' }[type] || 'info'
}

/** 为新打开的配置重置草稿说明及筛选条件；重试检查不经过这里，因此保留用户填写的说明。 */
async function initialize() {
  sessionVersion++
  previewRequest++
  preview.value = null
  previewError.value = ''
  previewLoading.value = false
  publishing.value = false
  showReviewOnly.value = false
  form.description = ''
  if (props.modelValue) await loadPreview()
}

/** 刷新当前配置的可发布内容。请求乱序、切换配置和关闭弹窗后返回的结果均不再写入界面。 */
async function loadPreview() {
  const request = ++previewRequest
  const session = sessionVersion
  const { configId, configType } = props
  previewLoading.value = true
  previewError.value = ''
  preview.value = null
  const isCurrent = () => request === previewRequest && session === sessionVersion
    && props.modelValue && props.configId === configId && props.configType === configType
  try {
    const result = configType === 'FORM'
      ? await previewFormPublish(configId, requestPayload())
      : await entityListConfigApi.previewPublish(configId, requestPayload())
    if (isCurrent()) preview.value = result
  } catch (error) {
    if (isCurrent()) previewError.value = error?.response?.data?.message || error?.message || '发布预检失败，请稍后重试'
  } finally {
    if (isCurrent()) previewLoading.value = false
  }
}

/** 提交已确认的预检结果；遇到并发冲突时保留发布说明并刷新对比，让用户确认新的变更。 */
async function submit() {
  if (!canSubmit.value) return
  const session = sessionVersion
  const { configId, configType } = props
  const isCurrent = () => session === sessionVersion && props.modelValue
    && props.configId === configId && props.configType === configType
  publishing.value = true
  try {
    const payload = requestPayload(true)
    const current = configType === 'FORM'
      ? await publishForm(configId, payload)
      : await entityListConfigApi.publish(configId, payload)
    if (!isCurrent()) return
    ElMessage.success('发布成功，已全局生效')
    emit('published', current)
    emit('update:modelValue', false)
  } catch (error) {
    if (!isCurrent()) return
    if (error?.response?.status === 409 || error?.status === 409) {
      ElMessage.warning('配置已发生变化，已重新检查，请确认最新内容后再次发布')
      await loadPreview()
    } else {
      ElMessage.error(error?.response?.data?.message || error?.message || '发布失败')
    }
  } finally {
    if (isCurrent()) publishing.value = false
  }
}
</script>

<style scoped>
.publish-body { max-height: 68vh; overflow-y: auto; padding-right: 4px; }
.publish-preview { min-height: 96px; margin-top: 20px; }
.preview-status { padding: 24px 0; text-align: center; color: var(--el-text-color-secondary); }
.publish-blockers, .review-summary { margin-bottom: 20px; }
.review-summary { padding: 14px 16px; border: 1px solid var(--el-color-warning-light-7); border-radius: 8px; background: var(--el-color-warning-light-9); }
.review-summary .section-heading { color: var(--el-color-warning-dark-2); }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.message-list { margin: 8px 0 0; padding-left: 20px; line-height: 1.7; overflow-wrap: anywhere; }
.changes-heading { margin-bottom: 16px; align-items: flex-start; }
.changes-heading h3 { margin: 0; font-size: 16px; color: var(--el-text-color-primary); }
.changes-heading p { margin: 8px 0 0; color: var(--el-text-color-regular); }
.review-filter { flex-shrink: 0; }
.change-group { margin-bottom: 18px; }
.change-group h4 { margin: 0; padding: 10px 14px; background: var(--el-fill-color-light); font-size: 14px; border-radius: 6px 6px 0 0; }
.change-group h4 span, .dependency-card summary span { margin-left: 8px; color: var(--el-text-color-secondary); font-size: 12px; font-weight: normal; }
.change-item { border: 1px solid var(--el-border-color-lighter); border-top: 0; padding: 14px; }
.change-item:last-child { border-radius: 0 0 6px 6px; }
.change-item__name { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; overflow-wrap: anywhere; }
.change-item__name strong { color: var(--el-text-color-primary); }
.change-impact { margin: 6px 0 12px; color: var(--el-text-color-regular); line-height: 1.6; overflow-wrap: anywhere; }
.change-impact--review { color: var(--el-color-warning-dark-2); }
.previous-name, .detail-impact { margin: 6px 0; color: var(--el-text-color-secondary); font-size: 12px; }
.detail-impact.change-impact--review { color: var(--el-color-warning-dark-2); }
.value-comparison { display: grid; grid-template-columns: minmax(100px, 0.7fr) minmax(0, 1fr) 20px minmax(0, 1fr); align-items: start; gap: 12px; }
.value-comparison__label { padding-top: 8px; color: var(--el-text-color-regular); overflow-wrap: anywhere; }
.value-comparison__value { display: flex; flex-direction: column; gap: 4px; min-width: 0; padding: 8px 10px; border-radius: 4px; background: var(--el-fill-color-light); white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.5; }
.value-comparison__value--after { background: var(--el-color-primary-light-9); }
.value-caption { color: var(--el-text-color-secondary); font-size: 12px; }
.comparison-arrow { align-self: center; color: var(--el-text-color-placeholder); text-align: center; }
.more-changes, .technical-details { margin-top: 12px; }
.more-changes summary, .technical-details summary, .dependency-card summary { cursor: pointer; width: fit-content; line-height: 1.7; }
.more-changes summary { color: var(--el-color-primary); }
.additional-change { padding-top: 12px; }
.technical-details { color: var(--el-text-color-secondary); font-size: 12px; }
.technical-details ul { padding: 8px 12px; margin: 8px 0 0; list-style: none; background: var(--el-fill-color-light); }
.technical-details li { display: flex; gap: 8px 20px; flex-wrap: wrap; padding: 4px 0; overflow-wrap: anywhere; }
.dependency-card { margin: 18px 0; padding: 12px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; }
.dependency-help { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
.publish-description { margin-top: 20px; }
.publish-description :deep(.el-form-item) { margin-bottom: 4px; }
.publish-footer { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.footer-hint { color: var(--el-text-color-secondary); font-size: 12px; text-align: left; }
.footer-actions { display: flex; flex-shrink: 0; }
@media (max-width: 640px) {
  .section-heading { flex-wrap: wrap; gap: 4px; }
  .value-comparison { grid-template-columns: minmax(0, 1fr) 16px minmax(0, 1fr); gap: 8px; }
  .value-comparison__label { grid-column: 1 / -1; padding-top: 0; }
  .publish-body { max-height: 65vh; }
  .publish-footer { align-items: flex-end; }
}
</style>
