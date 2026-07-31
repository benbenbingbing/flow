<template>
  <div class="config-schema-editor">
    <el-empty v-if="!schema.length" description="该扩展无需额外配置" :image-size="48" />
    <el-empty
      v-else-if="!schemaGroups.length"
      description="当前条件下无可配置项"
      :image-size="48"
    />
    <el-form v-else label-width="110px" size="small" class="config-schema-form">
      <SettingsSection
        v-for="group in schemaGroups"
        :key="group.key"
        :title="group.label"
        :description="group.description"
        :collapsible="group.advanced"
        :default-expanded="false"
        :primary="group.primary"
      >
        <template #summary>
          <span class="config-group-count">{{ group.items.length }} 项</span>
        </template>

        <el-form-item
          v-for="item in group.items"
          :key="item.key"
          :required="item.required"
        >
          <template #label>
            <JsonConfigLabel
              v-if="item.type === 'json'"
              :label="item.label"
              :help-key="item.helpKey || ''"
              :help="schemaJsonHelp(item)"
            />
            <span v-else>{{ item.label }}</span>
          </template>
          <el-switch
            v-if="item.type === 'boolean'"
            :model-value="currentValue[item.key]"
            @update:model-value="updateValue(item, $event)"
          />
          <el-input-number
            v-else-if="item.type === 'number'"
            :model-value="currentValue[item.key]"
            :min="item.min"
            :max="item.max"
            :step="item.step || 1"
            controls-position="right"
            style="width: 100%"
            @update:model-value="updateValue(item, $event)"
          />
          <el-select
            v-else-if="item.type === 'select'"
            :model-value="currentValue[item.key]"
            :multiple="item.multiple === true"
            :placeholder="item.placeholder || `请选择${item.label}`"
            clearable
            style="width: 100%"
            @update:model-value="updateValue(item, $event)"
          >
            <el-option
              v-for="option in item.options || []"
              :key="String(option.value)"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-input
            v-else-if="item.type === 'textarea'"
            :model-value="currentValue[item.key]"
            type="textarea"
            :rows="item.rows || 3"
            :placeholder="item.placeholder"
            @update:model-value="updateValue(item, $event)"
          />
          <el-input
            v-else-if="item.type === 'json'"
            :model-value="formatJsonValue(currentValue[item.key])"
            type="textarea"
            :rows="item.rows || 5"
            :placeholder="item.placeholder || schemaJsonPlaceholder(item)"
            @change="updateJsonValue(item, $event)"
          />
          <el-input
            v-else
            :model-value="currentValue[item.key]"
            :placeholder="item.placeholder"
            clearable
            @update:model-value="updateValue(item, $event)"
          />
          <div v-if="item.description" class="config-help">{{ item.description }}</div>
        </el-form-item>
      </SettingsSection>
    </el-form>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import SettingsSection from '@/components/SettingsSection.vue'
import JsonConfigLabel from '@/components/JsonConfigLabel.vue'
import { applySchemaDefaults, sanitizeConfigObject } from '@/shared/config-runtime'
import {
  buildSchemaJsonHelp,
  getJsonConfigHelp
} from '@/shared/json-config-help'
import { parseJsonConfig } from '@/utils/jsonConfig'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  schema: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue'])

const currentValue = computed(() => applySchemaDefaults(props.schema, props.modelValue))
const schemaGroups = computed(() => {
  const groups = new Map()

  props.schema
    .map((item, index) => normalizeSchemaItem(item, index))
    .filter(item => isSchemaItemVisible(item.visibleWhen, currentValue.value))
    .forEach((item) => {
      const group = groups.get(item.groupKey) || {
        key: item.groupKey,
        label: item.groupLabel,
        description: item.groupDescription,
        advanced: item.isAdvanced,
        primary: item.groupKey === 'common',
        firstIndex: item.schemaIndex,
        items: []
      }
      group.items.push(item)
      group.firstIndex = Math.min(group.firstIndex, item.schemaIndex)
      groups.set(item.groupKey, group)
    })

  return Array.from(groups.values())
    .map(group => ({
      ...group,
      items: group.items.sort(compareSchemaItems)
    }))
    .sort(compareSchemaGroups)
})

function normalizeSchemaItem(item, index) {
  const groupConfig = item.group && typeof item.group === 'object'
    ? item.group
    : {}
  const rawGroup = typeof item.group === 'string'
    ? item.group.trim()
    : String(groupConfig.key || groupConfig.name || '').trim()
  const normalizedGroup = rawGroup.toLowerCase()
  const isAdvanced = item.advanced === true
    || groupConfig.advanced === true
    || normalizedGroup === 'advanced'
  const groupKey = normalizedGroup || (isAdvanced ? 'advanced' : 'common')

  return {
    ...item,
    schemaIndex: index,
    groupKey,
    groupLabel: item.groupLabel
      || groupConfig.label
      || getDefaultGroupLabel(groupKey, rawGroup),
    groupDescription: item.groupDescription
      || groupConfig.description
      || getDefaultGroupDescription(groupKey, isAdvanced),
    isAdvanced,
    orderValue: toFiniteNumber(item.order),
    priorityValue: normalizePriority(item.priority)
  }
}

function getDefaultGroupLabel(groupKey, rawGroup) {
  if (groupKey === 'common') return '常用配置'
  if (groupKey === 'advanced') return '高级配置'
  return rawGroup || groupKey
}

function getDefaultGroupDescription(groupKey, isAdvanced) {
  if (groupKey === 'common') return '高频参数直接展示，未分组的历史 Schema 自动归入此处'
  if (isAdvanced) return '低频、技术性或高风险参数，默认折叠'
  return ''
}

function compareSchemaGroups(left, right) {
  const leftRank = left.primary ? 0 : (left.advanced ? 2 : 1)
  const rightRank = right.primary ? 0 : (right.advanced ? 2 : 1)
  return leftRank - rightRank || left.firstIndex - right.firstIndex
}

function compareSchemaItems(left, right) {
  if (left.orderValue !== null || right.orderValue !== null) {
    const leftOrder = left.orderValue ?? Number.MAX_SAFE_INTEGER
    const rightOrder = right.orderValue ?? Number.MAX_SAFE_INTEGER
    if (leftOrder !== rightOrder) return leftOrder - rightOrder
  }
  return right.priorityValue - left.priorityValue || left.schemaIndex - right.schemaIndex
}

function normalizePriority(priority) {
  if (typeof priority === 'number' && Number.isFinite(priority)) return priority
  const numericPriority = Number(priority)
  if (priority !== '' && Number.isFinite(numericPriority)) return numericPriority
  const priorityMap = {
    critical: 400,
    highest: 400,
    high: 300,
    primary: 300,
    normal: 200,
    medium: 200,
    low: 100
  }
  return priorityMap[String(priority || '').toLowerCase()] || 0
}

function toFiniteNumber(value) {
  if (value === undefined || value === null || value === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function isSchemaItemVisible(rule, values) {
  if (rule === undefined || rule === null) return true
  if (typeof rule === 'boolean') return rule
  if (typeof rule === 'string') return Boolean(getValueByPath(values, rule))
  if (typeof rule === 'function') {
    try {
      return rule(values) !== false
    } catch {
      return false
    }
  }
  if (Array.isArray(rule)) {
    return rule.every(item => isSchemaItemVisible(item, values))
  }
  if (typeof rule !== 'object') return Boolean(rule)

  const allRules = rule.all || rule.and
  if (Array.isArray(allRules) && !allRules.every(item => isSchemaItemVisible(item, values))) {
    return false
  }
  const anyRules = rule.any || rule.or
  if (Array.isArray(anyRules) && !anyRules.some(item => isSchemaItemVisible(item, values))) {
    return false
  }
  if (rule.not !== undefined && isSchemaItemVisible(rule.not, values)) {
    return false
  }

  const path = rule.key || rule.field || rule.path
  if (path) {
    return matchVisibleValue(getValueByPath(values, path), rule)
  }

  const reservedKeys = new Set(['all', 'and', 'any', 'or', 'not'])
  return Object.entries(rule)
    .filter(([key]) => !reservedKeys.has(key))
    .every(([key, expected]) => valuesEqual(getValueByPath(values, key), expected))
}

function matchVisibleValue(actual, rule) {
  if (Object.prototype.hasOwnProperty.call(rule, 'equals')) {
    return valuesEqual(actual, rule.equals)
  }
  if (Object.prototype.hasOwnProperty.call(rule, 'notEquals')) {
    return !valuesEqual(actual, rule.notEquals)
  }
  if (Array.isArray(rule.in)) return rule.in.some(item => valuesEqual(actual, item))
  if (Array.isArray(rule.notIn)) return !rule.notIn.some(item => valuesEqual(actual, item))
  if (Object.prototype.hasOwnProperty.call(rule, 'includes')) {
    return Array.isArray(actual) || typeof actual === 'string'
      ? actual.includes(rule.includes)
      : false
  }
  if (Object.prototype.hasOwnProperty.call(rule, 'exists')) {
    const exists = actual !== undefined && actual !== null
    return rule.exists ? exists : !exists
  }
  if (rule.truthy === true) return Boolean(actual)
  if (rule.falsy === true) return !actual

  const expected = Object.prototype.hasOwnProperty.call(rule, 'value')
    ? rule.value
    : rule.is
  switch (String(rule.operator || '').toLowerCase()) {
    case 'ne':
    case '!=':
    case 'notequals':
      return !valuesEqual(actual, expected)
    case 'in':
      return Array.isArray(expected) && expected.some(item => valuesEqual(actual, item))
    case 'notin':
      return Array.isArray(expected) && !expected.some(item => valuesEqual(actual, item))
    case 'includes':
      return Array.isArray(actual) || typeof actual === 'string'
        ? actual.includes(expected)
        : false
    case 'truthy':
      return Boolean(actual)
    case 'falsy':
      return !actual
    case 'exists':
      return actual !== undefined && actual !== null
    default:
      return expected === undefined ? Boolean(actual) : valuesEqual(actual, expected)
  }
}

function getValueByPath(source, path) {
  return String(path)
    .split('.')
    .filter(Boolean)
    .reduce((value, key) => value?.[key], source)
}

function valuesEqual(left, right) {
  if (Object.is(left, right)) return true
  if (
    left && right
    && typeof left === 'object'
    && typeof right === 'object'
  ) {
    try {
      return JSON.stringify(left) === JSON.stringify(right)
    } catch {
      return false
    }
  }
  return false
}

function updateValue(item, value) {
  emit('update:modelValue', sanitizeConfigObject({
    ...currentValue.value,
    [item.key]: value
  }))
}

function formatJsonValue(value) {
  if (value === undefined || value === null || value === '') return ''
  return typeof value === 'string' ? value : JSON.stringify(value, null, 2)
}

function schemaJsonHelp(item) {
  return buildSchemaJsonHelp(item)
}

function schemaJsonPlaceholder(item) {
  const help = getJsonConfigHelp(item.helpKey)
    || buildSchemaJsonHelp(item)
  return `例如 ${JSON.stringify(help?.example ?? {})}`
}

function updateJsonValue(item, value) {
  if (!value) {
    updateValue(item, null)
    return
  }
  try {
    updateValue(item, parseJsonConfig(value, {
      fieldName: item.label,
      expectedType: item.jsonShape || 'object-or-array'
    }))
  } catch (error) {
    ElMessage.warning(error.message)
  }
}
</script>

<style scoped>
.config-schema-editor {
  width: 100%;
}

.config-schema-form :deep(.settings-section:last-child) {
  margin-bottom: 0;
}

.config-schema-form :deep(.settings-section__body) {
  padding-bottom: 6px;
}

.config-schema-form :deep(.el-form-item:last-child) {
  margin-bottom: 10px;
}

.config-group-count {
  color: #909399;
  font-size: 12px;
}

.config-help {
  width: 100%;
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}
</style>
