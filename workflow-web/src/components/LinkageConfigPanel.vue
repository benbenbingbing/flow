/**
 * 字段联动配置面板
 * 在表单设计器中配置字段的显隐、联动、计算规则
 */

<template>
  <div class="linkage-config-panel">
    <div class="panel-header">
      <span class="title"><el-icon><Connection /></el-icon> 字段联动配置</span>
      <el-tag v-if="hasLinkage" type="success" size="small" effect="dark">已启用</el-tag>
    </div>
    
    <el-tabs v-model="activeTab" type="border-card" class="linkage-tabs">
      <!-- 显隐控制 -->
      <el-tab-pane label="显隐控制" name="visibility">
        <div class="tab-content">
          <el-alert type="info" :closable="false" class="tab-tip">
            根据条件动态显示或隐藏此字段
          </el-alert>
          
          <div class="condition-builder">
            <div class="condition-header">
              <span>当满足以下条件时显示：</span>
              <el-switch v-model="config.visibilityEnabled" />
            </div>
            
            <template v-if="config.visibilityEnabled">
              <div class="condition-list">
                <div 
                  v-for="(condition, index) in visibilityConditions" 
                  :key="index"
                  class="condition-item"
                >
                  <el-select v-model="condition.field" placeholder="选择字段" size="small" style="width: 120px">
                    <el-option
                      v-for="f in availableFields"
                      :key="f.fieldCode || f.fieldKey"
                      :label="f.fieldName"
                      :value="f.fieldCode || f.fieldKey"
                      :disabled="(f.fieldCode || f.fieldKey) === currentFieldKey"
                    />
                  </el-select>
                  
                  <el-select v-model="condition.operator" placeholder="操作符" size="small" style="width: 100px">
                    <el-option label="等于" value="==" />
                    <el-option label="不等于" value="!=" />
                    <el-option label="大于" value=">" />
                    <el-option label="小于" value="<" />
                    <el-option label="大于等于" value=">=" />
                    <el-option label="小于等于" value="<=" />
                    <el-option label="包含" value="contains" />
                    <el-option label="为空" value="empty" />
                    <el-option label="不为空" value="notEmpty" />
                  </el-select>
                  
                  <el-input 
                    v-if="!['empty', 'notEmpty'].includes(condition.operator)"
                    v-model="condition.value" 
                    placeholder="值" 
                    size="small" 
                    style="width: 100px"
                  />
                  
                  <el-button type="danger" size="small" text @click="removeVisibilityCondition(index)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
              
              <el-button type="primary" size="small" text @click="addVisibilityCondition">
                <el-icon><Plus /></el-icon> 添加条件
              </el-button>
              
              <div class="logic-selector">
                <span>条件组合方式：</span>
                <el-radio-group v-model="config.visibilityLogic" size="small">
                  <el-radio-button label="and">全部满足</el-radio-button>
                  <el-radio-button label="or">任一满足</el-radio-button>
                </el-radio-group>
              </div>
            </template>
          </div>
        </div>
      </el-tab-pane>
      
      <!-- 值联动 -->
      <el-tab-pane label="值联动" name="value">
        <div class="tab-content">
          <el-alert type="info" :closable="false" class="tab-tip">
            根据其他字段的值自动填充此字段
          </el-alert>
          
          <div class="value-linkage-builder">
            <el-form label-width="100px" size="small">
              <el-form-item label="启用值联动">
                <el-switch v-model="config.valueLinkageEnabled" />
              </el-form-item>
              
              <template v-if="config.valueLinkageEnabled">
                <el-form-item label="数据来源">
                  <el-radio-group v-model="config.valueSourceType">
                    <el-radio label="field">字段值</el-radio>
                    <el-radio label="api">接口查询</el-radio>
                    <el-radio label="formula">计算公式</el-radio>
                  </el-radio-group>
                </el-form-item>
                
                <!-- 字段值来源 -->
                <template v-if="config.valueSourceType === 'field'">
                  <el-form-item label="源字段">
                    <el-select v-model="config.sourceField" placeholder="选择源字段" style="width: 100%">
                      <el-option
                        v-for="f in availableFields"
                        :key="f.fieldCode || f.fieldKey"
                        :label="f.fieldName"
                        :value="f.fieldCode || f.fieldKey"
                        :disabled="(f.fieldCode || f.fieldKey) === currentFieldKey"
                      />
                    </el-select>
                  </el-form-item>
                  
                  <el-form-item label="映射规则">
                    <div class="mapping-rules">
                      <div v-for="(rule, index) in valueMappingRules" :key="index" class="mapping-item">
                        <el-input v-model="rule.sourceValue" placeholder="源值" size="small" style="width: 100px" />
                        <span class="arrow">→</span>
                        <el-input v-model="rule.targetValue" placeholder="目标值" size="small" style="width: 100px" />
                        
                        <el-button type="danger" size="small" text @click="removeValueMapping(index)">
                          <el-icon><Delete /></el-icon>
                        </el-button>
                      </div>
                      
                      <el-button type="primary" size="small" text @click="addValueMapping">
                        <el-icon><Plus /></el-icon> 添加映射
                      </el-button>
                    </div>
                  </el-form-item>
                </template>
                
                <!-- 接口查询来源 -->
                <template v-if="config.valueSourceType === 'api'">
                  <el-form-item label="接口地址">
                    <el-input v-model="config.apiUrl" placeholder="如：/api/region/getByParentId" />
                  </el-form-item>
                  
                  <el-form-item label="请求参数">
                    <el-input 
                      v-model="config.apiParams" 
                      type="textarea" 
                      :rows="2"
                      placeholder='{"parentId": "${sourceField}"}' 
                    />
                  </el-form-item>
                  
                  <el-form-item label="结果字段">
                    <el-input v-model="config.apiResultField" placeholder="如：data.name" />
                  </el-form-item>
                </template>
                
                <!-- 计算公式来源 -->
                <template v-if="config.valueSourceType === 'formula'">
                  <el-form-item label="计算公式">
                    <el-input 
                      v-model="config.valueFormula" 
                      type="textarea" 
                      :rows="3"
                      placeholder="如：${amount} * ${price} * ${discount}"
                    />
                    <div class="formula-help">
                      <p>支持的运算符：+ - * / ( )</p>
                      <p>使用 ${字段名} 引用字段值</p>
                    </div>
                  </el-form-item>
                </template>
              </template>
            </el-form>
          </div>
        </div>
      </el-tab-pane>
      
      <!-- 选项联动 -->
      <el-tab-pane label="选项联动" name="options">
        <div class="tab-content">
          <el-alert type="info" :closable="false" class="tab-tip">
            根据其他字段的值动态过滤下拉选项
          </el-alert>
          
          <div class="options-linkage-builder">
            <el-form label-width="100px" size="small">
              <el-form-item label="启用选项联动">
                <el-switch v-model="config.optionsLinkageEnabled" />
              </el-form-item>
              
              <template v-if="config.optionsLinkageEnabled">
                <el-form-item label="依赖字段">
                  <el-select v-model="config.optionsDependField" placeholder="选择依赖字段" style="width: 100%">
                    <el-option
                      v-for="f in availableFields"
                      :key="f.fieldCode || f.fieldKey"
                      :label="f.fieldName"
                      :value="f.fieldCode || f.fieldKey"
                      :disabled="(f.fieldCode || f.fieldKey) === currentFieldKey"
                    />
                  </el-select>
                </el-form-item>
                
                <el-form-item label="选项过滤规则">
                  <div class="filter-rules">
                    <div v-for="(rule, index) in optionsFilterRules" :key="index" class="filter-item">
                      <div class="filter-header">
                        <span>当 {{ config.optionsDependField }} =</span>
                        <el-input v-model="rule.dependValue" placeholder="值" size="small" style="width: 100px" />
                        <span>时显示：</span>
                        
                        <el-button type="danger" size="small" text @click="removeFilterRule(index)">
                          <el-icon><Delete /></el-icon>
                        </el-button>
                      </div>
                      
                      <el-select 
                        v-model="rule.allowedOptions" 
                        multiple 
                        placeholder="选择要显示的选项" 
                        style="width: 100%"
                      >
                        <el-option 
                          v-for="opt in currentFieldOptions" 
                          :key="opt.value" 
                          :label="opt.label" 
                          :value="opt.value"
                        />
                      </el-select>
                    </div>
                    
                    <el-button type="primary" size="small" text @click="addFilterRule">
                      <el-icon><Plus /></el-icon> 添加过滤规则
                    </el-button>
                  </div>
                </el-form-item>
              </template>
            </el-form>
          </div>
        </div>
      </el-tab-pane>
      
      <!-- 计算字段 -->
      <el-tab-pane label="计算字段" name="calculation">
        <div class="tab-content">
          <el-alert type="info" :closable="false" class="tab-tip">
            根据公式自动计算字段值（如：数量 * 单价 = 总价）
          </el-alert>
          
          <div class="calculation-builder">
            <el-form label-width="100px" size="small">
              <el-form-item label="启用计算">
                <el-switch v-model="config.calculationEnabled" />
              </el-form-item>
              
              <template v-if="config.calculationEnabled">
                <el-form-item label="计算公式">
                  <el-input 
                    v-model="config.calculationFormula" 
                    type="textarea" 
                    :rows="3"
                    placeholder="如：${quantity} * ${price} * (1 - ${discount})"
                  />
                </el-form-item>
                
                <el-form-item label="计算精度">
                  <el-input-number v-model="config.calculationPrecision" :min="0" :max="10" />
                  <span class="unit">位小数</span>
                </el-form-item>
                
                <el-form-item label="可编辑">
                  <el-switch v-model="config.calculationEditable" />
                  <span class="hint">关闭后用户无法修改计算结果</span>
                </el-form-item>
                
                <div class="formula-preview" v-if="config.calculationFormula">
                  <div class="preview-title">公式预览</div>
                  <div class="preview-content">
                    <code>{{ formatFormula(config.calculationFormula) }}</code>
                  </div>
                </div>
              </template>
            </el-form>
          </div>
        </div>
      </el-tab-pane>
      
      <!-- 禁用/必填联动 -->
      <el-tab-pane label="禁用/必填" name="state">
        <div class="tab-content">
          <el-form label-width="100px" size="small">
            <el-form-item label="禁用条件">
              <el-switch v-model="config.disabledEnabled" />
            </el-form-item>
            
            <template v-if="config.disabledEnabled">
              <el-form-item label="条件表达式">
                <el-input 
                  v-model="config.disabledCondition" 
                  placeholder="如：${status} == 'locked'"
                />
              </el-form-item>
            </template>
            
            <el-form-item label="必填条件">
              <el-switch v-model="config.requiredEnabled" />
            </el-form-item>
            
            <template v-if="config.requiredEnabled">
              <el-form-item label="条件表达式">
                <el-input 
                  v-model="config.requiredCondition" 
                  placeholder="如：${type} == 'urgent'"
                />
              </el-form-item>
            </template>
          </el-form>
        </div>
      </el-tab-pane>
    </el-tabs>
    
    <div class="panel-footer">
      <el-button type="primary" @click="saveConfig">保存配置</el-button>
      <el-button @click="resetConfig">重置</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Connection, Plus, Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  field: {
    type: Object,
    required: true
  },
  allFields: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['save'])

const activeTab = ref('visibility')

// 配置数据
const config = ref({
  // 显隐控制
  visibilityEnabled: false,
  visibilityLogic: 'and',
  visibilityConditions: [],
  
  // 值联动
  valueLinkageEnabled: false,
  valueSourceType: 'field',
  sourceField: '',
  valueFormula: '',
  apiUrl: '',
  apiParams: '',
  apiResultField: '',
  
  // 选项联动
  optionsLinkageEnabled: false,
  optionsDependField: '',
  optionsFilterRules: [],
  
  // 计算字段
  calculationEnabled: false,
  calculationFormula: '',
  calculationPrecision: 2,
  calculationEditable: false,
  
  // 禁用/必填
  disabledEnabled: false,
  disabledCondition: '',
  requiredEnabled: false,
  requiredCondition: ''
})

// 当前字段的 key
const currentFieldKey = computed(() => props.field?.fieldKey || props.field?.fieldCode)

// 可用的字段（排除当前字段）
const availableFields = computed(() => {
  return props.allFields.filter(f => {
    const key = f.fieldKey || f.fieldCode
    return key !== currentFieldKey.value
  })
})

// 当前字段的选项
const currentFieldOptions = computed(() => {
  const field = props.field
  if (!field) return []
  
  if (field.options) {
    return typeof field.options === 'string' 
      ? JSON.parse(field.options) 
      : field.options
  }
  
  if (field.optionsJson) {
    try {
      return JSON.parse(field.optionsJson)
    } catch (e) {
      return []
    }
  }
  
  return []
})

// 是否有联动配置
const hasLinkage = computed(() => {
  return config.value.visibilityEnabled ||
         config.value.valueLinkageEnabled ||
         config.value.optionsLinkageEnabled ||
         config.value.calculationEnabled ||
         config.value.disabledEnabled ||
         config.value.requiredEnabled
})

// 显隐条件
const visibilityConditions = computed({
  get() {
    return config.value.visibilityConditions || []
  },
  set(val) {
    config.value.visibilityConditions = val
  }
})

// 值映射规则
const valueMappingRules = ref([])

// 选项过滤规则
const optionsFilterRules = computed({
  get() {
    return config.value.optionsFilterRules || []
  },
  set(val) {
    config.value.optionsFilterRules = val
  }
})

// 添加显隐条件
function addVisibilityCondition() {
  visibilityConditions.value.push({
    field: '',
    operator: '==',
    value: ''
  })
}

// 删除显隐条件
function removeVisibilityCondition(index) {
  visibilityConditions.value.splice(index, 1)
}

// 添加值映射
function addValueMapping() {
  valueMappingRules.value.push({
    sourceValue: '',
    targetValue: ''
  })
}

// 删除值映射
function removeValueMapping(index) {
  valueMappingRules.value.splice(index, 1)
}

// 添加选项过滤规则
function addFilterRule() {
  optionsFilterRules.value.push({
    dependValue: '',
    allowedOptions: []
  })
}

// 删除选项过滤规则
function removeFilterRule(index) {
  optionsFilterRules.value.splice(index, 1)
}

// 格式化公式显示
function formatFormula(formula) {
  return formula.replace(/\$\{(\w+)\}/g, '${$1}')
}

// 保存配置
function saveConfig() {
  // 构建联动规则 JSON
  const linkageRules = buildLinkageRules()
  emit('save', linkageRules)
  ElMessage.success('联动配置已保存')
}

// 构建联动规则
function buildLinkageRules() {
  const rules = {}
  
  // 显隐规则
  if (config.value.visibilityEnabled && visibilityConditions.value.length > 0) {
    const conditions = visibilityConditions.value.map(c => {
      if (c.operator === 'empty') return `!${c.field} || ${c.field} == ''`
      if (c.operator === 'notEmpty') return `${c.field} && ${c.field} != ''`
      if (c.operator === 'contains') return `${c.field}.contains('${c.value}')`
      return `${c.field} ${c.operator} '${c.value}'`
    })
    rules.visibilityRule = config.value.visibilityLogic === 'and' 
      ? conditions.join(' && ')
      : conditions.join(' || ')
  }
  
  // 值联动规则
  if (config.value.valueLinkageEnabled) {
    if (config.value.valueSourceType === 'formula') {
      rules.valueFormula = config.value.valueFormula
    }
  }
  
  // 选项联动规则
  if (config.value.optionsLinkageEnabled && config.value.optionsDependField) {
    rules.optionsLinkage = {
      dependsOn: config.value.optionsDependField,
      filterRules: {}
    }
    optionsFilterRules.value.forEach(rule => {
      rules.optionsLinkage.filterRules[rule.dependValue] = rule.allowedOptions
    })
  }
  
  // 计算字段规则
  if (config.value.calculationEnabled && config.value.calculationFormula) {
    rules.calculationFormula = config.value.calculationFormula
    rules.calculationPrecision = config.value.calculationPrecision
    rules.calculationEditable = config.value.calculationEditable
  }
  
  // 禁用规则
  if (config.value.disabledEnabled && config.value.disabledCondition) {
    rules.disabledRule = config.value.disabledCondition
  }
  
  // 必填规则
  if (config.value.requiredEnabled && config.value.requiredCondition) {
    rules.requiredRule = config.value.requiredCondition
  }
  
  return rules
}

// 重置配置
function resetConfig() {
  config.value = {
    visibilityEnabled: false,
    visibilityLogic: 'and',
    visibilityConditions: [],
    valueLinkageEnabled: false,
    valueSourceType: 'field',
    sourceField: '',
    valueFormula: '',
    apiUrl: '',
    apiParams: '',
    apiResultField: '',
    optionsLinkageEnabled: false,
    optionsDependField: '',
    optionsFilterRules: [],
    calculationEnabled: false,
    calculationFormula: '',
    calculationPrecision: 2,
    calculationEditable: false,
    disabledEnabled: false,
    disabledCondition: '',
    requiredEnabled: false,
    requiredCondition: ''
  }
  valueMappingRules.value = []
}

// 监听字段变化，加载已有配置
watch(() => props.field, (newField) => {
  if (newField?.linkageRules) {
    // 解析已有配置
    parseLinkageRules(newField.linkageRules)
  } else {
    resetConfig()
  }
}, { immediate: true })

// 反向解析显隐规则字符串为条件数组
function parseVisibilityRuleString(ruleStr) {
  if (!ruleStr) return []
  const conditions = []
  // 先判断逻辑关系
  const isOr = ruleStr.includes(' || ')
  config.value.visibilityLogic = isOr ? 'or' : 'and'
  // 按 && 或 || 分割
  const parts = ruleStr.split(/\s*\|\|\s*|\s*&&\s*/)
  parts.forEach(part => {
    part = part.trim()
    if (!part) return
    // 解析 empty: !field || field == ''
    const emptyMatch = part.match(/^!(\w+)\s*\|\|\s*\1\s*==\s*''$/)
    if (emptyMatch) {
      conditions.push({ field: emptyMatch[1], operator: 'empty', value: '' })
      return
    }
    // 解析 notEmpty: field && field != ''
    const notEmptyMatch = part.match(/^(\w+)\s*&&\s*\1\s*!=\s*''$/)
    if (notEmptyMatch) {
      conditions.push({ field: notEmptyMatch[1], operator: 'notEmpty', value: '' })
      return
    }
    // 解析 contains: field.contains('value')
    const containsMatch = part.match(/^(\w+)\.contains\('([^']*)'\)$/)
    if (containsMatch) {
      conditions.push({ field: containsMatch[1], operator: 'contains', value: containsMatch[2] })
      return
    }
    // 解析标准比较: field == 'value', field != 'value', field > 'value' 等
    const stdMatch = part.match(/^(\w+)\s*(==|!=|>|>=|<|<=)\s*'([^']*)'$/)
    if (stdMatch) {
      conditions.push({ field: stdMatch[1], operator: stdMatch[2], value: stdMatch[3] })
      return
    }
    // 解析无引号的数字比较
    const numMatch = part.match(/^(\w+)\s*(==|!=|>|>=|<|<=)\s*([\d.]+)$/)
    if (numMatch) {
      conditions.push({ field: numMatch[1], operator: numMatch[2], value: numMatch[3] })
      return
    }
    // 解析简单字段引用（如 field == variable）
    const varMatch = part.match(/^(\w+)\s*(==|!=|>|>=|<|<=)\s*(\w+)$/)
    if (varMatch) {
      conditions.push({ field: varMatch[1], operator: varMatch[2], value: varMatch[3] })
      return
    }
  })
  return conditions
}

// 解析已有联动规则
function parseLinkageRules(rules) {
  if (!rules) return

  // 显隐规则
  if (rules.visibilityRule) {
    config.value.visibilityEnabled = true
    const parsed = parseVisibilityRuleString(rules.visibilityRule)
    if (parsed.length > 0) {
      config.value.visibilityConditions = parsed
    }
  }

  // 值联动
  if (rules.valueFormula) {
    config.value.valueLinkageEnabled = true
    config.value.valueSourceType = 'formula'
    config.value.valueFormula = rules.valueFormula
  }

  // 选项联动
  if (rules.optionsLinkage) {
    config.value.optionsLinkageEnabled = true
    config.value.optionsDependField = rules.optionsLinkage.dependsOn
    optionsFilterRules.value = Object.entries(rules.optionsLinkage.filterRules || {}).map(([key, value]) => ({
      dependValue: key,
      allowedOptions: value
    }))
  }

  // 计算字段
  if (rules.calculationFormula) {
    config.value.calculationEnabled = true
    config.value.calculationFormula = rules.calculationFormula
    config.value.calculationPrecision = rules.calculationPrecision || 2
    config.value.calculationEditable = rules.calculationEditable || false
  }

  // 禁用/必填
  if (rules.disabledRule) {
    config.value.disabledEnabled = true
    config.value.disabledCondition = rules.disabledRule
  }
  if (rules.requiredRule) {
    config.value.requiredEnabled = true
    config.value.requiredCondition = rules.requiredRule
  }
}
</script>

<style scoped>
.linkage-config-panel {
  padding: 15px;
  background: #f5f7fa;
  border-radius: 4px;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 15px;
  padding-bottom: 10px;
  border-bottom: 1px solid #e4e7ed;
}

.panel-header .title {
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 5px;
}

.linkage-tabs {
  background: #fff;
}

.tab-content {
  padding: 15px;
}

.tab-tip {
  margin-bottom: 15px;
}

.condition-builder,
.value-linkage-builder,
.options-linkage-builder,
.calculation-builder {
  background: #fff;
  padding: 15px;
  border-radius: 4px;
}

.condition-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.condition-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 15px;
}

.condition-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logic-selector {
  margin-top: 15px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.mapping-rules,
.filter-rules {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.mapping-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.arrow {
  color: #909399;
}

.filter-item {
  padding: 10px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fafafa;
}

.filter-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.formula-help {
  margin-top: 10px;
  padding: 10px;
  background: #f4f4f5;
  border-radius: 4px;
  font-size: 12px;
  color: #606266;
}

.formula-help p {
  margin: 5px 0;
}

.formula-preview {
  margin-top: 15px;
  padding: 10px;
  background: #f0f9ff;
  border: 1px solid #b3d8ff;
  border-radius: 4px;
}

.preview-title {
  font-size: 12px;
  color: #606266;
  margin-bottom: 5px;
}

.preview-content code {
  font-family: monospace;
  font-size: 13px;
  color: #409eff;
}

.unit {
  margin-left: 8px;
  color: #606266;
}

.hint {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}

.panel-footer {
  margin-top: 15px;
  padding-top: 15px;
  border-top: 1px solid #e4e7ed;
  display: flex;
  gap: 10px;
}

.checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
