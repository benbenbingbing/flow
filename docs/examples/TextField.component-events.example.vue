<template>
  <div class="text-field">
    <el-input
      v-if="isTextarea"
      v-model="fieldValue"
      type="textarea"
      :rows="textareaRows"
      :placeholder="placeholder"
      :disabled="isDisabled"
      :maxlength="maxlength"
      :show-word-limit="showWordLimit"
      v-on="textListeners"
      @change="handleChange"
      @input="handleInput"
      @blur="handleBlur"
      @focus="handleFocus"
      @keydown="handleKeydown"
    />
    <el-input
      v-else
      v-model="fieldValue"
      :type="isPassword ? 'password' : 'text'"
      :show-password="isPassword && !isDisabled"
      :placeholder="placeholder"
      :disabled="isDisabled"
      :maxlength="maxlength"
      :show-word-limit="showWordLimit"
      clearable
      @clear="handleClear"
      v-on="textListeners"
      @change="handleChange"
      @input="handleInput"
      @blur="handleBlur"
      @focus="handleFocus"
      @keydown="handleKeydown"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useFormField } from '@flow/workflow-core/vue/useFormField'
import { resolveTextFieldMaxLength } from '@flow/workflow-core/config-runtime'

// 事件目录作为组件静态选项随构建保留；安装器按 input/password/textarea 过滤变体。
defineOptions({
  name: 'TextField',
  scriptEvents: [
    { name: 'onInput', label: '输入中', description: '文本输入时触发，不执行值变化事件链' },
    { name: 'onChange', label: '值变化', description: '提交变化或清空时触发，处理完成后执行值变化事件链' },
    { name: 'onFocus', label: '聚焦', description: '输入框获得焦点时触发' },
    { name: 'onBlur', label: '失焦', description: '输入框失去焦点时触发' },
    { name: 'onClear', label: '清空', description: '点击清空按钮后触发；随后执行输入和值变化事件', variants: ['input', 'password'] },
    { name: 'onKeydown', label: '按键', description: '按下键盘时触发；仅观察，不阻止默认行为', parameters: [
      { name: 'key', type: 'string', description: '按键名称，例如 Enter', required: true },
      { name: 'code', type: 'string', description: '物理按键编码' },
      { name: 'isComposing', type: 'boolean', description: '是否正在输入法组词', required: true },
      { name: 'ctrlKey', type: 'boolean', description: '是否按住 Ctrl' },
      { name: 'altKey', type: 'boolean', description: '是否按住 Alt' },
      { name: 'shiftKey', type: 'boolean', description: '是否按住 Shift' },
      { name: 'metaKey', type: 'boolean', description: '是否按住 Command/Meta' }
    ] },
    { name: 'onKeyup', label: '松开按键', description: '松开键盘按键时触发' },
    { name: 'onMouseenter', label: '鼠标进入', description: '鼠标进入输入控件时触发' },
    { name: 'onMouseleave', label: '鼠标离开', description: '鼠标离开输入控件时触发' },
    { name: 'onClick', label: '点击', description: '点击输入控件时触发' },
    { name: 'onCompositionstart', label: '开始组词', description: '输入法开始组合文本时触发' },
    { name: 'onCompositionupdate', label: '组词更新', description: '输入法组合文本更新时触发' },
    { name: 'onCompositionend', label: '结束组词', description: '输入法完成组合文本时触发' }
  ]
})

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [String, Number], default: '' },
  disabled: { type: Boolean, default: false },
  options: { type: Array, default: null }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])

const {
  fieldValue,
  placeholder,
  isDisabled,
  handleChange,
  handleInput,
  handleBlur,
  handleFocus,
  handleClear,
  triggerScriptEvent,
  customEventListeners,
  scriptEvents,
  parsedComponentProps
} = useFormField(props, emit, { eventAdapter: 'text' })

/** DOM 事件只提取普通数据，避免将 Event/组件实例交给配置代码。 */
function handleKeydown(event) {
  return triggerScriptEvent('onKeydown', { data: {
    key: event.key, code: event.code, isComposing: event.isComposing || event.keyCode === 229,
    ctrlKey: event.ctrlKey, altKey: event.altKey, shiftKey: event.shiftKey, metaKey: event.metaKey
  } })
}

// 保留原来实际可用的鼠标与输入法事件；监听名称来自组件实现，不从用户任意事件名生成。
const observationListeners = Object.fromEntries([
  'Keyup', 'Mouseenter', 'Mouseleave', 'Click', 'Compositionstart', 'Compositionupdate', 'Compositionend'
].map(name => [name.toLowerCase(), () => triggerScriptEvent(`on${name}`)]))
// 已发布字符串脚本保留原透传入口；新配置只接受目录事件，显式事件不能重复监听。
const textListeners = computed(() => {
  const declared = new Set((scriptEvents.value || []).map(item => item.name.slice(2).replace(/^./, value => value.toLowerCase())))
  return { ...Object.fromEntries(Object.entries(customEventListeners.value).filter(([name]) => !declared.has(name))), ...observationListeners }
})

const isTextarea = computed(() => {
  const type = props.field?.componentType || props.field?.fieldType || ''
  return type.toLowerCase() === 'textarea'
})

// 密码控件复用文本的数据与事件协议；只读时也保持遮蔽，不能回退成明文展示。
const isPassword = computed(() => String(props.field?.componentType || '').toLowerCase() === 'password')

const textareaRows = computed(() => {
  return parsedComponentProps.value.rows || 3
})

const maxlength = computed(() => {
  return resolveTextFieldMaxLength(props.field, parsedComponentProps.value)
})

const showWordLimit = computed(() => {
  return !isPassword.value && !!maxlength.value && parsedComponentProps.value.showWordLimit !== false
})
</script>

<style scoped>
.text-field {
  width: 100%;
}
</style>
