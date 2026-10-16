<template>
  <div class="sub-list-parameter-editor">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="勾选“查询”的参数会成为子列表固定条件；勾选“新增”的参数会成为新记录初始值。指向当前主实体的引用字段固定使用父记录ID查询。"
    />

    <div class="mapping-heading">
      <div>
        <strong>参数映射</strong>
        <span>目标字段必须在目标列表中启用查询，才能用于默认列表过滤。</span>
      </div>
      <el-button
        size="small"
        plain
        :disabled="availableTargets.length === 0"
        @click="addMapping"
      >
        添加
      </el-button>
    </div>

    <template
      v-for="(row, index) in rows"
      :key="`${row.targetField || 'new'}-${index}`"
    >
      <div class="mapping-row">
        <el-select
          :model-value="row.targetField"
          filterable
          placeholder="目标字段"
          @update:model-value="value => updateTarget(index, value)"
        >
          <el-option
            v-for="option in targetOptions"
            :key="option.value"
            :value="option.value"
            :label="option.label"
            :disabled="option.invalid || !hasTargetUsage(option)"
          >
            <div class="target-option">
              <span>{{ option.label }}</span>
              <span v-if="!option.invalid" class="target-capabilities">
                {{ option.queryable ? '可查询' : '未启用查询' }}
                ·
                {{ option.writable ? '可新增' : '不可新增' }}
              </span>
            </div>
          </el-option>
        </el-select>

        <el-select
          :model-value="sourceType(row.source)"
          placeholder="参数来源"
          :disabled="isParentReferenceMapping(row)"
          @update:model-value="value => updateSourceType(index, value)"
        >
          <el-option
            v-for="option in sourceTypes"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>

        <span
          v-if="sourceType(row.source) === 'PARENT_RECORD_ID'"
          class="source-fixed"
        >
          <span>parent.recordId</span>
          <el-tooltip
            content="当前父记录的数据库主键 ID，不是名称、编码或其他业务字段值。"
            placement="top"
          >
            <el-icon class="source-help" aria-label="父记录ID说明">
              <QuestionFilled />
            </el-icon>
          </el-tooltip>
        </span>
        <el-select
          v-else-if="sourceType(row.source) === 'PARENT_FIELD'"
          :model-value="sourceValue(row.source)"
          filterable
          placeholder="选择父字段"
          @update:model-value="value => updateSourceValue(index, value)"
        >
          <el-option
            v-for="field in parentFields"
            :key="field.fieldCode"
            :label="`${field.fieldName || field.fieldCode} (${field.fieldCode})`"
            :value="field.fieldCode"
          />
        </el-select>
        <el-select
          v-else-if="sourceType(row.source) === 'CONTEXT'"
          :model-value="sourceValue(row.source)"
          filterable
          allow-create
          placeholder="选择或输入上下文键"
          @update:model-value="value => updateSourceValue(index, value)"
        >
          <el-option
            v-for="option in contextOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-input
          v-else
          :model-value="sourceValue(row.source)"
          placeholder="固定值"
          @update:model-value="value => updateSourceValue(index, value)"
        />

        <el-select
          v-if="row.useForQuery"
          :model-value="row.operator"
          placeholder="查询方式"
          :disabled="isParentReferenceMapping(row)"
          @update:model-value="value => patch(index, { operator: value })"
        >
          <el-option
            v-for="option in operatorOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <span v-else class="operator-placeholder">不参与查询</span>

        <div class="mapping-uses">
          <el-checkbox
            :model-value="row.useForQuery"
            :disabled="
              isParentReferenceMapping(row)
                || !targetCapability(row.targetField).queryable
                || (row.useForQuery && !row.useForCreate)
            "
            @update:model-value="value => patch(index, { useForQuery: value })"
          >
            查询
          </el-checkbox>
          <el-checkbox
            :model-value="row.useForCreate"
            :disabled="
              !targetCapability(row.targetField).writable
                || (row.useForCreate && !row.useForQuery)
            "
            @update:model-value="value => patch(index, { useForCreate: value })"
          >
            新增
          </el-checkbox>
          <el-checkbox
            :model-value="row.required"
            :disabled="
              isParentReferenceMapping(row)
                || !hasSelectedUsage(row)
            "
            @update:model-value="value => patch(index, { required: value })"
          >
            必填
          </el-checkbox>
        </div>

        <el-button
          link
          type="danger"
          aria-label="删除参数映射"
          title="删除参数映射"
          @click="remove(index)"
        >
          删除
        </el-button>
      </div>

      <div
        v-if="isParentReferenceMapping(row)"
        class="mapping-policy"
      >
        该目标字段引用当前主实体，系统固定使用
        <code>parent.recordId</code>
        以“等于”方式过滤，避免子列表显示其他主记录的数据。
      </div>
      <div
        v-else-if="!hasTargetUsage(targetCapability(row.targetField))"
        class="mapping-warning"
      >
        该目标字段未启用查询且不可用于新增，请更换目标字段或删除此映射。
      </div>
    </template>

    <el-empty
      v-if="rows.length === 0"
      description="未配置参数映射，子列表将按目标列表自身条件查询"
      :image-size="48"
    />
  </div>
</template>

<script setup>
import { computed, watch } from 'vue'
import { QuestionFilled } from '@element-plus/icons-vue'
import {
  enforceSubListParameterUsage,
  enforceSubListParentReferenceMapping,
  isParentEntityReferenceTarget,
  normalizeSubListParameterContract,
  resolveDefaultSubListParameterSource,
  SUB_LIST_PARAMETER_CONTRACT_VERSION
} from '@/shared/sub-list'

const props = defineProps({
  targetFields: { type: Array, default: () => [] },
  parentFields: { type: Array, default: () => [] },
  parentEntityId: { type: [String, Number], default: '' }
})

const contract = defineModel({ type: Object, default: () => ({}) })

const normalizedContract = computed(() =>
  normalizeSubListParameterContract(contract.value)
)
const rows = computed(() => normalizedContract.value.mappings)
const targetOptions = computed(() => {
  const selected = new Set(rows.value.map(row => row.targetField))
  const valid = props.targetFields
    .filter(field => field.fieldCode)
    .map(field => ({
      ...field,
      value: field.fieldCode,
      label: `${field.fieldName || field.fieldCode} (${field.fieldCode})`,
      queryable: field.queryable === true,
      writable: field.writable !== false
    }))
  const validCodes = new Set(valid.map(option => option.value))
  return [
    ...valid,
    ...[...selected]
      .filter(code => code && !validCodes.has(code))
      .map(code => ({
        value: code,
        label: `${code}（已失效）`,
        queryable: false,
        writable: false,
        invalid: true
      }))
  ]
})
const availableTargets = computed(() => {
  const selected = new Set(rows.value.map(row => row.targetField))
  return targetOptions.value.filter(option =>
    !option.invalid
      && hasTargetUsage(option)
      && !selected.has(option.value)
  )
})

const sourceTypes = [
  { value: 'PARENT_FIELD', label: '父表单字段值' },
  { value: 'PARENT_RECORD_ID', label: '父记录ID（主键）' },
  { value: 'CONTEXT', label: '运行上下文' },
  { value: 'LITERAL', label: '固定值' }
]
const contextOptions = [
  { value: 'userId', label: '当前用户ID' },
  { value: 'username', label: '当前用户名' },
  { value: 'processDefinitionId', label: '流程定义ID' },
  { value: 'processInstanceId', label: '流程实例ID' },
  { value: 'taskId', label: '当前任务ID' },
  { value: 'mode', label: '表单模式' }
]
const operatorOptions = [
  { value: 'EQ', label: '等于' },
  { value: 'NE', label: '不等于' },
  { value: 'LIKE', label: '包含' },
  { value: 'GT', label: '大于' },
  { value: 'GE', label: '大于等于' },
  { value: 'LT', label: '小于' },
  { value: 'LE', label: '小于等于' },
  { value: 'IN', label: '属于集合' },
  { value: 'NOT_IN', label: '不属于集合' }
]

watch(
  [
    () => contract.value,
    () => props.targetFields,
    () => props.parentEntityId
  ],
  () => {
    const normalized = normalizeSubListParameterContract(contract.value)
    const mappings = normalized.mappings.map(enforceMappingPolicy)
    if (JSON.stringify(mappings) === JSON.stringify(normalized.mappings)) {
      return
    }
    contract.value = {
      version: SUB_LIST_PARAMETER_CONTRACT_VERSION,
      mappings
    }
  },
  { immediate: true, deep: true }
)

function addMapping() {
  const target = availableTargets.value[0]
  if (!target) return
  updateRows([
    ...rows.value,
    {
      targetField: target.value,
      targetFieldName: target.label.replace(/\s*\([^)]*\)$/, ''),
      source: resolveDefaultSubListParameterSource(
        target,
        props.parentFields,
        props.parentEntityId
      ),
      operator: 'EQ',
      required: true,
      useForQuery: target.queryable,
      useForCreate: target.writable
    }
  ])
}

function updateTarget(index, targetField) {
  const capability = targetCapability(targetField)
  const target = targetOptions.value.find(option =>
    option.value === targetField
  )
  patch(index, {
    targetField,
    targetFieldName: target?.label?.replace(/\s*\([^)]*\)$/, '') || '',
    source: resolveDefaultSubListParameterSource(
      target,
      props.parentFields,
      props.parentEntityId
    ),
    useForQuery: capability.queryable,
    useForCreate: capability.writable
  })
}

function updateSourceType(index, type) {
  let source = ''
  if (type === 'PARENT_RECORD_ID') {
    source = 'parent.recordId'
  } else if (type === 'PARENT_FIELD') {
    source = props.parentFields[0]?.fieldCode
      ? `parent.data.${props.parentFields[0].fieldCode}`
      : ''
  } else if (type === 'CONTEXT') {
    source = 'context.userId'
  } else {
    source = { literal: '' }
  }
  patch(index, { source })
}

function updateSourceValue(index, value) {
  const type = sourceType(rows.value[index]?.source)
  if (type === 'PARENT_FIELD') {
    patch(index, { source: value ? `parent.data.${value}` : '' })
  } else if (type === 'CONTEXT') {
    patch(index, { source: value ? `context.${value}` : '' })
  } else {
    patch(index, { source: { literal: value } })
  }
}

function sourceType(source) {
  if (source && typeof source === 'object'
      && Object.prototype.hasOwnProperty.call(source, 'literal')) {
    return 'LITERAL'
  }
  const value = String(source || '')
  if (value === 'parent.recordId') return 'PARENT_RECORD_ID'
  if (value.startsWith('parent.data.')) return 'PARENT_FIELD'
  if (value.startsWith('context.')) return 'CONTEXT'
  return 'LITERAL'
}

function sourceValue(source) {
  const type = sourceType(source)
  if (type === 'LITERAL') {
    return source && typeof source === 'object' ? source.literal : source
  }
  if (type === 'PARENT_FIELD') {
    return String(source).slice('parent.data.'.length)
  }
  if (type === 'CONTEXT') {
    return String(source).slice('context.'.length)
  }
  return ''
}

function targetCapability(targetField) {
  return targetOptions.value.find(option =>
    option.value === targetField
  ) || {
    queryable: false,
    writable: false
  }
}

function isParentReferenceMapping(row) {
  return isParentEntityReferenceTarget(
    targetCapability(row.targetField),
    props.parentEntityId
  )
}

function hasTargetUsage(target) {
  return target?.queryable === true || target?.writable === true
}

function hasSelectedUsage(row) {
  return row?.useForQuery === true || row?.useForCreate === true
}

function enforceMappingPolicy(row) {
  const target = targetCapability(row?.targetField)
  return enforceSubListParentReferenceMapping(
    enforceSubListParameterUsage(row, target),
    target,
    props.parentEntityId
  )
}

function patch(index, updates) {
  updateRows(rows.value.map((row, rowIndex) =>
    rowIndex === index ? { ...row, ...updates } : row
  ))
}

function remove(index) {
  updateRows(rows.value.filter((_, rowIndex) => rowIndex !== index))
}

function updateRows(mappings) {
  contract.value = {
    version: SUB_LIST_PARAMETER_CONTRACT_VERSION,
    mappings: mappings.map(enforceMappingPolicy)
  }
}
</script>

<style scoped>
.sub-list-parameter-editor {
  container-type: inline-size;
  min-width: 0;
  width: 100%;
}

.mapping-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin: 12px 0 8px;
}

.mapping-heading > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.mapping-heading span,
.target-capabilities {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.mapping-row {
  display: grid;
  grid-template-columns:
    minmax(180px, 1.2fr)
    minmax(110px, 0.8fr)
    minmax(180px, 1.2fr)
    minmax(110px, 0.7fr)
    auto
    auto;
  align-items: center;
  gap: 8px;
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.mapping-row > * {
  min-width: 0;
}

.mapping-row :deep(.el-select),
.mapping-row :deep(.el-input) {
  width: 100%;
}

.target-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.source-fixed,
.operator-placeholder {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.source-fixed {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.source-help {
  color: var(--el-color-primary);
  cursor: help;
}

.mapping-policy {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: -1px 0 8px;
  padding: 8px 12px;
  color: var(--el-color-primary-dark-2);
  background: var(--el-color-primary-light-9);
  border-bottom: 1px solid var(--el-color-primary-light-7);
  font-size: 12px;
  line-height: 1.5;
}

.mapping-warning {
  margin: -1px 0 8px;
  padding: 8px 12px;
  color: var(--el-color-warning-dark-2);
  background: var(--el-color-warning-light-9);
  border-bottom: 1px solid var(--el-color-warning-light-7);
  font-size: 12px;
  line-height: 1.5;
}

.mapping-policy code {
  padding: 1px 4px;
  color: inherit;
  background: var(--el-fill-color-light);
  border-radius: 3px;
}

.mapping-uses {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.mapping-uses :deep(.el-checkbox) {
  margin-right: 0;
}

@container (max-width: 760px) {
  .mapping-row {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }

  .mapping-uses {
    flex-wrap: wrap;
  }

}

@container (max-width: 460px) {
  .mapping-row {
    grid-template-columns: minmax(0, 1fr);
  }

  .mapping-row > .el-button {
    justify-self: end;
  }

  .source-fixed,
  .operator-placeholder {
    min-height: 32px;
    padding: 0 12px;
    border: 1px solid var(--el-border-color);
    border-radius: var(--el-border-radius-base);
  }

  .mapping-policy {
    width: 100%;
    box-sizing: border-box;
  }
}
</style>
