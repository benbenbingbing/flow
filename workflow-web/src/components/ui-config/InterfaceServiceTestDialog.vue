<template>
  <el-dialog
    v-model="visible"
    title="调试接口操作"
    width="760px"
    append-to-body
    destroy-on-close
  >
    <el-form :model="editor" label-width="120px">
      <!-- 问号按钮不能嵌入原生 label；空 for 让 FormItem 以 ARIA group 渲染，控件再各自提供名称。 -->
      <el-form-item for="">
        <template #label>
          <ConfigHelpLabel
            label="接口服务"
            help-key="interfaceService.debugService"
          />
        </template>
        <el-input
          :model-value="service?.sourceName"
          aria-label="接口服务"
          disabled
        />
      </el-form-item>
      <el-form-item for="">
        <template #label>
          <ConfigHelpLabel
            label="操作"
            help-key="interfaceService.debugOperation"
          />
        </template>
        <el-select v-model="editor.operationCode" aria-label="操作">
          <el-option
            v-for="operation in serviceOperations(service || {})"
            :key="operation.code"
            :label="`${operation.name} (${operation.code})`"
            :value="operation.code"
          />
        </el-select>
      </el-form-item>
      <el-form-item for="" required>
        <template #label>
          <ConfigHelpLabel
            label="业务上下文"
            help-key="interfaceService.debugBusinessContext"
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
            help-key="interfaceService.debugConfigObject"
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
            help-key="interfaceService.debugUsage"
          />
        </template>
        <el-select v-model="editor.usage" aria-label="调用用途" filterable>
          <el-option
            v-for="usage in interfaceServiceUsageOptions"
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
            help-key="interfaceService.debugInput"
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
            help-key="interfaceService.debugResult"
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
import { computed, reactive, ref, watch } from 'vue'
import { VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { uiDataSourceApi } from '@/api/uiConfig'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import {
  defaultInterfaceServiceDebugUsage,
  interfaceServiceUsageOptions,
  parseEditorJson,
  serviceOperations
} from './interfaceServiceModel'

const props = defineProps({
  forms: { type: Array, default: () => [] },
  lists: { type: Array, default: () => [] },
  entityId: { type: String, default: '' },
  entityCode: { type: String, default: '' }
})

const visible = ref(false)
const testing = ref(false)
const service = ref(null)
const resultText = ref('')
const editor = reactive({
  operationCode: '',
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
        ? [{
            id: props.entityId,
            label: props.entityCode || props.entityId
          }]
        : [])

const selectedOperation = computed(() =>
  serviceOperations(service.value || {}).find(operation =>
    operation.code === editor.operationCode))

const contextTypeLabel = computed(() => ({
  FORM: '表单上下文',
  LIST: '列表上下文',
  ENTITY: '实体上下文'
}[editor.configType] || '-'))

function open(targetService) {
  service.value = targetService
  const operations = serviceOperations(targetService)
  const firstOperation = operations[0]
  const configType = firstOperation?.contextType || ''
  Object.assign(editor, {
    operationCode: firstOperation?.code || '',
    configType,
    configId: firstOriginId(configType),
    usage: defaultInterfaceServiceDebugUsage(firstOperation),
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
    const input = parseEditorJson(editor.inputText, '调试输入')
    const result = await uiDataSourceApi.previewOperation(
      service.value.id,
      editor.operationCode,
      {
        usage: editor.usage,
        configType: editor.configType,
        configId: editor.configId,
        input
      }
    )
    resultText.value = JSON.stringify(result, null, 2)
  } catch (error) {
    resultText.value = JSON.stringify({
      error: error.message || '执行失败'
    }, null, 2)
  } finally {
    testing.value = false
  }
}

watch(() => editor.operationCode, () => {
  editor.configType = selectedOperation.value?.contextType || ''
  editor.configId = firstOriginId(editor.configType)
  editor.usage = defaultInterfaceServiceDebugUsage(selectedOperation.value)
})

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
