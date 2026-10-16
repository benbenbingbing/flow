<template>
  <router-view v-slot="{ Component, route }">
    <keep-alive :key="workspace.generation" :include="cacheNames">
      <component v-if="Component && activeTab" :is="pageType(activeTab)" :key="activeTab.key" :view="Component" :route="route" />
    </keep-alive>
  </router-view>
</template>

<script setup>
import { cloneVNode, computed, defineComponent, h, markRaw, nextTick, onActivated, provide, ref, shallowReactive, watch } from 'vue'
import { routeLocationKey } from 'vue-router'
import { useWorkspaceTabsStore } from '@/stores/workspaceTabs'
import { snapshotWorkspaceRoute, workspacePageKey } from '@/shared/workspace-tabs'

const workspace = useWorkspaceTabsStore()
const activeTab = computed(() => workspace.tabs.find(tab => tab.key === workspace.activeKey))
const cacheNames = computed(() => workspace.tabs.map(tab => tab.cacheName))
const pageTypes = new Map()

/** 不同标签使用不同具名缓存容器，关闭一个标签时只释放它的实例。 */
function pageType(tab) {
  if (pageTypes.has(tab.cacheName)) return pageTypes.get(tab.cacheName)
  const type = markRaw(defineComponent({
    name: tab.cacheName,
    props: ['view', 'route'],
    setup(props) {
      const element = ref(null)
      const ownRoute = shallowReactive(snapshotWorkspaceRoute(props.route))
      const active = computed(() => workspace.activeKey === tab.key)
      // useRoute 在此子树内读取独立快照；仅当前标签收到的页内参数变更才更新它。
      provide(routeLocationKey, ownRoute)
      provide(workspacePageKey, { key: tab.key, active, registerGuard: guard => workspace.registerGuard(tab.key, guard) })
      watch(() => props.route, route => Object.assign(ownRoute, snapshotWorkspaceRoute(route)))
      onActivated(async () => {
        await nextTick()
        if (element.value) { element.value.scrollTop = tab.scrollTop; element.value.scrollLeft = tab.scrollLeft }
      })
      return () => h('div', {
        ref: element, class: 'workspace-page', role: 'tabpanel', 'aria-label': tab.title,
        onScroll: event => {
          if (active.value) { tab.scrollTop = event.target.scrollTop; tab.scrollLeft = event.target.scrollLeft }
        }
      }, [cloneVNode(props.view)])
    }
  }))
  pageTypes.set(tab.cacheName, type)
  return type
}

// 页面关闭后同时释放工厂引用，避免反复开关标签积累组件定义。
watch(cacheNames, names => {
  for (const name of pageTypes.keys()) if (!names.includes(name)) pageTypes.delete(name)
})
</script>

<style>
.workspace-page { width: 100%; height: 100%; min-width: 0; overflow: auto; }
</style>
