<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑视图' : '新建视图'"
    width="900px"
    :close-on-click-modal="false"
  >
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="视图名称" prop="viewName">
            <el-input v-model="form.viewName" placeholder="请输入视图名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="视图编码" prop="viewCode">
            <el-input v-model="form.viewCode" placeholder="请输入视图编码" :disabled="isEdit" />
          </el-form-item>
        </el-col>
      </el-row>
      
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="视图类型" prop="viewType">
            <el-select v-model="form.viewType" placeholder="选择视图类型" style="width: 100%">
              <el-option label="列表" value="LIST" />
              <el-option label="图表" value="CHART" />
              <el-option label="看板" value="DASHBOARD" />
              <el-option label="详情" value="DETAIL" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="关联实体" prop="entityCode">
            <el-select v-model="form.entityCode" placeholder="选择实体" style="width: 100%" @change="handleEntityChange">
              <el-option
                v-for="entity in entityList"
                :key="entity.entityCode"
                :label="entity.entityName"
                :value="entity.entityCode"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      
      <!-- 字段配置 -->
      <el-divider>字段配置</el-divider>
      <div class="field-config">
        <el-table :data="fieldList" size="small" border>
          <el-table-column type="index" width="40" />
          <el-table-column label="字段编码" prop="fieldCode" min-width="120" />
          <el-table-column label="显示名称" min-width="120">
            <template #default="{ row }">
              <el-input v-model="row.fieldName" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="显示" width="60" align="center">
            <template #default="{ row }">
              <el-checkbox v-model="row.isShow" :true-label="1" :false-label="0" />
            </template>
          </el-table-column>
          <el-table-column label="宽度" width="80">
            <template #default="{ row }">
              <el-input v-model="row.width" size="small" placeholder="如150px" />
            </template>
          </el-table-column>
          <el-table-column label="对齐" width="90">
            <template #default="{ row }">
              <el-select v-model="row.align" size="small">
                <el-option label="左" value="left" />
                <el-option label="中" value="center" />
                <el-option label="右" value="right" />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-form>
    
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { saveView, getViewConfig } from '@/api/view-engine'
import { entityApi } from '@/api/entity'

const props = defineProps({
  modelValue: Boolean,
  viewId: String
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const isEdit = computed(() => !!props.viewId)

const formRef = ref()
const submitting = ref(false)
const entityList = ref([])
const fieldList = ref([])

const form = reactive({
  viewName: '',
  viewCode: '',
  viewType: 'LIST',
  entityCode: '',
  dataSourceType: 'ENTITY'
})

const rules = {
  viewName: [{ required: true, message: '请输入视图名称', trigger: 'blur' }],
  viewType: [{ required: true, message: '请选择视图类型', trigger: 'change' }],
  entityCode: [{ required: true, message: '请选择关联实体', trigger: 'change' }]
}

// 加载实体列表
const loadEntityList = async () => {
  try {
    const res = await entityApi.getList()
    entityList.value = res || []
  } catch (error) {
    console.error('加载实体列表失败:', error)
  }
}

// 加载视图详情
const loadViewDetail = async () => {
  if (!props.viewId) {
    resetForm()
    return
  }
  
  try {
    const res = await getViewConfig(props.viewId)
    const view = res.view
    Object.assign(form, view)
    fieldList.value = res.fields || []
  } catch (error) {
    console.error('加载视图详情失败:', error)
    ElMessage.error('加载视图详情失败')
  }
}

// 实体变更时加载字段
const handleEntityChange = async (entityCode) => {
  if (!entityCode) {
    fieldList.value = []
    return
  }
  
  try {
    const res = await entityApi.getByCode(entityCode)
    const fields = res.fields || []
    fieldList.value = fields.filter(f => !f.isSystem).map((f, index) => ({
      fieldCode: f.fieldCode,
      fieldName: f.fieldName,
      fieldType: f.fieldType,
      sortOrder: index,
      width: '150px',
      align: 'left',
      isShow: 1,
      isSortable: 0,
      isSearchable: f.isQuery ? 1 : 0,
      formatterType: 'TEXT',
      showInList: 1,
      showInDetail: 1
    }))
  } catch (error) {
    console.error('加载实体字段失败:', error)
  }
}

const resetForm = () => {
  form.viewName = ''
  form.viewCode = ''
  form.viewType = 'LIST'
  form.entityCode = ''
  fieldList.value = []
}

// 提交
const handleSubmit = async () => {
  await formRef.value.validate()
  
  submitting.value = true
  try {
    await saveView({
      view: form,
      fields: fieldList.value,
      queries: [],
      buttons: []
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
    loadEntityList()
    loadViewDetail()
  }
})
</script>

<style scoped lang="scss">
.field-config {
  max-height: 300px;
  overflow-y: auto;
}
</style>
