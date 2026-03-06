<template>
  <div class="entity-design">
    <div class="design-header">
      <div class="header-left">
        <el-button @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        <span class="entity-name">{{ entityData.entityName || '实体设计' }}</span>
      </div>
      <div class="header-right">
        <el-button @click="handlePreview">
          <el-icon><View /></el-icon>预览
        </el-button>
        <el-button type="primary" @click="handleSave">
          <el-icon><Check /></el-icon>保存
        </el-button>
      </div>
    </div>

    <div class="design-body">
      <!-- 字段类型面板 -->
      <div class="field-types-panel">
        <div class="panel-title">字段类型</div>
        <div class="field-type-list">
          <div
            v-for="type in fieldTypes"
            :key="type.value"
            class="field-type-item"
            draggable="true"
            @dragstart="handleDragStart(type)"
            @click="handleAddField(type)"
          >
            <el-icon><component :is="type.icon" /></el-icon>
            <span>{{ type.label }}</span>
          </div>
        </div>
      </div>

      <!-- 字段列表 -->
      <div class="fields-panel">
        <div class="panel-title">
          字段列表
          <el-button type="primary" size="small" @click="handleAddField()">
            <el-icon><Plus /></el-icon>添加
          </el-button>
        </div>
        <div class="fields-list">
          <div
            v-for="(field, index) in fields"
            :key="field.id || index"
            class="field-item"
            :class="{ active: selectedField === field }"
            @click="selectField(field)"
          >
            <div class="field-info">
              <span class="field-name">{{ field.fieldName || '未命名' }}</span>
              <span class="field-code">{{ field.fieldCode || '-' }}</span>
              <el-tag size="small" :type="getFieldTypeTag(field.fieldType)">
                {{ getFieldTypeLabel(field.fieldType) }}
              </el-tag>
              <el-tag v-if="field.isRequired" type="danger" size="small" effect="plain">必填</el-tag>
            </div>
            <div class="field-actions">
              <el-icon class="action-btn" @click.stop="moveField(index, -1)"><ArrowUp /></el-icon>
              <el-icon class="action-btn" @click.stop="moveField(index, 1)"><ArrowDown /></el-icon>
              <el-icon class="action-btn delete" @click.stop="deleteField(index)"><Delete /></el-icon>
            </div>
          </div>
        </div>
      </div>

      <!-- 字段属性配置 -->
      <div class="property-panel">
        <div class="panel-title">属性配置</div>
        <el-form v-if="selectedField" :model="selectedField" label-width="100px" size="small">
          <el-form-item label="字段名称" required>
            <el-input v-model="selectedField.fieldName" placeholder="请输入字段名称" />
          </el-form-item>
          <el-form-item label="字段编码" required>
            <el-input v-model="selectedField.fieldCode" placeholder="请输入字段编码" />
          </el-form-item>
          <el-form-item label="字段类型" required>
            <el-select v-model="selectedField.fieldType" placeholder="选择类型" style="width: 100%">
              <el-option
                v-for="type in fieldTypes"
                :key="type.value"
                :label="type.label"
                :value="type.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="是否必填">
            <el-switch v-model="selectedField.isRequired" />
          </el-form-item>
          <el-form-item label="是否唯一">
            <el-switch v-model="selectedField.isUnique" />
          </el-form-item>
          <el-form-item label="列表显示">
            <el-switch v-model="selectedField.showInList" />
          </el-form-item>
          <el-form-item label="表单显示">
            <el-switch v-model="selectedField.showInForm" />
          </el-form-item>
          <el-form-item label="查询条件">
            <el-switch v-model="selectedField.isQuery" />
          </el-form-item>
          <el-form-item label="默认值">
            <el-input v-model="selectedField.defaultValue" placeholder="请输入默认值" />
          </el-form-item>
          <el-form-item label="选项配置" v-if="showOptions">
            <el-input
              v-model="optionsText"
              type="textarea"
              rows="4"
              placeholder="每行一个选项，格式：value:label"
            />
          </el-form-item>
          <el-form-item label="验证规则">
            <el-input
              v-model="selectedField.validateRules"
              type="textarea"
              rows="2"
              placeholder="JSON格式验证规则"
            />
          </el-form-item>
        </el-form>
        <div v-else class="empty-tip">请选择字段进行配置</div>
      </div>
    </div>

    <!-- 预览对话框 -->
    <el-dialog v-model="previewVisible" title="表单预览" width="600px">
      <el-form label-width="100px">
        <el-form-item
          v-for="field in fields"
          :key="field.fieldCode"
          :label="field.fieldName"
          :required="field.isRequired"
        >
          <FormFieldRenderer :field="convertToFormField(field)" />
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { entityApi } from '@/api/entity'
import FormFieldRenderer from '@/components/FormFieldRenderer.vue'

const route = useRoute()
const router = useRouter()
const entityId = route.params.id

// 字段类型定义
const fieldTypes = [
  { value: 'STRING', label: '文本', icon: 'Document' },
  { value: 'TEXT', label: '长文本', icon: 'Tickets' },
  { value: 'INTEGER', label: '整数', icon: 'Sort' },
  { value: 'DECIMAL', label: '小数', icon: 'Money' },
  { value: 'DATE', label: '日期', icon: 'Calendar' },
  { value: 'DATETIME', label: '日期时间', icon: 'Timer' },
  { value: 'BOOLEAN', label: '布尔', icon: 'Check' },
  { value: 'SELECT', label: '下拉选择', icon: 'ArrowDown' },
  { value: 'MULTI_SELECT', label: '多选', icon: 'Collection' },
  { value: 'RADIO', label: '单选', icon: 'CircleCheck' },
  { value: 'CHECKBOX', label: '复选框', icon: 'Checked' },
  { value: 'FILE', label: '文件', icon: 'DocumentChecked' },
  { value: 'IMAGE', label: '图片', icon: 'Picture' },
  { value: 'USER', label: '用户', icon: 'User' },
  { value: 'DEPT', label: '部门', icon: 'OfficeBuilding' }
]

const entityData = ref({})
const fields = ref([])
const selectedField = ref(null)
const previewVisible = ref(false)
const draggedType = ref(null)
const optionsText = ref('')

// 是否显示选项配置
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

// 加载实体数据
const loadEntity = async () => {
  try {
    const data = await entityApi.getById(entityId)
    entityData.value = data
    fields.value = data.fields || []
  } catch (error) {
    console.error(error)
    ElMessage.error('加载失败')
  }
}

// 添加字段
const handleAddField = (type) => {
  const newField = {
    id: 'temp_' + Date.now(),
    fieldName: '',
    fieldCode: '',
    fieldType: type?.value || 'STRING',
    isRequired: false,
    isUnique: false,
    showInList: true,
    showInForm: true,
    isQuery: false,
    sortOrder: fields.value.length
  }
  fields.value.push(newField)
  selectField(newField)
}

// 选择字段
const selectField = (field) => {
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

// 删除字段
const deleteField = (index) => {
  fields.value.splice(index, 1)
  if (selectedField.value && !fields.value.find(f => f === selectedField.value)) {
    selectedField.value = null
  }
}

// 移动字段
const moveField = (index, direction) => {
  const newIndex = index + direction
  if (newIndex < 0 || newIndex >= fields.value.length) return
  const temp = fields.value[index]
  fields.value[index] = fields.value[newIndex]
  fields.value[newIndex] = temp
  // 更新排序
  fields.value.forEach((f, i) => f.sortOrder = i)
}

// 获取字段类型标签
const getFieldTypeTag = (type) => {
  const tags = { 'STRING': '', 'TEXT': 'info', 'INTEGER': 'success', 'DECIMAL': 'success', 'DATE': 'warning', 'DATETIME': 'warning' }
  return tags[type] || ''
}

const getFieldTypeLabel = (type) => {
  const found = fieldTypes.find(t => t.value === type)
  return found?.label || type
}

// 转换为表单字段格式
const convertToFormField = (field) => {
  return {
    fieldName: field.fieldName,
    fieldKey: field.fieldCode,
    fieldType: field.fieldType,
    isRequired: field.isRequired,
    defaultValue: field.defaultValue,
    optionsJson: field.optionsJson
  }
}

// 预览
const handlePreview = () => {
  previewVisible.value = true
}

// 保存
const handleSave = async () => {
  // 验证字段
  for (const field of fields.value) {
    if (!field.fieldName || !field.fieldCode) {
      ElMessage.warning('请完善字段信息')
      return
    }
  }

  try {
    await entityApi.update(entityId, {
      ...entityData.value,
      fields: fields.value.map(f => ({
        ...f,
        // 移除临时ID
        id: f.id?.startsWith('temp_') ? null : f.id
      }))
    })
    ElMessage.success('保存成功')
    loadEntity()
  } catch (error) {
    console.error(error)
    ElMessage.error('保存失败')
  }
}

// 拖拽开始
const handleDragStart = (type) => {
  draggedType.value = type
}

onMounted(() => {
  loadEntity()
})
</script>

<style scoped>
.entity-design {
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

.entity-name {
  font-size: 16px;
  font-weight: bold;
}

.design-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.field-types-panel {
  width: 180px;
  background: #fff;
  border-right: 1px solid #dcdfe6;
  padding: 15px;
}

.panel-title {
  font-weight: bold;
  margin-bottom: 15px;
  padding-bottom: 10px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.field-type-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.field-type-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
}

.field-type-item:hover {
  border-color: #409eff;
  color: #409eff;
  background: #ecf5ff;
}

.field-type-item .el-icon {
  font-size: 20px;
  margin-bottom: 5px;
}

.field-type-item span {
  font-size: 12px;
}

.fields-panel {
  width: 350px;
  background: #fff;
  border-right: 1px solid #dcdfe6;
  display: flex;
  flex-direction: column;
}

.fields-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
}

.field-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  margin-bottom: 8px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
}

.field-item:hover {
  border-color: #409eff;
}

.field-item.active {
  border-color: #409eff;
  background: #ecf5ff;
}

.field-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field-name {
  font-weight: bold;
}

.field-code {
  font-size: 12px;
  color: #909399;
}

.field-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  cursor: pointer;
  color: #409eff;
  font-size: 16px;
}

.action-btn.delete {
  color: #f56c6c;
}

.property-panel {
  flex: 1;
  background: #f5f7fa;
  padding: 15px;
  overflow-y: auto;
}

.empty-tip {
  text-align: center;
  color: #909399;
  padding: 50px 0;
}
</style>
