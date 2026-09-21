import { safeParseConfig } from '@flow/workflow-core/config-runtime'

export const businessFormProps = {
  form: { type: Object, default: () => ({}) }, modelValue: { type: Object, default: () => ({}) }, fields: { type: Array, default: () => [] }, entityFields: { type: Array, default: () => [] },
  readonly: Boolean, mode: { type: String, default: 'view' }, context: { type: Object, default: () => ({}) }, services: { type: Object, default: () => ({}) },
  dataSourceRuntime: Object, linkageState: { type: Object, default: () => ({}) }, config: { type: Object, default: () => ({}) }
}

/** 发布配置覆盖 UI 的默认属性；保留实体约束和引用范围，禁止丢失后退化为全量选择。 */
export function mergeBusinessField(fallback, props) {
  const code = fallback.fieldCode
  const entity = props.entityFields.find(item => item.fieldCode === code) || {}
  const published = props.fields.find(item => (item.fieldCode || item.fieldKey) === code) || {}
  return { ...fallback, ...entity, ...published, fieldCode: code, componentProps: { ...safeParseConfig(fallback.componentProps), ...safeParseConfig(entity.componentProps), ...safeParseConfig(published.componentProps) } }
}
