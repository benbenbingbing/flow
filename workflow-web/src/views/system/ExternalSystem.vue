<template>
  <div class="external-system-management">
    <div class="page-header">
      <div>
        <h2>外部系统</h2>
        <p>维护外部系统基础信息与对接参数；具体接口逻辑仍需按系统定制开发。</p>
      </div>
      <el-button v-if="canManage" type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        新增外部系统
      </el-button>
    </div>

    <el-form :model="query" inline class="filter-bar">
      <el-form-item label="名称">
        <el-input
          v-model="query.systemName"
          clearable
          placeholder="外部系统名称"
          @keyup.enter="search"
        />
      </el-form-item>
      <el-form-item label="编码">
        <el-input
          v-model="query.systemCode"
          clearable
          placeholder="外部系统编码"
          @keyup.enter="search"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部状态">
          <el-option label="启用" value="0" />
          <el-option label="禁用" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <PageState
      v-if="loadError"
      type="error"
      title="外部系统加载失败"
      :description="loadError"
      retryable
      @retry="loadSystems"
    />
    <el-table
      v-else
      v-loading="loading"
      :data="systems"
      border
      stripe
      empty-text="当前条件下没有外部系统"
    >
      <el-table-column prop="systemName" label="名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="systemCode" label="编码" min-width="150" show-overflow-tooltip />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-switch
            v-model="row.status"
            active-value="0"
            inactive-value="1"
            inline-prompt
            active-text="启"
            inactive-text="禁"
            :disabled="!canManage || statusChangingIds.has(row.id)"
            :loading="statusChangingIds.has(row.id)"
            @change="changeStatus(row)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="address" label="地址" min-width="210" show-overflow-tooltip />
      <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
      <el-table-column label="更新时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button
            type="primary"
            link
            size="small"
            :loading="openingId === row.id"
            @click="openView(row)"
          >
            查看
          </el-button>
          <el-button
            type="primary"
            link
            size="small"
            :loading="openingId === row.id"
            @click="openEdit(row)"
          >
            编辑
          </el-button>
          <el-button type="danger" link size="small" @click="removeSystem(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.pageNum"
      v-model:page-size="query.pageSize"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      class="pagination"
      @size-change="changePageSize"
      @current-change="loadSystems"
    />

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="min(980px, 94vw)"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        :disabled="readOnly"
        label-width="96px"
      >
        <div class="form-grid">
          <el-form-item label="名称" prop="systemName">
            <el-input
              v-model="form.systemName"
              maxlength="100"
              placeholder="请输入外部系统名称"
            />
          </el-form-item>
          <el-form-item label="编码" prop="systemCode">
            <el-input
              v-model="form.systemCode"
              maxlength="100"
              :disabled="Boolean(form.id)"
              placeholder="请输入稳定编码"
            />
            <div class="field-help">创建后不可修改，定制接口将通过该编码识别外部系统。</div>
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-radio-group v-model="form.status">
              <el-radio value="0">启用</el-radio>
              <el-radio value="1">禁用</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="地址" prop="address">
            <el-input
              v-model="form.address"
              maxlength="500"
              placeholder="请输入外部系统地址，例如：https://example.com"
            />
          </el-form-item>
          <el-form-item label="描述" prop="description" class="full-width">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="说明系统用途和对接范围"
            />
          </el-form-item>
        </div>

        <section class="parameter-section">
          <div class="section-header">
            <div>
              <h3>其他参数</h3>
              <p>参数仅供后续定制接口读取；英文名在同一外部系统内忽略大小写唯一。</p>
              <p class="security-help">
                请勿在此保存密码、令牌、密钥等敏感凭据；敏感信息应接入受控密钥存储。
              </p>
            </div>
            <el-button
              v-if="!readOnly"
              :icon="Plus"
              :disabled="form.parameters.length >= 200"
              @click="addParameter"
            >
              添加参数
            </el-button>
          </div>

          <el-table
            :data="form.parameters"
            border
            row-key="rowKey"
            empty-text="暂无其他参数，可按需添加"
          >
            <el-table-column label="中文名" min-width="180">
              <template #default="{ row }">
                <el-input
                  v-model="row.nameZh"
                  maxlength="100"
                  placeholder="例如：客户端标识"
                  @input="parameterError = ''"
                />
              </template>
            </el-table-column>
            <el-table-column label="英文名" min-width="210">
              <template #default="{ row }">
                <el-input
                  v-model="row.nameEn"
                  maxlength="100"
                  placeholder="例如：client.id"
                  @input="parameterError = ''"
                />
              </template>
            </el-table-column>
            <el-table-column label="参数值" min-width="280">
              <template #default="{ row }">
                <el-input
                  v-model="row.value"
                  type="textarea"
                  :autosize="{ minRows: 1, maxRows: 4 }"
                  maxlength="65535"
                  placeholder="请输入参数值"
                  @input="parameterError = ''"
                />
              </template>
            </el-table-column>
            <el-table-column v-if="!readOnly" label="操作" width="70" align="center">
              <template #default="{ $index }">
                <el-button
                  text
                  circle
                  type="danger"
                  :icon="Delete"
                  :aria-label="`删除第 ${$index + 1} 个参数`"
                  :title="`删除第 ${$index + 1} 个参数`"
                  @click="removeParameter($index)"
                />
              </template>
            </el-table-column>
          </el-table>
          <div v-if="parameterError" class="parameter-error">{{ parameterError }}</div>
        </section>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ readOnly ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!readOnly" type="primary" :loading="saving" @click="saveSystem">
          {{ form.id ? '保存外部系统' : '创建外部系统' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageState from '@/components/PageState.vue'
import { useUserStore } from '@/stores/user'
import {
  createExternalSystem,
  deleteExternalSystem,
  getExternalSystemById,
  getExternalSystemPage,
  updateExternalSystem,
  updateExternalSystemStatus
} from '@/api/system/externalSystem'
import type {
  ExternalSystemPayload,
  ExternalSystemRecord,
  ExternalSystemStatus
} from '@/api/system/externalSystem'
import {
  normalizeExternalSystemParameters,
  validateExternalSystemParameters
} from '@/shared/external-system-model'

interface EditorParameter {
  id?: string
  nameZh: string
  nameEn: string
  value: string
  sortOrder?: number
  rowKey: string
}

interface EditorForm {
  id: string
  version: number
  systemName: string
  systemCode: string
  status: ExternalSystemStatus
  address: string
  description: string
  parameters: EditorParameter[]
}

const userStore = useUserStore()
const canManage = computed(() => userStore.isSuperAdmin
  || userStore.permissions.includes('*')
  || userStore.permissions.includes('system:external-system:manage'))

const loading = ref(false)
const loadError = ref('')
const systems = ref<ExternalSystemRecord[]>([])
const total = ref(0)
const query = reactive({
  systemName: '',
  systemCode: '',
  status: '' as ExternalSystemStatus | '',
  pageNum: 1,
  pageSize: 20
})

const dialogVisible = ref(false)
const readOnly = ref(false)
const openingId = ref('')
const statusChangingIds = reactive(new Set<string>())
const saving = ref(false)
const formRef = ref()
const parameterError = ref('')
let parameterRowSequence = 0
let detailRequestSequence = 0
let listRequestSequence = 0

const emptyForm = (): EditorForm => ({
  id: '',
  version: 0,
  systemName: '',
  systemCode: '',
  status: '0',
  address: '',
  description: '',
  parameters: []
})
const form = reactive<EditorForm>(emptyForm())
const dialogTitle = computed(() => {
  if (readOnly.value) return '查看外部系统'
  return form.id ? '编辑外部系统' : '新增外部系统'
})
const formRules = {
  systemName: [{ required: true, whitespace: true, message: '请输入外部系统名称', trigger: 'blur' }],
  systemCode: [
    { required: true, message: '请输入外部系统编码', trigger: 'blur' },
    {
      pattern: /^[A-Za-z][A-Za-z0-9_.-]{0,99}$/,
      message: '编码应以字母开头且只包含字母、数字、下划线、点和短横线',
      trigger: 'blur'
    }
  ],
  address: [
    { required: true, whitespace: true, message: '请输入外部系统地址', trigger: 'blur' },
    { max: 500, message: '地址不能超过 500 个字符', trigger: 'blur' }
  ]
}

function createEditorParameter(parameter: Partial<EditorParameter> = {}): EditorParameter {
  parameterRowSequence += 1
  return {
    id: parameter.id ? String(parameter.id) : undefined,
    nameZh: String(parameter.nameZh ?? ''),
    nameEn: String(parameter.nameEn ?? ''),
    value: parameter.value == null ? '' : String(parameter.value),
    sortOrder: parameter.sortOrder,
    rowKey: parameter.id ? `persisted-${parameter.id}` : `new-${parameterRowSequence}`
  }
}

/** 同时替换所有字段和参数数组，防止上一次编辑残留的子行进入下一次提交。 */
function resetForm(value: Partial<EditorForm> = {}) {
  Object.assign(form, emptyForm(), value, {
    parameters: (value.parameters || []).map(createEditorParameter)
  })
  parameterError.value = ''
}

async function loadSystems() {
  const requestSequence = ++listRequestSequence
  loading.value = true
  loadError.value = ''
  try {
    const result = await getExternalSystemPage({
      systemName: query.systemName.trim() || undefined,
      systemCode: query.systemCode.trim() || undefined,
      status: query.status || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    // 快速切换筛选或分页时，旧请求不得覆盖最新条件的结果。
    if (requestSequence !== listRequestSequence) return
    // 共享响应层统一提供 list，同时兼容后端直接返回 MyBatis records 的结构。
    systems.value = result?.records || result?.list || []
    total.value = Number(result?.total || 0)
    query.pageNum = Number(result?.pageNum || result?.current || query.pageNum)
    query.pageSize = Number(result?.pageSize || result?.size || query.pageSize)
  } catch (error: any) {
    if (requestSequence === listRequestSequence) {
      loadError.value = error?.message || '无法读取外部系统，请检查权限或稍后重试。'
    }
  } finally {
    if (requestSequence === listRequestSequence) loading.value = false
  }
}

function search() {
  query.pageNum = 1
  loadSystems()
}

function resetQuery() {
  Object.assign(query, {
    systemName: '',
    systemCode: '',
    status: '',
    pageNum: 1
  })
  loadSystems()
}

function changePageSize() {
  query.pageNum = 1
  loadSystems()
}

function openCreate() {
  // 使仍在途的详情请求失效，避免慢响应覆盖新建表单。
  detailRequestSequence += 1
  openingId.value = ''
  readOnly.value = false
  resetForm()
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

/** 查看和编辑共用详情查询，避免列表摘要缺少参数时展示不完整数据。 */
async function openDetail(row: ExternalSystemRecord, viewOnly: boolean) {
  const requestSequence = ++detailRequestSequence
  openingId.value = row.id
  try {
    const detail = await getExternalSystemById(row.id)
    // 用户可能已切换到另一条记录或新建页，只接纳最后一次详情请求。
    if (requestSequence !== detailRequestSequence) return
    resetForm({
      id: String(detail.id),
      version: Number(detail.version),
      systemName: detail.systemName || '',
      systemCode: detail.systemCode || '',
      status: detail.status || '0',
      address: detail.address || '',
      description: detail.description || '',
      parameters: detail.parameters || []
    })
    readOnly.value = viewOnly
    dialogVisible.value = true
    nextTick(() => formRef.value?.clearValidate())
  } finally {
    if (requestSequence === detailRequestSequence) openingId.value = ''
  }
}

function openView(row: ExternalSystemRecord) {
  return openDetail(row, true)
}

function openEdit(row: ExternalSystemRecord) {
  return openDetail(row, false)
}

function addParameter() {
  form.parameters.push(createEditorParameter())
  parameterError.value = ''
}

function removeParameter(index: number) {
  form.parameters.splice(index, 1)
  parameterError.value = ''
}

/**
 * 基本信息与参数一次提交，保证页面不会出现“主记录已保存、参数只保存一半”的中间状态。
 */
async function saveSystem() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  parameterError.value = validateExternalSystemParameters(form.parameters)
  if (parameterError.value) {
    ElMessage.warning(parameterError.value)
    return
  }

  const payload: ExternalSystemPayload = {
    systemName: form.systemName.trim(),
    systemCode: form.systemCode.trim(),
    status: form.status,
    address: form.address.trim(),
    description: form.description.trim(),
    parameters: normalizeExternalSystemParameters(form.parameters)
  }

  saving.value = true
  try {
    if (form.id) {
      await updateExternalSystem(form.id, {
        systemName: payload.systemName,
        status: payload.status,
        address: payload.address,
        description: payload.description,
        parameters: payload.parameters,
        expectedVersion: form.version
      })
    } else {
      await createExternalSystem(payload)
    }
    ElMessage.success(form.id ? '外部系统已更新' : '外部系统已创建')
    dialogVisible.value = false
    await loadSystems()
  } finally {
    saving.value = false
  }
}

async function changeStatus(row: ExternalSystemRecord) {
  if (statusChangingIds.has(row.id)) return
  statusChangingIds.add(row.id)
  const nextStatus = row.status
  const previousStatus: ExternalSystemStatus = nextStatus === '0' ? '1' : '0'
  const action = nextStatus === '0' ? '启用' : '禁用'
  let confirmed = false
  try {
    await ElMessageBox.confirm(
      `${action}外部系统「${row.systemName}」只改变配置可用状态，不会执行任何对接接口。`,
      `${action}外部系统`,
      {
        type: nextStatus === '0' ? 'info' : 'warning',
        confirmButtonText: `确认${action}`,
        cancelButtonText: '取消'
      }
    )
    confirmed = true
    await updateExternalSystemStatus(row.id, nextStatus, row.version)
    ElMessage.success(`外部系统已${action}`)
    await loadSystems()
  } catch {
    // 用户取消或接口失败时都恢复开关，避免页面状态与服务端不一致。
    row.status = previousStatus
    if (confirmed) await loadSystems()
  } finally {
    statusChangingIds.delete(row.id)
  }
}

async function removeSystem(row: ExternalSystemRecord) {
  let submitted = false
  try {
    const confirmation = await ElMessageBox.prompt(
      `删除会同时删除该外部系统的全部参数。请输入系统编码「${row.systemCode}」确认。`,
      '删除外部系统',
      {
        type: 'warning',
        inputPlaceholder: row.systemCode,
        inputValidator: value => value === row.systemCode || '系统编码不匹配',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消'
      }
    )
    if (confirmation.value !== row.systemCode) return
    submitted = true
    await deleteExternalSystem(row.id, row.version)
    ElMessage.success('外部系统已删除')
    if (systems.value.length === 1 && query.pageNum > 1) query.pageNum -= 1
    await loadSystems()
  } catch {
    // 请求失败由共享层提示，并刷新可能已被其他管理员修改的版本。
    if (submitted) await loadSystems()
  }
}

function formatDateTime(value?: string) {
  return value
    ? new Date(value).toLocaleString('zh-CN', { hour12: false })
    : '-'
}

onMounted(loadSystems)
</script>

<style scoped>
.external-system-management {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  padding: 20px;
  box-sizing: border-box;
  background: #fff;
}

.page-header,
.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.page-header {
  margin-bottom: 16px;
}

.page-header h2,
.section-header h3 {
  margin: 0;
  font-weight: 500;
}

.page-header h2 {
  font-size: 20px;
}

.page-header p,
.section-header p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.filter-bar {
  padding: 12px 12px 0;
  margin-bottom: 16px;
  background: var(--el-fill-color-extra-light);
  border-radius: 8px;
}

.filter-bar :deep(.el-input),
.filter-bar :deep(.el-select) {
  width: 190px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.full-width {
  grid-column: 1 / -1;
}

.field-help {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.parameter-section {
  margin-top: 10px;
}

.section-header {
  align-items: flex-end;
  margin-bottom: 10px;
}

.section-header h3 {
  font-size: 16px;
}

.parameter-error {
  margin-top: 8px;
  color: var(--el-color-danger);
  font-size: 13px;
}

.security-help {
  color: var(--el-color-warning-dark-2) !important;
}

@media (max-width: 760px) {
  .external-system-management {
    padding: 12px;
  }

  .page-header,
  .section-header {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .full-width {
    grid-column: auto;
  }

  .pagination {
    overflow-x: auto;
    justify-content: flex-start;
  }
}
</style>
