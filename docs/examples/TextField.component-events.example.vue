<!--
  设计示例：展示组件自声明前端事件后的 TextField 写法，不参与当前应用构建。
  尚需实现：组件声明提取、按声明生成编辑器、函数模式执行，以及 useFormField 的
  eventAdapter: 'text'、handleClear、triggerScriptEvent。当前 API 不提供这些能力。
  完整契约见 docs/component-owned-field-events-plan.md；不能直接替换生产组件。
-->
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
      @input="handleInput"
      @change="handleChange"
      @focus="handleFocus"
      @blur="handleBlur"
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
      @input="handleInput"
      @change="handleChange"
      @focus="handleFocus"
      @blur="handleBlur"
      @clear="handleClear"
      @keydown="handleKeydown"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useFormField } from '@flow/workflow-core/vue/useFormField'
import { resolveTextFieldMaxLength } from '@flow/workflow-core/config-runtime'

// 声明随组件维护；注册器在挂载前读取，供设计器展示和运行时校验。
// 这里只保存能力说明，不保存某个表单的业务代码，也不在 manifest 重复填写。
defineOptions({
  name: 'TextField',
  scriptEvents: [
    {
      name: 'onInput',
      label: '输入中',
      description: '文本输入过程中触发；不执行后台值变化事件链'
    },
    {
      name: 'onChange',
      label: '值变化',
      description: '用户提交值变化时触发；处理结束后通知宿主执行原有 change 链路'
    },
    {
      name: 'onFocus',
      label: '聚焦',
      description: '输入框获得焦点时触发'
    },
    {
      name: 'onBlur',
      label: '失焦',
      description: '输入框失去焦点时触发，不要求值已变化'
    },
    {
      name: 'onClear',
      label: '清空',
      description: '点击清空按钮时触发；开始执行时 value 为空字符串，键盘删除不触发',
      // textarea 没有清空按钮；这里按规范注册名过滤，历史别名由注册器归一。
      variants: ['input', 'password']
    },
    {
      name: 'onKeydown',
      label: '按键',
      description: '按下键盘时通知脚本；可观察按键信息，不提供阻止默认行为的能力',
      parameters: [
        { name: 'key', type: 'string', description: '按键名称，例如 Enter' },
        { name: 'code', type: 'string', description: '物理按键编码，例如 Enter、NumpadEnter' },
        { name: 'isComposing', type: 'boolean', description: '输入法正在组词时为 true，用于排除候选词确认' },
        { name: 'ctrlKey', type: 'boolean', description: '是否同时按下 Ctrl' },
        { name: 'altKey', type: 'boolean', description: '是否同时按下 Alt' },
        { name: 'shiftKey', type: 'boolean', description: '是否同时按下 Shift' },
        { name: 'metaKey', type: 'boolean', description: '是否同时按下 Command 或 Windows 键' }
      ]
    }
  ]
})

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [String, Number], default: '' },
  disabled: { type: Boolean, default: false },
  options: { type: Array, default: null }
})

// 保留现有宿主协议。scriptEvents 定义的脚本通知由 composable 可等待地执行，
// 不通过 Vue emit 的返回值等待，避免脚本完成前就启动后台事件链。
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
  parsedComponentProps
} = useFormField(props, emit, {
  // 拟新增：合并一次清空产生的 update/change/clear/input 通知，
  // 按 onClear -> onInput -> onChange -> 宿主 change 执行，防止重复与误取消。
  eventAdapter: 'text'
})

const isTextarea = computed(() => {
  const type = props.field?.componentType || props.field?.fieldType || ''
  return type.toLowerCase() === 'textarea'
})

// 密码仍复用文本值协议，展示时保持遮蔽，不因事件改造退回明文输入。
const isPassword = computed(() => String(props.field?.componentType || '').toLowerCase() === 'password')
const textareaRows = computed(() => parsedComponentProps.value.rows || 3)
const maxlength = computed(() => resolveTextFieldMaxLength(props.field, parsedComponentProps.value))
const showWordLimit = computed(() => !isPassword.value
  && !!maxlength.value
  && parsedComponentProps.value.showWordLimit !== false)

/**
 * 将原生按键转为声明中的业务参数。脚本可以判断 Enter 或修饰键，
 * 但不会持有 DOM/组件实例；当前值由共享执行器获取，避免 props 尚未同步。
 * @param {KeyboardEvent} event 输入框的原生按键事件。
 * @returns {Promise<{current: boolean, value: unknown}>|undefined} 禁用时不执行，
 *   否则等待本字段脚本处理；超时和异常由共享执行器提示。
 */
function handleKeydown(event) {
  if (isDisabled.value) return
  return triggerScriptEvent('onKeydown', {
    data: {
      key: event.key,
      code: event.code,
      isComposing: event.isComposing,
      ctrlKey: event.ctrlKey,
      altKey: event.altKey,
      shiftKey: event.shiftKey,
      metaKey: event.metaKey
    }
  })
}
</script>

<style scoped>
.text-field {
  width: 100%;
}
</style>
