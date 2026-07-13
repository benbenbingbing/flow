<template>
  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="75%" class="entity-form-dialog" top="3vh">
    <el-tabs v-if="hasTabSubForms" v-model="activeTab" type="border-card" class="form-dialog-tabs">
      <el-tab-pane label="基本信息" name="basic">
        <EntityDataFormFields
          ref="basicFormFieldsRef"
          v-model:formData="formData"
          :entityCode="entityCode"
          :entityDefinition="entityDefinition"
          :entityFields="entityFields"
          :defaultForm="defaultForm"
          :isEdit="isEdit"
          :showStartProcess="!isEdit"
          :noInternalTabs="true"
        />
      </el-tab-pane>
      <el-tab-pane
        v-for="(field, idx) in tabSubForms"
        :key="'subform-' + idx + '-' + (field.id || field.fieldCode || field.fieldKey || '')"
        :label="field.fieldLabel || field.fieldName"
        :name="'subform_' + idx"
      >
        <FormFieldRendererLinkage
          :field="field"
          v-model="formData.data[getFieldKey(field)]"
        />
      </el-tab-pane>
      <el-tab-pane v-if="hasProcessInfo" label="流程图" name="diagram">
        <EntityApprovalDiagram
          :bpmnXml="bpmnXml"
          :progressData="progressData"
          :processInstanceId="processInstanceId"
        />
      </el-tab-pane>
      <el-tab-pane v-if="hasProcessInfo" label="审批历史" name="history">
        <EntityApprovalHistory :processHistory="processHistory" />
      </el-tab-pane>
    </el-tabs>

    <EntityDataFormFields
      v-else
      ref="formFieldsRef"
      v-model:formData="formData"
      :entityCode="entityCode"
      :entityDefinition="entityDefinition"
      :entityFields="entityFields"
      :defaultForm="defaultForm"
      :isEdit="isEdit"
      :showStartProcess="!isEdit"
    />

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="submitLoading">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { entityDataApi } from '@/api/entity'
import { useUserStore } from '@/stores/user'
import { executeFormInitializer } from '@/utils/formInitializer'
import { useProcessDetail } from '@/composables/useProcessDetail'
import { getFieldKey } from '@/shared/form-runtime'
import FormFieldRendererLinkage from '@/components/FormFieldRendererLinkage.vue'
import EntityDataFormFields from './EntityDataFormFields.vue'
import EntityApprovalHistory from './approval/EntityApprovalHistory.vue'
import EntityApprovalDiagram from './approval/EntityApprovalDiagram.vue'

const props = defineProps<{
  entityCode: string
  entityDefinition: any
  entityFields: any[]
  defaultForm: any
}>()

const emit = defineEmits<{
  success: []
}>()

const route = useRoute()
const userStore = useUserStore()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formFieldsRef = ref<InstanceType<typeof EntityDataFormFields>>()
const basicFormFieldsRef = ref<InstanceType<typeof EntityDataFormFields>>()
const isEdit = ref(false)
const activeTab = ref('form')
const processInstanceId = ref('')
const currentProcessStatus = ref('')
const currentProcessName = ref('')

const formData = reactive({
  id: '',
  name: '',
  data: {} as Record<string, any>,
  startProcess: false
})

const hasProcessInfo = computed(() => !!processInstanceId.value)

const {
  bpmnXml,
  progressData,
  processHistory,
  isTabSubForm,
  loadProcessDetail
} = useProcessDetail()

const formFields = computed(() => props.entityFields.filter((f: any) => !f.isSystem))
const tabSubForms = computed(() => {
  const fields = props.defaultForm?.fields || formFields.value
  return fields.filter((f: any) => isTabSubForm(f))
})
const hasTabSubForms = computed(() => tabSubForms.value.length > 0)

// 切换到流程图 tab 时重新触发 BPMN 渲染，避免隐藏 tab 中画布尺寸为 0
watch(activeTab, (newVal) => {
  if (newVal === 'diagram' && bpmnXml.value && progressData.value) {
    nextTick(() => {
      const tempXml = bpmnXml.value
      bpmnXml.value = ''
      nextTick(() => {
        bpmnXml.value = tempXml
      })
    })
  }
})

// 重置表单
const resetForm = () => {
  formData.id = ''
  formData.name = ''
  formData.data = {}
  formData.startProcess = false
  
  const fields = props.entityFields.filter((f: any) => !f.isSystem)
  fields.forEach((field: any) => {
    if (field.defaultValue) {
      try {
        formData.data[field.fieldCode] = JSON.parse(field.defaultValue)
      } catch {
        formData.data[field.fieldCode] = field.defaultValue
      }
    } else {
      formData.data[field.fieldCode] = ''
    }
  })
}

// 新增
const openCreate = async () => {
  isEdit.value = false
  processInstanceId.value = ''
  currentProcessStatus.value = ''
  currentProcessName.value = ''
  activeTab.value = hasTabSubForms.value ? 'basic' : 'form'
  resetForm()
  dialogTitle.value = '新增数据'

  if (props.defaultForm?.initConfig) {
    try {
      const initData = await executeFormInitializer(props.defaultForm.initConfig, {
        entityCode: props.entityCode,
        entityDefinition: props.entityDefinition,
        routeQuery: route.query,
        userStore: userStore
      })
      if (initData && typeof initData === 'object') {
        Object.entries(initData).forEach(([key, value]) => {
          formData.data[key] = value
        })
      }
    } catch (e) {
      console.warn('表单初始化失败:', e)
    }
  }

  dialogVisible.value = true
  nextTick(() => {
    formFieldsRef.value?.refreshLinkage()
  })
}

// 编辑
const openEdit = async (row: any) => {
  isEdit.value = true
  const detail = await entityDataApi.getDetail(props.entityCode, row.id).catch(() => row)
  formData.id = detail.id
  formData.name = detail.name
  formData.data = { ...(detail.data || {}) }
  if (detail.name != null) formData.data.name = detail.name
  if (detail.code != null) formData.data.code = detail.code
  if (detail.status != null) formData.data.status = detail.status
  if (detail.dataNo != null) formData.data.dataNo = detail.dataNo
  if (detail.title != null) formData.data.title = detail.title
  if (detail.deptId != null) formData.data.deptId = detail.deptId
  if (detail.submitterId != null) formData.data.submitterId = detail.submitterId
  if (detail.submitterName != null) formData.data.submitterName = detail.submitterName
  if (detail.processInstanceId != null) formData.data.processInstanceId = detail.processInstanceId
  if (detail.currentTaskId != null) formData.data.currentTaskId = detail.currentTaskId
  if (detail.currentTaskName != null) formData.data.currentTaskName = detail.currentTaskName
  if (detail.currentTaskAssignee != null) formData.data.currentTaskAssignee = detail.currentTaskAssignee

  processInstanceId.value = detail.processInstanceId || ''
  if (processInstanceId.value) {
    await loadProcessDetail(processInstanceId.value, {
      onLoad: (progressRes: any) => {
        currentProcessStatus.value = progressRes.status || ''
        currentProcessName.value = progressRes.processName || ''
      }
    })
  } else {
    currentProcessStatus.value = ''
    currentProcessName.value = ''
  }

  activeTab.value = hasTabSubForms.value ? 'basic' : 'form'

  dialogTitle.value = '编辑数据'
  dialogVisible.value = true
  nextTick(() => {
    if (hasTabSubForms.value) {
      basicFormFieldsRef.value?.refreshLinkage()
    } else {
      formFieldsRef.value?.refreshLinkage()
    }
  })
}

// 提交
const handleSubmit = async () => {
  const valid = await (hasTabSubForms.value
    ? basicFormFieldsRef.value?.validate()
    : formFieldsRef.value?.validate())
  if (!valid) return

  submitLoading.value = true
  try {
    const data = {
      entityCode: props.entityCode,
      id: formData.id,
      name: formData.data?.name || formData.name,
      data: formData.data,
      startProcess: formData.startProcess
    }

    if (formData.id) {
      await entityDataApi.update(props.entityCode, formData.id, data, data.startProcess)
      ElMessage.success('更新成功')
    } else {
      await entityDataApi.save(data, data.startProcess)
      ElMessage.success('创建成功')
    }

    dialogVisible.value = false
    emit('success')
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    submitLoading.value = false
  }
}

defineExpose({
  openCreate,
  openEdit
})
</script>

<style scoped lang="scss">
.entity-form-dialog {
  margin-top: 15px !important;
  margin-bottom: 15px !important;
  height: 94vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}
.entity-form-dialog :deep(.el-dialog__body) {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.form-dialog-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}
.form-dialog-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}
.form-dialog-tabs :deep(.el-tab-pane) {
  height: 100%;
}
</style>
