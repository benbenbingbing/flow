<template>
  <VanTabs v-if="tabbed" :active="activeTab" :lazy-render="false" shrink @update:active="$emit('update:activeTab', $event)">
    <VanTab v-for="page in pages" :key="page.name" :name="page.name" :title="page.label">
      <div class="mobile-form-tab-content"><slot :page="page" /></div>
    </VanTab>
    <slot name="after-tabs" />
  </VanTabs>
  <slot v-else :page="pages[0]" />
</template>
<script setup>
import { Tabs as VanTabs, Tab as VanTab } from 'vant'
// 页签只负责布局；全部内容保持挂载，表单数据、联动和校验仍由唯一的父渲染器管理。
defineProps({ tabbed: Boolean, activeTab: String, pages: { type: Array, required: true } })
defineEmits(['update:activeTab'])
</script>
<style scoped>
.mobile-form-tab-content { padding: 18px 18px 24px; }
</style>
