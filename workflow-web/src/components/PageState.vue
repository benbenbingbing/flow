<template>
  <div class="page-state" :class="{ compact }" role="status">
    <el-result
      :icon="resultIcon"
      :title="title"
      :sub-title="description"
    >
      <template v-if="retryable" #extra>
        <el-button type="primary" @click="emit('retry')">重新加载</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  type: {
    type: String,
    default: 'empty',
    validator: value => ['empty', 'error', 'permission', 'stale'].includes(value)
  },
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  retryable: { type: Boolean, default: false },
  compact: { type: Boolean, default: false }
})

const emit = defineEmits(['retry'])

const resultIcon = computed(() => ({
  empty: 'info',
  error: 'error',
  permission: 'warning',
  stale: 'warning'
}[props.type] || 'info'))
</script>

<style scoped>
.page-state {
  display: grid;
  min-height: 280px;
  place-items: center;
  padding: 24px;
}

.page-state.compact {
  min-height: 180px;
  padding: 12px;
}

.page-state.compact :deep(.el-result) {
  padding: 12px;
}
</style>
