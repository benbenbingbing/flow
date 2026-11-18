<template>
  <div class="entity-validation-rule-editor">
    <div class="validation-rule-heading">
      <strong><ConfigHelpLabel label="验证规则" :content="validationRuleHelp" /></strong>
      <el-tag size="small" :type="configuredRuleCount ? 'success' : 'info'" effect="plain">
        {{ validationRuleStatus }}
      </el-tag>
    </div>

    <el-alert
      v-if="!validationState.valid"
      type="error"
      :closable="false"
      show-icon
      title="历史验证规则格式异常"
    >
      <div class="invalid-rule-content">
        <span>{{ validationState.error }}</span>
        <el-button link type="danger" @click="clearRules">清空异常配置</el-button>
      </div>
    </el-alert>

    <template v-else-if="currentGroup.key === 'TEXT'">
      <el-form-item label="最小长度">
        <el-input-number
          :model-value="validationConfig.minLength"
          :min="0"
          :max="20000"
          controls-position="right"
          placeholder="不限制"
          style="width: 100%"
          @update:model-value="updateRule('minLength', $event)"
        />
      </el-form-item>
      <el-form-item label="最大长度">
        <el-input-number
          :model-value="validationConfig.maxLength"
          :min="0"
          :max="20000"
          controls-position="right"
          placeholder="不限制"
          style="width: 100%"
          @update:model-value="updateRule('maxLength', $event)"
        />
      </el-form-item>
      <el-form-item label="格式">
        <el-select
          :model-value="validationConfig.format || ''"
          clearable
          placeholder="不限制"
          style="width: 100%"
          @update:model-value="updateRule('format', $event)"
        >
          <el-option label="邮箱" value="EMAIL" />
          <el-option label="中国大陆手机号" value="PHONE" />
          <el-option label="HTTP(S) 网址" value="URL" />
        </el-select>
      </el-form-item>
    </template>

    <template v-else-if="currentGroup.key === 'NUMBER'">
      <el-form-item label="最小值">
        <el-input-number
          :model-value="validationConfig.min"
          controls-position="right"
          placeholder="不限制"
          style="width: 100%"
          @update:model-value="updateRule('min', $event)"
        />
      </el-form-item>
      <el-form-item label="最大值">
        <el-input-number
          :model-value="validationConfig.max"
          controls-position="right"
          placeholder="不限制"
          style="width: 100%"
          @update:model-value="updateRule('max', $event)"
        />
      </el-form-item>
    </template>

  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import {
  getEntityValidationRuleGroup,
  validateEntityValidationRules
} from '@/shared/entity-validation-rules'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  fieldType: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue'])

const currentGroup = computed(() => getEntityValidationRuleGroup(props.fieldType))
const validationState = computed(() =>
  validateEntityValidationRules(props.fieldType, props.modelValue)
)
const validationConfig = computed(() =>
  validationState.value.valid ? validationState.value.config : {}
)
const configuredRuleCount = computed(() =>
  ['minLength', 'maxLength', 'min', 'max', 'format'].filter(key => {
    const value = validationConfig.value[key]
    return value !== undefined && value !== null && value !== ''
  }).length
)
const validationRuleHelp = computed(() =>
  currentGroup.value.description || '仅校验非空值。是否允许为空请使用“是否必填”。'
)
const validationRuleStatus = computed(() => {
  if (!validationState.value.valid) return '配置异常'
  if (!currentGroup.value.rules.length) return '不适用'
  return configuredRuleCount.value ? `${configuredRuleCount.value} 项规则` : '未配置'
})

const updateRule = (key, value) => {
  if (!validationState.value.valid) return

  const nextConfig = {
    ...validationConfig.value
  }
  if (value === undefined || value === null || value === '') {
    delete nextConfig[key]
  } else {
    nextConfig[key] = value
  }

  const result = validateEntityValidationRules(
    props.fieldType,
    JSON.stringify(nextConfig)
  )
  if (!result.valid) {
    ElMessage.warning(result.error)
    return
  }
  emit('update:modelValue', result.normalized)
}

const clearRules = () => {
  emit('update:modelValue', '')
}
</script>

<style scoped>
.entity-validation-rule-editor {
  padding-top: 4px;
}

.validation-rule-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.validation-rule-heading strong {
  color: #303133;
  font-size: 13px;
}

.invalid-rule-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
</style>
