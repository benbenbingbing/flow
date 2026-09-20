import { computed, inject, onActivated, onDeactivated, onBeforeUnmount, provide, shallowRef } from 'vue'

const breadcrumbParentsKey = Symbol('breadcrumbParents')

/** 布局提供页面级父导航入口，返回随页面数据更新的父级条目。 */
export function provideBreadcrumbParents() {
  const source = shallowRef(null)
  provide(breadcrumbParentsKey, source)
  return computed(() => source.value?.() || [])
}

/**
 * 页面在 setup 中注册父级条目 getter；异步加载实体后自动更新返回链接。
 * 离开页面时清理，避免下一个页面继承旧层级；独立渲染时无需布局支持。
 */
export function useBreadcrumbParents(getParents) {
  const source = inject(breadcrumbParentsKey, null)
  if (!source) return

  source.value = getParents
  const release = () => {
    // 新页面可能已注册自己的导航，只清理当前页面持有的条目。
    if (source.value === getParents) source.value = null
  }
  onActivated(() => { source.value = getParents })
  onDeactivated(release)
  onBeforeUnmount(release)
}
