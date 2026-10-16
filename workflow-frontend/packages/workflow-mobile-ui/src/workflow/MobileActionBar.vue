<template>
  <footer v-if="primary.length || (more.length && !$slots.more)" class="mobile-action-bar">
    <VanButton v-if="more.length && !$slots.more" :disabled="Boolean(loadingKey)" @click="openMore">更多操作</VanButton>
    <VanButton v-for="action in primary" :key="action.runtimeKey || action.key" type="primary" :disabled="action.enabled === false || Boolean(loadingKey)" :loading="loadingKey === (action.runtimeKey || action.key)" @click="$emit('action', action)">{{ action.label }}</VanButton>
  </footer>
  <slot v-if="more.length" name="more" :open="openMore" :disabled="Boolean(loadingKey)" :expanded="showMore" />
  <VanActionSheet teleport="body" v-model:show="showMore" :actions="more.map(action => ({ name: action.label, disabled: action.enabled === false, action }))" cancel-text="取消" close-on-click-action @select="item => $emit('action', item.action)" />
</template>
<script setup>
import { computed, ref } from 'vue'
import { Button as VanButton, ActionSheet as VanActionSheet } from 'vant'
const props = defineProps({ actions: { type: Array, default: () => [] }, loadingKey: String })
defineEmits(['action'])
// 弹层始终挂到 body，入口挪到标题栏后仍从屏幕底部弹出。
const showMore = ref(false)
const visible = computed(() => props.actions.filter(action => action.visible !== false))
const primary = computed(() => visible.value.filter(action => action.primary || action.key === 'submitApproval').slice(0, 2))
const more = computed(() => visible.value.filter(action => !primary.value.includes(action)))
/** 自定义入口只改变位置，复用原有操作分类、弹层和提交期间的禁用规则。 */
function openMore() { if (!props.loadingKey) showMore.value = true }
</script>
<style scoped>.mobile-action-bar { position: fixed; bottom: 0; left: 50%; transform: translateX(-50%); width: min(100%, 600px); display: flex; gap: 12px; padding: 10px 16px calc(10px + env(safe-area-inset-bottom)); background: var(--flow-mobile-surface); border-top: 1px solid var(--flow-mobile-border); z-index: 10; }.mobile-action-bar > button { flex: 1; }</style>
