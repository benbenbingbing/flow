import { provide } from 'vue'
import { ElMessage } from 'element-plus'
import { FIELD_SCRIPT_CONTEXT } from '../shared/field-event-scripts.js'

/** 为字段脚本提供当前表单的读写入口，保留父表单的响应式同步与联动校验。 */
export function provideFieldScriptContext(getContext) {
  const record = () => {
    const context = getContext()
    if (context.getFormData) return context.getFormData()
    return context.record?.data || context.record || null
  }
  provide(FIELD_SCRIPT_CONTEXT, {
    getFieldValue: code => record()?.[code],
    setFieldValue(code, value) {
      const context = getContext()
      const data = record()
      const fields = context.scriptFields || context.form?.fields || []
      // 只接受字段编码，不能借路径字符串修改原型或跳出当前表单。
      if (typeof code !== 'string' || ['__proto__', 'prototype', 'constructor'].includes(code)
          || !data || !(Object.hasOwn(data, code)
            || fields.some(field => (field.fieldCode || field.fieldKey) === code))) {
        throw new Error(`当前表单不存在字段：${String(code)}`)
      }
      if (context.setFormFieldValue) context.setFormFieldValue(code, value)
      else data[code] = value
    },
    reportError(message, error) {
      console.error(message, error)
      ElMessage.error({ message, grouping: true })
    }
  })
}
