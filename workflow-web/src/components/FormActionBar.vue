<template>
  <div
    v-if="visibleActions.length"
    class="form-action-bar"
    :class="{ 'is-inline': inline }"
  >
    <el-button
      v-for="action in visibleActions"
      :key="action.runtimeKey || action.key"
      :type="action.buttonType || 'default'"
      :disabled="action.enabled === false || disabled"
      :loading="loadingKey === (action.runtimeKey || action.key)"
      :title="action.enabled === false ? action.reason : ''"
      @click="$emit('action', action)"
    >
      <el-icon v-if="action.icon && iconMap[action.icon]">
        <component :is="iconMap[action.icon]" />
      </el-icon>
      {{ action.label }}
    </el-button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  Check,
  Close,
  Document,
  Download,
  Edit,
  Link,
  Message,
  Plus,
  Printer,
  Promotion,
  Refresh,
  RefreshLeft,
  Select,
  Setting,
  Upload,
  View
} from '@element-plus/icons-vue'

const props = defineProps({
  actions: { type: Array, default: () => [] },
  loadingKey: { type: String, default: '' },
  disabled: Boolean,
  inline: Boolean
})

defineEmits(['action'])

const visibleActions = computed(() =>
  props.actions.filter(action => action?.visible !== false)
)

const iconMap = {
  Check,
  Close,
  Document,
  Download,
  Edit,
  Link,
  Message,
  Plus,
  Printer,
  Promotion,
  Refresh,
  RefreshLeft,
  Select,
  Setting,
  Upload,
  View
}
</script>

<style scoped>
.form-action-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-height: 32px;
  flex-wrap: wrap;
}

.form-action-bar.is-inline {
  justify-content: flex-start;
  width: 100%;
  padding: 4px 0;
}

.form-action-bar :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
