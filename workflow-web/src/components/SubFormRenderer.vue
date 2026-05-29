<template>
  <div class="sub-form-renderer">
    <!-- 子表单标题和操作：仅可重复添加时显示 -->
    <div v-if="isRepeatable" class="sub-form-header">
      <span class="sub-form-title">{{ title || config.label || '明细' }}</span>
      <el-tag v-if="config.required" type="danger" size="small" effect="plain">必填</el-tag>
      <span class="sub-form-summary" v-if="showSummary">
        共 {{ rowData.length }} 条，
        <span v-for="(sum, key) in summaryData" :key="key" class="summary-item">
          {{ sum.label }}: {{ sum.value }}
        </span>
      </span>
      <el-button
        v-if="!disabled && !readonly"
        type="primary"
        size="small"
        @click="addRow"
        :disabled="rowData.length >= maxRows"
      >
        <el-icon><Plus /></el-icon>添加
      </el-button>
    </div>

    <!-- 表单布局 -->
    <template v-if="config.layout === 'form'">
      <!-- 空状态：只读/禁用模式下显示字段预览；编辑模式由 watch 自动初始化，这里兜底 -->
      <template v-if="rowData.length === 0">
        <template v-if="disabled || readonly">
          <div v-if="config.fields && config.fields.length > 0" class="form-fields-preview">
            <div v-for="field in config.fields" :key="field.fieldKey" class="preview-field-item">
              <span class="preview-label">{{ field.fieldName }}</span>
              <span class="preview-placeholder">—</span>
            </div>
          </div>
          <el-empty v-else description="暂无数据" :image-size="60" />
        </template>
        <template v-else>
          <el-empty v-if="isRepeatable" description="暂无数据，点击添加" :image-size="60">
            <el-button type="primary" size="small" @click="addRow">添加明细</el-button>
          </el-empty>
          <el-empty v-else description="暂无数据" :image-size="60" />
        </template>
      </template>

      <!-- 表单卡片列表（单条记录时不显示记录标题） -->
      <div v-for="(row, index) in rowData" :key="index" :class="['form-row-card', { 'single-record': rowData.length === 1 }]">
        <div v-if="isRepeatable && rowData.length > 1" class="form-row-header">
          <span class="row-title">记录 {{ index + 1 }}</span>
          <el-button v-if="!disabled && !readonly" type="danger" size="small" text @click="removeRow(index)">
            <el-icon><Delete /></el-icon>删除
          </el-button>
        </div>
        <div class="form-row-body">
          <div v-for="field in config.fields" :key="field.fieldKey" class="form-field-item">
            <label class="field-label" :class="{ required: field.isRequired }">
              {{ field.fieldName }}
              <el-tag v-if="field.isRequired" type="danger" size="small" effect="plain" class="required-tag">必填</el-tag>
            </label>
            <div class="field-control">
              <!-- 只读模式 -->
              <template v-if="readonly || disabled || !field.isEditable">
                <span>{{ formatCellValue(row[field.fieldKey], field) }}</span>
              </template>
              <!-- 编辑模式 -->
              <template v-else>
                <el-input v-if="field.fieldType === 'TEXT' || field.fieldType === 'input' || field.fieldType === 'string'" v-model="row[field.fieldKey]" size="default" :placeholder="field.placeholder" @blur="validateField(row, field, index)" />
                <el-input-number v-else-if="field.fieldType === 'NUMBER' || field.fieldType === 'number' || field.fieldType === 'integer' || field.fieldType === 'long' || field.fieldType === 'double' || field.fieldType === 'decimal'" v-model="row[field.fieldKey]" size="default" :precision="field.precision || 2" :min="field.min" :max="field.max" style="width: 100%" @change="handleNumberChange(row, field, index)" />
                <el-date-picker v-else-if="field.fieldType === 'DATE' || field.fieldType === 'date'" v-model="row[field.fieldKey]" type="date" size="default" :placeholder="field.placeholder" style="width: 100%" value-format="YYYY-MM-DD" />
                <el-date-picker v-else-if="field.fieldType === 'DATETIME' || field.fieldType === 'datetime'" v-model="row[field.fieldKey]" type="datetime" size="default" :placeholder="field.placeholder" style="width: 100%" value-format="YYYY-MM-DD HH:mm:ss" />
                <el-select v-else-if="field.fieldType === 'SELECT' || field.fieldType === 'select'" v-model="row[field.fieldKey]" size="default" :placeholder="field.placeholder" style="width: 100%" filterable clearable>
                  <el-option v-for="opt in getFieldOptions(field)" :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
                <el-select v-else-if="field.fieldType === 'MULTI_SELECT' || field.fieldType === 'select_multiple'" v-model="row[field.fieldKey]" size="default" :placeholder="field.placeholder" style="width: 100%" multiple filterable clearable>
                  <el-option v-for="opt in getFieldOptions(field)" :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
                <el-input v-else v-model="row[field.fieldKey]" size="default" :placeholder="field.placeholder" />
                <div v-if="getFieldError(index, field.fieldKey)" class="field-error">{{ getFieldError(index, field.fieldKey) }}</div>
              </template>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 表格布局 -->
    <template v-else>
      <el-table 
        :data="rowData" 
        border 
        stripe
        size="small"
        class="sub-form-table"
        :max-height="config.maxHeight || 400"
      >
        <!-- 空状态插槽 -->
        <template #empty>
          <el-empty :description="isRepeatable ? '暂无数据，点击添加' : '暂无数据'" :image-size="60">
            <template v-if="isRepeatable && !disabled && !readonly">
              <el-button type="primary" size="small" @click="addRow">添加明细</el-button>
            </template>
          </el-empty>
        </template>
        <!-- 序号列 -->
        <el-table-column type="index" width="50" align="center" />

        <!-- 动态字段列 -->
        <el-table-column 
          v-for="field in config.fields" 
          :key="field.fieldKey"
          :prop="field.fieldKey"
          :label="field.fieldName"
          :min-width="field.width || 120"
          :show-overflow-tooltip="!field.isEditable"
        >
          <template #default="{ row, $index }">
            <!-- 只读模式 -->
            <template v-if="readonly || disabled || !field.isEditable">
              <span>{{ formatCellValue(row[field.fieldKey], field) }}</span>
            </template>

            <!-- 编辑模式 -->
            <template v-else>
              <!-- 文本输入 -->
              <el-input
                v-if="field.fieldType === 'TEXT' || field.fieldType === 'input'"
                v-model="row[field.fieldKey]"
                size="small"
                :placeholder="field.placeholder"
                @blur="validateField(row, field, $index)"
              />

              <!-- 数字输入 -->
              <el-input-number
                v-else-if="field.fieldType === 'NUMBER' || field.fieldType === 'number'"
                v-model="row[field.fieldKey]"
                size="small"
                :precision="field.precision || 2"
                :min="field.min"
                :max="field.max"
                style="width: 100%"
                @change="handleNumberChange(row, field, $index)"
              />

              <!-- 日期选择 -->
              <el-date-picker
                v-else-if="field.fieldType === 'DATE' || field.fieldType === 'date'"
                v-model="row[field.fieldKey]"
                type="date"
                size="small"
                :placeholder="field.placeholder"
                style="width: 100%"
                value-format="YYYY-MM-DD"
              />

              <!-- 下拉选择 -->
              <el-select
                v-else-if="field.fieldType === 'SELECT' || field.fieldType === 'select'"
                v-model="row[field.fieldKey]"
                size="small"
                :placeholder="field.placeholder"
                style="width: 100%"
                filterable
                clearable
              >
                <el-option
                  v-for="opt in getFieldOptions(field)"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>

              <!-- 下拉多选 -->
              <el-select
                v-else-if="field.fieldType === 'MULTI_SELECT' || field.fieldType === 'select_multiple'"
                v-model="row[field.fieldKey]"
                size="small"
                :placeholder="field.placeholder"
                style="width: 100%"
                multiple
                filterable
                clearable
              >
                <el-option
                  v-for="opt in getFieldOptions(field)"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>

              <!-- 默认文本 -->
              <el-input
                v-else
                v-model="row[field.fieldKey]"
                size="small"
                :placeholder="field.placeholder"
              />

              <!-- 字段校验错误提示 -->
              <div v-if="getFieldError($index, field.fieldKey)" class="field-error">
                {{ getFieldError($index, field.fieldKey) }}
              </div>
            </template>
          </template>
        </el-table-column>

        <!-- 操作列：仅可重复添加时显示 -->
        <el-table-column v-if="isRepeatable && !disabled && !readonly" label="操作" width="80" align="center" fixed="right">
          <template #default="{ $index }">
            <el-button type="danger" size="small" text @click="removeRow($index)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- 校验错误汇总 -->
    <el-alert 
      v-if="validationErrors.length > 0" 
      type="error" 
      :closable="false"
      class="validation-summary"
    >
      <template #title>
        存在 {{ validationErrors.length }} 处错误，请修正
      </template>
    </el-alert>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { Plus, Delete } from '@element-plus/icons-vue'

const props = defineProps({
  // 兼容旧版属性
  refEntityId: String,
  displayMode: {
    type: String,
    default: 'embedded'
  },
  subFormType: String,
  title: String,
  
  // 新版子表单配置
  config: {
    type: Object,
    default: () => ({
      label: '明细',
      fieldKey: 'detailList',
      required: false,
      minRows: 0,
      maxRows: 100,
      fields: [],
      showSummary: false,
      summaryFields: [],
      layout: 'table',
      repeatable: false
    })
  },
  // 数据绑定
  modelValue: {
    type: Array,
    default: () => []
  },
  // 是否只读
  readonly: {
    type: Boolean,
    default: false
  },
  // 是否禁用
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'change', 'validate'])

// 内部数据副本
const rowData = ref([])

// 校验错误
const fieldErrors = ref({})

// 初始化数据
watch(() => props.modelValue, (newVal) => {
  if (newVal && Array.isArray(newVal)) {
    rowData.value = JSON.parse(JSON.stringify(newVal))
  } else {
    rowData.value = []
  }
}, { immediate: true, deep: true })

// 字段加载完成后自动初始化一行空数据（预览/编辑/新增都直接显示表单）
watch(() => props.config.fields, (fields) => {
  if (fields && fields.length > 0 && rowData.value.length === 0) {
    addRow()
  }
}, { immediate: true, deep: true })

// 数据变化时触发更新
watch(rowData, (newVal) => {
  emit('update:modelValue', newVal)
  emit('change', newVal)
  calculateSummary()
}, { deep: true })

// 兜底：挂载后若仍为空则自动初始化，避免异步数据未加载完导致显示空状态
onMounted(() => {
  nextTick(() => {
    if (props.config.fields?.length > 0 && rowData.value.length === 0) {
      addRow()
    }
  })
})

// 最大行数（不可重复时最多1条）
const maxRows = computed(() => isRepeatable.value ? (props.config.maxRows || 100) : 1)

// 最小行数
const minRows = computed(() => props.config.minRows || 0)

// 是否可重复添加（默认false：单条平铺，不显示添加按钮和记录标题）
const isRepeatable = computed(() => props.config.repeatable === true)

// 是否显示汇总
const showSummary = computed(() => {
  return props.config.showSummary && props.config.summaryFields?.length > 0
})

// 汇总数据
const summaryData = ref({})

// 校验错误列表
const validationErrors = computed(() => {
  return Object.values(fieldErrors.value).filter(e => e)
})

// 计算汇总
function calculateSummary() {
  if (!showSummary.value) return
  
  const result = {}
  props.config.summaryFields.forEach(fieldKey => {
    const field = props.config.fields.find(f => f.fieldKey === fieldKey)
    if (!field) return
    
    const sum = rowData.value.reduce((acc, row) => {
      const val = parseFloat(row[fieldKey]) || 0
      return acc + val
    }, 0)
    
    result[fieldKey] = {
      label: field.fieldName,
      value: formatNumber(sum, field.precision || 2)
    }
  })
  
  summaryData.value = result
}

// 格式化数字
function formatNumber(num, precision) {
  return num.toFixed(precision)
}

// 格式化单元格显示
function formatCellValue(value, field) {
  if (value == null || value === '') return '-'
  
  if (field.fieldType === 'NUMBER' || field.fieldType === 'number') {
    return formatNumber(value, field.precision || 2)
  }
  
  if (field.fieldType === 'SELECT' || field.fieldType === 'select') {
    const options = getFieldOptions(field)
    const option = options.find(o => o.value === value)
    return option?.label || value
  }
  
  if (field.fieldType === 'MULTI_SELECT' || field.fieldType === 'select_multiple') {
    const options = getFieldOptions(field)
    if (Array.isArray(value)) {
      const labels = value.map(v => {
        const option = options.find(o => o.value === v)
        return option?.label || v
      })
      return labels.join(', ') || '-'
    }
    const option = options.find(o => o.value === value)
    return option?.label || value || '-'
  }
  
  return value
}

// 获取字段选项
function getFieldOptions(field) {
  if (!field.options) return []
  
  if (typeof field.options === 'string') {
    try {
      return JSON.parse(field.options)
    } catch (e) {
      return []
    }
  }
  
  return field.options
}

// 添加行
function addRow() {
  if (rowData.value.length >= maxRows.value) {
    return
  }
  
  const newRow = {}
  // 初始化字段默认值
  props.config.fields.forEach(field => {
    if (field.defaultValue != null) {
      newRow[field.fieldKey] = field.defaultValue
    } else {
      newRow[field.fieldKey] = ''
    }
  })
  
  rowData.value.push(newRow)
  emit('change', rowData.value)
}

// 删除行
function removeRow(index) {
  if (rowData.value.length <= minRows.value) {
    return
  }
  
  rowData.value.splice(index, 1)
  clearRowErrors(index)
  emit('change', rowData.value)
}

// 数字变化处理
function handleNumberChange(row, field, index) {
  if (props.config.summaryFields?.includes(field.fieldKey)) {
    calculateSummary()
  }
}

// 字段校验
function validateField(row, field, index) {
  const key = `${index}_${field.fieldKey}`
  let error = null
  
  const value = row[field.fieldKey]
  
  if (field.required && (value == null || value === '')) {
    error = `${field.fieldName}不能为空`
  }
  
  if ((field.fieldType === 'NUMBER' || field.fieldType === 'number') && value != null) {
    const num = parseFloat(value)
    if (field.min != null && num < field.min) {
      error = `${field.fieldName}不能小于${field.min}`
    }
    if (field.max != null && num > field.max) {
      error = `${field.fieldName}不能大于${field.max}`
    }
  }
  
  if (error) {
    fieldErrors.value[key] = error
  } else {
    delete fieldErrors.value[key]
  }
  
  return !error
}

// 获取字段错误
function getFieldError(index, fieldKey) {
  return fieldErrors.value[`${index}_${fieldKey}`]
}

// 清除行错误
function clearRowErrors(index) {
  Object.keys(fieldErrors.value).forEach(key => {
    if (key.startsWith(`${index}_`)) {
      delete fieldErrors.value[key]
    }
  })
}

// 校验整个子表单
function validate() {
  let isValid = true
  
  rowData.value.forEach((row, index) => {
    props.config.fields.forEach(field => {
      if (field.isEditable !== false) {
        const valid = validateField(row, field, index)
        if (!valid) isValid = false
      }
    })
  })
  
  if (rowData.value.length < minRows.value) {
    isValid = false
  }
  
  emit('validate', isValid)
  return isValid
}

// 暴露方法
defineExpose({
  validate,
  getData: () => rowData.value
})
</script>

<style scoped>
.sub-form-renderer {
  padding: 10px;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  background-color: #fafafa;
}

.sub-form-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 15px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
}

.sub-form-title {
  font-weight: 500;
  font-size: 14px;
}

.sub-form-summary {
  flex: 1;
  font-size: 13px;
  color: #606266;
}

.summary-item {
  margin-left: 15px;
  color: #409eff;
  font-weight: 500;
}

.sub-form-table {
  margin-top: 10px;
}

.field-error {
  color: #f56c6c;
  font-size: 12px;
  margin-top: 4px;
}

.validation-summary {
  margin-top: 10px;
}

/* 表单布局样式 */
.form-row-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  margin-bottom: 16px;
  padding: 16px;
}

/* 单条记录时去掉卡片样式，直接平铺显示 */
.form-row-card.single-record {
  background: transparent;
  border: none;
  border-radius: 0;
  padding: 0;
  margin-bottom: 0;
}

.form-row-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
}

.row-title {
  font-weight: 500;
  font-size: 14px;
  color: #303133;
}

.form-field-item {
  margin-bottom: 16px;
}

.field-label {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  color: #606266;
  line-height: 1.4;
}

.field-label.required {
  font-weight: 500;
}

.required-tag {
  margin-left: 4px;
  font-size: 10px;
  height: 18px;
  line-height: 16px;
}

.field-control {
  width: 100%;
}

:deep(.el-table__body-wrapper) {
  overflow-x: auto;
}

/* 空状态字段预览 */
.form-fields-preview {
  margin-top: 16px;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 4px;
  border: 1px dashed #dcdfe6;
}

.preview-field-item {
  display: flex;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #ebeef5;
}

.preview-field-item:last-child {
  border-bottom: none;
}

.preview-label {
  width: 120px;
  color: #606266;
  font-size: 14px;
  flex-shrink: 0;
}

.preview-placeholder {
  color: #c0c4cc;
  font-size: 14px;
}
</style>
