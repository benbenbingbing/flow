<template>
  <el-tooltip :disabled="!disabled || !reason" :content="reason" placement="top" :show-after="200">
    <div
      class="settings-capability"
      :class="{ 'is-disabled': disabled }"
      :aria-disabled="disabled || undefined"
      :aria-label="disabled ? reason : undefined"
      :tabindex="disabled && reason ? 0 : undefined"
    >
      <div :inert="disabled || undefined"><slot /></div>
    </div>
  </el-tooltip>
</template>

<script setup>
import { computed, inject, provide, reactive, toRefs } from 'vue'
import { formContextKey } from 'element-plus'

const props = defineProps({
  disabled: { type: Boolean, default: false },
  reason: { type: String, default: '当前类型不支持此配置' }
})

// 复用所在表单的上下文，让所有 Element Plus 控件继承禁用状态；保留校验、
// 标签等原有配置。inert 同时拦截自定义编辑器里的原生按钮、键盘和点击操作。
const parentForm = inject(formContextKey, undefined)
if (parentForm) {
  provide(formContextKey, reactive({
    ...toRefs(parentForm),
    disabled: computed(() => props.disabled || Boolean(parentForm.disabled))
  }))
}
</script>

<style scoped>
.settings-capability { min-width: 0; }
.settings-capability.is-disabled { cursor: not-allowed; }
.settings-capability.is-disabled > div { opacity: 0.6; }
</style>
