<template>
  <div class="rule-group">
    <div class="group-header">
      <el-radio-group v-model="node.logic" size="small">
        <el-radio-button label="AND">全部满足</el-radio-button>
        <el-radio-button label="OR">任一满足</el-radio-button>
      </el-radio-group>
      <div>
        <el-button size="small" text type="primary" @click="addCondition">添加条件</el-button>
        <el-button v-if="depth < 5" size="small" text type="primary" @click="addGroup">添加条件组</el-button>
        <el-button v-if="removable" size="small" text type="danger" @click="$emit('remove')">删除组</el-button>
      </div>
    </div>

    <div v-for="(child, index) in node.children" :key="index" class="rule-item">
      <ActionRuleGroupEditor
        v-if="child.type === 'GROUP'"
        :node="child"
        :depth="depth + 1"
        :fields="fields"
        :statuses="statuses"
        removable
        @remove="node.children.splice(index, 1)"
      />
      <div v-else class="condition-row">
        <el-select v-model="child.type" size="small" style="width: 150px" @change="resetCondition(child)">
          <el-option label="当前用户关系" value="RELATION" />
          <el-option label="流程状态" value="PROCESS_STATE" />
          <el-option label="状态编码" value="STATUS_CODE" />
          <el-option label="状态分类" value="STATUS_CATEGORY" />
          <el-option label="当前用户属性" value="USER_FIELD" />
          <el-option label="数据字段" value="FIELD" />
          <el-option
            v-for="definition in customConditions"
            :key="definition.type"
            :label="definition.label"
            :value="definition.type"
          />
        </el-select>

        <template v-if="child.type === 'RELATION'">
          <el-select v-model="child.relation" size="small" style="width: 220px">
            <el-option label="当前用户是创建人" value="CURRENT_USER_IS_CREATOR" />
            <el-option label="当前用户是提交人" value="CURRENT_USER_IS_SUBMITTER" />
            <el-option label="当前用户是当前办理人" value="CURRENT_USER_IS_ASSIGNEE" />
            <el-option label="当前用户与数据同部门" value="CURRENT_USER_SAME_DEPT" />
          </el-select>
        </template>

        <template v-else-if="child.type === 'PROCESS_STATE'">
          <OperatorSelect v-model="child.operator" :operators="simpleOperators" />
          <el-select v-model="child.value" size="small" style="width: 160px">
            <el-option label="未发起" value="NOT_STARTED" />
            <el-option label="进行中" value="RUNNING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已终止" value="TERMINATED" />
            <el-option label="已撤回" value="WITHDRAWN" />
          </el-select>
        </template>

        <template v-else-if="child.type === 'STATUS_CODE'">
          <OperatorSelect v-model="child.operator" :operators="setOperators" />
          <el-select
            v-model="child.value"
            :multiple="isSetOperator(child.operator)"
            size="small"
            style="width: 220px"
            clearable
          >
            <el-option
              v-for="status in statuses"
              :key="status.statusCode"
              :label="`${status.statusName} (${status.statusCode})`"
              :value="status.statusCode"
            />
          </el-select>
        </template>

        <template v-else-if="child.type === 'STATUS_CATEGORY'">
          <OperatorSelect v-model="child.operator" :operators="setOperators" />
          <el-select
            v-model="child.value"
            :multiple="isSetOperator(child.operator)"
            size="small"
            style="width: 190px"
          >
            <el-option label="新建" value="NEW" />
            <el-option label="审批中" value="PROCESSING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已终止" value="TERMINATED" />
            <el-option label="已撤回" value="WITHDRAWN" />
          </el-select>
        </template>

        <template v-else-if="child.type === 'FIELD'">
          <el-select v-model="child.field" size="small" filterable style="width: 180px">
            <el-option v-for="field in fields" :key="field.value" :label="field.label" :value="field.value" />
          </el-select>
          <OperatorSelect v-model="child.operator" :operators="fieldOperators" />
          <el-input
            v-if="!['EMPTY', 'NOT_EMPTY'].includes(child.operator)"
            v-model="child.value"
            size="small"
            placeholder="多个值用逗号分隔"
            style="width: 190px"
          />
        </template>

        <template v-else-if="child.type === 'USER_FIELD'">
          <el-select v-model="child.field" size="small" style="width: 180px">
            <el-option label="用户ID" value="id" />
            <el-option label="用户名" value="username" />
            <el-option label="部门ID" value="deptId" />
            <el-option label="组织ID" value="orgId" />
            <el-option label="角色ID集合" value="roleIds" />
          </el-select>
          <OperatorSelect v-model="child.operator" :operators="fieldOperators" />
          <el-input
            v-if="!['EMPTY', 'NOT_EMPTY'].includes(child.operator)"
            v-model="child.value"
            size="small"
            placeholder="多个值用逗号分隔"
            style="width: 190px"
          />
        </template>

        <component
          v-else-if="customDefinition(child.type)?.component"
          :is="customDefinition(child.type).component"
          :model-value="child"
          :fields="fields"
          :statuses="statuses"
          @update:model-value="value => updateCustomChild(index, value)"
        />

        <el-button text type="danger" size="small" @click="node.children.splice(index, 1)">删除</el-button>
      </div>
    </div>

    <el-empty v-if="!node.children?.length" description="暂无条件，点击添加条件" :image-size="48" />
  </div>
</template>

<script setup>
import { defineComponent, h, resolveComponent } from 'vue'
import { getEntityActionRuleCondition, getEntityActionRuleConditions } from '@/utils/entityActionRuleRegistry'

defineOptions({ name: 'ActionRuleGroupEditor' })

const props = defineProps({
  node: { type: Object, required: true },
  depth: { type: Number, default: 1 },
  fields: { type: Array, default: () => [] },
  statuses: { type: Array, default: () => [] },
  removable: { type: Boolean, default: false }
})

defineEmits(['remove'])

const customConditions = getEntityActionRuleConditions()
const simpleOperators = ['EQ', 'NE']
const setOperators = ['EQ', 'NE', 'IN', 'NOT_IN']
const fieldOperators = ['EQ', 'NE', 'IN', 'NOT_IN', 'CONTAINS', 'NOT_CONTAINS', 'EMPTY', 'NOT_EMPTY', 'GT', 'GTE', 'LT', 'LTE']

const operatorLabels = {
  EQ: '等于',
  NE: '不等于',
  IN: '属于',
  NOT_IN: '不属于',
  CONTAINS: '包含',
  NOT_CONTAINS: '不包含',
  EMPTY: '为空',
  NOT_EMPTY: '不为空',
  GT: '大于',
  GTE: '大于等于',
  LT: '小于',
  LTE: '小于等于'
}

const OperatorSelect = defineComponent({
  props: {
    modelValue: String,
    operators: { type: Array, default: () => [] }
  },
  emits: ['update:modelValue'],
  setup(componentProps, { emit }) {
    return () => {
      const ElSelect = resolveComponent('ElSelect')
      const ElOption = resolveComponent('ElOption')
      return h(ElSelect, {
      modelValue: componentProps.modelValue,
      size: 'small',
      style: 'width: 120px',
      'onUpdate:modelValue': (value) => emit('update:modelValue', value)
      }, () => componentProps.operators.map(operator =>
        h(ElOption, { label: operatorLabels[operator] || operator, value: operator })
      ))
    }
  }
})

function addCondition() {
  props.node.children ||= []
  props.node.children.push({
    type: 'RELATION',
    relation: 'CURRENT_USER_IS_CREATOR',
    operator: 'EQ',
    value: true
  })
}

function addGroup() {
  props.node.children ||= []
  props.node.children.push({
    type: 'GROUP',
    logic: 'AND',
    children: []
  })
}

function resetCondition(condition) {
  const definition = customDefinition(condition.type)
  Object.keys(condition).forEach(key => {
    if (key !== 'type') delete condition[key]
  })
  if (definition?.createDefault) {
    Object.assign(condition, definition.createDefault())
    condition.type = definition.type
    return
  }
  if (condition.type === 'RELATION') {
    condition.relation = 'CURRENT_USER_IS_CREATOR'
  } else {
    condition.operator = 'EQ'
    condition.value = ''
  }
}

function isSetOperator(operator) {
  return ['IN', 'NOT_IN'].includes(operator)
}

function customDefinition(type) {
  return getEntityActionRuleCondition(type)
}

function updateCustomChild(index, value) {
  props.node.children.splice(index, 1, value)
}
</script>

<style scoped>
.rule-group {
  padding: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
}
.group-header,
.condition-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.rule-item {
  margin-top: 10px;
}
.condition-row {
  justify-content: flex-start;
  padding: 8px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}
</style>
