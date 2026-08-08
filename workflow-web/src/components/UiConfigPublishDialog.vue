<template>
  <el-dialog
    :model-value="modelValue"
    :title="`${configLabel}发布`"
    width="820px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @opened="initialize"
  >
    <el-form label-width="96px">
      <el-form-item label="发布方式">
        <template #label>
          <ConfigHelpLabel
            label="发布方式"
            help-key="uiConfig.releaseMode"
          />
        </template>
        <el-radio-group v-model="form.releaseMode" @change="loadPreview">
          <el-radio-button value="STANDARD">普通发布</el-radio-button>
          <el-radio-button value="HOTFIX" :disabled="!canHotfix">
            兼容热修复
          </el-radio-button>
        </el-radio-group>
        <div class="publish-mode-help">
          <template v-if="form.releaseMode === 'STANDARD'">
            <span v-if="configType === 'FORM'">
              表单需要重新发布流程后生效；运行中实例继续使用原始版本。
            </span>
            <span v-else>
              列表发布后立即切换当前全局生效版本，所有列表页面同步生效。
            </span>
          </template>
          <template v-else>
            <span v-if="configType === 'FORM'">
              所有通过发布校验的表单变更都可热修复；REVIEW 仅提示风险，不阻止发布，并原子作用于当前可发起版本和运行中实例。
            </span>
            <span v-else>
              所有通过发布校验的列表变更都可热修复；REVIEW 仅提示风险，不阻止发布，发布后全局立即生效。
            </span>
          </template>
        </div>
      </el-form-item>

      <el-form-item label="发布说明">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <el-alert
      v-if="configType === 'LIST'"
      title="列表运行时始终读取当前全局生效版本，发布后所有列表页面立即生效。"
      type="warning"
      :closable="false"
      show-icon
      class="publish-alert"
    />

    <div v-loading="previewLoading" class="publish-preview">
      <template v-if="preview">
        <div class="publish-summary">
          <el-tag :type="riskTagType(preview.riskLevel)">
            {{ riskLabel(preview.riskLevel) }}
          </el-tag>
          <span>变更 {{ preview.changedItems?.length || 0 }} 项</span>
          <span v-if="configType === 'FORM'">
            影响流程版本 {{ preview.processVersionCount || 0 }} 个
          </span>
          <span v-if="configType === 'FORM'">
            运行中实例 {{ preview.activeInstanceCount || 0 }} 个
          </span>
          <span v-if="configType === 'FORM'">
            跳过历史实例 {{ preview.skippedHistoricalInstanceCount || 0 }} 个
          </span>
        </div>

        <el-alert
          v-if="preview.blockers?.length"
          :title="preview.blockers.join('；')"
          type="error"
          :closable="false"
          show-icon
          class="publish-alert"
        />

        <el-alert
          v-if="hasForcedTargets"
          :title="forceReviewText"
          type="warning"
          :closable="false"
          show-icon
          class="publish-alert"
        />

        <el-table
          v-if="preview.riskItems?.length"
          :data="preview.riskItems"
          size="small"
          max-height="220"
        >
          <el-table-column prop="section" label="区域" width="120" />
          <el-table-column label="修改项" min-width="220">
            <template #default="{ row }">
              <span :title="row.path">
                {{ publishPathLabel(row.path) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="riskLevel" label="风险" width="90">
            <template #default="{ row }">
              <el-tag :type="riskTagType(row.riskLevel)" size="small">
                {{ riskLabel(row.riskLevel) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="说明" min-width="260" />
        </el-table>

        <el-table
          v-if="preview.targets?.length"
          :data="preview.targets"
          size="small"
          max-height="220"
          class="target-table"
        >
          <el-table-column prop="processName" label="流程" min-width="150" />
          <el-table-column prop="processVersion" label="版本" width="70" />
          <el-table-column prop="activeInstanceCount" label="运行中" width="80" />
          <el-table-column
            prop="skippedHistoricalInstanceCount"
            label="跳过历史"
            width="90"
          />
          <el-table-column label="应用方式" width="100">
            <template #default="{ row }">
              <el-tag :type="targetModeTagType(row)" size="small">
                {{ targetModeLabel(row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="220">
            <template #default="{ row }">
              {{
                row.blockers?.join('；')
                  || row.reviewNotes?.join('；')
                  || '可原子应用'
              }}
            </template>
          </el-table-column>
        </el-table>
      </template>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :loading="publishing"
        :disabled="!canSubmit"
        @click="submit"
      >
        {{ submitLabel }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import {
  previewFormPublish,
  publishForm
} from '@/api/entityForm'
import { entityListConfigApi } from '@/api/entityListConfig'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  modelValue: Boolean,
  configType: { type: String, required: true },
  configId: { type: String, required: true },
  configLabel: { type: String, default: 'UI 配置' }
})

const emit = defineEmits(['update:modelValue', 'published'])
const userStore = useUserStore()
const preview = ref(null)
const previewLoading = ref(false)
const publishing = ref(false)
const form = reactive({
  releaseMode: 'STANDARD',
  description: ''
})

const canHotfix = computed(() =>
  userStore.isSuperAdmin
  || userStore.permissions.includes('entity:ui-config:hotfix')
)
const canSubmit = computed(() =>
  Boolean(preview.value?.changed && preview.value?.canPublish)
)
const forcedTargets = computed(() =>
  (preview.value?.targets || []).filter(target =>
    target.compatible && target.applicationMode === 'FULL_SNAPSHOT'
  )
)
const hasForcedTargets = computed(() =>
  form.releaseMode === 'HOTFIX' && forcedTargets.value.length > 0
)
const forceReviewText = computed(() =>
  `${forcedTargets.value.length} 个流程版本无法安全增量对齐，`
    + '将使用当前草稿的完整快照强制覆盖；新快照仍会完整校验并原子生效。'
)
const submitLabel = computed(() => {
  if (form.releaseMode !== 'HOTFIX') return '普通发布'
  return hasForcedTargets.value
    ? '强制发布热修复'
    : '发布兼容热修复'
})

function requestPayload(includePreviewState = false) {
  return {
    releaseMode: form.releaseMode,
    rolloutScope: form.releaseMode === 'HOTFIX'
      ? 'ACTIVE_AND_FUTURE'
      : undefined,
    description: form.description,
    ...(includePreviewState && preview.value
      ? {
          expectedActiveReleaseId: preview.value.activeReleaseId,
          expectedDraftHash: preview.value.draftHash,
          impactToken: preview.value.impactToken
        }
      : {})
  }
}

async function initialize() {
  form.releaseMode = 'STANDARD'
  form.description = ''
  await loadPreview()
}

async function loadPreview() {
  previewLoading.value = true
  try {
    preview.value = props.configType === 'FORM'
      ? await previewFormPublish(props.configId, requestPayload())
      : await entityListConfigApi.previewPublish(
          props.configId,
          requestPayload()
        )
  } catch (error) {
    preview.value = null
    ElMessage.error(error?.message || '发布预检失败')
  } finally {
    previewLoading.value = false
  }
}

async function submit() {
  if (form.releaseMode === 'HOTFIX') {
    await loadPreview()
  }
  if (!canSubmit.value) return
  publishing.value = true
  try {
    const payload = requestPayload(true)
    const release = props.configType === 'FORM'
      ? await publishForm(props.configId, payload)
      : await entityListConfigApi.publish(props.configId, payload)
    ElMessage.success(
      form.releaseMode === 'HOTFIX'
        ? hasForcedTargets.value
          ? '强制热修复已通过完整快照原子生效'
          : '兼容热修复已原子生效'
        : '普通发布成功'
    )
    emit('published', release)
    emit('update:modelValue', false)
  } catch (error) {
    ElMessage.error(error?.message || '发布失败')
    if (error?.response?.status === 409 || error?.status === 409) {
      await loadPreview()
    }
  } finally {
    publishing.value = false
  }
}

function riskTagType(risk) {
  return {
    SAFE: 'success',
    REVIEW: 'warning',
    BLOCKED: 'warning'
  }[risk] || 'info'
}

function riskLabel(risk) {
  return {
    SAFE: '低风险',
    REVIEW: '需复核',
    BLOCKED: '需复核'
  }[risk] || '待评估'
}

function targetModeTagType(target) {
  if (!target?.compatible) return 'danger'
  return target.applicationMode === 'FULL_SNAPSHOT'
    ? 'warning'
    : 'success'
}

function targetModeLabel(target) {
  if (!target?.compatible) return '不可应用'
  return target.applicationMode === 'FULL_SNAPSHOT'
    ? '强制覆盖'
    : '增量'
}

function publishPathLabel(path) {
  const raw = String(path || '').trim()
  if (!raw) return '配置调整'
  const labels = [
    ['fieldInitializationMapping', '子字段初始化映射'],
    ['parameterMapping', '子表单运行参数映射'],
    ['parameterContract', '子表单参数传递契约'],
    ['inputParameterSchema', '子表单输入参数契约'],
    ['dataSourceBindingsDocument', '数据源绑定'],
    ['extensionConfig', '扩展配置'],
    ['viewConfig', '表单设置'],
    ['propsDocument', '节点属性']
  ]
  return labels.find(([key]) => raw.includes(key))?.[1] || raw
}
</script>

<style scoped>
.publish-mode-help {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.publish-alert,
.target-table {
  margin-top: 12px;
}

.publish-preview {
  min-height: 96px;
}

.publish-summary {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 12px;
  color: var(--el-text-color-regular);
}

</style>
