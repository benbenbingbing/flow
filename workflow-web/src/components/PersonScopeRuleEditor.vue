<template>
  <div class="person-scope-rule-editor">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="候选人员只会从下列范围的并集中加载；如需全员，必须显式添加“全部启用用户”规则。"
      class="person-scope-rule-editor__alert"
    />

    <div
      v-for="(rule, index) in localRules"
      :key="index"
      class="person-scope-rule-editor__rule"
    >
      <div class="person-scope-rule-editor__rule-head">
        <span>范围 {{ index + 1 }}</span>
        <el-button
          link
          type="danger"
          aria-label="删除人员范围"
          :disabled="disabled"
          @click="removeRule(index)"
        >
          <el-icon><Delete /></el-icon>
        </el-button>
      </div>

      <el-form-item label="范围类型" required>
        <el-select
          :model-value="rule.type"
          :disabled="disabled"
          style="width: 100%"
          @update:model-value="changeRuleType(rule, $event)"
        >
          <el-option label="指定用户" value="USER" />
          <el-option label="组织部门" value="ORGANIZATION" />
          <el-option label="角色" value="ROLE" />
          <el-option label="用户组" value="GROUP" />
          <el-option label="全部启用用户" value="ALL_USERS" />
        </el-select>
      </el-form-item>

      <el-form-item v-if="rule.type === 'USER'" label="用户" required>
        <UserSelector
          v-model="rule.values"
          multiple
          value-key="code"
          placeholder="请选择用户"
          title="选择人员范围"
          :disabled="disabled"
        />
      </el-form-item>

      <el-form-item
        v-else-if="rule.type !== 'ALL_USERS'"
        :label="ruleValueLabel(rule.type)"
        required
      >
        <el-select-v2
          v-model="rule.values"
          :options="optionsForRule(rule.type)"
          multiple
          filterable
          clearable
          :disabled="disabled"
          style="width: 100%"
          :placeholder="`请选择${ruleValueLabel(rule.type)}`"
        />
      </el-form-item>

      <el-form-item
        v-if="rule.type === 'ORGANIZATION'"
        label="包含下级"
      >
        <el-switch
          v-model="rule.includeChildren"
          :disabled="disabled"
        />
      </el-form-item>

      <el-alert
        v-if="rule.type === 'ALL_USERS'"
        type="warning"
        :closable="false"
        show-icon
        title="该规则允许从全部启用且未删除的用户中选择。"
      />
    </div>

    <el-button
      class="person-scope-rule-editor__add"
      :disabled="disabled"
      @click="addRule"
    >
      <el-icon><Plus /></el-icon>
      添加人员范围
    </el-button>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import UserSelector from '@/components/UserSelector.vue'
import {
  normalizeNextApproverScope,
  validateNextApproverSelectionConfig
} from '@/shared/next-approver'

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => []
  },
  roleOptions: {
    type: Array,
    default: () => []
  },
  groupOptions: {
    type: Array,
    default: () => []
  },
  organizationOptions: {
    type: Array,
    default: () => []
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])
const localRules = ref(normalizeRules(props.modelValue))

function stable(value) {
  return JSON.stringify(value)
}

function normalizeRules(value) {
  return (Array.isArray(value) ? value : [])
    .map(normalizeNextApproverScope)
}

watch(
  () => props.modelValue,
  value => {
    const normalized = normalizeRules(value)
    if (stable(normalized) !== stable(localRules.value)) {
      localRules.value = normalized
    }
  },
  { deep: true }
)

watch(
  localRules,
  value => {
    const normalized = normalizeRules(value)
    if (stable(normalized) !== stable(normalizeRules(props.modelValue))) {
      emit('update:modelValue', normalized)
    }
  },
  { deep: true }
)

function addRule() {
  localRules.value = [
    ...localRules.value,
    { type: 'USER', values: [], includeChildren: false }
  ]
}

function removeRule(index) {
  localRules.value = localRules.value.filter((_, itemIndex) =>
    itemIndex !== index)
}

function changeRuleType(rule, type) {
  rule.type = type
  rule.values = []
  rule.includeChildren = false
}

function ruleValueLabel(type) {
  return ({
    ORGANIZATION: '组织部门',
    ROLE: '角色',
    GROUP: '用户组'
  })[type] || '范围数据'
}

function canonicalOptions(options) {
  return (options || []).map(option => ({
    ...option,
    // 流程定义持久化稳定业务编码，避免环境间导入时依赖数据库主键。
    value: String(option.code ?? option.value ?? option.id ?? ''),
    label: option.label || option.name || option.code || option.value
  })).filter(option => option.value)
}

function optionsForRule(type) {
  if (type === 'ROLE') return canonicalOptions(props.roleOptions)
  if (type === 'GROUP') return canonicalOptions(props.groupOptions)
  if (type === 'ORGANIZATION') {
    return canonicalOptions(props.organizationOptions)
  }
  return []
}

function validate() {
  return validateNextApproverSelectionConfig({
    visible: true,
    editable: true,
    source: { type: 'SCOPE', rules: localRules.value }
  })
}

defineExpose({ validate })
</script>

<style scoped>
.person-scope-rule-editor__alert {
  margin-bottom: 14px;
}

.person-scope-rule-editor__rule {
  margin-bottom: 12px;
  padding: 12px 12px 2px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-extra-light);
}

.person-scope-rule-editor__rule-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  color: var(--el-text-color-regular);
  font-weight: 600;
}

.person-scope-rule-editor__add {
  width: 100%;
}
</style>
