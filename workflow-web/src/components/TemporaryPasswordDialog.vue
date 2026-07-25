<template>
  <el-dialog
    :model-value="modelValue"
    title="临时密码已生成"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="emit('closed')"
  >
    <el-alert
      title="该密码只显示这一次"
      description="请通过安全渠道交给用户。用户登录后必须先修改密码，才能继续使用系统。"
      type="warning"
      :closable="false"
      show-icon
    />
    <el-descriptions :column="1" border class="password-details">
      <el-descriptions-item label="用户名">{{ username || '-' }}</el-descriptions-item>
      <el-descriptions-item label="临时密码">
        <div class="password-value">
          <code>{{ temporaryPassword }}</code>
          <el-tooltip content="复制临时密码" placement="top">
            <el-button
              :icon="CopyDocument"
              circle
              aria-label="复制临时密码"
              @click="copyPassword"
            />
          </el-tooltip>
        </div>
      </el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button type="primary" @click="emit('update:modelValue', false)">我已妥善记录</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { CopyDocument } from '@element-plus/icons-vue'

const props = defineProps<{
  modelValue: boolean
  username: string
  temporaryPassword: string
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'closed'): void
}>()

const copyPassword = async () => {
  try {
    await navigator.clipboard.writeText(props.temporaryPassword)
    ElMessage.success('临时密码已复制')
  } catch {
    ElMessage.error('复制失败，请手动记录临时密码')
  }
}
</script>

<style scoped>
.password-details {
  margin-top: 18px;
}

.password-value {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.password-value code {
  overflow-wrap: anywhere;
  color: #303133;
  font-size: 15px;
}
</style>
