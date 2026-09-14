<template>
  <el-dialog
    v-model="visible"
    title="调试扩展接口"
    width="760px"
    append-to-body
    destroy-on-close
  >
    <el-form :model="editor" label-width="120px">
      <!-- 帮助按钮不嵌入原生 label，避免无障碍名称与交互冲突。 -->
      <el-form-item for="">
        <template #label>
          <ConfigHelpLabel
            label="扩展接口"
            help-key="extensionInterface.debugInterface"
          />
        </template>
        <el-input
          :model-value="interfaceItem?.displayName"
          aria-label="扩展接口"
          disabled
        />
      </el-form-item>
      <el-form-item for="" required>
        <template #label>
          <ConfigHelpLabel
            label="业务上下文"
            help-key="extensionInterface.debugBusinessContext"
          />
        </template>
        <el-input
          :model-value="contextTypeLabel"
          aria-label="业务上下文"
          disabled
        />
      </el-form-item>
      <el-form-item for="" required>
        <template #label>
          <ConfigHelpLabel
            label="配置对象"
            help-key="extensionInterface.debugConfigObject"
          />
        </template>
        <el-select v-model="editor.configId" aria-label="配置对象" filterable>
          <el-option
            v-for="option in originOptions"
            :key="option.id"
            :label="option.label"
            :value="option.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item for="">
        <template #label>
          <ConfigHelpLabel
            label="调用用途"
            help-key="extensionInterface.debugUsage"
          />
        </template>
        <el-select v-model="editor.usage" aria-label="调用用途" filterable>
          <el-option
            v-for="usage in availableUsageOptions"
            :key="usage.value"
            :label="usage.label"
            :value="usage.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item for="">
        <template #label>
          <ConfigHelpLabel
            label="输入参数"
            help-key="extensionInterface.debugInput"
          />
        </template>
        <el-input
          v-model="editor.inputText"
          type="textarea"
          :rows="8"
          aria-label="输入参数"
        />
      </el-form-item>
      <el-form-item v-if="resultText" for="">
        <template #label>
          <ConfigHelpLabel
            label="执行结果"
            help-key="extensionInterface.debugResult"
          />
        </template>
        <pre class="test-result">{{ resultText }}</pre>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :loading="testing" @click="run">
        <el-icon><VideoPlay /></el-icon>
        执行调试
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { uiExtensionApi } from '@/api/uiConfig'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import {
  defaultInterfaceDebugUsage,
  interfaceUsageOptions,
  isInterfaceUsageCompatible,
  normalizeInterfaceExtension,
  parseInterfaceEditorJson
} from './interfaceExtensionModel'

const props = defineProps({
  forms: { type: Array, default: () => [] },
  lists: { type: Array, default: () => [] },
  entityId: { type: String, default: '' },
  entityCode: { type: String, default: '' }
})
const visible = ref(false)
const testing = ref(false)
const interfaceItem = ref(null)
const resultText = ref('')
const editor = reactive({
  configType: 'FORM',
  configId: '',
  usage: 'DETAIL_LOAD',
  inputText: '{}'
})

const originOptions = computed(() =>
  editor.configType === 'FORM'
    ? props.forms.map(item => ({
        id: item.id,
        label: `${item.formName} (${item.formKey})`
      }))
    : editor.configType === 'LIST'
      ? props.lists.map(item => ({
          id: item.id,
          label: `${item.listName} (${item.listKey})`
        }))
      : props.entityId
        ? [{ id: props.entityId, label: props.entityCode || props.entityId }]
        : [])

const contextTypeLabel = computed(() => ({
  FORM: '表单上下文',
  LIST: '列表上下文',
  ENTITY: '实体上下文'
}[editor.configType] || '-') )

const availableUsageOptions = computed(() => interfaceUsageOptions.filter(option =>
  interfaceItem.value
    && isInterfaceUsageCompatible(interfaceItem.value, option.value)
))

function open(value) {
  interfaceItem.value = normalizeInterfaceExtension(value)
  const configType = interfaceItem.value.interfaceContextType
  Object.assign(editor, {
    configType,
    configId: firstOriginId(configType),
    usage: defaultInterfaceDebugUsage(interfaceItem.value),
    inputText: '{}'
  })
  resultText.value = ''
  visible.value = true
}

async function run() {
  if (!editor.configId) {
    ElMessage.warning('请选择用于权限和数据范围校验的业务上下文')
    return
  }
  testing.value = true
  try {
    const result = await uiExtensionApi.preview(interfaceItem.value.extensionId, {
      usage: editor.usage,
      configType: editor.configType,
      configId: editor.configId,
      input: parseInterfaceEditorJson(editor.inputText, '调试输入')
    })
    resultText.value = JSON.stringify(result, null, 2)
  } catch (error) {
    resultText.value = JSON.stringify({ error: error?.message || '执行失败' }, null, 2)
  } finally {
    testing.value = false
  }
}

function firstOriginId(configType) {
  if (configType === 'FORM') return props.forms[0]?.id || ''
  if (configType === 'LIST') return props.lists[0]?.id || ''
  if (configType === 'ENTITY') return props.entityId || ''
  return ''
}

defineExpose({ open })
</script>

<style scoped>
.test-result {
  width: 100%;
  max-height: 320px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  box-sizing: border-box;
}
</style>
