<template>
  <div v-if="showCustomForm">
    <component
      :is="getCustomFormComponent(defaultForm.customComponent)"
      :entityCode="entityCode"
      :entityDefinition="entityDefinition"
      :entityFields="entityFields"
      v-model="formData"
      :readonly="false"
      :mode="isEdit ? 'edit' : 'create'"
    />
  </div>
  <template v-else-if="defaultForm && defaultForm.fields && defaultForm.fields.length > 0">
    <!-- 有表单配置时：用 FormPreviewLinkage 渲染（支持 tab 子表单、联动） -->
    <FormPreviewLinkage
      ref="previewRef"
      :form="defaultForm"
      v-model="formData.data"
      :show-header="false"
      :no-internal-tabs="noInternalTabs"
    />
    <el-form label-width="100px" v-if="entityDefinition.enableProcess && showStartProcess">
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

    <el-divider v-if="entityDefinition.enableProcess && showStartProcess" />
    <el-form-item v-if="entityDefinition.enableProcess && showStartProcess" label="发起流程">
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
  return linkageState.value.visibility[field.fieldCode] !== false
}

// 判断字段是否禁用
function isFieldDisabled(field: any) {
  if (linkageState.value.disabled[field.fieldCode] === true) return true
  if (field.isReadonly === 1) {
    const refType = (field.refEntityType || '').toUpperCase()
    if (['USER', 'DEPT', 'ROLE', 'GROUP', 'CUSTOM'].includes(refType)) {
      return false
    }
    return true
  }
  return false
}

// 获取字段验证规则（含联动必填）
function getFieldRules(field: any) {
  const isRequired = linkageState.value.required[field.fieldCode] !== undefined
    ? linkageState.value.required[field.fieldCode]
    : field.isRequired
  if (isRequired) {
    return [{ required: true, message: `请输入${field.fieldName}`, trigger: 'blur' }]
  }
  return []
}

// 获取字段选项（含联动过滤）
function getFieldOptions(field: any) {
  if (linkageState.value.options[field.fieldCode]) {
    return linkageState.value.options[field.fieldCode]
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
  return props.noInternalTabs ? normalFields.value : formFields.value
})

// 暴露校验方法
async function validate() {
  if (props.defaultForm?.fields?.length > 0 && !showCustomForm.value) {
    const valid = await previewRef.value?.validate()
    if (!valid) return false
  } else if (formRef.value) {
    try {
      await formRef.value.validate()
    } catch {
      return false
    }
  }
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
