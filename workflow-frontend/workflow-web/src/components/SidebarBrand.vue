<template>
  <div class="sidebar-brand" :class="{ 'is-collapsed': collapsed }" :title="brand.title" :aria-label="brand.title">
    <img v-if="brand.imageBase64 && !imageFailed" class="brand-image" :src="brand.imageBase64" alt="" @error="imageFailed = true" />
    <el-icon v-else :size="24"><component :is="icon" /></el-icon>
    <span v-if="!collapsed">{{ brand.title }}</span>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { normalizeSidebarBranding } from '@/shared/sidebar-branding'
import { resolveMenuIcon } from '@/utils/menuIcons'

const props = defineProps({ branding: Object, collapsed: Boolean })
const brand = computed(() => normalizeSidebarBranding(props.branding))
const icon = computed(() => resolveMenuIcon(brand.value.icon))
const imageFailed = ref(false)
// 更换图片后重新尝试加载；损坏图片仅回退图标，不清除已保存的系统配置。
watch(() => brand.value.imageBase64, () => { imageFailed.value = false })
</script>

<style scoped>
.sidebar-brand {
  box-sizing: border-box;
  height: 60px;
  display: flex;
  align-items: center;
  /* 与一级菜单共用 20px 缩进、24px 图标槽和 5px 间距，调宽侧栏时保持对齐。 */
  justify-content: flex-start;
  padding: 0 20px;
  gap: 5px;
  color: #fff;
  background-color: #304156;
  font-size: 16px;
  font-weight: bold;
  border-bottom: 1px solid #1f2d3d;
  overflow: hidden;
}
.sidebar-brand .el-icon { flex: 0 0 24px; }
.brand-image { flex: 0 0 24px; width: 24px; height: 24px; object-fit: contain; }
.sidebar-brand span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sidebar-brand.is-collapsed { justify-content: center; padding: 0; gap: 0; }
</style>
