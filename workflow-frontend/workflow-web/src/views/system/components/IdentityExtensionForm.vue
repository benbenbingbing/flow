<template>
  <div v-if="loading || error || schemas.length" v-loading="loading" class="identity-extensions">
    <el-alert v-if="error" :title="error" type="error" :closable="false">
      <el-button link type="primary" @click="load">重新加载</el-button>
    </el-alert>
    <el-card v-for="schema in schemas" :key="schema.code" shadow="never" class="extension-card">
      <template #header>
        {{ schema.title }}
        <el-tag v-if="schema.maintenance === 'SOURCE'" type="info" size="small">业务系统维护</el-tag>
      </template>
      <template v-if="drafts[schema.code]">
        <el-form-item v-for="field in schema.fields" :key="field.name" :label="field.label" :required="field.required && editable(schema)">
          <el-select v-if="field.control === 'SELECT'" v-model="drafts[schema.code][field.name]" :disabled="!editable(schema)" clearable>
            <el-option v-for="option in field.options" :key="option" :label="option" :value="option" />
          </el-select>
          <el-input-number v-else-if="field.control === 'NUMBER' && !field.numberAsString" v-model="drafts[schema.code][field.name]" :disabled="!editable(schema)" />
          <el-switch v-else-if="field.control === 'BOOLEAN'" v-model="drafts[schema.code][field.name]" :disabled="!editable(schema)" />
          <el-date-picker v-else-if="field.control === 'DATE'" v-model="drafts[schema.code][field.name]" type="date" value-format="YYYY-MM-DD" :disabled="!editable(schema)" />
          <el-input v-else v-model="drafts[schema.code][field.name]" :maxlength="field.maxLength" :disabled="!editable(schema)" :placeholder="field.control === 'DATETIME' ? 'ISO 日期时间，例如 2026-11-02T09:00:00' : ''" />
        </el-form-item>
      </template>
      <el-button v-else-if="editable(schema)" @click="drafts[schema.code] = {}">添加资料</el-button>
      <el-text v-else type="info">尚无资料</el-text>
    </el-card>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { extensionSnapshot, buildExtensionChanges } from '@/shared/identity-extension-form'

const props = defineProps({ targetType: { type: String, required: true }, targetId: { type: String, default: '' }, readonly: Boolean })
const schemas = ref([])
const drafts = ref({})
const originals = ref({})
const versions = ref({})
const loading = ref(false)
const error = ref('')
let generation = 0
const editable = schema => schema.writable && schema.maintenance === 'LOCAL' && !props.readonly

/** 切换目标后丢弃旧响应，避免慢请求把上一用户的资料写入当前编辑草稿。 */
async function load() {
  const current = ++generation
  loading.value = true
  error.value = ''
  schemas.value = []
  drafts.value = {}
  originals.value = {}
  versions.value = {}
  try {
    const [definitions, detail] = await Promise.all([
      request.get('/system/identity/extensions/schema', { params: { targetType: props.targetType } }),
      props.targetId ? request.get('/system/identity/extensions/' + props.targetType + '/' + encodeURIComponent(props.targetId)) : Promise.resolve({ extensions: {} })
    ])
    if (current !== generation) return
    schemas.value = definitions
    for (const schema of definitions) {
      const existing = detail.extensions?.[schema.code]
      if (!existing) continue
      const data = extensionSnapshot(schema, existing.data)
      drafts.value[schema.code] = structuredClone(data)
      originals.value[schema.code] = JSON.stringify(data)
      versions.value[schema.code] = existing.rowVersion
    }
  } catch (cause) {
    if (current === generation) error.value = '扩展资料加载失败，请重新加载后保存。'
  } finally {
    if (current === generation) loading.value = false
  }
}
function getChanges() {
  try {
    if (loading.value || error.value) throw new Error('请等待扩展资料加载完成或重试')
    return props.readonly ? {} : buildExtensionChanges(schemas.value, drafts.value, originals.value, versions.value)
  } catch (cause) {
    ElMessage.error(cause.message)
    throw cause
  }
}
watch(() => [props.targetType, props.targetId], load, { immediate: true })
defineExpose({ getChanges, reload: load })
</script>

<style scoped>
.identity-extensions { margin-top: 16px; }
.extension-card { margin-bottom: 12px; }
.extension-card :deep(.el-card__header) { display: flex; gap: 12px; align-items: center; }
</style>
