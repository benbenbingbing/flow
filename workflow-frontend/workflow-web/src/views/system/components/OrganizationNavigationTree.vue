<template>
  <div class="organization-navigation-tree">
    <ManagementNavigationTree
      ref="treeRef"
      v-bind="$attrs"
      :page-loader="loadPage"
      :children-loader="loadChildren"
      :path-loader="getOrganizationAncestors"
      :node-selectable="nodeSelectable"
      label-key="orgName"
      root-label="全部组织"
      search-placeholder="搜索组织名称或编码"
      empty-description="暂无匹配组织部门"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { getOrganizationAncestors, getOrganizationChildren, getOrganizationPage, getOrganizationSelectionPage } from '@/api/system/org'
import ManagementNavigationTree from './ManagementNavigationTree.vue'

defineOptions({ inheritAttrs: false })
const props = defineProps({ selection: Boolean, selectableType: { type: String, default: '' } })
const treeRef = ref(null)
// 选择器浏览保留所有类型的祖先；类型只限制可选资格和搜索结果。
const loadChildren = params => getOrganizationChildren({ parentId: params.parentId,
  type: props.selection ? undefined : params.type, excludeId: params.excludeId })
const loadPage = params => props.selection
  ? getOrganizationSelectionPage({ ...params, type: props.selectableType || undefined })
  : getOrganizationPage(params)
// 选择器保留可展开的祖先，管理树仍允许维护停用节点。
const nodeSelectable = node => !props.selection
  || (node.status === '0' && (!props.selectableType || node.type === props.selectableType))
// 组织页和上级选择器共用导航行为，保留页面通过 ref 刷新、定位的既有入口。
defineExpose({
  reload: () => treeRef.value?.reload(),
  reveal: id => treeRef.value?.reveal(id)
})
</script>
