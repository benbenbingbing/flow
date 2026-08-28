<template>
  <div class="embed-management-page">
    <div class="page-toolbar">
      <div>
        <h2>嵌入集成</h2>
        <p>
          管理可发布的嵌入视图、应用授权与外部用户映射
        </p>
      </div>
      <el-tag effect="plain" type="info">Embed Management V1</el-tag>
    </div>

    <el-card v-if="availableTabs.length" shadow="never" class="workspace-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane
          v-if="canView"
          label="View 与 Grant"
          name="views"
        >
          <EmbedViewWorkspace />
        </el-tab-pane>
        <el-tab-pane
          v-if="canManageIdentity"
          label="Identity Provider 与 Binding"
          name="identity"
        >
          <EmbedIdentityWorkspace />
        </el-tab-pane>
        <el-tab-pane
          v-if="canView"
          label="Launch / Session 运维"
          name="operations"
        >
          <EmbedOperationsWorkspace />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <PageState
      v-else
      type="empty"
      title="暂无可用功能"
      description="当前账号没有 Embed 管理权限。"
    />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import PageState from '@/components/PageState.vue'
import { useUserStore } from '@/stores/user'
import EmbedIdentityWorkspace from './embed-management/EmbedIdentityWorkspace.vue'
import EmbedOperationsWorkspace from './embed-management/EmbedOperationsWorkspace.vue'
import EmbedViewWorkspace from './embed-management/EmbedViewWorkspace.vue'
import {
  EMBED_PERMISSIONS,
  hasEmbedPermission
} from './embed-management/embedManagementModel'

const userStore = useUserStore()
const activeTab = ref('views')

const canView = computed(() => permission(EMBED_PERMISSIONS.view))
const canManageIdentity = computed(() =>
  permission(EMBED_PERMISSIONS.identityManage)
)
const availableTabs = computed(() => [
  canView.value ? 'views' : '',
  canManageIdentity.value ? 'identity' : '',
  canView.value ? 'operations' : ''
].filter(Boolean))

watch(availableTabs, values => {
  if (!values.includes(activeTab.value)) {
    activeTab.value = values[0] || ''
  }
}, { immediate: true })

function permission(value) {
  return hasEmbedPermission(
    userStore.permissions,
    value,
    userStore.isSuperAdmin
  )
}
</script>

<style scoped>
.embed-management-page {
  min-width: 0;
}

.page-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.page-toolbar h2 {
  margin: 0 0 4px;
  color: #303133;
}

.page-toolbar p {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.workspace-card {
  min-height: calc(100vh - 126px);
}

:deep(.workspace-card > .el-card__body) {
  padding: 0 18px 18px;
}
</style>
