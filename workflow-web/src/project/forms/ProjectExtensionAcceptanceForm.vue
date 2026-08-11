<template>
  <div class="project-acceptance-form">
    <header class="form-heading">
      <div>
        <h3>{{ config.title || form?.formName || '项目扩展验收单' }}</h3>
        <p>整表单扩展 · {{ modeLabel }} · {{ entityCode || '未绑定实体' }}</p>
      </div>
      <el-tag :type="modeTagType">{{ modeLabel }}</el-tag>
    </header>

    <el-alert
      v-if="config.showRuntimeTrace !== false"
      :title="localValue.provider_trace || '等待统一数据源执行'"
      type="info"
      :closable="false"
      show-icon
      class="runtime-trace"
    />

    <el-form
      ref="formRef"
      :model="localValue"
      :rules="rules"
      label-width="110px"
    >
      <el-row :gutter="18">
        <el-col :xs="24" :md="12">
          <el-form-item label="验收单名称" prop="name">
            <el-input
              v-model="localValue.name"
              :disabled="disabled"
              placeholder="请输入验收单名称"
              @input="syncValue"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="验收场景" prop="acceptance_scene">
            <el-select
              v-model="localValue.acceptance_scene"
              :disabled="disabled"
              style="width: 100%"
              @change="syncValue"
            >
              <el-option label="全扩展链路" value="FULL_EXTENSION" />
              <el-option label="表单扩展" value="FORM_EXTENSION" />
              <el-option label="列表扩展" value="LIST_EXTENSION" />
              <el-option label="流程扩展" value="PROCESS_EXTENSION" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="验收负责人" prop="owner_name">
            <el-input
              v-model="localValue.owner_name"
              :disabled="disabled"
              placeholder="请输入负责人"
              @input="syncValue"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="计划验收日" prop="planned_date">
            <el-date-picker
              v-model="localValue.planned_date"
              :disabled="disabled"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%"
              @change="syncValue"
            />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="验收评分" prop="acceptance_score">
            <el-slider
              v-model="localValue.acceptance_score"
              :disabled="disabled"
              :min="0"
              :max="100"
              show-input
              @change="syncValue"
            />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="验收说明" prop="description">
            <el-input
              v-model="localValue.description"
              :disabled="disabled"
              type="textarea"
              :rows="4"
              maxlength="1000"
              show-word-limit
              placeholder="记录本次需要验收的扩展点"
              @input="syncValue"
            />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="扩展结果">
            <el-input
              v-model="localValue.extension_result"
              :disabled="disabled"
              type="textarea"
              :rows="3"
              placeholder="流程动作和节点扩展的可见结果"
              @input="syncValue"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <footer v-if="!disabled" class="form-runtime-actions">
      <el-button
        :loading="dataSourceLoading"
        @click="executeFormDataSource"
      >
        <el-icon><Refresh /></el-icon>
        执行表单统一数据源
      </el-button>
      <span>结果将写入“数据源轨迹”字段，并打印后端 Provider 日志。</span>
    </footer>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

const props = defineProps({
  form: { type: Object, default: () => ({}) },
  modelValue: { type: Object, default: () => ({}) },
  readonly: Boolean,
  fields: { type: Array, default: () => [] },
  linkageState: { type: Object, default: () => ({}) },
  entityCode: String,
  entityDefinition: { type: Object, default: () => ({}) },
  entityFields: { type: Array, default: () => [] },
  mode: { type: String, default: 'view' },
  config: { type: Object, default: () => ({}) },
  context: { type: Object, default: () => ({}) },
  dataSourceRuntime: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue'])
const formRef = ref()
const dataSourceLoading = ref(false)
const localValue = reactive(createValue(props.modelValue))

const disabled = computed(() => props.readonly || props.mode === 'view')
const modeLabel = computed(() => ({
  create: '新增',
  edit: '编辑',
  approve: '审批',
  view: '查看'
})[props.mode] || props.mode)
const modeTagType = computed(() => ({
  create: 'success',
  edit: 'primary',
  approve: 'warning',
  view: 'info'
})[props.mode] || 'info')
const rules = {
  name: [{ required: true, message: '请输入验收单名称', trigger: 'blur' }],
  acceptance_scene: [{ required: true, message: '请选择验收场景', trigger: 'change' }],
  owner_name: [{ required: true, message: '请输入验收负责人', trigger: 'blur' }],
  acceptance_score: [{ required: true, message: '请填写验收评分', trigger: 'change' }]
}

watch(
  () => props.modelValue,
  value => Object.assign(localValue, createValue(value)),
  { deep: true }
)

function createValue(value = {}) {
  return {
    ...value,
    name: value.name || '',
    acceptance_scene: value.acceptance_scene || 'FULL_EXTENSION',
    owner_name: value.owner_name || '',
    planned_date: value.planned_date || '',
    acceptance_score: Number(value.acceptance_score ?? 65),
    description: value.description || '',
    provider_trace: value.provider_trace || '',
    extension_result: value.extension_result || ''
  }
}

function syncValue() {
  emit('update:modelValue', { ...localValue })
}

async function executeFormDataSource() {
  if (!props.dataSourceRuntime?.executeOwnerUsage) {
    ElMessage.warning('当前表单没有可用的统一数据源运行时')
    return
  }
  dataSourceLoading.value = true
  try {
    const results = await props.dataSourceRuntime.executeOwnerUsage(
      props.form,
      'AFTER_LOAD',
      {
        record: localValue,
        input: {
          source: 'ProjectExtensionAcceptanceForm',
          requestedAt: new Date().toISOString()
        }
      }
    )
    results.forEach(result => {
      const patch = result?.data ?? result
      if (patch && typeof patch === 'object' && !Array.isArray(patch)) {
        Object.assign(localValue, patch)
      }
    })
    syncValue()
    console.info('[ProjectExtensionAcceptance] 整表单统一数据源执行完成', {
      formId: props.form?.id,
      resultCount: results.length,
      resultKeys: results.flatMap(result =>
        Object.keys(result?.data ?? result ?? {})
      )
    })
    ElMessage.success(`表单统一数据源已执行，共返回 ${results.length} 个结果`)
  } catch (error) {
    console.error('[ProjectExtensionAcceptance] 整表单统一数据源执行失败', error)
    ElMessage.error(error.message || '表单统一数据源执行失败')
  } finally {
    dataSourceLoading.value = false
  }
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
.project-acceptance-form {
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 18px;
  background: var(--el-bg-color);
}

.form-heading,
.form-runtime-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.form-heading {
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.form-heading h3 {
  margin: 0;
  font-size: 18px;
}

.form-heading p {
  margin: 5px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.runtime-trace {
  margin-bottom: 18px;
}

.form-runtime-actions {
  justify-content: flex-start;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

@media (max-width: 720px) {
  .form-runtime-actions {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
