<template><footer class="mobile-action-bar"><VanButton v-if="more.length" :disabled="Boolean(loadingKey)" @click="showMore = true">更多操作</VanButton><VanButton v-for="action in primary" :key="action.runtimeKey || action.key" type="primary" :disabled="action.enabled === false || Boolean(loadingKey)" :loading="loadingKey === (action.runtimeKey || action.key)" @click="$emit('action', action)">{{ action.label }}</VanButton><VanActionSheet teleport="body" v-model:show="showMore" :actions="more.map(action => ({ name: action.label, disabled: action.enabled === false, action }))" cancel-text="取消" close-on-click-action @select="item => $emit('action', item.action)" /></footer></template>
<script setup>
import { computed, ref } from 'vue'
import { Button as VanButton, ActionSheet as VanActionSheet } from 'vant'
const props = defineProps({ actions: { type: Array, default: () => [] }, loadingKey: String })
defineEmits(['action'])
// 操作栏使用 transform 居中，面板必须挂到 body，避免 fixed 定位被局部包含块截断。
const showMore = ref(false)
const visible = computed(() => props.actions.filter(action => action.visible !== false))
const primary = computed(() => visible.value.filter(action => action.primary || action.key === 'submitApproval').slice(0, 2))
const more = computed(() => visible.value.filter(action => !primary.value.includes(action)))
</script>
<style scoped>.mobile-action-bar { position: fixed; bottom: 0; left: 50%; transform: translateX(-50%); width: min(100%, 600px); display: flex; gap: 12px; padding: 10px 16px calc(10px + env(safe-area-inset-bottom)); background: var(--flow-mobile-surface); border-top: 1px solid var(--flow-mobile-border); z-index: 10; }.mobile-action-bar > button { flex: 1; }</style>
