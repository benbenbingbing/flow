<template>
  <div class="policy-editor">
    <el-form-item label="空办理人策略">
      <el-select :model-value="policy.policy" style="width: 100%" @update:model-value="update('policy', $event)">
        <el-option v-if="allowInherit" label="继承流程默认策略" value="INHERIT" />
        <el-option label="阻断发布 / 启动" value="BLOCK_PUBLISH" />
        <el-option label="创建运维事件" value="CREATE_INCIDENT" />
        <el-option label="兜底用户组" value="FALLBACK_GROUP" />
        <el-option label="兜底用户" value="FALLBACK_USER" />
        <el-option label="等待并自动重试" value="WAIT_AND_RETRY" />
      </el-select>
      <div class="policy-hint">{{ policyHint }}</div>
    </el-form-item>
    <el-form-item v-if="policy.policy === 'FALLBACK_USER'" label="兜底用户 ID" required>
      <el-input :model-value="policy.fallbackUser" placeholder="请输入已启用用户 ID" @update:model-value="update('fallbackUser', $event)" />
    </el-form-item>
    <el-form-item v-if="policy.policy === 'FALLBACK_GROUP'" label="兜底用户组编码" required>
      <el-input :model-value="policy.fallbackGroup" placeholder="请输入有效用户组编码" @update:model-value="update('fallbackGroup', $event)" />
    </el-form-item>
    <div v-if="policy.policy === 'WAIT_AND_RETRY'" class="retry-grid">
      <el-form-item label="最大重试次数">
        <el-input-number :model-value="policy.maxRetries" :min="1" :max="20" controls-position="right" @update:model-value="update('maxRetries', $event)" />
      </el-form-item>
      <el-form-item label="首次等待（秒）">
        <el-input-number :model-value="policy.initialDelaySeconds" :min="10" :max="86400" controls-position="right" @update:model-value="update('initialDelaySeconds', $event)" />
      </el-form-item>
      <el-form-item label="退避倍率">
        <el-input-number :model-value="policy.backoffMultiplier" :min="1" :max="10" :step="0.5" controls-position="right" @update:model-value="update('backoffMultiplier', $event)" />
      </el-form-item>
    </div>
    <el-form-item v-if="policy.policy !== 'INHERIT' && policy.policy !== 'BLOCK_PUBLISH'" label="责任人 / 值班组" required>
      <el-input :model-value="policy.responsibilityOwner" placeholder="用于事件归属和告警升级" @update:model-value="update('responsibilityOwner', $event)" />
    </el-form-item>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  allowInherit: { type: Boolean, default: true }
})
const emit = defineEmits(['update:modelValue'])
const defaults = {
  policy: props.allowInherit ? 'INHERIT' : 'BLOCK_PUBLISH',
  fallbackUser: '',
  fallbackGroup: '',
  maxRetries: 3,
  initialDelaySeconds: 60,
  backoffMultiplier: 2,
  responsibilityOwner: ''
}
const policy = computed(() => ({ ...defaults, ...(props.modelValue || {}) }))
const policyHint = computed(() => ({
  INHERIT: '节点不单独决策，使用流程版本中固化的默认策略。',
  BLOCK_PUBLISH: '发布预检或运行时发现空办理人时立即阻断。',
  CREATE_INCIDENT: '保留任务并创建可见、可人工恢复的运维事件。',
  FALLBACK_GROUP: '将任务转交给指定用户组，原始失败原因仍会留痕。',
  FALLBACK_USER: '将任务直接分配给指定用户，原始失败原因仍会留痕。',
  WAIT_AND_RETRY: '创建事件并按指数退避计划重新运行解析器。'
}[policy.value.policy] || '请选择空办理人处理策略。'))

function update(key, value) {
  emit('update:modelValue', { ...policy.value, [key]: value })
}
</script>

<style scoped>
.policy-editor { padding: 14px 14px 2px; border: 1px solid #dfe7e1; border-radius: 12px; background: linear-gradient(145deg, #f7faf7, #fff); }
.policy-hint { margin-top: 7px; color: #718079; font-size: 12px; line-height: 1.55; }
.retry-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.retry-grid :deep(.el-input-number) { width: 100%; }
@media (max-width: 560px) { .retry-grid { grid-template-columns: 1fr; } }
</style>
