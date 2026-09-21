<template>
  <div class="demo-project-form" :style="{ '--demo-accent': config.accentColor || '#409eff' }">
    <div class="form-banner">
      <div>
        <strong>{{ form?.formName || '项目定制表单' }}</strong>
        <p>{{ config.subtitle || '使用统一 modelValue、mode、readonly 与 validate 契约' }}</p>
      </div>
      <el-tag :type="modeTagType">{{ modeText }}</el-tag>
    </div>

    <el-form ref="formRef" :model="localValue" :rules="rules" label-width="110px">
      <el-row :gutter="18">
        <el-col v-if="isVisible('projectName')" :span="12">
          <el-form-item label="项目名称" prop="projectName" :required="isRequired('projectName')">
            <el-input
              v-model="localValue.projectName"
              :disabled="isDisabled('projectName')"
              placeholder="请输入项目名称"
              @input="syncValue"
            />
          </el-form-item>
        </el-col>
        <el-col v-if="isVisible('code')" :span="12">
          <el-form-item label="项目编码" prop="code" :required="isRequired('code')">
            <el-input
              v-model="localValue.code"
              :disabled="isDisabled('code') || mode === 'edit'"
              placeholder="例如 DEMO-001"
              @input="syncValue"
            />
          </el-form-item>
        </el-col>
        <el-col v-if="isVisible('ownerName')" :span="12">
          <el-form-item label="负责人" prop="ownerName" :required="isRequired('ownerName')">
            <el-input
              v-model="localValue.ownerName"
              :disabled="isDisabled('ownerName')"
              placeholder="请输入负责人"
              @input="syncValue"
            />
          </el-form-item>
        </el-col>
        <el-col v-if="isVisible('budget')" :span="12">
          <el-form-item label="项目预算" prop="budget" :required="isRequired('budget')">
            <el-input-number
              v-model="localValue.budget"
              :disabled="isDisabled('budget')"
              :min="0"
              :precision="2"
              :step="1000"
              controls-position="right"
              style="width: 100%"
              @change="syncValue"
            />
          </el-form-item>
        </el-col>
        <el-col v-if="isVisible('riskScore')" :span="24">
          <el-form-item label="风险评分" prop="riskScore" :required="isRequired('riskScore')">
            <el-slider
              v-model="localValue.riskScore"
              :disabled="isDisabled('riskScore')"
              :min="0"
              :max="100"
              show-input
              @change="syncValue"
            />
            <el-alert
              v-if="config.showRiskHint !== false"
              :title="riskHint"
              :type="riskAlertType"
              :closable="false"
              show-icon
              class="risk-hint"
            />
          </el-form-item>
        </el-col>
        <el-col v-if="isVisible('description')" :span="24">
          <el-form-item label="项目说明" prop="description" :required="isRequired('description')">
            <el-input
              v-model="localValue.description"
              :disabled="isDisabled('description')"
              type="textarea"
              :rows="4"
              maxlength="1000"
              show-word-limit
              placeholder="请输入项目目标、范围和验收标准"
              @input="syncValue"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { isFieldReadonlyForMode } from '@/shared/config-runtime'

const props = defineProps({
  form: { type: Object, default: () => ({}) },
  modelValue: { type: Object, default: () => ({}) },
  readonly: Boolean,
  fields: { type: Array, default: () => [] },
  linkageState: { type: Object, default: () => ({}) },
  mode: { type: String, default: 'view' },
  config: { type: Object, default: () => ({}) },
  context: { type: Object, default: () => ({}) },
  entityCode: String,
  entityDefinition: { type: Object, default: () => ({}) },
  entityFields: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue'])
const formRef = ref()
const localValue = reactive(createValue(props.modelValue))

const disabled = computed(() => props.readonly || props.mode === 'view')
const fieldMap = computed(() =>
  Object.fromEntries((props.fields || []).map(field => [field.fieldCode || field.fieldKey, field]))
)
const rules = computed(() => {
  const result = {}
  ;[
    ['projectName', '请输入项目名称', 'blur'],
    ['code', '请输入项目编码', 'blur'],
    ['ownerName', '请输入负责人', 'blur'],
    ['budget', '请输入项目预算', 'change']
  ].forEach(([fieldCode, message, trigger]) => {
    if (isVisible(fieldCode) && isRequired(fieldCode)) {
      result[fieldCode] = [{ required: true, message, trigger }]
    }
  })
  return result
})
const modeText = computed(() => ({
  create: '新增模式',
  edit: '编辑模式',
  approve: '审批模式',
  view: '查看模式'
})[props.mode] || props.mode)
const modeTagType = computed(() => ({
  create: 'success',
  edit: 'primary',
  approve: 'warning',
  view: 'info'
})[props.mode] || 'info')

const riskHint = computed(() => {
  const score = Number(localValue.riskScore || 0)
  if (score >= 70) return '高风险：建议补充专项风险应对措施'
  if (score >= 40) return '中风险：建议明确责任人和跟踪频率'
  return '低风险：按常规节奏跟踪即可'
})
const riskAlertType = computed(() => {
  const score = Number(localValue.riskScore || 0)
  if (score >= 70) return 'error'
  if (score >= 40) return 'warning'
  return 'success'
})

watch(
  () => props.modelValue,
  value => Object.assign(localValue, createValue(value)),
  { deep: true }
)

function createValue(value = {}) {
  return {
    projectName: value.projectName || value.name || '',
    code: value.code || '',
    ownerName: value.ownerName || '',
    budget: value.budget ?? 0,
    riskScore: value.riskScore ?? 20,
    description: value.description || ''
  }
}

function syncValue() {
  emit('update:modelValue', { ...props.modelValue, ...localValue, name: localValue.projectName })
}

function isVisible(fieldCode) {
  if (props.fields.length > 0 && !fieldMap.value[fieldCode]) return false
  return props.linkageState?.visibility?.[fieldCode] !== false
}

function isDisabled(fieldCode) {
  if (disabled.value || props.linkageState?.disabled?.[fieldCode] === true) return true
  const field = fieldMap.value[fieldCode]
  return field ? isFieldReadonlyForMode(field, props.mode, props.readonly) : false
}

function isRequired(fieldCode) {
  const linkageRequired = props.linkageState?.required?.[fieldCode]
  if (linkageRequired !== undefined) return linkageRequired
  const required = fieldMap.value[fieldCode]?.isRequired
  return required === true || required === 1 || required === '1'
}

async function validate() {
  if (disabled.value) return true
  try {
    await formRef.value?.validate()
    syncValue()
    return true
  } catch {
    return false
  }
}

defineExpose({ validate })
</script>

<style scoped>
.demo-project-form {
  --demo-accent: #409eff;
  border: 1px solid color-mix(in srgb, var(--demo-accent) 35%, #dcdfe6);
  border-radius: 10px;
  padding: 18px;
  background: linear-gradient(180deg, color-mix(in srgb, var(--demo-accent) 6%, white), white 150px);
}

.form-banner {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 18px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.form-banner strong {
  font-size: 17px;
}

.form-banner p {
  margin: 5px 0 0;
  color: #909399;
  font-size: 13px;
}

.risk-hint {
  margin-top: 10px;
}
</style>
