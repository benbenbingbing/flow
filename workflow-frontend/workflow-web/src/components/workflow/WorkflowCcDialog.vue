<template>
  <el-dialog :model-value="Boolean(task)" title="人工知会" width="min(680px, 92vw)"
    append-to-body :close-on-click-modal="!submitting" :close-on-press-escape="!submitting"
    :show-close="!submitting" @update:model-value="closeDialog">
    <el-form label-width="96px" :disabled="submitting">
      <el-form-item label="流程名称">
        <div class="task-dialog-readonly-value" :title="task?.processName">
          {{ task?.processName || '-' }}
        </div>
      </el-form-item>
      <el-form-item label="流程编码">
        <div class="task-dialog-readonly-value task-dialog-readonly-value--code" :title="task?.code">
          {{ task?.code || '-' }}
        </div>
      </el-form-item>
      <el-form-item label="知会人员" required>
        <UserSelector
          v-model="userIds"
          multiple
          value-key="code"
          :disabled="submitting"
          placeholder="请选择知会人员"
          title="选择知会人员"
        />
      </el-form-item>
      <el-form-item label="知会说明">
        <el-input v-model="comment" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="submitting" @click="closeDialog">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitCc">确认知会</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ccTask } from '@/api/processTask'
import { showRequestError } from '@/shared/request'
import UserSelector from '@/components/UserSelector.vue'

const task = defineModel({ type: Object, default: null })
const props = defineProps({ beforeSubmit: Function })
const emit = defineEmits(['completed'])
const userIds = ref([])
const comment = ref('')
const submitting = ref(false)

watch(task, current => {
  if (!current) return
  userIds.value = []
  comment.value = ''
})

function closeDialog() {
  task.value = null
}

/** 提交选中的人员编码和说明，成功后通知入口刷新对应列表及权限。 */
async function submitCc() {
  const target = task.value
  if (!target?.taskId || submitting.value) return
  if (!userIds.value.length) return ElMessage.warning('请选择知会人员')
  const payload = { userIds: [...userIds.value], comment: comment.value }
  submitting.value = true
  try {
    // 权限检查期间可能切换任务，旧弹窗的数据只能用于原来打开的那次操作。
    if (props.beforeSubmit && !await props.beforeSubmit()) return
    if (task.value !== target) return
    await ccTask(target.taskId, payload)
    if (task.value !== target) return
    ElMessage.success('知会成功')
    closeDialog()
    emit('completed')
  } catch (error) {
    if (task.value === target) showRequestError(error, '知会失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.task-dialog-readonly-value {
  min-width: 0;
  width: 100%;
  color: #303133;
  font-size: 15px;
  line-height: 32px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-dialog-readonly-value--code {
  color: #606266;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
</style>
