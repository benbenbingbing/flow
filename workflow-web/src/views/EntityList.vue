<template>
  <div class="entity-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>实体管理</span>
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon>新建实体
          </el-button>
        </div>
      </template>

      <el-table :data="entityList" v-loading="loading" stripe>
        <el-table-column prop="entityName" label="实体名称" min-width="150" />
        <el-table-column prop="entityCode" label="实体编码" min-width="120" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="enableProcess" label="启用流程" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.enableProcess" type="success">是</el-tag>
            <el-tag v-else type="info">否</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="processName" label="绑定流程" min-width="150">
          <template #default="{ row }">
            {{ row.processName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleDesign(row)">设计</el-button>
            <el-button
              v-if="row.status !== 'PUBLISHED'"
              link
              type="success"
              @click="handlePublish(row)"
            >
              发布
            </el-button>
            <el-button
              v-else
              link
              type="success"
              @click="handleRepublish(row)"
            >
              重新发布
            </el-button>
            <el-button link type="primary" @click="handleListConfig(row)">列表</el-button>
            <el-button link type="primary" @click="handleForm(row)">表单</el-button>
            <el-dropdown>
              <el-button link type="info">···</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="handleStatusConfig(row)">
                    <el-icon><SetUp /></el-icon>状态配置
                  </el-dropdown-item>
                  <el-dropdown-item @click="handleBindProcess(row)">
                    <el-icon><Link /></el-icon>绑定流程
                  </el-dropdown-item>
                  <el-dropdown-item @click="handleViewHistory(row)">
                    <el-icon><Clock /></el-icon>版本历史
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="handleDelete(row)">
                    <el-icon><Delete /></el-icon>删除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="实体名称" prop="entityName">
          <el-input v-model="formData.entityName" placeholder="请输入实体名称" />
        </el-form-item>
        <el-form-item label="实体编码" prop="entityCode">
          <el-input v-model="formData.entityCode" placeholder="请输入实体编码" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formData.description" type="textarea" rows="3" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>

    <!-- 绑定流程对话框 -->
    <el-dialog v-model="bindDialogVisible" title="绑定流程" width="500px">
      <el-form label-width="100px">
        <el-form-item label="选择流程">
          <el-select v-model="selectedProcessId" placeholder="请选择要绑定的流程" style="width: 100%">
            <el-option
              v-for="process in processList"
              :key="process.id"
              :label="process.processName"
              :value="process.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirmBind" :loading="bindLoading">确定</el-button>
      </template>
    </el-dialog>

    <!-- 状态配置对话框 -->
    <el-dialog v-model="statusDialogVisible" title="实体状态配置" width="800px">
      <div class="status-config-header">
        <span>实体：{{ currentEntity?.entityName }}</span>
        <el-button type="primary" size="small" @click="addStatus">
          <el-icon><Plus /></el-icon>添加状态
        </el-button>
      </div>
      <el-table 
        :data="statusList" 
        border 
        size="small"
        row-key="id"
        class="status-drag-table"
      >
        <el-table-column type="index" width="50" />
        <el-table-column label="拖拽排序" width="80" align="center">
          <template #default>
            <el-icon class="drag-handle"><Rank /></el-icon>
          </template>
        </el-table-column>
        <el-table-column label="状态分类" width="150">
          <template #default="{ row }">
            <el-select v-model="row.statusCategory" placeholder="选择分类" size="small">
              <el-option label="📋 新建流程" value="NEW" />
              <el-option label="⏳ 审批中" value="PROCESSING" />
              <el-option label="✅ 已完成" value="COMPLETED" />
              <el-option label="❌ 终止" value="TERMINATED" />
              <el-option label="↩️ 已撤回" value="WITHDRAWN" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="状态编码" width="120">
          <template #default="{ row }">
            <el-input v-model="row.statusCode" placeholder="如：PENDING" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="状态名称" width="120">
          <template #default="{ row }">
            <el-input v-model="row.statusName" placeholder="如：审批中" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.description" placeholder="状态说明" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="removeStatus($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="statusDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveStatusConfig" :loading="statusLoading">保存</el-button>
      </template>
    </el-dialog>

    <!-- 版本历史对话框 -->
    <el-dialog v-model="historyDialogVisible" title="版本历史" width="900px">
      <div class="version-header">
        <span>实体：{{ currentEntity?.entityName }} ({{ currentEntity?.entityCode }})</span>
        <el-tag v-if="currentEntity?.status === 'PUBLISHED'" type="success">已发布</el-tag>
        <el-tag v-else type="info">草稿</el-tag>
      </div>
      <el-timeline>
        <el-timeline-item
          v-for="(item, index) in versionHistoryList"
          :key="item.id"
          :type="index === 0 ? 'primary' : ''"
          :color="index === 0 ? '#409EFF' : ''"
          :timestamp="formatDate(item.publishedAt)"
        >
          <div class="version-item" :class="{ 'version-clickable': index < versionHistoryList.length - 1 }" @click="viewVersionDiff(item, index)">
            <div class="version-title">
              <span class="version-number">V{{ item.version }}</span>
              <el-tag size="small" :type="item.publishType === 'CREATE' ? 'success' : 'warning'" class="version-type">
                {{ item.publishType === 'CREATE' ? '首次发布' : '结构变更' }}
              </el-tag>
            </div>
            <div class="version-desc">{{ item.versionDescription }}</div>
            <div class="version-meta">
              <span v-if="item.publishedByName">发布人：{{ item.publishedByName }}</span>
              <span v-else-if="item.publishedBy">发布人：{{ item.publishedBy }}</span>
              <span v-if="item.changesDescription" class="changes-desc">变更：{{ item.changesDescription }}</span>
            </div>
            <div v-if="index < versionHistoryList.length - 1" class="version-tip">
              <el-link type="primary" :underline="false" @click.stop="viewVersionDiff(item, index)">
                <el-icon><View /></el-icon> 点击查看与上一版本的差异
              </el-link>
            </div>
            <div v-if="item.fields && item.fields.length > 0" class="version-fields">
              <el-collapse>
                <el-collapse-item title="查看字段详情" name="1">
                  <el-table :data="item.fields" size="small" border>
                    <el-table-column prop="fieldCode" label="字段编码" width="120" />
                    <el-table-column prop="fieldName" label="字段名称" width="120" />
                    <el-table-column prop="fieldType" label="字段类型" width="100" />
                    <el-table-column label="数据库类型" width="120">
                      <template #default="{ row }">
                        {{ formatDbType(row) }}
                      </template>
                    </el-table-column>
                    <el-table-column prop="isRequired" label="必填" width="60">
                      <template #default="{ row }">
                        <el-tag v-if="row.isRequired" type="danger" size="small">是</el-tag>
                        <span v-else>-</span>
                      </template>
                    </el-table-column>
                    <el-table-column prop="isSystem" label="系统" width="60">
                      <template #default="{ row }">
                        <el-tag v-if="row.isSystem" type="info" size="small">是</el-tag>
                        <span v-else>-</span>
                      </template>
                    </el-table-column>
                  </el-table>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
      <template #footer>
        <el-button @click="historyDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 版本间差异对比对话框 -->
    <el-dialog 
      v-model="versionDiffDialogVisible" 
      title="版本差异对比" 
      width="800px"
    >
      <div v-loading="versionDiffLoading">
        <div v-if="versionDiffData" class="version-diff-content">
          <!-- 版本信息 -->
          <div class="diff-header">
            <div class="version-compare">
              <div class="version-box old">
                <div class="version-label">版本 V{{ versionDiffData.currentVersion }}</div>
                <div class="version-desc">上一版本</div>
              </div>
              <div class="version-arrow">
                <el-icon><ArrowRight /></el-icon>
              </div>
              <div class="version-box new">
                <div class="version-label">版本 V{{ versionDiffData.nextVersion }}</div>
                <div class="version-desc">当前版本</div>
              </div>
            </div>
            <div class="change-summary">
              <el-alert :title="versionDiffData.changeSummary" type="info" :closable="false" />
            </div>
          </div>

          <!-- 新增字段 -->
          <div v-if="versionDiffData.addedFields?.length > 0" class="diff-section">
            <div class="section-title">
              <el-tag type="success">新增</el-tag>
              <span>新增字段（{{ versionDiffData.addedFields.length }}）</span>
            </div>
            <el-table :data="versionDiffData.addedFields" size="small" border>
              <el-table-column prop="fieldCode" label="字段编码" width="120" />
              <el-table-column prop="fieldName" label="字段名称" width="120" />
              <el-table-column prop="fieldType" label="字段类型" width="100" />
              <el-table-column label="数据库类型">
                <template #default="{ row }">
                  {{ formatDbType(row) }}
                </template>
              </el-table-column>
              <el-table-column prop="isRequired" label="必填" width="60">
                <template #default="{ row }">
                  <el-tag v-if="row.isRequired" type="danger" size="small">是</el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 修改字段 -->
          <div v-if="versionDiffData.modifiedFields?.length > 0" class="diff-section">
            <div class="section-title">
              <el-tag type="warning">修改</el-tag>
              <span>修改字段（{{ versionDiffData.modifiedFields.length }}）</span>
            </div>
            <el-table :data="versionDiffData.modifiedFields" size="small" border>
              <el-table-column prop="fieldCode" label="字段编码" width="120" />
              <el-table-column prop="fieldName" label="字段名称" width="120" />
              <el-table-column prop="changeDescription" label="变更内容" min-width="200" />
            </el-table>
          </div>

          <!-- 删除字段 -->
          <div v-if="versionDiffData.removedFields?.length > 0" class="diff-section">
            <div class="section-title">
              <el-tag type="danger">删除</el-tag>
              <span>删除字段（{{ versionDiffData.removedFields.length }}）</span>
            </div>
            <el-table :data="versionDiffData.removedFields" size="small" border>
              <el-table-column prop="fieldCode" label="字段编码" width="120" />
              <el-table-column prop="fieldName" label="字段名称" />
            </el-table>
          </div>

          <!-- 无变更字段 -->
          <div v-if="versionDiffData.unchangedFields?.length > 0" class="diff-section">
            <el-collapse>
              <el-collapse-item :title="`无变更字段（${versionDiffData.unchangedFields.length}）`" name="1">
                <el-table :data="versionDiffData.unchangedFields" size="small" border>
                  <el-table-column prop="fieldCode" label="字段编码" width="120" />
                  <el-table-column prop="fieldName" label="字段名称" />
                </el-table>
              </el-collapse-item>
            </el-collapse>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="versionDiffDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 发布差异预览对话框 -->
    <el-dialog 
      v-model="publishDiffDialogVisible" 
      :title="publishDiffData?.isFirstPublish ? '发布预览 - 首次发布' : '发布预览 - 版本升级'" 
      width="800px"
    >
      <div v-loading="publishDiffLoading">
        <div v-if="publishDiffData" class="publish-diff-content">
          <!-- 版本信息 -->
          <div class="diff-header">
            <div class="version-compare">
              <div class="version-box old">
                <div class="version-label">当前版本</div>
                <div class="version-value">V{{ publishDiffData.currentVersion || 0 }}</div>
              </div>
              <div class="version-arrow">
                <el-icon><ArrowRight /></el-icon>
              </div>
              <div class="version-box new">
                <div class="version-label">即将发布</div>
                <div class="version-value">V{{ publishDiffData.nextVersion }}</div>
              </div>
            </div>
            <div class="change-summary">
              <el-alert :title="publishDiffData.changeSummary" type="info" :closable="false" />
            </div>
          </div>

          <!-- 新增字段 -->
          <div v-if="publishDiffData.addedFields?.length > 0" class="diff-section">
            <div class="section-title">
              <el-tag type="success">新增</el-tag>
              <span>新增字段（{{ publishDiffData.addedFields.length }}）</span>
            </div>
            <el-table :data="publishDiffData.addedFields" size="small" border>
              <el-table-column prop="fieldCode" label="字段编码" width="120" />
              <el-table-column prop="fieldName" label="字段名称" width="120" />
              <el-table-column prop="fieldType" label="字段类型" width="100" />
              <el-table-column label="数据库类型">
                <template #default="{ row }">
                  {{ formatDbType(row) }}
                </template>
              </el-table-column>
              <el-table-column prop="dbColumnName" label="数据库列名" width="150" />
              <el-table-column prop="isRequired" label="必填" width="60">
                <template #default="{ row }">
                  <el-tag v-if="row.isRequired" type="danger" size="small">是</el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 修改字段 -->
          <div v-if="publishDiffData.modifiedFields?.length > 0" class="diff-section">
            <div class="section-title">
              <el-tag type="warning">修改</el-tag>
              <span>修改字段（{{ publishDiffData.modifiedFields.length }}）</span>
            </div>
            <el-table :data="publishDiffData.modifiedFields" size="small" border>
              <el-table-column prop="fieldCode" label="字段编码" width="120" />
              <el-table-column prop="fieldName" label="字段名称" width="120" />
              <el-table-column prop="changeDescription" label="变更内容" min-width="200" />
            </el-table>
          </div>

          <!-- 无变更字段 -->
          <div v-if="publishDiffData.unchangedFields?.length > 0" class="diff-section">
            <el-collapse>
              <el-collapse-item :title="`无变更字段（${publishDiffData.unchangedFields.length}）`" name="1">
                <el-table :data="publishDiffData.unchangedFields" size="small" border>
                  <el-table-column prop="fieldCode" label="字段编码" width="120" />
                  <el-table-column prop="fieldName" label="字段名称" />
                </el-table>
              </el-collapse-item>
            </el-collapse>
          </div>

          <!-- DDL预览 -->
          <div v-if="publishDiffData.pendingDdls?.length > 0" class="diff-section">
            <div class="section-title">
              <el-tag type="info">DDL</el-tag>
              <span>即将执行的SQL</span>
            </div>
            <div class="ddl-preview">
              <pre v-for="(ddl, index) in publishDiffData.pendingDdls" :key="index">{{ ddl }}</pre>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="publishDiffDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmPublish" :loading="publishDiffLoading">
          {{ publishDiffData?.isFirstPublish ? '确认发布' : '确认重新发布' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Rank, View, ArrowRight, ArrowDown, SetUp, Link, Clock, Delete } from '@element-plus/icons-vue'
import { entityApi } from '@/api/entity'
import { entityPublishHistoryApi } from '@/api/entityPublishHistory'
import { entityVersionDiffApi } from '@/api/entityVersionDiff'
import { processApi } from '@/api/process'
import { getEntityStatusList, saveEntityStatusList } from '@/api/entityStatus'
import Sortable from 'sortablejs'

const router = useRouter()
const loading = ref(false)
const entityList = ref([])
const processList = ref([])
const dialogVisible = ref(false)
const bindDialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitting = ref(false)
const bindLoading = ref(false)
const selectedProcessId = ref('')
const currentEntity = ref(null)
const formRef = ref()

const formData = ref({
  entityName: '',
  entityCode: '',
  description: ''
})

const formRules = {
  entityName: [{ required: true, message: '请输入实体名称', trigger: 'blur' }],
  entityCode: [
    { required: true, message: '请输入实体编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '必须以字母开头，只能包含字母、数字、下划线', trigger: 'blur' }
  ]
}

const fetchData = async () => {
  loading.value = true
  try {
    entityList.value = await entityApi.getList()
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const fetchProcessList = async (currentProcessId = null) => {
  try {
    // 获取所有可用于绑定的流程（包括当前已绑定的和未绑定的）
    processList.value = await processApi.getBindableList(currentProcessId)
  } catch (error) {
    console.error(error)
    ElMessage.error('获取流程列表失败')
  }
}

const getStatusType = (status) => {
  const types = { 'DRAFT': 'info', 'PUBLISHED': 'success', 'DISABLED': 'danger' }
  return types[status] || 'info'
}

const getStatusText = (status) => {
  const texts = { 'DRAFT': '草稿', 'PUBLISHED': '已发布', 'DISABLED': '已禁用' }
  return texts[status] || status
}

const handleCreate = () => {
  isEdit.value = false
  dialogTitle.value = '新建实体'
  formData.value = { entityName: '', entityCode: '', description: '' }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value) {
      await entityApi.update(formData.value.id, formData.value)
      ElMessage.success('更新成功')
    } else {
      await entityApi.create(formData.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch (error) {
    console.error(error)
  } finally {
    submitting.value = false
  }
}

const handleDesign = (row) => {
  router.push(`/entity/design/${row.id}`)
}

const handleListConfig = (row) => {
  router.push(`/entity-list-config/${row.id}`)
}

const handleForm = (row) => {
  // 跳转到实体表单列表页面
  router.push(`/entity-form/list-by-entity/${row.id}`)
}

const handleData = (row) => {
  router.push(`/entity/data/${row.entityCode}`)
}

const handleBindProcess = (row) => {
  currentEntity.value = row
  // 如果已绑定流程，默认显示当前绑定的流程ID
  selectedProcessId.value = row.processDefinitionId || ''
  // 获取可绑定的流程列表，包括当前已绑定的
  fetchProcessList(row.processDefinitionId)
  bindDialogVisible.value = true
}

const handleConfirmBind = async () => {
  if (!selectedProcessId.value) {
    ElMessage.warning('请选择流程')
    return
  }
  
  if (!currentEntity.value) {
    ElMessage.warning('实体信息丢失，请重新打开对话框')
    return
  }
  
  // 如果没有变化，直接关闭
  if (selectedProcessId.value === currentEntity.value.processDefinitionId) {
    bindDialogVisible.value = false
    return
  }
  
  bindLoading.value = true
  try {
    await entityApi.bindProcess(currentEntity.value.id, selectedProcessId.value)
    ElMessage.success('绑定成功')
    bindDialogVisible.value = false
    fetchData()
  } catch (error) {
    console.error(error)
    // 显示后端返回的错误信息
    if (error.response?.data?.message) {
      ElMessage.error(error.response.data.message)
    } else if (error.message) {
      ElMessage.error(error.message)
    }
  } finally {
    bindLoading.value = false
  }
}

// ========== 状态配置相关 ==========
const statusDialogVisible = ref(false)
const statusLoading = ref(false)
const statusList = ref([])

// ========== 版本历史相关 ==========
const historyDialogVisible = ref(false)
const versionHistoryList = ref([])
const selectedVersionIndex = ref(null)
const versionDiffDialogVisible = ref(false)
const versionDiffLoading = ref(false)
const versionDiffData = ref(null)

// ========== 发布差异预览相关 ==========
const publishDiffDialogVisible = ref(false)
const publishDiffLoading = ref(false)
const publishDiffData = ref(null)
const publishTargetEntity = ref(null)

// 格式化日期
const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 格式化数据库类型（根据字段类型、长度、精度动态计算）
const formatDbType = (field) => {
  if (field.dbType) return field.dbType
  switch (field.fieldType) {
    case 'STRING':
    case 'SELECT':
    case 'RADIO':
    case 'USER':
    case 'DEPT':
    case 'REFERENCE':
      return `varchar(${field.fieldLength || 200})`
    case 'TEXT':
      return 'text'
    case 'INTEGER':
      return 'int'
    case 'LONG':
      return 'bigint'
    case 'DECIMAL':
      return `decimal(${field.fieldLength || 18},${field.fieldPrecision || 2})`
    case 'DATE':
      return 'date'
    case 'DATETIME':
      return 'datetime'
    case 'BOOLEAN':
      return 'tinyint(1)'
    case 'MULTI_SELECT':
    case 'CHECKBOX':
      return 'varchar(500)'
    case 'FILE':
    case 'IMAGE':
      return 'text'
    case 'MULTI_REFERENCE':
      return 'json'
    default:
      return 'varchar(255)'
  }
}

// 发布实体（先显示差异预览）
const handlePublish = async (row) => {
  publishTargetEntity.value = row
  publishDiffDialogVisible.value = true
  publishDiffLoading.value = true
  
  try {
    const diff = await entityVersionDiffApi.getPendingPublishDiff(row.id)
    publishDiffData.value = diff
  } catch (error) {
    console.error(error)
    ElMessage.error('获取发布预览失败')
  } finally {
    publishDiffLoading.value = false
  }
}

// 重新发布实体（已发布的实体修改字段后再次发布）
const handleRepublish = async (row) => {
  publishTargetEntity.value = row
  publishDiffDialogVisible.value = true
  publishDiffLoading.value = true
  
  try {
    const diff = await entityVersionDiffApi.getPendingPublishDiff(row.id)
    publishDiffData.value = diff
  } catch (error) {
    console.error(error)
    ElMessage.error('获取发布预览失败')
  } finally {
    publishDiffLoading.value = false
  }
}

// 确认发布
const confirmPublish = async () => {
  if (!publishTargetEntity.value) return
  
  publishDiffLoading.value = true
  try {
    await entityApi.publish(publishTargetEntity.value.id)
    ElMessage.success(publishDiffData.value?.isFirstPublish ? '发布成功' : '重新发布成功，表结构已更新')
    publishDiffDialogVisible.value = false
    fetchData()
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '发布失败')
  } finally {
    publishDiffLoading.value = false
  }
}

// 查看版本历史
const handleViewHistory = async (row) => {
  currentEntity.value = row
  historyDialogVisible.value = true
  selectedVersionIndex.value = null
  
  try {
    const res = await entityPublishHistoryApi.getVersionHistory(row.id)
    versionHistoryList.value = res || []
  } catch (error) {
    console.error('加载版本历史失败:', error)
    ElMessage.error('加载版本历史失败')
    versionHistoryList.value = []
  }
}

// 查看版本与上一版本的差异
const viewVersionDiff = async (item, index) => {
  if (index === versionHistoryList.value.length - 1) {
    // 最后一个版本，没有上一个版本可比较
    ElMessage.info('这是第一个版本，无上一版本可比较')
    return
  }
  
  versionDiffLoading.value = true
  versionDiffDialogVisible.value = true
  
  try {
    const diff = await entityVersionDiffApi.compareVersions(
      currentEntity.value.id,
      item.version - 1,
      item.version
    )
    versionDiffData.value = diff
  } catch (error) {
    console.error('获取版本差异失败:', error)
    ElMessage.error('获取版本差异失败')
  } finally {
    versionDiffLoading.value = false
  }
}

const handleStatusConfig = async (row) => {
  currentEntity.value = row
  statusDialogVisible.value = true
  
  try {
    const res = await getEntityStatusList(row.entityCode)
    statusList.value = res || []
    
    // 如果没有配置，添加默认状态
    if (statusList.value.length === 0) {
      statusList.value = [
        { statusCategory: 'NEW', statusCode: 'DRAFT', statusName: '草稿', description: '新建数据' },
        { statusCategory: 'PROCESSING', statusCode: 'PENDING', statusName: '审批中', description: '审批进行中' },
        { statusCategory: 'COMPLETED', statusCode: 'APPROVED', statusName: '已通过', description: '审批已通过' },
        { statusCategory: 'TERMINATED', statusCode: 'TERMINATED', statusName: '已终止', description: '流程已终止' },
        { statusCategory: 'WITHDRAWN', statusCode: 'WITHDRAWN', statusName: '已撤回', description: '发起人撤回流程' }
      ]
    }
    
    // 初始化拖拽排序
    nextTick(() => {
      initSortable()
    })
  } catch (error) {
    console.error('加载状态配置失败:', error)
    ElMessage.error('加载状态配置失败')
  }
}

// 拖拽排序实例
let sortableInstance = null

// 初始化拖拽排序
const initSortable = () => {
  const tableEl = document.querySelector('.status-drag-table .el-table__body-wrapper tbody')
  if (!tableEl) return
  
  // 销毁旧实例
  if (sortableInstance) {
    sortableInstance.destroy()
  }
  
  sortableInstance = new Sortable(tableEl, {
    handle: '.drag-handle',
    animation: 150,
    onEnd: (evt) => {
      const { oldIndex, newIndex } = evt
      if (oldIndex === newIndex) return
      
      // 重新排序数组
      const item = statusList.value.splice(oldIndex, 1)[0]
      statusList.value.splice(newIndex, 0, item)
      
      // 更新排序号
      statusList.value.forEach((status, index) => {
        status.sortOrder = index
      })
      
      console.log('排序更新:', statusList.value)
    }
  })
}

const addStatus = () => {
  statusList.value.push({
    statusCategory: 'PROCESSING',
    statusCode: '',
    statusName: '',
    description: ''
  })
}

const removeStatus = (index) => {
  statusList.value.splice(index, 1)
}

const saveStatusConfig = async () => {
  // 验证数据
  for (const status of statusList.value) {
    if (!status.statusCode || !status.statusName) {
      ElMessage.warning('请填写完整的状态编码和名称')
      return
    }
  }
  
  statusLoading.value = true
  try {
    await saveEntityStatusList(currentEntity.value.entityCode, statusList.value)
    ElMessage.success('保存成功')
    statusDialogVisible.value = false
  } catch (error) {
    console.error('保存状态配置失败:', error)
    ElMessage.error('保存失败')
  } finally {
    statusLoading.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该实体吗？', '提示', { type: 'warning' })
    await entityApi.delete(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      console.error(error)
    }
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.entity-list {
  height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.status-config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
  padding: 10px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.drag-handle {
  cursor: move;
  color: #909399;
  font-size: 16px;
}

.drag-handle:hover {
  color: #409eff;
}

.status-drag-table .sortable-ghost {
  opacity: 0.5;
  background-color: #f5f7fa;
}

/* 版本历史样式 */
.version-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 15px;
  background-color: #f5f7fa;
  border-radius: 4px;
  font-weight: bold;
}

.version-item {
  padding: 10px;
  background-color: #fff;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
}

.version-item.version-clickable {
  cursor: pointer;
  transition: all 0.3s;
}

.version-item.version-clickable:hover {
  border-color: #409eff;
  box-shadow: 0 2px 12px 0 rgba(64, 158, 255, 0.1);
}

.version-tip {
  margin-top: 8px;
  font-size: 12px;
}

.version-diff-content {
  max-height: 500px;
  overflow-y: auto;
}

.version-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.version-number {
  font-size: 18px;
  font-weight: bold;
  color: #409EFF;
}

.version-type {
  font-size: 12px;
}

.version-desc {
  color: #606266;
  margin-bottom: 8px;
  font-size: 14px;
}

.version-meta {
  font-size: 12px;
  color: #909399;
  display: flex;
  gap: 15px;
  flex-wrap: wrap;
}

.changes-desc {
  color: #E6A23C;
}

.version-fields {
  margin-top: 10px;
}

/* 发布差异预览样式 */
.publish-diff-content {
  max-height: 500px;
  overflow-y: auto;
}

.diff-header {
  margin-bottom: 20px;
  padding: 15px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.version-compare {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  margin-bottom: 15px;
}

.version-box {
  text-align: center;
  padding: 15px 30px;
  border-radius: 8px;
  min-width: 100px;
}

.version-box.old {
  background-color: #f4f4f5;
  border: 2px solid #e4e7ed;
}

.version-box.new {
  background-color: #ecf5ff;
  border: 2px solid #409eff;
}

.version-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 5px;
}

.version-value {
  font-size: 24px;
  font-weight: bold;
}

.version-box.old .version-value {
  color: #606266;
}

.version-box.new .version-value {
  color: #409eff;
}

.version-arrow {
  font-size: 24px;
  color: #909399;
}

.diff-section {
  margin-bottom: 20px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-weight: bold;
}

.ddl-preview {
  background-color: #f5f7fa;
  border-radius: 4px;
  padding: 10px;
}

.ddl-preview pre {
  margin: 0;
  padding: 8px;
  background-color: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.ddl-preview pre:not(:last-child) {
  margin-bottom: 8px;
}
</style>
