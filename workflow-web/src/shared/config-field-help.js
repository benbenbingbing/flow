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
    '决定规则按全部用户、指定用户、角色、用户组、部门、组织或自定义 SQL 匹配。部门和组织还可选择是否包含下级。',
  'entity.permissionMatchSql':
    '只写判断当前用户是否命中的条件，不要写完整语句。不能引用主表别名 biz。可用 #{userId}、#{username}、#{deptId}、#{orgId}。',
  'entity.permissionFilterSql':
    '选择「自定义 SQL」后只写 WHERE 条件。主表别名统一为 biz，例如 biz.create_by = #{userId}。运行时会替换成实体物理表。',
  'entityList.dataScopeMode':
    '本列表只使用自己绑定的数据规则。未绑定允许规则时，有列表权限的人看到全部数据。',
  'entityList.selectionMode':
    '决定列表是普通浏览页，还是给表单或其他页面返回一条或多条选中记录。选择模式还需配置返回值字段和返回映射。',
  'entityList.queryType':
    '决定查询控件如何生成条件，例如等于、模糊、区间或多值匹配。可选项会按字段类型过滤。',
  'entityList.dataSourceType':
    '自定义列数据源需实现 ListFieldDataProvider、加 @Component，并用 getDataSourceType() 返回唯一编码；实体字段可直接读取记录值。',
  'uiDataSource.service':
    '后端自定义需实现 UiDataSourceProvider、加 @Component，并保证 getCode() 唯一；可参考 ProjectCustomFormUiDataSourceProvider，再到“接口服务”中新建“平台注册能力”。',
  'entityList.renderComponent':
    '只改变单元格如何展示，例如文本、状态标签、日期或已注册扩展组件，不改变原始字段值。',
  'uiConfig.releaseMode':
    '普通发布遵循标准版本切换；兼容热修复会先做影响预检并显示风险提醒，确认后按允许范围作用于当前可发起版本和运行中实例。历史完成实例仍使用原快照。',
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
  'actionRule.visibleWhen':
    '按钮的第一层判断。显示条件不满足时直接隐藏按钮，不再评估启用条件；适合当前场景没有必要暴露的操作。',
  'actionRule.enabledWhen':
    '仅在按钮通过显示条件后判断。启用条件不满足时按钮仍然可见，但不可点击，并展示配置的禁用提示。',
  'actionRule.disabledMessage':
    '启用条件不满足时展示给用户的原因。请写清限制主体、当前状态或下一步，例如“仅本人未流转草稿可以删除”。',
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
    'Java 类需实现 Flowable JavaDelegate 并填写全限定类名；Spring Bean 同样实现 JavaDelegate、加 @Component，并填写 ${beanName}。外部 HTTP 使用 REST 配置。',
  'process.sequenceConditionType':
    '无条件会直接通过；表达式在计算为真时通过；默认流只在同一网关其他条件都未命中时使用，一个排他网关只能有一条默认流。',
  'process.allowManualCc':
    '开启后任务办理人可临时选择额外知会人；关闭后只能执行节点预配置的知会规则。',
  'process.slaCalendarSource':
    '决定 SLA 工作时间从节点、流程、业务归属部门、发起人部门还是系统默认日历解析。自然时间口径不受日历影响。',
  'process.actionTriggerTiming':
    '决定动作在哪个流程生命周期事件执行；可用时机会随当前作用域变化。新增自定义时机需实现 FlowActionTriggerProvider 并注册为 Spring Bean。',
  'process.actionExecutionMode':
    '事务内执行失败时可回滚当前流程操作；提交后执行不阻塞主事务，适合通知和外部接口，但必须依赖幂等与重试。',
  'process.actionFailurePolicy':
    '事务内可选择回滚或记录后继续；提交后可选择自动重试或记录后忽略。可用策略会随执行方式变化。',
  'process.flowActionHandler':
    '后端自定义需实现 FlowActionHandler、加 @Component；Bean 名称即处理器编码。需要类型化参数时可实现 TypedFlowActionHandler。',
  'process.personResolver':
    '后端自定义需实现 PersonResolver、加 @Component，并在 descriptor() 中声明唯一编码、适用场景和参数 Schema。',
  'uiEvent.inheritanceMode':
    '继承并追加会保留上级自定义事件链；替换上级只使用当前层自定义链；禁用自定义会清空自定义步骤。对存在平台默认动作的事件，是否替代该动作仍由步骤执行位置决定。',
  'uiEvent.formButtonInheritanceMode':
    '表单按钮目标中，“继承并追加”保留上级步骤，“仅使用当前层”清除上级步骤。自定义按钮没有平台默认动作；如需停用按钮，请关闭按钮的“启用”开关，不要用继承方式代替。',
  'uiEvent.stepStrategy':
    '前置在平台默认处理前执行；替代平台处理会取代默认逻辑且同一事件最多一个；后置在默认处理成功后执行。',
  'uiEvent.formButtonStepStrategy':
    'FORM_BUTTON_CLICK 没有平台默认动作。步骤按“前置处理 → 主处理 → 后置处理”执行；最终有效链必须恰好包含一个无执行条件的主处理（底层值为 REPLACE），主处理结果供后置步骤使用。条件请配置在按钮显示/启用规则或前置、后置步骤中。',
  'uiEvent.inputMapping':
    '把当前事件数据组装成接口入参：左侧填写来源路径，右侧填写接口参数路径，例如 input.form.customerId → customerId。不配置时会直接使用事件原始输入。用户、任务、流程等可信身份信息由服务端上下文提供，不要通过页面参数传入。',
  'uiEvent.outputMapping':
    '把接口结果或当前事件数据写入目标字段：左侧填写来源路径，右侧选择或填写回填路径，并可设置值转换和覆盖策略。未选择接口服务时，本步骤至少需要配置一条字段回填。',
  'uiEvent.failurePolicy':
    '停止执行会返回错误；记录后继续会跳过失败步骤；按空结果继续会把失败步骤当作空结果再执行后续映射。',
  'interfaceService.operationConfig':
    '当前操作的静态配置，不是调用时传入的 input。执行时先加载服务基础配置，再用操作配置覆盖同名键；Provider 可从 context.common().operationCode() 读取操作编码。',
  'interfaceService.backendImplementation':
    '平台注册能力需实现 UiDataSourceProvider、加 @Component，并由 getCode() 返回唯一编码。',
  'interfaceService.operationInputSchema':
    '在调用 Provider 前校验当前操作最终收到的 input。支持 type、required、properties、items；填写空对象表示不校验。多操作服务运行时以操作级 Schema 为准。',
  'interfaceService.operationOutputSchema':
    '在接口执行后校验最终返回值，缓存命中结果和失败策略产生的回退结果也会校验。事件回填依赖固定结构时应配置；填写空对象表示不校验。',
  'interfaceService.baseConfig':
    '所有操作共享的静态配置。适合放 Provider 公共参数或字典编码；某个操作需要不同值时，在操作配置中使用同名键覆盖。',
  'interfaceService.baseInputSchema':
    '服务基础定义的输入契约，主要兼容没有操作目录的历史单操作服务。新建多操作服务应在每个操作中配置输入 Schema，操作级空对象表示该操作不校验。',
  'interfaceService.baseOutputSchema':
    '服务基础定义的输出契约，主要兼容没有操作目录的历史单操作服务。新建多操作服务应在每个操作中配置输出 Schema，操作级空对象表示该操作不校验。',
  'interfaceService.debugService':
    '含义：当前正在调试的接口服务，只读显示。使用方法：如需切换，关闭弹窗后从目标服务所在行点击“调试”。适用场景：服务保存后、正式绑定前，确认它能在真实页面上下文中运行。',
  'interfaceService.debugOperation':
    '含义：本次执行的具体操作，决定操作配置、输入输出 Schema 及 READ/WRITE 语义。使用方法：选择与未来绑定相同的操作；切换后业务上下文会按操作定义同步变化。适用场景：一个服务包含查询、详情、保存等多个操作时逐项联调；WRITE 操作会产生真实写入或外部副作用。',
  'interfaceService.debugBusinessContext':
    '含义：操作声明的 FORM、LIST 或 ENTITY 运行上下文，系统据此校验实体、权限和数据范围。使用方法：该项随所选操作自动确定；如不符合预期，请编辑操作的业务上下文。适用场景：模拟操作未来在表单、列表或实体变更中的真实执行环境。',
  'interfaceService.debugConfigObject':
    '含义：用于本次调试的具体表单、列表或实体。使用方法：选择最终计划绑定此操作的真实配置对象，系统会校验访问权限及服务作用范围。适用场景：同一服务用于多个页面时，分别选择各对象验证权限、字段和数据范围。',
  'interfaceService.debugUsage':
    '含义：“操作”决定调用哪个能力，“调用用途”决定该能力在何处或何时调用，并进入执行上下文和缓存键。使用方法：选择与未来绑定相同的编码，例如 FIELD_OPTIONS 加载字段选项、DETAIL_LOAD 加载详情、DATA_UPDATE 更新数据。适用场景：实现会按用途返回不同结构，或需要分别验证不同位置和事件的缓存、权限行为；调试不会创建绑定，正式运行时才按用途匹配已发布绑定。',
  'interfaceService.debugInput':
    '含义：传给操作的业务 JSON 对象，不包含系统身份等可信上下文。使用方法：填写合法 JSON，字段及类型须符合所选操作的输入 Schema；无参数时填写 {}。适用场景：传递查询条件、记录 ID、分页参数或待处理字段，并验证必填项和类型约束。',
  'interfaceService.debugResult':
    '含义：显示本次执行返回的格式化 JSON 或错误信息。使用方法：对照所选用途、输出 Schema 及后续结果映射核对结构；如提示“类型应为 array，实际为 object”，先确认用途是否选对，再检查实际返回与 Schema。适用场景：验证选项列表、分页结果、字段回填以及异常或空结果；内容不会自动保存为页面配置。',
  'entityVersion.enabled':
    '启用后，保存的生成时机才会自动匹配数据变化，也允许具备权限的用户手工固化。停用会停止生成新版本，但不会删除已有历史版本。',
  'entityVersion.triggerType':
    '根实体变化监听当前实体写入；关联数据变化监听所选关系中的数据写入；手工固化只在用户或流程显式请求时生成版本。',
  'entityVersion.triggerCode':
    '生成时机的稳定业务标识，会随数据版本一起保存并用于追溯。创建后不可修改；名称可以调整，但不要把同一编码复用于另一种含义。',
  'entityVersion.triggerRelation':
    '仅“关联数据变化”使用。所选关系必须已加入固化范围；其子记录变化命中条件后，会为对应根记录生成完整版本。',
  'entityVersion.sourceTypes':
    '限定根实体变化由表单、审批、流程动作、接口、导入、批量或系统任务等哪些入口触发。留空表示不按入口限制。',
  'entityVersion.operationTypes':
    '限定生成时机匹配新增、修改、删除、状态变化、变更生效或幂等写入中的哪些操作；留空表示匹配全部操作。',
  'entityVersion.businessIntents':
    '只匹配运行上下文中相同编码的业务意图，例如审批通过或变更生效；留空表示不按业务意图限制。',
  'entityVersion.triggerCondition':
    '在入口、操作和业务意图均匹配后继续判断。字段默认读取变更后的记录，可用 all、any、not 组合条件；{} 表示不限制。',
  'entityVersion.triggerPriority':
    '同一次变化命中多个生成时机时，只采用优先级最高的一项生成版本；数值越大优先级越高。',
  'entityVersion.titleTemplate':
    '生成版本标题的模板。可用 ${versionNo}、${triggerName}（兼容 ${scenarioName}）和 ${businessIntentName}；未填写时使用版本号和生成时机名称。',
  'entityVersion.scopeFields':
    '“全部已发布字段”会在保存配置时冻结当时的字段集合；“指定字段”只固化选中的稳定字段编码。之后实体字段变化不会改写历史版本。',
  'entityVersion.scopeRelation':
    '选择要随根记录一起固化的已发布实体关系。保存配置时会冻结关系路径和展示定义，后续关系定义变化需重新检查并保存配置。',
  'entityVersion.scopeFilter':
    '只固化满足固定条件的关联记录；“全部满足”要求每个条件都成立，“任一满足”只需一个条件成立。未添加条件表示不过滤。',
  'entityVersion.scopeMaxRows':
    '该关系单次最多固化的行数，实际还会受全局“单关系最多”限制；任一上限被超过时，本次版本整体生成失败。',
  'entityVersion.scopeLimits':
    '单关系最多是所有关系的全局上限，整版最多限制关联总行数，整版大小限制快照体积；实际还会取每个关系自身上限，超限时不会保存截断版本。',
  'entityVersion.diffChangedOnly':
    '只决定打开版本比较时是否默认隐藏未变化字段和关联行，不影响版本中实际固化的数据，也可在比较页面临时切换。',
  'entityVersion.diffTrackOrder':
    '开启后比较会把同一关联记录的位置变化识别为“移动”；关闭后只比较记录新增、删除和字段变化。',
  'entityVersion.diffIgnoredFields':
    '所选字段仍会固化在版本快照中，但版本比较不会把它们的值变化计入差异，适合更新时间等高频噪声字段。',
  'embed.application.internalId':
    '填写“开放集成”应用详情中的内部 Application ID。OAuth 换取 Token 使用 Client ID（client_id），二者不能互换。',
  'embed.version.cas':
    '用于防止多人或多标签页静默覆盖配置的乐观锁版本，不是 Draft / Published Revision；发生 409 时请刷新后重新修改。',
  'embed.view.key':
    '第三方创建 Launch 时提交的稳定业务标识，不是数据库 ID。对外使用后不要改名或复用为其他页面。',
  'embed.view.surface':
    'LIST 是列表容器，可开放 LIST、VIEW、CREATE；FORM 是直接表单入口，只可开放 VIEW、CREATE。',
  'embed.view.entityCode':
    '从已发布实体中选择。页面显示实体名称，系统会在配置内部保存稳定引用；后续列表和表单只会从该实体下加载。',
  'embed.view.listKey':
    '选择当前实体下已有 ACTIVE 版本的列表。页面显示列表名称，系统在内部保存稳定引用；仅 LIST 类型使用。',
  'embed.view.defaultFormId':
    '选择当前实体下已有 ACTIVE 版本的表单。LIST 中用于 VIEW / CREATE 打开记录，FORM 中就是直接嵌入的目标表单；无需填写 ID 或版本号。',
  'embed.view.entryModes':
    '决定 Launch API 允许进入哪些页面；勾选入口不会自动授予能力，还须开放对应 Capability。',
  'embed.view.capabilities':
    'View 与 Grant 能力用于约束嵌入入口和宿主 Bridge；iframe 内原生页的按钮显隐仍完全按映射 Flow 用户的普通权限、对象权限与 DataScope 计算。',
  'embed.view.fieldPolicyMode':
    'FORM 固定直接引用 Flow 已发布表单：iframe 使用同一发布态字段、布局、联动和内建控件，新 Launch 自动跟随新 Release；第三方的 viewKey 和启动接口不变，不能用 Embed 字段参数另做一份表单。',
  'embed.view.visibleFields':
    '仅 EXPLICIT 模式使用。iframe 可展示的字段必须存在于底层已发布资源，并仍受 Flow 用户权限约束。',
  'embed.view.queryableFields':
    'LIST 中可用于筛选的字段，必须由底层已发布列表声明为可查询。',
  'embed.view.writableFields':
    '仅 EXPLICIT 模式使用。CREATE 可提交的字段必须由底层已发布表单声明为可写；Context 强制值不能被浏览器覆盖。',
  'embed.view.returnableFields':
    '可经 selection.changed / form.saved 回传宿主的字段，必须是 Visible 子集，敏感字段禁止回传。',
  'embed.view.advancedJson':
    '用于 contextSchema、contextBindings、ui 等未在基础表单展开的配置。保存时，上方基础字段会覆盖 JSON 中的同名路径。',
  'embed.provider.type':
    'SIGNED_JWT（推荐）由第三方后端签名人员断言；TRUSTED_EXTERNAL_ID 仅限受信任的服务端直传，不是浏览器传用户名的快捷模式。',
  'embed.provider.subjectNamespace':
    '参与 external subject 摘要域隔离的固定命名空间，不是 JWT Claim。建议按来源系统和环境唯一命名，创建后不可修改。',
  'embed.provider.issuer':
    '必须与人员 JWT 的 iss Claim 完全一致；它不是 Flow 地址，也不是 JWKS URL。',
  'embed.provider.audiences':
    '允许的人员 JWT aud 值，每行一个，必须包含 flow-embed-launch；这里不填 OAuth Client ID。',
  'embed.provider.algorithms':
    '人员 JWT 签名算法白名单，必须与 JWK 密钥类型及 EC 曲线匹配；只开放第三方实际使用的算法。',
  'embed.provider.jwksMode':
    'STATIC_JWK_SET 由 Flow 保存公钥快照并由管理员轮换；REMOTE_JWKS 从受控 HTTPS URL 获取公钥。两种模式都不允许私钥。',
  'embed.provider.publicJwks':
    '只粘贴公开验签材料，每把 JWK 必须有唯一 kid。禁止提交 d、p、q、k 等私钥参数。',
  'embed.provider.jwksUrl':
    '填写身份提供方受控的 HTTPS JWKS 地址，返回内容只能包含公开验签材料。',
  'embed.provider.clockSkew':
    '签发方与 Flow 的时钟误差容忍，不会延长 Session；值越大，断言可被接受的时间窗口越大。',
  'embed.provider.maxAssertionLifetime':
    '限制人员 JWT 的 exp - iat，与 Clock Skew、Launch Code TTL、Session 时长都不同；生产应使用短期断言。',
  'embed.binding.identityProvider':
    '决定 External Subject 由哪套信任策略验证。Launch 只会在 Application、Provider 和 Subject 三者精确匹配时命中 Binding。',
  'embed.binding.externalSubject':
    'SIGNED_JWT 时必须与 JWT sub 精确一致。应使用稳定且不可复用的外部用户 ID，不要使用邮箱或显示名；原文不会持久化。',
  'embed.binding.flowUserId':
    'Flow 用户内部 ID，不是用户名或手机号；用户必须存在且启用，映射后仍按该用户角色、字段权限和 DataScope 鉴权。',
  'embed.binding.subjectHint':
    '仅用于管理员识别 Binding 的脱敏前后缀，不是 Subject 原文或摘要，也不能用它做精确查找。',
  'embed.grant.identityProvider':
    '限定该 Application 访问 View 时使用的人员断言来源，必须与对应 Binding 的 Provider 一致。',
  'embed.grant.trustedSubjectAssertion':
    '仅 TRUSTED_EXTERNAL_ID Provider 可启用，SIGNED_JWT 必须关闭。启用会扩大服务端信任边界，浏览器仍不得直接声明用户。',
  'embed.grant.allowedOrigins':
    '填写第三方宿主页面的 window.origin（协议、域名、可选端口），不是 Flow Embed 地址；仅允许精确 HTTPS Origin，不含路径或通配符。',
  'embed.grant.capabilityCeiling':
    '该 Application 在此 View 上的能力上限，只能收窄 View 能力，不能放大；最终仍与 Flow 用户权限和数据范围取交集。',
  'embed.grant.maxActiveSessionsPerUser':
    '同一 Grant + Flow 用户允许同时处于 ACTIVE 的 Session 数量；达到上限后，新 Launch 在兑换 Session 时会被拒绝。',
  'embed.grant.maxSessionSeconds':
    '单个 Embed Session 的绝对最长时长，不是空闲超时；实际时长还会受平台上限和授权到期时间约束。',
  'embed.grant.maxConcurrency':
    '该 Application + Grant 允许同时执行的 Runtime 请求数，不是用户数或 Session 数。',
  'embed.grant.launchRate':
    '该 Application + Grant 每分钟可签发的 Launch 数量上限；超限返回 429，宿主后端应按 Retry-After 重试。',
  'embed.grant.runtimeRate':
    '该 Application + Grant 每分钟可执行的 iframe Runtime API 请求上限；每个 Session 还受平台硬上限约束。',
  'embed.grant.expiresAt':
    '授权的绝对到期时间。到期后不再签发 Launch，已建立 Session 的后续 Runtime 请求也会被拒绝。',
  'embed.operations.queryScope':
    'Application ID 或 View ID 至少填写一项，两项同时填写时按交集查询。未选时间默认近 24 小时，最长可查 31 天。',
  'embed.operations.launchStatus':
    'ISSUED 表示尚未兑换，CONSUMED 表示已兑换为 Session，EXPIRED 表示启动码超时，REVOKED 表示已撤销。已兑换后应撤销 Session。',
  'embed.operations.sessionStatus':
    'ACTIVE 仍可使用；LOGGED_OUT 由 iframe 正常退出；EXPIRED 已超时；REVOKED 由管理员撤销。终态 Session 不能恢复。',
  'embed.operations.absoluteExpiry':
    '该 Session 无论是否持续活动都会失效的绝对时间，与最后活动时间和空闲超时不同。',
  'embed.operations.bulkScope':
    '按 View 撤销只影响该嵌入视图；按 Application 撤销影响该接入应用的所有 View。撤销不可恢复，超过 200 条需继续下一批。',
  'workCalendar.scopeType':
    '部门绑定优先于组织绑定；同一范围命中多个日历时，使用优先级更高且处于生效日期内的绑定。'
})

export function getConfigFieldHelp(helpKey) {
  return CONFIG_FIELD_HELP[String(helpKey || '').trim()] || ''
}
