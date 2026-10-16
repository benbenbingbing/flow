<template><MobileUserPicker v-bind="$attrs" :show="show" :model-value="modelValue" :load-options="loadOptions" :multiple="multiple" :title="title" @update:show="$emit('update:show', $event)" @confirm="$emit('confirm', $event)" /></template>
<script setup>
import MobileUserPicker from './MobileUserPicker.vue'
defineProps({ show: Boolean, modelValue: { type: Array, default: () => [] }, loadOptions: { type: Function, required: true }, multiple: Boolean, title: { type: String, default: '选择业务数据' } })
defineEmits(['update:show', 'confirm'])
</script>
