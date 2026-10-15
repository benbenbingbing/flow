<template>
  <button v-if="useNative" v-bind="$attrs" type="button" class="mobile-built-in-action mobile-built-in-action--native"
    :data-builtin-action="actionKey" :style="buttonStyle" :aria-label="button.label">
    <VanIcon v-if="icon" :name="icon" />{{ button.label }}
  </button>
  <VanButton v-else v-bind="$attrs" class="mobile-built-in-action" :data-builtin-action="actionKey"
    :class="appearanceClasses" native-type="button" :type="buttonType" :size="buttonSize"
    :plain="button.buttonAppearance === 'PLAIN'" :round="['ROUND', 'CIRCLE'].includes(button.buttonAppearance)"
    :style="buttonStyle" :aria-label="button.label">
    <VanIcon v-if="icon" :name="icon" />
    <span v-if="button.buttonAppearance !== 'CIRCLE'">{{ button.label }}</span>
  </VanButton>
</template>
<script setup>
import { computed } from 'vue'
import { Button as VanButton, Icon as VanIcon } from 'vant'
import { normalizeBuiltInActionBar, resolveBuiltInButton } from '@flow/workflow-core/builtin-button-appearance'

// 与 PC 共用外观协议；禁用、加载和点击均透传宿主，配置不参与权限判断或动作分派。
defineOptions({ inheritAttrs: false })
const props = defineProps({
  actionKey: { type: String, required: true },
  config: { type: Object, default: () => ({}) },
  defaults: { type: Object, required: true },
  nativeDefault: { type: Boolean, default: false }
})
const icons = {
  Check: 'success', Close: 'cross', Document: 'description', Download: 'down', Edit: 'edit', Link: 'link-o',
  Message: 'envelop-o', Plus: 'plus', Printer: 'printer', Promotion: 'guide-o', Refresh: 'replay',
  RefreshLeft: 'revoke', Select: 'checked', Setting: 'setting-o', Upload: 'upgrade', View: 'eye-o', Delete: 'delete-o'
}
const button = computed(() => resolveBuiltInButton(props.config, props.actionKey, props.defaults))
const override = computed(() => normalizeBuiltInActionBar(props.config, [props.actionKey]).buttons?.[props.actionKey] || {})
// 历史移动端删除是原生文字按钮；仅改文案或图标时仍保留它原来的留白和触摸范围。
const useNative = computed(() => props.nativeDefault && !['buttonAppearance', 'buttonType', 'size'].some(key => key in override.value))
const icon = computed(() => icons[button.value.icon] || '')
const buttonType = computed(() => button.value.buttonType === 'info' ? 'default' : button.value.buttonType || 'default')
const buttonSize = computed(() => ({ small: 'small', default: 'normal', large: 'large' }[button.value.size] || 'normal'))
const appearanceClasses = computed(() => ({
  'mobile-built-in-action--text': ['TEXT', 'LINK'].includes(button.value.buttonAppearance),
  'mobile-built-in-action--circle': button.value.buttonAppearance === 'CIRCLE'
}))
const buttonStyle = computed(() => {
  const value = button.value
  const isText = ['TEXT', 'LINK'].includes(value.buttonAppearance)
  const colors = { primary: 'var(--van-primary-color)', success: 'var(--van-success-color)', warning: 'var(--van-warning-color)', danger: 'var(--van-danger-color)', info: '#909399' }
  const style = {}
  if (isText && !useNative.value) style.color = colors[value.buttonType] || 'var(--van-text-color)'
  if (value.buttonType === 'info' && !isText) {
    style.backgroundColor = value.buttonAppearance === 'PLAIN' ? 'transparent' : '#909399'
    style.borderColor = '#909399'
    style.color = value.buttonAppearance === 'PLAIN' ? '#909399' : '#fff'
  }
  // 值已经由共享白名单规范化，禁止直接拼接任意 CSS；禁用态保留 Vant 的透明度反馈。
  for (const key of ['textColor', 'backgroundColor', 'borderColor']) {
    if (value[key]) style[key === 'textColor' ? 'color' : key] = value[key]
  }
  if (value.borderRadius != null) style.borderRadius = `${value.borderRadius}px`
  // 行内删除和独立入口不可与宿主内容重排，排序只对同一操作栏中的按钮生效。
  if (value.sort != null && !['remove', 'open'].includes(props.actionKey)) style.order = value.sort
  return style
})
</script>
<style scoped>
.mobile-built-in-action .van-icon + span { margin-left: 4px; }
.mobile-built-in-action--text { background: transparent; border-color: transparent; }
.mobile-built-in-action--circle { padding: 0; width: var(--van-button-normal-height); min-width: 0; }
.mobile-built-in-action--circle.van-button--small { width: var(--van-button-small-height); }
.mobile-built-in-action--circle.van-button--large { width: var(--van-button-large-height); }
.mobile-built-in-action--native .van-icon { margin-right: 4px; }
.mobile-built-in-action--native:disabled { opacity: var(--van-button-disabled-opacity); }
</style>
