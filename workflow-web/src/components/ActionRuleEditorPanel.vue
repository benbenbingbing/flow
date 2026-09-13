<template>
  <div class="action-rule-editor-panel">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="rule-priority-alert"
      title="系统先判断显示条件：不满足则隐藏；按钮显示后再判断启用条件：不满足则禁用并展示原因。功能权限和数据范围仍优先校验。"
    />

    <section class="rule-branch" aria-label="显示条件">
      <header class="rule-branch__header">
        <div>
          <h3>显示条件</h3>
          <p>条件全部通过时显示按钮；不满足则隐藏，且不再判断启用条件。</p>
        </div>
        <el-tag type="info" effect="plain">不满足则隐藏</el-tag>
      </header>
      <el-form label-position="top" class="rule-branch__form">
        <el-form-item label="常用预设" class="rule-preset-item">
          <el-select
            v-model="visiblePreset"
            clearable
            placeholder="选择预设并应用到显示条件"
            @change="value => applyPreset('visibleWhen', value)"
          >
            <el-option v-for="item in visiblePresets" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item class="rule-condition-item">
          <template #label>
            <ConfigHelpLabel label="显示规则" help-key="actionRule.visibleWhen" />
          </template>
          <div class="rule-condition-editor">
            <el-alert
              v-if="!rule.visibleWhen"
              type="info"
              :closable="false"
              title="未配置显示条件，按钮默认显示。"
            />
            <ActionRuleGroupEditor
              v-if="rule.visibleWhen"
              :node="rule.visibleWhen"
              :fields="fieldOptions"
              :statuses="statuses"
              :allow-custom-conditions="allowCustomConditions"
            />
            <div class="rule-condition-actions">
              <el-button
                v-if="!rule.visibleWhen"
                type="primary"
                text
                @click="createRoot('visibleWhen')"
              >添加显示条件</el-button>
              <el-button
                v-else
                type="danger"
                text
                @click="rule.visibleWhen = null"
              >清空显示条件</el-button>
            </div>
          </div>
        </el-form-item>
      </el-form>
    </section>

    <section class="rule-branch" aria-label="启用条件">
      <header class="rule-branch__header">
        <div>
          <h3>启用条件</h3>
          <p>按钮已显示时再判断；不满足则保留按钮并禁用，向用户解释原因。</p>
        </div>
        <el-tag type="warning" effect="plain">不满足则禁用</el-tag>
      </header>
      <el-form label-position="top" class="rule-branch__form rule-branch__form--enabled">
        <el-form-item label="常用预设" class="rule-preset-item">
          <el-select
            v-model="enabledPreset"
            clearable
            placeholder="选择预设并应用到启用条件"
            @change="value => applyPreset('enabledWhen', value)"
          >
            <el-option v-for="item in enabledPresets" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item class="rule-message-item" :required="Boolean(rule.enabledWhen)">
          <template #label>
            <ConfigHelpLabel label="禁用提示" help-key="actionRule.disabledMessage" />
          </template>
          <el-input
            v-model="rule.disabledMessage"
            maxlength="300"
            show-word-limit
            placeholder="例如：仅本人未流转草稿可以删除"
          />
        </el-form-item>
        <el-form-item class="rule-condition-item">
          <template #label>
            <ConfigHelpLabel label="启用规则" help-key="actionRule.enabledWhen" />
          </template>
          <div class="rule-condition-editor">
            <el-alert
              v-if="!rule.enabledWhen"
              type="info"
              :closable="false"
              title="未配置启用条件，显示后的按钮默认可用。"
            />
            <ActionRuleGroupEditor
              v-if="rule.enabledWhen"
              :node="rule.enabledWhen"
              :fields="fieldOptions"
              :statuses="statuses"
              :allow-custom-conditions="allowCustomConditions"
            />
            <div class="rule-condition-actions">
              <el-button
                v-if="!rule.enabledWhen"
                type="primary"
                text
                @click="createRoot('enabledWhen')"
              >添加启用条件</el-button>
              <el-button
                v-else
                type="danger"
                text
                @click="rule.enabledWhen = null"
              >清空启用条件</el-button>
            </div>
          </div>
        </el-form-item>
      </el-form>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import ConfigHelpLabel from './ConfigHelpLabel.vue'
import ActionRuleGroupEditor from './ActionRuleGroupEditor.vue'
import {
  ACTION_RULE_VERSION,
  createActionRulePreset,
  createEmptyActionRule,
  hasActionRuleComparisonValue
} from '@/shared/action-rules'
import { getEntityActionRuleCondition } from '@/utils/entityActionRuleRegistry'

const props = defineProps({
  entityFields: { type: Array, default: () => [] },
  statuses: { type: Array, default: () => [] },
  allowCustomConditions: { type: Boolean, default: true }
})

const rule = defineModel({
  type: Object,
  default: () => createEmptyActionRule()
})
const visiblePreset = ref('')
const enabledPreset = ref('')

const systemFields = [
  { label: '数据名称', value: 'name' },
  { label: '数据编码', value: 'code' },
  { label: '业务单号', value: 'dataNo' },
  { label: '状态', value: 'status' },
  { label: '创建人', value: 'createdBy' },
  { label: '提交人', value: 'submitterId' },
  { label: '所属部门', value: 'deptId' },
  { label: '流程实例', value: 'processInstanceId' },
  { label: '当前办理人', value: 'currentTaskAssignee' },
  { label: '创建时间', value: 'createdAt' },
  { label: '更新时间', value: 'updatedAt' }
]

const fieldOptions = computed(() => [
  ...systemFields,
  ...props.entityFields
    .filter(field => field.fieldCode && !systemFields.some(item => item.value === field.fieldCode))
    .map(field => ({ label: `${field.fieldName} (${field.fieldCode})`, value: field.fieldCode }))
])

const presets = Object.freeze([
  { label: '清空当前条件', value: 'ALWAYS' },
  { label: '仅本人数据', value: 'OWN_DATA' },
  { label: '仅本人未流转草稿', value: 'OWN_DRAFT' },
  { label: '仅本人草稿或已撤回', value: 'OWN_DRAFT_OR_WITHDRAWN' },
  { label: '仅当前任务办理人', value: 'CURRENT_ASSIGNEE' },
  { label: '仅流程进行中', value: 'RUNNING' },
  { label: '仅本部门数据', value: 'SAME_DEPT' },
  { label: '指定状态', value: 'STATUS' }
])
const visiblePresets = presets.map(item => item.value === 'ALWAYS'
  ? { ...item, label: '始终显示' }
  : item)
const enabledPresets = presets.map(item => item.value === 'ALWAYS'
  ? { ...item, label: '显示后始终启用' }
  : item)
const relationValues = new Set([
  'CURRENT_USER_IS_CREATOR',
  'CURRENT_USER_IS_SUBMITTER',
  'CURRENT_USER_IS_ASSIGNEE',
  'CURRENT_USER_SAME_DEPT'
])
const processStateValues = new Set([
  'NOT_STARTED', 'RUNNING', 'COMPLETED', 'TERMINATED', 'WITHDRAWN'
])
const statusCategoryValues = new Set([
  'NEW', 'PROCESSING', 'COMPLETED', 'TERMINATED', 'WITHDRAWN'
])
const userFieldValues = new Set(['id', 'username', 'deptId', 'orgId', 'roleIds'])
const simpleOperators = new Set(['EQ', 'NE'])
const setOperators = new Set(['EQ', 'NE', 'IN', 'NOT_IN'])
const fieldOperators = new Set([
  ...setOperators,
  'CONTAINS', 'NOT_CONTAINS', 'EMPTY', 'NOT_EMPTY', 'GT', 'GTE', 'LT', 'LTE'
])

watch(rule, resetPresets)

/**
 * 校验并生成可持久化的 v2 规则。面板本身不提示消息，便于弹窗和内嵌场景
 * 使用同一套校验后决定是否关闭各自容器。
 */
function buildValidatedRule() {
  const currentRule = rule.value || createEmptyActionRule()
  const error = validateRuleNode(currentRule.visibleWhen, '显示条件')
    || validateRuleNode(currentRule.enabledWhen, '启用条件')
    || (currentRule.enabledWhen && !String(currentRule.disabledMessage || '').trim()
      ? '配置启用条件后，请填写禁用提示'
      : '')
  if (error) return { error, rule: null }

  const savedRule = cloneValue(currentRule)
  savedRule.version = ACTION_RULE_VERSION
  savedRule.disabledMessage = savedRule.enabledWhen
    ? String(savedRule.disabledMessage || '').trim()
    : ''
  return { error: '', rule: savedRule }
}

/**
 * 对递归条件树做结构校验，防止空条件组或半成品比较条件在运行时恒为 false。
 * 自定义条件仅在注册表存在对应 Provider 时放行，具体业务字段仍由 Provider 校验。
 */
function validateRuleNode(node, branchLabel) {
  if (!node) return ''
  const type = String(node.type || '').toUpperCase()
  if (!type) return `${branchLabel}中存在未选择类型的条件`
  if (type === 'GROUP') {
    if (!['AND', 'OR'].includes(String(node.logic || '').toUpperCase())) {
      return `${branchLabel}中存在未选择逻辑的条件组`
    }
    if (!Array.isArray(node.children) || node.children.length === 0) {
      return `${branchLabel}中存在空条件组，请添加条件或删除该条件组`
    }
    for (const child of node.children) {
      const childError = validateRuleNode(child, branchLabel)
      if (childError) return childError
    }
    return ''
  }
  if (type === 'RELATION') {
    return relationValues.has(String(node.relation || '').toUpperCase())
      ? ''
      : `${branchLabel}中存在未选择的当前用户关系`
  }
  if (type === 'PROCESS_STATE') {
    if (!simpleOperators.has(String(node.operator || '').toUpperCase())) {
      return `${branchLabel}中的流程状态比较方式无效`
    }
    return processStateValues.has(String(node.value || '').toUpperCase())
      ? ''
      : `${branchLabel}中存在未选择的流程状态`
  }
  if (type === 'STATUS_CODE' || type === 'STATUS_CATEGORY') {
    if (!setOperators.has(String(node.operator || '').toUpperCase())) {
      return `${branchLabel}中的状态比较方式无效`
    }
    if (!hasActionRuleComparisonValue(node.value, node.operator)) {
      return `${branchLabel}中存在未选择的状态值`
    }
    if (type === 'STATUS_CATEGORY') {
      const values = Array.isArray(node.value) ? node.value : [node.value]
      if (values.some(value => !statusCategoryValues.has(String(value || '').toUpperCase()))) {
        return `${branchLabel}中存在无效的状态分类`
      }
    }
    return ''
  }
  if (type === 'FIELD' || type === 'USER_FIELD') {
    const field = String(node.field || '')
    if (!field || (type === 'USER_FIELD' && !userFieldValues.has(field))) {
      return `${branchLabel}中存在未选择的${type === 'FIELD' ? '数据字段' : '用户属性'}`
    }
    if (!fieldOperators.has(String(node.operator || '').toUpperCase())) {
      return `${branchLabel}中的字段比较方式无效`
    }
    return hasActionRuleComparisonValue(node.value, node.operator)
      ? ''
      : `${branchLabel}中存在未填写的比较值`
  }
  if (!props.allowCustomConditions || !getEntityActionRuleCondition(type)) {
    return `${branchLabel}中存在未注册的条件类型：${type}`
  }
  return ''
}

function createRoot(branch) {
  if (!rule.value) rule.value = createEmptyActionRule()
  rule.value[branch] = group('AND', [])
}

function applyPreset(branch, value) {
  const definition = createActionRulePreset(value)
  if (definition) {
    if (!rule.value) rule.value = createEmptyActionRule()
    rule.value[branch] = cloneValue(definition.root)
    if (branch === 'enabledWhen') {
      rule.value.disabledMessage = definition.message
    }
  }
  // 预设是一次性命令；应用后清空选择，允许再次选择同一预设恢复模板。
  if (branch === 'visibleWhen') {
    visiblePreset.value = ''
  } else {
    enabledPreset.value = ''
  }
}

function resetPresets() {
  visiblePreset.value = ''
  enabledPreset.value = ''
}

function group(logic, children) {
  return { type: 'GROUP', logic, children }
}

function cloneValue(value) {
  return JSON.parse(JSON.stringify(value))
}

defineExpose({ buildValidatedRule, resetPresets })
</script>

<style scoped>
.rule-priority-alert {
  margin-bottom: 16px;
}

.rule-branch {
  padding: 16px 18px 18px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-fill-color-extra-light);
}

.rule-branch + .rule-branch {
  margin-top: 16px;
}

.rule-branch__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.rule-branch__header h3 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 16px;
  line-height: 24px;
}

.rule-branch__header p {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 20px;
}

.rule-branch__form {
  display: grid;
  grid-template-columns: minmax(280px, 340px) minmax(0, 1fr);
  gap: 0 16px;
}

.rule-branch__form--enabled {
  grid-template-columns: minmax(280px, 340px) minmax(320px, 1fr);
}

.rule-condition-item {
  grid-column: 1 / -1;
  margin-bottom: 0;
}

.rule-condition-editor,
.rule-preset-item :deep(.el-select) {
  width: 100%;
}

.rule-condition-actions {
  margin-top: 4px;
}

@media (max-width: 760px) {
  .rule-branch {
    padding: 14px;
  }

  .rule-branch__header,
  .rule-branch__form,
  .rule-branch__form--enabled {
    display: block;
  }

  .rule-branch__header :deep(.el-tag) {
    margin-top: 8px;
  }
}
</style>
