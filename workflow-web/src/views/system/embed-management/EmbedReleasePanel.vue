<template>
  <div class="release-panel">
    <div class="panel-toolbar">
      <div>
        <strong>不可变 Release 历史</strong>
        <span class="toolbar-note">每次发布都生成独立快照和 SHA-256 摘要</span>
      </div>
      <el-button :loading="loading" @click="loadReleases">刷新</el-button>
    </div>
    <el-alert
      v-if="loadError"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
      class="panel-alert"
    />
    <el-table v-loading="loading" :data="releases" border>
      <el-table-column prop="revision" width="110">
        <template #header>
          <ConfigHelpLabel label="Revision" help-key="embed.release.revision" />
        </template>
        <template #default="{ row }">r{{ row.revision }}</template>
      </el-table-column>
      <el-table-column prop="releaseId" min-width="180">
        <template #header>
          <ConfigHelpLabel label="Release ID" help-key="embed.release.id" />
        </template>
      </el-table-column>
      <el-table-column prop="entityCode" label="Entity" min-width="120" />
      <el-table-column prop="listKey" label="List Key" min-width="120" />
      <el-table-column prop="defaultFormId" label="Form ID" min-width="120" />
      <el-table-column min-width="210">
        <template #header>
          <ConfigHelpLabel label="Config Hash" help-key="embed.release.configHash" />
        </template>
        <template #default="{ row }">
          <span class="hash-value" :title="row.configHash">
            {{ shortHash(row.configHash) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="publishedBy" label="发布人" min-width="120" />
      <el-table-column label="发布时间" min-width="170">
        <template #default="{ row }">{{ formatTime(row.publishedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="90">
        <template #default="{ row }">
          <el-button link type="primary" @click="inspectRelease(row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer
      v-model="detailVisible"
      title="Release 快照"
      size="min(720px, 96vw)"
      append-to-body
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <el-descriptions v-if="selected" :column="1" border size="small">
          <el-descriptions-item label="Release">
            r{{ selected.revision }} / {{ selected.releaseId }}
          </el-descriptions-item>
          <el-descriptions-item label="Config Hash">
            <span class="hash-value">{{ selected.configHash }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="发布说明">
            {{ selected.releaseNote || '-' }}
          </el-descriptions-item>
        </el-descriptions>
        <el-input
          v-if="selected"
          :model-value="JSON.stringify(selected.config || {}, null, 2)"
          type="textarea"
          :rows="24"
          readonly
          class="json-viewer"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import dayjs from 'dayjs'
import { embedManagementApi } from '@/api/system/embedManagement'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import { describeEmbedManagementError } from './embedManagementModel'

const props = defineProps({
  view: { type: Object, required: true }
})

const releases = ref([])
const loading = ref(false)
const loadError = ref('')
const detailVisible = ref(false)
const detailLoading = ref(false)
const selected = ref(null)

watch(() => props.view.id, loadReleases, { immediate: true })

async function loadReleases() {
  loading.value = true
  loadError.value = ''
  try {
    releases.value = await embedManagementApi.views.releases(props.view.id) || []
  } catch (error) {
    loadError.value = describeEmbedManagementError(error)
  } finally {
    loading.value = false
  }
}

async function inspectRelease(row) {
  detailVisible.value = true
  detailLoading.value = true
  selected.value = null
  try {
    selected.value = await embedManagementApi.views.release(
      props.view.id,
      row.revision
    )
  } catch (error) {
    selected.value = {
      ...row,
      config: { error: describeEmbedManagementError(error) }
    }
  } finally {
    detailLoading.value = false
  }
}

function shortHash(value) {
  return value ? `${value.slice(0, 16)}…${value.slice(-8)}` : '-'
}

function formatTime(value) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'
}
</script>

<style scoped>
.panel-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.toolbar-note {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}

.panel-alert {
  margin-bottom: 12px;
}

.hash-value,
.json-viewer :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.hash-value {
  overflow-wrap: anywhere;
  font-size: 12px;
}

.json-viewer {
  margin-top: 14px;
}

.json-viewer :deep(textarea) {
  font-size: 12px;
  line-height: 1.5;
}
</style>
