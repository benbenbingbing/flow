<template>
  <div
    v-if="visibleActions.length"
    class="form-action-bar"
    :class="{ 'is-inline': inline }"
  >
    <el-button
      v-for="action in visibleActions"
      :key="action.runtimeKey || action.key"
      v-bind="appearanceProps(action)"
      :type="action.buttonType || 'default'"
      :disabled="action.enabled === false || disabled"
      :loading="loadingKey === (action.runtimeKey || action.key)"
      :aria-label="isCircleAction(action) ? action.label : undefined"
      :title="actionTitle(action)"
      @click="$emit('action', action)"
    >
      <el-icon v-if="hasRenderableIcon(action)">
        <component :is="iconComponent(action)" />
      </el-icon>
      <span v-if="!isCircleAction(action)">{{ action.label }}</span>
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
import {
  isRegisteredFormButtonIcon,
  normalizeFormButtonAppearance,
  resolveFormButtonAppearanceProps
} from '@/shared/form-actions'

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

function hasRenderableIcon(action) {
  return Boolean(iconComponent(action))
}

function iconComponent(action) {
  const iconName = String(action?.icon || '').trim()
  return isRegisteredFormButtonIcon(iconName)
    ? iconMap[iconName] || null
    : null
}

function isCircleAction(action) {
  return normalizeFormButtonAppearance(action?.buttonAppearance) === 'CIRCLE'
    && hasRenderableIcon(action)
}

/** 无有效图标的异常圆形配置回退默认外观，避免渲染空白按钮。 */
function appearanceProps(action) {
  const appearance = normalizeFormButtonAppearance(action?.buttonAppearance)
  return resolveFormButtonAppearanceProps(
    appearance === 'CIRCLE' && !hasRenderableIcon(action)
      ? 'DEFAULT'
      : appearance
  )
}

function actionTitle(action) {
  if (action?.enabled === false && action?.reason) return action.reason
  return isCircleAction(action) ? action.label : ''
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
