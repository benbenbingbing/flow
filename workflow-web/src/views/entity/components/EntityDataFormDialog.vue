<template>
  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="75%" class="entity-form-dialog" top="3vh">
    <el-tabs v-if="showOuterTabs" v-model="activeTab" type="border-card" class="form-dialog-tabs">
      <el-tab-pane v-if="showBasicTab" label="基本信息" name="basic">
        <EntityDataFormFields
          ref="basicFormFieldsRef"
          v-model:formData="formData"
          :entityCode="entityCode"
          :entityDefinition="entityDefinition"
          :entityFields="entityFields"
          :defaultForm="defaultForm"
          :isEdit="isEdit"
          :showStartProcess="canStartProcess"
          :noInternalTabs="true"
          :excludedNodeIds="liftedRootNodeIds"
          :dataSourceRuntime="dataSourceRuntime"
          :skipDataSourcePrevalidation="true"
        />
      </el-tab-pane>
      <el-tab-pane
        v-for="(tab, idx) in runtimeNodeTabs"
        :key="tab.name"
        :label="tab.label"
        :name="tab.name"
      >
        <EntityDataFormFields
          :ref="(instance) => setNodeFormFieldsRef(tab.name, instance)"
          v-model:formData="formData"
          :entityCode="entityCode"
          :entityDefinition="entityDefinition"
          :entityFields="entityFields"
          :defaultForm="defaultForm"
          :isEdit="isEdit"
          :showStartProcess="canStartProcess && !showBasicTab && idx === 0"
          :noInternalTabs="true"
          :nodeRootParentId="tab.rootParentId"
          :dataSourceRuntime="dataSourceRuntime"
          :skipDataSourcePrevalidation="true"
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
      <el-tab-pane
        v-if="hasProcessInfo && userStore.isSuperAdmin"
        label="动作执行记录"
        name="actionExecutions"
      >
        <FlowActionExecutionLog
          :process-instance-id="processInstanceId"
          :active="activeTab === 'actionExecutions'"
        />
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
      :showStartProcess="canStartProcess"
      :dataSourceRuntime="dataSourceRuntime"
      :skipDataSourcePrevalidation="true"
    />

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="submitLoading">
        {{ isEdit ? '保存修改' : '创建数据' }}
      </el-button>
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
import {
  createFormDataSourceRuntime,
  getFieldKey,
  isRuntimeFieldVisible,
  resolveRuntimeFormTabLayout
} from '@/shared/form-runtime'
import FormFieldRendererLinkage from '@/components/FormFieldRendererLinkage.vue'
import EntityDataFormFields from './EntityDataFormFields.vue'
import EntityApprovalHistory from './approval/EntityApprovalHistory.vue'
import EntityApprovalDiagram from './approval/EntityApprovalDiagram.vue'
import FlowActionExecutionLog from '@/components/FlowActionExecutionLog.vue'

const props = defineProps<{
  entityCode: string
  entityDefinition: any
  entityFields: any[]
  defaultForm: any
  listKey?: string
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
const nodeFormFieldsRefs = ref<Record<string, InstanceType<typeof EntityDataFormFields>>>({})
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
const canStartProcess = computed(() => !hasProcessInfo.value)
const dataSourceRuntime = createFormDataSourceRuntime({
  entityCode: props.entityCode,
  getRecord: () => formData.data || {},
  getRecordId: () => formData.id,
  getListKey: () => props.listKey,
  getMode: () => isEdit.value ? 'edit' : 'create',
  getForm: () => props.defaultForm,
  getEntityDefinition: () => props.entityDefinition
})

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
  const mode = isEdit.value ? 'edit' : 'create'
  return fields.filter((f: any) => isRuntimeFieldVisible(f, mode) && isTabSubForm(f))
})
const hasTabSubForms = computed(() => tabSubForms.value.length > 0)
const runtimeTabLayout = computed(() => resolveRuntimeFormTabLayout(props.defaultForm))
const runtimeNodeTabs = computed(() => runtimeTabLayout.value.tabs)
const liftedRootNodeIds = computed(() => runtimeTabLayout.value.liftedRootNodeIds)
const hasRuntimeFormTabs = computed(() =>
  runtimeNodeTabs.value.length > 0 || hasTabSubForms.value
)
const showOuterTabs = computed(() => hasProcessInfo.value || hasRuntimeFormTabs.value)
const showBasicTab = computed(() =>
  runtimeTabLayout.value.hasBaseContent
  || !hasRuntimeFormTabs.value
  || (canStartProcess.value && runtimeNodeTabs.value.length === 0)
)
const firstFormTabName = computed(() => {
  if (showBasicTab.value) return 'basic'
  if (runtimeNodeTabs.value.length > 0) return runtimeNodeTabs.value[0].name
  if (tabSubForms.value.length > 0) return 'subform_0'
  return 'form'
})

function setNodeFormFieldsRef(
  tabName: string,
  instance: InstanceType<typeof EntityDataFormFields> | null
) {
  if (instance) {
    nodeFormFieldsRefs.value[tabName] = instance
  } else {
    delete nodeFormFieldsRefs.value[tabName]
  }
}

function refreshFormLinkage() {
  if (!showOuterTabs.value) {
    formFieldsRef.value?.refreshLinkage()
    return
  }
  if (showBasicTab.value) {
    basicFormFieldsRef.value?.refreshLinkage()
    return
  }
  nodeFormFieldsRefs.value[firstFormTabName.value]?.refreshLinkage()
}

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
  activeTab.value = firstFormTabName.value
  resetForm()
  dialogTitle.value = props.defaultForm?.formName
    ? `新增数据 - ${props.defaultForm.formName}${props.defaultForm.formKey ? `（${props.defaultForm.formKey}）` : ''}`
    : '新增数据'

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
    refreshFormLinkage()
  })
}

// 编辑
const openEdit = async (row: any) => {
  isEdit.value = true
  formData.startProcess = false
  const detail = await entityDataApi.getDetail(props.entityCode, row.id, props.listKey).catch(() => row)
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

  activeTab.value = firstFormTabName.value

  dialogTitle.value = '编辑数据'
  dialogVisible.value = true
  nextTick(() => {
    refreshFormLinkage()
  })
}

async function validateRuntimeForms() {
  const formRefs: Array<InstanceType<typeof EntityDataFormFields>> = []
  if (!showOuterTabs.value) {
    if (formFieldsRef.value) formRefs.push(formFieldsRef.value)
  } else {
    if (showBasicTab.value && basicFormFieldsRef.value) {
      formRefs.push(basicFormFieldsRef.value)
    }
    runtimeNodeTabs.value.forEach(tab => {
      const formRef = nodeFormFieldsRefs.value[tab.name]
      if (formRef) formRefs.push(formRef)
    })
  }

  for (const formRef of formRefs) {
    if ((await formRef.validate()) === false) return false
  }

  await dataSourceRuntime.prevalidateBeforeSubmit({
    form: props.defaultForm,
    fields: props.defaultForm?.fields || [],
    nodes: props.defaultForm?.nodes || []
  })
  return true
}

// 提交
const handleSubmit = async () => {
  const valid = await validateRuntimeForms()
  if (!valid) return

  submitLoading.value = true
  try {
    const data = {
      entityCode: props.entityCode,
      listKey: props.listKey,
      id: formData.id,
      name: formData.data?.name || formData.name,
      data: formData.data,
      startProcess: formData.startProcess
    }

    if (formData.id) {
      await entityDataApi.update(props.entityCode, formData.id, data, data.startProcess, props.listKey)
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
