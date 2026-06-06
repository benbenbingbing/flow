<template>
  <div class="entity-selector">
    <!-- 单选模式 -->
    <template v-if="!multiple">
      <div class="selector-input" @click="openSelector">
        <div v-if="selectedData" class="selected-item">
          <el-tag size="small" :type="getEntityTypeTag(selectedData.entityType)">
            {{ getEntityTypeLabel(selectedData.entityType) }}
          </el-tag>
          <span class="item-name">{{ selectedData.name || selectedData.code || selectedData.id }}</span>
          <el-icon class="clear-icon" @click.stop="clearSelection"><Close /></el-icon>
        </div>
        <div v-else class="placeholder">{{ placeholder }}</div>
        <el-icon class="arrow-icon"><ArrowDown /></el-icon>
      </div>
    </template>
    
    <!-- 多选模式 -->
    <template v-else>
      <div class="selector-input multiple" @click="openSelector">
        <div v-if="selectedList.length > 0" class="selected-list">
          <el-tag
            v-for="item in selectedList"
            :key="item.id"
            closable
            size="small"
            :type="getEntityTypeTag(item.entityType)"
            @close="removeSelection(item)"
          >
            {{ item.name || item.code || item.id }}
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
      width="800px"
      :close-on-click-modal="false"
    >
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
            placeholder="搜索名称或编码"
            clearable
            @keyup.enter="handleSearch"
          >
            <template #append>
              <el-button @click="handleSearch">
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
          style="width: 100%"
          @selection-change="handleSelectionChange"
          @row-click="handleRowClick"
        >
          <el-table-column v-if="multiple" type="selection" width="55" />
          <el-table-column prop="name" label="名称" min-width="150" />
          <el-table-column prop="code" label="编码" width="120" />
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
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" v-if="!multiple">
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

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button v-if="multiple" type="primary" @click="confirmSelection">
            确定 ({{ selectedRows.length }})
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, computed } from 'vue'
import { ArrowDown, Close, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

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
  // 关联实体ID（当 entityCode 为空时，用于后端查询 entityCode）
  refEntityId: {
    type: String,
    default: null
  },
  // 数据接口URL（用于定制返回数据范围）
  apiUrl: {
    type: String,
    default: null
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

// 计算属性
const isSystemEntity = computed(() => props.entityType !== 'CUSTOM')
const dialogTitle = computed(() => {
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

// 监听值变化
watch(() => props.modelValue, (val) => {
  if (val) {
    loadSelectedData()
  } else {
    selectedData.value = null
    selectedList.value = []
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
  if (props.entityType === 'CUSTOM' && !props.entityCode && !props.refEntityId) {
    return
  }

  try {
    const ids = props.multiple && Array.isArray(props.modelValue)
      ? props.modelValue.join(',')
      : props.modelValue

    if (!ids) return

    const params = new URLSearchParams({ ids })
    if (props.entityType === 'CUSTOM') {
      if (props.entityCode) {
        params.append('entityCode', props.entityCode)
      } else if (props.refEntityId) {
        params.append('refEntityId', props.refEntityId)
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
  dialogVisible.value = true
  pageNum.value = 1
  searchKeyword.value = ''
  selectedRows.value = []
  await loadData()
  
  // 回显已选项
  if (props.multiple && tableRef.value) {
    // 防御性提取已选 id（兼容对象数组）
    const selectedIds = Array.isArray(props.modelValue)
      ? props.modelValue.map(v => v && typeof v === 'object' ? v.id : v)
      : []
    setTimeout(() => {
      tableData.value.forEach(row => {
        const isSelected = props.multiple
          ? selectedIds.includes(row.id)
          : props.modelValue === row.id
        if (isSelected) {
          tableRef.value.toggleRowSelection(row, true)
        }
      })
    }, 100)
  }
}

// 加载数据
async function loadData() {
  // CUSTOM 类型必须配置 entityCode 或 refEntityId
  if (props.entityType === 'CUSTOM' && !props.entityCode && !props.refEntityId) {
    ElMessage.warning('该实体引用字段未配置关联实体，请先配置')
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
      if (props.entityCode) {
        params.append('entityCode', props.entityCode)
      } else if (props.refEntityId) {
        params.append('refEntityId', props.refEntityId)
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
  selectedRows.value = rows
}

// 点击行（单选）
function handleRowClick(row) {
  if (!props.multiple) {
    selectRow(row)
  }
}

// 选择单行
function selectRow(row) {
  selectedData.value = row
  emit('update:modelValue', row.id)
  emit('change', row)
  dialogVisible.value = false
}

// 确认选择（多选）
function confirmSelection() {
  const ids = selectedRows.value.map(r => r.id)
  selectedList.value = [...selectedRows.value]
  emit('update:modelValue', ids)
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
  selectedList.value = selectedList.value.filter(i => i.id !== item.id)
  const ids = selectedList.value.map(i => i.id)
  emit('update:modelValue', ids)
  emit('change', selectedList.value)
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

// 获取状态样式
function getStatusType(status) {
  const map = {
    '0': 'info',
    '1': 'success',
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
  cursor: pointer;
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

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

:deep(.el-tag) {
  margin: 2px;
}
</style>
