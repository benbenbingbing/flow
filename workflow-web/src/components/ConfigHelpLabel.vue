<template>
  <span class="config-help-label">
    <span>{{ label }}</span>
    <el-tooltip
      v-if="resolvedContent"
      :content="resolvedContent"
      placement="top"
      :show-after="200"
      :hide-after="50"
      :popper-style="{ maxWidth: '360px', lineHeight: '1.6' }"
    >
      <button
        type="button"
        class="config-help-label__button"
        :aria-label="`查看${label}配置说明`"
        @click.stop
      >
        <el-icon class="config-help-label__icon">
          <QuestionFilled />
        </el-icon>
      </button>
    </el-tooltip>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { QuestionFilled } from '@element-plus/icons-vue'
import { getConfigFieldHelp } from '@/shared/config-field-help'

const props = defineProps({
  label: { type: String, required: true },
  content: { type: String, default: '' },
  helpKey: { type: String, default: '' }
})

const resolvedContent = computed(() =>
  props.content || getConfigFieldHelp(props.helpKey)
)
</script>

<style scoped>
.config-help-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
}

.config-help-label__button {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: help;
  outline: none;
}

.config-help-label__icon {
  font-size: 14px;
  transition: color 0.2s ease;
}

.config-help-label__button:hover,
.config-help-label__button:focus-visible {
  color: var(--el-color-primary);
}
</style>
