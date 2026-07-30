<template>
  <el-dialog
    v-model="visible"
    title="表单级统一数据源"
    width="920px"
    destroy-on-close
    :close-on-click-modal="false"
  >
    <div class="toolbar">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="FORM_INIT 用于初始化整表数据；AFTER_LOAD 用于加载后处理；BEFORE_SUBMIT 默认只在后端执行。"
      />
      <el-button type="primary" plain @click="add">
        添加绑定
      </el-button>
    </div>
    <el-empty
      v-if="rows.length === 0"
      description="尚未配置表单级数据源"
    />
    <div
      v-for="(binding, index) in rows"
      :key="binding.rowKey"
      class="binding-row"
    >
      <div class="row-header">
        <strong>绑定 {{ index + 1 }}</strong>
        <el-button link type="danger" @click="rows.splice(index, 1)">
          删除
        </el-button>
      </div>
      <el-form label-width="96px" size="small">
        <div class="mapping-grid">
          <el-form-item label="绑定位置">
            <el-select v-model="binding.usage" style="width: 100%">
              <el-option
                v-for="usage in usages"
                :key="usage.value"
                :label="usage.label"
                :value="usage.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="数据源">
            <el-select
              v-model="binding.sourceId"
              clearable
              filterable
              placeholder="选择受控数据源"
              style="width: 100%"
            >
              <el-option
                v-for="source in dataSources"
                :key="source.id"
                :label="`${source.sourceName} (${source.sourceType})`"
                :value="source.id"
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
          <el-form-item label="输入映射">
            <el-input
              v-model="binding.inputMappingText"
              type="textarea"
              :rows="4"
              placeholder='{"filters.ownerId":"data.ownerId"}'
            />
          </el-form-item>
          <el-form-item label="输出映射">
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
        保存表单数据源
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { patchFormMetadata } from '@/api/entityForm'
import { uiDataSourceApi } from '@/api/uiConfig'
import { safeParseConfig, stringifyConfig } from '@/shared/config-runtime'
import { parseJsonConfig } from '@/utils/jsonConfig'

const props = defineProps({
  form: { type: Object, required: true },
  dataSources: { type: Array, default: () => [] }
})

const emit = defineEmits(['saved', 'error'])
const visible = ref(false)
const saving = ref(false)
const rows = ref([])
let rowSequence = 0
const usages = [
  { label: '表单初始化', value: 'FORM_INIT' },
  { label: '加载后处理', value: 'AFTER_LOAD' },
  { label: '提交前处理', value: 'BEFORE_SUBMIT' }
]

function newRow(value = {}) {
  return {
    rowKey: `form_source_${++rowSequence}`,
    usage: value.usage || 'FORM_INIT',
    sourceId: value.sourceId || value.id || '',
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
      const normalized = typeof value === 'string' ? { sourceId: value } : { ...value }
      const {
        sourceId,
        id,
        inputMapping,
        outputMapping,
        clientPrevalidate,
        sideEffectFree,
        usage: ignoredUsage,
        ...extra
      } = normalized
      values.push(newRow({
        usage,
        sourceId: sourceId || id,
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

function serialize() {
  const bindings = {}
  rows.value.forEach(row => {
    if (!row.sourceId) throw new Error('表单级数据源不能为空')
    if (row.usage === 'BEFORE_SUBMIT'
      && row.clientPrevalidate
      && !row.sideEffectFree) {
      throw new Error('浏览器预校验必须同时标记为无副作用')
    }
    const binding = {
      ...(row.extra || {}),
      sourceId: row.sourceId,
      inputMapping: parseJsonConfig(row.inputMappingText, {
        fieldName: '表单数据源输入映射'
      }),
      outputMapping: parseJsonConfig(row.outputMappingText, {
        fieldName: '表单数据源输出映射'
      })
    }
    if (row.usage === 'BEFORE_SUBMIT') {
      binding.clientPrevalidate = row.clientPrevalidate === true
      binding.sideEffectFree = row.sideEffectFree === true
    }
    if (!bindings[row.usage]) bindings[row.usage] = []
    bindings[row.usage].push(binding)
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
      uiDataSourceApi.validateBinding(row.sourceId, row.usage)))
    const updated = await patchFormMetadata(props.form.id, {
      expectedRevision: props.form.revision,
      dataSourceBindings: bindings
    })
    visible.value = false
    emit('saved', updated)
    ElMessage.success('表单级数据源草稿已保存，发布后生效')
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
