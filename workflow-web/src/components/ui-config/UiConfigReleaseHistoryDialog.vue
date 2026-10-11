<template>
  <el-dialog
    v-model="visible"
    :title="`${configLabel}发布版本`"
    width="1200px"
    class="ui-config-release-history-dialog"
    append-to-body
  >
    <el-table
      v-loading="loading"
      :data="releases"
      size="small"
      class="release-history-table"
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <el-descriptions :column="2" border size="small" class="technical-details">
            <el-descriptions-item label="内容校验值">
              {{ row.contentHash || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="70" />
      <el-table-column prop="releaseMode" label="方式" width="90">
        <template #default="{ row }">
          {{ row.releaseMode === 'HOTFIX' ? '已退役' : '普通发布' }}
        </template>
      </el-table-column>
      <el-table-column prop="riskLevel" label="风险" width="80" />
      <el-table-column prop="description" label="版本说明" min-width="150">
        <template #default="{ row }">{{ row.description || '未填写' }}</template>
      </el-table-column>
      <el-table-column label="发布人" min-width="160">
        <template #default="{ row }">{{ formatPublisher(row) }}</template>
      </el-table-column>
      <el-table-column label="发布时间" min-width="160">
        <template #default="{ row }">{{ formatPublishedAt(row.publishedAt) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80" />
      <el-table-column label="操作" min-width="180">
        <template #default="{ row }">
          <el-button
            v-if="row.releaseMode !== 'HOTFIX'"
            link
            type="primary"
            :disabled="row.status === 'ACTIVE'"
            @click="activate(row)"
          >
            激活
          </el-button>
          <el-button
            link
            type="warning"
            @click="restoreDraft(row)"
          >
            恢复为草稿
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-if="total > pageSize"
      v-model:current-page="pageNum"
      :page-size="pageSize"
      :total="total"
      layout="prev, pager, next, total"
      class="history-pagination"
      @current-change="load"
    />
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import { entityListConfigApi } from '@/api/entityListConfig'

const props = defineProps({
  configId: { type: [String, Number], default: '' },
  configLabel: { type: String, default: '配置' }
})

const emit = defineEmits(['changed'])
const visible = ref(false)
const loading = ref(false)
const releases = ref([])
const pageNum = ref(1)
const pageSize = 20
const total = ref(0)
/**
 * 发布记录保存的是人员 ID；摘要接口补齐姓名和登录名后统一按人员可读格式展示。
 * 保留 ID 回退，避免历史用户被删除时整列空白。
 */
function formatPublisher(release) {
  const name = String(release?.publishedByName || '').trim()
  const username = String(release?.publishedByUsername || '').trim()
  if (name && username) return `${name}（${username}）`
  return name || username || release?.publishedBy || '-'
}

/** 将后端 LocalDateTime/ISO 时间统一展示到秒。 */
function formatPublishedAt(value) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'
}

/** 只读取仍保留版本管理的列表；表单始终使用当前配置，不进入历史窗口。 */
async function load() {
  const page = await entityListConfigApi.getReleaseSummaries(
    props.configId,
    { pageNum: pageNum.value, pageSize }
  )
  releases.value = page?.records || []
  total.value = Number(page?.total || 0)
}

async function open() {
  if (!props.configId) {
    ElMessage.warning(`请先保存${props.configLabel}草稿`)
    return
  }
  visible.value = true
  pageNum.value = 1
  loading.value = true
  try {
    await load()
  } finally {
    loading.value = false
  }
}

async function activate(release) {
  const preview = await entityListConfigApi.previewActivation(
    props.configId,
    release.id
  )
  const { value } = await ElMessageBox.prompt(
    activationDescription(release, preview),
    '回滚发布版本',
    {
      type: 'warning',
      inputPlaceholder: '请输入激活原因',
      inputValidator: text => Boolean(String(text || '').trim())
        || '激活原因不能为空'
    }
  )
  await entityListConfigApi.activateRelease(
    props.configId,
    release.id,
    value,
    preview?.currentReleaseId
  )
  await load()
  emit('changed', { action: 'ACTIVATE', release })
  ElMessage.success('历史版本已激活')
}

async function restoreDraft(release) {
  const { value } = await ElMessageBox.prompt(
    `将 v${release.version} 的配置复制到当前草稿，不会切换线上生效版本。`,
    '恢复列表草稿',
    {
      type: 'warning',
      inputPlaceholder: '请输入恢复原因',
      inputValidator: text => Boolean(String(text || '').trim())
        || '恢复原因不能为空'
    }
  )
  await entityListConfigApi.restoreDraft(
    props.configId,
    release.id,
    value
  )
  await load()
  emit('changed', { action: 'RESTORE_DRAFT', release })
  ElMessage.success('历史版本已恢复为当前草稿，线上版本未改变')
}

function activationDescription(release, preview) {
  const count = preview?.changedItems?.length || 0
  const sections = (preview?.changedSections || [])
    .filter(section => !['schemaVersion', 'configType'].includes(section))
    .join('、')
  return `确认将运行时切换到 v${release.version}？`
    + `预计变更 ${count} 项`
    + (preview?.riskLevel ? `，风险 ${preview.riskLevel}` : '')
    + (sections ? `，涉及 ${sections}` : '')
    + '。当前草稿不会被覆盖。'
}

defineExpose({ open })
</script>

<style scoped>
:global(.ui-config-release-history-dialog) {
  max-width: calc(100vw - 32px);
}

.technical-details {
  margin: 8px 16px;
}

.release-history-table :deep(.cell) {
  white-space: normal;
  overflow-wrap: anywhere;
}

.history-pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

</style>
