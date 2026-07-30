<template>
  <el-dialog
    v-model="visible"
    title="调试接口操作"
    width="760px"
    append-to-body
    destroy-on-close
  >
    <el-form :model="editor" label-width="92px">
      <el-form-item label="接口服务">
        <el-input :model-value="service?.sourceName" disabled />
      </el-form-item>
      <el-form-item label="操作">
        <el-select v-model="editor.operationCode">
          <el-option
            v-for="operation in serviceOperations(service || {})"
            :key="operation.code"
            :label="`${operation.name} (${operation.code})`"
            :value="operation.code"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="业务上下文" required>
        <el-radio-group v-model="editor.configType">
          <el-radio-button value="FORM">表单</el-radio-button>
          <el-radio-button value="LIST">列表</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="配置对象" required>
        <el-select v-model="editor.configId" filterable>
          <el-option
            v-for="option in originOptions"
            :key="option.id"
            :label="option.label"
            :value="option.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="事件用途">
        <el-select v-model="editor.usage" filterable>
          <el-option
            v-for="event in eventCodes"
            :key="event"
            :label="event"
            :value="event"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="输入参数">
        <el-input v-model="editor.inputText" type="textarea" :rows="8" />
      </el-form-item>
      <el-form-item v-if="resultText" label="执行结果">
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
import {
  parseEditorJson,
  serviceOperations
} from './interfaceServiceModel'

const props = defineProps({
  forms: { type: Array, default: () => [] },
  lists: { type: Array, default: () => [] },
  entityCode: { type: String, default: '' },
  eventCodes: { type: Array, default: () => [] }
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
    : props.lists.map(item => ({
        id: item.id,
        label: `${item.listName} (${item.listKey})`
      })))

function open(targetService) {
  service.value = targetService
  const operations = serviceOperations(targetService)
  Object.assign(editor, {
    operationCode: operations[0]?.code || 'default',
    configType: props.forms.length ? 'FORM' : 'LIST',
    configId: props.forms[0]?.id || props.lists[0]?.id || '',
    usage: operations[0]?.kind === 'WRITE' ? 'DATA_UPDATE' : 'DETAIL_LOAD',
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
        entityCode: props.entityCode,
        input,
        context: editor.configType === 'FORM'
          ? { formId: editor.configId }
          : { listId: editor.configId }
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

watch(() => editor.configType, () => {
  editor.configId = originOptions.value[0]?.id || ''
})

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
