<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑报表' : '新建报表'"
    width="1000px"
    :close-on-click-modal="false"
    top="5vh"
  >
    <el-tabs v-model="activeTab" type="border-card">
      <!-- 基础配置 -->
      <el-tab-pane label="基础配置" name="basic">
        <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="报表名称" prop="reportName">
                <el-input v-model="form.reportName" placeholder="请输入报表名称" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="报表编码" prop="reportCode">
                <el-input v-model="form.reportCode" placeholder="请输入报表编码" :disabled="isEdit" />
              </el-form-item>
            </el-col>
          </el-row>
          
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="报表类型" prop="reportType">
                <el-select v-model="form.reportType" placeholder="选择报表类型" style="width: 100%">
                  <el-option label="表格报表" value="TABLE" />
                  <el-option label="图表报表" value="CHART" />
                  <el-option label="大屏报表" value="DASHBOARD" />
                  <el-option label="打印报表" value="PRINT" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="报表分类" prop="categoryId">
                <el-select v-model="form.categoryId" placeholder="选择分类" style="width: 100%">
                  <el-option
                    v-for="cat in categories"
                    :key="cat.id"
                    :label="cat.categoryName"
                    :value="cat.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-tab-pane>
      
      <!-- 数据集配置 -->
      <el-tab-pane label="数据集" name="dataset">
        <div class="dataset-config">
          <el-button type="primary" @click="handleAddDataset">添加数据集</el-button>
          
          <el-table :data="datasets" border class="dataset-table">
            <el-table-column type="index" width="50" />
            <el-table-column label="数据集编码" prop="datasetCode" min-width="120" />
            <el-table-column label="数据集名称" prop="datasetName" min-width="120" />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">
                <el-tag>{{ row.datasetType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="{ $index }">
                <el-button link type="primary" @click="handleEditDataset($index)">编辑</el-button>
                <el-button link type="danger" @click="handleDeleteDataset($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
      
      <!-- 布局配置 -->
      <el-tab-pane label="布局配置" name="layout">
        <el-form label-width="100px">
          <el-form-item label="报表标题">
            <el-input v-model="layoutConfig.title" placeholder="报表标题" />
          </el-form-item>
          <el-form-item label="显示表头">
            <el-switch v-model="layoutConfig.showHeader" />
          </el-form-item>
          <el-form-item label="显示序号">
            <el-switch v-model="layoutConfig.showIndex" />
          </el-form-item>
          <el-form-item label="分页大小">
            <el-input-number v-model="layoutConfig.pageSize" :min="10" :max="1000" :step="10" />
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
    
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>
    
    <!-- 数据集编辑弹窗 -->
    <el-dialog
      v-model="datasetDialogVisible"
      title="数据集配置"
      width="700px"
      append-to-body
    >
      <el-form :model="currentDataset" label-width="100px">
        <el-form-item label="数据集编码">
          <el-input v-model="currentDataset.datasetCode" placeholder="如: ds1" />
        </el-form-item>
        <el-form-item label="数据集名称">
          <el-input v-model="currentDataset.datasetName" placeholder="数据集名称" />
        </el-form-item>
        <el-form-item label="数据集类型">
          <el-radio-group v-model="currentDataset.datasetType">
            <el-radio label="SQL">SQL查询</el-radio>
            <el-radio label="ENTITY">实体查询</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="SQL语句" v-if="currentDataset.datasetType === 'SQL'">
          <el-input
            v-model="sqlConfig.sql"
            type="textarea"
            rows="6"
            placeholder="输入SQL查询语句，支持 ${参数名} 作为参数占位符"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleTestSql">测试SQL</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="datasetDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveDataset">确定</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { saveReport, getReportConfig, getReportCategories, executeSql } from '@/api/report-engine'

const props = defineProps({
  modelValue: Boolean,
  reportId: String
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const isEdit = computed(() => !!props.reportId)

const formRef = ref()
const submitting = ref(false)
const activeTab = ref('basic')
const categories = ref([])
const datasets = ref([])
const datasetDialogVisible = ref(false)
const currentDatasetIndex = ref(-1)
const currentDataset = ref({
  datasetCode: '',
  datasetName: '',
  datasetType: 'SQL',
  sourceConfig: ''
})
const sqlConfig = ref({ sql: '' })

const form = reactive({
  reportName: '',
  reportCode: '',
  reportType: 'TABLE',
  categoryId: ''
})

const layoutConfig = reactive({
  title: '',
  showHeader: true,
  showIndex: true,
  pageSize: 50
})

const rules = {
  reportName: [{ required: true, message: '请输入报表名称', trigger: 'blur' }],
  reportType: [{ required: true, message: '请选择报表类型', trigger: 'change' }]
}

// 加载分类
const loadCategories = async () => {
  try {
    const res = await getReportCategories()
    categories.value = res || []
  } catch (error) {
    console.error('加载分类失败:', error)
  }
}

// 加载报表详情
const loadReportDetail = async () => {
  if (!props.reportId) {
    resetForm()
    return
  }
  
  try {
    const res = await getReportConfig(props.reportId)
    const report = res.report
    Object.assign(form, report)
    datasets.value = res.datasets || []
    if (report.layoutConfig) {
      Object.assign(layoutConfig, JSON.parse(report.layoutConfig))
    }
  } catch (error) {
    console.error('加载报表详情失败:', error)
    ElMessage.error('加载报表详情失败')
  }
}

const resetForm = () => {
  form.reportName = ''
  form.reportCode = ''
  form.reportType = 'TABLE'
  form.categoryId = ''
  datasets.value = []
}

// 添加数据集
const handleAddDataset = () => {
  currentDatasetIndex.value = -1
  currentDataset.value = {
    datasetCode: `ds${datasets.value.length + 1}`,
    datasetName: '',
    datasetType: 'SQL',
    sourceConfig: ''
  }
  sqlConfig.value = { sql: '' }
  datasetDialogVisible.value = true
}

// 编辑数据集
const handleEditDataset = (index) => {
  currentDatasetIndex.value = index
  currentDataset.value = { ...datasets.value[index] }
  try {
    sqlConfig.value = JSON.parse(currentDataset.value.sourceConfig || '{}')
  } catch {
    sqlConfig.value = { sql: '' }
  }
  datasetDialogVisible.value = true
}

// 删除数据集
const handleDeleteDataset = (index) => {
  datasets.value.splice(index, 1)
}

// 保存数据集
const handleSaveDataset = () => {
  currentDataset.value.sourceConfig = JSON.stringify(sqlConfig.value)
  
  if (currentDatasetIndex.value >= 0) {
    datasets.value[currentDatasetIndex.value] = { ...currentDataset.value }
  } else {
    datasets.value.push({ ...currentDataset.value })
  }
  
  datasetDialogVisible.value = false
}

// 测试SQL
const handleTestSql = async () => {
  try {
    const res = await executeSql({ sql: sqlConfig.value.sql, params: {} })
    ElMessage.success(`查询成功，返回 ${res.length} 条数据`)
  } catch (error) {
    ElMessage.error(error.message || 'SQL执行失败')
  }
}

// 提交
const handleSubmit = async () => {
  await formRef.value.validate()
  
  submitting.value = true
  try {
    await saveReport({
      report: { ...form, layoutConfig: JSON.stringify(layoutConfig) },
      datasets: datasets.value
    })
    
    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    emit('success')
    visible.value = false
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

watch(() => props.modelValue, (val) => {
  if (val) {
    loadCategories()
    loadReportDetail()
  }
})
</script>

<style scoped lang="scss">
.dataset-config {
  .dataset-table {
    margin-top: 15px;
  }
}
</style>
