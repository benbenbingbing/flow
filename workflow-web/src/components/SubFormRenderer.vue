<template>
  <div class="sub-form-renderer">
    <!-- 嵌入模式：直接显示子表单 -->
    <div v-if="displayMode === 'embedded'" class="sub-form-embedded">
      <el-card shadow="never" class="sub-form-card">
        <template #header v-if="title">
          <span>{{ title }}</span>
        </template>
        
        <!-- 列表模式 -->
        <template v-if="isListMode">
          <div class="sub-form-toolbar" v-if="!disabled">
            <el-button type="primary" size="small" @click="addRow">
              <el-icon><Plus /></el-icon>添加
            </el-button>
          </div>
          <el-table :data="tableData" border size="small">
            <el-table-column 
              v-for="col in columns" 
              :key="col.fieldCode"
              :prop="col.fieldCode"
              :label="col.fieldName"
              min-width="120"
            >
              <template #default="{ row, $index }">
                <FormFieldRenderer 
                  :field="col"
                  v-model="row[col.fieldCode]"
                  :disabled="disabled"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" v-if="!disabled">
              <template #default="{ $index }">
                <el-button type="danger" link size="small" @click="deleteRow($index)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
        
        <!-- 单条模式 -->
        <template v-else>
          <el-form label-width="100px">
            <el-form-item
              v-for="col in columns"
              :key="col.fieldCode"
              :label="col.fieldName"
            >
              <FormFieldRenderer 
                :field="col"
                v-model="formData[col.fieldCode]"
                :disabled="disabled"
              />
            </el-form-item>
          </el-form>
        </template>
      </el-card>
    </div>
    
    <!-- Tab 模式：以 Tab 页签形式显示 -->
    <div v-else-if="displayMode === 'tab'" class="sub-form-tab">
      <el-tabs type="border-card">
        <el-tab-pane :label="title || '子表单'">
          <!-- 列表模式 -->
          <template v-if="isListMode">
            <div class="sub-form-toolbar" v-if="!disabled">
              <el-button type="primary" size="small" @click="addRow">
                <el-icon><Plus /></el-icon>添加
              </el-button>
            </div>
            <el-table :data="tableData" border size="small">
              <el-table-column 
                v-for="col in columns" 
                :key="col.fieldCode"
                :prop="col.fieldCode"
                :label="col.fieldName"
                min-width="120"
              >
                <template #default="{ row, $index }">
                  <FormFieldRenderer 
                    :field="col"
                    v-model="row[col.fieldCode]"
                    :disabled="disabled"
                  />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80" v-if="!disabled">
                <template #default="{ $index }">
                  <el-button type="danger" link size="small" @click="deleteRow($index)">
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>
          
          <!-- 单条模式 -->
          <template v-else>
            <el-form label-width="100px">
              <el-form-item
                v-for="col in columns"
                :key="col.fieldCode"
                :label="col.fieldName"
              >
                <FormFieldRenderer 
                  :field="col"
                  v-model="formData[col.fieldCode]"
                  :disabled="disabled"
                />
              </el-form-item>
            </el-form>
          </template>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import FormFieldRenderer from './FormFieldRenderer.vue'
import { entityApi } from '@/api/entity'

const props = defineProps({
  // 关联实体ID
  refEntityId: {
    type: String,
    required: true
  },
  // 显示模式：embedded-嵌入, tab-Tab页
  displayMode: {
    type: String,
    default: 'embedded'
  },
  // 子表单类型：sub_form-单条, sub_form_list-列表
  subFormType: {
    type: String,
    default: 'sub_form'
  },
  // 标题
  title: {
    type: String,
    default: ''
  },
  // 数据值
  modelValue: {
    type: [Object, Array],
    default: () => null
  },
  // 是否禁用
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

// 关联实体的字段列表
const columns = ref([])

// 是否是列表模式
const isListMode = computed(() => props.subFormType === 'SUB_FORM_LIST' || props.subFormType === 'sub_form_list')

// 表格数据（列表模式）
const tableData = computed({
  get() {
    const val = props.modelValue
    if (!val) return []
    return Array.isArray(val) ? val : [val]
  },
  set(val) {
    if (isListMode.value) {
      emit('update:modelValue', val)
    } else {
      emit('update:modelValue', val[0] || {})
    }
  }
})

// 表单数据（单条模式）
const formData = computed({
  get() {
    const val = props.modelValue
    if (!val) return {}
    return Array.isArray(val) ? (val[0] || {}) : val
  },
  set(val) {
    if (isListMode.value) {
      emit('update:modelValue', [val])
    } else {
      emit('update:modelValue', val)
    }
  }
})

// 加载关联实体的字段
const loadRefEntityFields = async () => {
  if (!props.refEntityId) return
  try {
    const data = await entityApi.getById(props.refEntityId)
    if (data && data.fields) {
      // 过滤掉子表单字段，避免嵌套过深
      columns.value = data.fields.filter(f => 
        f.fieldType !== 'SUB_FORM' && f.fieldType !== 'SUB_FORM_LIST'
      ).map(f => ({
        fieldCode: f.fieldCode,
        fieldName: f.fieldName,
        fieldType: f.fieldType,
        componentType: getComponentType(f.fieldType),
        optionsJson: f.optionsJson,
        placeholder: f.placeholder
      }))
    }
  } catch (error) {
    console.error('加载关联实体字段失败:', error)
  }
}

// 获取组件类型
const getComponentType = (fieldType) => {
  const typeMap = {
    'STRING': 'input',
    'TEXT': 'textarea',
    'INTEGER': 'number',
    'DECIMAL': 'number',
    'DATE': 'date',
    'DATETIME': 'datetime',
    'BOOLEAN': 'switch',
    'SELECT': 'select',
    'MULTI_SELECT': 'checkbox',
    'RADIO': 'radio',
    'CHECKBOX': 'checkbox',
    'FILE': 'file',
    'IMAGE': 'file',
    'USER': 'input',
    'DEPT': 'input'
  }
  return typeMap[fieldType] || 'input'
}

// 添加行（列表模式）
const addRow = () => {
  const newRow = {}
  columns.value.forEach(col => {
    newRow[col.fieldCode] = ''
  })
  const newData = [...tableData.value, newRow]
  tableData.value = newData
}

// 删除行（列表模式）
const deleteRow = (index) => {
  const newData = [...tableData.value]
  newData.splice(index, 1)
  tableData.value = newData
}

onMounted(() => {
  loadRefEntityFields()
})

// 监听关联实体ID变化
watch(() => props.refEntityId, () => {
  loadRefEntityFields()
})
</script>

<style scoped>
.sub-form-renderer {
  width: 100%;
}

.sub-form-embedded {
  margin-top: 8px;
}

.sub-form-card {
  background-color: #f5f7fa;
}

.sub-form-card :deep(.el-card__header) {
  padding: 10px 15px;
  font-weight: 500;
}

.sub-form-card :deep(.el-card__body) {
  padding: 15px;
}

.sub-form-toolbar {
  margin-bottom: 10px;
}

.sub-form-tab :deep(.el-tabs__content) {
  padding: 15px;
}
</style>
