<template>
  <div ref="strip" class="workspace-tabs" role="tablist" aria-label="已打开的页面" @wheel="scrollTabs">
    <div v-for="tab in tabs" :key="tab.key" class="workspace-tab" :class="{ 'is-active': tab.key === activeKey }">
      <button type="button" role="tab" :aria-selected="tab.key === activeKey" :tabindex="tab.key === activeKey ? 0 : -1"
        :title="tab.title" @click="$emit('activate', tab)" @keydown="event => navigateKeys(event, tab)">
        {{ tab.title }}
      </button>
      <button v-if="tab.route.path !== '/home'" type="button" class="close-tab" :aria-label="`关闭 ${tab.title}`"
        :disabled="busy" @click="$emit('close', tab.key)"><el-icon><Close /></el-icon></button>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Close } from '@element-plus/icons-vue'
const props = defineProps({ tabs: { type: Array, required: true }, activeKey: String, busy: Boolean })
const emit = defineEmits(['activate', 'close'])
const strip = ref(null)
let resizeObserver

function revealActiveTab() {
  strip.value?.querySelector('.is-active')?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
}

/** 活动标签保持可见，超出的标签只滚动本区域，不挤压搜索和用户菜单。 */
watch(() => [props.activeKey, props.tabs.length], async () => {
  await nextTick()
  revealActiveTab()
}, { immediate: true })

// 侧栏展开或窗口缩窄同样会挤出活动标签，不能只在切换路由时调整滚动位置。
onMounted(() => {
  resizeObserver = new ResizeObserver(revealActiveTab)
  if (strip.value) resizeObserver.observe(strip.value)
})
onBeforeUnmount(() => resizeObserver?.disconnect())

function scrollTabs(event) {
  if (!strip.value || strip.value.scrollWidth <= strip.value.clientWidth) return
  event.preventDefault()
  strip.value.scrollLeft += event.deltaX || event.deltaY
}

/** 支持左右键/Home/End切换与 Delete 关闭，保留原生按钮键盘语义。 */
async function navigateKeys(event, tab) {
  const index = props.tabs.findIndex(item => item.key === tab.key)
  const targets = { ArrowLeft: (index - 1 + props.tabs.length) % props.tabs.length,
    ArrowRight: (index + 1) % props.tabs.length, Home: 0, End: props.tabs.length - 1 }
  if (event.key === 'Delete' && tab.route.path !== '/home') { event.preventDefault(); emit('close', tab.key); return }
  if (!(event.key in targets)) return
  event.preventDefault()
  emit('activate', props.tabs[targets[event.key]])
  await nextTick()
  strip.value?.querySelectorAll('[role="tab"]')[targets[event.key]]?.focus()
}
</script>

<style scoped>
.workspace-tabs { display: flex; flex: 1; min-width: 0; overflow-x: auto; overflow-y: hidden; gap: 5px; margin-left: 12px; scrollbar-width: thin; scrollbar-color: #d4d8df transparent; }
/* Chromium/WebKit 使用明确的 4px 高度；标准 thin 仅供其他浏览器回退，避免覆盖伪元素尺寸。 */
@supports selector(::-webkit-scrollbar) {
  .workspace-tabs { scrollbar-width: auto; scrollbar-color: auto; }
  .workspace-tabs::-webkit-scrollbar { height: 4px; }
  .workspace-tabs::-webkit-scrollbar-track { background: transparent; }
  .workspace-tabs::-webkit-scrollbar-thumb { background: #d4d8df; border-radius: 999px; }
  .workspace-tabs::-webkit-scrollbar-thumb:hover { background: #b6bbc4; }
}
.workspace-tab { flex: 0 0 auto; display: flex; align-items: center; border-radius: 6px; background: #f5f7fa; border: 1px solid transparent; }
.workspace-tab.is-active { background: #ecf5ff; border-color: #b3d8ff; color: #409eff; }
.workspace-tab button { border: 0; background: none; color: inherit; cursor: pointer; font: inherit; font-size: 13px; }
.workspace-tab [role="tab"] { padding: 9px 12px; max-width: 200px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.workspace-tab .close-tab { display: flex; align-items: center; padding: 7px; margin-right: 3px; border-radius: 4px; }
.close-tab:hover { background: #dceafb; }
.workspace-tab button:focus-visible { outline: 2px solid #409eff; outline-offset: -2px; }
@media (max-width: 760px) { .workspace-tabs { margin-left: 2px; } .workspace-tab [role="tab"] { max-width: 130px; } }
</style>
