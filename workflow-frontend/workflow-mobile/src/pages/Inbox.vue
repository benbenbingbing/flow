<template>
  <section class="inbox-page">
    <div class="inbox-sticky-header">
      <header class="inbox-header">
        <h1>{{ definition.label }}</h1>
        <VanSearch v-model="current.keyword" class="inbox-search" :show-action="false" placeholder="搜索" aria-label="搜索流程名称或事项" @search="refresh" @clear="refresh" />
        <div class="inbox-actions">
          <button class="inbox-icon-button" :class="{ filtered: hasFilters }" aria-label="筛选" @click="filterOpen = true"><svg class="inbox-filter-icon" viewBox="0 0 18 22" aria-hidden="true"><path d="M1 1H17L11 10V21L7 18V10Z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" /></svg></button>
          <button class="inbox-avatar" aria-label="用户菜单" @click="userMenu = true">{{ nickname.slice(0, 1) }}</button>
        </div>
      </header>
      <div class="inbox-section"><span>{{ sortLabel }}</span><span>共 {{ current.total }} 条</span></div>
    </div>
    <div v-if="current.error" class="mobile-error" role="alert">{{ current.error }}<button @click="load(kind, current.page === 0)">重试</button></div>
    <VanPullRefresh v-model="current.refreshing" @refresh="refresh">
      <VanList :loading="current.loading" :finished="current.finished" :error="Boolean(current.error)" :immediate-check="false" finished-text="已经到底了" @load="load(kind)">
        <MobileTaskCard v-for="item in current.rows" :key="item.id || item.taskId || item.processInstanceId" :item="item" :kind="kind" @open="openDetail" />
        <VanEmpty v-if="current.initialized && !current.rows.length && !current.loading && !current.error" :description="current.keyword ? '没有找到相关流程' : `暂无${definition.label}`" />
      </VanList>
    </VanPullRefresh>
    <VanTabbar :model-value="kind" safe-area-inset-bottom @change="value => router.replace(`/inbox/${value}`)">
      <VanTabbarItem v-for="tab in INBOXES" :key="tab.key" :name="tab.key" :icon="tab.icon" :badge="badgeCounts[tab.key] > 0 ? badgeCounts[tab.key] : undefined" :badge-props="{ max: 99 }">{{ tab.label }}</VanTabbarItem>
    </VanTabbar>
    <VanActionSheet v-model:show="userMenu" :description="nickname" :actions="[{ name: '退出登录', color: '#bf433b' }]" cancel-text="取消" close-on-click-action @select="leave" />
    <VanPopup v-model:show="filterOpen" position="bottom" round safe-area-inset-bottom>
      <h2 class="mobile-panel-title">筛选流程</h2>
      <VanField v-model="current.startUserName" label="发起人" placeholder="姓名" />
      <VanField v-model="current.startDate" type="date" label="开始日期" />
      <VanField v-model="current.endDate" type="date" label="结束日期" />
      <div class="mobile-panel-actions"><VanButton @click="resetFilters">重置</VanButton><VanButton type="primary" @click="applyFilters">确定</VanButton></div>
    </VanPopup>
  </section>
</template>
<script setup>
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search as VanSearch, List as VanList, PullRefresh as VanPullRefresh, Empty as VanEmpty, Tabbar as VanTabbar, TabbarItem as VanTabbarItem, ActionSheet as VanActionSheet, Popup as VanPopup, Field as VanField, Button as VanButton, showConfirmDialog, showFailToast } from 'vant'
import { MobileTaskCard } from '@flow/workflow-mobile-ui'
import { session, tasks, logout, onSessionCleared } from '../adapters/services.js'
import { INBOXES, createInboxState, createInboxLoader, onInboxesInvalidated } from '../inbox.js'
defineOptions({ name: 'MobileInbox' })
const router = useRouter(), route = useRoute()
const state = reactive(createInboxState()), lastKind = ref('todo')
const badgeCounts = reactive({ todo: 0, cc: 0 })
let badgeGeneration = 0
const kind = computed(() => INBOXES.some(item => item.key === route.params.kind) ? route.params.kind : lastKind.value)
const definition = computed(() => INBOXES.find(item => item.key === kind.value))
const current = computed(() => state[kind.value])
const nickname = computed(() => session.userInfo?.nickname || session.userInfo?.username || '用户')
const sortLabel = computed(() => ({ todo: '按到达时间', done: '按办理时间', started: '按发起时间', cc: '按知会时间' })[kind.value])
const hasFilters = computed(() => Boolean(current.value.startUserName || current.value.startDate || current.value.endDate))
const userMenu = ref(false), filterOpen = ref(false)
const load = createInboxLoader(state, tasks)
/** 角标使用未筛选的待办总数与未读知会数，避免搜索或尚未打开的列表影响提醒。 */
async function loadBadgeCounts() {
  const generation = ++badgeGeneration
  try {
    const statistics = await tasks.getStatistics()
    // 切换页面、办理失效或退出登录后，迟到的统计响应不能覆盖最新状态。
    if (generation !== badgeGeneration) return
    Object.assign(badgeCounts, { todo: Number(statistics?.todoCount) || 0, cc: Number(statistics?.unreadCcCount) || 0 })
  } catch {
    // 请求层已提示错误；保留上次成功的数量，下次刷新时重试。
  }
}
const detach = onSessionCleared(() => {
  badgeGeneration++
  Object.assign(badgeCounts, { todo: 0, cc: 0 })
  const initial = createInboxState()
  for (const key of Object.keys(state)) { const generation = state[key].generation + 1; Object.assign(state[key], initial[key], { generation }) }
})
const detachInvalidation = onInboxesInvalidated(kinds => {
  badgeGeneration++
  // 失效时也淘汰在途请求，避免办理前的旧响应把缓存重新标记成有效。
  kinds.forEach(key => {
    if (state[key]) Object.assign(state[key], { initialized: false, generation: state[key].generation + 1, loading: false })
  })
})
onBeforeUnmount(() => { badgeGeneration++; detach(); detachInvalidation() })
watch(() => route.params.kind, async (value, previous) => {
  if (!state[value]) return
  // 包括从详情返回：已读或办理操作后，即使列表有缓存也要更新角标。
  void loadBadgeCounts()
  if (state[previous]) state[previous].scroll = window.scrollY
  lastKind.value = value
  await load.ensure(value)
  await nextTick(); window.scrollTo(0, state[value].scroll)
}, { immediate: true })
onDeactivated(() => { current.value.scroll = window.scrollY })
onActivated(async () => {
  if (router.options.history.state.refreshInbox) {
    await load(kind.value, true)
    history.replaceState({ ...history.state, refreshInbox: false }, '')
  } else {
    await load.ensure(kind.value)
  }
  await nextTick(); window.scrollTo(0, current.value.scroll)
})
function refresh() { return Promise.all([load(kind.value, true), loadBadgeCounts()]) }
function resetFilters() { Object.assign(current.value, { startUserName: '', startDate: '', endDate: '' }) }
function applyFilters() {
  if (current.value.startDate && current.value.endDate && current.value.startDate > current.value.endDate) { showFailToast('结束日期不能早于开始日期'); return }
  filterOpen.value = false; refresh()
}
function openDetail(item) {
  if (!item.processInstanceId) { showFailToast('该记录缺少流程实例，请刷新后重试'); return }
  current.value.scroll = window.scrollY
  router.push({ name: 'detail', params: { instanceId: item.processInstanceId }, query: { kind: kind.value, taskId: item.taskId || undefined, ccId: kind.value === 'cc' ? item.id : undefined }, state: { row: JSON.parse(JSON.stringify(item)) } })
}
async function leave() {
  try { await showConfirmDialog({ title: '退出登录', message: '确定退出当前账号？' }) } catch { return }
  try { await logout() } finally { router.replace('/login') }
}
</script>
<style scoped>
.inbox-page { padding: 0 18px calc(72px + env(safe-area-inset-bottom)); background: var(--flow-mobile-surface); min-height: 100dvh; }
/* 两行一起吸顶，继续使用页面滚动以保留分页、下拉刷新和返回时的滚动位置；背景覆盖两侧留白。 */
.inbox-sticky-header { position: sticky; top: 0; z-index: 10; margin: 0 -18px; padding: 8px 18px 0; background: var(--flow-mobile-surface); }
/* 四个页签共用单行工具栏；标题和操作按钮固定宽度，搜索框使用剩余空间。 */
.inbox-header { height: 56px; display: flex; align-items: center; gap: 6px; }.inbox-header h1 { margin: 0; flex-shrink: 0; font-size: 16px; font-weight: 650; white-space: nowrap; }
.inbox-actions { display: flex; align-items: center; flex-shrink: 0; gap: 6px; }
/* 仅约束工具栏按钮，避免父级 scoped 选择器把子组件根按钮压成固定高度。 */
.inbox-icon-button, .inbox-avatar { border: 0; background: transparent; color: var(--flow-mobile-accent-text); width: 36px; height: 44px; padding: 0; flex-shrink: 0; cursor: pointer; font-size: 21px; }.inbox-avatar { font-size: 14px; border-radius: 50%; background: radial-gradient(circle, var(--flow-mobile-accent-soft) 0 14px, transparent 14.5px); }.inbox-icon-button.filtered { border-radius: 9px; background: var(--flow-mobile-accent-soft); }
/* 头像宽度与可见圆形一致，去除透明侧边，使圆形右边缘与标题左边缘同为 18px 页边距。 */
.inbox-avatar { width: 28px; }
/* 筛选图形使用贴合轮廓的边界，三处可见间距均为 6px；透明点击区延伸到间距内，便于触控。 */
.inbox-icon-button { position: relative; display: flex; align-items: center; justify-content: center; width: 16px; }
.inbox-icon-button::before { content: ''; position: absolute; inset: 0 -6px; }
.inbox-filter-icon { display: block; width: 16px; height: 20px; }
.inbox-search { flex: 1; min-width: 0; padding: 0; background: transparent; }.inbox-search :deep(.van-search__content) { min-width: 0; border-radius: 9px; background: var(--flow-mobile-inset); padding-left: 10px; }.inbox-search :deep(.van-cell) { height: 35px; min-height: 35px; align-items: center; padding: 0 8px 0 0; }.inbox-search :deep(.van-field__control) { font-size: 13px; }.inbox-search :deep(.van-field__left-icon) { color: var(--flow-mobile-muted); }
/* 收紧说明行上方留白，使其与搜索框之间的距离减半，下方仍保留分隔空间。 */
.inbox-section { padding: 3px 0 17px; display: flex; align-items: center; justify-content: space-between; gap: 12px; border-bottom: 1px solid var(--flow-mobile-border); }.inbox-section > span { color: var(--flow-mobile-muted); font-size: 11px; line-height: 14px; }
:deep(.van-pull-refresh) { min-height: 60vh; }
</style>
