<template>
  <SettingsCapability :disabled="disabled" :reason="disabledReason">
    <el-form-item v-bind="$attrs">
      <template v-if="$slots.label" #label><slot name="label" /></template>
      <slot />
    </el-form-item>
  </SettingsCapability>
</template>

<script setup>
import SettingsCapability from './SettingsCapability.vue'

defineOptions({ inheritAttrs: false })
defineProps({
  disabled: { type: Boolean, default: false },
  disabledReason: { type: String, default: '当前类型不支持此配置' }
})
</script>
