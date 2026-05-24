/**
 * 支持字段联动的字段渲染器
 * 扩展 FormFieldRenderer，支持动态选项
 */

<template>
  <div class="form-field-renderer-linkage">
    <!-- 文本输入 -->
    <el-input
      v-if="renderType === 'input'"
      v-model="fieldValue"
      :placeholder="field.placeholder || `请输入${fieldLabel}`"
      :disabled="disabled"
      clearable
      v-on="customEventListeners"
      @change="handleValueChange"
      @input="handleValueChange"
      @blur="handleBlur"
      @focus="handleFocus"
    />
    
    <!-- 多行文本 -->
    <el-input
      v-else-if="renderType === 'textarea'"
      v-model="fieldValue"
      type="textarea"
      :rows="3"
      :placeholder="field.placeholder || `请输入${fieldLabel}`"
      :disabled="disabled"
      v-on="customEventListeners"
      @change="handleValueChange"
      @input="handleValueChange"
      @blur="handleBlur"
      @focus="handleFocus"
    />
    
    <!-- 数字 -->
    <el-input-number
      v-else-if="renderType === 'number'"
      v-model="fieldValue"
      :placeholder="field.placeholder || `请输入${fieldLabel}`"
      :disabled="disabled"
      style="width: 100%"
      v-on="customEventListeners"
      @change="handleValueChange"
      @blur="handleBlur"
      @focus="handleFocus"
    />
    
    <!-- 日期 -->
    <el-date-picker
      v-else-if="renderType === 'date'"
      v-model="fieldValue"
      type="date"
      :placeholder="field.placeholder || `请选择${fieldLabel}`"
      :disabled="disabled"
      style="width: 100%"
      value-format="YYYY-MM-DD"
      v-on="customEventListeners"
      @change="handleValueChange"
      @blur="handleBlur"
      @focus="handleFocus"
    />
    
    <!-- 日期时间 -->
    <el-date-picker
      v-else-if="renderType === 'datetime'"
      v-model="fieldValue"
      type="datetime"
      :placeholder="field.placeholder || `请选择${fieldLabel}`"
      :disabled="disabled"
      style="width: 100%"
      value-format="YYYY-MM-DD HH:mm:ss"
      v-on="customEventListeners"
      @change="handleValueChange"
      @blur="handleBlur"
      @focus="handleFocus"
    />
    
    <!-- 下拉选择（单选）- 支持动态选项 -->
    <el-select
      v-else-if="renderType === 'select'"
      v-model="fieldValue"
      :placeholder="field.placeholder || `请选择${fieldLabel}`"
      :disabled="disabled"
      style="width: 100%"
      clearable
      v-on="customEventListeners"
      @change="handleValueChange"
      @blur="handleBlur"
      @focus="handleFocus"
    >
      <el-option
        v-for="opt in currentOptions"
        :key="opt.value"
        :label="opt.label"
        :value="opt.value"
      />
    </el-select>
    
    <!-- 下拉选择（多选）- 支持动态选项 -->
    <el-select
      v-else-if="renderType === 'select_multiple'"
      v-model="fieldValue"
      :placeholder="field.placeholder || `请选择${fieldLabel}`"
      :disabled="disabled"
      style="width: 100%"
      multiple
      clearable
      v-on="customEventListeners"
      @change="handleValueChange"
      @blur="handleBlur"
      @focus="handleFocus"
    >
      <el-option
        v-for="opt in currentOptions"
        :key="opt.value"
        :label="opt.label"
        :value="opt.value"
      />
    </el-select>
    
    <!-- 单选 - 支持动态选项 -->
    <el-radio-group
      v-else-if="renderType === 'radio'"
      v-model="fieldValue"
      :disabled="disabled"
      v-on="customEventListeners"
      @change="handleValueChange"
    >
      <el-radio
        v-for="opt in currentOptions"
        :key="opt.value"
        :label="opt.value"
      >
        {{ opt.label }}
      </el-radio>
    </el-radio-group>
    
    <!-- 多选 - 支持动态选项 -->
    <el-checkbox-group
      v-else-if="renderType === 'checkbox'"
      v-model="fieldValue"
      :disabled="disabled"
      v-on="customEventListeners"
      @change="handleValueChange"
    >
      <el-checkbox
        v-for="opt in currentOptions"
        :key="opt.value"
        :label="opt.value"
      >
        {{ opt.label }}
      </el-checkbox>
    </el-checkbox-group>
    
    <!-- 开关 -->
    <el-switch
      v-else-if="renderType === 'switch'"
      v-model="fieldValue"
      :disabled="disabled"
      v-on="customEventListeners"
      @change="handleValueChange"
    />
    
    <!-- 文件上传 -->
    <FileUploader
      v-else-if="renderType === 'file' || renderType === 'image'"
      v-model="fieldValue"
      :field="field"
      :disabled="disabled"
      :is-image="renderType === 'image'"
      @change="handleValueChange"
    />
    
    <!-- 用户选择 -->
    <el-select-v2
      v-else-if="renderType === 'user'"
      v-model="fieldValue"
      :options="userOptions"
      :placeholder="field.placeholder || `请选择${fieldLabel}`"
      filterable
      clearable
      :disabled="disabled"
      style="width: 100%"
      v-on="customEventListeners"
      @change="handleValueChange"
      @blur="handleBlur"
      @focus="handleFocus"
    />
    
    <!-- 级联选择（省市联动） -->
    <el-cascader
      v-else-if="renderType === 'cascader'"
      v-model="fieldValue"
      :options="cascaderOptions"
      :placeholder="field.placeholder || `请选择${fieldLabel}`"
      :disabled="disabled"
      style="width: 100%"
      clearable
      v-on="customEventListeners"
      @change="handleValueChange"
      @blur="handleBlur"
      @focus="handleFocus"
    />
    
    <!-- 子表单 -->
    <SubFormRenderer
      v-else-if="renderType === 'sub_form' || renderType === 'SUB_FORM'"
      v-model="fieldValue"
      :config="subFormConfig"
      :readonly="disabled"
      :disabled="disabled"
      @change="handleValueChange"
    />
    
    <!-- 默认文本输入 -->
    <el-input
      v-else
      v-model="fieldValue"
      :placeholder="field.placeholder || `请输入${fieldLabel}`"
      :disabled="disabled"
      v-on="customEventListeners"
      @change="handleValueChange"
      @input="handleValueChange"
      @blur="handleBlur"
      @focus="handleFocus"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import FileUploader from './FileUploader.vue'
import SubFormRenderer from './SubFormRenderer.vue'
import { getFormFields } from '../api/entityForm'

const props = defineProps({
  field: {
    type: Object,
    required: true
  },
  modelValue: {
    type: [String, Number, Array, Date, Object, Boolean],
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  },
  options: {
    type: Array,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

// 获取字段所有事件代码（含自定义事件）
function getAllEvents(field) {
  const result = {}
  // 从根属性读取
  Object.keys(field).forEach(key => {
    if (key.startsWith('eventOn') && field[key]) {
      const eventName = 'on' + key.slice(7)
      result[eventName] = field[key]
    }
  })
  // 从 componentProps 读取
  if (field.componentProps) {
    try {
      const compProps = JSON.parse(field.componentProps)
      if (compProps.events) {
        Object.keys(compProps.events).forEach(key => {
          if (!result[key]) {
            result[key] = compProps.events[key]
          }
        })
      }
    } catch (e) {}
  }
  return result
}

// 自定义事件监听器（排除内置的 onChange/onBlur/onFocus）
const customEventListeners = computed(() => {
  const listeners = {}
  const events = getAllEvents(props.field)
  Object.keys(events).forEach(key => {
    if (['onChange', 'onBlur', 'onFocus'].includes(key)) return
    // DOM 事件名：onDoubleClick → dblclick，onSelect → select
    const domEvent = key.startsWith('on') ? key.slice(2) : key
    const eventName = domEvent.charAt(0).toLowerCase() + domEvent.slice(1)
    listeners[eventName] = () => executeEvent(events[key], fieldValue.value)
  })
  return listeners
})

// 渲染类型
const renderType = computed(() => {
  const type = props.field.componentType || props.field.fieldType || ''
  return type.toLowerCase()
})

// 字段显示标签
const fieldLabel = computed(() => {
  return props.field.fieldLabel || props.field.fieldName || ''
})

// 当前选项（优先使用传入的动态选项）
const currentOptions = computed(() => {
  if (props.options && props.options.length > 0) {
    return props.options
  }
  return parsedOptions.value
})

// 解析选项
const parsedOptions = computed(() => {
  // 尝试从 componentProps 解析选项
  if (props.field.componentProps) {
    try {
      const compProps = JSON.parse(props.field.componentProps)
      if (compProps.options) return compProps.options
    } catch (e) {}
  }
  
  // 从 optionsJson 解析
  if (props.field.optionsJson) {
    try {
      return JSON.parse(props.field.optionsJson)
    } catch (e) {
      return []
    }
  }
  
  // 从 options 解析
  if (props.field.options) {
    if (typeof props.field.options === 'string') {
      try {
        return JSON.parse(props.field.options)
      } catch (e) {
        return []
      }
    }
    return props.field.options
  }
  
  return []
})

// 级联选项（省市联动）
const cascaderOptions = computed(() => {
  // 支持从 field.cascaderOptions 或 field.componentProps 获取
  if (props.field.cascaderOptions) {
    return props.field.cascaderOptions
  }
  
  if (props.field.componentProps) {
    try {
      const compProps = JSON.parse(props.field.componentProps)
      if (compProps.cascaderOptions) return compProps.cascaderOptions
    } catch (e) {}
  }
  
  // 默认中国省市区数据（简化版）
  return getChinaRegionData()
})

// 字段值
const fieldValue = computed({
  get() {
    // 多选字段必须是数组
    if ((renderType.value === 'checkbox' || renderType.value === 'CHECKBOX') && !Array.isArray(props.modelValue)) {
      return props.modelValue ? [props.modelValue] : []
    }
    // 子表单数据必须是数组
    if ((renderType.value === 'sub_form' || renderType.value === 'SUB_FORM') && !Array.isArray(props.modelValue)) {
      return []
    }
    return props.modelValue
  },
  set(val) {
    emit('update:modelValue', val)
  }
})

// 解析子表单元数据（优先根属性，再读 componentProps）
const subFormMeta = computed(() => {
  const field = props.field
  // 优先根属性
  if (field.subFormType === 'ref' && field.refFormId) {
    return { subFormType: 'ref', refFormId: field.refFormId, refEntityId: field.refEntityId || '' }
  }
  // 从 componentProps 读取
  if (field.componentProps) {
    try {
      const compProps = JSON.parse(field.componentProps)
      if (compProps.subFormConfig?.type === 'ref' && compProps.subFormConfig?.refFormId) {
        return {
          subFormType: 'ref',
          refFormId: compProps.subFormConfig.refFormId,
          refEntityId: compProps.subFormConfig.refEntityId || ''
        }
      }
    } catch (e) {}
  }
  return { subFormType: field.subFormType || 'embedded', refFormId: null, refEntityId: '' }
})

// 外部表单字段（子表单引用外部表单时使用）
const externalFormFields = ref([])

watch(() => subFormMeta.value.refFormId, async (formId) => {
  if (formId && subFormMeta.value.subFormType === 'ref') {
    try {
      const res = await getFormFields(formId)
      // 兼容直接返回数组或 { data: [...] } 两种格式
      const fields = Array.isArray(res) ? res : (Array.isArray(res.data) ? res.data : [])
      // 将外部表单字段转换为 SubFormRenderer 需要的格式
      externalFormFields.value = fields.map(f => ({
        fieldKey: f.fieldCode || f.fieldId || f.id,
        fieldName: f.fieldLabel || f.fieldName,
        fieldType: mapFieldType(f.componentType || f.fieldType),
        isEditable: true,
        isRequired: f.isRequired === 1,
        options: f.options
      }))
    } catch (e) {
      externalFormFields.value = []
    }
  } else {
    externalFormFields.value = []
  }
}, { immediate: true })

// 字段类型映射（外部表单字段类型 → SubFormRenderer 类型）
function mapFieldType(type) {
  const map = {
    'string': 'TEXT',
    'text': 'TEXT',
    'integer': 'NUMBER',
    'decimal': 'NUMBER',
    'date': 'DATE',
    'datetime': 'DATE',
    'select': 'SELECT',
    'radio': 'SELECT',
    'checkbox': 'SELECT'
  }
  return map[(type || '').toLowerCase()] || 'TEXT'
}

// 子表单配置
const subFormConfig = computed(() => {
  const field = props.field
  const isRef = subFormMeta.value.subFormType === 'ref' && externalFormFields.value.length > 0
  const fields = isRef
    ? externalFormFields.value
    : (field.subFields || field.fields || [])
  return {
    label: field.fieldName || '明细',
    fieldKey: field.fieldKey || 'detailList',
    required: field.required || false,
    minRows: field.minRows || 0,
    maxRows: field.maxRows || 100,
    fields: fields,
    showSummary: field.showSummary || false,
    summaryFields: field.summaryFields || [],
    layout: isRef ? 'form' : 'table'
  }
})

// 从字段配置中获取事件代码（支持根属性和 componentProps.events）
function getEventCode(field, eventType) {
  // eventType 可能是 'onChange' 或 'change'，统一转为 'eventOnChange'
  const suffix = eventType.startsWith('on') ? eventType.slice(2) : eventType
  const rootKey = 'eventOn' + suffix.charAt(0).toUpperCase() + suffix.slice(1)
  if (field[rootKey]) return field[rootKey]
  if (field.componentProps) {
    try {
      const compProps = JSON.parse(field.componentProps)
      return compProps.events?.[eventType] || ''
    } catch (e) {}
  }
  return ''
}

// 执行字段自定义事件代码
function executeEvent(code, value) {
  if (!code) return
  try {
    const func = new Function('value', 'field', code)
    func(value, props.field)
  } catch (e) {
    console.error('字段事件执行失败:', e)
  }
}

// 字段值变化处理（含事件触发）
function handleValueChange(val) {
  fieldValue.value = val
  const code = getEventCode(props.field, 'onChange')
  executeEvent(code, val)
  emit('change', val)
}

// 字段失焦处理
function handleBlur() {
  const code = getEventCode(props.field, 'onBlur')
  executeEvent(code, fieldValue.value)
}

// 字段聚焦处理
function handleFocus() {
  const code = getEventCode(props.field, 'onFocus')
  executeEvent(code, fieldValue.value)
}

// 用户选项
const userOptions = ref([
  { value: 'user1', label: '张三' },
  { value: 'user2', label: '李四' },
  { value: 'user3', label: '王五' }
])

// 处理值变化
function handleChange(val) {
  emit('change', val)
}

// 获取中国省市区数据（简化版）
function getChinaRegionData() {
  return [
    {
      value: 'beijing',
      label: '北京',
      children: [
        { value: 'dongcheng', label: '东城区' },
        { value: 'xicheng', label: '西城区' },
        { value: 'chaoyang', label: '朝阳区' },
        { value: 'haidian', label: '海淀区' }
      ]
    },
    {
      value: 'shanghai',
      label: '上海',
      children: [
        { value: 'huangpu', label: '黄浦区' },
        { value: 'xuhui', label: '徐汇区' },
        { value: 'changning', label: '长宁区' },
        { value: 'jingan', label: '静安区' }
      ]
    },
    {
      value: 'guangdong',
      label: '广东',
      children: [
        {
          value: 'guangzhou',
          label: '广州',
          children: [
            { value: 'tianhe', label: '天河区' },
            { value: 'yuexiu', label: '越秀区' },
            { value: 'liwan', label: '荔湾区' }
          ]
        },
        {
          value: 'shenzhen',
          label: '深圳',
          children: [
            { value: 'futian', label: '福田区' },
            { value: 'nanshan', label: '南山区' },
            { value: 'luohu', label: '罗湖区' }
          ]
        }
      ]
    },
    {
      value: 'zhejiang',
      label: '浙江',
      children: [
        {
          value: 'hangzhou',
          label: '杭州',
          children: [
            { value: 'gongshu', label: '拱墅区' },
            { value: 'xihu', label: '西湖区' },
            { value: 'binjiang', label: '滨江区' }
          ]
        }
      ]
    }
  ]
}

// 设置默认值
watch(() => props.field.defaultValue, (val) => {
  if (val && !props.modelValue) {
    emit('update:modelValue', val)
  }
}, { immediate: true })
</script>

<style scoped>
.form-field-renderer-linkage {
  width: 100%;
}
</style>
