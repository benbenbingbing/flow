<template>
  <div class="form-design">
    <div class="design-header">
      <div class="header-left">
        <el-button @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        <span class="title">表单设计</span>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="handleSave">
          <el-icon><Check /></el-icon>保存表单
        </el-button>
        <el-button @click="handlePreview">
          <el-icon><View /></el-icon>预览
        </el-button>
      </div>
    </div>
    
    <div class="design-body">
      <div class="component-panel">
        <div class="panel-title">组件库</div>
        <div class="component-list">
          <div
            v-for="item in componentList"
            :key="item.type"
            class="component-item"
            draggable="true"
            @dragstart="handleDragStart(item)"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </div>
        </div>
      </div>
      
      <div class="canvas-panel">
        <div
          class="form-canvas"
          @dragover.prevent
          @drop="handleDrop"
        >
          <div v-if="formFields.length === 0" class="empty-tip">
            从左侧拖拽组件到此处
          </div>
          <div
            v-for="(field, index) in formFields"
            :key="field.id"
            class="form-field"
            :class="{ active: selectedField?.id === field.id }"
            @click="handleSelectField(field)"
          >
            <div class="field-drag-handle">⋮⋮</div>
            <div class="field-content">
              <el-form-item :label="field.fieldName" :required="field.isRequired">
                <FormFieldRenderer :field="field" :disabled="true" />
              </el-form-item>
            </div>
            <div class="field-actions">
              <el-icon class="action-btn" @click.stop="handleCopyField(index)"><CopyDocument /></el-icon>
              <el-icon class="action-btn delete" @click.stop="handleDeleteField(index)"><Delete /></el-icon>
            </div>
          </div>
        </div>
      </div>
      
      <div class="property-panel">
        <div class="panel-title">属性配置</div>
        <el-form v-if="selectedField" :model="selectedField" label-width="80px" size="small">
          <el-form-item label="字段名称">
            <el-input v-model="selectedField.fieldName" />
          </el-form-item>
          <el-form-item label="字段标识">
            <el-input v-model="selectedField.fieldKey" />
          </el-form-item>
          <el-form-item label="是否必填">
            <el-switch v-model="selectedField.isRequired" />
          </el-form-item>
          <el-form-item label="默认值">
            <el-input v-model="selectedField.defaultValue" :placeholder="showOptions ? '请输入选项的 value 值（如 1）' : '请输入默认值'" />
            <div v-if="showOptions" class="form-tip">默认值应填写选项的 value（key），而非显示文本 label</div>
          </el-form-item>
          <el-form-item label="选项" v-if="showOptions">
            <el-input
              v-model="optionsText"
              type="textarea"
              rows="4"
              placeholder="每行一个选项，格式：value:label"
            />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="selectedField.sortOrder" :min="0" />
          </el-form-item>
        </el-form>
        <div v-else class="empty-property">
          请选择字段进行配置
        </div>
      </div>
    </div>
    
    <!-- 预览对话框 -->
    <el-dialog v-model="previewVisible" title="表单预览" width="600px">
      <el-form label-width="100px">
        <el-form-item
          v-for="field in formFields"
          :key="field.id"
          :label="field.fieldName"
          :required="field.isRequired"
        >
          <FormFieldRenderer :field="field" />
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import FormFieldRenderer from '@/components/FormFieldRenderer.vue'

const route = useRoute()
const nodeId = route.params.nodeId

const componentList = [
  { type: 'TEXT', label: '文本', icon: 'Document' },
  { type: 'TEXTAREA', label: '多行文本', icon: 'Tickets' },
  { type: 'NUMBER', label: '数字', icon: 'Sort' },
  { type: 'DATE', label: '日期', icon: 'Calendar' },
  { type: 'DATETIME', label: '日期时间', icon: 'Timer' },
  { type: 'SELECT', label: '选择', icon: 'ArrowDown' },
  { type: 'RADIO', label: '选择（单选框）', icon: 'CircleCheck' },
  { type: 'CHECKBOX', label: '选择（复选框）', icon: 'Checked' },
  { type: 'FILE', label: '文件', icon: 'DocumentChecked' },
  { type: 'USER', label: '用户选择', icon: 'User' }
]

const formFields = ref([])
const selectedField = ref(null)
const previewVisible = ref(false)
const draggedComponent = ref(null)
const optionsText = ref('')

const showOptions = computed(() => {
  return selectedField.value && ['SELECT', 'MULTI_SELECT', 'RADIO', 'CHECKBOX'].includes(selectedField.value.fieldType)
})

// 监听选项文本变化
watch(optionsText, (val) => {
  if (selectedField.value && showOptions.value) {
    const options = val.split('\n').map(line => {
      const [value, label] = line.split(':')
      return { value: value?.trim(), label: label?.trim() || value?.trim() }
    }).filter(opt => opt.value)
    selectedField.value.optionsJson = JSON.stringify(options)
  }
})

const handleDragStart = (item) => {
  draggedComponent.value = item
}

const handleDrop = () => {
  if (!draggedComponent.value) return
  
  const field = {
    id: Date.now().toString(),
    fieldName: draggedComponent.value.label,
    fieldKey: `field_${Date.now()}`,
    fieldType: draggedComponent.value.type,
    isRequired: false,
    defaultValue: '',
    optionsJson: '',
    sortOrder: formFields.value.length
  }
  
  formFields.value.push(field)
  selectedField.value = field
  draggedComponent.value = null
}

const handleSelectField = (field) => {
  selectedField.value = field
  if (showOptions.value && field.optionsJson) {
    try {
      const options = JSON.parse(field.optionsJson)
      optionsText.value = options.map(opt => `${opt.value}:${opt.label}`).join('\n')
    } catch (e) {
      optionsText.value = ''
    }
  } else {
    optionsText.value = ''
  }
}

const handleCopyField = (index) => {
  const field = formFields.value[index]
  const newField = {
    ...field,
    id: Date.now().toString(),
    fieldKey: `${field.fieldKey}_copy`,
    sortOrder: formFields.value.length
  }
  formFields.value.splice(index + 1, 0, newField)
}

const handleDeleteField = (index) => {
  formFields.value.splice(index, 1)
  if (selectedField.value && !formFields.value.find(f => f.id === selectedField.value.id)) {
    selectedField.value = null
  }
}

const handleSave = () => {
  console.log('保存表单:', formFields.value)
  ElMessage.success('保存成功')
}

const handlePreview = () => {
  previewVisible.value = true
}
</script>

<style scoped>
.form-design {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.design-header {
  height: 50px;
  background: #fff;
  border-bottom: 1px solid #dcdfe6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.title {
  font-size: 16px;
  font-weight: bold;
}

.design-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.component-panel {
  width: 200px;
  background: #fff;
  border-right: 1px solid #dcdfe6;
  padding: 15px;
}

.panel-title {
  font-weight: bold;
  margin-bottom: 15px;
  padding-bottom: 10px;
  border-bottom: 1px solid #e4e7ed;
}

.component-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.component-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: move;
  transition: all 0.3s;
}

.component-item:hover {
  border-color: #409eff;
  color: #409eff;
}

.canvas-panel {
  flex: 1;
  background: #f5f7fa;
  padding: 20px;
  overflow-y: auto;
}

.form-canvas {
  min-height: 100%;
  background: #fff;
  border-radius: 4px;
  padding: 20px;
}

.empty-tip {
  text-align: center;
  color: #909399;
  padding: 100px 0;
}

.form-field {
  display: flex;
  align-items: center;
  padding: 10px;
  margin-bottom: 10px;
  border: 1px solid transparent;
  border-radius: 4px;
  transition: all 0.3s;
}

.form-field:hover {
  background: #f5f7fa;
  border-color: #dcdfe6;
}

.form-field.active {
  border-color: #409eff;
  background: #ecf5ff;
}

.field-drag-handle {
  color: #909399;
  cursor: move;
  margin-right: 10px;
}

.field-content {
  flex: 1;
}

.field-actions {
  display: none;
  gap: 10px;
}

.form-field:hover .field-actions {
  display: flex;
}

.action-btn {
  cursor: pointer;
  color: #409eff;
}

.action-btn.delete {
  color: #f56c6c;
}

.property-panel {
  width: 300px;
  background: #fff;
  border-left: 1px solid #dcdfe6;
  padding: 15px;
}

.empty-property {
  text-align: center;
  color: #909399;
  padding: 50px 0;
}
</style>
