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
        <el-button @click="codeRuleVisible = true">
          <el-icon><Ticket /></el-icon>编码规则
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
              <el-tag v-if="field.isSystem" type="info" size="small" effect="plain">系统</el-tag>
              <el-tag v-if="field.isPublished" type="success" size="small" effect="plain" title="该字段已发布到数据库">已发布</el-tag>
              <el-tag v-else-if="!field.isSystem" type="warning" size="small" effect="plain">未发布</el-tag>
            </div>
            <div class="field-actions">
              <el-icon class="action-btn" @click.stop="moveField(index, -1)"><ArrowUp /></el-icon>
              <el-icon class="action-btn" @click.stop="moveField(index, 1)"><ArrowDown /></el-icon>
              <el-icon 
                v-if="!field.isSystem && !field.isPublished" 
                class="action-btn delete" 
                @click.stop="deleteField(index)"
                title="删除字段"
              ><Delete /></el-icon>
            </div>
          </div>
        </div>
      </div>

      <!-- 字段属性配置 -->
      <div class="property-panel">
        <div class="panel-title">属性配置</div>
        <el-form v-if="selectedField" :model="selectedField" label-width="90px" size="small">
          <el-form-item label="字段名称" required>
            <el-input v-model="selectedField.fieldName" placeholder="请输入字段名称" />
          </el-form-item>
          <el-form-item label="字段编码" required>
            <el-input 
              v-model="selectedField.fieldCode" 
              placeholder="请输入字段编码"
              :disabled="selectedField.isPublished"
            />
            <div v-if="selectedField.isPublished" class="form-tip text-warning">
              已发布字段的编码不能修改
            </div>
          </el-form-item>
          <el-form-item label="数据库列名">
            <el-input 
              :model-value="formatDbColumnName(selectedField.fieldCode)" 
              disabled
            />
          </el-form-item>
          <el-form-item label="字段类型" required>
            <el-select 
              v-model="selectedField.fieldType" 
              placeholder="选择类型" 
              style="width: 100%"
              :disabled="selectedField.isPublished"
            >
              <el-option
                v-for="type in fieldTypes"
                :key="type.value"
                :label="type.label"
                :value="type.value"
              />
            </el-select>
            <div v-if="selectedField.isPublished" class="form-tip text-warning">
              已发布字段的类型不能修改
            </div>
          </el-form-item>
          
          <!-- 字段长度配置（文本等字符串类型） -->
          <el-form-item label="字段长度" v-if="showFieldLength">
            <el-input-number 
              v-model="selectedField.fieldLength" 
              :min="1" 
              :max="4000" 
              placeholder="默认200" 
              style="width: 100%"
            />
            <div class="form-tip">对应数据库 VARCHAR 长度</div>
          </el-form-item>
          
          <!-- 小数精度配置（DECIMAL 类型） -->
          <template v-if="selectedField.fieldType === 'DECIMAL'">
            <el-form-item label="总位数">
              <el-input-number 
                v-model="selectedField.fieldLength" 
                :min="1" 
                :max="65" 
                placeholder="默认18" 
                style="width: 100%"
              />
              <div class="form-tip">DECIMAL 总位数（precision）</div>
            </el-form-item>
            <el-form-item label="小数位数">
              <el-input-number 
                v-model="selectedField.fieldPrecision" 
                :min="0" 
                :max="30" 
                placeholder="默认2" 
                style="width: 100%"
              />
              <div class="form-tip">DECIMAL 小数位数（scale）</div>
            </el-form-item>
          </template>
          
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
          
          <!-- 子表单配置 -->
          <template v-if="isSubForm">
            <el-divider>子表单配置</el-divider>
            <el-form-item label="关联实体" required>
              <el-select 
                v-model="selectedField.refEntityId" 
                placeholder="选择关联实体"
                style="width: 100%"
                @change="onRefEntityChange"
              >
                <el-option
                  v-for="entity in availableEntities"
                  :key="entity.id"
                  :label="entity.entityName"
                  :value="entity.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="显示方式">
              <el-radio-group v-model="selectedField.displayMode">
                <el-radio label="embedded">嵌入</el-radio>
                <el-radio label="tab">Tab页</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="关联字段" v-if="selectedField.refEntityId">
              <el-select 
                v-model="selectedField.refFieldCode" 
                placeholder="选择关联字段（用于数据关联）"
                style="width: 100%"
              >
                <el-option
                  v-for="field in refEntityFields"
                  :key="field.fieldCode"
                  :label="field.fieldName"
                  :value="field.fieldCode"
                />
              </el-select>
            </el-form-item>
          </template>
          
          <!-- 附件配置 -->
          <template v-if="isAttachment">
            <el-divider>附件配置</el-divider>
            <div v-for="(item, index) in selectedField.fileItems" :key="index" class="file-item-config">
              <div class="file-item-header">
                <span class="file-item-title">附件项 {{ index + 1 }}</span>
                <el-button type="danger" size="small" text @click="removeFileItem(index)">
                  <el-icon><Delete /></el-icon> 删除
                </el-button>
              </div>
              <el-form-item label="项名称">
                <el-input v-model="item.itemName" placeholder="如：项目章程、需求文档" />
              </el-form-item>
              <el-form-item label="文件类型">
                <el-select 
                  v-model="item.fileTypes" 
                  multiple 
                  placeholder="选择允许的文件类型"
                  style="width: 100%"
                >
                  <el-option label="图片 (.jpg, .jpeg, .png, .gif)" value=".jpg,.jpeg,.png,.gif" />
                  <el-option label="文档 (.pdf, .doc, .docx)" value=".pdf,.doc,.docx" />
                  <el-option label="表格 (.xls, .xlsx)" value=".xls,.xlsx" />
                  <el-option label="文本 (.txt)" value=".txt" />
                  <el-option label="压缩包 (.zip, .rar)" value=".zip,.rar" />
                </el-select>
                <div class="form-tip">不选则表示允许所有类型</div>
              </el-form-item>
              <el-form-item label="单文件大小">
                <el-input-number 
                  v-model="item.maxSize" 
                  :min="1" 
                  :max="100" 
                  placeholder="MB"
                  style="width: 150px"
                />
                <span class="unit-text">MB</span>
              </el-form-item>
              <el-form-item label="数量限制">
                <el-input-number 
                  v-model="item.maxCount" 
                  :min="1" 
                  :max="20" 
                  placeholder="个"
                  style="width: 150px"
                />
                <span class="unit-text">个</span>
              </el-form-item>
            </div>
            <el-button type="primary" size="small" text @click="addFileItem">
              <el-icon><Plus /></el-icon> 添加附件项
            </el-button>
          </template>
          
          <!-- 实体引用配置 -->
          <template v-if="isReference">
            <el-divider>实体引用配置</el-divider>
            <el-form-item label="引用类型" required>
              <el-select 
                v-model="selectedField.refEntityType" 
                placeholder="选择引用类型"
                style="width: 100%"
              >
                <el-option label="用户自定义实体" value="CUSTOM" />
                <el-option label="系统用户" value="USER" />
                <el-option label="系统部门" value="DEPT" />
                <el-option label="系统角色" value="ROLE" />
                <el-option label="系统用户组" value="GROUP" />
              </el-select>
              <div class="form-tip">选择要引用的实体类型</div>
            </el-form-item>
            <el-form-item label="关联实体" v-if="selectedField.refEntityType === 'CUSTOM'" required>
              <el-select 
                v-model="selectedField.refEntityId" 
                placeholder="选择关联实体"
                style="width: 100%"
              >
                <el-option
                  v-for="entity in availableEntities"
                  :key="entity.id"
                  :label="entity.entityName"
                  :value="entity.id"
                />
              </el-select>
              <div class="form-tip">选择用户自定义的业务实体</div>
            </el-form-item>
          </template>
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

    <!-- 编码规则配置对话框 -->
    <el-dialog v-model="codeRuleVisible" title="数据编码规则配置" width="550px">
      <el-form :model="codeRule" label-width="100px" size="default">
        <el-alert type="info" :closable="false" style="margin-bottom: 16px">
          配置实体数据的自动编码规则，默认格式：前缀 + 日期 + 序列号
        </el-alert>
        
        <el-form-item label="编码前缀">
          <el-input v-model="codeRule.prefix" placeholder="如：CG、DD、ORDER" maxlength="20" show-word-limit />
          <div class="form-tip">建议使用大写字母，如采购单用CG，订单用DD</div>
        </el-form-item>
        
        <el-form-item label="日期格式">
          <el-select v-model="codeRule.dateFormat" placeholder="选择日期格式" style="width: 100%">
            <el-option label="yyyyMMdd (如：20240101)" value="yyyyMMdd" />
            <el-option label="yyyy-MM-dd (如：2024-01-01)" value="yyyy-MM-dd" />
            <el-option label="yyyy/MM/dd (如：2024/01/01)" value="yyyy/MM/dd" />
            <el-option label="yyyyMM (如：202401)" value="yyyyMM" />
            <el-option label="yyMMdd (如：240101)" value="yyMMdd" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="序列号位数">
          <el-slider v-model="codeRule.seqLength" :min="3" :max="10" show-stops />
          <div class="form-tip">当前：{{ codeRule.seqLength }}位（格式：{{ '0'.repeat(codeRule.seqLength).replace(/0/g, '0') }}1）</div>
        </el-form-item>
        
        <el-form-item label="重置周期">
          <el-radio-group v-model="codeRule.seqType">
            <el-radio-button label="DAY">按天</el-radio-button>
            <el-radio-button label="MONTH">按月</el-radio-button>
            <el-radio-button label="YEAR">按年</el-radio-button>
            <el-radio-button label="NEVER">不重置</el-radio-button>
          </el-radio-group>
          <div class="form-tip">
            <span v-if="codeRule.seqType === 'DAY'">每天从000001开始编号</span>
            <span v-if="codeRule.seqType === 'MONTH'">每月从000001开始编号</span>
            <span v-if="codeRule.seqType === 'YEAR'">每年从000001开始编号</span>
            <span v-if="codeRule.seqType === 'NEVER'">永远不重置，持续递增</span>
          </div>
        </el-form-item>
        
        <el-divider />
        
        <el-form-item label="编码示例">
          <el-input v-model="codeRule.example" readonly>
            <template #append>
              <el-button @click="previewCode">刷新</el-button>
            </template>
          </el-input>
          <div class="form-tip">根据上述配置生成的编码示例</div>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="codeRuleVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCodeRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { entityApi } from '@/api/entity'
import { codeRuleApi } from '@/api/codeRule'
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
  { value: 'DEPT', label: '部门', icon: 'OfficeBuilding' },
  { value: 'REFERENCE', label: '单选实体', icon: 'Connection' },
  { value: 'MULTI_REFERENCE', label: '多选实体', icon: 'Share' },
  { value: 'SUB_FORM', label: '子表单', icon: 'Grid' },
  { value: 'SUB_FORM_LIST', label: '子表单列表', icon: 'List' }
]

const entityData = ref({})
const fields = ref([])
const selectedField = ref(null)
const previewVisible = ref(false)
const draggedType = ref(null)
const optionsText = ref('')
const refEntityFields = ref([])

// 编码规则配置
const codeRuleVisible = ref(false)
const codeRule = ref({
  entityCode: '',
  prefix: '',
  dateFormat: 'yyyyMMdd',
  seqLength: 6,
  seqType: 'DAY',
  example: ''
})

// 是否显示选项配置
const showOptions = computed(() => {
  return selectedField.value && ['SELECT', 'MULTI_SELECT', 'RADIO', 'CHECKBOX'].includes(selectedField.value.fieldType)
})

// 是否显示字段长度配置（字符串相关类型）
const showFieldLength = computed(() => {
  return selectedField.value && ['STRING', 'TEXT', 'SELECT', 'RADIO', 'MULTI_SELECT', 'CHECKBOX', 'USER', 'DEPT', 'REFERENCE'].includes(selectedField.value.fieldType)
})

// 驼峰转下划线
const formatDbColumnName = (fieldCode) => {
  if (!fieldCode) return ''
  return fieldCode.replace(/([a-z])([A-Z]+)/g, '$1_$2').toLowerCase()
}

// 是否显示子表单配置
const isSubForm = computed(() => {
  return selectedField.value && ['SUB_FORM', 'SUB_FORM_LIST'].includes(selectedField.value.fieldType)
})

// 是否显示附件配置
const isAttachment = computed(() => {
  return selectedField.value && ['FILE', 'IMAGE'].includes(selectedField.value.fieldType)
})

// 是否显示实体引用配置
const isReference = computed(() => {
  return selectedField.value && ['REFERENCE', 'MULTI_REFERENCE'].includes(selectedField.value.fieldType)
})

// 可选的实体列表（排除当前实体）
const availableEntities = ref([])

// 加载可选实体列表
const loadAvailableEntities = async () => {
  try {
    const res = await entityApi.getList()
    availableEntities.value = res.filter(item => item.id !== entityId)
  } catch (error) {
    console.error('加载实体列表失败:', error)
  }
}

// 关联实体变化时加载字段
const onRefEntityChange = async (entityId) => {
  if (!entityId) {
    refEntityFields.value = []
    return
  }
  try {
    const data = await entityApi.getById(entityId)
    refEntityFields.value = data.fields || []
  } catch (error) {
    console.error('加载实体字段失败:', error)
    refEntityFields.value = []
  }
}

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
    fields.value = (data.fields || []).map(f => {
      const field = {
        ...f,
        // fileTypes 在数据库中是逗号分隔字符串，但 el-select multiple 需要数组
        fileTypes: f.fileTypes ? (typeof f.fileTypes === 'string' ? f.fileTypes.split(',') : f.fileTypes) : []
      }
      // fileItems 中的 fileTypes 同样需要转换
      if (field.fileItems && field.fileItems.length > 0) {
        field.fileItems = field.fileItems.map(item => ({
          ...item,
          fileTypes: item.fileTypes ? (typeof item.fileTypes === 'string' ? item.fileTypes.split(',') : item.fileTypes) : []
        }))
      }
      return field
    })
    // 设置编码规则的实体编码
    if (data.entityCode) {
      codeRule.value.entityCode = data.entityCode
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('加载失败')
  }
}

// 加载编码规则
const loadCodeRule = async () => {
  try {
    const data = await codeRuleApi.getByEntityCode(entityData.value.entityCode)
    if (data) {
      codeRule.value = { ...codeRule.value, ...data }
    } else {
      // 使用默认配置
      codeRule.value.prefix = entityData.value.entityCode?.toUpperCase() || ''
      codeRule.value.dateFormat = 'yyyyMMdd'
      codeRule.value.seqLength = 6
      codeRule.value.seqType = 'DAY'
      previewCode()
    }
  } catch (error) {
    console.error('加载编码规则失败:', error)
  }
}

// 预览编码
const previewCode = async () => {
  try {
    const preview = await codeRuleApi.preview(codeRule.value)
    codeRule.value.example = preview
  } catch (error) {
    // 本地计算示例
    const date = new Date()
    const format = codeRule.value.dateFormat || 'yyyyMMdd'
    const dateStr = format
      .replace('yyyy', date.getFullYear())
      .replace('MM', String(date.getMonth() + 1).padStart(2, '0'))
      .replace('dd', String(date.getDate()).padStart(2, '0'))
      .replace(/-/g, '')
      .replace(/\//g, '')
    const seqStr = '1'.padStart(codeRule.value.seqLength || 6, '0')
    codeRule.value.example = (codeRule.value.prefix || '') + dateStr + seqStr
  }
}

// 保存编码规则
const saveCodeRule = async () => {
  try {
    await codeRuleApi.save(codeRule.value)
    ElMessage.success('编码规则保存成功')
    codeRuleVisible.value = false
  } catch (error) {
    console.error(error)
    ElMessage.error('保存失败')
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
  refEntityFields.value = []
  
  // FILE/IMAGE 字段自动初始化 fileItems
  if ((field.fieldType === 'FILE' || field.fieldType === 'IMAGE') && (!field.fileItems || field.fileItems.length === 0)) {
    field.fileItems = [{
      itemName: field.fieldName || '附件',
      fileTypes: field.fileTypes || [],
      maxSize: field.fileMaxSize || 10,
      maxCount: field.fileMaxCount || 5
    }]
  }
  
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
  
  // 如果是子表单字段，加载关联实体的字段
  if (isSubForm.value && field.refEntityId) {
    onRefEntityChange(field.refEntityId)
  }
}

// 删除字段
const deleteField = (index) => {
  const field = fields.value[index]
  if (field.isPublished) {
    ElMessage.warning('已发布的字段不能删除，请先修改字段配置')
    return
  }
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
  const tags = { 
    'STRING': '', 
    'TEXT': 'info', 
    'INTEGER': 'success', 
    'DECIMAL': 'success', 
    'DATE': 'warning', 
    'DATETIME': 'warning',
    'REFERENCE': 'primary',
    'MULTI_REFERENCE': 'primary'
  }
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
    fieldCode: field.fieldCode,
    fieldType: field.fieldType,
    isRequired: field.isRequired,
    defaultValue: field.defaultValue,
    optionsJson: field.optionsJson,
    // 子表单/实体引用相关属性
    refEntityId: field.refEntityId,
    refEntityType: field.refEntityType,
    displayMode: field.displayMode,
    refFieldCode: field.refFieldCode,
    // 附件相关属性
    fileTypes: field.fileTypes,
    fileMaxSize: field.fileMaxSize,
    fileMaxCount: field.fileMaxCount
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
        id: f.id?.startsWith('temp_') ? null : f.id,
        // fileTypes 是数组，需要转为逗号分隔字符串传给后端
        fileTypes: Array.isArray(f.fileTypes) ? f.fileTypes.join(',') : f.fileTypes,
        // fileItems 中的 fileTypes 同样需要转换
        fileItems: f.fileItems ? f.fileItems.map(item => ({
          ...item,
          fileTypes: Array.isArray(item.fileTypes) ? item.fileTypes.join(',') : item.fileTypes
        })) : []
      }))
    })
    ElMessage.success('保存成功')
    loadEntity()
  } catch (error) {
    console.error(error)
    ElMessage.error('保存失败')
  }
}

// 添加附件项
const addFileItem = () => {
  if (!selectedField.value.fileItems) {
    selectedField.value.fileItems = []
  }
  selectedField.value.fileItems.push({
    itemName: '',
    fileTypes: [],
    maxSize: 10,
    maxCount: 5
  })
}

// 删除附件项
const removeFileItem = (index) => {
  if (selectedField.value.fileItems) {
    selectedField.value.fileItems.splice(index, 1)
  }
}

// 拖拽开始
const handleDragStart = (type) => {
  draggedType.value = type
}

onMounted(() => {
  loadEntity()
  loadAvailableEntities()
  loadCodeRule()
})
</script>

<style scoped>
.entity-design {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
}

/* ===== 头部样式 ===== */
.design-header {
  height: 60px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-left :deep(.el-button) {
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: #fff;
  backdrop-filter: blur(10px);
  transition: all 0.3s;
}

.header-left :deep(.el-button:hover) {
  background: rgba(255, 255, 255, 0.35);
  border-color: rgba(255, 255, 255, 0.5);
}

.entity-name {
  font-size: 18px;
  font-weight: 600;
  color: #fff;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.header-right {
  display: flex;
  gap: 12px;
}

.header-right :deep(.el-button) {
  border-radius: 6px;
  padding: 8px 20px;
}

.header-right :deep(.el-button:first-child) {
  background: rgba(255, 255, 255, 0.9);
  border: none;
  color: #606266;
}

.header-right :deep(.el-button:first-child:hover) {
  background: #fff;
  color: #409eff;
}

/* ===== 主体布局 ===== */
.design-body {
  flex: 1;
  display: flex;
  overflow: hidden;
  padding: 16px;
  gap: 16px;
}

/* ===== 左侧字段类型面板 ===== */
.field-types-panel {
  width: 200px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  padding: 20px;
  display: flex;
  flex-direction: column;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 2px solid #f0f2f5;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.field-type-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  overflow-y: auto;
  padding-right: 4px;
}

.field-type-list::-webkit-scrollbar {
  width: 4px;
}

.field-type-list::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 2px;
}

.field-type-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px 8px;
  background: #fafbfc;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.25s ease;
}

.field-type-item:hover {
  background: #fff;
  border-color: #409eff;
  color: #409eff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.25);
  transform: translateY(-2px);
}

.field-type-item .el-icon {
  font-size: 22px;
  margin-bottom: 6px;
  transition: transform 0.2s;
}

.field-type-item:hover .el-icon {
  transform: scale(1.1);
}

.field-type-item span {
  font-size: 12px;
  font-weight: 500;
}

/* ===== 中间字段列表面板 ===== */
.fields-panel {
  flex: 1;
  min-width: 450px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.fields-panel .panel-title {
  margin: 0;
  padding: 16px 20px;
  background: #fafbfc;
  border-bottom: 1px solid #ebeef5;
}

.fields-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #f8f9fa;
}

.field-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  margin-bottom: 10px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.25s ease;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
}

.field-item:hover {
  border-color: #409eff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
  transform: translateX(4px);
}

.field-item.active {
  border-color: #409eff;
  background: linear-gradient(135deg, #ecf5ff 0%, #f5f7ff 100%);
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.2);
}

.field-info {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.field-info .field-name {
  font-weight: 600;
  color: #303133;
  font-size: 14px;
  flex-shrink: 0;
}

.field-info .field-code {
  font-size: 12px;
  color: #909399;
  background: #f4f4f5;
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
}

.field-info .el-tag {
  flex-shrink: 0;
  border-radius: 4px;
  font-weight: 500;
}

.field-actions {
  display: flex;
  gap: 6px;
  opacity: 0.6;
  transition: opacity 0.2s;
}

.field-item:hover .field-actions {
  opacity: 1;
}

.action-btn {
  cursor: pointer;
  color: #409eff;
  font-size: 16px;
  padding: 6px;
  border-radius: 6px;
  transition: all 0.2s;
}

.action-btn:hover {
  background: #ecf5ff;
}

.action-btn.delete {
  color: #f56c6c;
}

.action-btn.delete:hover {
  background: #fef0f0;
}

/* ===== 右侧属性面板 ===== */
.property-panel {
  width: 360px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.property-panel .panel-title {
  margin: 0;
  padding: 16px 20px;
  background: linear-gradient(135deg, #fafbfc 0%, #f5f7fa 100%);
  border-bottom: 1px solid #ebeef5;
}

.property-panel :deep(.el-form) {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.property-panel :deep(.el-form-item) {
  margin-bottom: 18px;
}

.property-panel :deep(.el-form-item__label) {
  font-weight: 500;
  color: #606266;
}

.property-panel :deep(.el-input__inner),
.property-panel :deep(.el-textarea__inner) {
  border-radius: 8px;
  transition: all 0.3s;
}

.property-panel :deep(.el-input__inner:focus),
.property-panel :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.empty-tip {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 14px;
  background: linear-gradient(135deg, #fafbfc 0%, #f5f7fa 100%);
}

.empty-tip::before {
  content: '';
  display: inline-block;
  width: 60px;
  height: 60px;
  margin-bottom: 16px;
  background: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23c0c4cc'%3E%3Cpath d='M9 2v2H7v2h2v2H7v2h2v2H7v2h2v2H7v2h2v2h6v-2h2v-2h-2v-2h2v-2h-2v-2h2V8h-2V6h2V4h-2V2H9zm2 2h2v2h-2V4zm0 4h2v2h-2V8zm0 4h2v2h-2v-2zm0 4h2v2h-2v-2z'/%3E%3C/svg%3E") no-repeat center;
  background-size: contain;
  opacity: 0.5;
}

/* ===== 滚动条美化 ===== */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: #909399;
}

/* ===== 附件项配置样式 ===== */
.file-item-config {
  background: #f8f9fb;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
  border: 1px solid #e4e7ed;
}

.file-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.file-item-title {
  font-weight: 600;
  color: #303133;
  font-size: 13px;
}

/* ===== 响应式调整 ===== */
@media (max-width: 1200px) {
  .property-panel {
    width: 320px;
  }
}
</style>
