<template>
  <div ref="formSectionRef" v-if="entityData" class="entity-form-section">
    <template v-if="hasConfiguredForm">
      <FormPreviewLinkage
        ref="formPreviewRef"
        :form="runtimeApprovalForm"
        :model-value="entityData"
        @update:model-value="(val) => emit('update:entityData', val)"
        :readonly="formReadonly"
        :mode="mode"
        :show-header="false"
        :node-root-parent-id="nodeRootParentId"
        :excluded-node-ids="excludedNodeIds"
        :entity-code="entityCode"
        :entity-definition="entityDefinition"
        :entity-fields="runtimeEntityFields"
        :context="runtimeContext"
        :data-source-runtime="dataSourceRuntime"
        :form-actions="formActions"
        :action-loading-key="actionLoadingKey"
        @form-action="emit('form-action', $event)"
      />
    </template>
    <template v-else>
      <el-form :model="entityData" label-width="100px" class="entity-form">
        <el-row :gutter="20">
          <el-col v-for="(value, key) in entityData" :key="key" :span="12">
            <el-form-item :label="displayFieldLabel(key)">
              <div v-if="isGroupedFileValue(value)" class="file-display-readonly">
                <div v-for="(urls, groupName) in value" :key="groupName" class="file-group-readonly">
                  <el-tag size="small" type="primary">{{ groupName }}</el-tag>
                  <div v-for="(url, idx) in (Array.isArray(urls) ? urls : [urls])" :key="idx" class="file-item-readonly">
                    <a
                      :href="url"
                      target="_blank"
                      rel="noopener noreferrer"
                      class="file-link"
                    >
                      <el-icon><Document /></el-icon>
                      {{ fileName(url) }}
                    </a>
                  </div>
                </div>
              </div>
              <div v-else-if="Array.isArray(value)" class="file-list-readonly">
                <div v-for="(url, idx) in value" :key="idx" class="file-item-readonly">
                  <a
                    v-if="isFileUrl(url)"
                    :href="url"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="file-link"
                  >
                    <el-icon><Document /></el-icon>
                    {{ fileName(url) }}
                  </a>
                  <span v-else>{{ formatReadonlyValue(url) }}</span>
                </div>
              </div>
              <div v-else-if="isFileUrl(value)" class="file-item-readonly">
                <a
                  :href="value"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="file-link"
                >
                  <el-icon><Document /></el-icon>
                  {{ fileName(value) }}
                </a>
              </div>
              <el-input
                v-else
                :model-value="formatReadonlyFieldValue(key, value)"
                :readonly="true"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { Document } from '@element-plus/icons-vue'
import FormPreviewLinkage from '@/components/FormPreviewLinkage.vue'
import {
  fileName,
  formatReadonlyValue,
  isFileUrl,
  isGroupedFileValue,
  resolveApprovalFieldLabel
} from './entityApprovalDisplay.js'
import {
  buildEntityStatusMap,
  isEntityStatusField,
  resolveEntityStatusLabel,
  withEntityStatusFieldOptions,
  withEntityStatusRuntimeForm
} from '@/shared/entity-status-runtime'

const props = defineProps<{
  entityData: any
  approvalNormalForm: any
  formReadonly: boolean
  mode: string
  entityCode?: string
  entityDefinition?: any
  entityFields?: any[]
  context?: Record<string, any>
  dataSourceRuntime?: any
  nodeRootParentId?: string | number
  excludedNodeIds?: Array<string | number>
  formActions?: any[]
  actionLoadingKey?: string
  entityStatusOptions?: any[]
}>()

const emit = defineEmits<{
  'update:entityData': [val: any]
  'form-action': [action: any]
}>()

const formPreviewRef = ref<any>()
const formSectionRef = ref<HTMLElement>()
const lastValidationError = ref('')
const runtimeEntityFields = computed(() =>
  (props.entityFields || []).map(field =>
    withEntityStatusFieldOptions(field, props.entityStatusOptions || [])
  )
)
const entityStatusMap = computed(() =>
  buildEntityStatusMap(props.entityStatusOptions || [])
)
const runtimeContext = computed(() => ({
  ...(props.context || {}),
  entityStatusMap: entityStatusMap.value,
  entityStatusOptions: props.entityStatusOptions || []
}))
const runtimeApprovalForm = computed(() =>
  withEntityStatusRuntimeForm(
    props.approvalNormalForm,
    runtimeEntityFields.value,
    props.entityStatusOptions || []
  )
)
const hasConfiguredForm = computed(() =>
  Boolean(runtimeApprovalForm.value) && (
    (runtimeApprovalForm.value?.fields?.length || 0) > 0
    || (runtimeApprovalForm.value?.nodes?.length || 0) > 0
    || Boolean(runtimeApprovalForm.value?.customComponent)
  )
)

function displayFieldLabel(fieldCode: string) {
  return resolveApprovalFieldLabel(fieldCode, runtimeEntityFields.value)
}

function formatReadonlyFieldValue(fieldCode: string, value: any) {
  if (isEntityStatusField({ fieldCode })) {
    return resolveEntityStatusLabel(value, entityStatusMap.value)
  }
  return formatReadonlyValue(value)
}

async function validate() {
  lastValidationError.value = ''
  if (!hasConfiguredForm.value) return true
  const valid = (await formPreviewRef.value?.validate?.()) !== false
  if (!valid) {
    // 优先从 FormPreview 读取当前作用域的唯一错误；嵌套子表和普通
    // Element Form 校验没有统一错误对象，等待 DOM 刷新后再作显示文案兜底。
    await nextTick()
    lastValidationError.value = String(
      formPreviewRef.value?.getValidationError?.() || ''
    ).trim() || resolveRenderedValidationError()
  }
  return valid
}

function resolveRenderedValidationError() {
  const root = formSectionRef.value
  if (!root) return ''
  const selectors = [
    '.field-error',
    '.custom-form-unique-error .el-alert__title',
    '.el-alert--error .el-alert__title',
    '.el-form-item__error'
  ]
  for (const selector of selectors) {
    const message = [...root.querySelectorAll<HTMLElement>(selector)]
      .map(element => String(element.textContent || '').trim())
      .find(Boolean)
    if (message) return message
  }
  return ''
}

defineExpose({
  validate,
  getValidationError: () => lastValidationError.value
})
</script>

<style scoped lang="scss">
/* 文件只读展示样式 */
.file-display-readonly {
  width: 100%;
}

.file-list-readonly {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.file-group-readonly {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
}

.file-item-readonly {
  display: flex;
  align-items: center;
}

.file-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #409eff;
  text-decoration: none;
  padding: 4px 8px;
  border-radius: 4px;
  background-color: #ecf5ff;
  transition: all 0.3s;
}

.file-link:hover {
  background-color: #409eff;
  color: #fff;
}
</style>
