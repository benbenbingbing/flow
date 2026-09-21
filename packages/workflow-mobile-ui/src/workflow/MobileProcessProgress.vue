<template>
  <div class="mobile-progress">
    <div class="progress-summary"><div><span class="progress-caption">流程状态</span><strong><span class="status-dot" />{{ statusLabel }}</strong></div><button v-if="hasDiagram" class="progress-diagram-link" aria-label="查看完整流程图" @click="$emit('diagram')"><VanIcon name="cluster-o" />流程图<VanIcon name="arrow" /></button></div>
    <section v-for="group in groups" :key="group.key" class="progress-group" :class="group.key">
      <h3>{{ group.label }}<span>{{ group.nodes.length }}</span><small v-if="group.key === 'activeNodes' && group.nodes.length > 1">多节点同时办理</small></h3>
      <div class="progress-nodes"><div v-for="node in group.nodes" :key="node.id" class="progress-node"><span class="node-icon"><VanIcon :name="group.icon" /></span><div><strong>{{ node.name }}</strong><p v-if="node.assignees">{{ node.assignees }}</p></div></div></div>
    </section>
    <VanEmpty v-if="!groups.length" description="暂无节点进度" />
  </div>
</template>
<script setup>
import { computed } from 'vue'
import { Icon as VanIcon, Empty as VanEmpty } from 'vant'
const props = defineProps({ progress: { type: Object, default: () => ({}) }, nodes: { type: Array, default: () => [] }, hasDiagram: Boolean })
defineEmits(['diagram'])
const statusLabel = computed(() => ({ RUNNING: '运行中', COMPLETED: '已完成', SUSPENDED: '已挂起', TERMINATED: '已终止', REJECTED: '已驳回', WITHDRAWN: '已撤回' })[props.progress.status] || props.progress.status || '—')
// 按真实状态分组，保留并行节点，不能以列表序号推断流程完成比例。
const groups = computed(() => [ ['activeNodes', '当前办理', 'clock-o'], ['completedNodes', '已完成', 'passed'], ['terminatedNodes', '已终止', 'cross'] ].map(([key, label, icon]) => ({ key, label, icon, nodes: (props.progress[key] || []).map(node => {
  const id = typeof node === 'object' ? node.id || node.nodeId : node, metadata = props.nodes.find(item => item.nodeId === id || item.id === id) || {}
  const people = props.progress.nodeAssigneesMap?.[id] || [props.progress.nodeAssigneeMap?.[id]].filter(Boolean)
  return { id, type: metadata.type, name: metadata.nodeName || metadata.name || node.nodeName || node.name || id, assignees: people.map(user => typeof user === 'object' ? user.displayName || user.name || user.username : user).join('、') }
}).filter(node => !['sequenceFlow', 'association', 'messageFlow'].includes(node.type)) })).filter(group => group.nodes.length))
</script>
<style scoped>
.progress-summary { display: flex; align-items: center; justify-content: space-between; gap: 12px; background: var(--flow-mobile-accent-soft); border-radius: 10px; padding: 12px 14px; margin: 4px 0 24px; }.progress-caption { display: block; font-size: 11px; color: var(--flow-mobile-muted); margin-bottom: 6px; }.progress-summary strong { display: flex; align-items: center; gap: 7px; font-size: 15px; font-weight: 600; }.status-dot { width: 6px; height: 6px; background: var(--flow-mobile-accent); border-radius: 50%; }.progress-diagram-link { display: flex; align-items: center; gap: 6px; border: 0; background: var(--flow-mobile-surface); border-radius: 8px; min-height: 44px; padding: 0 10px; color: var(--flow-mobile-accent-text); font-size: 12px; cursor: pointer; }.progress-diagram-link > .van-icon:first-child { font-size: 17px; }
.progress-group { margin-bottom: 22px; }.progress-group h3 { display: flex; align-items: center; gap: 8px; margin: 0 0 10px; font-size: 13px; font-weight: 600; }.progress-group h3 span { font-size: 11px; color: var(--flow-mobile-muted); font-weight: 400; }.progress-group small { margin-left: auto; font-size: 11px; color: var(--flow-mobile-muted); font-weight: 400; }.progress-nodes { border: 1px solid var(--flow-mobile-border); border-radius: 10px; padding: 0 12px; }.progress-node { display: flex; align-items: flex-start; gap: 10px; padding: 14px 0; }.progress-node + .progress-node { border-top: 1px solid var(--flow-mobile-border); }.progress-node > div { min-width: 0; }.node-icon { display: grid; place-items: center; flex-shrink: 0; width: 28px; height: 28px; font-size: 17px; border-radius: 50%; background: var(--flow-mobile-success-soft); color: var(--flow-mobile-success); }.progress-node strong { font-size: 14px; line-height: 28px; font-weight: 500; overflow-wrap: anywhere; }.progress-node p { font-size: 12px; color: var(--flow-mobile-muted); margin: 3px 0 0; line-height: 1.7; overflow-wrap: anywhere; }.terminatedNodes .node-icon { color: #b14c3b; background: #fbece7; }.activeNodes .progress-nodes { border-color: var(--flow-mobile-accent-border); }.activeNodes .node-icon { background: var(--flow-mobile-accent); color: var(--flow-mobile-on-accent); }
</style>
