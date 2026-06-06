import { computed, watch } from 'vue'

/**
 * 表单字段共享逻辑 Composable
 * 统一处理字段值转换、选项解析、组件属性解析、自定义事件脚本执行
 *
 * @param {Object} props - 组件 props，必须包含 field, modelValue, disabled, options
 * @param {Function} emit - Vue emit 函数
 * @returns {Object} 字段渲染所需的响应式状态和方法
 */
export function useFormField(props, emit) {
  // 渲染类型：优先使用 componentType，其次使用 fieldType
  const renderType = computed(() => {
    const type = props.field?.componentType || props.field?.fieldType || ''
    return type.toLowerCase()
  })

  // 字段显示标签
  const fieldLabel = computed(() => {
    return props.field?.fieldLabel || props.field?.fieldName || ''
  })

  // 字段值（含类型转换）
  const fieldValue = computed({
    get() {
      const val = props.modelValue
      const type = renderType.value

      if (['number', 'integer', 'long', 'decimal', 'double'].includes(type) && val === '') {
        return null
      }

      // 多选类字段强制数组化
      if ((type === 'checkbox' || type === 'select_multiple') && !Array.isArray(val)) {
        return val != null ? [val] : []
      }

      // 子表单按关系类型处理
      if (type === 'sub_form' && !Array.isArray(val)) {
        if (props.field?.relationType === 'ONE_TO_ONE' || props.field?.relation?.type === 'ONE_TO_ONE') {
          return val && typeof val === 'object' ? val : null
        }
        return val && typeof val === 'object' ? [val] : []
      }

      return val
    },
    set(val) {
      emit('update:modelValue', val)
    }
  })

  // 安全解析 componentProps JSON
  const parsedComponentProps = computed(() => {
    const cp = props.field?.componentProps
    if (!cp) return {}
    if (typeof cp === 'object') return cp
    try {
      return JSON.parse(cp)
    } catch (e) {
      return {}
    }
  })

  // 解析静态选项（多来源：componentProps.options -> optionsJson -> options）
  const staticOptions = computed(() => {
    const field = props.field
    if (!field) return []

    // 1. 从 componentProps 解析
    if (parsedComponentProps.value.options) {
      return parsedComponentProps.value.options
    }

    // 2. 从 optionsJson 解析
    if (field.optionsJson) {
      try {
        return JSON.parse(field.optionsJson)
      } catch (e) {
        return []
      }
    }

    // 3. 从 options 解析
    if (field.options) {
      if (typeof field.options === 'string') {
        try {
          return JSON.parse(field.options)
        } catch (e) {
          return []
        }
      }
      return field.options
    }

    return []
  })

  // 当前选项（外部传入的动态选项优先）
  const currentOptions = computed(() => {
    if (props.options && props.options.length > 0) {
      return props.options
    }
    return staticOptions.value
  })

  // 占位文本
  const placeholder = computed(() => {
    return props.field?.placeholder || `请输入${fieldLabel.value}`
  })

  // 是否禁用（props.disabled 已包含联动引擎的计算结果）
  const isDisabled = computed(() => {
    return props.disabled || props.field?.isReadonly === 1
  })

  // ========== 自定义事件脚本 ==========

  // 收集字段上配置的所有事件脚本
  function getAllEvents() {
    const field = props.field
    const result = {}
    if (!field) return result

    // 从根属性读取 eventOnXxx
    Object.keys(field).forEach(key => {
      if (key.startsWith('eventOn') && field[key]) {
        const eventName = 'on' + key.slice(7)
        result[eventName] = field[key]
      }
    })

    // 从 componentProps.events 读取
    if (parsedComponentProps.value.events) {
      Object.keys(parsedComponentProps.value.events).forEach(key => {
        if (!result[key]) {
          result[key] = parsedComponentProps.value.events[key]
        }
      })
    }

    return result
  }

  // 获取指定类型的事件脚本代码
  function getEventCode(eventType) {
    const suffix = eventType.startsWith('on') ? eventType.slice(2) : eventType
    const rootKey = 'eventOn' + suffix.charAt(0).toUpperCase() + suffix.slice(1)
    const field = props.field
    if (!field) return ''

    if (field[rootKey]) return field[rootKey]
    if (parsedComponentProps.value.events?.[eventType]) {
      return parsedComponentProps.value.events[eventType]
    }
    return ''
  }

  // 执行事件脚本
  function executeEvent(code, value) {
    if (!code) return
    try {
      const func = new Function('value', 'field', code)
      func(value, props.field)
    } catch (e) {
      console.error('字段事件执行失败:', e)
    }
  }

  // 标准事件处理器
  function handleChange(val) {
    fieldValue.value = val
    executeEvent(getEventCode('onChange'), val)
    emit('change', val)
  }

  function handleBlur() {
    executeEvent(getEventCode('onBlur'), fieldValue.value)
    emit('blur', fieldValue.value)
  }

  function handleFocus() {
    executeEvent(getEventCode('onFocus'), fieldValue.value)
    emit('focus', fieldValue.value)
  }

  // 自定义 DOM 事件监听器（排除已手动处理的 change/blur/focus）
  const customEventListeners = computed(() => {
    const listeners = {}
    const events = getAllEvents()
    Object.keys(events).forEach(key => {
      if (['onChange', 'onBlur', 'onFocus'].includes(key)) return
      const domEvent = key.startsWith('on') ? key.slice(2) : key
      const eventName = domEvent.charAt(0).toLowerCase() + domEvent.slice(1)
      listeners[eventName] = () => executeEvent(events[key], fieldValue.value)
    })
    return listeners
  })

  // ========== 默认值注入 ==========

  watch(
    () => props.field?.defaultValue,
    (val) => {
      if (val != null && (props.modelValue == null || props.modelValue === '')) {
        emit('update:modelValue', val)
      }
    },
    { immediate: true }
  )

  return {
    renderType,
    fieldLabel,
    fieldValue,
    parsedComponentProps,
    staticOptions,
    currentOptions,
    placeholder,
    isDisabled,
    handleChange,
    handleBlur,
    handleFocus,
    customEventListeners,
    getEventCode,
    executeEvent
  }
}
