<template>
  <el-button
    class="built-in-action-button"
    :class="{ 'has-custom-text': button.textColor, 'has-custom-background': button.backgroundColor, 'has-custom-border': button.borderColor }"
    :data-builtin-action="actionKey"
    :type="button.buttonType || 'default'"
    :size="button.size"
    :plain="button.buttonAppearance === 'PLAIN'"
    :text="button.buttonAppearance === 'TEXT'"
    :link="button.buttonAppearance === 'LINK'"
    :round="button.buttonAppearance === 'ROUND'"
    :circle="button.buttonAppearance === 'CIRCLE'"
    :style="buttonStyle"
    :aria-label="button.label"
    :title="button.buttonAppearance === 'CIRCLE' ? button.label : undefined"
  >
    <el-icon v-if="icon"><component :is="icon" /></el-icon>
    <span v-if="button.buttonAppearance !== 'CIRCLE'">{{ button.label }}</span>
  </el-button>
</template>

<script setup>
import { computed } from 'vue'
import { Check, Close, Delete, Document, Download, Edit, Link, Message, Plus, Printer, Promotion, Refresh, RefreshLeft, Select, Setting, Upload, View } from '@element-plus/icons-vue'
import { builtInButtonStyle, resolveBuiltInButton } from '@flow/workflow-core/builtin-button-appearance'

// 只接收展示配置；click、disabled、loading 通过根组件透传，由宿主原有业务逻辑控制。
const props = defineProps({
  actionKey: { type: String, required: true },
  config: { type: Object, default: () => ({}) },
  defaults: { type: Object, required: true }
})
const icons = { Check, Close, Delete, Document, Download, Edit, Link, Message, Plus, Printer, Promotion, Refresh, RefreshLeft, Select, Setting, Upload, View }
const button = computed(() => resolveBuiltInButton(props.config, props.actionKey, props.defaults))
const icon = computed(() => icons[button.value.icon] || null)
const buttonStyle = computed(() => {
  const style = builtInButtonStyle(button.value)
  // 打开入口及行内删除跟随原对象；排序仅作用于区域操作栏。
  if (['open', 'remove'].includes(props.actionKey)) delete style.order
  return style
})
</script>

<style scoped>
/* 文字/链接按钮默认使用透明背景；显式颜色也应生效，禁用时仍交回组件库呈现状态。 */
.built-in-action-button.el-button.has-custom-text:not(.is-disabled) { color: var(--el-button-text-color); }
.built-in-action-button.el-button.has-custom-background:not(.is-disabled) { background-color: var(--el-button-bg-color); }
.built-in-action-button.el-button.has-custom-border:not(.is-disabled) { border-color: var(--el-button-border-color); border-width: 1px; }
</style>
