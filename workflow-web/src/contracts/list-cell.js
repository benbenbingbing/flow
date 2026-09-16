/**
 * 列表单元格 Vue 契约，registerCellComponent(name, Component, metadata)。
 * 列配置 renderComponent 选择组件；config 优先取 renderConfig，兼容 dataSourceConfig。
 * value 已经由宿主 formatListFieldValue 格式化，不能当作原始 ID；原值按字段从 row 读取。
 * context 由页面提供实体、列表、刷新、状态和引用显示信息，具体能力使用前检查存在性。
 * 单元格不负责修改记录；仅改格式时用 configSchema 暴露参数，无需复制整列表。
 */
export const listCellProps = {
  value: { type: [String, Number, Boolean, Object, Array], default: '' },
  row: { type: Object, default: () => ({}) },
  field: { type: Object, default: () => ({}) },
  config: { type: Object, default: () => ({}) },
  context: { type: Object, default: () => ({}) }
}
