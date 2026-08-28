<template>
  <section class="embed-form" aria-labelledby="embed-form-title">
    <header class="embed-form__header">
      <div>
        <p class="embed-form__mode">{{ modeLabel }}</p>
        <h1 id="embed-form-title">{{ form.title }}</h1>
      </div>
      <div class="embed-form__header-actions">
        <button
          v-if="canBack"
          type="button"
          class="embed-form__back"
          :disabled="loading || submitting"
          @click="$emit('back')"
        >
          返回列表
        </button>
        <span class="embed-form__actor" aria-label="当前用户">
          {{ bootstrap.actor.displayName }}
        </span>
      </div>
    </header>

    <div v-if="error" class="embed-form__error" role="alert">
      <span>{{ error.message }}</span>
      <button v-if="error.recoverable" type="button" @click="$emit('retry')">重试</button>
    </div>

    <form
      id="embed-record-create-form"
      class="embed-form__body"
      :class="`embed-form__body--${form.layout.type.toLowerCase()}`"
      :aria-busy="loading || submitting"
      @submit.prevent="submitCreate"
    >
      <div
        v-for="field in form.fields"
        :key="field.code"
        :class="['embed-form__field', spanClass(field)]"
      >
        <label :for="fieldId(field)">
          {{ field.label }}
          <span v-if="field.required" class="embed-form__required" aria-label="必填">*</span>
        </label>

        <template v-if="field.type === 'BOOLEAN'">
          <label class="embed-form__checkbox">
            <input
              :id="fieldId(field)"
              v-model="model[field.code]"
              type="checkbox"
              :disabled="field.readOnly || loading || submitting"
              @change="fieldChanged(field.code)"
            >
            <span>{{ model[field.code] ? '是' : '否' }}</span>
          </label>
        </template>

        <template v-else-if="isSelectField(field)">
          <input
            v-if="field.optionSource"
            :value="searches[field.code]"
            class="embed-form__search"
            type="search"
            maxlength="200"
            autocomplete="off"
            :disabled="field.readOnly || loading || submitting"
            :aria-label="`搜索${field.label}选项`"
            @input="searchRemote(field, $event.target.value)"
          >
          <select
            :id="fieldId(field)"
            v-model="model[field.code]"
            :multiple="field.type === 'MULTI_SELECT'"
            :disabled="field.readOnly || loading || submitting || choiceState(field).loading"
            @change="fieldChanged(field.code)"
          >
            <option v-if="field.type === 'SELECT' && !field.required" value="">请选择</option>
            <option
              v-for="option in selectOptions(field)"
              :key="choiceKey(option.value)"
              :value="option.value"
              :disabled="option.disabled"
            >
              {{ option.label }}
            </option>
          </select>
        </template>

        <input
          v-else
          :id="fieldId(field)"
          v-model="model[field.code]"
          :type="inputType(field)"
          :required="field.required"
          :readonly="field.readOnly"
          :disabled="loading || submitting"
          :min="numberConstraint(field, 'minimum')"
          :max="numberConstraint(field, 'maximum')"
          :minlength="textConstraint(field, 'minLength')"
          :maxlength="textConstraint(field, 'maxLength')"
          autocomplete="off"
          @change="fieldChanged(field.code)"
        >

        <span v-if="choiceState(field).loading" class="embed-form__hint" role="status">
          正在加载选项…
        </span>
        <span v-else-if="choiceState(field).error" class="embed-form__field-error" role="alert">
          {{ choiceErrorMessage(field) }}
        </span>
        <span v-else-if="choiceState(field).hasMore" class="embed-form__hint">
          结果较多，请输入关键字缩小范围
        </span>
      </div>

      <div v-if="loading || submitting" class="embed-form__loading" role="status">
        {{ submitting ? '正在保存记录…' : '正在刷新表单…' }}
      </div>
    </form>

    <footer v-if="form.mode === 'CREATE'" class="embed-form__footer">
      <p v-if="submitResult" class="embed-form__success" role="status">
        记录已创建：{{ submitResult.record.id }}
      </p>
      <p v-else-if="submitError" class="embed-form__submit-error" role="alert">
        {{ submitError.message }}
      </p>
      <button
        v-for="action in form.actions"
        :key="action.key"
        form="embed-record-create-form"
        type="submit"
        :disabled="!canSubmit(action)"
        :title="action.disabledReason || ''"
      >
        {{ submitting ? '保存中…' : action.label }}
      </button>
    </footer>

    <footer v-else-if="form.record?.meta?.updatedAt" class="embed-form__meta">
      更新时间：{{ formatDateTime(form.record.meta.updatedAt) }}
    </footer>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, watch } from 'vue'
import { mergeEmbedCreateDraft } from '../projection/normalizeEmbedForm.js'
import { createEmbedRemoteChoices } from './embedRemoteChoices.js'

const props = defineProps({
  bootstrap: { type: Object, required: true },
  form: { type: Object, required: true },
  controller: { type: Object, required: true },
  canBack: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  error: { type: Object, default: null },
  submitting: { type: Boolean, default: false },
  submitError: { type: Object, default: null },
  submitResult: { type: Object, default: null }
})

defineEmits(['retry', 'back'])

const model = reactive({})
const searches = reactive({})
const choices = reactive({})
const loaders = new Map()
const subscriptions = new Map()
const searchTimers = new Map()
let evaluationTimer

const modeLabel = computed(() => ({
  CREATE: '新建记录',
  VIEW: '查看记录'
}[props.form.mode] || '记录表单'))

function clearObject(value) {
  for (const key of Object.keys(value)) delete value[key]
}

function initialValue(field) {
  if (Object.prototype.hasOwnProperty.call(props.form.record?.values || {}, field.code)) {
    return props.form.record.values[field.code]
  }
  if (field.defaultValue !== null && field.defaultValue !== undefined) return field.defaultValue
  if (field.type === 'MULTI_SELECT') return []
  if (field.type === 'BOOLEAN') return false
  return ''
}

function destroyLoaders() {
  for (const timer of searchTimers.values()) globalThis.clearTimeout(timer)
  searchTimers.clear()
  for (const unsubscribe of subscriptions.values()) unsubscribe()
  subscriptions.clear()
  for (const loader of loaders.values()) loader.destroy()
  loaders.clear()
}

function createLoader(field) {
  if (!field.optionSource) return
  const loader = createEmbedRemoteChoices({
    query(keyword, options) {
      const input = { keyword, formValues: { ...model }, pageNum: 1 }
      return props.controller.queryFormOptions(field.code, { ...input, pageSize: 50 }, options)
    }
  })
  loaders.set(field.code, loader)
  subscriptions.set(field.code, loader.subscribe(value => {
    choices[field.code] = value
  }))
  loader.load('')
}

function resetForm(nextForm, previousForm) {
  const previousValues = { ...model }
  const nextValues = mergeEmbedCreateDraft(nextForm, previousForm, previousValues)
  destroyLoaders()
  clearObject(model)
  clearObject(searches)
  clearObject(choices)
  for (const field of nextForm.fields) {
    model[field.code] = Object.prototype.hasOwnProperty.call(nextValues, field.code)
      ? nextValues[field.code]
      : initialValue(field)
    searches[field.code] = ''
    createLoader(field)
  }
}

watch(() => props.form, resetForm, { immediate: true })
onBeforeUnmount(() => {
  destroyLoaders()
  if (evaluationTimer !== undefined) globalThis.clearTimeout(evaluationTimer)
})

function fieldId(field) { return `embed-form-${field.code}` }
function spanClass(field) {
  const span = Math.min(24, Math.max(1, Math.floor(Number(field.span) || 24)))
  return `embed-form__field--span-${span}`
}
function isSelectField(field) { return ['SELECT', 'MULTI_SELECT'].includes(field.type) }
function choiceKey(value) { return `${typeof value}:${String(value)}` }

function inputType(field) {
  if (field.type === 'NUMBER') return 'number'
  if (field.type === 'DATE') return 'date'
  if (field.type === 'DATETIME') return 'datetime-local'
  if (field.type === 'TIME') return 'time'
  return 'text'
}

function numberConstraint(field, key) {
  return typeof field.validation[key] === 'number' ? field.validation[key] : undefined
}

function textConstraint(field, key) {
  const value = Number(field.validation[key])
  return Number.isSafeInteger(value) && value >= 0 ? value : undefined
}

function choiceState(field) {
  return choices[field.code] || { loading: false, items: [], hasMore: false, error: null }
}

function includeCurrentOptions(field, candidates, valueKey) {
  const result = [...candidates]
  const currentValues = Array.isArray(model[field.code]) ? model[field.code] : [model[field.code]]
  for (const value of currentValues) {
    if (value === '' || value === null || value === undefined) continue
    if (!result.some(option => option[valueKey] === value)) {
      result.push(valueKey === 'id'
        ? { id: String(value), label: String(value) }
        : { value, label: String(value), disabled: false })
    }
  }
  return result
}

function selectOptions(field) {
  const source = field.optionSource ? choiceState(field).items : field.options
  return includeCurrentOptions(field, source, 'value')
}

function searchRemote(field, value) {
  searches[field.code] = String(value || '').slice(0, 200)
  const previous = searchTimers.get(field.code)
  if (previous !== undefined) globalThis.clearTimeout(previous)
  searchTimers.set(field.code, globalThis.setTimeout(() => {
    searchTimers.delete(field.code)
    loaders.get(field.code)?.load(searches[field.code])
  }, 250))
}

function fieldChanged(code) {
  for (const field of props.form.fields) {
    const dependencies = field.optionSource?.dependencies || []
    if (dependencies.some(rule => rule.source === 'CLIENT_WRITABLE' && rule.code === code)) {
      loaders.get(field.code)?.load(searches[field.code])
    }
  }
  scheduleCreateEvaluation()
}

/** 合并快速字段变更；控制器会继续用 Abort + sequence 处理在途响应竞态。 */
function scheduleCreateEvaluation() {
  if (props.form.mode !== 'CREATE' || typeof props.controller.evaluateCreate !== 'function') return
  if (evaluationTimer !== undefined) globalThis.clearTimeout(evaluationTimer)
  evaluationTimer = globalThis.setTimeout(async () => {
    evaluationTimer = undefined
    const values = {}
    for (const field of props.form.fields) {
      if (field.writable === true && field.readOnly !== true) {
        values[field.code] = model[field.code]
      }
    }
    try {
      await props.controller.evaluateCreate(values)
    } catch {
      // 控制器统一处理会话失效和结构化错误；组件保留当前草稿供用户修正或重试。
    }
  }, 120)
}

function choiceErrorMessage(field) {
  return choiceState(field).error?.normalizedEmbedError?.message || '选项暂时不可用'
}

function canSubmit(action) {
  return props.form.mode === 'CREATE'
    && props.bootstrap.capabilities.includes('RECORD_CREATE')
    && action.key === 'save'
    && action.transport === 'RECORD_CREATE'
    && action.enabled === true
    && action.idempotencyRequired === true
    && action.requiresRecordVersion !== true
    && !props.loading
    && !props.submitting
    && !props.submitResult
}

/** 组件只提取 External Projection 明确标记为 writable 的值。 */
async function submitCreate() {
  const action = props.form.actions.find(candidate => candidate.key === 'save')
  if (!canSubmit(action || {})) return
  if (evaluationTimer !== undefined) {
    globalThis.clearTimeout(evaluationTimer)
    evaluationTimer = undefined
  }
  const values = {}
  for (const field of props.form.fields) {
    if (field.writable === true && field.readOnly !== true) {
      values[field.code] = model[field.code]
    }
  }
  try {
    await props.controller.createRecord(values)
  } catch {
    // 控制器统一保存结构化错误并保留本次幂等键，组件只负责展示。
  }
}

function formatDateTime(value) {
  const timestamp = Date.parse(value)
  return Number.isFinite(timestamp) ? new Date(timestamp).toLocaleString() : '—'
}
</script>

<style scoped>
.embed-form {
  padding: 24px;
  color: #101828;
  font-family: Inter, ui-sans-serif, system-ui, sans-serif;
}

.embed-form__header {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.embed-form__header h1 { margin: 3px 0 0; font-size: 22px; }
.embed-form__mode { margin: 0; color: #475467; font-size: 13px; }
.embed-form__actor { color: #475467; font-size: 14px; }
.embed-form__header-actions { display: flex; align-items: center; gap: 12px; }
.embed-form__back {
  color: #344054;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
  background: #fff;
}

.embed-form__body {
  position: relative;
  display: grid;
  grid-template-columns: repeat(24, minmax(0, 1fr));
  gap: 18px 16px;
}

.embed-form__body--vertical .embed-form__field { grid-column: 1 / -1 !important; }
.embed-form__body--horizontal { align-items: end; }

.embed-form__field {
  display: grid;
  min-width: 0;
  gap: 7px;
}

.embed-form__field--span-1 { grid-column: span 1; }
.embed-form__field--span-2 { grid-column: span 2; }
.embed-form__field--span-3 { grid-column: span 3; }
.embed-form__field--span-4 { grid-column: span 4; }
.embed-form__field--span-5 { grid-column: span 5; }
.embed-form__field--span-6 { grid-column: span 6; }
.embed-form__field--span-7 { grid-column: span 7; }
.embed-form__field--span-8 { grid-column: span 8; }
.embed-form__field--span-9 { grid-column: span 9; }
.embed-form__field--span-10 { grid-column: span 10; }
.embed-form__field--span-11 { grid-column: span 11; }
.embed-form__field--span-12 { grid-column: span 12; }
.embed-form__field--span-13 { grid-column: span 13; }
.embed-form__field--span-14 { grid-column: span 14; }
.embed-form__field--span-15 { grid-column: span 15; }
.embed-form__field--span-16 { grid-column: span 16; }
.embed-form__field--span-17 { grid-column: span 17; }
.embed-form__field--span-18 { grid-column: span 18; }
.embed-form__field--span-19 { grid-column: span 19; }
.embed-form__field--span-20 { grid-column: span 20; }
.embed-form__field--span-21 { grid-column: span 21; }
.embed-form__field--span-22 { grid-column: span 22; }
.embed-form__field--span-23 { grid-column: span 23; }
.embed-form__field--span-24 { grid-column: span 24; }

.embed-form__field > label:first-child { color: #344054; font-size: 14px; font-weight: 600; }
.embed-form__required { color: #d92d20; }

.embed-form input:not([type="checkbox"]),
.embed-form select {
  box-sizing: border-box;
  width: 100%;
  min-height: 38px;
  padding: 8px 10px;
  border: 1px solid #d0d5dd;
  border-radius: 7px;
  color: inherit;
  background: #fff;
}

.embed-form select[multiple] { min-height: 96px; }
.embed-form input:disabled,
.embed-form input[readonly],
.embed-form select:disabled { color: #475467; background: #f2f4f7; }
.embed-form__search { min-height: 34px !important; font-size: 13px; }
.embed-form__checkbox { display: flex; align-items: center; gap: 8px; min-height: 38px; }
.embed-form__hint { color: #667085; font-size: 12px; }
.embed-form__field-error { color: #b42318; font-size: 12px; }
.embed-form__submit-error { color: #b42318; }
.embed-form__success { color: #027a48; }

.embed-form__error {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
  padding: 12px;
  border-radius: 7px;
  color: #b42318;
  background: #fef3f2;
}

.embed-form__loading {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #475467;
  background: rgb(255 255 255 / 70%);
}

.embed-form__footer,
.embed-form__meta {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  color: #667085;
  font-size: 13px;
}

.embed-form button { padding: 8px 14px; }

@media (max-width: 720px) {
  .embed-form { padding: 16px; }
  .embed-form__field { grid-column: 1 / -1 !important; }
  .embed-form__header { display: block; }
  .embed-form__actor { display: inline-block; margin-top: 8px; }
}
</style>
