<template>
  <el-alert
    v-if="showStartProcess && entityDefinition.lifecycleMode === 'WORKFLOW' && !canStartProcess"
    :title="workflowReadinessMessage"
    type="warning"
    :closable="false"
    show-icon
    class="workflow-readiness-alert"
  />
  <div v-if="showCustomForm">
    <component
      ref="customFormRef"
      :is="getCustomFormComponent(defaultForm.customComponent)"
      :form="defaultForm"
      :entityCode="entityCode"
      :entityDefinition="entityDefinition"
      :entityFields="entityFields"
      :fields="runtimeFormFields"
      :linkageState="linkageState"
      v-model="formData.data"
      :readonly="false"
      :mode="isEdit ? 'edit' : 'create'"
      :config="formViewConfig.customComponentProps || {}"
      :context="{ entityCode, entityDefinition, mode: isEdit ? 'edit' : 'create', record: formData }"
      :data-source-runtime="dataSourceRuntime"
    />
    <el-form label-width="100px" v-if="canStartProcess && showStartProcess">
      <el-divider />
      <el-form-item label="发起流程">
        <el-switch v-model="formData.startProcess" />
        <span class="form-tip">保存数据同时发起流程</span>
      </el-form-item>
    </el-form>
  </div>
  <template v-else-if="hasConfiguredForm">
    <!-- 有表单配置时：用 FormPreviewLinkage 渲染（支持 tab 子表单、联动） -->
    <FormPreviewLinkage
      ref="previewRef"
      :form="defaultForm"
      v-model="formData.data"
      :show-header="false"
      :no-internal-tabs="noInternalTabs"
      :mode="isEdit ? 'edit' : 'create'"
      :entity-code="entityCode"
      :entity-definition="entityDefinition"
      :entity-fields="entityFields"
      :context="{ entityCode, entityDefinition, mode: isEdit ? 'edit' : 'create', record: formData }"
      :data-source-runtime="dataSourceRuntime"
    />
    <el-form label-width="100px" v-if="canStartProcess && showStartProcess">
      <el-divider />
      <el-form-item label="发起流程">
        <el-switch v-model="formData.startProcess" />
        <span class="form-tip">保存数据同时发起流程</span>
      </el-form-item>
    </el-form>
  </template>
  <el-form v-else ref="formRef" :model="formData" label-width="100px">
    <template v-for="field in renderFields" :key="field.fieldCode">
      <div v-if="isSectionField(field)" class="form-section-row">
        <SectionField :field="field" />
      </div>
      <el-form-item
        v-else
        v-show="isFieldVisible(field)"
        :label="field.fieldName" :prop="`data.${field.fieldCode}`"
        :rules="getFieldRules(field)"
      >
        <!-- 使用 FormFieldRendererLinkage 统一渲染 -->
        <FormFieldRendererLinkage
          v-model="formData.data[field.fieldCode]"
          :field="field"
          :disabled="isFieldDisabled(field)"
          :options="getFieldOptions(field)"
        />
      </el-form-item>
    </template>

    <el-divider v-if="canStartProcess && showStartProcess" />
    <el-form-item v-if="canStartProcess && showStartProcess" label="发起流程">
      <el-switch v-model="formData.startProcess" />
      <span class="form-tip">保存数据同时发起流程</span>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import FormPreviewLinkage from '@/components/FormPreviewLinkage.vue'
import FormFieldRendererLinkage from '@/components/FormFieldRendererLinkage.vue'
import SectionField from '@/components/form-fields/components/SectionField.vue'
import { LinkageEngine } from '@/utils/linkageEngine'
import { getCustomFormComponent, hasCustomFormComponent } from '@/utils/customComponentRegistry.js'
import { parseJsonOptions } from '@/shared/list-runtime'
import { entityDataApi } from '@/api/entity.js'
import { getItemTreeByDictCode } from '@/api/system/dict'
import {
  buildRuntimeFieldRules,
  createFormDataSourceRuntime,
  isRuntimeFieldReadonly,
  isRuntimeFieldVisible
} from '@/shared/form-runtime'
import { safeParseConfig } from '@/shared/config-runtime'
import { isWorkflowReady } from '@/shared/entity-design'

const props = defineProps<{
  entityCode: string
  entityDefinition: any
  entityFields: any[]
  defaultForm: any
  isEdit: boolean
  showStartProcess?: boolean
  noInternalTabs?: boolean
}>()

const formData = defineModel<any>('formData', { required: true })

const formRef = ref()
const previewRef = ref()
const customFormRef = ref()
const dictOptionMap = ref<Record<string, any[]>>({})
const dataSourceOptionMap = ref<Record<string, any[]>>({})
const runtimeMode = computed(() => props.isEdit ? 'edit' : 'create')
const formViewConfig = computed(() => safeParseConfig(props.defaultForm?.viewConfig))
const canStartProcess = computed(() => isWorkflowReady(props.entityDefinition))
const workflowReadinessMessage = computed(() => {
  const messages: Record<string, string> = {
    UNBOUND: '流程实体尚未绑定流程，当前只能保存业务数据。',
    DRAFT: '绑定流程尚未发布，当前只能保存业务数据。',
    DISABLED: '绑定流程已禁用，当前不能发起流程。',
    MISSING: '绑定流程不存在，请重新配置流程绑定。'
  }
  return messages[props.entityDefinition?.workflowBindingStatus]
    || '绑定流程尚未就绪，当前只能保存业务数据。'
})

// 字段联动状态
const linkageState = ref({
  visibility: {} as Record<string, boolean>,
  disabled: {} as Record<string, boolean>,
  required: {} as Record<string, boolean>,
  options: {} as Record<string, any[]>,
  values: {} as Record<string, any>
})

function isLinkageStateEqual(a: any, b: any) {
  if (a === b) return true
  const keysA = Object.keys(a)
  const keysB = Object.keys(b)
  if (keysA.length !== keysB.length) return false
  return keysA.every(k => {
    const va = a[k]
    const vb = b[k]
    if (va === vb) return true
    if (typeof va === 'object' && typeof vb === 'object' && va !== null && vb !== null) {
      const subKeysA = Object.keys(va)
      const subKeysB = Object.keys(vb)
      if (subKeysA.length !== subKeysB.length) return false
      return subKeysA.every(sk => va[sk] === vb[sk])
    }
    return false
  })
}

// 更新联动状态
function updateLinkageState() {
  const fields = formFields.value || []
  const newState = LinkageEngine.processAllLinkages(fields, formData.value.data || {})

  if (!isLinkageStateEqual(linkageState.value, newState)) {
    linkageState.value = newState
  }

  const values = linkageState.value.values
  if (values) {
    const entries = Object.entries(values).filter(([key, val]) => val !== null && val !== undefined && formData.value.data[key] !== val)
    if (entries.length > 0) {
      nextTick(() => {
        entries.forEach(([key, val]) => {
          formData.value.data[key] = val
        })
      })
    }
  }
}

// 判断是否为节字段
function isSectionField(field: any) {
  return (field?.fieldType || '').toUpperCase() === 'SECTION' ||
    (field?.componentType || '').toLowerCase() === 'section'
}

// 判断字段是否可见
function isFieldVisible(field: any) {
  return isRuntimeFieldVisible(field, runtimeMode.value)
    && linkageState.value.visibility[field.fieldCode] !== false
}

// 判断字段是否禁用
function isFieldDisabled(field: any) {
  if (linkageState.value.disabled[field.fieldCode] === true) return true
  return isRuntimeFieldReadonly(field, false, runtimeMode.value)
}

function isBlankValue(value: any) {
  return value === null || value === undefined || (typeof value === 'string' && value.trim() === '')
}

async function checkFieldUnique(field: any, value: any) {
  if (isBlankValue(value) || !field.isUnique) return true
  try {
    const params: Record<string, any> = {
      [field.fieldCode]: value,
      [field.fieldCode + '_op']: 'EQ'
    }
    const res = await entityDataApi.getList(props.entityCode, params)
    const list = res.data || res || []
    const currentId = formData.value?.id
    const duplicates = currentId ? list.filter((item: any) => item.id !== currentId) : list
    return duplicates.length === 0
  } catch (e) {
    // 唯一性预校验接口异常时不阻断提交，由后端兜底校验
    return true
  }
}

// 获取字段验证规则（含联动必填、程序级唯一）
function getFieldRules(field: any) {
  const isRequired = linkageState.value.required[field.fieldCode] !== undefined
    ? linkageState.value.required[field.fieldCode]
    : field.isRequired
  const rules: any[] = buildRuntimeFieldRules(field, isRequired, field.fieldName)
  if (field.isUnique) {
    rules.push({
      validator: (rule: any, value: any, callback: any) => {
        checkFieldUnique(field, value).then(valid => {
          if (valid) {
            callback()
          } else {
            callback(new Error(`${field.fieldName} 的值已存在`))
          }
        }).catch(() => callback())
      },
      trigger: 'blur'
    })
  }
  return rules
}

// 获取字段选项（含联动过滤）
function getFieldOptions(field: any) {
  if (linkageState.value.options[field.fieldCode]) {
    return linkageState.value.options[field.fieldCode]
  }
  if (dataSourceOptionMap.value[field.fieldCode]) {
    return dataSourceOptionMap.value[field.fieldCode]
  }
  if (field.dictType && dictOptionMap.value[field.dictType]) {
    return dictOptionMap.value[field.dictType]
  }
  if (field.optionsJson) {
    return parseJsonOptions(field.optionsJson)
  }
  if (field.componentProps) {
    try {
      const compProps = JSON.parse(field.componentProps)
      if (compProps.options && Array.isArray(compProps.options)) {
        return compProps.options
      }
    } catch (e) {}
  }
  return []
}

async function executeFieldDataSource(field: any, usage: string) {
  const [result] = await dataSourceRuntime.executeOwnerUsage(field, usage, {
    input: {
      fieldCode: field.fieldCode,
      value: formData.value?.data?.[field.fieldCode]
    }
  })
  return result ?? null
}

const dataSourceRuntime = createFormDataSourceRuntime({
  entityCode: props.entityCode,
  getRecord: () => formData.value?.data || {},
  getRecordId: () => formData.value?.id,
  getListKey: () => formData.value?.listKey,
  getMode: () => runtimeMode.value,
  getForm: () => props.defaultForm,
  getEntityDefinition: () => props.entityDefinition
})

async function loadRuntimeDataSources(fields: any[]) {
  await dataSourceRuntime.initialize({
    form: props.defaultForm,
    fields,
    nodes: props.defaultForm?.nodes || []
  })
  const optionEntries = await Promise.all(
    (fields || []).map(async field => {
      try {
        const options = await executeFieldDataSource(field, 'FIELD_OPTIONS')
        return [field.fieldCode, Array.isArray(options) ? options : []]
      } catch {
        return [field.fieldCode, []]
      }
    })
  )
  dataSourceOptionMap.value = Object.fromEntries(
    optionEntries.filter(([, options]) => options.length > 0)
  )

  for (const field of fields || []) {
    if (!dataSourceOptionMap.value[field.fieldCode]) continue
  }
}

function flattenDictItems(items: any[]): any[] {
  return (items || []).flatMap((item: any) => [
    {
      value: item.itemCode,
      label: item.itemLabel,
      disabled: item.status !== '0'
    },
    ...flattenDictItems(item.children || [])
  ])
}

watch(
  () => props.entityFields.map((field: any) => field.dictType).filter(Boolean),
  async (dictCodes: string[]) => {
    const uniqueCodes = [...new Set(dictCodes)]
    const entries = await Promise.all(uniqueCodes.map(async dictCode => {
      try {
        const items = await getItemTreeByDictCode(dictCode)
        return [dictCode, flattenDictItems(items || [])]
      } catch {
        return [dictCode, []]
      }
    }))
    dictOptionMap.value = Object.fromEntries(entries)
  },
  { immediate: true }
)

watch(
  () => props.defaultForm?.fields,
  (fields: any[]) => {
    loadRuntimeDataSources(fields || [])
  },
  { immediate: true }
)

// 监听表单数据变化，触发联动（只在无表单配置时处理，有表单配置时由 FormPreviewLinkage 自己管理）
watch(() => formData.value.data, () => {
  if (!props.defaultForm?.fields?.length) {
    updateLinkageState()
  }
}, { deep: true })

// 是否使用自定义表单组件
const showCustomForm = computed(() => {
  return props.defaultForm?.customComponent && hasCustomFormComponent(props.defaultForm.customComponent)
})

const hasConfiguredForm = computed(() =>
  Boolean(props.defaultForm) && (
    (props.defaultForm?.fields?.length || 0) > 0
    || (props.defaultForm?.nodes?.length || 0) > 0
  )
)

// 判断是否为 Tab 模式的子表单
function isTabSubForm(field: any) {
  if (!field) return false
  const type = (field.componentType || field.fieldType || '').toUpperCase()
  if (!['SUB_FORM', 'SUB_FORM_LIST'].includes(type)) return false
  if (field.displayMode === 'tab') return true
  if (field.componentProps) {
    try {
      const compProps = typeof field.componentProps === 'string'
        ? JSON.parse(field.componentProps)
        : field.componentProps
      return compProps.subFormConfig?.displayMode === 'tab'
    } catch (e) {}
  }
  return false
}

// 表单字段（排除系统字段）
const formFields = computed(() => {
  return props.entityFields.filter((f: any) => !f.isSystem)
})

const runtimeFormFields = computed(() =>
  (props.defaultForm?.fields || formFields.value)
    .filter((field: any) => isRuntimeFieldVisible(field, runtimeMode.value))
    .map((field: any) => {
      const entityField = props.entityFields.find((item: any) =>
        item.fieldCode === field.fieldCode || item.id === field.fieldId)
      const dictType = field.dictType || entityField?.dictType
      const options = dictType ? dictOptionMap.value[dictType] : null
      return options
        ? { ...field, dictType, optionsJson: JSON.stringify(options) }
        : field
    })
)

// 普通字段（不含 tab 子表单）
const normalFields = computed(() => {
  return formFields.value.filter((f: any) => !isTabSubForm(f))
})

// tab 子表单字段
const tabSubForms = computed(() => {
  return formFields.value.filter((f: any) => isTabSubForm(f))
})

// 实际渲染的字段
const renderFields = computed(() => {
  return (props.noInternalTabs ? normalFields.value : formFields.value)
    .filter((field: any) => isRuntimeFieldVisible(field, runtimeMode.value))
})

// 暴露校验方法
async function validate() {
  if (showCustomForm.value && customFormRef.value?.validate) {
    const valid = await customFormRef.value.validate()
    if (valid === false) return false
  } else if (hasConfiguredForm.value && !showCustomForm.value) {
    const valid = await previewRef.value?.validate()
    if (!valid) return false
  } else if (formRef.value) {
    try {
      await formRef.value.validate()
    } catch {
      return false
    }
  }
  await dataSourceRuntime.prevalidateBeforeSubmit({
    form: props.defaultForm,
    fields: props.defaultForm?.fields || [],
    nodes: props.defaultForm?.nodes || []
  })
  return true
}

// 触发一次联动更新
function refreshLinkage() {
  updateLinkageState()
}

defineExpose({
  validate,
  refreshLinkage,
  tabSubForms,
  normalFields,
  isTabSubForm
})
</script>

<style scoped lang="scss">
.form-section-row {
  width: 100%;
}

.form-tip {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
</style>
