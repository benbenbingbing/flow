export const CONFIG_FIELD_HELP = Object.freeze({
  'entity.teamVisibilityLevel':
    '相关人查看改为列表绑定 TEAM 规则。此开关不再参与运行时计算。',
  'entity.permissionFilterTeam':
    '相关人只包含已在该记录 team 表留下参与事件的人。尚未生成任务的下一审批人不算相关人。',
  'entity.permissionFilterHasTodo':
    '匹配 process_task 中当前用户未完成的待办。会签按人一条；办理人可见数据应绑这条，不要再用实体当前办理人字段。',
  'entity.permissionRuleEffect':
    'ALLOW 把规则计算出的范围加入可见结果；DENY 从最终结果中排除该范围。多条规则会与实体和列表的数据范围一起计算。',
  'entity.permissionMatchLogic':
    'OR 表示命中任一适用对象条件即可应用规则；AND 表示用户必须同时满足全部条件。',
  'entity.permissionScopeType':
    '决定规则按全部用户、指定用户、角色、用户组、部门或组织匹配。部门和组织还可选择是否包含下级。',
  'entityList.dataScopeMode':
    '本列表只使用自己绑定的数据规则。未绑定允许规则时，有列表权限的人看到全部数据。',
  'entityList.selectionMode':
    '决定列表是普通浏览页，还是给表单或其他页面返回一条或多条选中记录。选择模式还需配置返回值字段和返回映射。',
  'entityList.queryType':
    '决定查询控件如何生成条件，例如等于、模糊、区间或多值匹配。可选项会按字段类型过滤。',
  'entityList.dataSourceType':
    '实体字段通常直接读取记录值；虚拟列可通过模板、受控 Provider 或统一数据源计算展示值。',
  'entityList.renderComponent':
    '只改变单元格如何展示，例如文本、状态标签、日期或已注册扩展组件，不改变原始字段值。',
  'uiConfig.releaseMode':
    '普通发布遵循标准版本切换；兼容热修复会先做影响预检，并按允许范围作用于当前可发起版本和运行中实例。历史完成实例仍使用原快照。',
  'form.inputParameterSchema':
    '声明本表单作为子表单时允许父表单传入的运行参数。子表单的数据源、联动和事件通过 params.参数编码 读取；参数默认不落库，需要保存时应由父表单显式初始化到子实体字段。',
  'formNode.subFormLayout':
    '“分行”用表单方式编辑单条或少量明细；“表格”适合多条结构相同的明细。',
  'formNode.subFormParameterContract':
    '运行参数用于子表单数据源、联动和事件，不直接保存；初始化子字段只在目标字段为空时写入。父子关系外键始终由系统维护，不能在这里手工映射。',
  'formDataSource.usage':
    'FORM_INIT 在新增时初始化整表；AFTER_LOAD 在记录加载后加工数据；BEFORE_SUBMIT 在提交前校验或转换，后端始终执行最终逻辑。',
  'entitySelection.overwrite':
    '始终覆盖会替换目标现值；仅空值覆盖会保护用户已填写内容；覆盖前确认会在存在旧值时询问用户。',
  'entitySelection.clearOnEmpty':
    '控制来源字段为空或清除实体选择时，目标字段是同步清空还是保留原值。',
  'actionRule.unavailableBehavior':
    '条件不满足时可完全隐藏按钮，或保留为禁用状态并展示原因。需要让用户知道功能存在但当前不可用时选择“禁用并说明”。',
  'process.multiInstanceType':
    '并行会同时创建多人任务；串行会按人员集合顺序逐个创建任务。并行或串行只表示创建顺序，不决定会签还是或签。',
  'process.multiInstanceDecision':
    '会签只统计通过票：达到阈值就通过，剩下的人全通过也凑不够才拒绝。或签第一人通过或驳回即结束本节点。',
  'process.multiInstanceCompletionCondition':
    '会签按已通过人数判断是否达标；驳回不加通过人数。开启“需要所有人审批”后等全员办完再按阈值判定，不会因一人驳回提前结束。',
  'process.multiInstanceCollection':
    '系统为当前节点生成的用户名集合变量，用于创建多实例任务。该变量只读，无需手工配置。',
  'process.multiInstanceElementVariable':
    '集合中的单个用户名在每个任务实例内使用的变量名，通常保持默认值 assignee。',
  'process.serviceImplementationType':
    '决定服务任务由 Java 类、表达式、Spring Bean 还是平台代理的 REST 调用执行。外部 HTTP 调用应使用 REST 配置并设置超时与失败策略。',
  'process.sequenceConditionType':
    '无条件会直接通过；表达式在计算为真时通过；默认流只在同一网关其他条件都未命中时使用，一个排他网关只能有一条默认流。',
  'process.allowManualCc':
    '开启后任务办理人可临时选择额外知会人；关闭后只能执行节点预配置的知会规则。',
  'process.slaCalendarSource':
    '决定 SLA 工作时间从节点、流程、业务归属部门、发起人部门还是系统默认日历解析。自然时间口径不受日历影响。',
  'process.actionTriggerTiming':
    '决定动作在任务创建、任务完成、连线通过、流程完成等哪个生命周期事件执行；可用时机会随当前作用域变化。',
  'process.actionExecutionMode':
    '事务内执行失败时可回滚当前流程操作；提交后执行不阻塞主事务，适合通知和外部接口，但必须依赖幂等与重试。',
  'process.actionFailurePolicy':
    '事务内可选择回滚或记录后继续；提交后可选择自动重试或记录后忽略。可用策略会随执行方式变化。',
  'uiEvent.inheritanceMode':
    '继承并追加会保留上级事件链；替换上级只使用当前层自定义链；禁用自定义会清空当前层步骤但保留平台默认处理。',
  'uiEvent.stepStrategy':
    '前置在平台默认处理前执行；替代平台处理会取代默认逻辑且同一事件最多一个；后置在默认处理成功后执行。',
  'uiEvent.failurePolicy':
    '停止执行会返回错误；记录后继续会跳过失败步骤；按空结果继续会把失败步骤当作空结果再执行后续映射。',
  'interfaceService.operationConfig':
    '当前操作的静态配置，不是调用时传入的 input。执行时先加载服务基础配置，再用操作配置覆盖同名键，并自动加入 operation 操作编码。适合不同操作的查询模式、目标字段或固定参数。',
  'interfaceService.operationInputSchema':
    '在调用 Provider 或 Connector 前校验当前操作最终收到的 input。支持 type、required、properties、items；填写空对象表示不校验。多操作服务运行时以操作级 Schema 为准。',
  'interfaceService.operationOutputSchema':
    '在接口执行后校验最终返回值，缓存命中结果和失败策略产生的回退结果也会校验。事件回填依赖固定结构时应配置；填写空对象表示不校验。',
  'interfaceService.baseConfig':
    '所有操作共享的静态配置。适合放 Provider 公共参数、字典编码或 Connector 配置编码；某个操作需要不同值时，在操作配置中使用同名键覆盖。',
  'interfaceService.baseInputSchema':
    '服务基础定义的输入契约，主要兼容没有操作目录的历史单操作服务。新建多操作服务应在每个操作中配置输入 Schema，操作级空对象表示该操作不校验。',
  'interfaceService.baseOutputSchema':
    '服务基础定义的输出契约，主要兼容没有操作目录的历史单操作服务。新建多操作服务应在每个操作中配置输出 Schema，操作级空对象表示该操作不校验。',
  'entityVersion.sourceTypes':
    '限定版本场景由表单、审批、流程动作、接口、导入、批量或系统任务等哪些入口触发。留空表示不按入口限制。',
  'entityVersion.operationTypes':
    '限定场景匹配新增、修改、删除、状态变化、变更生效或幂等写入中的哪些操作。',
  'entityVersion.phase':
    '准备和写入前发生在落库前；写入后仍在事务内；提交后发生在事务成功后，适合外部副作用。',
  'entityVersion.stepType':
    '选择内置规则、表达式、字段映射、受管理接口或 Java Provider。受管理接口固定在准备阶段执行。',
  'entityVersion.resolverType':
    '决定如何找到需要联动修改的目标记录：从引用字段取 ID、按实体关系查找，或调用受管理的 Java 解析器。',
  'entityVersion.applyStrategy':
    '合并只更新映射得到的字段；替换按新结果重建目标内容，未提供字段可能被清除。',
  'workCalendar.scopeType':
    '部门绑定优先于组织绑定；同一范围命中多个日历时，使用优先级更高且处于生效日期内的绑定。'
})

export function getConfigFieldHelp(helpKey) {
  return CONFIG_FIELD_HELP[String(helpKey || '').trim()] || ''
}
