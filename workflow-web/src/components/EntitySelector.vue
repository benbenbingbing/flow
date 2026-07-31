<template>
  <div class="entity-selector">
    <!-- 单选模式 -->
    <template v-if="!multiple">
      <div
        class="selector-input"
        role="button"
        :tabindex="disabled ? -1 : 0"
        :aria-disabled="disabled"
        @click="openSelector"
        @keydown.enter="openSelector"
        @keydown.space.prevent="openSelector"
      >
        <div v-if="selectedData" class="selected-item">
          <el-tag size="small" :type="getEntityTypeTag(selectedData.entityType)">
            {{ getEntityTypeLabel(selectedData.entityType) }}
          </el-tag>
          <span class="item-name">{{ getItemLabel(selectedData) }}</span>
          <el-button
            class="clear-icon"
            link
            aria-label="清除当前选择"
            title="清除当前选择"
            @click.stop="clearSelection"
          >
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
        <div v-else class="placeholder">{{ placeholder }}</div>
        <el-icon class="arrow-icon"><ArrowDown /></el-icon>
      </div>
    </template>
    
    <!-- 多选模式 -->
    <template v-else>
      <div
        class="selector-input multiple"
        role="button"
        :tabindex="disabled ? -1 : 0"
        :aria-disabled="disabled"
        @click="openSelector"
        @keydown.enter="openSelector"
        @keydown.space.prevent="openSelector"
      >
        <div v-if="selectedList.length > 0" class="selected-list">
          <el-tag
            v-for="item in selectedList"
            :key="item.id"
            closable
            size="small"
            :type="getEntityTypeTag(item.entityType)"
            @close="removeSelection(item)"
          >
            {{ getItemLabel(item) }}
          </el-tag>
        </div>
        <div v-else class="placeholder">{{ placeholder }}</div>
        <el-icon class="arrow-icon"><ArrowDown /></el-icon>
      </div>
    </template>

    <!-- 选择弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      :width="useUnifiedList ? '85%' : multiple ? 'min(1040px, 94vw)' : '800px'"
      top="5vh"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      class="entity-selector-dialog"
    >
      <AsyncEntityDataList
        v-if="useUnifiedList"
        :entity-code="effectiveEntityCode"
        :list-key="listKey"
        scene="FORM_PICKER"
        :context="context"
        :selection-mode="multiple ? 'MULTIPLE' : 'SINGLE'"
        :initial-selected-rows="selectedList"
        @confirm="handleRuntimeConfirm"
        @cancel="dialogVisible = false"
      />

      <div v-else class="selector-layout" :class="{ 'is-multiple': multiple }">
        <div class="selector-body">
          <!-- 实体类型标签（系统实体时显示） -->
          <div v-if="isSystemEntity" class="entity-type-bar">
            <el-tag :type="getEntityTypeTag(entityType)" size="large">
              {{ getEntityTypeLabel(entityType) }}
            </el-tag>
            <span class="type-desc">{{ getEntityTypeDesc(entityType) }}</span>
          </div>

          <!-- 搜索栏 -->
          <div class="search-bar">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索名称或标识"
              clearable
              @keyup.enter="handleSearch"
            >
              <template #append>
                <el-button
                  aria-label="搜索可选记录"
                  title="搜索可选记录"
                  @click="handleSearch"
                >
                  <el-icon><Search /></el-icon>
                </el-button>
              </template>
            </el-input>
          </div>

          <!-- 数据表格 -->
          <el-table
            ref="tableRef"
            :data="tableData"
            v-loading="loading"
            row-key="id"
            max-height="360"
            style="width: 100%"
            @selection-change="handleSelectionChange"
            @row-click="handleRowClick"
          >
            <el-table-column v-if="multiple" type="selection" width="55" />
            <el-table-column prop="name" label="名称" min-width="150" />
            <el-table-column prop="code" label="标识" width="120" />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="getEntityTypeTag(row.entityType)">
                  {{ getEntityTypeLabel(row.entityType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.status" size="small" :type="getStatusType(row.status)">
                  {{ getStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="!multiple" label="操作" width="80">
              <template #default="{ row }">
                <el-button link type="primary" @click.stop="selectRow(row)">
                  选择
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 分页 -->
          <div class="pagination">
            <el-pagination
              v-model:current-page="pageNum"
              v-model:page-size="pageSize"
              :total="total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="handleSizeChange"
              @current-change="handleCurrentChange"
            />
          </div>
        </div>

        <aside v-if="multiple" class="selected-records">
          <div class="selected-records-header">
            <div>
              <strong>已选数据</strong>
              <span>{{ selectedRows.length }} 条</span>
            </div>
            <el-button
              v-if="selectedRows.length"
              link
              type="primary"
              @click="clearDraftSelection"
            >
              清空
            </el-button>
          </div>
          <el-scrollbar class="selected-records-scrollbar" height="360px">
            <el-empty
              v-if="selectedRows.length === 0"
              description="尚未选择数据"
              :image-size="72"
            />
            <div v-else class="selected-record-list">
              <div
                v-for="item in selectedRows"
                :key="item.id"
                class="selected-record-item"
              >
                <div>
                  <strong>{{ getItemLabel(item) }}</strong>
                  <span>{{ getItemSecondary(item) }}</span>
                </div>
                <el-button
                  circle
                  text
                  title="移除"
                  @click="removeDraftSelection(item)"
                >
                  <el-icon><Close /></el-icon>
                </el-button>
              </div>
            </div>
          </el-scrollbar>
        </aside>
      </div>

      <template v-if="!useUnifiedList" #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button v-if="multiple" type="primary" @click="confirmSelection">
            确认选择 ({{ selectedRows.length }})
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, computed, defineAsyncComponent } from 'vue'
import { ArrowDown, Close, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  resolveRuntimeEntitySelectionReference
} from '@/shared/entity-selection-mapping'
import {
  normalizeRecordSelection,
  reconcileRecordPageSelection,
  recordSelectionIds,
  recordSelectionValues,
  removeRecordSelection
} from '@/shared/entity-record-selection'

const props = defineProps({
  // 实体类型：CUSTOM(用户实体)/USER/DEPT/ROLE/GROUP
  entityType: {
    type: String,
    required: true
  },
  // 实体编码（仅 CUSTOM 类型时必填）
  entityCode: {
    type: String,
    default: null
  },
  // 目标实体ID（当 entityCode 为空时，用于后端查询 entityCode）
  refEntityId: {
    type: String,
    default: null
  },
  // 数据接口URL（用于定制返回数据范围）
  apiUrl: {
    type: String,
    default: null
  },
  listKey: {
    type: String,
    default: ''
  },
  runtimeEntityCode: {
    type: String,
    default: ''
  },
  context: {
    type: Object,
    default: () => ({})
  },
  // 是否多选
  multiple: {
    type: Boolean,
    default: false
  },
  // 当前值（单选为id，多选为id数组）
  modelValue: {
    type: [String, Array],
    default: null
  },
  valueKey: {
    type: String,
    default: 'id',
    validator: value => ['id', 'code'].includes(value)
  },
  title: {
    type: String,
    default: ''
  },
  // 占位文本
  placeholder: {
    type: String,
    default: '请选择'
  },
  // 是否禁用
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'change'])
const AsyncEntityDataList = defineAsyncComponent(() => import('@/views/entity/EntityDataList.vue'))

// 计算属性
const isSystemEntity = computed(() => props.entityType !== 'CUSTOM')
const customReference = computed(() =>
  resolveRuntimeEntitySelectionReference(props)
)
const effectiveEntityCode = computed(() =>
  customReference.value.entityCode
)
const effectiveRefEntityId = computed(() =>
  customReference.value.refEntityId
)
const useUnifiedList = computed(() =>
  props.entityType === 'CUSTOM'
  && !!effectiveEntityCode.value
  && !!props.listKey
)
const dialogTitle = computed(() => {
  if (props.title) return props.title
  const typeMap = {
    'CUSTOM': '选择数据',
    'USER': '选择用户',
    'DEPT': '选择部门',
    'ROLE': '选择角色',
    'GROUP': '选择用户组'
  }
  return typeMap[props.entityType] || '选择数据'
})

// 弹窗状态
const dialogVisible = ref(false)
const loading = ref(false)

// 搜索和分页
const searchKeyword = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableData = ref([])

// 选择状态
const selectedData = ref(null)
const selectedList = ref([])
const selectedRows = ref([])
const tableRef = ref(null)
const restoringPageSelection = ref(false)

// 监听值变化
watch(() => props.modelValue, (val) => {
  const hasValue = Array.isArray(val)
    ? val.length > 0
    : val !== null && val !== undefined && val !== ''
  if (hasValue) {
    loadSelectedData()
  } else {
    selectedData.value = null
    selectedList.value = []
    selectedRows.value = []
  }
}, { immediate: true })

// 加载已选择的数据（用于回显）
async function loadSelectedData() {
  if (!props.entityType) return

  // 多选模式：如果 modelValue 已经是对象数组（含 name），直接回显，不去后台
  if (props.multiple && Array.isArray(props.modelValue) && props.modelValue.length > 0) {
    const first = props.modelValue[0]
    if (first && typeof first === 'object' && (first.name || first.code)) {
      selectedList.value = props.modelValue.map(item => ({
        id: item.id || item,
        name: item.name || item.code || item.id,
        code: item.code,
        entityType: item.entityType || props.entityType
      }))
      return
    }
  }

  // 单选模式：如果 modelValue 已经是对象（含 name），直接回显
  if (!props.multiple && props.modelValue && typeof props.modelValue === 'object') {
    selectedData.value = {
      id: props.modelValue.id,
      name: props.modelValue.name || props.modelValue.code || props.modelValue.id,
      code: props.modelValue.code,
      entityType: props.modelValue.entityType || props.entityType
    }
    return
  }

  // 纯 ID 模式：去后台查询详情
  if (
    props.entityType === 'CUSTOM'
    && !effectiveEntityCode.value
    && !effectiveRefEntityId.value
  ) {
    return
  }

  try {
    const values = props.multiple && Array.isArray(props.modelValue)
      ? props.modelValue.join(',')
      : props.modelValue

    if (!values) return

    const params = new URLSearchParams({
      ids: values,
      valueKey: props.valueKey
    })
    if (props.entityType === 'CUSTOM') {
      if (effectiveEntityCode.value) {
        params.append('entityCode', effectiveEntityCode.value)
      } else if (effectiveRefEntityId.value) {
        params.append('refEntityId', effectiveRefEntityId.value)
      }
    }

    const res = await fetch(`/api/entity-selector/${props.entityType}/batch?${params}`, {
      headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
    }).then(r => r.json())

    if (res.code === 200) {
      if (props.multiple) {
        selectedList.value = res.data || []
      } else {
        selectedData.value = res.data?.[0] || null
      }
    }
  } catch (e) {
    console.error('加载已选数据失败:', e)
  }
}

// 打开选择器
async function openSelector() {
  if (props.disabled) return
  await loadSelectedData()
  selectedRows.value = normalizeRecordSelection(selectedList.value)
  dialogVisible.value = true
  if (useUnifiedList.value) {
    return
  }
  pageNum.value = 1
  searchKeyword.value = ''
  await loadData()
}

function handleRuntimeConfirm(rows) {
  const selected = Array.isArray(rows) ? rows : []
  if (props.multiple) {
    selectedList.value = selected
    emit('update:modelValue', recordSelectionValues(selected, props.valueKey))
    emit('change', selected)
  } else {
    const row = selected[0] || null
    selectedData.value = row
    emit('update:modelValue', selectionValue(row))
    emit('change', row)
  }
  dialogVisible.value = false
}

// 加载数据
async function loadData() {
  // CUSTOM 类型必须配置 entityCode 或 refEntityId
  if (
    props.entityType === 'CUSTOM'
    && !effectiveEntityCode.value
    && !effectiveRefEntityId.value
  ) {
    ElMessage.warning('该实体引用字段未配置目标实体，请先配置')
    return
  }

  loading.value = true
  try {
    const params = new URLSearchParams({
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    if (searchKeyword.value) {
      params.append('keyword', searchKeyword.value)
    }
    if (props.entityType === 'CUSTOM') {
      if (effectiveEntityCode.value) {
        params.append('entityCode', effectiveEntityCode.value)
      } else if (effectiveRefEntityId.value) {
        params.append('refEntityId', effectiveRefEntityId.value)
      }
    }
    
    // 如果配置了自定义接口，使用接口获取数据
    let url = `/api/entity-selector/${props.entityType}?${params}`
    if (props.apiUrl) {
      url = `${props.apiUrl}?${params}`
    }
    
    const res = await fetch(url, {
      headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
    }).then(r => r.json())
    
    if (res.code === 200) {
      tableData.value = res.data.records || []
      total.value = res.data.total || 0
      await restoreCurrentPageSelection()
    } else {
      ElMessage.error(res.message || '加载数据失败')
    }
  } catch (e) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

// 搜索
function handleSearch() {
  pageNum.value = 1
  loadData()
}

// 分页
function handleSizeChange(val) {
  pageSize.value = val
  loadData()
}

function handleCurrentChange(val) {
  pageNum.value = val
  loadData()
}

// 选择变化（多选）
function handleSelectionChange(rows) {
  if (restoringPageSelection.value) return
  selectedRows.value = reconcileRecordPageSelection(
    selectedRows.value,
    tableData.value,
    rows
  )
}

// 点击行（单选）
function handleRowClick(row) {
  if (props.multiple) {
    tableRef.value?.toggleRowSelection(row)
    return
  }
  selectRow(row)
}

// 选择单行
function selectRow(row) {
  selectedData.value = row
  emit('update:modelValue', selectionValue(row))
  emit('change', row)
  dialogVisible.value = false
}

// 确认选择（多选）
function confirmSelection() {
  selectedRows.value = normalizeRecordSelection(selectedRows.value)
  const values = recordSelectionValues(selectedRows.value, props.valueKey)
  selectedList.value = selectedRows.value.map(item => ({ ...item }))
  emit('update:modelValue', values)
  emit('change', selectedRows.value)
  dialogVisible.value = false
}

// 清除选择（单选）
function clearSelection() {
  selectedData.value = null
  emit('update:modelValue', null)
  emit('change', null)
}

// 移除选择（多选）
function removeSelection(item) {
  selectedList.value = removeRecordSelection(selectedList.value, item)
  const values = recordSelectionValues(selectedList.value, props.valueKey)
  emit('update:modelValue', values)
  emit('change', selectedList.value)
}

function selectionValue(item) {
  if (!item) return null
  const value = item[props.valueKey]
  return value == null || value === '' ? null : String(value)
}

async function restoreCurrentPageSelection() {
  if (!props.multiple || !tableRef.value) return
  restoringPageSelection.value = true
  await nextTick()
  tableRef.value.clearSelection()
  const selectedIds = new Set(recordSelectionIds(selectedRows.value))
  tableData.value.forEach(row => {
    if (selectedIds.has(String(row.id))) {
      tableRef.value.toggleRowSelection(row, true)
    }
  })
  await nextTick()
  restoringPageSelection.value = false
}

async function removeDraftSelection(item) {
  selectedRows.value = removeRecordSelection(selectedRows.value, item)
  await restoreCurrentPageSelection()
}

async function clearDraftSelection() {
  selectedRows.value = []
  await restoreCurrentPageSelection()
}

// 获取实体类型标签
function getEntityTypeLabel(type) {
  const map = {
    'CUSTOM': '实体',
    'USER': '用户',
    'DEPT': '部门',
    'ROLE': '角色',
    'GROUP': '用户组'
  }
  return map[type] || type
}

function getEntityTypeTag(type) {
  const map = {
    'CUSTOM': '',
    'USER': 'primary',
    'DEPT': 'success',
    'ROLE': 'warning',
    'GROUP': 'info'
  }
  return map[type] || ''
}

function getEntityTypeDesc(type) {
  const map = {
    'CUSTOM': '用户自定义业务实体',
    'USER': '系统用户',
    'DEPT': '组织架构部门',
    'ROLE': '系统角色',
    'GROUP': '用户组'
  }
  return map[type] || ''
}

function getItemLabel(item) {
  return item?.name
    || item?.title
    || item?.code
    || item?.dataNo
    || item?.id
    || '未命名记录'
}

function getItemSecondary(item) {
  return item?.dataNo || item?.code || item?.id || ''
}

function getStatusLabel(status) {
  const map = {
    '0': '启用',
    '1': '禁用',
    DRAFT: '草稿',
    PENDING: '审批中',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    COMPLETED: '已完成'
  }
  return map[String(status)] || '未知状态'
}

// 获取状态样式
function getStatusType(status) {
  const map = {
    '0': 'success',
    '1': 'info',
    'DRAFT': 'info',
    'PENDING': 'warning',
    'APPROVED': 'success',
    'REJECTED': 'danger',
    'COMPLETED': 'success'
  }
  return map[status] || ''
}
</script>

<style scoped>
.entity-selector {
  width: 100%;
}

.selector-input {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  padding: 4px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  background: #fff;
  transition: border-color 0.2s;
}

.selector-input:hover {
  border-color: #409eff;
}

.selector-input.multiple {
  min-height: 40px;
  padding: 4px 8px;
}

.placeholder {
  color: #a8abb2;
  font-size: 14px;
}

.selected-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.item-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.clear-icon {
  color: #a8abb2;
  flex: none;
  margin-left: 2px;
  padding: 2px;
}

.clear-icon:hover {
  color: #409eff;
}

.selected-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  flex: 1;
}

.arrow-icon {
  color: #a8abb2;
  margin-left: 8px;
}

.entity-type-bar {
  margin-bottom: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.type-desc {
  color: #606266;
  font-size: 13px;
}

.search-bar {
  margin-bottom: 16px;
}

.selector-layout.is-multiple {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 16px;
}

.selector-body {
  min-width: 0;
}

.selected-records {
  min-width: 0;
  padding-left: 16px;
  border-left: 1px solid var(--el-border-color-lighter);
}

.selected-records-header {
  display: flex;
  min-height: 40px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.selected-records-header > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.selected-records-header span,
.selected-record-item span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.selected-record-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 8px;
}

.selected-records-scrollbar {
  height: 360px;
  min-height: 0;
}

.selected-record-item {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 9px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-fill-color-lighter);
}

.selected-record-item > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.selected-record-item strong,
.selected-record-item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

:deep(.el-tag) {
  margin: 2px;
}

.entity-selector-dialog :deep(.el-dialog__footer) {
  position: relative;
  z-index: 1;
  background: var(--el-bg-color);
}
</style>
