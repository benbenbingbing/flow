<template>
  <div class="approval-decision-section">
    <div
      v-if="approvalConfig.enabled !== false"
      class="approval-opinion-section"
      :class="{ 'is-collapsed': !expanded }"
    >
      <div class="approval-opinion-header">
        <div class="section-title">审批意见</div>
        <el-button
          class="approval-collapse-toggle"
          text
          type="primary"
          :aria-expanded="expanded"
          aria-controls="approval-decision-content"
          :aria-label="expanded ? '收起审批信息' : '展开审批信息'"
          :title="expanded ? '收起审批信息' : '展开审批信息'"
          @click="emit('update:expanded', !expanded)"
        >
          <span>{{ expanded ? '收起' : '展开' }}</span>
          <el-icon>
            <ArrowUp v-if="expanded" />
            <ArrowDown v-else />
          </el-icon>
        </el-button>
      </div>

      <el-collapse-transition>
        <!-- 使用 v-show 保留审批意见和下一审批人选择，折叠不会影响最终提交。 -->
        <div
          v-show="expanded"
          id="approval-decision-content"
          class="approval-opinion-content"
        >
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
      </el-collapse-transition>
    </div>

  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ArrowDown, ArrowUp } from '@element-plus/icons-vue'
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
  },
  expanded: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits([
  'update:action',
  'update:comment',
  'update:expanded'
])
const nextApproverSectionRef = ref()
const selectedOption = computed(() =>
  props.approvalConfig.options?.find(option => option.value === props.action)
)

function validate() {
  const result = nextApproverSectionRef.value?.validate?.()
    || { valid: true, message: '' }
  // 折叠状态下若校验失败，应恢复内容区，让办理人能直接看到并修正问题。
  if (!result.valid && !props.expanded) {
    emit('update:expanded', true)
  }
  return result
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
  position: relative;
  padding: 12px 0 4px;
}

.approval-opinion-header {
  position: relative;
}

.section-title {
  margin-bottom: 16px;
  padding-left: 8px;
  border-left: 4px solid #409eff;
  color: #303133;
  font-size: 16px;
  font-weight: 600;
}

.approval-opinion-section.is-collapsed {
  padding-bottom: 12px;
}

.approval-opinion-section.is-collapsed .section-title {
  margin-bottom: 0;
}

.approval-collapse-toggle {
  position: absolute;
  top: -13px;
  left: 50%;
  min-width: 72px;
  height: 26px;
  margin: 0;
  transform: translateX(-50%);
  border: 1px solid #dcdfe6;
  border-top: 0;
  border-radius: 0 0 6px 6px;
  background: #ffffff;
  box-shadow: 0 2px 4px rgb(0 0 0 / 8%);
}

.approval-collapse-toggle:hover,
.approval-collapse-toggle:focus-visible {
  background: #ecf5ff;
}

.approval-collapse-toggle :deep(.el-icon) {
  margin-left: 4px;
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
