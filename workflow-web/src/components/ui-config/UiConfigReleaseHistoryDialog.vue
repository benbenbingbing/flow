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
      <el-table-column label="操作" width="150">
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
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  activateFormRelease,
  getFormReleases,
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

async function load() {
  releases.value = props.configType === 'FORM'
    ? await getFormReleases(String(props.configId))
    : await entityListConfigApi.getReleases(props.configId)
}

async function open() {
  if (!props.configId) {
    ElMessage.warning(`请先保存${props.configLabel}草稿`)
    return
  }
  visible.value = true
  loading.value = true
  try {
    await load()
  } finally {
    loading.value = false
  }
}

async function activate(release) {
  await ElMessageBox.confirm(
    `确认激活历史版本 v${release.version}？`,
    '回滚发布版本',
    { type: 'warning' }
  )
  if (props.configType === 'FORM') {
    await activateFormRelease(String(props.configId), release.id)
  } else {
    await entityListConfigApi.activateRelease(props.configId, release.id)
  }
  await load()
  emit('changed', { action: 'ACTIVATE', release })
  ElMessage.success('历史版本已激活')
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
</style>
