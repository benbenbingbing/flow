import { safeParseConfig } from '@flow/workflow-core/config-runtime'

/** 移动端自定义整表单接入参数；使用宿主的 services 与运行时，沿用发布配置的权限和字段事件。 */
export const extensionFormProps = {
  form: { type: Object, default: () => ({}) }, modelValue: { type: Object, default: () => ({}) }, fields: { type: Array, default: () => [] }, entityFields: { type: Array, default: () => [] },
  readonly: Boolean, mode: { type: String, default: 'view' }, context: { type: Object, default: () => ({}) }, services: { type: Object, default: () => ({}) },
  dataSourceRuntime: Object, linkageState: { type: Object, default: () => ({}) }, config: { type: Object, default: () => ({}) }
}

/** 发布配置覆盖 UI 的默认属性；保留实体约束和引用范围，禁止丢失后退化为全量选择。 */
export function mergeExtensionField(fallback, props) {
  const code = fallback.fieldCode
  const entity = props.entityFields.find(item => item.fieldCode === code) || {}
  const published = props.fields.find(item => (item.fieldCode || item.fieldKey) === code) || {}
  return { ...fallback, ...entity, ...published, fieldCode: code, componentProps: { ...safeParseConfig(fallback.componentProps), ...safeParseConfig(entity.componentProps), ...safeParseConfig(published.componentProps) } }
}
