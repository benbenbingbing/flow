/**
 * 关联内容自定义呈现的独立契约，来源 RelatedContentRuntime.vue。
 * 按目标内容类型复用 registerCustomFormComponent / registerCustomListComponent 注册，
 * 关联配置使用组件 name/version/artifactDigest/props；实际选版和装载由宿主完成。
 *
 * 这里没有普通整列表的 dataList/queryForm/pageNum，也不保证 mode、formActionSlots。
 * modelValue 是目标记录的业务字段数据，不是来源记录；LIST 应通过 runtime.query 查询。
 * 宿主没有监听 update:modelValue 或调用 validate()，修改必须走已发布的 runtime.dispatch。
 *
 * @typedef {Object} RelatedContentRuntime
 * @property {function(): Promise<*>} refresh 重新解析并刷新关联内容。
 * @property {function({pageNum?: number, pageSize?: number, filters?: Object}): Promise<*>} query
 *   仅 LIST 目标可调用；服务端约束查询范围，响应是列表查询 API 结果。
 * @property {function(string, Object=): Promise<*>} dispatch
 *   传 capabilities 中的动作名和 { targetRecordIds }。不接受自选 extensionId/input；
 *   不可用或忙碌时可返回 null，返回值不能一律理解为成功。
 * @property {function(): Promise<*>} openLinkCandidates 打开受控关联候选选择。
 * @property {string[]} capabilities 已启用的动作名，如 VIEW/EDIT/CREATE。
 * @property {Object} actionCapabilities 服务端动作可用性结果，应结合宿主结果使用。
 */
export const relatedContentProps = {
  form: { type: Object, default: null },
  modelValue: { type: Object, default: () => ({}) },
  readonly: { type: Boolean, default: true },
  fields: { type: Array, default: () => [] },
  entityCode: { type: String, default: '' },
  entityDefinition: { type: Object, default: null },
  config: { type: Object, default: () => ({}) },
  context: { type: Object, default: () => ({}) },
  runtime: { type: Object, required: true }
}
