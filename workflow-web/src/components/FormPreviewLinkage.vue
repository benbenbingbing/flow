/**
 * 带字段联动功能的表单渲染组件
 * 支持显隐控制、值联动、选项联动、计算字段
 */

<template>
  <div
    class="linkage-form-preview"
    :class="form?.layoutType"
    :style="previewStyle"
  >
    <div class="preview-header" v-if="showHeader">
      <h3>{{ form?.formName }}</h3>
    </div>
    
    <template v-if="form?.customComponent && hasCustomFormComponent(form.customComponent)">
      <component
        ref="customFormRef"
        :is="getCustomFormComponent(form.customComponent)"
        :form="form"
        :modelValue="formData"
        @update:modelValue="handleCustomFormUpdate"
        :readonly="readonly"
        :fields="processedFields"
        :linkageState="linkageState"
        :mode="mode"
        :config="formViewConfig.customComponentProps || {}"
        :context="runtimeContext"
        :entity-code="entityCode"
        :entity-definition="entityDefinition"
        :entity-fields="entityFields"
        :data-source-runtime="dataSourceRuntime"
      />
    </template>
    <FormNodeRenderer
      v-else-if="hasNodeTree"
      ref="nodeFormRef"
      :nodes="form.nodes"
      :fields="processedFields"
      :model-value="formData"
      :linkage-state="linkageState"
      :readonly="readonly"
      :mode="mode"
      :context="runtimeContext"
      :data-source-runtime="dataSourceRuntime"
      :label-width="labelWidth"
      :label-position="labelPosition"
      :layout-type="form?.layoutType || 'vertical'"
      :root-parent-id="nodeRootParentId"
      :excluded-node-ids="excludedNodeIds"
      @update:model-value="handleCustomFormUpdate"
    >
      <template
        v-for="slotKey in actionSlotKeys"
        #[`action-${slotKey}`]
      >
        <FormActionBar
          :key="slotKey"
          :actions="slotFormActions(currentFormActions, slotKey)"
          :loading-key="actionLoadingKey"
          inline
          @action="$emit('form-action', $event)"
        />
      </template>
    </FormNodeRenderer>
    <el-form
      v-else
      ref="formRef"
      :model="formData"
      :label-width="labelWidth"
      :label-position="labelPosition"
      class="preview-form"
    >
      <div
        v-for="field in processedFields"
        :key="field.id"
        class="preview-field-wrapper"
        :class="{ 'section-preview': isSectionField(field) }"
        :style="isSectionField(field) ? { width: '100%' } : getFieldStyle(field)"
        v-show="linkageState.visibility[getFieldKey(field)] !== false"
      >
        <template v-if="isSectionField(field)">
          <SectionField :field="field" />
        </template>
        <el-form-item
          v-else
          :label="field.fieldLabel || field.fieldName"
          :prop="getFieldKey(field)"
          :rules="getFieldRules(field)"
          :required="isFieldRequired(field)"
        >
          <FormFieldRendererLinkage
            :field="field"
            v-model="formData[getFieldKey(field)]"
            :disabled="isFieldDisabled(field)"
            :options="linkageState.options[getFieldKey(field)] || field.options"
            :context="{ ...runtimeContext, field }"
            :data-source-runtime="dataSourceRuntime"
          />
        </el-form-item>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import FormFieldRendererLinkage from './FormFieldRendererLinkage.vue'
import FormNodeRenderer from './FormNodeRenderer.vue'
import FormActionBar from './FormActionBar.vue'
import SectionField from './form-fields/components/SectionField.vue'
import LinkageEngine from '../utils/linkageEngine'
import { getCustomFormComponent, hasCustomFormComponent } from '@/utils/customComponentRegistry.js'
import { buildRuntimeFieldRules, getFieldKey } from '@/shared/form-runtime'
import { slotFormActions } from '@/shared/form-actions'
import {
  isFieldReadonlyForMode,
  isFieldVisibleForMode,
  safeParseConfig
} from '@/shared/config-runtime'

const props = defineProps({
  form: {
    type: Object,
    required: true
  },
  modelValue: {
    type: Object,
    default: () => ({})
  },
  readonly: {
    type: Boolean,
    default: false
  },
  mode: {
    type: String,
    default: 'view'
  },
  showHeader: {
    type: Boolean,
    default: true
  },
  height: {
    type: String,
    default: '70vh'
  },
  entityCode: {
    type: String,
    default: ''
  },
  entityDefinition: {
    type: Object,
    default: null
  },
  entityFields: {
    type: Array,
    default: () => []
  },
  context: {
    type: Object,
    default: () => ({})
  },
  dataSourceRuntime: {
    type: Object,
    default: null
  },
  formActions: {
    type: Array,
    default: () => []
  },
  actionLoadingKey: {
    type: String,
    default: ''
  },
  nodeRootParentId: {
    type: [String, Number],
    default: ''
  },
  excludedNodeIds: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue', 'form-action'])
const formViewConfig = computed(() => safeParseConfig(props.form?.viewConfig))

const previewStyle = computed(() => ({
  height: props.height,
  overflowY: 'auto'
}))

// 自定义表单组件数据更新回调
function handleCustomFormUpdate(val) {
  formData.value = { ...val }
  emit('update:modelValue', formData.value)
}

const formRef = ref(null)
const customFormRef = ref(null)
const nodeFormRef = ref(null)
const formData = ref(props.modelValue || {})
const linkageState = ref({
  visibility: {},
  disabled: {},
  required: {},
  options: {}
})
const hasNodeTree = computed(() => Array.isArray(props.form?.nodes) && props.form.nodes.length > 0)
const actionSlotKeys = computed(() =>
  (props.form?.nodes || [])
    .filter(node => String(node?.nodeType || '').toUpperCase() === 'ACTION_SLOT')
    .map(node => node.nodeKey)
    .filter(Boolean)
)
const currentFormActions = computed(() => {
  const formId = String(
    props.form?.id
    || props.form?.formId
    || props.form?.entityFormId
    || ''
  )
  return props.formActions.filter(action =>
    action?.type === 'built-in'
    || !formId
    || !action?.ownerFormId
    || String(action.ownerFormId) === formId
  )
})
const runtimeContext = computed(() => ({
  ...props.context,
  mode: props.mode,
  form: props.form,
  readonly: props.readonly,
  entityCode: props.entityCode,
  entityDefinition: props.entityDefinition
}))

// 判断是否为 Tab 模式的子表单
function isSectionField(field) {
  return (field?.fieldType || '').toUpperCase() === 'SECTION' ||
    (field?.componentType || '').toLowerCase() === 'section'
}

// 同步外部数据（只在引用变化时同步，避免与内部 watcher 循环）
watch(() => props.modelValue, (val) => {
  if (val === formData.value) return
  formData.value = val || {}
  updateLinkageState()
})

// 同步内部数据到外部，并触发联动更新
watch(formData, (val) => {
  emit('update:modelValue', val)
  updateLinkageState()
  applyLinkageValues()
  applyCalculatedValues()
}, { deep: true })

// 处理后的字段列表（过滤掉隐藏的）
const processedFields = computed(() => {
  const fields = props.form?.fields || []
  return [...fields]
    .filter(field => isFieldVisibleForMode(field, props.mode))
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
})

// 标签位置
const labelPosition = computed(() => {
  switch (props.form?.layoutType) {
    case 'horizontal':
      return 'right'
    case 'vertical':
      return 'top'
    default:
      return 'right'
  }
})

// 标签宽度
const labelWidth = computed(() => {
  if (formViewConfig.value.labelWidth) {
    return `${formViewConfig.value.labelWidth}px`
  }
  switch (props.form?.layoutType) {
    case 'horizontal':
      return '120px'
    case 'vertical':
      return 'auto'
    default:
      return '120px'
  }
})

// 获取字段样式
function getFieldStyle(field) {
  if (props.form?.layoutType === 'grid') {
    const span = field.gridSpan || 24
    return {
      width: `${(span / 24) * 100}%`,
      flex: `0 0 ${(span / 24) * 100}%`,
      padding: '8px',
      boxSizing: 'border-box'
    }
  }
  if (props.form?.layoutType === 'horizontal') {
    return {
      width: 'calc(50% - 10px)',
      flex: '0 0 calc(50% - 10px)'
    }
  }
  // vertical
  return {
    width: '100%',
    padding: '4px 0'
  }
}

// 判断字段是否禁用（实体引用字段不受 isReadonly 影响，需保持可交互以选择数据）
function isFieldDisabled(field) {
  const fieldKey = getFieldKey(field)
  if (linkageState.value.disabled[fieldKey]) return true
  return isFieldReadonlyForMode(field, props.mode, props.readonly)
}

// 判断字段是否必填（联动状态优先，未配置时回退到字段本身的 isRequired）
function isFieldRequired(field) {
  const fieldKey = getFieldKey(field)
  if (linkageState.value.required[fieldKey] !== undefined) {
    return Boolean(linkageState.value.required[fieldKey])
  }
  return Boolean(field.isRequired)
}

// 获取字段验证规则
function getFieldRules(field) {
  return buildRuntimeFieldRules(
    field,
    isFieldRequired(field),
    field.fieldLabel || field.fieldName
  )
}

function isLinkageStateEqual(a, b) {
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

// 更新联动状态（只更新 linkageState，不直接修改 formData，避免 watcher 递归）
function updateLinkageState() {
  const fields = props.form?.fields || []
  const newState = LinkageEngine.processAllLinkages(fields, formData.value)
  if (!isLinkageStateEqual(linkageState.value, newState)) {
    linkageState.value = newState
  }
}

// 应用值联动和计算字段的结果（使用 nextTick 避免在 watcher 回调中递归）
function applyLinkageValues() {
  const values = linkageState.value.values
  if (!values) return
  const entries = Object.entries(values).filter(([key, val]) => val !== null && val !== undefined && formData.value[key] !== val)
  if (entries.length > 0) {
    nextTick(() => {
      entries.forEach(([key, val]) => {
        formData.value[key] = val
      })
    })
  }
}

// 应用计算字段值
function applyCalculatedValues() {
  const fields = props.form?.fields || []
  nextTick(() => {
    fields.forEach(field => {
      const rules = LinkageEngine.getFieldLinkageRules(field)
      if (rules.calculationFormula) {
        const fieldKey = field.fieldCode || field.fieldKey
        const calculatedValue = LinkageEngine.calculate(rules.calculationFormula, formData.value)
        if (calculatedValue !== null && formData.value[fieldKey] !== calculatedValue) {
          formData.value[fieldKey] = calculatedValue
        }
      }
    })
  })
}

// 处理字段值变化
function handleFieldChange(fieldKey, value) {
  // 更新当前字段值
  formData.value[fieldKey] = value

  // 重新计算联动状态并应用
  updateLinkageState()
  applyLinkageValues()
  applyCalculatedValues()
}

// 初始化
onMounted(() => {
  updateLinkageState()
  applyLinkageValues()
  applyCalculatedValues()
})

// 验证表单
async function validate() {
  if (customFormRef.value?.validate) {
    return (await customFormRef.value.validate()) !== false
  }
  if (hasNodeTree.value) {
    return (await nodeFormRef.value?.validate()) !== false
  }
  if (!formRef.value) return true
  try {
    await formRef.value.validate()
    return true
  } catch {
    return false
  }
}

// 暴露方法
defineExpose({
  validate,
  getData: () => formData.value
})
</script>

<style scoped>
.linkage-form-preview {
  width: 100%;
}

.preview-header h3 {
  margin: 0 0 20px 0;
  padding-bottom: 10px;
  border-bottom: 1px solid #e4e7ed;
}

.preview-form {
  display: flex;
  flex-wrap: wrap;
}

.preview-field-wrapper {
  transition: all 0.3s ease;
}

.preview-field-wrapper[v-show="false"] {
  display: none !important;
}
</style>
