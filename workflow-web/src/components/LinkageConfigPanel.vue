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
      <el-tab-pane label="显示与状态" name="display-state">
        <div class="tab-content">
          <SettingsSection
            title="显示与状态条件"
            description="分别配置字段显示、禁用和必填条件"
            :collapsible="false"
            primary
          >
            <el-alert type="info" :closable="false" class="tab-tip">
              三类规则独立生效，每类规则都支持嵌套条件组及 AND / OR 组合。
            </el-alert>

            <div class="condition-rule-list">
              <LinkageConditionRuleEditor
                title="显示条件"
                description="满足条件时显示当前字段；未启用时始终显示。"
                v-model:enabled="config.visibilityEnabled"
                :root="config.visibilityConditionRoot"
                :fields="availableFields"
                :parse-warning="conditionParseWarnings.visibility"
                @change="clearConditionWarning('visibility')"
                @reset-group="resetConditionGroup('visibility')"
              />
              <LinkageConditionRuleEditor
                title="禁用条件"
                description="满足条件时禁用当前字段，使字段可见但不可编辑。"
                v-model:enabled="config.disabledEnabled"
                :root="config.disabledConditionRoot"
                :fields="availableFields"
                :parse-warning="conditionParseWarnings.disabled"
                @change="clearConditionWarning('disabled')"
                @reset-group="resetConditionGroup('disabled')"
              />
              <LinkageConditionRuleEditor
                title="必填条件"
                description="满足条件时把当前字段设为必填，并参与表单提交校验。"
                v-model:enabled="config.requiredEnabled"
                :root="config.requiredConditionRoot"
                :fields="availableFields"
                :parse-warning="conditionParseWarnings.required"
                @change="clearConditionWarning('required')"
                @reset-group="resetConditionGroup('required')"
              />
            </div>
          </SettingsSection>
        </div>
      </el-tab-pane>

      <el-tab-pane label="值与计算" name="value-calculation">
        <div class="tab-content">
          <SettingsSection
            title="值联动"
            description="从字段映射、受限公式或历史接口配置生成当前字段值"
            :collapsible="false"
            primary
          >
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
                    <el-radio-group v-model="config.valueSourceType" class="source-type-group">
                      <el-radio value="field">字段值</el-radio>
                      <el-radio value="formula">计算公式</el-radio>
                      <el-radio value="api">历史接口（高级）</el-radio>
                    </el-radio-group>
                  </el-form-item>

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

                    <el-alert type="info" :closable="false" size="small" style="margin-bottom: 8px">
                      当<b>源字段</b>的值等于<b>源值</b>时，当前字段自动填充为<b>目标值</b>
                    </el-alert>

                    <el-form-item label="映射规则">
                      <div class="mapping-rules">
                        <div v-for="(rule, index) in valueMappingRules" :key="index" class="mapping-item">
                          <el-input v-model="rule.sourceValue" placeholder="源字段的值" size="small" />
                          <span class="arrow">→</span>
                          <el-input v-model="rule.targetValue" placeholder="当前字段显示的值" size="small" />

                          <el-button type="danger" size="small" text aria-label="删除值映射" title="删除值映射" @click="removeValueMapping(index)">
                            <el-icon><Delete /></el-icon>
                          </el-button>
                        </div>

                        <el-button type="primary" size="small" text @click="addValueMapping">
                          <el-icon><Plus /></el-icon> 添加映射
                        </el-button>
                      </div>
                    </el-form-item>
                  </template>

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

                  <template v-if="config.valueSourceType === 'api'">
                    <el-alert
                      type="warning"
                      :closable="false"
                      show-icon
                      class="controlled-source-tip"
                      title="生产环境请使用受控 Provider / Connector"
                      description="以下字段仅保留历史配置兼容。生产环境应从统一数据源目录选择 Provider 或 Connector，并由平台统一处理凭据、权限、超时与审计；不建议新增任意接口地址。"
                    />

                    <SettingsSection
                      title="受控数据源 / 高级兼容"
                      description="维护历史 apiUrl、apiParams、apiResultField，不改变原保存字段"
                      :default-expanded="false"
                      class="legacy-api-section"
                    >
                      <el-form-item label="接口地址">
                        <el-input
                          v-model="config.apiUrl"
                          placeholder="历史兼容地址，如：/api/region/getByParentId"
                        />
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
                    </SettingsSection>
                  </template>
                </template>
              </el-form>
            </div>
          </SettingsSection>

          <SettingsSection
            title="计算字段"
            description="按公式持续计算字段值，并控制精度与可编辑性"
            :default-expanded="config.calculationEnabled"
          >
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

                  <div v-if="config.calculationFormula" class="formula-preview">
                    <div class="preview-title">公式预览</div>
                    <div class="preview-content">
                      <code>{{ formatFormula(config.calculationFormula) }}</code>
                    </div>
                  </div>
                </template>
              </el-form>
            </div>
          </SettingsSection>
        </div>
      </el-tab-pane>

      <el-tab-pane label="选项" name="options">
        <div class="tab-content">
          <SettingsSection
            title="选项联动"
            description="根据依赖字段动态过滤当前字段可选项"
            :collapsible="false"
            primary
          >
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
                          <el-input v-model="rule.dependValue" placeholder="值" size="small" class="filter-depend-value" />
                          <span>时显示：</span>

                          <el-button type="danger" size="small" text aria-label="删除选项过滤规则" title="删除选项过滤规则" @click="removeFilterRule(index)">
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
          </SettingsSection>
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
import { reactive, ref, computed, watch } from 'vue'
import { Connection, Plus, Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import SettingsSection from '@/components/SettingsSection.vue'
import LinkageConditionRuleEditor from '@/components/LinkageConditionRuleEditor.vue'
import { getProcessConditionFieldType } from '@/shared/process-config'
import { LinkageEngine } from '../utils/linkageEngine'
import {
  buildFlowConditionExpression,
  createFlowConditionConfig,
  createFlowConditionGroup,
  isFlowConditionGroupComplete,
  parseFlowConditionConfig,
  parseFlowConditionExpression
} from '@/utils/flowConditionGroups'

const props = defineProps({
  field: {
    type: Object,
    required: true
  },
  allFields: {
    type: Array,
    default: () => []
  },
  initialTab: { type: String, default: 'display-state' }
})

const emit = defineEmits(['save'])
const activeTab = ref('display-state')

function createDefaultConfig() {
  return {
    // 显隐控制
    visibilityEnabled: false,
    visibilityConditionRoot: createFlowConditionGroup(),

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
    disabledConditionRoot: createFlowConditionGroup(),
    requiredEnabled: false,
    requiredConditionRoot: createFlowConditionGroup()
  }
}

const config = ref(createDefaultConfig())
const conditionParseWarnings = reactive({
  visibility: '',
  disabled: '',
  required: ''
})
const legacyConditionRules = reactive({
  visibility: '',
  disabled: '',
  required: ''
})
const legacyConditionConfigs = reactive({
  visibility: null,
  disabled: null,
  required: null
})

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

  // 从 componentProps 解析选项
  if (field.componentProps) {
    try {
      const compProps = typeof field.componentProps === 'string'
        ? JSON.parse(field.componentProps)
        : field.componentProps
      if (compProps && compProps.options) return compProps.options
    } catch (e) {}
  }

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
  const invalidRule = conditionRuleDefinitions.find(definition =>
    config.value[definition.enabledKey]
      && !conditionParseWarnings[definition.name]
      && !isFlowConditionGroupComplete(
        config.value[definition.rootKey]))
  if (invalidRule) {
    ElMessage.warning(`${invalidRule.label}至少需要一个完整条件`)
    return
  }
  // 构建联动规则 JSON
  const linkageRules = buildLinkageRules()
  emit('save', linkageRules)
  ElMessage.success('联动配置已保存')
}

const conditionRuleDefinitions = [
  {
    name: 'visibility',
    label: '显示条件',
    enabledKey: 'visibilityEnabled',
    rootKey: 'visibilityConditionRoot',
    configKey: 'visibilityConditionConfig',
    expressionKey: 'visibilityRule'
  },
  {
    name: 'disabled',
    label: '禁用条件',
    enabledKey: 'disabledEnabled',
    rootKey: 'disabledConditionRoot',
    configKey: 'disabledConditionConfig',
    expressionKey: 'disabledRule'
  },
  {
    name: 'required',
    label: '必填条件',
    enabledKey: 'requiredEnabled',
    rootKey: 'requiredConditionRoot',
    configKey: 'requiredConditionConfig',
    expressionKey: 'requiredRule'
  }
]

// 将裸字段名转为 ${field} 格式
function wrapFieldRefs(expr) {
  if (!expr || expr.includes('${')) return expr
  return expr.replace(/\b([a-zA-Z_]\w*)\b/g, '${$1}')
}

// 构建联动规则
function buildLinkageRules() {
  const rules = {}
  
  conditionRuleDefinitions.forEach(definition => {
    appendConditionRule(rules, definition)
  })
  
  // 值联动规则
  if (config.value.valueLinkageEnabled) {
    if (config.value.valueSourceType === 'formula') {
      rules.valueFormula = wrapFieldRefs(config.value.valueFormula)
    } else if (config.value.valueSourceType === 'field') {
      rules.valueMapping = {
        sourceField: config.value.sourceField,
        rules: valueMappingRules.value
      }
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
    rules.calculationFormula = wrapFieldRefs(config.value.calculationFormula)
    rules.calculationPrecision = config.value.calculationPrecision
    rules.calculationEditable = config.value.calculationEditable
  }
  
  return rules
}

function appendConditionRule(rules, definition) {
  if (!config.value[definition.enabledKey]) return
  if (conditionParseWarnings[definition.name]) {
    if (legacyConditionConfigs[definition.name]) {
      rules[definition.configKey] =
        legacyConditionConfigs[definition.name]
    }
    if (legacyConditionRules[definition.name]) {
      rules[definition.expressionKey] =
        legacyConditionRules[definition.name]
    }
    return
  }
  const root = config.value[definition.rootKey]
  if (!isFlowConditionGroupComplete(root)) return
  rules[definition.configKey] = createFlowConditionConfig(root)
  rules[definition.expressionKey] = buildFlowConditionExpression(
    root,
    getConditionFieldType)
}

function getConditionFieldType(fieldCode) {
  const field = availableFields.value.find(item =>
    (item.fieldCode || item.fieldKey) === fieldCode)
  return getProcessConditionFieldType(field)
}

// 重置配置
function resetConfig() {
  config.value = createDefaultConfig()
  valueMappingRules.value = []
  conditionRuleDefinitions.forEach(definition => {
    conditionParseWarnings[definition.name] = ''
    legacyConditionRules[definition.name] = ''
    legacyConditionConfigs[definition.name] = null
  })
}

watch(() => props.initialTab, initialTab => {
  if (['display-state', 'value-calculation', 'options'].includes(initialTab)) {
    activeTab.value = initialTab
  }
}, { immediate: true })

watch(() => props.field, newField => {
  resetConfig()
  const rules = LinkageEngine.getFieldLinkageRules(newField)
  if (Object.keys(rules).length > 0) {
    parseLinkageRules(rules)
  }
}, { immediate: true })

// 解析已有联动规则
function parseLinkageRules(rules) {
  if (!rules) return

  conditionRuleDefinitions.forEach(definition => {
    parseConditionRule(rules, definition)
  })

  // 值联动
  if (rules.valueMapping) {
    config.value.valueLinkageEnabled = true
    config.value.valueSourceType = 'field'
    config.value.sourceField = rules.valueMapping.sourceField
    valueMappingRules.value = rules.valueMapping.rules || []
  } else if (rules.valueFormula) {
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

}

function parseConditionRule(rules, definition) {
  const expression = rules[definition.expressionKey]
  const savedRoot = parseFlowConditionConfig(rules[definition.configKey])
  const completeSavedRoot = savedRoot && isFlowConditionGroupComplete(savedRoot)
    ? savedRoot
    : null
  const parsedRoot = completeSavedRoot || parseFlowConditionExpression(expression)
  if (!rules[definition.configKey] && !expression) return
  config.value[definition.enabledKey] = true
  if (parsedRoot) {
    config.value[definition.rootKey] = parsedRoot
    return
  }
  legacyConditionConfigs[definition.name] =
    rules[definition.configKey] || null
  legacyConditionRules[definition.name] = expression || ''
  conditionParseWarnings[definition.name] =
    '原配置会继续保留且不会被自动覆盖。若要使用条件组，请先确认并清空原配置。'
}

function clearConditionWarning(name) {
  conditionParseWarnings[name] = ''
  legacyConditionRules[name] = ''
  legacyConditionConfigs[name] = null
}

function resetConditionGroup(name) {
  const definition = conditionRuleDefinitions.find(
    item => item.name === name)
  if (!definition) return
  config.value[definition.rootKey] = createFlowConditionGroup()
  clearConditionWarning(name)
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

.tab-content :deep(.settings-section:last-child) {
  margin-bottom: 0;
}

.tab-tip {
  margin-bottom: 15px;
}

.condition-rule-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.value-linkage-builder,
.options-linkage-builder,
.calculation-builder {
  background: #fff;
  padding: 15px;
  border-radius: 4px;
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

.mapping-item .el-input {
  min-width: 0;
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

.filter-depend-value {
  width: 100px;
}

.source-type-group {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
}

.source-type-group :deep(.el-radio) {
  margin-right: 0;
}

.controlled-source-tip {
  margin: 4px 0 12px;
}

.legacy-api-section {
  margin-top: 8px;
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

@media (max-width: 720px) {
  .linkage-config-panel {
    padding: 8px;
  }

  .panel-header {
    flex-wrap: wrap;
    margin-bottom: 10px;
  }

  .linkage-tabs :deep(.el-tabs__content) {
    padding: 8px;
  }

  .tab-content {
    padding: 4px 0;
  }

  .tab-content :deep(.settings-section__header) {
    padding: 8px;
  }

  .tab-content :deep(.settings-section__heading small) {
    overflow: visible;
    text-overflow: clip;
    white-space: normal;
  }

  .tab-content :deep(.settings-section__body) {
    padding: 8px 6px 2px;
  }

  .condition-rule-list {
    gap: 10px;
  }

  .tab-tip {
    margin-bottom: 10px;
  }

  .mapping-item,
  .filter-header {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .filter-depend-value {
    width: 100%;
  }
}
</style>
