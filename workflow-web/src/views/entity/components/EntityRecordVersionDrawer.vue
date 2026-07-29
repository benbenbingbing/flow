<template>
  <el-drawer
    v-model="visible"
    size="82%"
    destroy-on-close
    class="record-version-drawer"
  >
    <template #header>
      <div class="drawer-heading">
        <div>
          <h3>{{ recordTitle }} · 数据版本</h3>
          <span>{{ entityCode }} / {{ record?.id || '-' }}</span>
        </div>
        <el-button :loading="loading" title="刷新版本" @click="loadVersions">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
    </template>

    <div v-loading="loading" class="version-body">
      <el-empty
        v-if="!loading && versions.length === 0"
        description="当前数据还没有正式版本"
      />

      <div v-else class="version-layout">
        <aside class="timeline-pane">
          <div class="pane-heading">
            <strong>版本时间线</strong>
            <span>共 {{ versions.length }} 个版本</span>
          </div>
          <el-timeline>
            <el-timeline-item
              v-for="item in versions"
              :key="item.id"
              :timestamp="formatTime(item.createTime)"
              placement="top"
              :type="item.hasFieldChanges ? 'primary' : 'info'"
            >
              <div class="version-item">
                <div class="version-title">
                  <strong>V{{ item.versionNo }} {{ item.scenarioName || item.versionTitle }}</strong>
                  <el-tag
                    v-if="!item.hasFieldChanges"
                    type="info"
                    effect="plain"
                    size="small"
                  >
                    无字段变化
                  </el-tag>
                </div>
                <div class="version-meta">
                  <span>{{ item.businessIntentName || operationText(item.operationType) }}</span>
                  <span>{{ sourceText(item.sourceType) }}</span>
                  <span>{{ item.operatorName || item.operatorId || '系统' }}</span>
                </div>
                <div v-if="item.processInstanceId || item.sourceRecordId" class="version-links">
                  <span v-if="item.processInstanceId">流程 {{ item.processInstanceId }}</span>
                  <span v-if="item.sourceRecordId">
                    来源 {{ item.sourceEntityCode || '' }} / {{ item.sourceRecordId }}
                  </span>
                </div>
                <el-button link type="primary" @click="openSnapshot(item)">查看快照</el-button>
              </div>
            </el-timeline-item>
          </el-timeline>
        </aside>

        <section class="compare-pane">
          <div class="pane-heading compare-heading">
            <strong>版本比较</strong>
            <el-checkbox v-model="changedOnly">仅显示变化</el-checkbox>
          </div>
          <div class="compare-toolbar">
            <el-select v-model="fromVersion" placeholder="起始版本">
              <el-option
                v-for="item in versionOptions"
                :key="item.versionNo"
                :label="versionOptionLabel(item)"
                :value="item.versionNo"
              />
            </el-select>
            <span>至</span>
            <el-select v-model="toVersion" placeholder="目标版本">
              <el-option
                v-for="item in versionOptions"
                :key="item.versionNo"
                :label="versionOptionLabel(item)"
                :value="item.versionNo"
              />
            </el-select>
            <el-button
              type="primary"
              :loading="compareLoading"
              :disabled="fromVersion == null || toVersion == null"
              @click="loadComparison"
            >
              比较
            </el-button>
          </div>

          <template v-if="comparison">
            <el-alert
              :title="comparison.message"
              :type="comparison.hasChanges ? 'success' : 'info'"
              show-icon
              :closable="false"
            />
            <el-collapse v-model="activeGroups" class="compare-groups">
              <el-collapse-item
                v-for="group in visibleCompareGroups"
                :key="group.code"
                :name="group.code"
              >
                <template #title>
                  <span class="group-title">
                    {{ group.name }}
                    <el-tag size="small" effect="plain">{{ group.fields.length }} 项</el-tag>
                  </span>
                </template>
                <el-table :data="group.fields" border size="small">
                  <el-table-column label="字段" min-width="150">
                    <template #default="{ row }">
                      <div class="field-name">{{ row.fieldName || row.fieldCode }}</div>
                      <div class="field-code">{{ row.fieldCode }}</div>
                    </template>
                  </el-table-column>
                  <el-table-column label="原值" min-width="190">
                    <template #default="{ row }">
                      <span class="value-text">{{ displayValue(row.oldDisplayValue ?? row.oldValue) }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="新值" min-width="190">
                    <template #default="{ row }">
                      <span class="value-text">{{ displayValue(row.newDisplayValue ?? row.newValue) }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="变化" width="90" align="center">
                    <template #default="{ row }">
                      <el-tag :type="changeTypeTag(row.changeType)" effect="plain">
                        {{ changeTypeText(row.changeType) }}
                      </el-tag>
                    </template>
                  </el-table-column>
                </el-table>
              </el-collapse-item>
            </el-collapse>
            <el-empty
              v-if="visibleCompareGroups.length === 0"
              description="筛选条件下没有字段变化"
            />
          </template>
          <el-empty v-else description="选择两个版本开始比较" />
        </section>
      </div>
    </div>

    <el-dialog
      v-model="snapshotVisible"
      append-to-body
      width="900px"
      :title="snapshotTitle"
    >
      <div v-loading="snapshotLoading">
        <el-descriptions v-if="selectedDetail" :column="3" border>
          <el-descriptions-item label="版本">V{{ selectedDetail.versionNo }}</el-descriptions-item>
          <el-descriptions-item label="场景">{{ selectedDetail.scenarioName }}</el-descriptions-item>
          <el-descriptions-item label="记录时间">{{ formatTime(selectedDetail.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="业务意图">
            {{ selectedDetail.businessIntentName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="操作人">
            {{ selectedDetail.operatorName || selectedDetail.operatorId || '系统' }}
          </el-descriptions-item>
          <el-descriptions-item label="来源">
            {{ sourceText(selectedDetail.sourceType) }}
          </el-descriptions-item>
        </el-descriptions>
        <el-collapse v-model="snapshotGroupsOpen" class="snapshot-groups">
          <el-collapse-item
            v-for="group in snapshotGroups"
            :key="group.code"
            :name="group.code"
            :title="`${group.name}（${group.fields.length}）`"
          >
            <el-table :data="group.fields" border size="small">
              <el-table-column label="字段" min-width="180">
                <template #default="{ row }">
                  <div class="field-name">{{ row.fieldName || row.fieldCode }}</div>
                  <div class="field-code">{{ row.fieldCode }}</div>
                </template>
              </el-table-column>
              <el-table-column label="历史值" min-width="360">
                <template #default="{ row }">
                  <span class="value-text">{{ displayValue(row.displayValue ?? row.value) }}</span>
                </template>
              </el-table-column>
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </div>
    </el-dialog>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { entityVersionApi } from '@/api/entityVersion'

const props = defineProps<{
  entityCode: string
}>()

const visible = ref(false)
const loading = ref(false)
const compareLoading = ref(false)
const snapshotLoading = ref(false)
const snapshotVisible = ref(false)
const record = ref<any>(null)
const versions = ref<any[]>([])
const fromVersion = ref<number | null>(null)
const toVersion = ref<number | null>(null)
const comparison = ref<any>(null)
const changedOnly = ref(true)
const activeGroups = ref<string[]>(['BUSINESS', 'SYSTEM', 'SUBFORM', 'RELATION'])
const selectedDetail = ref<any>(null)
const snapshotGroupsOpen = ref<string[]>(['BUSINESS'])

const recordTitle = computed(() =>
  record.value?.name
  || record.value?.title
  || record.value?.code
  || record.value?.dataNo
  || record.value?.id
  || '业务数据')

const versionOptions = computed(() =>
  [...versions.value].sort((a, b) => Number(a.versionNo) - Number(b.versionNo)))

const visibleCompareGroups = computed(() =>
  (comparison.value?.groups || [])
    .map((group: any) => ({
      ...group,
      fields: changedOnly.value
        ? (group.fields || []).filter((field: any) => field.changeType !== 'UNCHANGED')
        : (group.fields || [])
    }))
    .filter((group: any) => group.fields.length > 0))

const snapshotGroups = computed(() => {
  const fields = selectedDetail.value?.snapshot?.fields || []
  const groups = [
    ['BUSINESS', '业务字段'],
    ['SYSTEM', '系统字段'],
    ['SUBFORM', '子表单'],
    ['RELATION', '关系数据']
  ]
  return groups
    .map(([code, name]) => ({
      code,
      name,
      fields: fields.filter((field: any) => field.group === code)
    }))
    .filter(group => group.fields.length > 0)
})

const snapshotTitle = computed(() =>
  selectedDetail.value
    ? `V${selectedDetail.value.versionNo} ${selectedDetail.value.scenarioName || '版本快照'}`
    : '版本快照')

async function open(rowValue: any) {
  record.value = rowValue
  visible.value = true
  comparison.value = null
  await loadVersions()
}

async function loadVersions() {
  if (!props.entityCode || !record.value?.id) return
  loading.value = true
  try {
    const data = await entityVersionApi.recordVersions(props.entityCode, record.value.id)
    versions.value = [...(data || [])].sort(
      (a, b) => Number(b.versionNo) - Number(a.versionNo))
    const ordered = versionOptions.value
    fromVersion.value = ordered[0]?.versionNo ?? null
    toVersion.value = ordered.at(-1)?.versionNo ?? null
    comparison.value = null
    if (ordered.length >= 2) {
      await loadComparison()
    }
  } catch (error: any) {
    ElMessage.error(error.message || '加载数据版本失败')
  } finally {
    loading.value = false
  }
}

async function loadComparison() {
  if (fromVersion.value == null || toVersion.value == null || !record.value?.id) return
  compareLoading.value = true
  try {
    comparison.value = await entityVersionApi.compareRecordVersions(
      props.entityCode,
      record.value.id,
      fromVersion.value,
      toVersion.value
    )
  } catch (error: any) {
    ElMessage.error(error.message || '版本比较失败')
  } finally {
    compareLoading.value = false
  }
}

async function openSnapshot(item: any) {
  snapshotVisible.value = true
  snapshotLoading.value = true
  selectedDetail.value = null
  try {
    selectedDetail.value = await entityVersionApi.recordVersion(
      props.entityCode,
      record.value.id,
      item.versionNo
    )
    snapshotGroupsOpen.value = snapshotGroups.value.map(group => group.code)
  } catch (error: any) {
    ElMessage.error(error.message || '加载版本快照失败')
  } finally {
    snapshotLoading.value = false
  }
}

function versionOptionLabel(item: any) {
  return `V${item.versionNo} ${item.scenarioName || item.versionTitle || ''}`.trim()
}

function displayValue(value: any) {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  if (Array.isArray(value)) {
    return value.map(displayValue).join('、')
  }
  return JSON.stringify(value, null, 2)
}

function formatTime(value: any) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

function sourceText(value: string) {
  return {
    FORM: '表单',
    LIST: '列表',
    APPROVAL_TASK: '审批',
    PROCESS_RUNTIME: '流程运行态',
    FLOW_ACTION: '流程动作',
    CUSTOM_INTERFACE: '自定义接口',
    BATCH: '批量',
    IMPORT: '导入',
    SCHEDULED_JOB: '定时任务',
    MESSAGE_CONSUMER: '消息消费',
    SYSTEM_TASK: '系统任务'
  }[value] || value || '-'
}

function operationText(value: string) {
  return {
    CREATE: '新增',
    UPDATE: '修改',
    DELETE: '删除',
    STATUS_CHANGE: '状态变化',
    APPLY_CHANGE: '变更生效',
    UPSERT: '新增或修改'
  }[value] || value || '-'
}

function changeTypeText(value: string) {
  return {
    ADDED: '新增',
    REMOVED: '删除',
    MODIFIED: '修改',
    UNCHANGED: '未变化'
  }[value] || value
}

function changeTypeTag(value: string) {
  return {
    ADDED: 'success',
    REMOVED: 'danger',
    MODIFIED: 'warning',
    UNCHANGED: 'info'
  }[value] || 'info'
}

defineExpose({ open })
</script>

<style scoped lang="scss">
.drawer-heading,
.pane-heading,
.version-title,
.version-meta,
.version-links,
.compare-toolbar,
.group-title {
  display: flex;
  align-items: center;
}

.drawer-heading,
.pane-heading {
  justify-content: space-between;
}

.drawer-heading {
  width: 100%;
  padding-right: 18px;

  h3 {
    margin: 0 0 4px;
  }

  span {
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }
}

.version-body {
  min-height: 360px;
}

.version-layout {
  display: grid;
  grid-template-columns: minmax(280px, 34%) minmax(0, 1fr);
  min-height: calc(100vh - 150px);
}

.timeline-pane {
  padding: 0 20px 20px 4px;
  border-right: 1px solid var(--el-border-color-light);
}

.compare-pane {
  min-width: 0;
  padding: 0 0 20px 20px;
}

.pane-heading {
  min-height: 38px;
  margin-bottom: 14px;

  span {
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }
}

.version-item {
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
}

.version-title,
.version-meta,
.version-links {
  flex-wrap: wrap;
  gap: 8px;
}

.version-meta,
.version-links {
  margin-top: 7px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.version-item > .el-button {
  margin-top: 6px;
  padding-left: 0;
}

.compare-heading {
  margin-bottom: 8px;
}

.compare-toolbar {
  gap: 10px;
  margin-bottom: 14px;

  .el-select {
    width: 220px;
  }
}

.compare-groups,
.snapshot-groups {
  margin-top: 14px;
}

.group-title {
  gap: 8px;
}

.field-name {
  font-weight: 600;
}

.field-code {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.value-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

@media (max-width: 960px) {
  .version-layout {
    display: block;
  }

  .timeline-pane {
    border-right: 0;
    border-bottom: 1px solid var(--el-border-color-light);
    padding-right: 0;
  }

  .compare-pane {
    padding: 20px 0 0;
  }

  .compare-toolbar {
    align-items: stretch;
    flex-direction: column;

    .el-select {
      width: 100%;
    }
  }
}
</style>
