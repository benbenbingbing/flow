<template>
  <button class="mobile-task-card" :class="[`task-${kind}`, { 'is-read': kind === 'cc' && !unread }]" type="button" @click="$emit('open', item)">
    <span v-if="kind === 'cc'" class="task-unread" :class="{ visible: unread }" aria-hidden="true" />
    <div class="task-body">
      <div class="task-heading">
        <h2>{{ item.dataName || item.name || item.processName || '流程详情' }}</h2>
      </div>
      <div class="card-meta">
        <p class="card-context"><VanIcon v-if="kind === 'started'" name="description-o" /><span>{{ item.processName || '流程' }}</span><template v-if="node && kind !== 'started'"><span class="context-divider">·</span><span>{{ node }}</span></template></p>
        <div class="card-statuses">
          <span v-if="kind === 'started'" class="card-status" :class="entityStatus.tone" :aria-label="`实体状态：${entityStatus.label}`">{{ entityStatus.label }}</span>
          <span class="card-status" :class="status.tone" :aria-label="kind === 'todo' ? `实体状态：${status.label}` : kind === 'started' ? `流程状态：${status.label}` : undefined">{{ status.label }}</span>
        </div>
      </div>
      <p v-if="kind === 'started' && nodeDescription" class="card-node"><VanIcon name="cluster-o" /><span>{{ nodeDescription }}</span></p>
      <div class="card-footer">
        <span v-if="kind === 'todo' || kind === 'done'" class="card-person"><span class="person-avatar">{{ person.slice(0, 1) }}</span><span>{{ person }}</span></span>
        <span v-else class="card-time-label">{{ kind === 'started' ? '发起于' : '知会于' }}</span>
        <time>{{ time }}</time><VanIcon name="arrow" />
      </div>
    </div>
  </button>
</template>
<script setup>
import { computed } from 'vue'
import { Icon as VanIcon } from 'vant'
import { resolveEntityStatusLabel } from '@flow/workflow-core/entity-status-runtime'
const props = defineProps({ item: { type: Object, required: true }, kind: { type: String, required: true } })
defineEmits(['open'])
const unread = computed(() => props.item.readStatus !== 'READ')
// 待办和已办都展示流程发起人；办理人 assigneeName 不能作为发起人的回退值。
const person = computed(() => props.item.startUserName || props.item.submitterName || '—')
const node = computed(() => props.item.currentNodeName || props.item.currentTaskName || props.item.taskName || props.item.nodeName || props.item.comment || '')
const nodeDescription = computed(() => {
  const terminal = { COMPLETED: '流程已结束', TERMINATED: '流程已终止', WITHDRAWN: '申请人已撤回' }[props.item.status || props.item.processStatus]
  return terminal || (node.value && node.value !== '-' ? `当前：${node.value}` : '')
})
// 优先采用实体配置的名称；缺失时使用内置状态或原始编码，不根据流程状态推断业务状态。
const entityStatus = computed(() => {
  const value = props.item.entityStatus
  const tone = ['REJECTED', 'TERMINATED'].includes(value) ? 'danger' : ['APPROVED', 'COMPLETED'].includes(value) ? 'success' : value === 'PENDING' ? 'pending' : 'neutral'
  return { label: props.item.entityStatusText || resolveEntityStatusLabel(value) || '—', tone }
})
// 待办展示实体状态，已办保留实际办理结果，我发起的额外展示流程实例状态。
const status = computed(() => {
  if (props.kind === 'cc') return { label: unread.value ? '未读' : '已读', tone: 'quiet' }
  if (props.kind === 'todo') return entityStatus.value
  if (props.kind === 'done') {
    const result = String(props.item.result || '').toLowerCase()
    return { label: props.item.actionLabel || ({ approve: '已通过', reject: '已驳回', transfer: '已转办' }[result]) || '已办理', tone: result === 'reject' ? 'danger' : result === 'transfer' ? 'neutral' : 'success' }
  }
  const value = props.item.status || props.item.processStatus
  return { label: props.item.statusText || ({ RUNNING: '运行中', COMPLETED: '已完成', TERMINATED: '已终止', SUSPENDED: '已挂起', REJECTED: '已驳回', WITHDRAWN: '已撤回' }[value]) || '已发起', tone: ['REJECTED', 'TERMINATED'].includes(value) ? 'danger' : ['SUSPENDED', 'WITHDRAWN'].includes(value) ? 'neutral' : value === 'COMPLETED' ? 'success' : 'brand' }
})
const time = computed(() => {
  // TaskVO.createTime 是当前任务到达时间，endTime 是办理时间；缺失时不能互相替代或回退到流程发起时间。
  const raw = props.kind === 'todo' ? props.item.createTime : props.kind === 'done' ? props.item.endTime : props.kind === 'started' ? props.item.startTime || props.item.createTime : props.item.createTime || props.item.startTime
  if (!raw) return '—'
  const date = new Date(raw)
  return Number.isNaN(date.getTime()) ? String(raw) : date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })
})
</script>
<style scoped>
.mobile-task-card { display: flex; align-items: flex-start; gap: 12px; width: 100%; height: auto; min-height: 112px; text-align: left; background: var(--flow-mobile-surface); border: 0; border-bottom: 1px solid var(--flow-mobile-border); border-radius: 0; padding: 18px 0; margin: 0; color: var(--flow-mobile-text); cursor: pointer; }
.mobile-task-card:active { background: var(--flow-mobile-inset); }.mobile-task-card:focus-visible { outline: 2px solid var(--flow-mobile-accent); outline-offset: -2px; }
.task-body { flex: 1; min-width: 0; }
/* 四类卡片的实体名称独占首行，长名称完整换行；标签与流程信息放到标题下方。 */
.task-heading h2 { min-width: 0; font-size: 16px; font-weight: 600; margin: 0; line-height: 1.5; white-space: normal; overflow-wrap: anywhere; }.task-todo h2, .task-done h2 { font-size: 15px; }
.card-meta { display: flex; align-items: flex-start; gap: 10px; margin-top: 6px; }
.card-meta .card-context { flex: 1; min-width: 0; margin: 0; }
/* 状态按实体、流程顺序同行显示；过长业务状态省略，避免挤掉流程状态或撑宽卡片。 */
.card-statuses { display: flex; align-items: center; flex-shrink: 0; gap: 4px; max-width: 46%; }
.card-status { min-width: 0; max-width: 100%; font-size: 11px; line-height: 20px; padding: 1px 7px; border-radius: 5px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }.card-status:last-child { flex-shrink: 0; }.pending { color: #a76a1a; background: #fff5df; }.success { color: var(--flow-mobile-success); background: var(--flow-mobile-success-soft); }.brand { color: var(--flow-mobile-accent-text); background: var(--flow-mobile-accent-soft); }.danger { color: #b14c3b; background: #fbece7; }.neutral { color: #586d7e; background: #edf1f5; }.quiet { color: var(--flow-mobile-muted); padding-right: 0; }
.card-context, .card-node { display: flex; align-items: baseline; gap: 6px; font-size: 13px; color: var(--flow-mobile-muted); margin: 6px 0 0; line-height: 1.65; overflow-wrap: anywhere; }.card-context > span, .card-node > span { min-width: 0; }.card-context { display: block; }.card-context > .van-icon { margin-right: 6px; }.context-divider { margin: 0 6px; color: var(--flow-mobile-border); flex-shrink: 0; }.card-context .van-icon, .card-node .van-icon { flex-shrink: 0; font-size: 14px; }
.card-footer { display: flex; align-items: center; gap: 6px; margin-top: 12px; color: var(--flow-mobile-muted); font-size: 12px; line-height: 22px; }.card-person { flex: 1; min-width: 0; display: flex; align-items: center; gap: 7px; }.card-person > span:last-child { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.person-avatar { display: grid; place-items: center; flex-shrink: 0; width: 22px; height: 22px; border-radius: 50%; color: var(--flow-mobile-accent-text); background: var(--flow-mobile-accent-soft); font-size: 11px; }.card-footer time { white-space: nowrap; }.card-footer > .van-icon { font-size: 13px; margin-left: auto; }.card-person ~ .van-icon { margin-left: 2px; }
.task-done { padding: 14px 0; }.task-started { padding: 12px 0; }.task-started .card-context, .task-started .card-node { font-size: 12px; line-height: 1.5; }.task-started .card-meta, .task-started .card-node { margin-top: 4px; }.task-started .card-footer { margin-top: 6px; line-height: 20px; }.task-unread { width: 7px; height: 7px; flex-shrink: 0; margin-top: 9px; border-radius: 50%; background: transparent; }.task-unread.visible { background: var(--flow-mobile-accent); }.task-cc { gap: 9px; padding: 14px 0; }.task-cc .card-footer { margin-top: 6px; }.task-cc.is-read h2 { font-weight: 400; }
</style>
