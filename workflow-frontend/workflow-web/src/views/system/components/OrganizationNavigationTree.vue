<template>
  <div class="organization-navigation-tree">
    <ManagementNavigationTree
      ref="treeRef"
      v-bind="$attrs"
      :page-loader="getOrganizationPage"
      :path-loader="getOrganizationAncestors"
      label-key="orgName"
      root-label="全部组织"
      search-placeholder="搜索组织名称或编码"
      empty-description="暂无匹配组织部门"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { getOrganizationAncestors, getOrganizationPage } from '@/api/system/org'
import ManagementNavigationTree from './ManagementNavigationTree.vue'

defineOptions({ inheritAttrs: false })
const treeRef = ref(null)
// 组织页和上级选择器共用导航行为，保留页面通过 ref 刷新、定位的既有入口。
defineExpose({
  reload: () => treeRef.value?.reload(),
  reveal: id => treeRef.value?.reveal(id)
})
</script>
