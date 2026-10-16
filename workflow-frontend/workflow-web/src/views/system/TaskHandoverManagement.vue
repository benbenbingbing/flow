<template>
  <div class="task-handover-management system-management">
    <el-card>
      <div class="page-heading">
        <h2>人员交接</h2>
        <p>查询人员名下的已分配、候选和加签待办，包含等待、暂缓的任务。待交接人员的账号状态不限，接收人必须为正常账号。</p>
      </div>

      <div class="source-filter">
        <label for="handover-source">待交接人员</label>
        <div class="source-picker">
          <UserSelector
            id="handover-source"
            :model-value="sourceUserId"
            :data-source="sourceUserDataSource"
            :disabled="submitting"
            value-key="id"
            title="选择待交接人员"
            placeholder="选择待交接人员（含禁用、已删除人员）"
            @selected="changeSource"
          />
        </div>
        <el-button :disabled="!sourceUserId || submitting" :loading="loading" @click="refresh">刷新待办</el-button>
      </div>

      <div v-if="sourceUserId" class="table-toolbar">
        <div class="selection-summary" role="status">
          共 {{ total }} 项待办，已选 {{ selectedCount }} 项<span v-if="selectedCount">（可跨页选择）</span>
          <el-button v-if="selectedCount" link type="primary" :disabled="submitting" @click="clearSelection">清空选择</el-button>
        </div>
        <template v-if="canTransfer">
          <el-button type="primary" :disabled="!selectedCount || loading || !!loadError || submitting" @click="openTransfer(false)">
            交接选中待办<span v-if="selectedCount">（{{ selectedCount }}）</span>
          </el-button>
          <el-button :disabled="!total || loading || !!loadError || submitting" @click="openTransfer(true)">交接全部待办</el-button>
        </template>
      </div>

      <PageState v-if="!sourceUserId" title="请选择待交接人员" description="支持查询正常、禁用或已删除人员名下的全部待办。" compact />
      <PageState v-else-if="loadError" type="error" title="待办加载失败" :description="loadError" retryable @retry="refresh" />
      <template v-else>
        <el-table
          ref="tableRef" v-loading="loading" :data="tasks" row-key="taskId" stripe
          empty-text="该人员当前没有待办"
          @select="selectCurrentPage" @select-all="selectCurrentPage"
        >
          <el-table-column v-if="canTransfer" type="selection" width="44" :selectable="() => !submitting" />
          <el-table-column label="业务单据" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <div>{{ row.businessName || row.businessCode || row.entityDataId || '-' }}</div>
              <small v-if="row.businessName && row.businessCode">{{ row.businessCode }}</small>
            </template>
          </el-table-column>
          <el-table-column prop="processName" label="流程" min-width="160" show-overflow-tooltip />
          <el-table-column prop="taskName" label="待办任务" min-width="160" show-overflow-tooltip />
          <el-table-column prop="assigneeName" label="当前办理人" min-width="130" show-overflow-tooltip />
          <el-table-column label="分配方式" width="110">
            <template #default="{ row }">{{ assignmentLabel(row.assignmentType) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'todo' ? 'primary' : 'info'" effect="plain">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="180">
            <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="total" class="pagination" :current-page="pageNum" :page-size="pageSize" :total="total"
          :page-sizes="[20, 50, 100]" :disabled="loading || submitting"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="changePage" @size-change="changePageSize"
        />
      </template>
    </el-card>

    <el-dialog
      v-model="dialogVisible" :title="dialogAll ? '交接全部待办' : '交接选中待办'" width="560px"
      :close-on-click-modal="false" :close-on-press-escape="!submitting" :show-close="!submitting" destroy-on-close
    >
      <el-alert type="warning" :closable="false" show-icon>
        <template #title>{{ dialogAll ? '将交接该人员当前全部待办，包含其他分页中的任务。' : `将交接已选的 ${dialogTaskIds.length} 项待办。` }}</template>
        候选任务交接后由接收人独立办理，其他候选人不再可办理。等待、暂缓任务将保留原有状态。
      </el-alert>
      <el-form class="handover-form" label-position="top" :disabled="submitting" @submit.prevent="submitTransfer">
        <el-form-item label="待交接人员">{{ handoverUserLabel(selectedSourceUser) }}</el-form-item>
        <el-form-item label="接收人" required>
          <UserSelector
            :model-value="selectedTargetUser?.id || ''"
            :data-source="targetUserDataSource"
            :disabled="submitting"
            value-key="id"
            title="选择接收人"
            placeholder="选择状态正常的接收人"
            @selected="rememberTarget"
          />
        </el-form-item>
        <el-form-item label="交接原因" required>
          <el-input v-model="reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明交接原因，将记录在任务办理历史中" />
        </el-form-item>
      </el-form>
      <p v-if="dialogAll" class="scope-hint">当前查询到 {{ total }} 项；实际交接范围以提交时该人员的全部待办为准。</p>
      <el-alert v-if="submitError" :title="submitError" type="error" :closable="false" show-icon />
      <template #footer>
        <el-button :disabled="submitting" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitTransfer">确认交接</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageState from '@/components/PageState.vue'
import UserSelector from '@/components/UserSelector.vue'
import { taskHandoverApi } from '@/api/taskHandover'
import { useUserStore } from '@/stores/user'
import {
  buildHandoverPayload, handoverUserLabel, useTaskHandover
} from './task-handover/useTaskHandover.js'
import { createHandoverUserDataSource } from './task-handover/handoverUserDataSource.js'

const userStore = useUserStore()
const canTransfer = computed(() => userStore.isSuperAdmin || userStore.permissions.includes('*') || userStore.permissions.includes('system:task-handover:transfer'))
const state = useTaskHandover(taskHandoverApi)
const {
  sourceUserId, tasks, total, pageNum, pageSize, loading, error: loadError,
  selectedTaskIds, selectedCount, clearSelection, selectCurrentPage, changePage, changePageSize, refresh
} = state
const sourceUserDataSource = createHandoverUserDataSource(taskHandoverApi)
const targetUserDataSource = createHandoverUserDataSource(taskHandoverApi, {
  targetOnly: true, getSourceUserId: () => sourceUserId.value
})
const tableRef = ref(null)
const selectedSourceUser = ref(null)
const selectedTargetUser = ref(null)
const reason = ref('')
const dialogVisible = ref(false)
const dialogAll = ref(false)
const dialogTaskIds = ref([])
const submitting = ref(false)
const submitError = ref('')

async function changeSource(user) {
  selectedSourceUser.value = user
  selectedTargetUser.value = null
  dialogVisible.value = false
  await state.changeSource(user?.id)
}

function rememberTarget(user) {
  selectedTargetUser.value = user
}

/** 将跨页选择回显到当前页；只监听用户 select 事件，防止程序性回显清空其他页选择。 */
watch([tasks, selectedTaskIds], async () => {
  await nextTick()
  const selected = new Set(selectedTaskIds.value)
  for (const task of tasks.value) tableRef.value?.toggleRowSelection(task, selected.has(String(task.taskId)))
})

function openTransfer(all) {
  if (!canTransfer.value || loading.value || loadError.value || (!all && !selectedCount.value) || !total.value) return
  dialogAll.value = all
  dialogTaskIds.value = [...selectedTaskIds.value]
  selectedTargetUser.value = null
  reason.value = ''
  submitError.value = ''
  dialogVisible.value = true
}

/**
 * 在二次确认前固定人员和任务范围，提交期间禁止再次提交或切换来源。
 * 服务端整批校验任务归属；失败保留表单，成功清空选择并重新查询实时待办。
 */
async function submitTransfer() {
  if (submitting.value || !canTransfer.value) return
  let payload
  try {
    payload = buildHandoverPayload({
      sourceUserId: sourceUserId.value, targetUser: selectedTargetUser.value,
      taskIds: dialogTaskIds.value, all: dialogAll.value, reason: reason.value
    })
  } catch (cause) {
    ElMessage.warning(cause.message)
    return
  }
  submitting.value = true
  submitError.value = ''
  try {
    const scope = payload.all ? '当前全部待办（含其他分页）' : `已选的 ${payload.taskIds.length} 项待办`
    await ElMessageBox.confirm(
      `确认将 ${handoverUserLabel(selectedSourceUser.value)} 的${scope}交接给 ${handoverUserLabel(selectedTargetUser.value)}？`,
      '确认人员交接', { type: 'warning', confirmButtonText: '确认交接', cancelButtonText: '返回修改' }
    )
  } catch {
    submitting.value = false
    return
  }
  try {
    const result = await taskHandoverApi.transfer(payload)
    dialogVisible.value = false
    ElMessage.success(`已交接 ${result.transferredCount} 项待办`)
    await refresh()
  } catch (cause) {
    submitError.value = cause?.message || '交接失败，请刷新待办后重试'
  } finally {
    submitting.value = false
  }
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
}
function assignmentLabel(value) {
  return ({ ASSIGNEE: '已分配', ASSIGNED: '已分配', CANDIDATE: '候选', ADD_SIGN: '加签' })[value] || value || '-'
}
function statusLabel(value) {
  return ({ todo: '待办理', waiting: '等待中', hold: '暂缓' })[value] || value || '-'
}

</script>

<style scoped lang="scss">
@use './system-management.scss';
.page-heading h2 { margin: 0 0 10px; font-size: 18px; }
.page-heading p, .scope-hint { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.7; }
.page-heading p { margin: 0 0 24px; }
.source-filter { display: flex; align-items: flex-start; gap: 14px; margin-bottom: 24px; }
.source-filter > label { line-height: 32px; font-size: 14px; white-space: nowrap; }
.source-picker { width: 390px; max-width: 100%; }
.source-picker .user-selector, .handover-form .user-selector { width: 100%; }
.selection-summary { margin-right: auto; color: var(--el-text-color-secondary); font-size: 13px; }
.selection-summary .el-button { margin-left: 8px; }
.handover-form { margin-top: 20px; }
small { color: var(--el-text-color-secondary); }
@media (max-width: 760px) {
  .source-filter { flex-wrap: wrap; }
  .source-filter > label { width: 100%; }
  .source-picker { width: 100%; }
  .selection-summary { width: 100%; margin-bottom: 6px; }
}
</style>
