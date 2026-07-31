<template>
  <div class="organization-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>组织部门管理</span>
          <div class="header-actions">
            <el-radio-group v-model="filterType" size="small" @change="handleFilterChange">
              <el-radio-button value="">全部</el-radio-button>
              <el-radio-button value="org">组织</el-radio-button>
              <el-radio-button value="dept">部门</el-radio-button>
            </el-radio-group>
            <el-button size="small" @click="handleAdd('org')">
              <el-icon><Plus /></el-icon>新增顶级组织
            </el-button>
            <el-button type="primary" size="small" @click="handleAdd('dept')">
              <el-icon><Plus /></el-icon>新增部门
            </el-button>
          </div>
        </div>
      </template>

      <el-alert
        title="层级规则：组织可位于顶级或其他组织下；部门必须隶属于组织或上级部门；组织不能放在部门下。"
        type="info"
        :closable="false"
        show-icon
        class="hierarchy-rule"
      />

      <PageState
        v-if="loadError"
        type="error"
        title="组织部门加载失败"
        :description="loadError"
        retryable
        @retry="loadOrgTree"
      />
      
      <!-- 组织部门树形表格 -->
      <el-table
        v-else
        :data="orgTree"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        v-loading="loading"
      >
        <el-table-column prop="orgName" label="名称" min-width="200">
          <template #default="{ row }">
            <el-icon v-if="row.type === 'org'"><OfficeBuilding /></el-icon>
            <el-icon v-else><House /></el-icon>
            <span style="margin-left: 5px">{{ row.orgName }}</span>
            <div class="org-path">{{ row.displayPath }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="orgCode" label="编码" width="150" />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.type === 'org'" type="primary">组织</el-tag>
            <el-tag v-else type="success">部门</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="leaderName" label="负责人" width="120" />
        <el-table-column prop="phone" label="联系电话" width="150" />
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.status === '0'" type="success">启用</el-tag>
            <el-tag v-else type="danger">禁用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleAddChild(row)">
              新增下级
            </el-button>
            <el-button type="primary" link size="small" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    
    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑组织部门' : '新增组织部门'"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="上级" prop="parentId" v-if="!isEdit || form.parentId !== '0'">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'orgName', value: 'id' }"
            placeholder="请选择上级（不选为顶级）"
            clearable
            check-strictly
            :render-after-expand="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type" @change="handleTypeChange">
            <el-radio-button value="org" :disabled="parentIsDepartment">组织</el-radio-button>
            <el-radio-button value="dept">部门</el-radio-button>
          </el-radio-group>
          <div class="field-help">{{ hierarchyHelp }}</div>
        </el-form-item>
        <el-form-item label="编码" prop="orgCode">
          <el-input v-model="form.orgCode" placeholder="请输入编码" />
        </el-form-item>
        <el-form-item label="名称" prop="orgName">
          <el-input v-model="form.orgName" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="负责人">
          <UserSelector
            v-model="form.leaderId"
            value-key="id"
            placeholder="请选择负责人"
            title="选择负责人"
          />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" placeholder="请输入地址" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="0">启用</el-radio>
            <el-radio value="1">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitLoading">
          {{ isEdit ? '保存组织部门' : form.type === 'org' ? '创建组织' : '创建部门' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, OfficeBuilding, House } from '@element-plus/icons-vue'
import request from '@/utils/request'
import PageState from '@/components/PageState.vue'
import UserSelector from '@/components/UserSelector.vue'

const loading = ref(false)
const loadError = ref('')
const orgTree = ref([])
const filterType = ref('')
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)

const flatOrganizations = computed(() => {
  const result = []
  const walk = (nodes) => {
    ;(nodes || []).forEach(node => {
      result.push(node)
      walk(node.children)
    })
  }
  walk(orgTree.value)
  return result
})

const parentIsDepartment = computed(() =>
  flatOrganizations.value.find(item => item.id === form.parentId)?.type === 'dept'
)

const parentOptions = computed(() => {
  const current = flatOrganizations.value.find(item => item.id === form.id)
  const currentPath = current?.path || ''
  const filterNodes = (nodes) => (nodes || []).flatMap(node => {
    if (node.id === form.id || (currentPath && node.path?.startsWith(currentPath))) return []
    if (form.type === 'org' && node.type !== 'org') return []
    return [{ ...node, children: filterNodes(node.children) }]
  })
  return filterNodes(orgTree.value)
})

const hierarchyHelp = computed(() => {
  if (form.type === 'org') return '组织可作为顶级节点，也可隶属于另一个组织。'
  return '部门必须选择上级组织或上级部门，不能创建为顶级节点。'
})

const form = reactive({
  id: '',
  parentId: '0',
  type: 'org',
  orgCode: '',
  orgName: '',
  leaderId: '',
  phone: '',
  email: '',
  address: '',
  sortOrder: 0,
  status: '0',
  description: ''
})

const rules = {
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  parentId: [{
    validator: (_rule, value, callback) => {
      if (form.type === 'dept' && (!value || value === '0')) {
        callback(new Error('部门必须选择上级'))
        return
      }
      callback()
    },
    trigger: 'change'
  }],
  orgCode: [
    { required: true, message: '请输入编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]+$/, message: '编码只能包含字母、数字、下划线和横线', trigger: 'blur' }
  ],
  orgName: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

// 加载组织部门树
async function loadOrgTree() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await request.get('/system/org/tree', { params: { type: filterType.value } })
    if (res && Array.isArray(res)) {
      const decorate = (nodes, ancestors = []) => (nodes || []).map(node => {
        const names = [...ancestors, node.orgName]
        return {
          ...node,
          displayPath: names.join(' / '),
          children: decorate(node.children, names)
        }
      })
      orgTree.value = decorate(res)
    }
  } catch (e) {
    console.error('加载组织部门失败:', e)
    loadError.value = e?.message || '无法读取组织部门树，请重试。'
  } finally {
    loading.value = false
  }
}

function handleFilterChange() {
  loadOrgTree()
}

function handleAdd(type = 'org') {
  isEdit.value = false
  resetForm()
  form.type = type
  form.parentId = type === 'org' ? '0' : ''
  dialogVisible.value = true
}

function handleAddChild(row) {
  isEdit.value = false
  resetForm()
  form.parentId = row.id
  form.type = 'dept'
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  resetForm()
  Object.assign(form, row)
  dialogVisible.value = true
}

function handleTypeChange(type) {
  if (type === 'org' && parentIsDepartment.value) {
    form.parentId = '0'
  }
  if (type === 'dept' && form.parentId === '0') {
    form.parentId = ''
  }
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    if (isEdit.value) {
      await request.post(`/system/org/${form.id}/update`, form)
      ElMessage.success('更新成功')
    } else {
      await request.post('/system/org', form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadOrgTree()
  } catch (e) {
    console.error('提交失败:', e)
    ElMessage.error('提交失败')
  } finally {
    submitLoading.value = false
  }
}

function handleDelete(row) {
  const childCount = row.children?.length || 0
  ElMessageBox.confirm(
    `删除前必须确保没有下级节点和关联用户。当前可见下级 ${childCount} 个。确认删除「${row.orgName}」吗？`,
    '删除组织部门',
    {
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await request.post(`/system/org/${row.id}/delete`)
      ElMessage.success('删除成功')
      loadOrgTree()
    } catch (e) {
      console.error('删除失败:', e)
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

function resetForm() {
  form.id = ''
  form.parentId = '0'
  form.type = 'org'
  form.orgCode = ''
  form.orgName = ''
  form.leaderId = ''
  form.phone = ''
  form.email = ''
  form.address = ''
  form.sortOrder = 0
  form.status = '0'
  form.description = ''
}

onMounted(() => {
  loadOrgTree()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.hierarchy-rule {
  margin-bottom: 14px;
}

.org-path,
.field-help {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.org-path {
  margin-left: 25px;
}

@media (max-width: 760px) {
  .card-header,
  .header-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
