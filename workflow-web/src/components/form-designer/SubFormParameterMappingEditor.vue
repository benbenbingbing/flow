<template>
  <div class="subform-mapping-editor">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="父子关系外键由系统自动维护。这里仅配置业务参数和子字段初始值。"
    />

    <div class="mapping-group">
      <div class="mapping-heading">
        <div>
          <strong>
            <ConfigHelpLabel
              label="运行参数"
              help-key="formNode.subFormParameterContract"
            />
          </strong>
          <span>供子表单数据源、联动和事件通过 params.xxx 使用</span>
        </div>
        <el-button
          size="small"
          plain
          :disabled="availableParameterTargets.length === 0"
          @click="addMapping('parameter')"
        >
          添加
        </el-button>
      </div>
      <MappingRows
        v-model="parameterRows"
        target-label="子表单参数"
        :target-options="parameterTargets"
        :parent-fields="parentFields"
      />
      <el-empty
        v-if="parameterRows.length === 0"
        description="未配置运行参数"
        :image-size="48"
      />
    </div>

    <div class="mapping-group">
      <div class="mapping-heading">
        <div>
          <strong>
            <ConfigHelpLabel
              label="初始化子字段"
              help-key="formNode.subFormParameterContract"
            />
          </strong>
          <span>新增行或目标字段为空时写入，不覆盖已填写值</span>
        </div>
        <el-button
          size="small"
          plain
          :disabled="availableFieldTargets.length === 0"
          @click="addMapping('field')"
        >
          添加
        </el-button>
      </div>
      <MappingRows
        v-model="fieldRows"
        target-label="子实体字段"
        :target-options="fieldTargets"
        :parent-fields="parentFields"
      />
      <el-empty
        v-if="fieldRows.length === 0"
        description="未配置子字段初始化"
        :image-size="48"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, resolveComponent } from 'vue'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import {
  normalizeSubFormParameterContract,
  SUBFORM_PARAMETER_CONTRACT_VERSION
} from '@/shared/subform-parameter-contract'

const props = defineProps({
  parameterOptions: { type: Array, default: () => [] },
  childFieldOptions: { type: Array, default: () => [] },
  parentFields: { type: Array, default: () => [] },
  childRefFieldCode: { type: String, default: '' }
})

const contract = defineModel({ type: Object, default: () => ({}) })

const parameterTargets = computed(() =>
  withInvalidTargets(
    props.parameterOptions.map(item => ({
      value: item.code,
      label: `${item.name || item.code} (${item.code})`
    })),
    Object.keys(normalizedContract.value.parameterMapping)
  )
)
const fieldTargets = computed(() => {
  const blocked = new Set(['id', props.childRefFieldCode].filter(Boolean))
  const valid = props.childFieldOptions
    .filter(item => item.fieldCode && !blocked.has(item.fieldCode)
      && item.readonly !== true)
    .map(item => ({
      value: item.fieldCode,
      label: `${item.fieldName || item.fieldCode} (${item.fieldCode})`
    }))
  return withInvalidTargets(
    valid,
    Object.keys(normalizedContract.value.fieldInitializationMapping)
  )
})
const normalizedContract = computed(() =>
  normalizeSubFormParameterContract(contract.value)
)
const parameterRows = computed({
  get: () => mappingToRows(normalizedContract.value.parameterMapping),
  set: rows => updateContract('parameterMapping', rows)
})
const fieldRows = computed({
  get: () => mappingToRows(
    normalizedContract.value.fieldInitializationMapping
  ),
  set: rows => updateContract('fieldInitializationMapping', rows)
})
const availableParameterTargets = computed(() =>
  unusedTargets(parameterTargets.value, parameterRows.value)
)
const availableFieldTargets = computed(() =>
  unusedTargets(fieldTargets.value, fieldRows.value)
)

function addMapping(kind) {
  const rows = kind === 'parameter' ? parameterRows : fieldRows
  const available = kind === 'parameter'
    ? availableParameterTargets.value
    : availableFieldTargets.value
  const first = available[0]
  if (!first) return
  rows.value = [
    ...rows.value,
    {
      target: first.value,
      sourceType: 'PARENT_FIELD',
      sourceValue: props.parentFields[0]?.fieldCode || ''
    }
  ]
}

function updateContract(key, rows) {
  const next = {
    ...normalizedContract.value,
    version: SUBFORM_PARAMETER_CONTRACT_VERSION,
    [key]: rowsToMapping(rows)
  }
  contract.value = next
}

function mappingToRows(mapping) {
  return Object.entries(mapping || {}).map(([target, selector]) => {
    if (selector && typeof selector === 'object'
        && Object.prototype.hasOwnProperty.call(selector, 'literal')) {
      return { target, sourceType: 'LITERAL', sourceValue: selector.literal }
    }
    const source = String(selector || '')
    if (source === 'parent.recordId') {
      return { target, sourceType: 'PARENT_RECORD_ID', sourceValue: '' }
    }
    if (source.startsWith('parent.data.')) {
      return {
        target,
        sourceType: 'PARENT_FIELD',
        sourceValue: source.slice('parent.data.'.length)
      }
    }
    if (source.startsWith('context.')) {
      return {
        target,
        sourceType: 'CONTEXT',
        sourceValue: source.slice('context.'.length)
      }
    }
    return { target, sourceType: 'PATH', sourceValue: source }
  })
}

function rowsToMapping(rows) {
  return Object.fromEntries((rows || [])
    .filter(row => row.target)
    .map(row => [row.target, serializeSource(row)]))
}

function serializeSource(row) {
  switch (row.sourceType) {
    case 'PARENT_RECORD_ID':
      return 'parent.recordId'
    case 'PARENT_FIELD':
      return row.sourceValue ? `parent.data.${row.sourceValue}` : ''
    case 'CONTEXT':
      return row.sourceValue ? `context.${row.sourceValue}` : ''
    case 'LITERAL':
      return { literal: row.sourceValue }
    default:
      return row.sourceValue || ''
  }
}

function withInvalidTargets(options, selected) {
  const values = new Set(options.map(item => item.value))
  return [
    ...options,
    ...selected
      .filter(value => value && !values.has(value))
      .map(value => ({
        value,
        label: `${value}（已失效）`,
        invalid: true
      }))
  ]
}

function unusedTargets(options, rows) {
  const used = new Set(rows.map(row => row.target))
  return options.filter(item => !used.has(item.value) && !item.invalid)
}

const MappingRows = defineComponent({
  name: 'SubFormMappingRows',
  props: {
    modelValue: { type: Array, default: () => [] },
    targetLabel: { type: String, required: true },
    targetOptions: { type: Array, default: () => [] },
    parentFields: { type: Array, default: () => [] }
  },
  emits: ['update:modelValue'],
  setup(rowProps, { emit }) {
    const sourceTypes = [
      { value: 'PARENT_FIELD', label: '父表单字段' },
      { value: 'PARENT_RECORD_ID', label: '父记录ID' },
      { value: 'CONTEXT', label: '运行上下文' },
      { value: 'LITERAL', label: '固定值' },
      { value: 'PATH', label: '失效路径（需重新选择）', disabled: true }
    ]
    const contextOptions = [
      { value: 'userId', label: '当前用户ID' },
      { value: 'username', label: '当前用户名' },
      { value: 'processDefinitionId', label: '流程定义ID' },
      { value: 'processInstanceId', label: '流程实例ID' },
      { value: 'taskId', label: '当前任务ID' },
      { value: 'mode', label: '表单模式' }
    ]

    function patch(index, updates) {
      emit('update:modelValue', rowProps.modelValue.map((row, rowIndex) =>
        rowIndex === index ? { ...row, ...updates } : row
      ))
    }
    function remove(index) {
      emit('update:modelValue', rowProps.modelValue.filter(
        (_, rowIndex) => rowIndex !== index
      ))
    }
    function sourceControl(row, index) {
      if (row.sourceType === 'PARENT_RECORD_ID') {
        return h('span', { class: 'source-fixed' }, 'parent.recordId')
      }
      if (row.sourceType === 'PARENT_FIELD') {
        return h(resolveComponent('ElSelect'), {
          modelValue: row.sourceValue,
          placeholder: '选择父字段',
          'onUpdate:modelValue': value => patch(index, { sourceValue: value })
        }, () => rowProps.parentFields.map(field =>
          h(resolveComponent('ElOption'), {
            key: field.fieldCode,
            label: `${field.fieldName || field.fieldCode} (${field.fieldCode})`,
            value: field.fieldCode
          })
        ))
      }
      if (row.sourceType === 'CONTEXT') {
        return h(resolveComponent('ElSelect'), {
          modelValue: row.sourceValue,
          filterable: true,
          allowCreate: true,
          placeholder: '选择或输入上下文键',
          'onUpdate:modelValue': value => patch(index, { sourceValue: value })
        }, () => contextOptions.map(item =>
          h(resolveComponent('ElOption'), {
            key: item.value,
            label: item.label,
            value: item.value
          })
        ))
      }
      return h(resolveComponent('ElInput'), {
        modelValue: row.sourceValue,
        disabled: row.sourceType === 'PATH',
        class: row.sourceType === 'PATH' ? 'invalid-source' : '',
        placeholder: row.sourceType === 'LITERAL'
          ? '输入固定值'
          : '当前来源不再受支持，请重新选择',
        'onUpdate:modelValue': value => patch(index, { sourceValue: value })
      })
    }

    return () => h('div', { class: 'mapping-rows' },
      rowProps.modelValue.map((row, index) =>
        h('div', { class: 'mapping-row', key: `${row.target}:${index}` }, [
          h(resolveComponent('ElSelect'), {
            modelValue: row.target,
            placeholder: `选择${rowProps.targetLabel}`,
            'onUpdate:modelValue': value => patch(index, { target: value })
          }, () => rowProps.targetOptions.map(item =>
            h(resolveComponent('ElOption'), {
              key: item.value,
              label: item.label,
              value: item.value,
              disabled: item.invalid === true
            })
          )),
          h(resolveComponent('ElSelect'), {
            modelValue: row.sourceType,
            'onUpdate:modelValue': value => patch(index, {
              sourceType: value,
              sourceValue: ''
            })
          }, () => sourceTypes.map(item =>
            h(resolveComponent('ElOption'), {
              key: item.value,
              label: item.label,
              value: item.value,
              disabled: item.disabled === true
            })
          )),
          sourceControl(row, index),
          h(resolveComponent('ElButton'), {
            link: true,
            type: 'danger',
            onClick: () => remove(index)
          }, () => '删除')
        ])
      )
    )
  }
})
</script>

<style scoped>
.subform-mapping-editor {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mapping-group {
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.mapping-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.mapping-heading div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.mapping-heading span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

:deep(.mapping-rows) {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

:deep(.mapping-row) {
  display: grid;
  grid-template-columns: minmax(150px, 1fr) 120px minmax(170px, 1fr) 48px;
  gap: 8px;
  align-items: center;
}

:deep(.source-fixed) {
  padding: 0 10px;
  color: var(--el-text-color-regular);
  font-family: monospace;
  font-size: 12px;
}

:deep(.invalid-source .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}
</style>
