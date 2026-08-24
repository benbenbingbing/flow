<template>
  <el-dialog
    v-model="visible"
    title="初始化与数据处理"
    width="920px"
    destroy-on-close
    :close-on-click-modal="false"
  >
    <div class="toolbar">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="FORM_INIT 仅在新增时初始化数据；AFTER_LOAD 用于全模式加载后处理；BEFORE_SUBMIT 默认只在后端执行。同一时机的多个步骤按列表顺序执行。"
      />
      <el-button type="primary" plain @click="add">
        添加处理步骤
      </el-button>
    </div>
    <el-empty
      v-if="rows.length === 0"
      description="尚未配置初始化或数据处理"
    />
    <div
      v-for="(binding, index) in rows"
      :key="binding.rowKey"
      class="binding-row"
    >
      <div class="row-header">
        <strong>{{ getFormDataSourceBindingStepLabel(rows, index) }}</strong>
        <div>
          <el-button
            link
            :disabled="!canMoveBinding(index, -1)"
            @click="moveBinding(index, -1)"
          >
            上移
          </el-button>
          <el-button
            link
            :disabled="!canMoveBinding(index, 1)"
            @click="moveBinding(index, 1)"
          >
            下移
          </el-button>
          <el-button link type="danger" @click="rows.splice(index, 1)">
            删除
          </el-button>
        </div>
      </div>
      <el-form label-width="96px" size="small">
        <div class="mapping-grid">
          <el-form-item label="处理时机">
            <template #label>
              <ConfigHelpLabel
                label="处理时机"
                help-key="formDataSource.usage"
              />
            </template>
            <el-select
              v-model="binding.usage"
              style="width: 100%"
              @change="handleUsageChange(binding)"
            >
              <el-option
                v-for="usage in usages"
                :key="usage.value"
                :label="usage.label"
                :value="usage.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="数据源">
            <template #label>
              <ConfigHelpLabel
                label="数据源"
                help-key="uiDataSource.service"
              />
            </template>
            <el-select
              v-model="binding.serviceId"
              clearable
              filterable
              placeholder="选择受控数据源"
              style="width: 100%"
              @change="handleSourceChange(binding)"
            >
              <el-option
                v-for="source in sourcesForUsage(binding.usage)"
                :key="source.id"
                :label="`${source.sourceName} (${source.sourceType})`"
                :value="source.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            v-if="binding.serviceId"
            label="接口操作"
            required
          >
            <el-select
              v-model="binding.operationCode"
              filterable
              placeholder="选择接口操作"
              style="width: 100%"
            >
              <el-option
                v-for="operation in operationsFor(binding)"
                :key="operation.code"
                :label="`${operation.name} (${operation.code})`"
                :value="operation.code"
              />
            </el-select>
          </el-form-item>
        </div>
        <div
          v-if="binding.usage === 'BEFORE_SUBMIT'"
          class="prevalidate"
        >
          <el-checkbox v-model="binding.clientPrevalidate">
            浏览器预校验
          </el-checkbox>
          <el-checkbox
            v-model="binding.sideEffectFree"
            :disabled="!binding.clientPrevalidate"
          >
            无副作用
          </el-checkbox>
          <span>只有两项同时开启时浏览器才会执行；后端始终是最终权威。</span>
        </div>
        <div class="mapping-grid">
          <el-form-item label="请求参数映射">
            <template #label>
              <JsonConfigLabel
                label="请求参数映射"
                help-key="entityForm.dataSourceInputMapping"
              />
            </template>
            <el-input
              v-model="binding.inputMappingText"
              type="textarea"
              :rows="4"
              placeholder='{"filters.ownerId":"data.ownerId"}'
            />
          </el-form-item>
          <el-form-item label="返回字段映射">
            <template #label>
              <JsonConfigLabel
                label="返回字段映射"
                help-key="entityForm.dataSourceOutputMapping"
              />
            </template>
            <el-input
              v-model="binding.outputMappingText"
              type="textarea"
              :rows="4"
              placeholder='{"ownerName":"data.user.name"}'
            />
          </el-form-item>
        </div>
      </el-form>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">
        保存初始化与数据处理
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { patchFormMetadata } from '@/api/entityForm'
import { uiDataSourceApi } from '@/api/uiConfig'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import JsonConfigLabel from '@/components/JsonConfigLabel.vue'
import { safeParseConfig, stringifyConfig } from '@/shared/config-runtime'
import { parseJsonConfig } from '@/utils/jsonConfig'
import { serviceOperations } from './interfaceServiceModel'
import {
  FORM_DATA_SOURCE_USAGE_OPTIONS,
  assertUniqueFormDataSourceOutputTargets,
  getFormDataSourceBindingStepLabel
} from '@/shared/form-runtime'

const props = defineProps({
  form: { type: Object, required: true },
  dataSourcesByUsage: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['saved', 'error'])
const visible = ref(false)
const saving = ref(false)
const rows = ref([])
let rowSequence = 0
const usages = FORM_DATA_SOURCE_USAGE_OPTIONS

function newRow(value = {}) {
  return {
    rowKey: `form_source_${++rowSequence}`,
    usage: value.usage || 'FORM_INIT',
    serviceId: value.serviceId || '',
    operationCode: value.operationCode || '',
    inputMappingText: stringifyConfig(value.inputMapping || {}),
    outputMappingText: stringifyConfig(value.outputMapping || {}),
    clientPrevalidate: value.clientPrevalidate === true,
    sideEffectFree: value.sideEffectFree === true,
    extra: value.extra || {}
  }
}

function open() {
  if (!props.form.id) {
    ElMessage.warning('请先保存表单草稿')
    return
  }
  const bindings = safeParseConfig(props.form.dataSourceBindingsDocument)
  const values = []
  Object.entries(bindings).forEach(([usage, configured]) => {
    const items = Array.isArray(configured) ? configured : [configured]
    items.filter(Boolean).forEach(value => {
      const normalized = value && typeof value === 'object'
        ? { ...value }
        : {}
      const {
        serviceId,
        operationCode,
        inputMapping,
        outputMapping,
        clientPrevalidate,
        sideEffectFree,
        usage: ignoredUsage,
        ...extra
      } = normalized
      values.push(newRow({
        usage,
        serviceId,
        operationCode,
        inputMapping,
        outputMapping,
        clientPrevalidate,
        sideEffectFree,
        extra
      }))
    })
  })
  rows.value = values
  visible.value = true
}

function add() {
  rows.value.push(newRow())
}

function adjacentBindingIndex(index, direction) {
  const usage = rows.value[index]?.usage
  for (
    let candidate = index + direction;
    candidate >= 0 && candidate < rows.value.length;
    candidate += direction
  ) {
    if (rows.value[candidate]?.usage === usage) return candidate
  }
  return -1
}

function canMoveBinding(index, direction) {
  return adjacentBindingIndex(index, direction) >= 0
}

/**
 * 步骤顺序只在同一处理时机内生效；交换同 usage 行可避免其他生命周期步骤被连带重排。
 */
function moveBinding(index, direction) {
  const targetIndex = adjacentBindingIndex(index, direction)
  if (targetIndex < 0) return
  const nextRows = [...rows.value]
  ;[nextRows[index], nextRows[targetIndex]] = [
    nextRows[targetIndex],
    nextRows[index]
  ]
  rows.value = nextRows
}

function sourcesForUsage(usage) {
  return props.dataSourcesByUsage?.[usage] || []
}

function operationsFor(binding) {
  const source = sourcesForUsage(binding.usage).find(item =>
    String(item.id) === String(binding.serviceId))
  return source ? serviceOperations(source) : []
}

function handleUsageChange(binding) {
  binding.serviceId = ''
  binding.operationCode = ''
}

function handleSourceChange(binding) {
  if (!binding.serviceId) {
    binding.operationCode = ''
    return
  }
  const operations = operationsFor(binding)
  binding.operationCode = operations.length === 1
    ? operations[0].code
    : ''
}

function serialize() {
  const bindings = {}
  const serializedRows = rows.value.map(row => {
    if (!row.serviceId) throw new Error('初始化与数据处理的数据源不能为空')
    if (!row.operationCode) throw new Error('初始化与数据处理必须选择接口操作')
    if (row.usage === 'BEFORE_SUBMIT'
      && row.clientPrevalidate
      && !row.sideEffectFree) {
      throw new Error('浏览器预校验必须同时标记为无副作用')
    }
    return {
      usage: row.usage,
      clientPrevalidate: row.clientPrevalidate === true,
      sideEffectFree: row.sideEffectFree === true,
      binding: {
        ...(row.extra || {}),
        serviceId: row.serviceId,
        operationCode: row.operationCode,
        inputMapping: parseJsonConfig(row.inputMappingText, {
          fieldName: '请求参数映射'
        }),
        outputMapping: parseJsonConfig(row.outputMappingText, {
          fieldName: '返回字段映射'
        })
      }
    }
  })
  assertUniqueFormDataSourceOutputTargets(
    serializedRows.map(row => ({
      usage: row.usage,
      outputMapping: row.binding.outputMapping
    }))
  )
  serializedRows.forEach(({
    usage,
    binding,
    clientPrevalidate,
    sideEffectFree
  }) => {
    if (usage === 'BEFORE_SUBMIT') {
      binding.clientPrevalidate = clientPrevalidate
      binding.sideEffectFree = sideEffectFree
    }
    if (!bindings[usage]) bindings[usage] = []
    bindings[usage].push(binding)
  })
  Object.keys(bindings).forEach(usage => {
    if (bindings[usage].length === 1) bindings[usage] = bindings[usage][0]
  })
  return bindings
}

async function save() {
  saving.value = true
  try {
    const bindings = serialize()
    await Promise.all(rows.value.map(row =>
      uiDataSourceApi.validateBinding(row.serviceId, row.usage)))
    const updated = await patchFormMetadata(props.form.id, {
      expectedRevision: props.form.revision,
      dataSourceBindings: bindings
    })
    visible.value = false
    emit('saved', updated)
    ElMessage.success('初始化与数据处理草稿已保存，发布后生效')
  } catch (error) {
    emit('error', error)
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.toolbar :deep(.el-alert) {
  flex: 1;
}

.binding-row {
  margin-bottom: 12px;
  padding: 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
}

.row-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.mapping-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.prevalidate {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: -4px 0 12px 96px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
