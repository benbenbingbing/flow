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
        <el-tab-pane
          v-for="item in eventList"
          :key="item.name"
          :label="item.name"
          :name="item.name"
        >
          <div class="editor-wrapper">
            <div class="editor-label">
              {{ item.label }}
              <el-button
                v-if="!item.builtin"
                type="danger"
                link
                size="small"
                class="del-btn"
                @click="removeEvent(item.name)"
              >
                删除
              </el-button>
            </div>
            <codemirror
              v-model="eventCodes[item.name]"
              :extensions="extensions"
              :style="editorStyle"
            />
          </div>
        </el-tab-pane>

        <!-- 添加自定义事件 -->
        <el-tab-pane name="__add__" disabled>
          <template #label>
            <el-button
              type="primary"
              link
              size="small"
              @click="showAddEvent = true"
            >
              <el-icon><Plus /></el-icon> 添加事件
            </el-button>
          </template>
        </el-tab-pane>
      </el-tabs>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>

  <!-- 添加自定义事件弹窗 -->
  <el-dialog
    v-model="showAddEvent"
    title="添加自定义事件"
    width="400px"
    append-to-body
    :close-on-click-modal="false"
  >
    <el-form :model="addForm" label-width="100px">
      <el-form-item label="事件名称" required>
        <el-input
          v-model="addForm.name"
          placeholder="如：onSelect、onDoubleClick"
          @keyup.enter="confirmAddEvent"
        />
        <div class="form-tip">建议以 on 开头，如 onSelect、onDoubleClick</div>
      </el-form-item>
      <el-form-item label="描述">
        <el-input
          v-model="addForm.label"
          placeholder="可选，如：选中时触发"
          @keyup.enter="confirmAddEvent"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showAddEvent = false">取消</el-button>
      <el-button type="primary" @click="confirmAddEvent">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { Codemirror } from 'vue-codemirror'
import { javascript } from '@codemirror/lang-javascript'
import { oneDark } from '@codemirror/theme-one-dark'

const props = defineProps({
  modelValue: {
    type: Object,
    default: () => ({})
  },
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'update:visible', 'save'])

const builtinEvents = [
  { name: 'onChange', label: '值变化时触发', builtin: true },
  { name: 'onBlur', label: '失焦时触发', builtin: true },
  { name: 'onFocus', label: '聚焦时触发', builtin: true }
]

const activeTab = ref('onChange')
const eventList = ref([...builtinEvents])
const eventCodes = ref({})
const showAddEvent = ref(false)
const addForm = ref({ name: '', label: '' })

const extensions = [javascript(), oneDark]
const editorStyle = { height: '300px', fontSize: '14px' }

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

watch(() => props.modelValue, (val) => {
  const codes = {}
  const list = [...builtinEvents]

  // 内置事件
  builtinEvents.forEach(e => {
    codes[e.name] = val?.[e.name] || ''
  })

  // 自定义事件
  if (val) {
    Object.keys(val).forEach(key => {
      if (!builtinEvents.find(e => e.name === key)) {
        codes[key] = val[key]
        if (!list.find(e => e.name === key)) {
          list.push({ name: key, label: key, builtin: false })
        }
      }
    })
  }

  // 清理已删除的自定义事件
  eventList.value = list.filter(e => {
    return e.builtin || (val && val[e.name] !== undefined)
  })

  eventCodes.value = codes

  // 确保 activeTab 有效
  if (!eventList.value.find(e => e.name === activeTab.value)) {
    activeTab.value = eventList.value[0]?.name || 'onChange'
  }
}, { immediate: true, deep: true })

function confirmAddEvent() {
  const name = addForm.value.name.trim()
  if (!name) {
    ElMessage.warning('请输入事件名称')
    return
  }
  if (eventList.value.find(e => e.name === name)) {
    ElMessage.warning('该事件已存在')
    return
  }

  eventList.value.push({
    name,
    label: addForm.value.label.trim() || name,
    builtin: false
  })
  eventCodes.value[name] = ''
  activeTab.value = name
  addForm.value = { name: '', label: '' }
  showAddEvent.value = false
}

function removeEvent(name) {
  const idx = eventList.value.findIndex(e => e.name === name)
  if (idx > -1) {
    eventList.value.splice(idx, 1)
    delete eventCodes.value[name]
    if (activeTab.value === name && eventList.value.length > 0) {
      activeTab.value = eventList.value[0].name
    }
  }
}

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
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.del-btn {
  margin-left: auto;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
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
