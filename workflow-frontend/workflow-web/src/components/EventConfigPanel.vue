<template>
  <el-dialog v-model="dialogVisible" title="前端脚本事件" width="880px" destroy-on-close :close-on-click-modal="false">
    <el-alert type="info" :closable="false" class="event-tip">
      <div>从组件声明的事件中按需配置，选择处理函数或直接输入代码片段。</div>
      <div>代码可使用 <code>value</code>、<code>field</code>、<code>selection</code>、<code>event.name</code> 和 <code>event.data</code>；
        用 <code>setValue(value)</code> 修改当前值，<code>getFieldValue('字段编码')</code> 读值，<code>setFieldValue('字段编码', value)</code> 回填。</div>
      <div>实体字段的 value 是 ID，selection 是记录。支持 await，异步等待最多 5 秒；返回值不会自动回填。嵌入页面不支持此功能。</div>
    </el-alert>
    <el-alert v-if="legacyComponent" type="warning" :closable="false" class="event-tip"
      title="此组件尚未声明事件，当前展示兼容事件；组件开发者可通过 scriptEvents 声明实际支持的事件。" />
    <el-empty v-if="!eventList.length" description="当前组件未提供前端事件" />
    <el-tabs v-else v-model="activeTab" type="border-card">
      <el-tab-pane v-for="item in eventList" :key="item.name" :name="item.name"
        :label="`${item.label} · ${item.name}${configured(item.name) ? ' ●' : ''}`">
        <div class="editor-label">
          <span>{{ item.description }}</span>
          <el-button type="danger" link @click="clearEvent(item.name)">清空配置</el-button>
        </div>
        <el-alert v-if="item.unsupported" type="warning" :closable="false" class="event-tip"
          title="当前组件未声明此事件，已有配置保留供核对；发布前请移除或换回支持此事件的组件。" />
        <template v-if="!item.unsupported && drafts[item.name]">
          <el-alert v-if="drafts[item.name].invalid" type="error" :closable="false" title="旧配置格式无效，请清空后重新配置。" />
          <pre v-if="drafts[item.name].invalid" class="legacy-config">{{ legacyPreview(item.name) }}</pre>
          <el-radio-group v-else v-model="drafts[item.name].mode" class="mode-switch" :aria-label="`${item.name}处理方式`">
            <el-radio-button value="body">代码片段</el-radio-button>
            <el-radio-button value="function">处理函数</el-radio-button>
          </el-radio-group>
          <div v-if="item.parameters?.length" class="event-parameters">
            <span v-for="parameter in item.parameters" :key="parameter.name">
              <code>event.data.{{ parameter.name }}</code>（{{ parameter.type }}{{ parameter.required ? '，必填' : '' }}）：{{ parameter.description }}
            </span>
          </div>
          <template v-if="drafts[item.name].mode === 'function'">
            <el-select v-model="drafts[item.name].handlerKey" filterable clearable placeholder="选择已注册的处理函数" class="handler-select">
              <el-option v-if="missingHandler(item.name)" :value="drafts[item.name].handlerKey"
                :label="`${drafts[item.name].handlerKey}（未安装或不适用）`" disabled />
              <el-option v-for="handler in handlerOptions(item.name)" :key="handler.key" :value="handler.key"
                :label="`${handler.label}（${handler.name}@${handler.version}）`" />
            </el-select>
            <p class="handler-description">{{ selectedHandler(item.name)?.description || '处理函数由组件开发者注册，以固定名称和版本保存到当前字段。' }}</p>
            <el-alert v-if="missingHandler(item.name)" title="所选处理函数未安装或不适用于当前事件，请重新选择。" type="error" :closable="false" />
          </template>
          <codemirror v-else-if="!drafts[item.name].invalid" v-model="drafts[item.name].code" :extensions="extensions" :style="editorStyle" />
        </template>
        <pre v-else class="legacy-config">{{ legacyPreview(item.name) }}</pre>
      </el-tab-pane>
    </el-tabs>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Codemirror } from 'vue-codemirror'
import { javascript } from '@codemirror/lang-javascript'
import { oneDark } from '@codemirror/theme-one-dark'
import { hasFieldScript, normalizeFieldScript, validateFieldScript } from '@flow/workflow-core/browser/field-event-scripts'
import { getFieldEventHandlerOptions } from '@flow/workflow-core/extensions/core/registries/fieldEventHandlerRegistry'
import { getFieldScriptEventCatalog } from '@/shared/field-script-configuration'

const props = defineProps({ field: { type: Object, default: null }, modelValue: { type: Object, default: () => ({}) }, visible: Boolean })
const emit = defineEmits(['update:modelValue', 'update:visible', 'save'])
const dialogVisible = computed({ get: () => props.visible, set: value => emit('update:visible', value) })
const catalog = computed(() => getFieldScriptEventCatalog(props.field))
const legacyComponent = computed(() => catalog.value.legacy)
const activeTab = ref('onChange')
const drafts = ref({})
const preserved = ref({})
const extensions = [javascript(), oneDark]
const editorStyle = { height: '300px', fontSize: '14px' }
const eventList = computed(() => [
  ...catalog.value.events,
  ...Object.keys(preserved.value).filter(name => !catalog.value.events.some(item => item.name === name))
    .map(name => ({ name, label: '不支持的旧事件', unsupported: true }))
])

// 打开、切换字段或组件时重建本地草稿；取消编辑不会修改字段对象。
watch([() => props.modelValue, () => props.field, () => props.visible], () => {
  preserved.value = JSON.parse(JSON.stringify(props.modelValue || {}))
  drafts.value = Object.fromEntries(catalog.value.events.map(item => {
    const raw = props.modelValue?.[item.name] || ''
    try {
      const binding = normalizeFieldScript(raw)
      return [item.name, { mode: binding.mode, code: binding.code || '', handlerKey: binding.mode === 'function' ? `${binding.handler}@${binding.version}` : '' }]
    } catch {
      // 损坏的旧配置仍展示原文，必须显式清空，不能因重开弹窗而丢失。
      return [item.name, { mode: 'invalid', code: '', handlerKey: '', invalid: raw }]
    }
  }))
  if (!eventList.value.some(item => item.name === activeTab.value)) activeTab.value = eventList.value[0]?.name || ''
}, { immediate: true, deep: true })

function handlerOptions(name) { return getFieldEventHandlerOptions(props.field, name).map(item => ({ ...item, key: `${item.name}@${item.version}` })) }
function selectedHandler(name) { return handlerOptions(name).find(item => item.key === drafts.value[name]?.handlerKey) }
function missingHandler(name) { return Boolean(drafts.value[name]?.handlerKey && !selectedHandler(name)) }
function configured(name) {
  const draft = drafts.value[name]
  return draft ? Boolean(draft.invalid || (draft.mode === 'function' ? draft.handlerKey : draft.code.trim())) : hasFieldScript(preserved.value[name])
}
function legacyPreview(name) { const value = preserved.value[name]; return typeof value === 'string' ? value : JSON.stringify(value, null, 2) }
function clearEvent(name) {
  delete preserved.value[name]
  if (drafts.value[name]) drafts.value[name] = { mode: 'body', code: '', handlerKey: '' }
  if (!eventList.value.some(item => item.name === activeTab.value)) activeTab.value = eventList.value[0]?.name || ''
}

/** 保存只校验，不执行；未知旧事件允许存草稿，发布检查要求先解决。 */
function handleSave() {
  const result = { ...preserved.value }
  for (const item of catalog.value.events) {
    const draft = drafts.value[item.name]
    try {
      if (draft.invalid) throw new Error('配置格式无效，请先清空后重新配置')
      delete result[item.name]
      if (!configured(item.name)) continue
      const handler = selectedHandler(item.name)
      const binding = draft.mode === 'function'
        ? { mode: 'function', handler: handler?.name, version: handler?.version }
        : { mode: 'body', code: draft.code }
      validateFieldScript(binding, props.field, item.name)
      result[item.name] = binding
    } catch (error) {
      activeTab.value = item.name
      ElMessage.error(`${item.name} 配置错误：${error.message}`)
      return
    }
  }
  emit('save', result)
  dialogVisible.value = false
}
</script>

<style scoped>
.event-tip, .mode-switch { margin-bottom: 12px; }
.event-tip code, .event-parameters code { color: #409eff; font-family: monospace; }
.editor-label { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; font-size: 13px; }
.event-parameters { display: grid; gap: 4px; margin-bottom: 12px; font-size: 12px; }
.handler-select { width: 100%; }
.handler-description { color: var(--el-text-color-secondary); font-size: 13px; }
.legacy-config { white-space: pre-wrap; overflow-wrap: anywhere; }
:deep(.cm-editor) { border: 1px solid #dcdfe6; border-radius: 4px; }
</style>
