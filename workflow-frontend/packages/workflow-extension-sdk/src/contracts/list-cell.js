/**
 * 列表单元格 Vue 契约，使用 LIST_CELL 类型 JSON 清单。
 * 列配置 renderComponent 选择组件；config 优先取 renderConfig，兼容 dataSourceConfig。
 * value 已经由宿主 formatListFieldValue 格式化，不能当作原始 ID；原值按字段从 row 读取。
 * context 由页面提供实体、列表、刷新、状态和引用显示信息，具体能力使用前检查存在性。
 * 单元格不负责修改记录；仅改格式时用 configSchema 暴露参数，无需复制整列表。
 */
export const listCellProps = {
  // 宿主格式化后的展示值；业务原始值从 row 取。
  value: { type: [String, Number, Boolean, Object, Array], default: '' },
  // 当前记录；工具栏按钮不保证存在。
  row: { type: Object, default: () => ({}) },
  // 宿主字段配置快照，包含字段编码和 componentProps；不要原地修改。
  field: { type: Object, default: () => ({}) },
  // 当前扩展的实例参数；与 JSON 清单的 configSchema 定义分开。
  config: { type: Object, default: () => ({}) },
  // 宿主运行上下文；可选能力须检查存在性，不自行构造可信身份。
  context: { type: Object, default: () => ({}) }
}
