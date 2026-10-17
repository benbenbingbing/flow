/**
 * 整列表 Vue 契约 v2，使用 LIST 类型 JSON 清单。
 * 配置 listConfig.customComponent 后由 EntityDataList 装载。
 * 组件只负责布局；查询、分页、导出和数据动作交回宿主，避免重复实现请求和权限。
 * queryForm 是宿主共享的查询对象，现有协议允许编辑其字段，再调用 runtime.search()。
 * dataList/listFields/pageNum 等输入不可自行替换。
 *
 * @typedef {Object} CustomListRuntime
 * @property {number} version 当前为 2。
 * @property {Object} viewConfig 列表显示配置。
 * @property {function(): *} reload 重新读取当前页。
 * @property {function(): *} search 按 queryForm 查询并重置页码。
 * @property {function(): *} reset 清空查询并重载。
 * @property {function(): *} create 打开新增表单。
 * @property {function(Object): *} view 查看行。
 * @property {function(Object): *} edit 编辑行。
 * @property {function(Object): *} delete 删除行，使用宿主确认与权限流程。
 * @property {function(Object): *} approve 办理行对应流程。
 * @property {function(): *} exportData 按宿主列表范围导出。
 * @property {function(Object, string): boolean} canAction 参数为 row、buttonKey。
 * @property {function(Object, string): string} getActionReason 操作不可用原因。
 * @property {boolean} [canViewVersions] 是否可以打开记录版本。
 * @property {function(Object): *} [versions] 可选版本查看动作，使用前检查存在性。
 * @property {Object} [entityStatusMap] 状态文本映射。
 * @property {Object[]} [entityStatusOptions] 状态选项。
 * @property {function(*): string} [getStatusText] 状态文本格式化。
 */
export const customListProps = {
  // 稳定实体编码，不是数据库实体 ID。
  entityCode: { type: String, default: '' },
  // 当前实体定义，独立预览时可能为空。
  entityDefinition: { type: Object, default: null },
  // 实体显示名称，只用于展示。
  entityName: { type: String, default: '' },
  // 已选列表的配置与运行上下文。
  listConfig: { type: Object, default: () => ({}) },
  // 列表全部配置列，可能包含当前不可显示的列。
  listConfigFields: { type: Array, default: () => [] },
  // 宿主已裁剪的可显示列。
  listFields: { type: Array, default: () => [] },
  // 宿主提供的查询字段配置。
  queryFields: { type: Array, default: () => [] },
  // 宿主共享查询条件；编辑后交回 runtime.search 执行。
  queryForm: { type: Object, default: () => ({}) },
  // 当前页记录，由宿主加载，组件不替换数据源。
  dataList: { type: Array, default: () => [] },
  // 页面整体加载状态。
  loading: { type: Boolean, default: false },
  // 列表数据刷新状态。
  tableLoading: { type: Boolean, default: false },
  // 查询总条数，不是当前页行数。
  total: { type: Number, default: 0 },
  // 当前页码，从 1 开始。
  pageNum: { type: Number, default: 1 },
  // 每页条数，修改通过 sizeChange 事件交回宿主。
  pageSize: { type: Number, default: 10 },
  // 当前扩展的实例参数；与 JSON 清单的 configSchema 定义分开。
  config: { type: Object, default: () => ({}) },
  // 宿主提供的受控查询/操作方法；具体方法见本文件类型说明。
  runtime: { type: Object, required: true },
  // 状态值到显示文本的映射。
  entityStatusMap: { type: Object, default: () => ({}) },
  // 状态选项，用于展示或筛选。
  entityStatusOptions: { type: Array, default: () => [] },
  // 兼容宿主直接传入的方法；新模板优先使用 runtime.canAction 等聚合能力。
  canAction: Function,
  getActionReason: Function,
  getStatusType: Function,
  getStatusText: Function,
  formatDate: Function
}

/**
 * 分页必须 emit('sizeChange', size) / emit('pageChange', page)，runtime 没有分页方法。
 * 其他事件兼容已有组件；同一次操作选择 runtime 方法或事件之一，避免重复请求。
 */
export const customListEmits = [
  'search', 'reset', 'sizeChange', 'pageChange', 'create',
  'view', 'edit', 'delete', 'approve', 'versions'
]
