<template>
  <SettingsSection title="实体字段赋值" description="实际经过当前连线时，更新当前实体。保存后需要发布流程。" :collapsible="false" primary>
    <p class="assignment-note">支持已发布的普通文本、数字、布尔、日期及单选字段。清空会写入空值；固定值中的 ${...} 按原文保存。字段更新不会自动同步流程变量。</p>
    <el-alert v-if="parseError || loadError" :title="parseError || loadError" type="error" :closable="false" show-icon />
    <el-button v-if="loadError" link type="primary" @click="loadFields">重新加载字段</el-button>
    <el-button v-if="parseError" link type="primary" @click="resetRows">重新配置字段赋值</el-button>
    <div v-loading="loading" class="assignment-list">
      <div v-for="(row, index) in rows" :key="index" class="assignment-row">
        <el-select v-model="row.fieldCode" aria-label="赋值字段" placeholder="选择字段" filterable @change="changeField(row)">
          <el-option v-if="row.fieldCode && !findField(row)" :value="row.fieldCode" :label="`${row.fieldCode}（不可用）`" disabled />
          <el-option v-for="field in fields" :key="field.fieldCode" :value="field.fieldCode" :label="field.fieldName"
            :disabled="rows.some(other => other !== row && other.fieldCode === field.fieldCode)" />
        </el-select>
        <el-select v-model="row.operation" aria-label="赋值方式" @change="changeOperation(row)">
          <el-option value="SET" label="固定值" />
          <el-option value="CLEAR" label="清空" :disabled="findField(row)?.required" />
        </el-select>
        <div class="assignment-value">
          <span v-if="!findField(row)">{{ loading ? '正在加载字段' : '字段当前不可用' }}</span>
          <span v-else-if="row.operation === 'CLEAR'">写入空值</span>
          <el-select v-else-if="findField(row)?.fieldType === 'BOOLEAN'" v-model="row.value" aria-label="固定布尔值">
            <el-option :value="true" label="是" /><el-option :value="false" label="否" />
          </el-select>
          <el-select v-else-if="['SELECT', 'RADIO'].includes(findField(row)?.fieldType)" v-model="row.value" aria-label="固定选项" placeholder="选择值">
            <el-option v-for="option in findField(row).options" :key="option.value" :value="option.value" :label="option.label" :disabled="option.disabled" />
          </el-select>
          <el-input-number v-else-if="findField(row)?.fieldType === 'INTEGER'" v-model="row.value" aria-label="固定整数" :step="1" :precision="0" :min="-2147483648" :max="2147483647" controls-position="right" />
          <el-date-picker v-else-if="findField(row)?.fieldType === 'DATE'" v-model="row.value" aria-label="固定日期" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
          <el-date-picker v-else-if="findField(row)?.fieldType === 'DATETIME'" v-model="row.value" aria-label="固定日期时间" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择日期时间" />
          <el-input v-else v-model="row.value" aria-label="固定值" :type="findField(row)?.fieldType === 'TEXT' ? 'textarea' : 'text'"
            :placeholder="['LONG', 'DECIMAL'].includes(findField(row)?.fieldType) ? '输入十进制数，保留完整精度' : '输入固定值'" />
        </div>
        <el-button link type="danger" aria-label="删除字段赋值" @click="rows.splice(index, 1)">删除</el-button>
      </div>
      <el-button link type="primary" :disabled="loading || Boolean(parseError || loadError) || rows.length >= 50" @click="rows.push({ fieldCode: '', operation: 'SET', value: '' })">添加字段</el-button>
      <el-empty v-if="!loading && !loadError && !fields.length" description="当前实体没有可赋值的已发布普通字段" :image-size="60" />
    </div>
  </SettingsSection>
</template>

<script setup>
import { ref, watch } from 'vue'
import { processActionApi } from '@/api/processAction'
import SettingsSection from '@/components/SettingsSection.vue'
import { parseFieldAssignments, buildFieldAssignments, initialAssignmentValue } from '@/shared/entity-field-assignment'

const props = defineProps({ processId: { type: String, required: true }, paramsJson: { type: String, default: '' } })
const rows = ref([])
const fields = ref([])
const loading = ref(false)
const parseError = ref('')
const loadError = ref('')
let requestId = 0

watch(() => props.paramsJson, raw => {
  try { rows.value = parseFieldAssignments(raw); parseError.value = '' }
  catch (error) { rows.value = []; parseError.value = error.message }
}, { immediate: true })
watch(() => props.processId, loadFields, { immediate: true })

/** 流程切换后丢弃旧响应；加载失败时保留已保存配置，禁止以空候选覆盖它。 */
async function loadFields() {
  const current = ++requestId
  fields.value = []
  loading.value = true
  loadError.value = ''
  try {
    const result = await processActionApi.assignableFields(props.processId)
    if (current === requestId) fields.value = result || []
  } catch (error) {
    if (current === requestId) loadError.value = error?.message || '加载可赋值字段失败，请重试。'
  } finally { if (current === requestId) loading.value = false }
}
function findField(row) { return fields.value.find(field => field.fieldCode === row.fieldCode) }
function changeField(row) { row.operation = 'SET'; row.value = initialAssignmentValue(findField(row)) }
function changeOperation(row) {
  if (row.operation === 'CLEAR') delete row.value
  else row.value = initialAssignmentValue(findField(row))
}
function resetRows() { rows.value = []; parseError.value = '' }

/** 父级保存前调用；加载失败、非法原配置和不可用字段都必须先由用户处理。 */
function paramsJson() {
  if (loading.value) throw new Error('正在加载可赋值字段，请稍后保存。')
  if (parseError.value || loadError.value) throw new Error(parseError.value || loadError.value)
  return buildFieldAssignments(rows.value, fields.value)
}
defineExpose({ paramsJson })
</script>

<style scoped>
.assignment-note { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
.assignment-list { margin-top: 12px; }
.assignment-row { display: grid; grid-template-columns: minmax(120px, 1fr) 95px minmax(150px, 1.4fr) 40px; gap: 8px; align-items: center; margin-bottom: 10px; }
.assignment-value { min-width: 0; }
.assignment-value :deep(.el-input-number), .assignment-value :deep(.el-select), .assignment-value :deep(.el-date-editor) { width: 100%; }
</style>
