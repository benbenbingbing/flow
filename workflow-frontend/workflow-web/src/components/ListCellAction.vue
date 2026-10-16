<template>
  <span
    v-if="button"
    class="list-cell-action"
    :class="{ 'is-disabled': disabled }"
    role="button"
    :tabindex="disabled ? -1 : 0"
    :aria-disabled="disabled"
    :aria-label="`${button.label}：${text}`"
    :title="disabled ? (getActionCapabilityReason(row, button.key) || '当前数据不可操作') : button.label"
    @click.capture="activate"
    @keydown.enter.capture="activate"
    @keydown.space.capture="activate"
  ><slot /></span>
  <slot v-else />
</template>

<script setup>
import { computed } from 'vue'
import { canExecuteAction, getActionCapabilityReason } from '@/utils/listButtonPermission'

const props = defineProps({
  button: { type: Object, default: null },
  row: { type: Object, required: true },
  text: { default: '' }
})
const emit = defineEmits(['action'])
const disabled = computed(() => !props.button || !canExecuteAction(props.row, props.button.key))

/**
 * 捕获点击与键盘激活，避免单元格渲染器自己的事件重复执行业务动作。
 * 快捷复制按钮位于此组件外，复制不会触发映射动作；权限在分发前再次检查。
 */
function activate(event) {
  event.preventDefault()
  event.stopImmediatePropagation()
  if (!disabled.value) emit('action', props.button, props.row)
}
</script>

<style scoped>
.list-cell-action {
  color: var(--el-color-primary);
  cursor: pointer;
  border-radius: 2px;
}
/* 状态标签和自定义渲染器也沿用映射入口的蓝色/禁用色，避免可点击内容看起来像普通文本。 */
.list-cell-action :deep(*) { color: inherit !important; }
.list-cell-action:hover { text-decoration: underline; }
.list-cell-action:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.list-cell-action.is-disabled { color: var(--el-text-color-placeholder); cursor: not-allowed; text-decoration: none; }
</style>
