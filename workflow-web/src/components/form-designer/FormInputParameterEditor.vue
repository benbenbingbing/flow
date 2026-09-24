<template>
  <div class="input-parameter-editor">
    <div class="editor-heading">
      <div>
        <h3>
          <ConfigHelpLabel
            label="输入参数"
            help-key="form.inputParameterSchema"
          />
        </h3>
        <p>声明当前页面接收的参数，可由来源表单、列表按钮或父表单传入。</p>
      </div>
      <el-button type="primary" plain @click="addParameter">添加参数</el-button>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="参数可用于字段初始化、列表查询或接口取值；映射到字段并保存后才会写入实体数据。"
    />

    <el-table :data="rows" border class="parameter-table">
      <el-table-column label="中文名称" min-width="150">
        <template #default="{ row }">
          <el-input v-model="row.name" placeholder="例如：项目ID" />
        </template>
      </el-table-column>
      <el-table-column label="参数编码" min-width="170">
        <template #default="{ row }">
          <el-input v-model="row.code" placeholder="例如：projectId" @focus="rememberParameterCode(row)" @blur="commitParameterCode(row)" />
        </template>
      </el-table-column>
      <el-table-column label="类型" width="130">
        <template #default="{ row }">
          <el-select v-model="row.type">
            <el-option
              v-for="item in typeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="必填" width="84" align="center">
        <template #default="{ row }">
          <el-switch v-model="row.required" />
        </template>
      </el-table-column>
      <el-table-column label="默认值" min-width="150">
        <template #default="{ row }">
          <el-select
            v-if="row.type === 'boolean'"
            v-model="row.defaultValue"
            clearable
            placeholder="来源页面未传值时使用"
          >
            <el-option label="true" :value="true" />
            <el-option label="false" :value="false" />
          </el-select>
          <el-input-number
            v-else-if="row.type === 'number' || row.type === 'integer'"
            v-model="row.defaultValue"
            :precision="row.type === 'integer' ? 0 : undefined"
            controls-position="right"
            placeholder="来源页面未传值时使用"
          />
          <el-input
            v-else
            v-model="row.defaultValue"
            :type="['object', 'array'].includes(row.type) ? 'textarea' : 'text'"
            :rows="2"
            :placeholder="defaultValuePlaceholder(row.type)"
          />
        </template>
      </el-table-column>
      <el-table-column label="说明" min-width="190">
        <template #default="{ row }">
          <el-input v-model="row.description" placeholder="说明参数用途" />
        </template>
      </el-table-column>
      <el-table-column label="" width="64" align="center">
        <template #default="{ $index }">
          <el-button link type="danger" @click="removeParameter($index)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty
      v-if="rows.length === 0"
      description="尚未声明输入参数"
      :image-size="64"
    />
    <div v-if="validationMessage" class="parameter-error">
      {{ validationMessage }}
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import {
  buildInputParameterSchema,
  getInputParameterDefinitions
} from '@flow/workflow-core/subform-parameter-contract'

const schema = defineModel({ type: Object, default: () => ({}) })
const emit = defineEmits(['code-change'])

const typeOptions = [
  { value: 'string', label: '文本' },
  { value: 'number', label: '数字' },
  { value: 'integer', label: '整数' },
  { value: 'boolean', label: '布尔值' },
  { value: 'object', label: '对象' },
  { value: 'array', label: '数组' }
]

const rows = ref(getInputParameterDefinitions(schema.value))
let syncingFromModel = false
let lastEmittedSchema = ''
const editOrigins = new WeakMap()

watch(
  schema,
  value => {
    // 输入框中的临时空编码尚不能进入 Schema；忽略本编辑器自己的写回，保留行供用户继续输入。
    if (JSON.stringify(value) === lastEmittedSchema) return
    const next = getInputParameterDefinitions(value)
    if (JSON.stringify(next) === JSON.stringify(rows.value)) return
    syncingFromModel = true
    rows.value = next
    syncingFromModel = false
  },
  { deep: true }
)

watch(
  rows,
  value => {
    if (syncingFromModel) return
    const nextSchema = buildInputParameterSchema(value)
    lastEmittedSchema = JSON.stringify(nextSchema)
    schema.value = nextSchema
  },
  { deep: true }
)

const validationMessage = computed(() => {
  const codes = rows.value.map(item => String(item.code || '').trim())
    .filter(Boolean)
  if (new Set(codes).size !== codes.length) return '参数编码不能重复'
  const invalid = codes.find(code => !/^[A-Za-z][A-Za-z0-9_]{0,99}$/.test(code))
  if (invalid) {
    return `参数编码 ${invalid} 不合法，应以字母开头且只包含字母、数字和下划线`
  }
  const invalidJson = rows.value.find(item =>
    ['object', 'array'].includes(item.type)
      && item.defaultValue !== ''
      && !matchesStructuredDefault(item.defaultValue, item.type)
  )
  return invalidJson
    ? `${invalidJson.name || invalidJson.code}的默认值必须是有效的${
        invalidJson.type === 'array' ? 'JSON 数组' : 'JSON 对象'
      }`
    : ''
})

function updateRows(next) {
  rows.value = [...next]
}

function rememberParameterCode(row) {
  if (!editOrigins.has(row)) editOrigins.set(row, row.code)
}

function commitParameterCode(row) {
  const previousCode = editOrigins.get(row)
  const nextCode = String(row.code || '').trim()
  // 空值、非法值和重复编码都不应重绑定用途；用户改成合法新键并离开输入框后再一次性迁移引用。
  if (!previousCode || !/^[A-Za-z][A-Za-z0-9_]{0,99}$/.test(nextCode)
    || rows.value.some(item => item !== row && item.code === nextCode)) return
  editOrigins.delete(row)
  if (previousCode !== nextCode) emit('code-change', previousCode, nextCode)
}

function addParameter() {
  const used = new Set(rows.value.map(item => item.code))
  let index = rows.value.length + 1
  let code = `param${index}`
  while (used.has(code)) {
    index += 1
    code = `param${index}`
  }
  updateRows([
    ...rows.value,
    {
      code,
      name: `参数${index}`,
      type: 'string',
      required: false,
      defaultValue: '',
      description: ''
    }
  ])
}

function removeParameter(index) {
  updateRows(rows.value.filter((_, rowIndex) => rowIndex !== index))
}

function defaultValuePlaceholder(type) {
  if (type === 'object') return '例如：{"source":"FORM"}'
  if (type === 'array') return '例如：["A","B"]'
  return '来源页面未传值时使用'
}

function matchesStructuredDefault(value, type) {
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    return type === 'array'
      ? Array.isArray(parsed)
      : parsed && typeof parsed === 'object' && !Array.isArray(parsed)
  } catch {
    return false
  }
}
</script>

<style scoped>
.input-parameter-editor {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.editor-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.editor-heading h3 {
  margin: 0 0 4px;
  font-size: 16px;
}

.editor-heading p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.parameter-table {
  width: 100%;
}

.parameter-error {
  color: var(--el-color-danger);
  font-size: 13px;
}
</style>
