<template>
  <el-dialog
    v-model="visible"
    :title="`${configLabel}发布版本`"
    width="920px"
    append-to-body
  >
    <el-table v-loading="loading" :data="releases" size="small">
      <el-table-column type="expand">
        <template #default="{ row }">
          <el-descriptions :column="2" border size="small" class="technical-details">
            <el-descriptions-item label="内容校验值">
              {{ row.contentHash || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="热修复状态">
              {{ row.rolloutStatus || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="80" />
      <el-table-column prop="releaseMode" label="方式" width="100">
        <template #default="{ row }">
          {{ row.releaseMode === 'HOTFIX' ? '兼容热修复' : '普通发布' }}
        </template>
      </el-table-column>
      <el-table-column prop="riskLevel" label="风险" width="90" />
      <el-table-column prop="description" label="版本说明" min-width="180">
        <template #default="{ row }">{{ row.description || '未填写' }}</template>
      </el-table-column>
      <el-table-column prop="publishedBy" label="发布人" width="120" />
      <el-table-column prop="publishedAt" label="发布时间" width="180" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column label="操作" width="230">
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
            v-else
            link
            type="danger"
            :disabled="row.rolloutStatus !== 'ACTIVE'"
            @click="rollback(row)"
          >
            撤回热修复
          </el-button>
          <el-button
            v-if="configType === 'LIST'"
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
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  activateFormRelease,
  getFormReleaseSummaries,
  previewFormActivation,
  rollbackFormHotfix
} from '@/api/entityForm'
import { entityListConfigApi } from '@/api/entityListConfig'

const props = defineProps({
  configType: { type: String, required: true },
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

async function load() {
  const page = props.configType === 'FORM'
    ? await getFormReleaseSummaries(String(props.configId), {
        pageNum: pageNum.value,
        pageSize
      })
    : await entityListConfigApi.getReleaseSummaries(
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
  const preview = props.configType === 'FORM'
    ? await previewFormActivation(String(props.configId), release.id)
    : await entityListConfigApi.previewActivation(
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
  if (props.configType === 'FORM') {
    await activateFormRelease(
      String(props.configId),
      release.id,
      value,
      preview?.currentReleaseId
    )
  } else {
    await entityListConfigApi.activateRelease(
      props.configId,
      release.id,
      value,
      preview?.currentReleaseId
    )
  }
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

async function rollback(release) {
  const { value } = await ElMessageBox.prompt(
    `确认按发布顺序撤回热修复 v${release.version}？`,
    '撤回兼容热修复',
    {
      type: 'warning',
      inputPlaceholder: '请输入撤回原因',
      inputValidator: text => Boolean(String(text || '').trim())
        || '撤回原因不能为空'
    }
  )
  if (props.configType === 'FORM') {
    await rollbackFormHotfix(String(props.configId), release.id, value)
  } else {
    await entityListConfigApi.rollbackHotfix(props.configId, release.id, value)
  }
  await load()
  emit('changed', { action: 'ROLLBACK_HOTFIX', release })
  ElMessage.success(`${props.configLabel}热修复已撤回`)
}

defineExpose({ open })
</script>

<style scoped>
.technical-details {
  margin: 8px 16px;
}

.history-pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
