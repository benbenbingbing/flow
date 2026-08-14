<template>
  <div class="approval-decision-section">
    <div
      v-if="approvalConfig.enabled !== false"
      class="approval-opinion-section"
    >
      <div class="section-title">审批意见</div>
      <el-form label-width="80px">
        <el-form-item label="审批操作" required>
          <el-radio-group
            :model-value="action"
            @update:model-value="emit('update:action', $event)"
          >
            <el-radio-button
              v-for="option in approvalConfig.options"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="selectedOption?.showComment !== false"
          :label="approvalConfig.commentLabel || '审批备注'"
        >
          <el-input
            :model-value="comment"
            type="textarea"
            :rows="3"
            :placeholder="`请输入${approvalConfig.commentLabel || '审批备注'}`"
            @update:model-value="emit('update:comment', $event)"
          />
        </el-form-item>

        <NextApproverSection
          ref="nextApproverSectionRef"
          :preview="preview"
          :loading="loading"
          :task-id="taskId"
          :action="action"
          :action-label="actionLabel"
          :comment="comment"
          :form-data="formData"
        />
      </el-form>
    </div>

  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import NextApproverSection from '@/components/NextApproverSection.vue'

const props = defineProps({
  approvalConfig: {
    type: Object,
    required: true
  },
  action: {
    type: String,
    default: ''
  },
  comment: {
    type: String,
    default: ''
  },
  preview: {
    type: Object,
    default: () => ({})
  },
  loading: {
    type: Boolean,
    default: false
  },
  taskId: {
    type: String,
    default: ''
  },
  actionLabel: {
    type: String,
    default: ''
  },
  formData: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['update:action', 'update:comment'])
const nextApproverSectionRef = ref()
const selectedOption = computed(() =>
  props.approvalConfig.options?.find(option => option.value === props.action)
)

function validate() {
  return nextApproverSectionRef.value?.validate?.()
    || { valid: true, message: '' }
}

function getChangedSelections() {
  return nextApproverSectionRef.value?.getChangedSelections?.() || []
}

defineExpose({ validate, getChangedSelections })
</script>

<style scoped>
.approval-decision-section {
  flex: 0 0 auto;
  max-height: min(440px, 48dvh);
  overflow-y: auto;
  border-top: 1px solid #e4e7ed;
  background: #ffffff;
}

.approval-opinion-section {
  padding: 12px 0 4px;
}

.section-title {
  margin-bottom: 16px;
  padding-left: 8px;
  border-left: 4px solid #409eff;
  color: #303133;
  font-size: 16px;
  font-weight: 600;
}

.approval-opinion-section :deep(.el-form-item) {
  margin-bottom: 12px;
}

.approval-opinion-section :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

@media (max-height: 760px) {
  .approval-decision-section {
    max-height: 42dvh;
  }
}
</style>
