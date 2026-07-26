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
              所有通过发布校验的表单变更都可热修复；高风险统一复核后，原子作用于当前可发起版本和运行中实例。
            </span>
            <span v-else>
              仍为全局立即生效，但增加后端风险判定、审计和快速回滚。
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

        <el-form
          v-if="preview.requiresOverride"
          label-width="96px"
          class="override-form"
        >
          <el-form-item label="风险覆盖">
            <el-checkbox
              v-model="form.overrideRisk"
              :disabled="!canOverride"
            >
              {{ configType === 'FORM'
                ? '我已确认高风险表单变更会立即影响运行中任务'
                : '我已确认这项需复核的变更会影响运行中页面' }}
            </el-checkbox>
          </el-form-item>
          <el-form-item label="覆盖原因">
            <el-input
              v-model="form.overrideReason"
              type="textarea"
              :rows="2"
              placeholder="必填：说明紧急原因、验证范围和回退方案"
              @blur="form.overrideRisk && loadPreview()"
            />
          </el-form-item>
        </el-form>

        <el-table
          v-if="preview.riskItems?.length"
          :data="preview.riskItems"
          size="small"
          max-height="220"
        >
          <el-table-column prop="section" label="区域" width="120" />
          <el-table-column prop="path" label="修改项" min-width="220" />
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
          <el-table-column label="兼容" width="80">
            <template #default="{ row }">
              <el-tag :type="row.compatible ? 'success' : 'danger'" size="small">
                {{ row.compatible ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="220">
            <template #default="{ row }">
              {{ row.blockers?.join('；') || '可原子应用' }}
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
        {{ form.releaseMode === 'HOTFIX' ? '发布兼容热修复' : '普通发布' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
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
  description: '',
  overrideRisk: false,
  overrideReason: ''
})

const canHotfix = computed(() =>
  userStore.isSuperAdmin
  || userStore.permissions.includes('entity:ui-config:hotfix')
)
const canOverride = computed(() =>
  userStore.isSuperAdmin
  || userStore.permissions.includes('entity:ui-config:hotfix:override')
)
const canSubmit = computed(() => {
  if (!preview.value?.changed || !preview.value?.canPublish) return false
  if (preview.value.requiresOverride) {
    return form.overrideRisk
      && canOverride.value
      && Boolean(form.overrideReason.trim())
  }
  return true
})

function requestPayload(includePreviewState = false) {
  return {
    releaseMode: form.releaseMode,
    rolloutScope: form.releaseMode === 'HOTFIX'
      ? 'ACTIVE_AND_FUTURE'
      : undefined,
    description: form.description,
    overrideRisk: form.overrideRisk,
    overrideReason: form.overrideReason,
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
  form.overrideRisk = false
  form.overrideReason = ''
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
        ? '兼容热修复已原子生效'
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
  if (props.configType === 'FORM' && risk === 'BLOCKED') {
    return 'warning'
  }
  return {
    SAFE: 'success',
    REVIEW: 'warning',
    BLOCKED: 'danger'
  }[risk] || 'info'
}

function riskLabel(risk) {
  if (props.configType === 'FORM' && risk === 'BLOCKED') {
    return '需复核'
  }
  return {
    SAFE: '低风险',
    REVIEW: '需复核',
    BLOCKED: '已阻断'
  }[risk] || '待评估'
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

.override-form {
  margin-top: 12px;
  padding: 12px 12px 0;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}
</style>
