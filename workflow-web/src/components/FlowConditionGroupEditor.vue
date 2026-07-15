<template>
  <div class="flow-condition-group" :class="`depth-${Math.min(depth, 4)}`">
    <div class="group-header">
      <div class="group-title">
        <span>{{ depth === 1 ? '根条件组' : `条件组 ${groupNumber}` }}</span>
        <el-tag size="small" type="info">{{ group.children?.length || 0 }} 项</el-tag>
      </div>
      <div class="group-actions">
        <el-radio-group v-model="group.logic" size="small" @change="emitChange">
          <el-radio-button label="AND">全部满足 (AND)</el-radio-button>
          <el-radio-button label="OR">任一满足 (OR)</el-radio-button>
        </el-radio-group>
        <el-button size="small" link type="primary" @click="addCondition">添加条件</el-button>
        <el-button v-if="depth < maxDepth" size="small" link type="primary" @click="addGroup">
          添加条件组
        </el-button>
        <el-button v-if="removable" size="small" link type="danger" @click="$emit('remove')">
          删除组
        </el-button>
      </div>
    </div>

    <div class="group-body">
      <template v-for="(child, index) in group.children" :key="index">
        <div v-if="index > 0" class="group-connector">
          <span>{{ group.logic === 'OR' ? '或 OR' : '且 AND' }}</span>
        </div>

        <FlowConditionGroupEditor
          v-if="child.type === 'GROUP'"
          :group="child"
          :depth="depth + 1"
          :group-number="index + 1"
          :max-depth="maxDepth"
          :entity-fields="entityFields"
          :approval-options="approvalOptions"
          removable
          @change="emitChange"
          @remove="removeChild(index)"
        />

        <div v-else class="condition-row">
          <el-select
            v-model="child.property"
            placeholder="选择属性"
            filterable
            size="small"
            class="condition-property"
            @change="onPropertyChange(child)"
          >
            <el-option label="审批结果 (approved)" value="approved" />
            <el-option
              v-for="field in entityFields"
              :key="field.fieldName"
              :label="field.fieldLabel || field.fieldName"
              :value="field.fieldName"
            />
          </el-select>

          <el-select
            v-model="child.operator"
            placeholder="操作符"
            size="small"
            class="condition-operator"
            @change="emitChange"
          >
            <el-option label="等于 (==)" value="==" />
            <el-option label="不等于 (!=)" value="!=" />
            <el-option label="大于 (>)" value=">" />
            <el-option label="小于 (<)" value="<" />
            <el-option label="大于等于 (>=)" value=">=" />
            <el-option label="小于等于 (<=)" value="<=" />
            <el-option label="包含" value="contains" />
          </el-select>

          <el-select
            v-if="getFieldType(child.property) === 'select'"
            v-model="child.value"
            placeholder="选择值"
            size="small"
            class="condition-value"
            @change="emitChange"
          >
            <el-option
              v-for="option in getFieldOptions(child.property)"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-select
            v-else-if="getFieldType(child.property) === 'boolean'"
            v-model="child.value"
            placeholder="选择值"
            size="small"
            class="condition-value"
            @change="emitChange"
          >
            <el-option label="是 (true)" value="true" />
            <el-option label="否 (false)" value="false" />
          </el-select>
          <el-input
            v-else
            v-model="child.value"
            placeholder="输入值"
            size="small"
            class="condition-value"
            @input="emitChange"
          />

          <el-button type="danger" link @click="removeChild(index)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </template>

      <el-empty
        v-if="!group.children?.length"
        description="暂无条件，请添加条件或条件组"
        :image-size="42"
      />
    </div>
  </div>
</template>

<script setup>
import { Delete } from '@element-plus/icons-vue'
import { createFlowCondition, createFlowConditionGroup } from '@/utils/flowConditionGroups'

defineOptions({ name: 'FlowConditionGroupEditor' })

const props = defineProps({
  group: { type: Object, required: true },
  depth: { type: Number, default: 1 },
  groupNumber: { type: Number, default: 1 },
  maxDepth: { type: Number, default: 4 },
  entityFields: { type: Array, default: () => [] },
  approvalOptions: { type: Array, default: () => [] },
  removable: { type: Boolean, default: false }
})

const emit = defineEmits(['change', 'remove'])

function addCondition() {
  props.group.children ||= []
  props.group.children.push(createFlowCondition())
  emitChange()
}

function addGroup() {
  props.group.children ||= []
  props.group.children.push(createFlowConditionGroup())
  emitChange()
}

function removeChild(index) {
  props.group.children.splice(index, 1)
  emitChange()
}

function onPropertyChange(condition) {
  condition.value = ''
  const fieldType = getFieldType(condition.property)
  if (fieldType === 'select' || fieldType === 'boolean') {
    condition.operator = '=='
  }
  emitChange()
}

function getFieldType(fieldName) {
  if (fieldName === 'approved') return 'select'
  const field = props.entityFields.find(item => item.fieldName === fieldName)
  if (!field) return 'string'
  const typeMap = {
    string: 'string',
    text: 'string',
    number: 'number',
    integer: 'number',
    decimal: 'number',
    select: 'select',
    radio: 'select',
    checkbox: 'select',
    date: 'date',
    datetime: 'date',
    boolean: 'boolean',
    user: 'string',
    dept: 'string'
  }
  return typeMap[field.fieldType] || 'string'
}

function getFieldOptions(fieldName) {
  if (fieldName === 'approved') return props.approvalOptions
  const field = props.entityFields.find(item => item.fieldName === fieldName)
  if (!field?.optionsJson) return []
  try {
    const options = JSON.parse(field.optionsJson)
    return Array.isArray(options) ? options : []
  } catch {
    return []
  }
}

function emitChange() {
  emit('change')
}
</script>

<style scoped>
.flow-condition-group {
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-left: 4px solid #409eff;
  border-radius: 7px;
  background: #fff;
}

.flow-condition-group.depth-2 {
  border-left-color: #7c3aed;
}

.flow-condition-group.depth-3 {
  border-left-color: #e6a23c;
}

.flow-condition-group.depth-4 {
  border-left-color: #67c23a;
}

.group-header {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
}

.group-title,
.group-actions,
.condition-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.group-title {
  flex-shrink: 0;
  font-weight: 600;
}

.group-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.group-body {
  padding: 12px;
}

.condition-row {
  padding: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
}

.condition-property {
  flex: 1.1;
  min-width: 140px;
}

.condition-operator {
  flex: 0.8;
  min-width: 125px;
}

.condition-value {
  flex: 1;
  min-width: 130px;
}

.group-connector {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 30px;
}

.group-connector::before,
.group-connector::after {
  width: 28px;
  height: 1px;
  background: var(--el-border-color);
  content: '';
}

.group-connector span {
  margin: 0 8px;
  padding: 2px 10px;
  border-radius: 10px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 11px;
  font-weight: 600;
}

@media (max-width: 900px) {
  .group-header,
  .condition-row {
    align-items: stretch;
    flex-direction: column;
  }

  .group-actions {
    justify-content: flex-start;
  }
}
</style>
