<template>
  <SettingsSection
    title="下一节点审批人"
    description="当前节点流转到本节点时，是否展示本节点审批人并允许发起选择"
    :default-expanded="localConfig.visible"
  >
    <template #summary>
      <el-tag :type="localConfig.visible ? 'success' : 'info'" size="small">
        {{ localConfig.visible ? (localConfig.editable ? '展示并可修改' : '仅展示') : '不展示' }}
      </el-tag>
    </template>

    <el-form-item label="审批时展示">
      <el-switch
        v-model="localConfig.visible"
        @change="onVisibleChange"
      />
      <div class="form-tip">标签使用本节点名称，审批人由流程条件命中的目标节点动态解析</div>
    </el-form-item>

    <template v-if="localConfig.visible">
      <el-form-item label="允许修改">
        <el-switch v-model="localConfig.editable" />
        <div class="form-tip">关闭后仅展示系统解析的审批人，不允许当前审批人调整</div>
      </el-form-item>

      <el-form-item label="人员来源" required>
        <el-radio-group
          :model-value="localConfig.source.type"
          @update:model-value="changeSourceType"
        >
          <el-radio-button value="SCOPE">人员范围</el-radio-button>
          <el-radio-button value="RESOLVER">人员接口</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <template v-if="localConfig.source.type === 'SCOPE'">
        <PersonScopeRuleEditor
          v-model="localConfig.source.rules"
          :role-options="roleOptions"
          :group-options="groupOptions"
          :organization-options="organizationOptions"
        />
      </template>

      <template v-else-if="localConfig.source.type === 'RESOLVER'">
        <el-form-item label="人员接口" required>
          <ExtensionCapabilityPicker
            v-model="localConfig.source.resolverCode"
            capability-type="PERSON_RESOLVER"
            placeholder="输入名称或编码搜索下一审批人人员接口"
            :context-params="resolverContext"
            :current-option="resolverCurrentOption"
          />
          <div class="form-tip">候选人员由平台受控人员解析器返回，不支持填写任意 URL</div>
        </el-form-item>

        <el-form-item label="extraParams">
          <el-input
            v-model="extraParamsText"
            type="textarea"
            :rows="4"
            placeholder='JSON 对象，例如 {"level": 2}'
            @input="parseExtraParams"
          />
          <div v-if="extraParamsError" class="next-approver-config__error">
            {{ extraParamsError }}
          </div>
          <div v-else class="form-tip">仅填写该人员接口声明的扩展参数</div>
        </el-form-item>
      </template>
    </template>
  </SettingsSection>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import ExtensionCapabilityPicker from '@/components/ExtensionCapabilityPicker.vue'
import PersonScopeRuleEditor from '@/components/PersonScopeRuleEditor.vue'
import SettingsSection from '@/components/SettingsSection.vue'
import {
  createNextApproverSelectionConfig,
  validateNextApproverSelectionConfig
} from '@/shared/next-approver'

const props = defineProps({
  modelValue: {
    type: Object,
    default: () => ({})
  },
  roleOptions: {
    type: Array,
    default: () => []
  },
  groupOptions: {
    type: Array,
    default: () => []
  },
  organizationOptions: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue'])
const localConfig = ref(createNextApproverSelectionConfig(props.modelValue))
const extraParamsText = ref(JSON.stringify(
  localConfig.value.source.type === 'RESOLVER'
    ? localConfig.value.source.extraParams || {}
    : {},
  null,
  2
))
const extraParamsError = ref('')
const resolverContext = { usage: 'CANDIDATE' }
let syncingFromProps = false

const resolverCurrentOption = computed(() => {
  const code = localConfig.value.source?.resolverCode
  return code ? { key: code, displayName: code } : null
})

function stable(value) {
  return JSON.stringify(value)
}

function resetFromProps(value) {
  const normalized = createNextApproverSelectionConfig(value)
  if (stable(normalized) === stable(localConfig.value)) return
  syncingFromProps = true
  localConfig.value = normalized
  extraParamsText.value = JSON.stringify(
    normalized.source.type === 'RESOLVER'
      ? normalized.source.extraParams || {}
      : {},
    null,
    2
  )
  extraParamsError.value = ''
  syncingFromProps = false
}

watch(
  () => props.modelValue,
  resetFromProps,
  { deep: true }
)

watch(
  localConfig,
  value => {
    if (syncingFromProps) return
    const normalized = createNextApproverSelectionConfig(value)
    if (stable(normalized) !== stable(createNextApproverSelectionConfig(props.modelValue))) {
      emit('update:modelValue', normalized)
    }
  },
  { deep: true }
)

function onVisibleChange(visible) {
  if (!visible) localConfig.value.editable = false
}

function changeSourceType(type) {
  if (type === localConfig.value.source.type) return
  localConfig.value.source = type === 'RESOLVER'
    ? { type: 'RESOLVER', resolverCode: '', extraParams: {} }
    : { type: 'SCOPE', rules: [] }
  extraParamsText.value = '{}'
  extraParamsError.value = ''
}

function parseExtraParams() {
  try {
    const parsed = extraParamsText.value?.trim()
      ? JSON.parse(extraParamsText.value)
      : {}
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error('extraParams 必须是 JSON 对象')
    }
    extraParamsError.value = ''
    if (localConfig.value.source.type === 'RESOLVER') {
      localConfig.value.source.extraParams = parsed
    }
  } catch (error) {
    extraParamsError.value = error?.message || 'extraParams JSON 格式错误'
  }
}

function validate() {
  if (
    localConfig.value.visible
    && localConfig.value.source.type === 'RESOLVER'
    && extraParamsError.value
  ) {
    return { valid: false, message: `下一审批人人员接口：${extraParamsError.value}` }
  }
  return validateNextApproverSelectionConfig(localConfig.value)
}

defineExpose({ validate })
</script>

<style scoped>
.next-approver-config__error {
  color: var(--el-color-danger);
  font-size: 12px;
  line-height: 1.5;
}
</style>
