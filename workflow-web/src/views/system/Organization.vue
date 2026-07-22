<template>
  <div class="organization-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>组织部门管理</span>
          <div class="header-actions">
            <el-radio-group v-model="filterType" size="small" @change="handleFilterChange">
              <el-radio-button label="">全部</el-radio-button>
              <el-radio-button label="org">组织</el-radio-button>
              <el-radio-button label="dept">部门</el-radio-button>
            </el-radio-group>
            <el-button type="primary" size="small" @click="handleAdd">
              <el-icon><Plus /></el-icon>新增
            </el-button>
          </div>
        </div>
      </template>
      
      <!-- 组织部门树形表格 -->
      <el-table
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
        <el-form-item label="上级" v-if="!isEdit || form.parentId !== '0'">
          <el-tree-select
            v-model="form.parentId"
            :data="orgTree"
            :props="{ label: 'orgName', value: 'id' }"
            placeholder="请选择上级（不选为顶级）"
            clearable
            check-strictly
            :render-after-expand="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio-button label="org">组织</el-radio-button>
            <el-radio-button label="dept">部门</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="编码" prop="orgCode">
          <el-input v-model="form.orgCode" placeholder="请输入编码" />
        </el-form-item>
        <el-form-item label="名称" prop="orgName">
          <el-input v-model="form.orgName" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select-v2
            v-model="form.leaderId"
            :options="userOptions"
            placeholder="请选择负责人"
            filterable
            clearable
            style="width: 100%"
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
            <el-radio label="0">启用</el-radio>
            <el-radio label="1">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, OfficeBuilding, House } from '@element-plus/icons-vue'
import request from '@/utils/request'

const loading = ref(false)
const orgTree = ref([])
const filterType = ref('')
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)
const userOptions = ref([])

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
  orgCode: [
    { required: true, message: '请输入编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]+$/, message: '编码只能包含字母、数字、下划线和横线', trigger: 'blur' }
  ],
  orgName: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

// 加载组织部门树
async function loadOrgTree() {
  loading.value = true
  try {
    const res = await request.get('/system/org/tree', { params: { type: filterType.value } })
    if (res && Array.isArray(res)) {
      orgTree.value = res
    }
  } catch (e) {
    console.error('加载组织部门失败:', e)
    ElMessage.error('加载组织部门失败')
  } finally {
    loading.value = false
  }
}

// 加载用户列表
async function loadUsers() {
  try {
    const res = await request.get('/system/user/list')
    if (res && Array.isArray(res)) {
      userOptions.value = res.map(user => ({
        label: `${user.nickname || user.username} (${user.username})`,
        value: user.id
      }))
    }
  } catch (e) {
    console.error('加载用户列表失败:', e)
  }
}

function handleFilterChange() {
  loadOrgTree()
}

function handleAdd() {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

function handleAddChild(row) {
  isEdit.value = false
  resetForm()
  form.parentId = row.id
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  resetForm()
  Object.assign(form, row)
  dialogVisible.value = true
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    if (isEdit.value) {
      await request.post(`/system/org/${form.id}`, form)
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
  ElMessageBox.confirm(`确定删除组织部门 "${row.orgName}" 吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await request.post(`/system/org/${row.id}`)
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
  loadUsers()
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
</style>
