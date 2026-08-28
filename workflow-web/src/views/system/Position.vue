<template>
  <div class="position-management">
    <div class="page-header">
      <div>
        <h2>职务管理</h2>
        <p>职务描述业务责任，任职关系决定某人在具体组织或部门中承担该职务。</p>
      </div>
      <div class="page-header__actions">
        <el-button
          v-if="activeTab === 'definitions' && canManage"
          type="primary"
          @click="openPositionDialog()"
        >
          <el-icon><Plus /></el-icon>新增职务
        </el-button>
        <template v-if="activeTab === 'assignments' && canAssign">
          <el-button @click="openBatchDialog">
            <el-icon><DocumentAdd /></el-icon>批量任命
          </el-button>
          <el-button type="primary" @click="openAssignmentDialog()">
            <el-icon><Plus /></el-icon>新增任职
          </el-button>
        </template>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="position-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="职务定义" name="definitions">
        <el-form :model="positionQuery" inline class="filter-bar">
          <el-form-item label="关键词">
            <el-input
              v-model="positionQuery.keyword"
              clearable
              placeholder="职务名称或编码"
              @keyup.enter="searchPositions"
            />
          </el-form-item>
          <el-form-item label="适用单位">
            <el-select v-model="positionQuery.applicableUnitType" clearable placeholder="全部" style="width: 130px">
              <el-option label="组织" value="ORG" />
              <el-option label="部门" value="DEPT" />
              <el-option label="组织和部门" value="ANY" />
            </el-select>
          </el-form-item>
          <el-form-item label="任职模式">
            <el-select v-model="positionQuery.holderMode" clearable placeholder="全部" style="width: 120px">
              <el-option label="单人" value="SINGLE" />
              <el-option label="多人" value="MULTIPLE" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="positionQuery.status" clearable placeholder="全部" style="width: 110px">
              <el-option label="启用" value="ENABLED" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="searchPositions">查询</el-button>
            <el-button @click="resetPositionQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <PageState
          v-if="positionLoadError"
          type="error"
          title="职务列表加载失败"
          :description="positionLoadError"
          retryable
          @retry="fetchPositions"
        />
        <el-table
          v-else
          v-loading="positionLoading"
          :data="positionList"
          border
          stripe
          empty-text="当前条件下没有职务"
        >
          <el-table-column prop="positionName" label="职务名称" min-width="150">
            <template #default="{ row }">
              <span>{{ row.positionName }}</span>
              <el-tag v-if="row.builtIn" size="small" type="warning" effect="plain" class="inline-tag">内置</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="positionCode" label="稳定编码" min-width="160" show-overflow-tooltip />
          <el-table-column label="适用单位" width="120">
            <template #default="{ row }">{{ unitTypeLabel(row.applicableUnitType) }}</template>
          </el-table-column>
          <el-table-column label="任职模式" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.holderMode === 'SINGLE' ? 'primary' : 'success'" size="small">
                {{ row.holderMode === 'SINGLE' ? '单人' : '多人' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="有效任职" width="95" align="center">
            <template #default="{ row }">{{ row.currentAssignmentCount ?? row.activeAssignmentCount ?? row.assignmentCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="流程引用" width="95" align="center">
            <template #default="{ row }">{{ row.processReferenceCount ?? row.referenceCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-switch
                :model-value="row.status"
                active-value="ENABLED"
                inactive-value="DISABLED"
                inline-prompt
                active-text="启"
                inactive-text="停"
                :disabled="!canManage"
                @change="changePositionStatus(row, $event)"
              />
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" :disabled="!canManage" @click="openPositionDialog(row)">编辑</el-button>
              <el-button type="primary" link size="small" @click="showPositionReferences(row)">查看引用</el-button>
              <el-button
                v-if="canManage && !row.builtIn"
                type="danger"
                link
                size="small"
                :disabled="hasPositionReferences(row)"
                @click="removePosition(row)"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="positionQuery.pageNum"
          v-model:page-size="positionQuery.pageSize"
          :total="positionTotal"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          class="pagination"
          @size-change="handlePositionPageSizeChange"
          @current-change="fetchPositions"
        />
      </el-tab-pane>

      <el-tab-pane label="组织任职" name="assignments">
        <div class="assignment-layout">
          <aside class="organization-panel">
            <div class="organization-panel__header">
              <strong>组织部门</strong>
              <el-button link type="primary" @click="selectAllUnits">全部</el-button>
            </div>
            <el-input
              v-model="organizationKeyword"
              clearable
              :prefix-icon="Search"
              placeholder="搜索组织或部门"
              class="organization-panel__search"
            />
            <el-tree
              ref="organizationTreeRef"
              v-loading="organizationLoading"
              :data="organizationTree"
              node-key="id"
              default-expand-all
              highlight-current
              :props="{ label: 'orgName', children: 'children' }"
              :filter-node-method="filterOrganizationNode"
              @node-click="selectOrganizationUnit"
            >
              <template #default="{ data }">
                <span class="organization-node">
                  <el-icon><OfficeBuilding v-if="data.type === 'org'" /><House v-else /></el-icon>
                  <span>{{ data.orgName }}</span>
                </span>
              </template>
            </el-tree>
          </aside>

          <section class="assignment-content">
            <div class="assignment-context">
              <div>
                <strong>{{ selectedUnit ? selectedUnit.orgName : '全部组织任职' }}</strong>
                <span v-if="selectedUnit?.displayPath">{{ selectedUnit.displayPath }}</span>
              </div>
            </div>
            <el-form :model="assignmentQuery" inline class="filter-bar assignment-filter">
              <el-form-item label="关键词">
                <el-input
                  v-model="assignmentQuery.keyword"
                  clearable
                  placeholder="用户姓名或账号"
                  @keyup.enter="searchAssignments"
                />
              </el-form-item>
              <el-form-item label="职务">
                <el-select
                  v-model="assignmentQuery.positionCode"
                  clearable
                  filterable
                  placeholder="全部职务"
                  style="width: 180px"
                >
                  <el-option
                    v-for="item in enabledPositionOptions"
                    :key="item.positionCode"
                    :label="item.positionName"
                    :value="item.positionCode"
                  />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-checkbox v-model="assignmentQuery.activeOnly">仅当前有效</el-checkbox>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="searchAssignments">查询</el-button>
                <el-button @click="resetAssignmentQuery">重置</el-button>
              </el-form-item>
            </el-form>

            <PageState
              v-if="assignmentLoadError"
              type="error"
              title="组织任职加载失败"
              :description="assignmentLoadError"
              retryable
              @retry="fetchAssignments"
            />
            <el-table
              v-else
              v-loading="assignmentLoading"
              :data="assignmentList"
              border
              stripe
              empty-text="当前范围没有任职记录"
            >
              <el-table-column label="任职用户" min-width="140">
                <template #default="{ row }">
                  <strong>{{ assignmentUserLabel(row) }}</strong>
                  <div class="secondary-text">{{ row.username || row.userCode || row.userId }}</div>
                </template>
              </el-table-column>
              <el-table-column label="职务" min-width="150">
                <template #default="{ row }">
                  {{ row.positionName || row.positionCode }}
                  <div class="secondary-text">{{ row.positionCode }}</div>
                </template>
              </el-table-column>
              <el-table-column label="组织节点" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">{{ assignmentUnitLabel(row) }}</template>
              </el-table-column>
              <el-table-column label="有效期" min-width="190">
                <template #default="{ row }">
                  {{ formatDateTime(row.effectiveFrom) }}
                  <div class="secondary-text">至 {{ row.effectiveTo ? formatDateTime(row.effectiveTo) : '长期' }}</div>
                </template>
              </el-table-column>
              <el-table-column label="主职" width="70" align="center">
                <template #default="{ row }"><el-tag v-if="row.isPrimary" size="small">主职</el-tag><span v-else>-</span></template>
              </el-table-column>
              <el-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="assignmentStatus(row).type" size="small">{{ assignmentStatus(row).label }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="130" fixed="right">
                <template #default="{ row }">
                  <el-button
                    type="primary"
                    link
                    size="small"
                    :disabled="!canAssign || !isAssignmentActive(row)"
                    @click="openTransferDialog(row)"
                  >转任</el-button>
                  <el-button
                    type="danger"
                    link
                    size="small"
                    :disabled="!canAssign || !isAssignmentActive(row)"
                    @click="revokeAssignment(row)"
                  >撤销</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination
              v-model:current-page="assignmentQuery.pageNum"
              v-model:page-size="assignmentQuery.pageSize"
              :total="assignmentTotal"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              class="pagination"
              @size-change="handleAssignmentPageSizeChange"
              @current-change="fetchAssignments"
            />
          </section>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="positionDialogVisible"
      :title="positionForm.id ? '编辑职务' : '新增职务'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="positionFormRef" :model="positionForm" :rules="positionRules" label-width="104px">
        <el-form-item label="职务名称" prop="positionName">
          <el-input v-model="positionForm.positionName" placeholder="如：部门负责人" />
        </el-form-item>
        <el-form-item label="稳定编码" prop="positionCode">
          <el-input
            v-model="positionForm.positionCode"
            :disabled="Boolean(positionForm.id)"
            placeholder="如：UNIT_LEADER"
          />
          <div class="form-tip">创建后不可修改，流程定义和外部集成使用该编码。</div>
        </el-form-item>
        <el-form-item label="适用单位" prop="applicableUnitType">
          <el-radio-group v-model="positionForm.applicableUnitType">
            <el-radio-button value="ORG">组织</el-radio-button>
            <el-radio-button value="DEPT">部门</el-radio-button>
            <el-radio-button value="ANY">组织和部门</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="任职模式" prop="holderMode">
          <el-radio-group v-model="positionForm.holderMode">
            <el-radio-button value="SINGLE">单人</el-radio-button>
            <el-radio-button value="MULTIPLE">多人</el-radio-button>
          </el-radio-group>
          <div class="form-tip">单人职务更换人员时必须显式转任；多人职务可设置唯一主职。</div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="positionForm.sortOrder" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="positionForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="positionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="positionSubmitting" @click="savePosition">保存职务</el-button>
      </template>
    </el-dialog>

    <PositionAssignmentDialog
      v-model="assignmentDialogVisible"
      :locked-unit="assignmentDialogContext.unit"
      :locked-position="assignmentDialogContext.position"
      :transfer="assignmentDialogContext.transfer"
      @saved="handleAssignmentSaved"
    />

    <el-dialog
      v-model="batchDialogVisible"
      title="平行部门批量任命"
      width="min(1180px, 96vw)"
      top="5vh"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-alert
        title="最多 200 行；先执行只读预检，正式提交将全量原子成功或失败。"
        type="info"
        :closable="false"
        show-icon
        class="batch-alert"
      />
      <el-table :data="batchRows" border max-height="480">
        <el-table-column label="#" type="index" width="50" />
        <el-table-column label="组织节点" min-width="210">
          <template #default="{ row }">
            <el-tree-select
              v-model="row.organizationUnitId"
              :data="organizationTree"
              :props="{ label: 'orgName', value: 'id', children: 'children' }"
              check-strictly
              filterable
              default-expand-all
              placeholder="选择组织或部门"
              style="width: 100%"
              @change="handleBatchUnitChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="职务" min-width="180">
          <template #default="{ row }">
            <el-select v-model="row.positionCode" filterable placeholder="选择职务" style="width: 100%" @change="clearBatchPrecheck">
              <el-option v-for="item in batchPositionOptions(row)" :key="item.positionCode" :label="item.positionName" :value="item.positionCode" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="新任职人" min-width="210">
          <template #default="{ row }">
            <UserSelector v-model="row.userId" value-key="id" placeholder="选择用户" title="选择任职人" @change="clearBatchPrecheck" />
          </template>
        </el-table-column>
        <el-table-column label="生效时间" min-width="180">
          <template #default="{ row }">
            <el-date-picker v-model="row.effectiveFromLocal" type="datetime" format="YYYY-MM-DD HH:mm" style="width: 100%" @change="clearBatchPrecheck" />
          </template>
        </el-table-column>
        <el-table-column label="处理方式" min-width="150">
          <template #default="{ row }">
            <el-select v-model="row.replaceExisting" style="width: 100%" @change="clearBatchPrecheck">
              <el-option label="新增任命" :value="false" />
              <el-option label="转任原负责人" :value="true" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="预检" min-width="150">
          <template #default="{ row }">
            <el-tag v-if="row.precheckStatus === 'passed'" type="success" size="small">通过</el-tag>
            <el-tooltip v-else-if="row.precheckStatus === 'failed'" :content="row.precheckMessage" placement="top">
              <el-tag type="danger" size="small">未通过</el-tag>
            </el-tooltip>
            <span v-else class="secondary-text">待预检</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" fixed="right">
          <template #default="{ $index }">
            <el-button type="danger" link :disabled="batchRows.length <= 1" @click="removeBatchRow($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button class="add-batch-row" :disabled="batchRows.length >= 200" @click="addBatchRow">
        <el-icon><Plus /></el-icon>添加一行
      </el-button>
      <el-form label-width="90px" class="batch-reason">
        <el-form-item label="任命原因" required>
          <el-input v-model="batchReason" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="请输入本次批量任命原因" @input="clearBatchPrecheck" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button :loading="batchPrechecking" @click="precheckBatch">预检全部</el-button>
        <el-button type="primary" :disabled="!batchPrecheckPassed" :loading="batchSubmitting" @click="submitBatch">原子提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DocumentAdd, House, OfficeBuilding, Plus, Search } from '@element-plus/icons-vue'
import PageState from '@/components/PageState.vue'
import UserSelector from '@/components/UserSelector.vue'
import PositionAssignmentDialog from '@/views/system/components/PositionAssignmentDialog.vue'
import { useUserStore } from '@/stores/user'
import { getOrgTree } from '@/api/system/org'
import { createIdempotentSubmissionKeyTracker } from '@/shared/idempotent-submission'
import {
  batchAssignPositions,
  createPosition,
  deletePosition,
  getEnabledPositions,
  getPositionAssignmentPage,
  getPositionPage,
  precheckPositionAssignments,
  revokePositionAssignment,
  updatePosition,
  updatePositionStatus
} from '@/api/system/position'

const userStore = useUserStore()
const hasPermission = (permission: string) => userStore.isSuperAdmin
  || userStore.permissions.includes('*')
  || userStore.permissions.includes(permission)
const canManage = computed(() => hasPermission('system:position:manage'))
const canAssign = computed(() => hasPermission('system:position:assign'))

const activeTab = ref('definitions')
const positionLoading = ref(false)
const positionLoadError = ref('')
const positionList = ref<any[]>([])
const positionTotal = ref(0)
const positionQuery = reactive({
  keyword: '', applicableUnitType: '', holderMode: '', status: '', pageNum: 1, pageSize: 20
})
const enabledPositionOptions = ref<any[]>([])

const positionDialogVisible = ref(false)
const positionFormRef = ref()
const positionSubmitting = ref(false)
const positionForm = reactive({
  id: '', positionName: '', positionCode: '', applicableUnitType: 'ANY', holderMode: 'SINGLE',
  status: 'ENABLED', sortOrder: 0, description: '', revision: undefined as number | undefined
})
const positionRules = {
  positionName: [{ required: true, message: '请输入职务名称', trigger: 'blur' }],
  positionCode: [
    { required: true, message: '请输入稳定编码', trigger: 'blur' },
    { pattern: /^[A-Z][A-Z0-9_]{1,99}$/, message: '编码应使用大写字母、数字和下划线', trigger: 'blur' }
  ],
  applicableUnitType: [{ required: true, message: '请选择适用单位', trigger: 'change' }],
  holderMode: [{ required: true, message: '请选择任职模式', trigger: 'change' }]
}

const organizationTreeRef = ref()
const organizationLoading = ref(false)
const organizationTree = ref<any[]>([])
const organizationKeyword = ref('')
const selectedUnit = ref<any>(null)
const assignmentLoading = ref(false)
const assignmentLoadError = ref('')
const assignmentList = ref<any[]>([])
const assignmentTotal = ref(0)
const assignmentQuery = reactive({ keyword: '', positionCode: '', activeOnly: true, pageNum: 1, pageSize: 20 })
const assignmentDialogVisible = ref(false)
const assignmentDialogContext = reactive({ unit: null as any, position: null as any, transfer: false })

const batchDialogVisible = ref(false)
const batchRows = ref<any[]>([])
const batchReason = ref('')
const batchPrechecking = ref(false)
const batchSubmitting = ref(false)
const batchSubmissionKeyTracker = createIdempotentSubmissionKeyTracker()
const batchPrecheckPassed = computed(() => batchRows.value.length > 0
  && batchRows.value.every(row => row.precheckStatus === 'passed'))
const flatOrganizationUnits = computed(() => {
  const result: any[] = []
  const walk = (nodes: any[]) => (nodes || []).forEach(node => {
    result.push(node)
    walk(node.children || [])
  })
  walk(organizationTree.value)
  return result
})

watch(organizationKeyword, value => organizationTreeRef.value?.filter(value))

function normalizePage(result: any, fallbackPage: number, fallbackSize: number) {
  return {
    records: result?.records || result?.list || (Array.isArray(result) ? result : []),
    total: Number(result?.total ?? (Array.isArray(result) ? result.length : 0)),
    pageNum: Number(result?.pageNum ?? result?.current ?? fallbackPage),
    pageSize: Number(result?.pageSize ?? result?.size ?? fallbackSize)
  }
}

async function fetchPositions() {
  positionLoading.value = true
  positionLoadError.value = ''
  try {
    const page = normalizePage(await getPositionPage({
      ...positionQuery,
      keyword: positionQuery.keyword.trim() || undefined,
      applicableUnitType: positionQuery.applicableUnitType || undefined,
      holderMode: positionQuery.holderMode || undefined,
      status: positionQuery.status || undefined
    } as any), positionQuery.pageNum, positionQuery.pageSize)
    positionList.value = page.records
    positionTotal.value = page.total
    positionQuery.pageNum = page.pageNum
    positionQuery.pageSize = page.pageSize
  } catch (error: any) {
    positionLoadError.value = error?.message || '无法读取职务定义，请重试。'
  } finally {
    positionLoading.value = false
  }
}

async function fetchEnabledPositions() {
  const result = await getEnabledPositions()
  enabledPositionOptions.value = Array.isArray(result) ? result : result?.records || result?.list || []
}

function searchPositions() { positionQuery.pageNum = 1; fetchPositions() }
function resetPositionQuery() {
  Object.assign(positionQuery, { keyword: '', applicableUnitType: '', holderMode: '', status: '', pageNum: 1 })
  fetchPositions()
}
function handlePositionPageSizeChange() { positionQuery.pageNum = 1; fetchPositions() }

function resetPositionForm() {
  Object.assign(positionForm, {
    id: '', positionName: '', positionCode: '', applicableUnitType: 'ANY', holderMode: 'SINGLE',
    status: 'ENABLED', sortOrder: 0, description: '', revision: undefined
  })
}

function openPositionDialog(row: any = null) {
  resetPositionForm()
  if (row) Object.assign(positionForm, {
    id: row.id,
    positionName: row.positionName,
    positionCode: row.positionCode,
    applicableUnitType: row.applicableUnitType,
    holderMode: row.holderMode,
    status: row.status,
    sortOrder: row.sortOrder ?? row.sort ?? 0,
    description: row.description || '',
    revision: row.revision
  })
  positionDialogVisible.value = true
  nextTick(() => positionFormRef.value?.clearValidate())
}

async function savePosition() {
  const valid = await positionFormRef.value?.validate().catch(() => false)
  if (!valid) return
  positionSubmitting.value = true
  try {
    const mutableFields = {
      positionName: positionForm.positionName,
      applicableUnitType: positionForm.applicableUnitType,
      holderMode: positionForm.holderMode,
      sortOrder: positionForm.sortOrder,
      description: positionForm.description
    }
    if (positionForm.id) {
      await updatePosition(positionForm.id, {
        ...mutableFields,
        revision: positionForm.revision
      })
    } else {
      await createPosition({
        positionCode: positionForm.positionCode,
        ...mutableFields
      })
    }
    ElMessage.success(positionForm.id ? '职务已更新' : '职务已创建')
    positionDialogVisible.value = false
    await Promise.all([fetchPositions(), fetchEnabledPositions()])
  } finally {
    positionSubmitting.value = false
  }
}

async function changePositionStatus(row: any, status: 'ENABLED' | 'DISABLED') {
  const action = status === 'ENABLED' ? '启用' : '停用'
  try {
    if (status === 'DISABLED') await ElMessageBox.confirm(
      '停用后，尚未创建的后续审批节点将不能再解析该职务；已生成任务不自动改派。',
      `确认${action}职务`, { type: 'warning', confirmButtonText: `确认${action}` }
    )
    await updatePositionStatus(row.id, status, row.revision)
    row.status = status
    ElMessage.success(`职务已${action}`)
    await fetchEnabledPositions()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') await fetchPositions()
  }
}

function hasPositionReferences(row: any) {
  return Number(row.currentAssignmentCount || row.activeAssignmentCount || row.assignmentCount || 0) > 0
    || Number(row.processReferenceCount || row.referenceCount || 0) > 0
}

async function removePosition(row: any) {
  try {
    await ElMessageBox.confirm(`删除职务「${row.positionName}」后编码不可复用，确认继续吗？`, '删除职务', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消'
    })
    await deletePosition(row.id, row.revision)
    ElMessage.success('职务已删除')
    await Promise.all([fetchPositions(), fetchEnabledPositions()])
  } catch { /* 用户取消 */ }
}

function showPositionReferences(row: any) {
  ElMessageBox.alert(
    `当前有效任职 ${row.currentAssignmentCount ?? row.activeAssignmentCount ?? row.assignmentCount ?? 0} 条，流程引用 ${row.processReferenceCount ?? row.referenceCount ?? 0} 处。`,
    `职务引用 - ${row.positionName}`, { confirmButtonText: '关闭' }
  )
}

async function loadOrganizationTree() {
  organizationLoading.value = true
  try {
    const result = await getOrgTree()
    organizationTree.value = Array.isArray(result) ? result : result?.records || result?.list || []
  } finally {
    organizationLoading.value = false
  }
}

function filterOrganizationNode(value: string, data: any) {
  if (!value) return true
  const keyword = value.trim().toLowerCase()
  return `${data.orgName || ''} ${data.orgCode || ''} ${data.displayPath || ''}`.toLowerCase().includes(keyword)
}

function selectOrganizationUnit(data: any) {
  selectedUnit.value = data
  assignmentQuery.pageNum = 1
  fetchAssignments()
}

function selectAllUnits() {
  selectedUnit.value = null
  organizationTreeRef.value?.setCurrentKey(null)
  assignmentQuery.pageNum = 1
  fetchAssignments()
}

async function fetchAssignments() {
  assignmentLoading.value = true
  assignmentLoadError.value = ''
  try {
    const page = normalizePage(await getPositionAssignmentPage({
      keyword: assignmentQuery.keyword.trim() || undefined,
      positionCode: assignmentQuery.positionCode || undefined,
      organizationUnitId: selectedUnit.value?.id || undefined,
      activeOnly: assignmentQuery.activeOnly,
      pageNum: assignmentQuery.pageNum,
      pageSize: assignmentQuery.pageSize
    }), assignmentQuery.pageNum, assignmentQuery.pageSize)
    assignmentList.value = page.records
    assignmentTotal.value = page.total
    assignmentQuery.pageNum = page.pageNum
    assignmentQuery.pageSize = page.pageSize
  } catch (error: any) {
    assignmentLoadError.value = error?.message || '无法读取组织任职，请重试。'
  } finally {
    assignmentLoading.value = false
  }
}

function searchAssignments() { assignmentQuery.pageNum = 1; fetchAssignments() }
function resetAssignmentQuery() {
  Object.assign(assignmentQuery, { keyword: '', positionCode: '', activeOnly: true, pageNum: 1 })
  fetchAssignments()
}
function handleAssignmentPageSizeChange() { assignmentQuery.pageNum = 1; fetchAssignments() }

function openAssignmentDialog(context: any = {}) {
  Object.assign(assignmentDialogContext, {
    unit: context.unit || selectedUnit.value || null,
    position: context.position || null,
    transfer: context.transfer === true
  })
  assignmentDialogVisible.value = true
}

function openTransferDialog(row: any) {
  openAssignmentDialog({
    unit: {
      id: row.organizationUnitId,
      orgName: row.organizationUnitName || row.orgName,
      displayPath: row.organizationPath || row.orgPath,
      type: row.organizationUnitType || row.unitType
    },
    position: {
      id: row.positionId,
      positionCode: row.positionCode,
      positionName: row.positionName,
      applicableUnitType: row.applicableUnitType || 'ANY',
      holderMode: row.holderMode || 'SINGLE'
    },
    transfer: true
  })
}

async function handleAssignmentSaved() {
  await Promise.all([fetchAssignments(), fetchPositions()])
}

async function revokeAssignment(row: any) {
  try {
    const result = await ElMessageBox.prompt(
      '撤销只影响尚未创建的后续审批任务，已生成任务不会自动改派。请输入撤销原因。',
      `撤销任职 - ${assignmentUserLabel(row)}`,
      {
        type: 'warning',
        inputType: 'textarea',
        inputPlaceholder: '请输入撤销原因',
        inputValidator: value => Boolean(String(value || '').trim()) || '撤销原因不能为空',
        confirmButtonText: '确认撤销', cancelButtonText: '取消'
      }
    )
    await revokePositionAssignment(row.id, {
      reason: String(result.value).trim(),
      revision: row.revision
    })
    ElMessage.success('任职已撤销')
    await Promise.all([fetchAssignments(), fetchPositions()])
  } catch { /* 用户取消 */ }
}

function createBatchRow() {
  return {
    organizationUnitId: selectedUnit.value?.id || '',
    positionCode: '', userId: '', effectiveFromLocal: new Date(), effectiveToLocal: null,
    isPrimary: true, sortOrder: 0, replaceExisting: false,
    precheckStatus: 'idle', precheckMessage: ''
  }
}

function openBatchDialog() {
  batchSubmissionKeyTracker.clear()
  batchRows.value = [createBatchRow()]
  batchReason.value = ''
  batchDialogVisible.value = true
}
function addBatchRow() { if (batchRows.value.length < 200) batchRows.value.push(createBatchRow()) }
function removeBatchRow(index: number) { batchRows.value.splice(index, 1); clearBatchPrecheck() }
function clearBatchPrecheck() {
  batchRows.value.forEach(row => { row.precheckStatus = 'idle'; row.precheckMessage = '' })
  batchSubmissionKeyTracker.clear()
}

function batchPositionOptions(row: any) {
  const unit = flatOrganizationUnits.value.find(item =>
    String(item.id) === String(row.organizationUnitId))
  const unitType = String(unit?.type || '').toUpperCase()
  if (!unitType) return enabledPositionOptions.value
  return enabledPositionOptions.value.filter(position =>
    position.applicableUnitType === 'ANY'
    || position.applicableUnitType === unitType)
}

function handleBatchUnitChange(row: any) {
  if (row.positionCode
      && !batchPositionOptions(row).some(position =>
        position.positionCode === row.positionCode)) {
    row.positionCode = ''
    ElMessage.warning('已清除不适用于该组织节点类型的职务')
  }
  clearBatchPrecheck()
}

function buildBatchRequest() {
  return {
    atomic: true,
    reason: batchReason.value.trim(),
    items: batchRows.value.map(row => ({
      positionCode: row.positionCode,
      organizationUnitId: row.organizationUnitId,
      userId: row.userId,
      effectiveFrom: row.effectiveFromLocal ? new Date(row.effectiveFromLocal).toISOString() : null,
      effectiveTo: row.effectiveToLocal ? new Date(row.effectiveToLocal).toISOString() : null,
      isPrimary: row.isPrimary,
      sortOrder: row.sortOrder,
      replaceExisting: row.replaceExisting
    }))
  }
}

function validateBatchRows() {
  if (!batchReason.value.trim()) return '请输入批量任命原因'
  const invalidIndex = batchRows.value.findIndex(row =>
    !row.organizationUnitId || !row.positionCode || !row.userId || !row.effectiveFromLocal)
  return invalidIndex >= 0 ? `第 ${invalidIndex + 1} 行信息不完整` : ''
}

async function precheckBatch() {
  const validationError = validateBatchRows()
  if (validationError) { ElMessage.warning(validationError); return }
  batchPrechecking.value = true
  clearBatchPrecheck()
  const request = buildBatchRequest()
  try {
    const result: any = await precheckPositionAssignments(request)
    const items = Array.isArray(result) ? result : result?.items || result?.results || []
    batchRows.value.forEach((row, index) => {
      const item = items.find((candidate: any) => Number(candidate.index) === index)
        || items[index]
        || {}
      const passed = item.valid ?? item.passed ?? item.success ?? (result?.valid ?? result?.passed ?? true)
      row.precheckStatus = passed === false ? 'failed' : 'passed'
      row.precheckMessage = item.message || item.errorMessage || item.errorCode || ''
    })
    if (batchPrecheckPassed.value) {
      batchSubmissionKeyTracker.keyFor(request)
      ElMessage.success(`全部 ${batchRows.value.length} 行预检通过`)
    }
    else ElMessage.warning('部分任职未通过预检，请查看行内结果')
  } catch (error: any) {
    ElMessage.error(error?.message || '批量预检失败')
  } finally {
    batchPrechecking.value = false
  }
}

async function submitBatch() {
  if (!batchPrecheckPassed.value) return
  batchSubmitting.value = true
  try {
    const request = buildBatchRequest()
    await batchAssignPositions(
      request,
      batchSubmissionKeyTracker.keyFor(request)
    )
    ElMessage.success(`已原子提交 ${batchRows.value.length} 条任职`)
    batchSubmissionKeyTracker.clear()
    batchDialogVisible.value = false
    await Promise.all([fetchAssignments(), fetchPositions()])
  } finally {
    batchSubmitting.value = false
  }
}

function assignmentStatus(row: any) {
  if (row.revokedAt || row.status === 'REVOKED') return { label: '已撤销', type: 'info' }
  const now = Date.now()
  if (row.effectiveFrom && new Date(row.effectiveFrom).getTime() > now) return { label: '待生效', type: 'warning' }
  if (row.effectiveTo && new Date(row.effectiveTo).getTime() <= now) return { label: '已到期', type: 'info' }
  return { label: '有效', type: 'success' }
}
function isAssignmentActive(row: any) { return assignmentStatus(row).label === '有效' }
function assignmentUserLabel(row: any) { return row.displayName || row.userDisplayName || row.nickname || row.userName || row.username || row.userId || '-' }
function assignmentUnitLabel(row: any) { return row.organizationPath || row.organizationUnitPath || row.orgPath || row.organizationUnitName || row.orgName || row.organizationUnitId || '-' }
function unitTypeLabel(value: string) { return ({ ORG: '组织', DEPT: '部门', ANY: '组织和部门' } as any)[value] || value || '-' }
function formatDateTime(value: any) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-' }

async function handleTabChange(name: string | number) {
  if (name === 'assignments') await Promise.all([loadOrganizationTree(), fetchEnabledPositions(), fetchAssignments()])
}

onMounted(async () => {
  await Promise.all([fetchPositions(), fetchEnabledPositions()])
})
</script>

<style scoped>
.position-management {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  padding: 20px;
  box-sizing: border-box;
  background: #fff;
}
.page-header,
.page-header__actions,
.assignment-context,
.organization-panel__header,
.organization-node {
  display: flex;
  align-items: center;
}
.page-header { justify-content: space-between; gap: 16px; margin-bottom: 10px; }
.page-header h2 { margin: 0; font-size: 20px; font-weight: 500; }
.page-header p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.page-header__actions { gap: 10px; }
.filter-bar { padding: 12px 12px 0; margin-bottom: 14px; background: var(--el-fill-color-extra-light); border-radius: 8px; }
.position-tabs :deep(.el-tabs__content) { overflow: visible; }
.inline-tag { margin-left: 8px; }
.pagination { margin-top: 16px; justify-content: flex-end; }
.assignment-layout { display: grid; grid-template-columns: 260px minmax(0, 1fr); gap: 16px; min-height: 600px; }
.organization-panel { min-width: 0; padding: 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; }
.organization-panel__header { justify-content: space-between; margin-bottom: 10px; }
.organization-panel__search { margin-bottom: 12px; }
.organization-node { min-width: 0; gap: 6px; }
.assignment-content { min-width: 0; }
.assignment-context { min-height: 38px; margin-bottom: 8px; }
.assignment-context strong { display: block; }
.assignment-context span { display: block; margin-top: 3px; color: var(--el-text-color-secondary); font-size: 12px; }
.assignment-filter { margin-bottom: 12px; }
.secondary-text, .form-tip { margin-top: 3px; color: var(--el-text-color-secondary); font-size: 12px; }
.batch-alert { margin-bottom: 14px; }
.add-batch-row { width: 100%; margin-top: 12px; }
.batch-reason { margin-top: 14px; }
@media (max-width: 900px) {
  .position-management { padding: 12px; }
  .page-header { align-items: flex-start; flex-direction: column; }
  .assignment-layout { grid-template-columns: 1fr; }
  .organization-panel { max-height: 320px; overflow: auto; }
}
</style>
