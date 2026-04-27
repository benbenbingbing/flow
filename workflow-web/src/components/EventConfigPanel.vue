<template>
  <el-dialog
    v-model="dialogVisible"
    title="字段事件配置"
    width="800px"
    destroy-on-close
    :close-on-click-modal="false"
  >
    <div class="event-config-panel">
      <el-alert type="info" :closable="false" class="event-tip">
        在代码中可使用 <code>value</code>（当前值）和 <code>field</code>（字段配置）两个变量
      </el-alert>

      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane label="onChange" name="onChange">
          <div class="editor-wrapper">
            <div class="editor-label">值变化时触发</div>
            <codemirror
              v-model="eventCodes.onChange"
              :extensions="extensions"
              :style="editorStyle"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="onBlur" name="onBlur">
          <div class="editor-wrapper">
            <div class="editor-label">失焦时触发</div>
            <codemirror
              v-model="eventCodes.onBlur"
              :extensions="extensions"
              :style="editorStyle"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="onFocus" name="onFocus">
          <div class="editor-wrapper">
            <div class="editor-label">聚焦时触发</div>
            <codemirror
              v-model="eventCodes.onFocus"
              :extensions="extensions"
              :style="editorStyle"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Codemirror } from 'vue-codemirror'
import { javascript } from '@codemirror/lang-javascript'
import { oneDark } from '@codemirror/theme-one-dark'

const props = defineProps({
  modelValue: {
    type: Object,
    default: () => ({ onChange: '', onBlur: '', onFocus: '' })
  },
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'update:visible', 'save'])

const activeTab = ref('onChange')
const eventCodes = ref({
  onChange: '',
  onBlur: '',
  onFocus: ''
})

const extensions = [javascript(), oneDark]
const editorStyle = { height: '300px', fontSize: '14px' }

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

watch(() => props.modelValue, (val) => {
  eventCodes.value = {
    onChange: val?.onChange || '',
    onBlur: val?.onBlur || '',
    onFocus: val?.onFocus || ''
  }
}, { immediate: true, deep: true })

function handleSave() {
  emit('save', { ...eventCodes.value })
  dialogVisible.value = false
}
</script>

<style scoped>
.event-config-panel {
  width: 100%;
}

.event-tip {
  margin-bottom: 12px;
}

.event-tip code {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  color: #409eff;
  font-family: monospace;
}

.editor-wrapper {
  padding: 8px 0;
}

.editor-label {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}

:deep(.cm-editor) {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

:deep(.cm-focused) {
  outline: none;
  border-color: #409eff;
}
</style>
