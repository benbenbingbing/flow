/**
 * 关联内容自定义呈现的独立契约，来源 RelatedContentRuntime.vue。
 * 使用 FORM/LIST 清单且 metadata.usageContexts 包含 RELATED_CONTENT，
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
  // 宿主选定的表单配置及发布上下文。
  form: { type: Object, default: null },
  // 目标记录的业务字段，只读输入；本宿主不监听 update:modelValue，修改须调用 runtime.dispatch。
  modelValue: { type: Object, default: () => ({}) },
  // 宿主整表或节点只读状态，不得在组件内部绕过。
  readonly: { type: Boolean, default: true },
  // 宿主提供的字段定义/选项，具体结构见本契约。
  fields: { type: Array, default: () => [] },
  // 稳定实体编码，不是数据库实体 ID。
  entityCode: { type: String, default: '' },
  // 当前实体定义，独立预览时可能为空。
  entityDefinition: { type: Object, default: null },
  // 当前扩展的实例参数；与 JSON 清单的 configSchema 定义分开。
  config: { type: Object, default: () => ({}) },
  // 宿主运行上下文；可选能力须检查存在性，不自行构造可信身份。
  context: { type: Object, default: () => ({}) },
  // 宿主提供的受控查询/操作方法；具体方法见本文件类型说明。
  runtime: { type: Object, required: true }
}
