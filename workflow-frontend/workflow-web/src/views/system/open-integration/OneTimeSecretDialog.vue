<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="min(640px, 92vw)"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @close="close"
  >
    <el-alert
      title="该密钥关闭后无法再次查看"
      type="warning"
      :closable="false"
      show-icon
      class="secret-alert"
    />
    <el-form label-position="top">
      <el-form-item
        v-for="field in fields"
        :key="field.label"
        :label="field.label"
      >
        <div class="secret-value">
          <el-input
            :model-value="field.value"
            readonly
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 5 }"
          />
          <el-tooltip content="复制" placement="top">
            <el-button
              :aria-label="`复制${field.label}`"
              @click="copy(field.value)"
            >
              <el-icon><DocumentCopy /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="close">我已妥善保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { DocumentCopy } from '@element-plus/icons-vue'

defineProps({
  modelValue: { type: Boolean, required: true },
  title: { type: String, default: '一次性密钥' },
  fields: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'closed'])

async function copy(value) {
  try {
    await navigator.clipboard.writeText(String(value || ''))
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择')
  }
}

function close() {
  emit('update:modelValue', false)
  emit('closed')
}
</script>

<style scoped>
.secret-alert {
  margin-bottom: 18px;
}

.secret-value {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 40px;
  gap: 8px;
  width: 100%;
}
</style>
