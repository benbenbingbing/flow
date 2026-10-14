<template>
  <div class="user-relation-path" v-loading="loading">
    <div v-for="(row, index) in rows" :key="index" class="path-row">
      <span class="path-level">第 {{ index + 1 }} 级</span>
      <el-select :model-value="stepKey(steps[index])" filterable clearable
        :placeholder="index && terminal ? '可继续选择下级关系' : '请选择实体关系'"
        no-data-text="此实体没有可继续选择的关系" @change="value => select(index, value)">
        <el-option v-for="option in row" :key="stepKey(option.step)" :value="stepKey(option.step)"
          :label="`${option.label} (${option.step.code})`" :disabled="!!option.disabledReason">
          <span>{{ option.label }}</span>
          <span class="option-kind">{{ option.multiple ? '多值' : '单值' }}{{ option.terminal ? ' · 用户' : '' }}</span>
        </el-option>
      </el-select>
    </div>
    <div v-if="labels.length" class="path-summary">{{ labels.join(' → ') }}</div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <p v-else-if="terminal" class="path-hint">已选到用户，可直接使用，也可继续选择其关联实体。{{ multiple ? '此路径可能返回多位用户。' : '此路径返回单个用户。' }}</p>
    <p v-else-if="steps.length" class="path-hint">请继续选择，直到最后一级为用户。</p>
    <p v-if="steps.length >= 8" class="path-hint">已达到 8 级关系上限。</p>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import request from '@/utils/request'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  processId: { type: String, required: true },
  entityCode: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'state', 'change'])
const rows = ref([])
const labels = ref([])
const loading = ref(false)
const error = ref('')
const terminal = ref(false)
const multiple = ref(false)
let sequence = 0
const steps = computed(() => props.modelValue.schemaVersion === 2
  ? (props.modelValue.path || [])
  : props.modelValue.fieldCode ? [{ type: 'REFERENCE_FIELD', code: props.modelValue.fieldCode }] : [])
const stepKey = step => step ? `${step.type}:${step.code}:${['REVERSE_REFERENCE', 'PARENT_RELATION'].includes(step.type) ? step.targetEntityCode || '' : ''}` : ''

/** 上游变化只保留此前路径；字段坐标由服务端选项提供，名称和基数不写入执行配置。 */
function select(index, value) {
  const option = rows.value[index]?.find(item => stepKey(item.step) === value)
  const path = steps.value.slice(0, index)
  if (option) path.push(option.step)
  emit('state', { valid: false, loading: true, multiple: false })
  emit('update:modelValue', { schemaVersion: 2, entityCode: props.entityCode, path })
  emit('change')
}

/** 按前缀逐级回显；序号隔离快速切换节点/流程的旧响应，失效坐标保留供用户修正。 */
async function load() {
  const current = ++sequence
  loading.value = true
  error.value = ''
  terminal.value = false
  multiple.value = false
  rows.value = []
  labels.value = []
  emit('state', { valid: false, loading: true, multiple: false })
  const selected = steps.value.map(step => ({ ...step }))
  try {
    if (!props.processId || !props.entityCode) throw new Error('当前流程尚未绑定实体')
    const base = `/process/${encodeURIComponent(props.processId)}/user-relation-path`
    const collected = []
    const names = []
    for (let index = 0; index <= selected.length && index < 8; index++) {
      const options = await request.get(`${base}/options`, {
        params: { prefix: JSON.stringify(selected.slice(0, index)) }, silentError: true
      })
      if (current !== sequence) return
      collected.push(options || [])
      rows.value = [...collected]
      if (index < selected.length) {
        const option = options?.find(item => stepKey(item.step) === stepKey(selected[index]))
        if (!option || option.disabledReason) {
          names.push(selected[index].code)
          labels.value = names
          throw new Error(`第 ${index + 1} 级关系失效：${option?.disabledReason || selected[index].code}`)
        }
        names.push(option.label)
        labels.value = [...names]
      }
    }
    if (selected.length) {
      const last = collected[selected.length - 1]?.find(item => stepKey(item.step) === stepKey(selected.at(-1)))
      terminal.value = !!last?.terminal
      if (terminal.value) {
        const description = await request.get(base, { params: { path: JSON.stringify(selected) }, silentError: true })
        if (current !== sequence) return
        multiple.value = description.multiple
      }
    }
    emit('state', { valid: terminal.value, loading: false, multiple: multiple.value })
  } catch (failure) {
    if (current !== sequence) return
    error.value = failure.message || '关系路径加载失败'
    emit('state', { valid: false, loading: false, multiple: false })
  } finally {
    if (current === sequence) loading.value = false
  }
}
watch(() => JSON.stringify([props.processId, props.entityCode, steps.value]), load, { immediate: true })
</script>

<style scoped>
.user-relation-path { width: 100%; min-height: 36px; }
.path-row { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; }
.path-level { flex-shrink: 0; color: var(--el-text-color-secondary); font-size: 12px; }
.path-row .el-select { flex: 1; min-width: 0; }
.path-summary { font-size: 13px; line-height: 1.6; overflow-wrap: anywhere; }
.path-hint { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; margin: 6px 0; }
.option-kind { color: var(--el-text-color-secondary); margin-left: 12px; font-size: 12px; }
</style>
