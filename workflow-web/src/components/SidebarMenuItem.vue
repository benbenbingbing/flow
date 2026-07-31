<template>
  <el-sub-menu v-if="hasChildren" :index="menu.path || menu.id">
    <template #title>
      <el-icon v-if="menuIcon"><component :is="menuIcon" /></el-icon>
      <span>{{ menu.menuName }}</span>
    </template>
    <sidebar-menu-item
      v-for="child in menu.children"
      :key="child.id"
      :menu="child"
    />
  </el-sub-menu>
  <el-menu-item v-else :index="menu.path">
    <el-icon v-if="menuIcon"><component :is="menuIcon" /></el-icon>
    <span>{{ menu.menuName }}</span>
  </el-menu-item>
</template>

<script setup>
import { computed } from 'vue'
import { resolveMenuIcon } from '@/utils/menuIcons'

const props = defineProps({
  menu: {
    type: Object,
    required: true
  }
})

const hasChildren = computed(() => {
  return props.menu.children && Array.isArray(props.menu.children) && props.menu.children.length > 0
})

const menuIcon = computed(() => {
  if (!props.menu.icon) return null
  return resolveMenuIcon(props.menu.icon, props.menu.menuType === 'M' ? 'FolderOpened' : 'Menu')
})
</script>
