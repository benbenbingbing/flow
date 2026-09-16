/**
 * JS 列表动作契约。工具栏通过 registerListToolbarAction(name, handler)，
 * 行通过 registerListRowAction(name, handler) 注册，handler(context) 是普通函数。
 * 打开列表后的选择回调也复用这两个注册表，配置项为 selectionHandler。
 * 当前宿主直接调用 handler，没有 await 或统一 catch；异步实现必须自行捕获错误，
 * 自行管理 loading/重复点击，成功后可 await context.refresh()。返回值不被宿主消费。
 *
 * @typedef {Object} ListActionContext
 * @property {string} entityCode 当前实体编码。
 * @property {Object} entityDefinition 实体定义。
 * @property {function(): *} refresh 刷新当前列表。
 * @property {Object} config 当前按钮完整配置，含 key/customHandler 等。
 * @property {Object} [row] 行动作的当前记录，工具栏动作不保证存在。
 * @property {Object[]} [selectedRows] 工具栏已选记录；选择回调时为弹出列表的选择结果。
 * @property {Object[]} [rows] 仅 selectionHandler 回调提供，和 selectedRows 为同一批结果。
 *
 * @callback ListActionHandler
 * @param {ListActionContext} context
 * @returns {void|Promise<void>}
 *
 * Vue 按钮契约：registerListButtonComponent(name, Component)。按钮配置
 * type='custom'、customMode='component'、customHandler=name。
 * 宿主传入 mode/row/disabled/reason/context，无标准 click 事件回调，业务在组件内执行。
 * context 提供 refresh/canAction/getActionReason 等；按钮组件不保证收到 config。
 * 组件应展示 disabled 和 reason，并在方法内部检查 disabled，保护键盘/程序触发。
 */
export const listButtonProps = {
  mode: { type: String, default: 'toolbar' },
  row: { type: Object, default: null },
  disabled: { type: Boolean, default: false },
  reason: { type: String, default: '' },
  context: { type: Object, default: () => ({}) }
}
