<template>
  <VanEmpty v-if="!items.length" description="暂无审批记录" />
  <ol v-else class="mobile-history"><li v-for="(item, index) in items" :key="index" :class="item.type"><span class="history-marker"><VanIcon :name="item.type === 'danger' ? 'cross' : item.type === 'warning' ? 'exchange' : item.type === 'success' ? 'success' : 'clock-o'" /></span><div class="history-body"><div class="history-heading"><strong>{{ item.title }}</strong></div><p>{{ item.description }}</p><time>{{ item.time?.replace('T', ' ') || '进行中' }}</time></div></li></ol>
</template>
<script setup>
import { Empty as VanEmpty, Icon as VanIcon } from 'vant'
defineProps({ items: { type: Array, default: () => [] } })
</script>
<style scoped>
.mobile-history { list-style: none; margin: 6px 0; padding: 0; }.mobile-history li { position: relative; display: flex; align-items: flex-start; gap: 12px; padding-bottom: 24px; }.mobile-history li:not(:last-child):before { content: ''; position: absolute; left: 13px; top: 30px; bottom: 3px; border-left: 1px solid var(--flow-mobile-border); }.history-marker { position: relative; display: grid; place-items: center; width: 28px; height: 28px; flex-shrink: 0; color: var(--flow-mobile-accent-text); background: var(--flow-mobile-accent-soft); border-radius: 50%; font-size: 15px; }.history-body { flex: 1; min-width: 0; padding-top: 3px; }.history-heading strong { font-size: 14px; line-height: 22px; font-weight: 600; }.mobile-history p { font-size: 13px; color: var(--flow-mobile-muted); line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; margin: 7px 0; }.mobile-history time { font-size: 11px; color: var(--flow-mobile-muted); }.mobile-history .success .history-marker { color: var(--flow-mobile-success); background: var(--flow-mobile-success-soft); }.mobile-history .danger .history-marker { color: #b14c3b; background: #fbece7; }.mobile-history .warning .history-marker { color: #9d701e; background: #fff5df; }
</style>
