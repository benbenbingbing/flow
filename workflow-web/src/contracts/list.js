/**
 * 整列表 Vue 契约 v2，registerCustomListComponent(name, Component, metadata)。
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
  entityCode: { type: String, default: '' },
  entityDefinition: { type: Object, default: null },
  entityName: { type: String, default: '' },
  listConfig: { type: Object, default: () => ({}) },
  listConfigFields: { type: Array, default: () => [] },
  listFields: { type: Array, default: () => [] },
  queryFields: { type: Array, default: () => [] },
  queryForm: { type: Object, default: () => ({}) },
  dataList: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  tableLoading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 10 },
  config: { type: Object, default: () => ({}) },
  runtime: { type: Object, required: true },
  entityStatusMap: { type: Object, default: () => ({}) },
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
