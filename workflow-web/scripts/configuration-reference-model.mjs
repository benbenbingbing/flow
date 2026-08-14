import { readFileSync } from 'node:fs'
import path from 'node:path'
import { parse as parseSfc } from '@vue/compiler-sfc'
import { baseParse, NodeTypes } from '@vue/compiler-dom'

const ROOT = process.cwd()

const source = (file, domain, area, patterns) => ({
  file,
  domain,
  area,
  patterns: patterns.map(pattern => new RegExp(pattern))
})

export const CONFIGURATION_SOURCES = Object.freeze([
  source('src/views/EntityList.vue', '实体配置', '实体基础与状态', [
    '^formData\\.(entityName|entityCode|description|lifecycleMode)$',
    '^selectedProcessId$',
    '^row\\.(statusCategory|statusCode|statusName|description)$',
    '^publishMigrationForm\\.(versionDescription|markForExport|migrationTag)$'
  ]),
  source('src/views/EntityDesign.vue', '实体配置', '实体字段与数据权限', [
    '^codeRule\\.',
    '^cond\\.',
    '^entityData\\.',
    '^item\\.',
    '^optionsText$',
    '^permissionForm\\.',
    '^quickDictForm\\.',
    '^row\\.enabled$',
    '^selectedField\\.'
  ]),
  source('src/views/EntityDesign.vue', '实体配置', '数据权限模拟（验证输入，不发布）', [
    '^simulationUserId$'
  ]),
  source('src/views/EntityListConfigDesign.vue', '实体配置', '列表设计', [
    '^configInfo\\.',
    '^editingColumnConfig\\.',
    '^editingDataSourceConfig$',
    '^editingField\\.',
    '^editingQueryConfig\\.',
    '^editingRenderConfig$',
    '^listQueryEditor\\.',
    '^row\\.(enabled|fieldCode|fieldName|isQuery|showInList)$',
    '^selectedListTemplateId$',
    '^(toolbarButtons|rowActionButtons)$',
    '^viewConfig\\.'
  ]),
  source('src/components/ListButtonConfigPanel.vue', '实体配置', '列表按钮', [
    '^advancedButton\\.',
    '^openListForm\\.',
    '^row\\.(customHandler|customMode|enabled|key|label|perm|sort|type)$'
  ]),
  source('src/components/ActionRuleEditorDialog.vue', '实体配置', '按钮适用条件', [
    '^rule\\.(unavailableBehavior|message)$'
  ]),
  source('src/components/ActionRuleGroupEditor.vue', '实体配置', '按钮适用条件', [
    '^node\\.logic$',
    '^child\\.(type|relation|operator|value|field)$'
  ]),
  source('src/views/EntityFormList.vue', '实体配置', '表单定义与初始化', [
    '^form\\.',
    '^initConfigType$',
    '^initConfigData\\.'
  ]),
  source('src/views/EntityFormDesignByEntity.vue', '实体配置', '表单设计', [
    '^form\\.layoutType$',
    '^selectedComponentConfig$',
    '^selectedField\\.',
    '^viewConfig\\.customComponentProps$'
  ]),
  source('src/components/form-designer/FormDesignerSettingsDrawer.vue', '实体配置', '表单设置', [
    '^form\\.',
    '^viewConfig\\.'
  ]),
  source('src/components/form-designer/FormInputParameterEditor.vue', '实体配置', '子表单输入参数', [
    '^row\\.(name|code|type|required|defaultValue|description)$'
  ]),
  source('src/components/form-designer/FormNodeDataSettings.vue', '实体配置', '表单节点数据绑定', [
    '^selectedField\\.',
    '^selectedParameterContract$',
    '^selectedSubListParameterContract$'
  ]),
  source('src/components/ui-config/FormDataSourceCompatDialog.vue', '实体配置', '表单级数据源', [
    '^binding\\.'
  ]),
  source('src/components/FormButtonConfigPanel.vue', '实体配置', '表单按钮', [
    '^advancedButton\\.',
    '^row\\.(enabled|key|label|modes|perm|placement|slotKey|sort)$'
  ]),
  source('src/components/LinkageConfigPanel.vue', '实体配置', '表单字段联动', [
    '^config\\.',
    '^condition\\.',
    '^rule\\.'
  ]),
  source('src/components/EventConfigPanel.vue', '实体配置', '旧字段脚本事件（受限）', [
    '^eventCodes(?:\\.|\\[)',
    '^addForm\\.(name|label)$'
  ]),
  source('src/components/ui-config/EntitySelectionMappingEditor.vue', '实体配置', '实体选择后回填', [
    '^row\\.(sourcePath|targetPath|overwrite|clearOnEmpty)$'
  ]),
  source('src/views/system/EntityVersionManagement.vue', '实体配置', '实体数据版本', [
    '^draft\\.(enabled|snapshotScope\\.|diffPolicy\\.)',
    '^triggerEditor\\.',
    '^scopeEditor\\.',
    '^condition\\.(fieldCode|operator|value)$',
    '^maxSizeMb$'
  ]),
  source('src/views/system/EntityMutationPolicyManagement.vue', '实体配置', '实体变更策略', [
    '^draft\\.enabled$'
  ]),
  source('src/views/system/components/EntityVersionConfigDialogs.vue', '实体配置', '实体变更策略', [
    '^scenario\\.',
    '^step\\.',
    '^target\\.'
  ]),
  source('src/components/UiConfigPublishDialog.vue', '实体配置', '表单与列表发布', [
    '^form\\.(releaseMode|description)$'
  ]),
  source('src/views/ProcessList.vue', '流程配置', '流程定义与发布', [
    '^formData\\.',
    '^publishForm\\.'
  ]),
  source('src/components/NodeConfigPanel.vue', '流程配置', '流程节点', [
    '^advancedForm\\.',
    '^approvalForm\\.',
    '^assigneeForm\\.',
    '^basicForm\\.',
    '^callForm\\.',
    '^ccForm\\.',
    '^conditionForm\\.',
    '^formConfig\\.',
    '^manualForm\\.',
    '^option\\.',
    '^receiveForm\\.',
    '^restForm\\.',
    '^rule\\.',
    '^ruleForm\\.',
    '^sendForm\\.',
    '^serviceForm\\.',
    '^slaForm\\.',
    '^statusForm\\.'
  ]),
  source('src/components/FlowConditionGroupEditor.vue', '流程配置', '流程条件', [
    '^group\\.logic$',
    '^child\\.(property|operator|value)$'
  ]),
  source('src/components/FlowActionConfigPanel.vue', '流程配置', '流程动作', [
    '^editingAction\\.',
    '^param\\.',
    '^retryForm\\.maxRetries$',
    '^selectedTemplate$'
  ]),
  source('src/components/FlowActionHandlerConfigDialog.vue', '流程配置', '流程动作扩展定义', [
    '^row\\.(displayName|description|visibilityScope|entityCodes|enabled)$'
  ]),
  source('src/components/ui-config/EventBindingEditor.vue', '流程配置', '事件绑定', [
    '^editor\\.',
    '^step\\.'
  ]),
  source('src/views/process/TaskSlaPolicyManagement.vue', '流程配置', '任务 SLA', [
    '^form\\.',
    '^row\\.'
  ]),
  source('src/views/system/WorkCalendarManagement.vue', '流程配置', '工作日历', [
    '^form\\.',
    '^effectiveRange$',
    '^row\\.'
  ])
])

export const IGNORED_UI_BINDINGS = Object.freeze({
  'src/views/EntityList.vue': [
    '^queryParams\\.', '^dialogVisible$', '^bindDialogVisible$', '^statusDialogVisible$',
    '^historyDialogVisible$', '^versionDiffDialogVisible$', '^publishDiffDialogVisible$'
  ],
  'src/views/EntityDesign.vue': [
    '^showSystemFields$', '^codeRuleVisible$', '^permissionVisible$', '^permissionEditVisible$',
    '^permissionSqlPreviewVisible$', '^permissionSqlPreview\\.sql$', '^quickDictVisible$'
  ],
  'src/views/EntityListConfigDesign.vue': [
    '^activeConfigTab$', '^previewDialogVisible$', '^previewViewport$', '^previewQueryForm$',
    '^previewPageNum$', '^fieldConfigDialogVisible$', '^activeFieldConfigTab$', '^publishDialogVisible$'
  ],
  'src/components/ListButtonConfigPanel.vue': [
    '^advancedDialogVisible$', '^openListDialogVisible$', '^buttonEventDialogVisible$'
  ],
  'src/components/ActionRuleEditorDialog.vue': [
    '^visible$', '^preset$'
  ],
  'src/views/EntityFormList.vue': [
    '^dialogVisible$', '^previewVisible$', '^initConfigVisible$'
  ],
  'src/views/EntityFormDesignByEntity.vue': [
    '^fieldSearch$', '^propertyDrawerVisible$', '^activeNodeSettingsTab$',
    '^activeNodeInteractionTab$', '^showFormSettings$', '^showPreview$', '^previewMode$',
    '^showLinkageConfig$', '^showEventConfig$', '^showFormExtensionConfig$', '^publishDialogVisible$'
  ],
  'src/components/form-designer/FormDesignerSettingsDrawer.vue': [
    '^drawerVisible$', '^currentTab$', '^activeBehaviorTab$'
  ],
  'src/components/ui-config/FormDataSourceCompatDialog.vue': [
    '^visible$'
  ],
  'src/components/FormButtonConfigPanel.vue': [
    '^activeMode$', '^advancedVisible$'
  ],
  'src/components/LinkageConfigPanel.vue': [
    '^activeTab$'
  ],
  'src/components/EventConfigPanel.vue': [
    '^dialogVisible$', '^activeTab$', '^showAddEvent$'
  ],
  'src/views/system/EntityVersionManagement.vue': [
    '^keyword$', '^drawerVisible$', '^activeTab$', '^releasePage$',
    '^triggerDialogVisible$', '^scopeDialogVisible$', '^previewVisible$',
    '^previewRecordId$'
  ],
  'src/views/system/EntityMutationPolicyManagement.vue': [
    '^keyword$', '^drawerVisible$', '^activeTab$', '^scenarioDialogVisible$',
    '^stepDialogVisible$', '^targetDialogVisible$', '^pickerVisible$',
    '^pickerKeyword$', '^pickerPage$'
  ],
  'src/components/UiConfigPublishDialog.vue': [],
  'src/views/ProcessList.vue': [
    '^queryParams\\.', '^dialogVisible$', '^versionDialogVisible$', '^versionActionsVisible$',
    '^versionDetailVisible$', '^publishDialogVisible$'
  ],
  'src/components/NodeConfigPanel.vue': [
    '^selectedStatusName$'
  ],
  'src/components/FlowActionConfigPanel.vue': [
    '^actionDialogVisible$'
  ],
  'src/components/FlowActionHandlerConfigDialog.vue': [
    '^visible$'
  ],
  'src/components/ui-config/EventBindingEditor.vue': [
    '^dialogVisible$'
  ],
  'src/views/process/TaskSlaPolicyManagement.vue': [
    '^dialogVisible$'
  ],
  'src/views/system/WorkCalendarManagement.vue': [
    '^dialogVisible$'
  ]
})

export const AUTHORITATIVE_ENUMS = Object.freeze([
  {
    domain: '实体配置',
    area: '实体字段类型',
    source: 'src/shared/entity-design/index.js',
    values: [
      ['STRING', '单行文本', '短文本、编号、名称'],
      ['TEXT', '多行文本', '备注、说明等长文本'],
      ['RICH_TEXT', '富文本', '需要格式、图片或链接的正文'],
      ['INTEGER', '整数', '人数、数量、序号'],
      ['DECIMAL', '小数', '金额、比例、计量值'],
      ['DATE', '日期', '只关心年月日的日期'],
      ['DATETIME', '日期时间', '需要精确到时间的业务时刻'],
      ['BOOLEAN', '布尔', '是/否、开启/关闭'],
      ['SELECT', '单选下拉', '从固定或字典选项中选一项'],
      ['MULTI_SELECT', '多选下拉', '从选项中选择多项'],
      ['RADIO', '单选按钮', '选项较少且需要平铺展示'],
      ['CHECKBOX', '复选框', '选项较少且允许多选'],
      ['FILE', '文件', '上传通用附件'],
      ['IMAGE', '图片', '上传并预览图片'],
      ['USER', '用户', '选择平台用户'],
      ['DEPT', '部门', '选择组织部门'],
      ['REFERENCE', '单选实体', '引用另一实体的一条记录，保存其 ID'],
      ['MULTI_REFERENCE', '多选实体', '引用另一实体的多条记录'],
      ['SUB_FORM', '子表单', '嵌入一条结构化子记录'],
      ['SUB_LIST', '子列表', '嵌入其他实体的已发布列表']
    ]
  },
  {
    domain: '实体配置',
    area: '表单节点类型',
    source: 'src/shared/form-node-property-schema.js',
    values: [
      ['SECTION', '分组', '组织一组相关字段'],
      ['GRID', '栅格', '按列宽布局子节点'],
      ['TAB_SET', '页签组', '承载多个页签'],
      ['TAB', '页签', '切换显示一组内容'],
      ['COLLAPSE', '折叠面板', '按需展开低频内容'],
      ['TEXT', '说明文本', '显示只读提示或说明'],
      ['FIELD', '实体字段', '编辑或展示一个实体字段'],
      ['SUB_FORM', '子表单', '嵌入单条子表单'],
      ['REPEATER', '重复器', '编辑多条子表单数据'],
      ['ACTION_SLOT', '动作插槽', '承载当前表单的自定义动作按钮']
    ]
  },
  {
    domain: '流程配置',
    area: 'BPMN 节点类型',
    source: 'src/shared/process-config/index.js',
    values: [
      ['bpmn:UserTask', '用户任务', '需要人员办理、审批或填写表单'],
      ['bpmn:ServiceTask', '服务任务', '由 Java、表达式或受控 REST 服务自动执行'],
      ['bpmn:SendTask', '发送任务', '发送站内信、邮件等通知'],
      ['bpmn:ReceiveTask', '接收任务', '等待外部消息或超时'],
      ['bpmn:ManualTask', '人工任务', '记录线下人工工作，不由引擎自动校验完成条件'],
      ['bpmn:BusinessRuleTask', '业务规则任务', '调用决策表或规则服务'],
      ['bpmn:ScriptTask', '脚本任务', '当前产品禁用，避免执行任意脚本'],
      ['bpmn:CallActivity', '调用活动', '复用另一流程或子流程'],
      ['bpmn:SubProcess', '子流程', '在当前定义中组织局部流程'],
      ['bpmn:ExclusiveGateway', '排他网关', '多个条件分支中只走一条'],
      ['bpmn:ParallelGateway', '并行网关', '同时拆分或汇聚并行路径'],
      ['bpmn:InclusiveGateway', '包容网关', '允许命中多条条件分支'],
      ['bpmn:EventBasedGateway', '事件网关', '由先发生的事件决定路径'],
      ['bpmn:SequenceFlow', '顺序流', '连接节点并可承载条件'],
      ['bpmn:StartEvent', '开始事件', '流程实例入口'],
      ['bpmn:EndEvent', '结束事件', '流程实例出口']
    ]
  },
  {
    domain: '实体配置',
    area: '实体生命周期模式',
    source: 'src/views/EntityList.vue',
    values: [
      ['STANDALONE', '独立业务实体', '不绑定流程，适合基础资料、主数据或仅通过表单和列表维护的数据。'],
      ['WORKFLOW', '流程实体', '允许绑定一个流程定义，记录可发起流程并跟随流程状态变化。']
    ]
  },
  {
    domain: '实体配置',
    area: '实体状态分类',
    source: 'src/views/EntityList.vue',
    values: [
      ['NEW', '初始', '草稿或尚未进入处理阶段的数据。'],
      ['PROCESSING', '处理中', '流程运行中或业务正在处理的数据。'],
      ['COMPLETED', '已完成', '流程或业务处理已正常结束的数据。'],
      ['TERMINATED', '已终止', '流程被终止或业务被取消且不再继续的数据。'],
      ['WITHDRAWN', '已撤回', '由发起人撤回的流程数据。']
    ]
  },
  {
    domain: '实体配置',
    area: '团队可见性覆盖级别',
    source: 'src/views/EntityDesign.vue',
    values: [
      ['ADDITIVE', '附加授权', '团队成员获得额外可见数据，但列表收窄和拒绝规则仍然生效。'],
      ['OVERRIDE_SCOPE', '覆盖普通数据范围', '团队授权可越过普通数据范围，但明确拒绝规则仍然生效。'],
      ['ABSOLUTE', '绝对参与授权', '团队授权还能覆盖业务拒绝，权限最强，仅用于明确的协作场景。']
    ]
  },
  {
    domain: '实体配置',
    area: '数据权限规则效果',
    source: 'src/views/EntityDesign.vue',
    values: [
      ['ALLOW', '允许', '命中对象可访问规则计算出的数据范围。'],
      ['DENY', '拒绝', '命中对象从最终结果中排除规则计算出的数据范围。']
    ]
  },
  {
    domain: '实体配置',
    area: '数据权限条件逻辑',
    source: 'src/views/EntityDesign.vue',
    values: [
      ['OR', '满足任一', '用户命中任意一个适用对象条件即应用该规则。'],
      ['AND', '满足全部', '用户必须同时命中所有适用对象条件才应用该规则。']
    ]
  },
  {
    domain: '实体配置',
    area: '列表数据范围模式',
    source: 'src/views/EntityListConfigDesign.vue',
    values: [
      ['INHERIT', '继承实体默认范围', '列表直接使用实体级数据权限；系统实体强制采用此模式。'],
      ['NARROW', '在实体范围内缩小', '列表只能在实体已允许的数据中进一步收窄，不能扩大权限。'],
      ['OVERRIDE', '使用列表独立范围', '列表使用独立规则，属于高风险配置，必须单独验证权限边界。']
    ]
  },
  {
    domain: '实体配置',
    area: '列表选择模式',
    source: 'src/views/EntityListConfigDesign.vue',
    values: [
      ['NONE', '不选择', '列表只用于浏览，不显示选择确认能力。'],
      ['SINGLE', '单选', '调用方只能选择一条记录，并按返回值字段和映射返回。'],
      ['MULTIPLE', '多选', '调用方可选择多条记录，返回数组结果。']
    ]
  },
  {
    domain: '实体配置',
    area: '按钮条件不满足策略',
    source: 'src/components/ActionRuleEditorDialog.vue',
    values: [
      ['HIDE', '隐藏按钮', '条件不满足时按钮完全不展示。'],
      ['DISABLE', '禁用并说明', '按钮保留但不可点击，并展示配置的禁用原因。']
    ]
  },
  {
    domain: '实体配置',
    area: '表单运行模式',
    source: 'src/shared/form-actions.js',
    values: [
      ['create', '新增', '创建新记录，字段采用新增模式权限，默认有取消、重置和保存。'],
      ['edit', '编辑', '修改已有记录，字段采用编辑模式权限，默认有取消、重置和保存修改。'],
      ['view', '查看', '只读查看已有记录，默认只显示关闭。'],
      ['approve', '审批', '办理流程任务，字段采用审批模式权限并显示提交审批。']
    ]
  },
  {
    domain: '实体配置',
    area: '表单内置按钮',
    source: 'src/shared/form-actions.js',
    values: [
      ['close', '关闭 / 取消', '新增和编辑时取消操作，查看和审批时关闭窗口。'],
      ['reset', '重置', '把新增或编辑表单恢复到本次打开或初始化后的值。'],
      ['save', '保存', '校验并保存记录，但不发起流程。'],
      ['saveAndStart', '保存并发起流程', '校验并保存后发起已发布且可启动的流程。'],
      ['submitApproval', '提交审批', '使用流程节点配置的审批结果和意见完成当前任务。']
    ]
  },
  {
    domain: '实体配置',
    area: '表单与列表发布方式',
    source: 'src/components/UiConfigPublishDialog.vue',
    values: [
      ['STANDARD', '普通发布', '表单需重新发布流程才进入新的流程快照；列表会切换全局生效版本。'],
      ['HOTFIX', '兼容热修复', '经过影响预检后兼容修复可作用于允许的活动和未来范围，并保留撤回审计。']
    ]
  },
  {
    domain: '实体配置',
    area: '统一事件继承方式',
    source: 'src/components/ui-config/EventBindingEditor.vue',
    values: [
      ['INHERIT', '继承并追加', '保留上级或平台处理，并按步骤位置合并当前事件链。'],
      ['REPLACE', '替换上级', '当前层自定义链替换上级自定义链；平台处理是否被替代由步骤策略决定。'],
      ['DISABLE', '禁用自定义', '当前层不执行自定义步骤，只保留平台默认处理。']
    ]
  },
  {
    domain: '实体配置',
    area: '统一事件步骤位置',
    source: 'src/components/ui-config/EventBindingEditor.vue',
    values: [
      ['BEFORE', '前置', '在平台默认处理前执行接口或字段映射。'],
      ['REPLACE', '替代平台处理', '用一个替代步骤取代平台默认处理；同一事件链最多保留一个。'],
      ['AFTER', '后置', '平台默认处理成功后再执行接口或字段映射。']
    ]
  },
  {
    domain: '实体配置',
    area: '统一事件失败策略',
    source: 'src/components/ui-config/EventBindingEditor.vue',
    values: [
      ['STOP', '停止执行', '当前步骤失败后终止事件链并返回错误。'],
      ['CONTINUE', '记录后继续', '记录失败信息并继续后续步骤。'],
      ['EMPTY', '按空结果继续', '把失败步骤视为空结果，继续执行输出映射和后续步骤。']
    ]
  },
  {
    domain: '流程配置',
    area: '用户任务办理人方式',
    source: 'src/components/NodeConfigPanel.vue',
    values: [
      ['user', '固定人员', '直接指定办理人或候选用户。'],
      ['group', '用户组', '用户组成员成为候选办理人。'],
      ['role', '角色', '拥有所选角色的用户成为候选办理人。'],
      ['expression', '表达式', '从流程变量表达式解析办理人或候选组。'],
      ['interface', '接口动态', '调用已注册人员解析器计算办理人。']
    ]
  },
  {
    domain: '流程配置',
    area: '服务任务实现方式',
    source: 'src/components/NodeConfigPanel.vue',
    values: [
      ['class', 'Java 类', 'Flowable 直接实例化并执行配置的 JavaDelegate 类。'],
      ['expression', '表达式', '执行受引擎支持的流程表达式。'],
      ['delegateExpression', 'Spring Bean', '通过受管理的 Spring Bean 表达式执行服务。'],
      ['rest', 'REST 接口', '由平台配置代理按 URL、方法、超时、重试和映射调用 HTTP 接口。']
    ]
  },
  {
    domain: '流程配置',
    area: '顺序流条件类型',
    source: 'src/components/NodeConfigPanel.vue',
    values: [
      ['', '无条件', '令牌到达时直接通过该连线；排他分支一般不应配置多条无条件线。'],
      ['expression', '表达式', '条件组生成表达式，计算为真时通过该连线。'],
      ['default', '默认流', '其他条件均未命中时使用；一个排他网关只能有一条默认流。']
    ]
  },
  {
    domain: '流程配置',
    area: '知会触发时机',
    source: 'src/components/NodeConfigPanel.vue',
    values: [
      ['TASK_CREATE', '任务创建时', '用户任务创建后生成自动知会。'],
      ['TASK_COMPLETE', '任务完成时', '用户任务完成后生成自动知会。'],
      ['EXPLICIT', '执行到知会节点', '服务任务或发送任务执行时显式生成知会。']
    ]
  },
  {
    domain: '流程配置',
    area: '知会收件人规则',
    source: 'src/components/NodeConfigPanel.vue',
    values: [
      ['USER', '固定用户', '向明确选择的用户发送知会。'],
      ['ROLE', '角色成员', '向所选角色的当前成员发送知会。'],
      ['GROUP', '用户组成员', '向所选用户组的当前成员发送知会。'],
      ['DEPARTMENT', '组织 / 部门成员', '向所选组织或部门成员发送，可选择包含下级。'],
      ['STARTER', '流程发起人', '向当前流程实例发起人发送知会。'],
      ['CURRENT_ASSIGNEE', '当前办理人', '向触发时的当前任务办理人发送知会。'],
      ['HISTORY_APPROVERS', '历史办理人', '向当前流程已完成任务的历史办理人发送知会。'],
      ['ENTITY_FIELD', '实体字段用户', '从绑定实体的用户字段读取收件人。'],
      ['RESOLVER', '受控解析器', '调用已注册人员解析器按上下文计算收件人。']
    ]
  },
  {
    domain: '流程配置',
    area: '流程动作执行方式',
    source: 'src/components/FlowActionConfigPanel.vue',
    values: [
      ['IN_TRANSACTION', '事务内执行', '动作与当前流程操作处于同一事务，失败可按策略回滚。'],
      ['AFTER_COMMIT', '提交后执行', '主事务提交后异步执行，适合通知和外部接口。']
    ]
  },
  {
    domain: '流程配置',
    area: '流程动作失败策略',
    source: 'src/components/FlowActionConfigPanel.vue',
    values: [
      ['ROLLBACK', '失败回滚流程', '事务内动作失败时回滚当前流程操作。'],
      ['CONTINUE', '记录失败后继续', '事务内记录错误但不阻止流程继续。'],
      ['RETRY', '失败自动重试', '提交后动作按重试配置调度，超过次数进入失败记录。'],
      ['IGNORE', '记录失败后忽略', '提交后动作记录失败但不再自动重试。']
    ]
  },
  {
    domain: '实体配置',
    area: '数据版本变更入口',
    source: 'src/views/system/EntityVersionManagement.vue',
    values: [
      ['FORM', '表单', '表单新增或编辑产生的数据变更。'],
      ['LIST', '列表', '列表内置或自定义动作产生的数据变更。'],
      ['APPROVAL_TASK', '审批', '办理任务时产生的数据变更。'],
      ['PROCESS_RUNTIME', '流程运行态', '流程监听器或状态同步产生的数据变更。'],
      ['FLOW_ACTION', '流程动作', '流程动作处理器产生的数据变更。'],
      ['CUSTOM_INTERFACE', '自定义接口', '受管理业务接口产生的数据变更。'],
      ['BATCH', '批量', '批量处理产生的数据变更。'],
      ['IMPORT', '导入', '数据导入产生的变更。'],
      ['SCHEDULED_JOB', '定时任务', '定时作业产生的数据变更。'],
      ['MESSAGE_CONSUMER', '消息消费', '消息消费者产生的数据变更。'],
      ['SYSTEM_TASK', '系统任务', '系统内部任务产生的数据变更。']
    ]
  },
  {
    domain: '实体配置',
    area: '数据版本操作类型',
    source: 'src/views/system/EntityVersionManagement.vue',
    values: [
      ['CREATE', '新增', '新建记录时匹配场景。'],
      ['UPDATE', '修改', '更新已有记录时匹配场景。'],
      ['DELETE', '删除', '删除记录时匹配场景。'],
      ['STATUS_CHANGE', '状态变化', '实体状态发生变化时匹配场景。'],
      ['APPLY_CHANGE', '变更生效', '审批后的数据变更正式应用时匹配场景。'],
      ['UPSERT', '新增或修改', '无法预先区分新增和更新的幂等写入时匹配场景。']
    ]
  },
  {
    domain: '实体配置',
    area: '数据版本执行阶段',
    source: 'src/views/system/EntityMutationPolicyManagement.vue',
    values: [
      ['PREPARE', '准备', '写库前准备上下文或默认数据。'],
      ['BEFORE_WRITE', '写入前', '正式写库前执行校验、表达式或映射。'],
      ['AFTER_WRITE', '写入后', '记录写入后、事务提交前执行后续处理。'],
      ['AFTER_COMMIT', '提交后', '事务成功提交后执行异步或外部副作用。']
    ]
  },
  {
    domain: '实体配置',
    area: '数据版本步骤类型',
    source: 'src/views/system/EntityMutationPolicyManagement.vue',
    values: [
      ['BUILT_IN_RULE', '内置规则', '执行平台登记的内置规则，保存前必须选择有效的规则实现。'],
      ['EXPRESSION', '条件表达式', '执行受控表达式计算或判断。'],
      ['FIELD_MAPPING', '字段映射', '按结构化映射转换当前记录或上下文；仅允许 PREPARE 或 BEFORE_WRITE。'],
      ['MANAGED_INTERFACE', '受管理接口', '调用平台登记的接口服务；仅允许 PREPARE。'],
      ['JAVA_PROVIDER', 'Java Provider', '调用已注册的 Java 扩展实现；执行阶段必须属于 Provider 的 supportedPhases。']
    ]
  },
  {
    domain: '实体配置',
    area: '数据版本目标解析方式',
    source: 'src/views/system/EntityMutationPolicyManagement.vue',
    values: [
      ['FIELD', '引用字段', '从来源记录的引用字段取得目标记录 ID。'],
      ['RELATION', '实体关系', '通过已定义实体关系查找目标记录。'],
      ['JAVA_PROVIDER', 'Java Provider', '调用已注册目标解析器计算目标记录。']
    ]
  },
  {
    domain: '实体配置',
    area: '数据版本目标应用策略',
    source: 'src/views/system/components/EntityVersionConfigDialogs.vue',
    values: [
      ['MERGE', '合并', '只更新映射得到的目标字段，保留目标其他字段。'],
      ['REPLACE', '替换', '按配置替换目标数据内容，未提供字段可能被清除。']
    ]
  },
  {
    domain: '流程配置',
    area: 'SLA 时间口径',
    source: 'src/views/process/TaskSlaPolicyManagement.vue',
    values: [
      ['WORKING_TIME', '工作时间', '仅在解析到的工作日历时段内累计分钟。'],
      ['NATURAL_TIME', '自然时间', '连续累计自然分钟，不受工作日历和节假日影响。']
    ]
  },
  {
    domain: '流程配置',
    area: 'SLA 指标',
    source: 'src/views/process/TaskSlaPolicyManagement.vue',
    values: [
      ['RESPONSE', '首次响应', '以任务首次有效响应为达成点。'],
      ['COMPLETION', '办结', '以任务完成为达成点。']
    ]
  },
  {
    domain: '流程配置',
    area: 'SLA 升级触发点',
    source: 'src/views/process/TaskSlaPolicyManagement.vue',
    values: [
      ['BEFORE_DUE', '到期前', '在截止时间前按偏移分钟提前执行动作。'],
      ['AT_DUE', '到期时', '到达截止时间时执行动作。'],
      ['AFTER_DUE', '到期后', '超过截止时间后按偏移分钟执行动作。']
    ]
  },
  {
    domain: '流程配置',
    area: 'SLA 升级动作',
    source: 'src/views/process/TaskSlaPolicyManagement.vue',
    values: [
      ['NOTIFY', '提醒当前人', '向当前任务办理人发送提醒。'],
      ['NOTIFY_MANAGER', '提醒上级', '解析办理人上级并发送提醒。'],
      ['ADD_CC', '增加知会', '按接收人配置新增知会记录。'],
      ['TRANSFER', '自动转办', '按目标配置把任务转交给新办理人。'],
      ['ADD_SIGN', '自动加签', '按目标配置增加办理人。']
    ]
  },
  {
    domain: '流程配置',
    area: '工作日历作用域',
    source: 'src/views/system/WorkCalendarManagement.vue',
    values: [
      ['DEPARTMENT', '部门', '日历绑定到指定部门；解析时优先于组织绑定。'],
      ['ORGANIZATION', '组织', '日历绑定到指定组织，作为组织范围的工作时间。']
    ]
  }
])

export const KNOWN_LIMITATIONS = Object.freeze([
  {
    id: 'process.script-task.disabled',
    domain: '流程配置',
    area: 'BPMN 节点类型',
    setting: '脚本任务',
    location: '流程配置-流程-设计-节点工具栏',
    status: '当前不可配置',
    reason: '设计器明确禁用脚本任务，避免在流程定义中执行任意脚本。',
    recommendation: '使用受管理的服务任务、接口服务或流程动作扩展。'
  },
  {
    id: 'entity.system-entity.readonly',
    domain: '实体配置',
    area: '系统实体',
    setting: '写操作与动作插槽',
    location: '实体配置-系统实体-表单与列表',
    status: '按约定不可配置',
    reason: '系统实体只开放受信任的只读列表与查看能力。',
    recommendation: '业务写入请通过对应系统管理页面或受管理接口完成。'
  },
  {
    id: 'entity.permission.create-by',
    domain: '实体配置',
    area: '数据权限',
    setting: '创建人字段',
    location: '实体配置-实体-设计-数据权限',
    status: '历史问题已关闭',
    reason: '动态业务表实际字段为 create_by；前端默认值、SQL 构建器和兼容归一化当前均使用 create_by。',
    recommendation: '新配置使用 create_by；旧配置写成 created_by 时由后端归一化。'
  },
  {
    id: 'process.auto-skip.runtime-arrival',
    domain: '流程配置',
    area: '节点自动跳过',
    setting: '运行时节点识别',
    location: '流程配置-流程-设计-节点属性-高级-自动跳过',
    status: '历史问题已关闭',
    reason: '当前后端监听 ACTIVITY_STARTED，按令牌实际到达节点处理，不再依赖首任务 BFS 推断。',
    recommendation: '在排他、并行和子流程场景仍需配置明确的跳过表达式并做发布前模拟。'
  },
  {
    id: 'entity.legacy-field-script-events',
    domain: '实体配置',
    area: '表单字段事件',
    setting: 'componentProps.events 脚本',
    location: '实体配置-表单-编辑-字段属性-事件',
    status: '仅兼容受信任开发配置',
    reason: '旧事件编辑器保存前端代码，仅用于历史受信任脚本兼容，不适合普通管理员或租户配置。',
    recommendation: '新增逻辑使用结构化联动、统一事件绑定或已注册组件；不要新增任意前端脚本。'
  }
])

const DEFAULT_LOCATION_BY_AREA = Object.freeze({
  '实体基础与状态': '实体配置-实体',
  '实体字段与数据权限': '实体配置-实体-设计',
  '数据权限模拟（验证输入，不发布）': '实体配置-实体-设计-数据权限-模拟',
  '列表设计': '实体配置-列表-编辑',
  '列表按钮': '实体配置-列表-编辑-按钮配置',
  '列表按钮结构': '实体配置-列表-编辑-按钮配置',
  '按钮适用条件': '实体配置-列表或表单-按钮配置-适用条件',
  '表单定义与初始化': '实体配置-表单-编辑',
  '表单设计': '实体配置-表单-编辑-设计器',
  '表单设置': '实体配置-表单-编辑-表单设置',
  '表单节点结构化属性': '实体配置-表单-编辑-节点属性',
  '表单节点数据绑定': '实体配置-表单-编辑-节点属性-数据设置',
  '表单级数据源': '实体配置-表单-编辑-表单设置-数据与事件-数据源',
  '表单按钮': '实体配置-表单-编辑-表单设置-按钮与操作',
  '表单操作栏结构': '实体配置-表单-编辑-表单设置-按钮与操作',
  '表单字段联动': '实体配置-表单-编辑-字段属性-交互与规则',
  '表单字段校验': '实体配置-表单-编辑-字段属性-校验规则',
  '表单字段运行模式权限': '实体配置-表单-编辑-字段属性-模式权限',
  '旧字段脚本事件（受限）': '实体配置-表单-编辑-字段属性-事件',
  '实体选择后回填': '实体配置-表单-编辑-单选实体属性-选择后回填',
  '实体数据版本': '实体配置-数据版本',
  '实体变更策略': '实体配置-实体变更策略',
  '表单与列表发布': '实体配置-表单或列表-编辑-发布',
  '流程定义与发布': '流程配置-流程',
  '流程节点': '流程配置-流程-设计-节点属性',
  '流程条件': '流程配置-流程-设计-连线条件',
  '审批选项': '流程配置-流程-设计-用户任务-审批设置',
  '流程动作': '流程配置-流程-设计-节点属性-流程动作',
  '流程动作扩展定义': '系统管理-扩展管理-流程动作处理器',
  '事件绑定': '流程配置-流程-设计-事件绑定',
  '任务 SLA': '流程配置-任务 SLA-策略管理',
  '工作日历': '流程配置-工作日历'
})

const locationRule = (file, pattern, location) => Object.freeze({
  file,
  pattern: new RegExp(pattern),
  location
})

const LOCATION_RULES = Object.freeze([
  locationRule('src/views/EntityList.vue', '^formData\\.', '实体配置-实体-新建/编辑'),
  locationRule('src/views/EntityList.vue', '^selectedProcessId$', '实体配置-实体-绑定流程'),
  locationRule('src/views/EntityList.vue', '^row\\.', '实体配置-实体-状态配置'),
  locationRule('src/views/EntityList.vue', '^publishMigrationForm\\.', '实体配置-实体-发布-发布与迁移'),

  locationRule('src/views/EntityDesign.vue', '^selectedField\\.(fieldName|fieldCode|fieldType|isRequired|isUnique|defaultValue|fieldLength|fieldPrecision)$', '实体配置-实体-设计-字段属性-基础信息'),
  locationRule('src/views/EntityDesign.vue', '^(selectedField\\.(optionSource|dictType)|optionsText|quickDictForm\\.)', '实体配置-实体-设计-字段属性-代码表与选项'),
  locationRule('src/views/EntityDesign.vue', '^selectedField\\.(validateRules)$', '实体配置-实体-设计-字段属性-校验规则'),
  locationRule('src/views/EntityDesign.vue', '^selectedField\\.(relationType|childEntityId|childRefFieldCode|cascadeDelete)$', '实体配置-实体-设计-字段属性-子表单'),
  locationRule('src/views/EntityDesign.vue', '^item\\.', '实体配置-实体-设计-字段属性-文件上传'),
  locationRule('src/views/EntityDesign.vue', '^selectedField\\.(refEntityId|refFieldCode)$', '实体配置-实体-设计-字段属性-实体引用'),
  locationRule('src/views/EntityDesign.vue', '^codeRule\\.', '实体配置-实体-设计-编码规则'),
  locationRule('src/views/EntityDesign.vue', '^entityData\\.teamVisibility', '实体配置-实体-设计-团队可见性'),
  locationRule('src/views/EntityDesign.vue', '^(permissionForm\\.|cond\\.|row\\.enabled$)', '实体配置-实体-设计-数据权限-规则编辑'),
  locationRule('src/views/EntityDesign.vue', '^simulationUserId$', '实体配置-实体-设计-数据权限-权限 SQL 预览'),

  locationRule('src/views/EntityListConfigDesign.vue', '^row\\.', '实体配置-列表-编辑-字段配置'),
  locationRule('src/views/EntityListConfigDesign.vue', '^viewConfig\\.search\\.', '实体配置-列表-编辑-列表设置-常用体验-查询区'),
  locationRule('src/views/EntityListConfigDesign.vue', '^viewConfig\\.(table|pagination)\\.', '实体配置-列表-编辑-列表设置-常用体验-表格'),
  locationRule('src/views/EntityListConfigDesign.vue', '^configInfo\\.(dataScopeMode|accessPermissionCode)', '实体配置-列表-编辑-列表设置-访问范围'),
  locationRule('src/views/EntityListConfigDesign.vue', '^configInfo\\.selection', '实体配置-列表-编辑-列表设置-选择行为'),
  locationRule('src/views/EntityListConfigDesign.vue', '^configInfo\\.(fixedFilterConfig|contextBindingConfig|queryProviderCode|queryDataSourceId|queryOperationCode)', '实体配置-列表-编辑-列表设置-查询实现'),
  locationRule('src/views/EntityListConfigDesign.vue', '^(configInfo\\.customComponent|viewConfig\\.customComponentProps)', '实体配置-列表-编辑-列表设置-扩展渲染'),
  locationRule('src/views/EntityListConfigDesign.vue', '^toolbarButtons$', '实体配置-列表-编辑-工具栏按钮'),
  locationRule('src/views/EntityListConfigDesign.vue', '^rowActionButtons$', '实体配置-列表-编辑-操作列按钮'),
  locationRule('src/views/EntityListConfigDesign.vue', '^(editingQueryConfig\\.|editingField\\.(queryType))', '实体配置-列表-编辑-字段高级配置-常用-查询项'),
  locationRule('src/views/EntityListConfigDesign.vue', '^editingField\\.(width|align)$', '实体配置-列表-编辑-字段高级配置-常用-列展示'),
  locationRule('src/views/EntityListConfigDesign.vue', '^editingColumnConfig\\.', '实体配置-列表-编辑-字段高级配置-常用-高级列布局'),
  locationRule('src/views/EntityListConfigDesign.vue', '^(editingDataSourceConfig|editingRenderConfig|editingField\\.(dataSourceType|dataSourceId|renderComponent|templateId))', '实体配置-列表-编辑-字段高级配置-数据与显示'),

  locationRule('src/components/ListButtonConfigPanel.vue', '^row\\.', '实体配置-列表-编辑-工具栏或操作列按钮'),
  locationRule('src/components/ListButtonConfigPanel.vue', '^advancedButton\\.', '实体配置-列表-编辑-按钮配置-高级设置'),
  locationRule('src/components/ListButtonConfigPanel.vue', '^openListForm\\.', '实体配置-列表-编辑-按钮配置-打开实体列表'),

  locationRule('src/views/EntityFormList.vue', '^form\\.', '实体配置-表单-编辑'),
  locationRule('src/views/EntityFormList.vue', '^(initConfigType|initConfigData\\.)', '实体配置-表单-编辑-初始化配置'),

  locationRule('src/views/EntityFormDesignByEntity.vue', '^form\\.layoutType$', '实体配置-表单-编辑'),
  locationRule('src/views/EntityFormDesignByEntity.vue', '^selectedField\\.(fieldLabel|componentType|placeholder|gridSpan|isRequired|isReadonly|isHidden)$', '实体配置-表单-编辑-字段属性-常用'),
  locationRule('src/views/EntityFormDesignByEntity.vue', '^selectedField\\.(componentName|templateId)$', '实体配置-表单-编辑-字段属性-复用与扩展'),
  locationRule('src/views/EntityFormDesignByEntity.vue', '^(selectedComponentConfig|viewConfig\\.customComponentProps)$', '实体配置-表单-编辑-字段属性-复用与扩展-组件参数'),

  locationRule('src/components/form-designer/FormDesignerSettingsDrawer.vue', '^viewConfig\\.actionBar$', '实体配置-表单-编辑-表单设置-按钮与操作'),
  locationRule('src/components/form-designer/FormDesignerSettingsDrawer.vue', '^viewConfig\\.inputParameterSchema$', '实体配置-表单-编辑-表单设置-数据与事件-输入参数'),
  locationRule('src/components/form-designer/FormDesignerSettingsDrawer.vue', '^form\\.customComponent$', '实体配置-表单-编辑-表单设置-渲染与扩展'),
  locationRule('src/components/form-designer/FormDesignerSettingsDrawer.vue', '^(form\\.|viewConfig\\.labelWidth$)', '实体配置-表单-编辑-表单设置-基本与布局'),
  locationRule('src/components/form-designer/FormInputParameterEditor.vue', '^row\\.', '实体配置-表单-编辑-表单设置-数据与事件-输入参数'),
  locationRule('src/components/form-designer/FormNodeDataSettings.vue', '^selectedParameterContract$', '实体配置-表单-编辑-字段属性-数据与关系-参数传递'),

  locationRule('src/components/LinkageConfigPanel.vue', '^(config\\.visibility|condition\\.)', '实体配置-表单-编辑-字段属性-交互与规则-显示条件'),
  locationRule('src/components/LinkageConfigPanel.vue', '^config\\.disabled', '实体配置-表单-编辑-字段属性-交互与规则-禁用条件'),
  locationRule('src/components/LinkageConfigPanel.vue', '^config\\.required', '实体配置-表单-编辑-字段属性-交互与规则-必填条件'),
  locationRule('src/components/LinkageConfigPanel.vue', '^(config\\.(value|sourceField|api)|rule\\.(sourceValue|targetValue))', '实体配置-表单-编辑-字段属性-交互与规则-值联动'),
  locationRule('src/components/LinkageConfigPanel.vue', '^(config\\.options|rule\\.(dependValue|allowedOptions))', '实体配置-表单-编辑-字段属性-交互与规则-选项联动'),
  locationRule('src/components/LinkageConfigPanel.vue', '^config\\.calculation', '实体配置-表单-编辑-字段属性-交互与规则-自动计算'),

  locationRule('src/views/system/EntityVersionManagement.vue', '^draft\\.enabled$', '实体配置-数据版本-策略设置'),
  locationRule('src/views/system/EntityVersionManagement.vue', '^triggerEditor\\.', '实体配置-数据版本-生成时机'),
  locationRule('src/views/system/EntityVersionManagement.vue', '^(draft\\.snapshotScope\\.|scopeEditor\\.|condition\\.|maxSizeMb$)', '实体配置-数据版本-固化范围'),
  locationRule('src/views/system/EntityVersionManagement.vue', '^draft\\.diffPolicy\\.', '实体配置-数据版本-比对规则'),
  locationRule('src/views/system/EntityMutationPolicyManagement.vue', '^draft\\.enabled$', '实体配置-实体变更策略-策略设置'),
  locationRule('src/views/system/components/EntityVersionConfigDialogs.vue', '^scenario\\.', '实体配置-实体变更策略-变更规则'),
  locationRule('src/views/system/components/EntityVersionConfigDialogs.vue', '^step\\.', '实体配置-实体变更策略-处理步骤'),
  locationRule('src/views/system/components/EntityVersionConfigDialogs.vue', '^target\\.', '实体配置-实体变更策略-变更目标'),

  locationRule('src/views/ProcessList.vue', '^formData\\.', '流程配置-流程-新建/编辑'),
  locationRule('src/views/ProcessList.vue', '^publishForm\\.', '流程配置-流程-发布'),

  locationRule('src/components/NodeConfigPanel.vue', '^statusForm\\.', '流程配置-流程-设计-连线属性-常用-实体状态'),
  locationRule('src/components/NodeConfigPanel.vue', '^formConfig\\.', '流程配置-流程-设计-节点属性-常用-办理表单'),
  locationRule('src/components/NodeConfigPanel.vue', '^(approvalForm\\.|option\\.)', '流程配置-流程-设计-用户任务属性-常用-审批设置'),
  locationRule('src/components/NodeConfigPanel.vue', '^(ccForm\\.|rule\\.)', '流程配置-流程-设计-节点属性-协同-知会配置'),
  locationRule('src/components/NodeConfigPanel.vue', '^assigneeForm\\.', '流程配置-流程-设计-用户任务属性-常用-执行人与多人办理'),
  locationRule('src/components/NodeConfigPanel.vue', '^(serviceForm\\.|restForm\\.)', '流程配置-流程-设计-服务任务属性-常用-服务调用'),
  locationRule('src/components/NodeConfigPanel.vue', '^sendForm\\.', '流程配置-流程-设计-发送任务属性-常用-发送消息'),
  locationRule('src/components/NodeConfigPanel.vue', '^receiveForm\\.', '流程配置-流程-设计-接收任务属性-常用-接收消息'),
  locationRule('src/components/NodeConfigPanel.vue', '^manualForm\\.', '流程配置-流程-设计-人工任务属性-常用-线下任务'),
  locationRule('src/components/NodeConfigPanel.vue', '^ruleForm\\.', '流程配置-流程-设计-业务规则任务属性-常用-业务规则'),
  locationRule('src/components/NodeConfigPanel.vue', '^callForm\\.', '流程配置-流程-设计-调用活动属性-常用-调用流程'),
  locationRule('src/components/NodeConfigPanel.vue', '^conditionForm\\.', '流程配置-流程-设计-连线属性-常用-流转条件'),
  locationRule('src/components/NodeConfigPanel.vue', '^slaForm\\.', '流程配置-流程-设计-用户任务属性-高级-任务 SLA'),
  locationRule('src/components/NodeConfigPanel.vue', '^advancedForm\\.(skipExpression|skipNode)$', '流程配置-流程-设计-节点属性-高级-自动跳过'),
  locationRule('src/components/NodeConfigPanel.vue', '^advancedForm\\.', '流程配置-流程-设计-节点属性-高级-执行控制'),
  locationRule('src/components/NodeConfigPanel.vue', '^basicForm\\.', '流程配置-流程-设计-节点属性-常用-标识与备注'),

  locationRule('src/components/FlowActionConfigPanel.vue', '.*', '流程配置-流程-设计-节点属性-流程动作-新增/编辑动作'),
  locationRule('src/components/ui-config/EventBindingEditor.vue', '.*', '流程配置-流程-设计-事件绑定-新增/编辑'),
  locationRule('src/views/process/TaskSlaPolicyManagement.vue', '^form\\.', '流程配置-任务 SLA-策略管理-新建/编辑策略'),
  locationRule('src/views/process/TaskSlaPolicyManagement.vue', '^row\\.', '流程配置-任务 SLA-策略管理-升级动作'),
  locationRule('src/views/system/WorkCalendarManagement.vue', '^form\\.|^effectiveRange$', '流程配置-工作日历-新建/编辑日历'),
  locationRule('src/views/system/WorkCalendarManagement.vue', '^row\\.(dayOfWeek|start|end)$', '流程配置-工作日历-每周工作时段'),
  locationRule('src/views/system/WorkCalendarManagement.vue', '^row\\.(date|type|name|periodText)$', '流程配置-工作日历-日期例外'),
  locationRule('src/views/system/WorkCalendarManagement.vue', '^row\\.(scopeType|scopeKey|priority|effectiveFrom|effectiveTo)$', '流程配置-工作日历-绑定范围')
])

export function configurationLocation({ domain, area, file, binding }) {
  const normalizedFile = String(file || '').split(':')[0]
  const matched = LOCATION_RULES.find(rule =>
    rule.file === normalizedFile && rule.pattern.test(binding)
  )
  return matched?.location
    || DEFAULT_LOCATION_BY_AREA[area]
    || `${domain}-${area}`
}

const ENUM_LOCATION_BY_AREA = Object.freeze({
  '实体字段类型': '实体配置-实体-设计-字段属性-基础信息-字段类型',
  '表单节点类型': '实体配置-表单-编辑-设计器-组件面板',
  'BPMN 节点类型': '流程配置-流程-设计-节点工具栏',
  '实体生命周期模式': '实体配置-实体-新建/编辑-实体类型',
  '实体状态分类': '实体配置-实体-状态配置',
  '团队可见性覆盖级别': '实体配置-实体-设计-团队可见性',
  '数据权限规则效果': '实体配置-实体-设计-数据权限-规则编辑',
  '数据权限条件逻辑': '实体配置-实体-设计-数据权限-规则编辑',
  '列表数据范围模式': '实体配置-列表-编辑-列表设置-访问范围',
  '列表选择模式': '实体配置-列表-编辑-列表设置-选择行为',
  '按钮条件不满足策略': '实体配置-列表或表单-按钮配置-适用条件',
  '表单运行模式': '实体配置-表单-编辑-表单设置-按钮与操作',
  '表单内置按钮': '实体配置-表单-编辑-表单设置-按钮与操作',
  '表单与列表发布方式': '实体配置-表单或列表-编辑-发布',
  '统一事件继承方式': '实体配置或流程配置-对应设计器-事件绑定',
  '统一事件步骤位置': '实体配置或流程配置-对应设计器-事件绑定-步骤配置',
  '统一事件失败策略': '实体配置或流程配置-对应设计器-事件绑定-步骤配置',
  '用户任务办理人方式': '流程配置-流程-设计-用户任务属性-常用-执行人与多人办理',
  '服务任务实现方式': '流程配置-流程-设计-服务任务属性-常用-服务调用',
  '顺序流条件类型': '流程配置-流程-设计-连线属性-常用-流转条件',
  '知会触发时机': '流程配置-流程-设计-节点属性-协同-知会配置',
  '知会收件人规则': '流程配置-流程-设计-节点属性-协同-知会配置',
  '流程动作执行方式': '流程配置-流程-设计-节点属性-流程动作-新增/编辑动作',
  '流程动作失败策略': '流程配置-流程-设计-节点属性-流程动作-新增/编辑动作',
  '数据版本变更入口': '实体配置-数据版本-场景配置',
  '数据版本操作类型': '实体配置-数据版本-场景配置',
  '数据版本执行阶段': '实体配置-实体变更策略-处理步骤',
  '数据版本步骤类型': '实体配置-实体变更策略-处理步骤',
  '数据版本目标解析方式': '实体配置-实体变更策略-变更目标',
  '数据版本目标应用策略': '实体配置-实体变更策略-变更目标',
  'SLA 时间口径': '流程配置-任务 SLA-策略管理-新建/编辑策略',
  'SLA 指标': '流程配置-任务 SLA-策略管理-升级动作',
  'SLA 升级触发点': '流程配置-任务 SLA-策略管理-升级动作',
  'SLA 升级动作': '流程配置-任务 SLA-策略管理-升级动作',
  '工作日历作用域': '流程配置-工作日历-绑定范围'
})

export function configurationEnumLocation(group) {
  return ENUM_LOCATION_BY_AREA[group.area] || `${group.domain}-${group.area}`
}

const structured = ({
  id,
  domain,
  area,
  label,
  location,
  binding,
  meaning,
  configureWhen,
  skipWhen,
  example,
  expectedEffect,
  source,
  sourceToken,
  verification
}) => Object.freeze({
  id,
  domain,
  area,
  label,
  location: location
    || DEFAULT_LOCATION_BY_AREA[area]
    || configurationLocation({
      domain,
      area,
      file: source,
      binding
    }),
  binding,
  component: 'STRUCTURED',
  meaning,
  configureWhen,
  skipWhen,
  example,
  expectedEffect,
  source,
  sourceToken: sourceToken || binding.split('.').at(-1),
  verification
})

const formNodeProperty = (
  key,
  label,
  meaning,
  example,
  expectedEffect,
  nodeTypes = '适用节点'
) => structured({
  id: `structured-form-node-${key}`,
  domain: '实体配置',
  area: '表单节点结构化属性',
  label,
  binding: `formNode.${key}`,
  meaning,
  configureWhen: `${nodeTypes}需要调整“${label}”时配置。`,
  skipWhen: `沿用节点默认值，或当前节点类型不支持“${label}”时无需配置。`,
  example,
  expectedEffect,
  source: 'src/shared/form-node-property-schema.js:51',
  sourceToken: key,
  verification: 'src/shared/__tests__/form-node-property-schema.spec.js'
})

export const STRUCTURED_CONFIGURATIONS = Object.freeze([
  formNodeProperty('label', '节点标签', '设置分组、页签、字段或折叠面板的显示名称。', '基本信息', '画布、预览和运行时显示该标签。'),
  formNodeProperty('parentId', '父容器', '设置节点所属的容器或表单根节点。', 'section-basic', '节点移动到目标容器，并受节点层级规则校验。'),
  formNodeProperty('gutter', '栅格列间距', '设置 GRID 子列之间的像素间距。', 16, '栅格内相邻子节点保持 16px 间距。', 'GRID'),
  formNodeProperty('defaultSpan', '栅格默认跨度', '设置拖入 GRID 的子节点默认占用 24 栅格中的列数。', 12, '新拖入节点默认占半行宽度。', 'GRID'),
  formNodeProperty('tabPosition', '页签位置', '设置 TAB_SET 的页签导航位置。', 'top', '页签导航显示在内容顶部。', 'TAB_SET'),
  formNodeProperty('defaultExpanded', '默认展开', '设置折叠面板首次渲染时是否展开。', true, '运行时首次打开表单时该面板展开。', 'COLLAPSE'),
  formNodeProperty('accordion', '手风琴模式', '设置同组折叠面板是否只允许展开一个。', true, '展开当前面板时收起同组其他面板。', 'COLLAPSE'),
  formNodeProperty('text', '说明内容', '设置 TEXT 节点展示的只读说明。', '请核对申请信息后提交', '运行时显示该说明，不提交为实体字段。', 'TEXT'),
  formNodeProperty('textStyle', '说明文本样式', '设置 TEXT 节点受控的字号、颜色或对齐参数。', '{"fontWeight":"bold"}', '说明内容按受支持样式展示。', 'TEXT'),
  formNodeProperty('componentProps', '组件参数', '保存字段组件或容器组件的结构化参数。', '{"clearable":true}', '运行时组件读取受支持参数，未知参数被忽略。', 'FIELD、SUB_FORM、REPEATER 或扩展节点'),
  formNodeProperty('dataSource', '节点数据源', '绑定字段选项、默认值、计算、加载后或提交前数据源。', '{"usage":"FIELD_OPTIONS","serviceId":"10001","operationCode":"queryOptions"}', '运行时在对应阶段调用受控接口操作并应用映射。', 'FIELD、SUB_FORM、REPEATER'),
  formNodeProperty('events', '统一事件绑定', '保存字段或表单标准事件的受管理接口执行链。', '{"ENTITY_SELECTED":{"steps":[]}}', '事件发生时按发布快照执行条件、映射和失败策略。', 'FIELD'),
  formNodeProperty('template', '组件模板引用', '锁定组件模板及其版本，并保存本地覆盖。', '{"templateId":"tpl-1","templateVersion":2}', '发布版本使用锁定模板快照，升级前不自动漂移。', 'FIELD、SUB_FORM、REPEATER'),
  formNodeProperty('nodeExtension', '节点扩展', '绑定已注册节点扩展及实现、快照版本。', '{"componentName":"ProjectCard","componentVersion":1}', '运行时只加载注册扩展，不允许任意组件路径。', '支持扩展的节点'),

  ...[
    ['create.visible', '新增时显示', true, '新增表单显示该字段。'],
    ['create.editable', '新增时可编辑', true, '新增表单允许录入该字段。'],
    ['edit.visible', '编辑时显示', true, '编辑表单显示该字段。'],
    ['edit.editable', '编辑时可编辑', true, '编辑表单允许修改该字段。'],
    ['view.visible', '查看时显示', true, '查看表单显示该字段，但始终只读。'],
    ['approve.visible', '审批时显示', true, '审批办理表单显示该字段。'],
    ['approve.editable', '审批时可编辑', false, '审批时字段保持只读；节点强制整表只读仍具有更高优先级。']
  ].map(([pathValue, label, example, expectedEffect]) => structured({
    id: `structured-mode-access-${pathValue}`,
    domain: '实体配置',
    area: '表单字段运行模式权限',
    label,
    binding: `modeAccess.${pathValue}`,
    meaning: `控制字段在${label.replace('时', '模式')}的显示或编辑权限。`,
    configureWhen: '同一字段在新增、编辑、查看或审批中需要不同权限时配置。',
    skipWhen: '各模式沿用字段默认显示、只读状态时无需配置。',
    example,
    expectedEffect,
    source: 'src/views/EntityFormDesignByEntity.vue:494',
    sourceToken: 'modeAccess',
    verification: 'src/shared/__tests__/form-node-property-schema.spec.js；src/shared/__tests__/runtime-form-tabs.spec.js'
  })),

  ...[
    ['minLength', '最小长度', 2, '非空文本少于 2 个字符时校验失败。'],
    ['maxLength', '最大长度', 200, '非空文本超过 200 个字符时校验失败。'],
    ['min', '最小值', 0, '数值小于 0 时校验失败。'],
    ['max', '最大值', 1000000, '数值大于 1000000 时校验失败。'],
    ['format', '格式', 'EMAIL', '非空文本必须符合邮箱格式。']
  ].map(([key, label, example, expectedEffect]) => structured({
    id: `structured-form-validation-${key}`,
    domain: '实体配置',
    area: '表单字段校验',
    label,
    binding: `validation.${key}`,
    meaning: `设置字段的${label}结构化校验。`,
    configureWhen: `字段业务规则需要限制${label}时配置。`,
    skipWhen: `字段类型不支持该规则或不需要限制${label}时无需配置。`,
    example,
    expectedEffect,
    source: 'src/views/EntityFormDesignByEntity.vue:425',
    sourceToken: key,
    verification: 'src/shared/__tests__/entity-validation-rules.spec.js；src/shared/__tests__/form-node-property-schema.spec.js'
  })),

  structured({
    id: 'structured-approval-option-show-comment',
    domain: '流程配置',
    area: '审批选项',
    label: '显示备注',
    binding: 'approvalOption.showComment',
    meaning: '控制办理人选择该审批结果时是否展示审批备注输入框。',
    configureWhen: '该审批结果需要记录原因或意见时开启。',
    skipWhen: '结果不需要附加备注时关闭。',
    example: true,
    expectedEffect: '选择该结果时显示备注输入框。',
    source: 'src/components/NodeConfigPanel.vue:1155',
    sourceToken: 'showComment',
    verification: 'workflow-app 审批提交与节点配置测试'
  }),
  structured({
    id: 'structured-approval-option-remark-required',
    domain: '流程配置',
    area: '审批选项',
    label: '备注必填',
    binding: 'approvalOption.remarkRequired',
    meaning: '要求办理人选择该审批结果时必须填写审批备注。',
    configureWhen: '驳回、退回、终止等结果必须说明原因时开启。',
    skipWhen: '审批备注可选或该结果不展示备注时无需开启。',
    example: true,
    expectedEffect: '备注为空时提交审批会被前后端校验阻止。',
    source: 'src/components/NodeConfigPanel.vue:1168',
    sourceToken: 'remarkRequired',
    verification: 'workflow-app 审批提交与节点配置测试'
  }),

  ...[
    ['version', '操作栏版本', 1, '按版本 1 的兼容规则解析操作栏。'],
    ['builtInOverrides.*.enabled', '内置按钮启用覆盖', false, '对应内置按钮在允许模式中隐藏。'],
    ['builtInOverrides.*.labelByMode', '内置按钮分模式名称', '{"edit":"提交修改"}', '编辑模式显示“提交修改”，标准动作不改变。'],
    ['builtInOverrides.*.sort', '内置按钮顺序', 35, '按钮在底部操作栏按顺序值排列。'],
    ['builtInOverrides.*.buttonType', '内置按钮样式', 'primary', '只改变按钮视觉层级，不改变权限和动作语义。'],
    ['builtInOverrides.*.enabledModes', '内置按钮适用模式', '["edit"]', '按钮只在内置允许范围与配置模式交集内显示。'],
    ['builtInOverrides.*.availabilityRule', '内置按钮适用条件', '{"root":{"type":"GROUP","logic":"AND","children":[]}}', '服务端重新校验条件，不满足时隐藏或禁用。'],
    ['customButtons[].availabilityRule', '自定义按钮适用条件', '{"unavailableBehavior":"DISABLE"}', '条件不满足时按配置隐藏或禁用并给出原因。'],
    ['customButtons[].eventBinding', '自定义按钮事件绑定', '{"eventCode":"FORM_BUTTON_CLICK"}', '点击时执行受管理 FORM_BUTTON_CLICK 接口链，不能执行任意脚本或 URL。']
  ].map(([binding, label, example, expectedEffect], index) => structured({
    id: `structured-action-bar-${index}`,
    domain: '实体配置',
    area: '表单操作栏结构',
    label,
    binding: `actionBar.${binding}`,
    meaning: `配置${label}。`,
    configureWhen: `平台默认按钮不能满足当前表单的${label}需求时配置。`,
    skipWhen: '平台默认约定已经满足业务时删除覆盖，自动恢复默认。',
    example,
    expectedEffect,
    source: binding.includes('eventBinding')
      ? 'src/components/ui-config/EventBindingEditor.vue:370'
      : 'src/shared/form-actions.js:1',
    sourceToken: binding.includes('eventBinding') ? 'FORM_BUTTON_CLICK' : binding.split('.').at(-1).replace('[]', ''),
    verification: 'src/shared/__tests__/form-actions.spec.js'
  })),

  ...[
    ['orderKey', '列表按钮稳定顺序键', 3000000, '并发插入或拖拽后仍保持稳定排序。'],
    ['templateVersion', '列表按钮模板版本', 2, '运行时使用锁定的模板版本。'],
    ['localOverridesDocument', '列表按钮模板本地覆盖', '{"buttonLabel":"导出明细"}', '只覆盖允许的模板字段，未覆盖项继续继承模板快照。'],
    ['actionParams', '列表按钮动作参数', '{"targetListKey":"project_picker"}', '内置或打开列表动作按结构化参数执行。'],
    ['availabilityRule', '列表按钮适用条件', '{"unavailableBehavior":"HIDE"}', '服务端按记录、用户、状态和流程条件重新校验按钮。']
  ].map(([key, label, example, expectedEffect]) => structured({
    id: `structured-list-action-${key}`,
    domain: '实体配置',
    area: '列表按钮结构',
    label,
    binding: `listAction.${key}`,
    meaning: `配置${label}。`,
    configureWhen: `列表按钮需要${label}能力时配置。`,
    skipWhen: '普通内置按钮且无对应扩展需求时无需配置。',
    example,
    expectedEffect,
    source: 'src/shared/list-config-design.js:48',
    sourceToken: key,
    verification: 'src/shared/__tests__/list-config-design.spec.js'
  }))
])

const KEY_GUIDANCE = Object.freeze({
  enabled: ['启用或停用该项配置。', true, '开启后该配置参与运行；关闭后保留内容但不执行。'],
  description: ['补充面向设计和运维人员的说明。', '采购申请流程', '说明会随定义保存，用于识别用途，不直接改变业务计算。'],
  status: ['控制定义当前是否可使用。', 'ENABLED', '只有满足状态约束的定义会进入相应设计或运行入口。'],
  entityName: ['设置业务实体的显示名称。', '采购申请', '实体目录、设计器和运行页面显示该名称。'],
  entityCode: ['设置实体稳定编码。', 'purchase_request', '编码用于表名、接口、权限和跨配置引用，创建后通常不可修改。'],
  lifecycleMode: ['选择实体独立使用还是跟随流程生命周期。', 'WORKFLOW', '流程型实体可绑定流程并展示流程运行信息；独立实体不要求流程实例。'],
  fieldName: ['设置字段面向用户的中文名称。', '申请金额', '表单、列表和配置选择器默认显示该名称。'],
  fieldCode: ['设置字段稳定编码。', 'apply_amount', '编码作为数据库列、表达式路径、映射和接口字段名。'],
  fieldType: ['选择字段的数据语义和基础控件能力。', 'DECIMAL', '决定存储类型、可选组件、校验能力和运行时值类型。'],
  fieldLength: ['限制文本或数据库列的最大长度。', 200, '发布建表或变更时应用长度约束，超长输入会被校验或数据库拒绝。'],
  fieldPrecision: ['设置小数字段的小数位数。', 2, '小数保存和展示按该精度处理。'],
  isRequired: ['要求字段必须有值。', true, '表单提交和服务端写入校验会阻止空值。'],
  isUnique: ['要求字段值在实体内唯一。', true, '重复值写入时会被唯一性约束拒绝。'],
  defaultValue: ['设置新建记录或字段初始化时的默认值。', 'DRAFT', '字段尚未填写时使用该值；已有值不会被默认值覆盖。'],
  placeholder: ['设置控件未填写时的提示文案。', '请输入申请金额', '只改变输入提示，不作为实际提交值。'],
  validateRules: ['配置字段长度、范围或格式校验。', '{"min":0,"max":1000000}', '运行时录入与提交按规则校验，不兼容字段类型的规则会被清理。'],
  dictType: ['绑定平台字典类型。', 'request_status', '选项从该字典加载，字典调整后运行时按发布策略展示。'],
  refEntityId: ['选择被引用的实体。', 'project', '实体选择控件从目标实体读取记录，并保存选中记录 ID。'],
  refFieldCode: ['指定引用记录用于显示的字段。', 'project_name', '选择器和只读展示使用该字段作为名称，真实引用值仍为记录 ID。'],
  relationType: ['设置父子或引用关系语义。', 'MANY_TO_ONE', '影响关系保存、反向查询和级联行为。'],
  cascadeDelete: ['控制删除主记录时是否级联删除明细。', false, '开启后主记录删除会连带处理子记录，需谨慎使用。'],
  accessPermissionCode: ['设置进入列表所需的权限码。', 'entity:purchase:list', '无此权限的用户不能访问该列表。'],
  dataScopeMode: ['设置列表权限与实体权限的组合方式。', 'INHERIT', '决定列表查询继承、收窄或使用独立数据范围。'],
  fixedFilterConfig: ['配置所有用户都必须满足的固定查询条件。', '{"status":{"operator":"NE","value":"DELETED"}}', '运行时查询始终附加该条件，用户不能在查询区移除。'],
  contextBindingConfig: ['把路由、流程或父记录上下文绑定为查询条件。', '{"project_id":"{{routeQuery.projectId}}"}', '打开列表时解析上下文并注入过滤条件。'],
  selectionMode: ['设置列表是否作为单选或多选选择器。', 'SINGLE', '运行时进入选择场景，并按所选模式返回记录。'],
  selectionValueField: ['指定选择器主返回值字段。', 'id', '确认选择后以该字段作为引用主值。'],
  selectionReturnMappingsText: ['配置选择记录到调用方字段的返回映射。', '{"projectName":"project_name"}', '确认选择后批量返回映射字段。'],
  queryProviderCode: ['绑定受管理的安全查询提供者。', 'projectVisibleQuery', '列表查询由注册提供者生成，不能执行任意 SQL。'],
  queryDataSourceId: ['绑定列表查询使用的接口服务。', 'service-list-project', '运行时通过已发布 LIST_QUERY 绑定执行该服务。'],
  queryOperationCode: ['指定列表查询服务中的操作编码。', 'queryProjects', '运行时生成 LIST 上下文并调用该 READ 操作。'],
  customComponent: ['绑定已注册的自定义组件。', 'ProjectSummaryForm', '运行时改用注册组件渲染；未注册组件会被阻止或回退。'],
  customComponentProps: ['传递给已注册自定义组件的受控参数。', '{"compact":true}', '组件按参数调整展示，参数本身不允许注入脚本。'],
  pageSize: ['设置列表首次加载的每页记录数。', 20, '分页默认按该数量查询，用户仍可在允许范围内切换。'],
  defaultSortField: ['设置列表默认排序字段。', 'create_time', '首次查询按该字段排序。'],
  defaultSortDirection: ['设置默认升序或降序。', 'DESC', '与默认排序字段一起决定首屏记录顺序。'],
  formName: ['设置表单显示名称。', '采购申请编辑表单', '设计器、流程节点和运行时标题使用该名称。'],
  formKey: ['设置表单稳定标识。', 'purchase_request_edit', '发布、流程引用和接口按此标识识别表单。'],
  layoutType: ['设置表单整体布局方式。', 'GRID', '运行时按对应布局组织字段和容器。'],
  labelWidth: ['设置表单字段标签宽度。', 120, '统一调整标签与输入控件的对齐空间。'],
  componentType: ['选择字段运行时控件。', 'InputNumber', '字段按该组件渲染，并受字段类型兼容策略限制。'],
  gridSpan: ['设置节点在 24 栅格中的占用列宽。', 12, '值为 12 时一行显示两个同宽字段。'],
  sourcePath: ['指定被选实体中的来源字段路径。', 'project_manager_id', '选择记录后从该路径读取回填值。'],
  targetPath: ['指定当前表单接收回填值的字段路径。', 'manager_id', '回填结果写入该表单字段。'],
  overwrite: ['设置目标已有值时的覆盖策略。', 'IF_EMPTY', '仅空值、始终覆盖或确认后覆盖按所选策略执行。'],
  clearOnEmpty: ['设置来源为空或清除选择时是否清空目标字段。', true, '开启后来源无值时同步清空目标；关闭则保留原值。'],
  processName: ['设置流程显示名称。', '采购申请审批', '流程目录、待办和运行轨迹显示该名称。'],
  processKey: ['设置流程稳定标识。', 'purchase_request_approval', '流程部署、启动和版本识别使用该键。'],
  category: ['设置流程分类。', '采购管理', '流程列表可按分类查询和组织。'],
  versionDescription: ['描述本次发布变更。', '增加财务复核节点', '版本历史记录该说明，便于审计和回滚判断。'],
  markForExport: ['标记版本是否纳入迁移导出。', true, '开启后版本可进入配置迁移包候选范围。'],
  migrationTag: ['设置跨环境迁移批次标签。', 'REL-20260801-001', '导入导出和审计可按该标签关联同一发布批次。'],
  assigneeType: ['选择任务办理人的解析方式。', 'role', '运行时按固定用户、用户组、角色、表达式或受控解析器确定候选人。'],
  resolverCode: ['选择已注册的受管理解析器。', 'projectManagerResolver', '运行时调用该解析器计算人员、目标记录或通知对象。'],
  completionCondition: ['设置多实例任务完成条件。', '${nrOfCompletedInstances/nrOfInstances >= 0.5}', '满足表达式后结束剩余多实例任务。'],
  implementationType: ['选择服务任务的受控实现方式。', 'delegateExpression', '决定引擎调用 Java 类、表达式或 REST 配置。'],
  url: ['设置受控 REST 服务地址。', '/api/integration/check-budget', '服务任务执行时请求该地址；仍受服务端安全策略限制。'],
  method: ['设置 HTTP 请求方法。', 'POST', 'REST 服务任务使用该方法发送请求。'],
  timeout: ['设置等待或请求超时时间。', 30, '超过该时长后按超时或错误策略处理。'],
  retryCount: ['设置自动重试次数。', 3, '调用失败后最多按该次数重试。'],
  errorHandling: ['设置 REST 调用失败后的处理方式。', 'FAIL', '决定抛错终止、继续或转为受控空结果。'],
  calledElement: ['指定被调用流程的流程键。', 'finance_review', '运行到调用活动时启动对应流程定义。'],
  conditionExpression: ['设置节点状态变更或流转条件表达式。', '${amount > 10000}', '仅表达式为真时应用对应状态或路径。'],
  formSource: ['选择节点使用实体表单还是外部自定义表单。', 'entity', '决定运行时从发布表单快照还是外部表单键加载。'],
  isReadonly: ['强制节点表单整体只读。', true, '开启后覆盖字段级审批可编辑设置。'],
  commentLabel: ['设置审批意见输入框名称。', '审批意见', '审批弹窗使用该名称提示办理人填写意见。'],
  showComment: ['控制某审批结果是否展示备注框。', true, '选择该结果时显示审批备注输入。'],
  remarkRequired: ['控制审批结果的备注是否必填。', true, '未填写备注时不能提交该审批结果。'],
  timings: ['设置知会发送时机。', '["TASK_COMPLETE"]', '运行时在命中的节点时机生成知会。'],
  channels: ['设置知会渠道。', '["IN_APP","EMAIL"]', '通知通过选中的渠道发送。'],
  includeOperator: ['设置是否把当前办理人加入知会对象。', true, '开启后即使规则未命中，当前办理人也会收到知会。'],
  allowManualCc: ['控制任务办理人能否临时添加知会对象。', false, '关闭后运行时不接受人工知会，只执行节点预配置规则。'],
  skipExpression: ['设置节点自动跳过表达式。', '${skipFinance == true}', '令牌实际到达节点且表达式为真时自动完成该节点。'],
  policyCode: ['绑定已发布的任务 SLA 策略。', 'STANDARD_APPROVAL', '任务按策略计算响应、完成时限和升级动作。'],
  calendarSource: ['选择 SLA 使用的工作日历来源。', 'PROCESS', '时限计算从系统、流程、节点或业务字段解析日历。'],
  triggerTiming: ['设置流程动作触发时机。', 'NODE_COMPLETED', '仅在该流程生命周期事件发生时执行动作。'],
  executionMode: ['设置流程动作同步或异步执行。', 'ASYNC', '异步模式不阻塞主流程事务，同步模式失败可直接影响当前操作。'],
  failurePolicy: ['设置扩展步骤失败后的处理策略。', 'STOP', '决定停止主操作、继续后续步骤或返回空结果。'],
  maxRetries: ['设置流程动作失败后的最大重试次数。', 3, '可重试失败在达到次数前继续调度。'],
  eventCode: ['选择表单或列表的标准事件。', 'ENTITY_SELECTED', '事件发生时按当前发布版本执行绑定步骤。'],
  inheritanceMode: ['设置事件绑定继承和覆盖方式。', 'MERGE', '决定当前配置与实体、模板或平台默认绑定如何合并。'],
  strategy: ['设置事件步骤位于默认逻辑之前、替换或之后。', 'AFTER', '步骤按 BEFORE、REPLACE、AFTER 的顺序编排。'],
  serviceId: ['选择受管理接口服务。', '10001', '事件执行时调用该服务，权限和输入输出由服务定义约束。'],
  operationCode: ['选择接口服务中的操作。', 'resolveProject', '事件步骤执行该具体操作。'],
  calendarCode: ['设置工作日历稳定编码。', 'CN_STANDARD', 'SLA 和流程配置通过编码引用该日历。'],
  calendarName: ['设置工作日历显示名称。', '中国标准工作日', '日历选择器和管理页面显示该名称。'],
  timezoneId: ['设置日历使用的时区。', 'Asia/Shanghai', '工作时段和例外日期按该时区解释。'],
  dayOfWeek: ['设置每周工作日。', 1, '对应星期的工作时段参与业务分钟计算。'],
  start: ['设置工作时段开始时间。', '09:00', '该时刻之后开始累计工作时间。'],
  end: ['设置工作时段结束时间。', '18:00', '该时刻之后暂停累计工作时间。'],
  date: ['设置日历例外规则对应的具体日期。', '2026-10-01', '到该日期时，例外规则覆盖每周工作时段设置。'],
  periodText: ['设置补班日内实际计入工作的一个或多个时间段。', '09:00-12:00,13:00-18:00', '补班日只在列出的时段内累计 SLA 工作分钟。'],
  effectiveFrom: ['设置作用域绑定开始生效的日期。', '2026-08-01', '该日期之前不会用此绑定解析工作日历。'],
  effectiveTo: ['设置作用域绑定停止生效的日期。', '2026-12-31', '超过该日期后解析日历时忽略此绑定。'],
  effectiveRange: ['同时设置工作日历本身的生效开始和结束日期。', ['2026-08-01', '2026-12-31'], '只有落在日期区间内的任务才会使用该日历。'],
  defaultFlag: ['指定该日历是否作为未命中更具体绑定时的系统默认日历。', true, '没有部门、组织、流程或节点专属日历时回退到该日历。'],
  selectedTemplate: ['选择一个流程动作快捷模板，并用模板值初始化当前编辑动作。', 'send_notification', '选择后带出处理器、时机和参数初始值，之后仍可继续修改。'],
  visibilityScope: ['限制流程动作处理器定义对全部实体或指定实体可见。', 'ENTITY', '设为指定实体后，只有列出的实体配置动作时能选择该处理器。'],
  entityCodes: ['列出允许使用该扩展定义的实体稳定编码。', ['purchase_request'], '可见范围为指定实体时，仅这些实体的设计器显示该扩展。'],
  sourceNodeName: ['记录状态映射所对应的流程来源节点名称。', '部门审批', '设计器用它说明该状态变更从哪个节点离开，不作为运行时匹配主键。'],
  targetNodeName: ['记录状态映射所对应的流程目标节点名称。', '财务审批', '设计器用它说明状态变更进入哪个节点，不作为运行时匹配主键。'],
  entityFormIds: ['选择用户任务需要展示或合并的已发布实体表单。', ['10001'], '办理任务时按节点配置加载这些表单；多个表单共用一组流程操作按钮。'],
  async: ['启用 Flowable 节点异步作业边界。', true, '令牌在该节点通过作业执行器继续，主事务先提交并获得重试边界。'],
  asyncBefore: ['在进入节点逻辑前创建异步作业边界。', true, '节点实际执行前先提交当前事务，后续失败由作业重试处理。'],
  asyncAfter: ['在节点逻辑执行完成后创建异步作业边界。', true, '节点完成后先提交，再由作业继续后续流转。'],
  skipNode: ['允许节点在满足跳过表达式时自动完成。', true, '令牌到达后计算跳过表达式；为真时不创建或不保留人工办理任务。'],
  assignee: ['设置固定办理人编码，或在表达式模式下设置办理人表达式。', 'zhangsan', '固定人员模式直接分配给该用户；表达式模式从流程变量解析用户。'],
  candidateUserIds: ['选择可领取或办理任务的候选用户。', ['zhangsan', 'lisi'], '任务创建后这些用户可在候选任务中领取或办理。'],
  candidateGroupIds: ['选择可领取任务的候选用户组。', ['finance'], '运行时属于所选用户组的成员成为候选办理人。'],
  candidateRoleIds: ['选择可领取任务的候选角色。', ['finance_manager'], '运行时把当前拥有这些角色的用户解析为候选办理人。'],
  candidateUsers: ['设置候选用户集合的流程表达式。', '${deptManagers}', '任务创建时表达式结果被解析为候选用户集合。'],
  candidateGroups: ['设置候选用户组集合的流程表达式。', '${departmentCode}_manager', '任务创建时表达式结果被解析为候选组编码。'],
  extraParamsText: ['为受管理人员或知会解析器提供额外 JSON 参数。', '{"level":2}', '运行时把参数连同流程、任务和实体上下文传给已注册解析器。'],
  isMultiInstance: ['把单个用户任务转换为多实例会签或依次办理任务。', true, '引擎按人员集合创建并行或串行任务实例。'],
  multiInstanceType: ['选择多实例任务并行创建还是逐个串行创建。', 'parallel', '并行时同时产生多个实例；串行时前一个完成后再创建下一个。'],
  collectionSource: ['选择多实例人员由设计器直接选择，还是由受管理人员接口动态解析。', 'variable', '直接选择使用已配置用户、组和角色；接口方式在运行时按上下文计算。'],
  multiInstanceUserIds: ['选择直接参与多实例任务的固定用户。', ['zhangsan', 'lisi'], '发布时生成多实例人员集合，运行时为每个用户创建任务实例。'],
  multiInstanceGroupIds: ['选择需要展开为多实例人员的用户组。', ['finance'], '运行时解析组成员并纳入多实例人员集合。'],
  multiInstanceRoleIds: ['选择需要展开为多实例人员的角色。', ['finance_manager'], '运行时解析角色成员并纳入多实例人员集合。'],
  collectionExtraParamsText: ['为多实例人员解析接口提供额外 JSON 参数。', '{"departmentLevel":2}', '接口解析人员时收到这些参数以及流程、节点和实体上下文。'],
  collection: ['显示多实例内部使用的人员集合表达式。', '${_wfMultiInstanceUsers_}', '引擎从该内部变量读取多实例办理人集合；此字段由平台维护。'],
  elementVariable: ['设置多实例中当前单个办理人的流程变量名。', 'approver', '每个实例执行时可通过该变量读取当前人员 ID。'],
  implementation: ['设置非 REST 服务任务的 Java 类名、表达式或 Spring Bean 表达式。', '${budgetCheckDelegate}', '服务任务按所选实现类型解析并执行该受管理实现。'],
  contentType: ['设置 REST 服务任务请求正文的媒体类型。', 'application/json', '当前运行时按 JSON 序列化请求体并发送 Content-Type 头。'],
  headers: ['配置 REST 服务任务要附加的请求头及流程变量模板。', '{"X-Business-Ref":"${businessRef}"}', '调用前解析模板并添加请求头，敏感头仍受服务端策略约束。'],
  body: ['配置 REST 服务任务的 JSON 请求正文及变量模板。', '{"recordId":"${recordId}"}', '运行时解析流程变量，生成 JSON 请求体发送给目标服务。'],
  queryParams: ['配置 REST 服务任务的 URL 查询参数及变量模板。', '{"page":"${page}","size":"10"}', '调用前把解析后的键值编码到请求 URL。'],
  resultMapping: ['配置 REST 响应路径到流程变量的映射。', '{"data.id":"userId","data.status":"status"}', '调用成功后从响应读取来源路径并写入指定流程变量。'],
  resultVariable: ['设置服务或规则任务完整执行结果保存到的流程变量名。', 'decisionResult', '任务成功后把结果放入该变量，供后续网关、表单或动作读取。'],
  to: ['设置发送任务接收人编码或流程变量表达式。', '${approverUsername}', '发送任务执行时解析接收人并投递所配置的消息。'],
  messageRef: ['设置接收任务等待的外部消息名称。', 'paymentCallback', '只有关联流程实例收到同名消息后，接收任务才继续流转。'],
  hasTimeout: ['为接收任务启用超时处理。', true, '等待超过配置时长后按超时动作继续或抛错。'],
  timeoutUnit: ['设置接收任务超时数值使用分钟、小时还是天。', 'HOUR', '超时截止时间按所选单位换算。'],
  timeoutAction: ['选择接收任务超时后抛出错误还是继续后续流程。', 'continue', '达到超时时间后不再等待消息，并按所选分支处理。'],
  completionCriteria: ['记录线下人工任务何时可视为完成的业务标准。', '纸质合同已签字并归档', '运行页面和流程文档展示该标准；引擎不会自动判断文本是否满足。'],
  responsible: ['记录线下人工任务的负责人说明。', '采购专员', '流程跟踪中显示负责人，但不会据此生成平台待办。'],
  decisionRef: ['指定业务规则任务调用的 DMN 决策表键。', 'approvalLevelDecision', '运行到该节点时加载对应已部署决策定义。'],
  inputVariables: ['把流程变量映射为决策表输入名称。', '{"amount":"${amount}","dept":"${department}"}', '调用决策表前按映射构造输入上下文。'],
  mapDecisionResult: ['决定是否把决策结果中的字段展开为流程变量。', true, '开启后结果字段可被后续网关直接引用；关闭时只保留完整结果变量。'],
  callActivityType: ['选择调用活动启动 BPMN 子流程还是 CMMN 案例。', 'bpmn', '引擎按类型解析被调用定义，类型与目标定义不匹配时发布或启动失败。'],
  inputParameters: ['配置父流程变量到被调用流程变量的输入映射。', '{"subProcessVar":"${parentVar}"}', '启动子流程时按映射初始化其变量。'],
  outputParameters: ['配置被调用流程结果回写父流程变量的映射。', '{"parentResult":"${subProcessResult}"}', '子流程结束后把结果写回父流程上下文。'],
  property: ['选择流程条件要读取的流程变量或实体字段。', 'amount', '网关流转时从该属性取值并与条件值比较。'],
  allowManualPause: ['允许有权限的办理人暂停当前任务的 SLA 计时。', true, '人工暂停期间不累计受策略约束的时长，并记录暂停审计。'],
  pauseOnProcessSuspend: ['在流程实例挂起期间自动暂停任务 SLA 计时。', true, '流程恢复后从挂起前的累计时长继续计算。'],
  policyName: ['设置任务 SLA 策略的显示名称。', '标准审批时限', '节点选择器和 SLA 管理页面以该名称识别策略。'],
  responseTimeBasis: ['选择首次响应时限按工作时间还是自然时间累计。', 'WORKING_TIME', '工作时间跳过非工作时段；自然时间连续累计分钟。'],
  completionTimeBasis: ['选择任务办结时限按工作时间还是自然时间累计。', 'WORKING_TIME', '截止时间按所选口径结合办结分钟计算。'],
  stepName: ['设置升级或数据版本处理步骤的可读名称。', '到期前提醒', '管理页面、执行日志和模拟结果使用该名称区分步骤。'],
  metricType: ['选择 SLA 升级步骤监控首次响应还是任务办结。', 'RESPONSE', '步骤的到期基准改为所选指标的截止时间。'],
  triggerType: ['选择 SLA 动作在到期前、到期时或到期后触发。', 'BEFORE_DUE', '调度时间由指标截止时间、触发点和偏移分钟共同确定。'],
  actionType: ['选择 SLA 到点后执行提醒、知会、转办或加签动作。', 'NOTIFY', '调度器调用对应受管理动作，并按目标和收件人配置执行。'],
  maxExecutions: ['限制同一 SLA 升级步骤最多执行的次数。', 3, '重复提醒或升级达到次数后不再继续调度。'],
  targetConfigJson: ['配置 SLA 转办、加签等动作的目标解析参数。', '{"targetType":"MANAGER"}', '执行动作时按结构解析目标人员；与动作无关的参数被忽略。'],
  recipientConfigJson: ['配置 SLA 通知或知会的接收人和渠道。', '{"includeAssignee":true,"channels":["IN_APP"]}', '升级动作按配置解析接收人并通过指定渠道发送。'],
  conditionOperator: ['选择事件步骤条件比较值、包含、为空等运算方式。', 'equals', '只有当前上下文值与配置值按该运算符比较为真时才执行步骤。'],
  conditionBoolean: ['设置事件步骤布尔条件期望为真还是为假。', true, '当来源值与该布尔值一致时步骤才执行。'],
  unavailableBehavior: ['设置按钮适用条件不满足时隐藏，还是保留为禁用状态并说明原因。', 'DISABLE', '运行时按策略移除按钮或返回禁用原因，服务端仍会拒绝越权执行。'],
  relation: ['选择当前用户与记录或流程之间必须满足的关系。', 'CURRENT_USER_IS_CREATOR', '只有当前用户满足创建人、提交人或办理人关系时条件成立。'],
  modes: ['选择自定义表单按钮出现的新增、编辑、查看或审批模式。', ['view', 'edit'], '按钮只在发布配置允许且当前运行模式命中的表单中显示。'],
  placement: ['选择自定义表单按钮放在底部操作栏还是指定动作插槽。', 'FOOTER', '底部按钮进入统一操作栏；插槽按钮渲染到引用的 ACTION_SLOT。'],
  perm: ['设置执行列表或表单自定义按钮所需的权限码。', 'entity:purchase:generate-report', '解析按钮和执行事件时服务端都校验当前用户是否拥有该权限。'],
  sort: ['设置同一操作栏内按钮的显示顺序值。', 50, '按钮按顺序值从小到大排列，稳定键用于处理同值顺序。'],
  icon: ['选择按钮前显示的已注册 Element Plus 图标。', 'Document', '运行时在按钮名称前显示该图标；留空时只显示文字。'],
  buttonType: ['设置按钮默认、主要、成功、警告或危险视觉层级。', 'primary', '只改变视觉强调，不改变按钮动作、权限或确认策略。'],
  validateBeforeExecute: ['决定自定义表单按钮执行事件前是否先校验当前表单。', true, '开启后必填或格式校验失败会阻止 FORM_BUTTON_CLICK 事件。'],
  paramsText: ['配置初始化接口、实体查询或自定义初始化器的 JSON 输入参数。', '{"projectId":"{{routeQuery.projectId}}"}', '打开新增表单时解析上下文模板并把参数传给所选初始化来源。'],
  dataText: ['配置表单初始化 API 的 JSON 请求体。', '{"requestType":"URGENT"}', '初始化请求使用该正文，返回数据再按字段映射写入表单。'],
  mappingText: ['配置来源数据字段到当前记录或目标记录字段的映射。', '{"projectName":"name","projectCode":"code"}', '运行时只回写映射目标，未映射字段保持原值或由应用策略处理。'],
  staticText: ['配置新增表单直接使用的静态 JSON 初始值。', '{"status":"DRAFT","requestType":"URGENT"}', '打开新增表单时把这些值合并到空白表单。'],
  usage: ['选择表单数据源在默认值、选项、加载后还是提交前阶段执行。', 'BEFORE_SUBMIT', '运行时只在所选阶段调用数据源，并应用对应输入输出映射。'],
  inputMappingText: ['把表单、路由或上下文路径映射成数据源输入参数。', '{"filters.ownerId":"data.ownerId"}', '调用数据源前按路径读取当前值并构造输入对象。'],
  outputMappingText: ['把数据源返回路径映射回当前表单字段。', '{"ownerName":"data.user.name"}', '数据源成功后批量写入映射目标字段。'],
  dataSourceInputMappingText: ['把当前节点或表单数据映射成节点数据源输入。', '{"filters.ownerId":"data.ownerId"}', '节点数据源执行前按映射生成过滤或业务参数。'],
  dataSourceOutputMappingText: ['把节点数据源结果映射到字段值、选项或关联字段。', '{"assigneeName":"data.user.name"}', '数据源成功后按绑定用途写入对应节点数据。'],
  layout: ['选择多条子表单记录按分行表单还是表格布局编辑。', 'table', '表格适合字段少的明细；分行适合单条内容较复杂的明细。'],
  refEntityType: ['选择引用系统内置实体还是用户创建的业务实体。', 'CUSTOM', '引用选择器按类型限制可选实体，并使用相应元数据接口。'],
  visibilityEnabled: ['启用字段基于其他字段值的动态显示条件。', true, '运行时条件不成立时隐藏字段，但是否清值仍按字段保存策略处理。'],
  disabledEnabled: ['启用字段基于条件动态变为只读或禁用。', true, '条件成立时用户不能编辑该字段，现有值仍可展示和提交。'],
  requiredEnabled: ['启用字段基于条件动态变为必填。', true, '条件成立且字段为空时，表单校验阻止提交。'],
  valueLinkageEnabled: ['启用其他字段变化后自动计算或加载当前字段值。', true, '来源字段变化时按所选来源刷新当前字段。'],
  valueSourceType: ['选择联动值来自字段、公式还是历史兼容接口。', 'field', '运行时按来源读取字段、计算公式或调用兼容接口。'],
  apiUrl: ['设置历史值联动接口的相对地址。', '/api/region/getByParentId', '来源字段变化时调用该兼容接口；新增配置优先使用统一数据源。'],
  apiParams: ['配置历史值联动接口请求参数及字段模板。', '{"parentId":"${sourceField}"}', '调用前用当前表单值替换模板并构造请求参数。'],
  calculationEnabled: ['启用当前字段的公式计算。', true, '依赖字段变化时重新计算，并把结果写入当前字段。'],
  calculationEditable: ['决定用户能否手工覆盖公式计算结果。', false, '关闭后计算字段只读；开启后用户可在计算值基础上修改。'],
  optionsLinkageEnabled: ['启用下拉、单选或多选字段的动态选项过滤。', true, '来源字段变化时按匹配规则重新计算可选项。'],
  allowedOptions: ['选择某条联动规则命中时允许保留的选项值。', ['A', 'B'], '规则命中后其他选项不再可选；已有无效值按运行时策略清理。'],
  customHandler: ['设置列表自定义按钮调用的已注册处理器或组件名称。', 'exportPurchaseReport', '点击时只解析注册实现，未注册名称不会执行任意脚本。'],
  customMode: ['选择列表自定义按钮使用受管理处理器还是已注册组件。', 'handler', '处理器执行标准动作协议；组件模式打开注册的交互组件。'],
  link: ['决定行操作按钮是否使用紧凑文字链接样式。', true, '开启后减少行内占用空间，不改变按钮行为。'],
  presentation: ['选择打开目标列表时使用弹窗还是侧边抽屉。', 'DRAWER', '运行时在所选容器中加载目标列表，选择返回协议不变。'],
  selectionHandler: ['设置选择目标列表记录后调用的已注册前端结果处理器。', 'applyProjectSelection', '确认选择后处理器接收标准返回对象；未注册名称不会执行。'],
  showInList: ['决定实体字段是否作为当前列表的展示列。', true, '开启后该列进入列表表头和行数据；关闭后仍可单独作为查询条件。'],
  isQuery: ['决定实体字段是否出现在当前列表查询区。', true, '开启后用户可按该字段过滤；字段可不同时出现在结果列。'],
  collapsible: ['允许列表查询区折叠低频查询项。', true, '超出默认显示数量的条件可展开或收起，查询语义不变。'],
  stripe: ['启用列表表格斑马纹行样式。', true, '相邻数据行使用交替背景，便于横向阅读。'],
  border: ['显示列表表格单元格边框。', true, '表头和数据单元格显示分隔边框。'],
  showIndex: ['在列表首列显示当前分页内的行序号。', true, '序号随分页和页大小计算，不作为实体字段提交。'],
  queryType: ['选择查询字段使用文本、日期范围、字典、实体选择等查询控件。', 'dateRange', '查询区按该类型收集值并转换为兼容的过滤条件。'],
  align: ['设置列表列内容左对齐、居中或右对齐。', 'right', '该列单元格和表头按所选方向对齐。'],
  fixed: ['把列表列固定在横向滚动区域左侧或右侧。', 'left', '横向滚动时该列保持可见；留空时随表格滚动。'],
  showOverflowTooltip: ['在列表单元格内容溢出时显示完整内容提示。', true, '截断文本悬停后通过提示层查看完整值。'],
  dataSourceType: ['选择虚拟列表字段的数据来自实体字段、表达式还是统一数据源。', 'DATA_SOURCE', '运行时按类型解析虚拟列值，并应用数据源或渲染配置。'],
  renderComponent: ['选择列表单元格使用的已注册渲染组件。', 'StatusTag', '该列改用组件展示；留空时平台按字段类型自动匹配。'],
  statusCategory: ['把实体业务状态归入初始、处理中、完成、终止或撤回分类。', 'PROCESSING', '平台按分类统一判断可编辑性、流程状态展示和按钮条件。'],
  scenarioName: ['设置实体数据版本场景的显示名称。', '采购申请审批变更', '版本配置和模拟结果用该名称识别场景。'],
  sourceTypes: ['选择哪些数据写入入口会命中版本场景。', ['FORM', 'APPROVAL_TASK'], '只有来源入口在集合内的变更才进入该场景。'],
  operationTypes: ['选择新增、修改、删除或状态变化等哪些操作会命中场景。', ['UPDATE', 'STATUS_CHANGE'], '写入操作类型命中后才继续计算业务意图和场景条件。'],
  businessIntents: ['限制版本场景只处理指定业务意图编码。', ['APPROVAL_SUBMIT'], '写入上下文携带其中一个意图时场景才匹配；留空表示不限制。'],
  conditionText: ['设置数据版本场景进一步匹配的结构化条件或受控表达式。', '{"field":"status","operator":"EQ","value":"APPROVED"}', '来源、操作和业务意图命中后，还需条件为真才执行步骤。'],
  versionTitleTemplate: ['设置数据版本记录标题的变量模板。', '采购申请 ${record.code} 审批变更', '生成版本时解析记录上下文，形成可读审计标题。'],
  phase: ['选择数据版本步骤在写入前、写入后或事务提交后执行。', 'BEFORE_WRITE', '步骤按阶段排序执行；受管理接口固定使用支持的阶段。'],
  stepType: ['选择版本步骤执行内置规则、表达式、字段映射、受管理接口或 Java Provider。', 'FIELD_MAPPING', '运行时按类型校验配置并调用对应执行器。'],
  configText: ['配置数据版本步骤所选执行器需要的 JSON 参数。', '{"mappings":{"amount":"requestAmount"}}', '场景命中后执行器按参数转换、校验或调用服务。'],
  bindingName: ['设置数据版本跨实体目标绑定的显示名称。', '同步项目预算', '管理页面和执行日志用该名称识别目标绑定。'],
  resolverType: ['选择通过引用字段、实体关系或 Java Provider 解析目标记录。', 'FIELD', '运行时按类型取得目标 ID；无法解析时按目标失败策略处理。'],
  resolverConfigText: ['配置目标解析器所需的字段、关系或 Provider 参数。', '{"sourceField":"project_id"}', '解析目标记录前按该结构构造解析请求。'],
  effectivePatchText: ['配置跨实体变更成功后回写来源记录的字段补丁。', '{"sync_status":"SUCCESS"}', '目标写入成功后把补丁合并到来源记录。'],
  failedPatchText: ['配置跨实体变更失败后回写来源记录的字段补丁。', '{"sync_status":"FAILED"}', '目标处理失败时记录失败状态，便于重试和人工处理。'],
  applyStrategy: ['选择跨实体目标数据采用字段合并还是完整替换。', 'MERGE', '合并只更新映射字段；替换可能清除未提供字段。'],
  optionSource: ['选择枚举字段选项来自系统代码表还是只读旧内嵌选项。', 'DICT', '新配置从代码表加载；旧内嵌选项仅用于历史兼容。'],
  fileTypes: ['限制文件或图片字段允许上传的扩展名集合。', ['.jpg', '.png', '.pdf'], '选择文件时前端过滤类型，服务端仍执行最终文件策略校验。'],
  prefix: ['设置自动编码在日期和流水号前的固定前缀。', 'CG', '生成编码时以该前缀开头，便于区分业务单据类型。'],
  dateFormat: ['选择自动编码中日期部分的格式。', 'yyyyMMdd', '生成编码时把当前日期格式化后拼接到前缀和序号之间。'],
  seqType: ['选择自动编码流水号按天、月、年还是永不重置。', 'MONTH', '进入新周期后序号从初始值重新开始。'],
  example: ['显示依据当前编码规则生成的示例结果。', 'CG2026080001', '刷新后用于核对规则效果；只读示例本身不会保存为业务编码。'],
  teamVisibilityEnabled: ['决定用户因提交、审批或操作记录成为参与人后是否获得记录查看权。', true, '开启后参与事件可授予查看权限；关闭时仍记参与审计但不扩权。'],
  teamVisibilityLevel: ['设置参与团队查看权对普通范围和拒绝规则的覆盖级别。', 'ADDITIVE', '附加、覆盖范围或绝对授权按级别参与最终权限计算。'],
  ruleEffect: ['选择数据权限规则为放行匹配范围还是从结果中排除匹配范围。', 'DENY', '允许规则增加可见集合；拒绝规则从最终集合中剔除数据。'],
  matchLogic: ['选择规则适用对象条件按任一命中还是全部命中。', 'AND', '当前用户只有满足组合逻辑时才应用该权限规则。'],
  targetIds: ['选择数据权限规则匹配的用户、角色、用户组、部门或组织 ID。', ['finance_manager'], '运行时按范围类型解析这些对象并判断当前用户是否命中。'],
  includeSubDept: ['决定部门或组织匹配是否递归包含下级范围。', true, '开启后下级部门或组织中的用户也满足该适用对象条件。'],
  filterType: ['选择权限规则最终允许或拒绝的实体数据范围。', 'DEPARTMENT', '权限引擎据此生成全部、个人、提交人、部门或自定义字段过滤。'],
  mode: ['选择状态限制集合表示允许列表还是排除列表。', 'IN', 'IN 只保留指定状态；NOT_IN 排除指定状态。'],
  itemsText: ['按“编码:名称”逐行录入要创建的系统代码项。', 'DRAFT:草稿\nAPPROVED:已通过', '保存后创建代码表及选项，实体枚举字段可立即引用。'],
  sourceType: ['为数据版本模拟选择本次假设变更的来源入口。', 'APPROVAL_TASK', '模拟器只按该入口匹配场景，不写入发布配置。'],
  operationType: ['为数据版本模拟选择本次假设写入的操作类型。', 'UPDATE', '模拟器只运行匹配该操作的场景，不实际修改实体记录。'],
  beforeText: ['提供数据版本模拟中的写入前记录 JSON。', '{"status":"DRAFT","amount":1000}', '模拟器用它计算字段差异、条件和目标解析，不实际写库。'],
  afterText: ['提供数据版本模拟中的写入后记录 JSON。', '{"status":"APPROVED","amount":1000}', '模拟器以该数据验证步骤转换、版本标题和目标映射。'],
  extraText: ['提供数据版本模拟所需的流程、用户或业务扩展上下文。', '{"processDefinitionKey":"purchase_approval"}', '模拟器把扩展参数并入匹配上下文，不进入正式配置。'],
  priority: ['设置多个同时匹配的日历绑定或版本场景之间的优先级。', 100, '数值较高的匹配项优先采用；同优先级再按平台稳定规则处理。'],
  estimatedHours: ['记录线下人工任务预计需要的工时。', 4, '流程文档和跟踪页面显示预计工时，当前引擎不会据此自动完成任务。'],
  maxPauseMinutes: ['限制单个任务累计允许暂停 SLA 的最长分钟数。', 480, '人工暂停累计达到上限后不能继续延长暂停时间。'],
  offsetMinutes: ['设置 SLA 动作相对指标截止时间提前或延后的分钟数。', 30, '到期前减去该分钟数，到期后加上该分钟数得到首次执行时间。'],
  repeatIntervalMinutes: ['设置可重复 SLA 升级动作之间的间隔分钟数。', 60, '动作未达到最大次数时按该间隔再次调度。'],
  responseTargetMinutes: ['设置任务从创建到首次有效响应的目标分钟数。', 120, 'SLA 服务按响应计时口径计算响应截止时间和是否超时。'],
  completionTargetMinutes: ['设置任务从创建到办结的目标分钟数。', 480, 'SLA 服务按办结计时口径计算完成截止时间和升级计划。'],
  index: ['选择实体初始化查询结果中的第几条记录作为默认数据。', 0, '查询返回多条时取从 0 开始的指定记录，其余记录不参与初始化。'],
  calculationPrecision: ['设置公式计算结果保留的小数位数。', 2, '每次计算后按该位数舍入，再写入当前字段。'],
  defaultVisibleCount: ['设置列表查询区收起状态默认展示的条件数量。', 4, '超出数量的查询项放入展开区域。'],
  size: ['选择列表表格的紧凑、默认或宽松行高。', 'small', '运行时表格按所选密度展示，不改变分页数量和数据内容。'],
  width: ['设置列表列的目标像素宽度。', 180, '该列按配置宽度布局，并受最小宽度和表格可用空间约束。'],
  minWidth: ['设置列表列在自适应布局中不得低于的像素宽度。', 120, '窗口收窄时该列不会缩到此值以下，表格可改为横向滚动。'],
  sortOrder: ['设置数据版本步骤在同一执行阶段内的先后顺序。', 20, '场景命中后按顺序值从小到大执行步骤。'],
  maxSize: ['限制单个上传文件允许的最大大小。', 20, '超过 20 MB 的文件在前端选择和服务端上传校验时被拒绝。'],
  maxCount: ['限制文件或图片字段最多保留的附件数量。', 5, '达到数量上限后不能继续添加附件。'],
  seqLength: ['设置自动编码流水号固定占用的位数。', 4, '序号 12 会补零为 0012；超过位数时按生成规则继续增长或报错。'],
  actionName: ['设置流程动作实例的可读名称。', '审批前预算校验', '动作列表、执行日志和失败记录显示该名称。'],
  displayName: ['设置扩展处理器面向设计者的中文名称。', '同步审批结果', '流程动作处理器选择器显示该名称，稳定实现编码不变。'],
  summary: ['设置知会记录面向接收人的说明文案。', '请关注本次权限变更结果', '接收人的知会列表展示该说明。'],
  subject: ['设置发送任务消息的标题及流程变量模板。', '采购申请 ${dataNo} 待处理', '发送时解析变量并作为站内信或扩展通知渠道的标题。'],
  content: ['设置发送任务消息正文及流程变量模板。', '申请人：${submitterName}', '发送时解析变量并作为消息正文。'],
  documentation: ['记录 BPMN 节点的设计备注和运维说明。', '金额超过 10 万时进入本节点', '备注随流程定义保存，供设计和排查使用，不直接改变流转。'],
  openListTitle: ['设置打开目标列表弹窗或抽屉时的标题。', '选择项目', '运行时容器顶部显示该标题，列表本身的名称不变。'],
  statusName: ['设置实体业务状态面向用户的名称。', '待财务复核', '列表、表单、流程轨迹和状态选择器显示该名称。'],
  itemName: ['设置文件上传项或附件分类的显示名称。', '申请附件', '表单运行时以该名称区分不同上传项。'],
  ruleName: ['设置数据权限规则的可读名称。', '部门经理查看本部门数据', '权限管理、模拟结果和审计日志使用该名称识别规则。'],
  dictName: ['设置系统代码表的显示名称。', '采购申请状态', '代码表管理和字段选项来源选择器显示该名称。'],
  scopeKey: ['指定工作日历绑定的部门或组织 ID。', 'dept-finance', '解析任务日历时按范围类型和该 ID 匹配业务归属。'],
  entityStatusCode: ['选择流程经过节点或连线后写入绑定实体的状态编码。', 'FINANCE_REVIEW', '令牌经过配置位置时更新实体状态，并触发相应状态审计。'],
  processCalendarCode: ['选择流程级 SLA 工作日历编码。', 'CN_STANDARD', '节点选择流程日历来源时使用该日历计算工作分钟。'],
  businessFieldCode: ['选择用于解析业务归属部门或组织的实体字段编码。', 'applicant_dept_id', 'SLA 运行时从记录读取该字段，再匹配部门或组织日历。'],
  collectionResolverCode: ['选择运行时计算多实例办理人的受管理人员解析器。', 'projectApproverResolver', '创建多实例前调用该解析器生成去重后的人员集合。'],
  templateKey: ['选择发送任务使用的已注册消息模板键。', 'task_created_notice', '发送时加载模板并结合主题、正文或流程变量生成消息。'],
  businessKey: ['设置调用活动传给子流程或案例的业务关联键表达式。', '${dataNo}', '父子实例可通过解析后的业务键建立可追踪关联。'],
  refListKey: ['选择实体引用控件打开的目标实体列表配置键。', 'project_picker', '点击选择时加载该列表的查询、权限、返回值和映射配置。'],
  targetEntityCode: ['选择列表按钮要打开的目标实体编码。', 'project', '运行时按该实体查找目标列表定义。'],
  targetListKey: ['选择列表按钮要打开的目标列表稳定键。', 'project_picker', '运行时加载该列表的已发布版本。'],
  relationKey: ['选择服务端注册的上下文关系键，用于约束目标列表。', 'project_members', '打开列表时服务端按关系生成可信上下文过滤。'],
  statusCode: ['设置实体业务状态的稳定编码。', 'FINANCE_REVIEW', '流程状态映射、权限限制和接口按该编码引用状态。'],
  scenarioCode: ['设置或引用实体数据版本场景的稳定编码。', 'approval_change', '版本步骤、模拟和运行时匹配通过该编码识别场景。'],
  providerCode: ['选择数据版本步骤使用的已注册 Java Provider 或内置实现编码。', 'syncProjectBudget', '步骤执行时只调用注册表中对应实现。'],
  bindingCode: ['设置跨实体目标绑定的稳定编码。', 'sync_project_budget', '场景、迁移和执行日志通过该编码引用目标绑定。'],
  sourceEntityCode: ['选择跨实体变更的来源实体编码。', 'purchase_request', '只有该实体的匹配变更会解析并写入目标记录。'],
  childRefFieldCode: ['选择子实体中指向主实体记录的外键字段编码。', 'request_id', '保存子表数据时把主记录 ID 写入该字段，并据此查询明细。'],
  listKey: ['选择数据权限规则适用的具体列表键。', 'my_pending', '留空时规则作用于实体默认范围；填写后只影响该列表。'],
  dictCode: ['设置系统代码表的稳定编码。', 'purchase_status', '实体字段、接口和迁移包通过该编码引用代码项。'],
  businessIntentCode: ['设置数据写入携带的业务意图编码。', 'APPROVAL_SUBMIT', '版本场景可按该编码区分相同操作类型下的不同业务目的。'],
  actionDefinitionId: ['选择流程动作要调用的已注册处理器定义。', 'sync-approval-result', '运行时按发布快照校验处理器可见范围并执行其实现。'],
  conditionPath: ['设置统一事件步骤条件从上下文读取值的数据路径。', 'input.status', '执行步骤前从该路径取值并按条件运算符比较。'],
  responsePath: ['设置表单初始化 API 响应中作为来源对象的数据路径。', 'data.record', '接口成功后先定位该对象，再执行字段映射。'],
  serviceId: ['选择表单、节点或事件绑定的接口服务。', '10001', '运行时按发布版本执行指定 operationCode 并应用输入输出映射。'],
  refFormId: ['选择子表单节点引用的表单定义。', '20001', '运行时在父表单中加载该子表单结构。'],
  childFormReleaseId: ['锁定子表单节点使用的已发布版本 ID。', '30001', '父表单发布快照持续使用该版本，直到显式升级。'],
  dataSourceId: ['选择字段、列表虚拟列或节点使用的统一数据源。', '10001', '运行时只调用所选数据源的已发布定义和受控 Provider。'],
  templateId: ['选择当前字段、列表列或按钮继承的组件模板。', 'template-10001', '发布时锁定模板版本，并合并允许的本地覆盖。'],
  sourceField: ['选择值联动读取的当前表单来源字段。', 'project_id', '来源字段变化时重新计算或加载目标字段值。'],
  apiResultField: ['设置历史联动接口响应中要取值的路径。', 'data.managerId', '接口成功后从该路径读取结果并写入当前字段。'],
  optionsDependField: ['选择选项联动依赖的来源字段。', 'request_type', '该字段变化时重新匹配允许选项规则。'],
  selectedProcessId: ['选择流程型实体绑定的流程定义。', '2082642342048706562', '实体记录可按该流程的已发布版本发起和同步状态。'],
  fieldMode: ['选择版本节点固化全部已发布字段，还是只固化明确选择的字段。', 'ALL_PUBLISHED', '生成版本时按发布快照冻结对应字段集合，之后字段配置变化不会改写历史版本。'],
  fieldCodes: ['选择需要进入版本快照的稳定字段编码。', ['name', 'status'], '指定字段模式下只固化这些字段，并冻结生成版本时的中文名称和显示值。'],
  maxRowsPerRelation: ['限制单个直接关系默认允许固化的最大行数。', 500, '关系数据超过该行数时版本整体生成失败，不保存截断快照。'],
  maxRowsPerVersion: ['限制一个版本中所有关联数据允许固化的总行数。', 2000, '关联数据总行数超过上限时版本整体生成失败并返回明确原因。'],
  maxSizeMb: ['限制单个完整版本允许占用的估算存储大小。', 5, '预计快照超过该 MiB 上限时拒绝固化，避免生成不完整或超大版本。'],
  changedOnlyDefault: ['设置打开版本比较时是否默认隐藏未变化字段。', true, '开启后比较首先聚焦变化项，用户仍可切换为查看全部字段。'],
  trackOrder: ['设置关联集合的行顺序变化是否作为独立差异。', false, '开启后同一关联记录位置变化显示为“移动”，关闭时只比较行内容。'],
  ignoredFieldCodes: ['选择不参与业务数据差异统计的稳定字段编码。', ['update_time'], '比较时跳过这些字段，但历史快照仍可保留其冻结值。'],
  triggerName: ['设置版本生成时机的中文名称。', '审批通过固化', '版本时间线和发布历史使用该名称说明版本为何生成。'],
  maxRows: ['限制当前关联范围单次允许固化的最大行数。', 500, '该关系查询结果超过上限时版本整体生成失败，不会静默截断。'],
  logic: ['选择固定过滤条件要求全部满足还是任一满足。', 'ALL', '固化关联数据时按该组合逻辑筛选记录，只有筛选结果进入版本。'],
  operator: ['选择固定过滤字段和值之间的参数化比较操作。', 'EQ', '固化范围查询使用该受控操作符生成过滤条件，不执行任意 SQL。'],
  childEntityId: ['选择子表单字段对应的子实体定义。', '2082642338789732355', '子表单按该实体的字段结构保存和查询明细。'],
  simulationUserId: ['选择数据权限模拟时假设的当前用户。', '1', '模拟器以该用户的角色、组织和关系计算可见范围，不修改正式权限。'],
  conditionValue: ['设置统一事件步骤条件要比较的目标值。', 'APPROVED', '来源路径值与该值比较为真时执行步骤。'],
  sourceValue: ['设置字段值联动映射规则的来源值。', 'URGENT', '来源字段等于该值时，把对应目标值写入当前字段。'],
  targetValue: ['设置字段值联动规则命中后写入的目标值。', 'HIGH', '命中来源值后当前字段更新为该值。'],
  dependValue: ['设置选项联动规则要匹配的依赖字段值。', 'DOMESTIC', '依赖字段等于该值时应用该规则允许的选项集合。']
})

const CONTROL_OVERRIDES = Object.freeze({
  'src/views/EntityDesign.vue:item.required': {
    label: '附件项是否必填',
    meaning: '控制当前附件项是否必须上传文件。',
    configureWhen: '该附件属于业务办理、审批或归档的必要材料时开启。',
    skipWhen: '附件允许选传、后补或仅作为补充材料时关闭。',
    example: true,
    expectedEffect: '开启后，该附件项没有已上传文件时表单校验失败并阻止提交；关闭后允许该附件项为空。'
  },
  'src/views/EntityListConfigDesign.vue:listQueryEditor.serviceId': {
    label: '列表查询接口服务',
    meaning: '选择列表运行时使用的已发布只读接口服务。',
    example: '10001',
    expectedEffect: '列表查询改由该受管理服务执行，并继续受服务权限和操作定义约束。'
  },
  'src/views/EntityListConfigDesign.vue:listQueryEditor.operationCode': {
    label: '列表查询操作',
    meaning: '选择接口服务中负责分页查询的只读操作。',
    example: 'queryProjects',
    expectedEffect: '列表运行时调用该操作，并按下方输入参数和输出路径完成分页适配。'
  },
  'src/views/EntityListConfigDesign.vue:listQueryEditor.inputTargets.filters': {
    label: '查询条件参数名',
    meaning: '设置接口操作接收列表查询条件对象的参数名。',
    example: 'filters',
    expectedEffect: '运行时把列表筛选条件写入该参数后调用查询操作。'
  },
  'src/views/EntityListConfigDesign.vue:listQueryEditor.inputTargets.pageNum': {
    label: '当前页参数名',
    meaning: '设置接口操作接收当前页码的参数名。',
    example: 'pageNum',
    expectedEffect: '翻页时运行时把当前页码写入该参数。'
  },
  'src/views/EntityListConfigDesign.vue:listQueryEditor.inputTargets.pageSize': {
    label: '每页条数参数名',
    meaning: '设置接口操作接收每页记录数的参数名。',
    example: 'pageSize',
    expectedEffect: '运行时把列表分页大小写入该参数，并继续执行平台分页上限校验。'
  },
  'src/views/EntityListConfigDesign.vue:listQueryEditor.inputTargets.scene': {
    label: '使用场景参数名',
    meaning: '设置接口操作接收列表运行场景标识的参数名。',
    example: 'scene',
    expectedEffect: '普通列表或实体选择等场景标识通过该参数传给查询操作。'
  },
  'src/views/EntityListConfigDesign.vue:listQueryEditor.inputTargets.context': {
    label: '运行上下文参数名',
    meaning: '设置接口操作接收路由、流程和父记录上下文的参数名。',
    example: 'context',
    expectedEffect: '受信任运行上下文通过该参数传入，接口可据此收窄查询结果。'
  },
  'src/views/EntityListConfigDesign.vue:listQueryEditor.outputPaths.records': {
    label: '数据列表结果路径',
    meaning: '设置从接口响应中读取当前页记录数组的路径。',
    example: 'data.records',
    expectedEffect: '运行时从该路径提取记录并交给列表列配置渲染。'
  },
  'src/views/EntityListConfigDesign.vue:listQueryEditor.outputPaths.total': {
    label: '总记录数结果路径',
    meaning: '设置从接口响应中读取总记录数的路径。',
    example: 'data.total',
    expectedEffect: '分页器使用该路径的数值计算总页数和记录总量。'
  },
  'src/views/EntityListConfigDesign.vue:listQueryEditor.outputPaths.pageNum': {
    label: '当前页结果路径',
    meaning: '设置从接口响应中读取实际当前页码的路径。',
    example: 'data.pageNum',
    expectedEffect: '接口修正页码时，列表分页器按该路径返回的页码同步状态。'
  },
  'src/views/EntityListConfigDesign.vue:listQueryEditor.outputPaths.pageSize': {
    label: '每页条数结果路径',
    meaning: '设置从接口响应中读取实际每页记录数的路径。',
    example: 'data.pageSize',
    expectedEffect: '列表分页器按该路径返回的分页大小同步展示和后续查询。'
  },
  'src/views/system/WorkCalendarManagement.vue:row.name': {
    label: '例外名称',
    meaning: '设置休息日或补班日例外的可读名称。',
    example: '国庆节',
    expectedEffect: '日历详情和运维核对时显示该名称，日期与类型仍决定实际计时效果。'
  },
  'src/views/system/WorkCalendarManagement.vue:row.type': {
    label: '例外类型',
    meaning: '指定该例外日期为全天不工作，还是按补班时段工作。',
    example: 'NON_WORKING',
    expectedEffect: '休息日不累计工作分钟；补班日只在配置的补班时段内累计。'
  },
  'src/views/system/WorkCalendarManagement.vue:row.scopeType': {
    label: '日历绑定范围',
    meaning: '选择把工作日历绑定到部门还是组织。',
    example: 'DEPARTMENT',
    expectedEffect: '解析 SLA 日历时优先匹配部门绑定，再回退到组织或系统默认日历。'
  },
  'src/components/FlowActionConfigPanel.vue:param.type': {
    label: '参数值类型',
    meaning: '声明流程动作参数是静态文本、数字、布尔值还是流程变量引用。',
    example: 'variable',
    expectedEffect: '保存时按类型转换参数；流程变量类型会在执行动作时从流程上下文取值。'
  },
  'src/components/FlowActionConfigPanel.vue:param.name': {
    label: '动作参数名',
    meaning: '设置传给流程动作处理器的参数键，必须与所选动作定义约定的入参名称一致。',
    example: 'recordId',
    expectedEffect: '执行动作时处理器可通过 recordId 读取该参数；名称不匹配时处理器无法取得对应值。'
  },
  'src/components/FlowActionConfigPanel.vue:param.value': {
    label: '动作参数值',
    meaning: '设置动作参数的原始值；静态类型直接使用该值，流程变量类型则把它作为变量名解析。',
    example: 'businessId',
    expectedEffect: '当参数类型为 variable 时，执行动作会读取流程变量 businessId，并以参数名传给处理器。'
  },
  'src/components/ui-config/EventBindingEditor.vue:step.name': {
    label: '事件步骤名称',
    meaning: '设置事件链中单个接口步骤的可读名称，用于区分执行顺序和定位失败步骤。',
    example: '校验客户状态',
    expectedEffect: '配置界面和执行日志以该名称标识步骤，不改变接口调用本身。'
  },
  'src/components/NodeConfigPanel.vue:basicForm.name': {
    label: '节点名称',
    meaning: '设置 BPMN 节点面向设计者和办理人的显示名称。',
    example: '财务审批',
    expectedEffect: '流程图、待办标题、轨迹和节点配置引用中显示该名称。'
  },
  'src/components/NodeConfigPanel.vue:basicForm.id': {
    label: 'BPMN 节点 ID',
    meaning: '显示当前 BPMN 元素的稳定技术标识，供连线、事件、运行轨迹和节点配置引用。',
    configureWhen: '系统创建或导入 BPMN 节点时由设计器生成并展示，通常只用于排查和引用核对。',
    skipWhen: '设计者无需手工配置；控件为只读，业务名称请配置“节点名称”。',
    example: 'UserTask_1',
    expectedEffect: '发布和运行时使用该 ID 关联节点配置与流程实例历史；该界面不会改写它。'
  },
  'src/components/NodeConfigPanel.vue:option.label': {
    label: '审批选项名称',
    meaning: '设置办理人看到的审批结果按钮文案。',
    example: '驳回',
    expectedEffect: '审批弹窗显示“驳回”按钮，提交时仍使用同一选项配置的稳定值。'
  },
  'src/components/NodeConfigPanel.vue:option.value': {
    label: '审批选项值',
    meaning: '设置审批结果写入任务处理结果和流程变量的稳定业务值。',
    example: 'REJECT',
    expectedEffect: '办理人点击对应按钮后提交 REJECT，后续条件流可据此分支；修改显示名称不会改变该值。'
  },
  'src/components/NodeConfigPanel.vue:option.type': {
    label: '审批选项样式',
    meaning: '设置该审批结果按钮的主要、成功、警告或危险视觉样式。',
    example: 'danger',
    expectedEffect: '办理弹窗使用对应颜色强调结果，不改变结果值和流程动作。'
  },
  'src/components/NodeConfigPanel.vue:rule.type': {
    label: '知会收件人类型',
    meaning: '选择知会对象来自固定用户、角色、用户组、部门、发起人、办理历史、实体字段或受控解析器。',
    example: 'ROLE',
    expectedEffect: '触发知会时按该类型解析实际接收人，并进行去重和权限范围处理。'
  },
  'src/components/NodeConfigPanel.vue:rule.values': {
    label: '知会对象',
    meaning: '选择当前收件人类型对应的用户、角色、用户组或部门编码。',
    example: ['finance_manager'],
    expectedEffect: '知会触发时把配置对象解析为当前成员并生成接收记录。'
  },
  'src/components/NodeConfigPanel.vue:rule.includeChildren': {
    label: '知会包含下级组织',
    meaning: '决定部门知会规则是否递归包含其下级部门和组织成员。',
    example: true,
    expectedEffect: '开启后下级组织成员也会成为知会接收人；关闭时只解析当前部门成员。'
  },
  'src/components/NodeConfigPanel.vue:conditionForm.type': {
    label: '顺序流条件类型',
    meaning: '选择连线无条件通过、按表达式判断，或作为其他条件未命中时的默认流。',
    example: 'expression',
    expectedEffect: '流程令牌按条件结果选择路径；默认流只在其他条件均不成立时使用。'
  },
  'src/components/FlowConditionGroupEditor.vue:child.operator': {
    label: '流程条件运算符',
    meaning: '选择顺序流条件属性与目标值的等于、不等于、大小或集合比较方式。',
    example: '>=',
    expectedEffect: '网关判断时按字段类型和该运算符比较，结果为真才允许通过该连线。'
  },
  'src/components/ActionRuleGroupEditor.vue:node.logic': {
    label: '按钮条件组逻辑',
    meaning: '设置同一按钮条件组中的子条件必须全部满足还是满足任一项。',
    example: 'AND',
    expectedEffect: '运行时按 AND 或 OR 汇总子条件，决定按钮可用性。'
  },
  'src/components/ActionRuleGroupEditor.vue:child.type': {
    label: '按钮条件类型',
    meaning: '选择按当前用户关系、流程状态、状态编码、状态分类、记录字段或用户字段判断。',
    example: 'PROCESS_STATE',
    expectedEffect: '条件编辑器和服务端解析器按该类型读取对应上下文数据。'
  },
  'src/components/ActionRuleGroupEditor.vue:child.operator': {
    label: '按钮条件比较运算符',
    meaning: '设置按钮条件使用等于、不等于、包含、集合、为空或大小比较。',
    example: 'IN',
    expectedEffect: '服务端按字段类型执行比较，比较结果参与按钮条件组计算。'
  },
  'src/components/ActionRuleGroupEditor.vue:child.field': {
    label: '按钮条件比较字段',
    meaning: '选择按钮条件从当前记录或当前用户上下文读取的字段，具体来源由条件类型决定。',
    example: 'status',
    expectedEffect: '解析按钮时读取记录的 status，再使用所选运算符与比较值判断按钮是否可用。'
  },
  'src/components/ActionRuleGroupEditor.vue:child.value': {
    label: '按钮条件比较值',
    meaning: '设置按钮条件的目标值；允许的值形态由条件类型和运算符决定。',
    example: 'DRAFT',
    expectedEffect: '记录字段或流程状态与 DRAFT 比较，结果参与按钮隐藏或禁用判断。'
  },
  'src/components/ActionRuleEditorDialog.vue:rule.message': {
    label: '按钮不可用原因',
    meaning: '设置适用条件不成立且选择“禁用并说明”时向用户展示的原因。',
    example: '仅本人未流转草稿可以删除',
    expectedEffect: '按钮保留但禁用，悬停或操作提示中展示该原因；选择“隐藏按钮”时不显示。'
  },
  'src/views/EntityFormDesignByEntity.vue:selectedField.isHidden': {
    label: '默认隐藏',
    meaning: '设置字段在没有模式显示覆盖和联动显示条件时默认隐藏。',
    example: true,
    expectedEffect: '运行时先应用默认隐藏，再叠加模式权限和显示条件决定最终可见性。'
  },
  'src/components/form-designer/FormDesignerSettingsDrawer.vue:form.isDefault': {
    label: '默认表单',
    meaning: '指定当前表单为实体在未明确传入 formKey 时的首选表单。',
    example: true,
    expectedEffect: '新增、编辑或查看入口没有指定表单时优先解析该表单的已发布版本。'
  },
  'src/components/UiConfigPublishDialog.vue:form.releaseMode': {
    label: '发布方式',
    meaning: '选择普通发布形成新快照，或在兼容校验后对允许范围执行热修复。',
    example: 'STANDARD',
    expectedEffect: '普通表单发布等待流程重新发布后生效；兼容热修复可按预检范围作用于活动版本。'
  },
  'src/components/LinkageConfigPanel.vue:condition.operator': {
    label: '显示条件运算符',
    meaning: '选择联动条件对来源字段执行等于、不等于、大小、包含或空值判断。',
    example: '==',
    expectedEffect: '来源字段变化时重新比较，结果参与当前字段显示条件计算。'
  },
  'src/components/LinkageConfigPanel.vue:condition.field': {
    label: '显示条件来源字段',
    meaning: '选择当前表单中用于控制本字段是否显示的来源字段。',
    example: 'request_type',
    expectedEffect: 'request_type 变化时重新计算该显示条件，但不会改写来源字段。'
  },
  'src/components/LinkageConfigPanel.vue:condition.value': {
    label: '显示条件比较值',
    meaning: '设置显示条件中与来源字段比较的目标值；为空和不为空运算符不使用该值。',
    example: 'CHANGE',
    expectedEffect: '来源字段 request_type 等于 CHANGE 时该条件成立，并参与字段可见性计算。'
  },
  'src/components/ListButtonConfigPanel.vue:row.type': {
    label: '列表按钮类型',
    meaning: '选择按钮执行平台内置动作还是受管理自定义动作。',
    example: 'custom',
    expectedEffect: '内置按钮使用固定平台语义；自定义按钮只能调用已注册处理器或组件。'
  },
  'src/components/ListButtonConfigPanel.vue:row.label': {
    label: '列表按钮名称',
    meaning: '设置列表工具栏或行操作中向用户展示的按钮文案。',
    example: '生成报告',
    expectedEffect: '列表在通过权限和条件解析后显示“生成报告”，不改变按钮动作编码。'
  },
  'src/components/ListButtonConfigPanel.vue:row.key': {
    label: '列表按钮动作编码',
    meaning: '内置按钮选择平台动作稳定键；自定义按钮使用唯一稳定编码关联权限、事件或处理器。',
    example: 'generate_report',
    expectedEffect: '运行时以该编码识别按钮并查找对应事件绑定；发布后改码会形成新的按钮身份。'
  },
  'src/views/EntityDesign.vue:cond.scopeType': {
    label: '权限适用对象类型',
    meaning: '选择权限规则面向全部用户、指定用户、角色、用户组、部门或组织。',
    example: 'ROLE',
    expectedEffect: '权限引擎按该类型解析目标集合并判断当前用户是否适用此规则。'
  },
  'src/views/EntityDesign.vue:cond.operator': {
    label: '权限对象匹配方式',
    meaning: '设置用户拥有的角色或用户组需要命中任一配置项还是全部配置项。',
    example: 'ANY',
    expectedEffect: 'ANY 命中一个对象即成立；ALL 要求当前用户同时属于所有配置对象。'
  },
  'src/views/EntityDesign.vue:permissionForm.statusLimit.values': {
    label: '权限状态值',
    meaning: '选择数据权限状态限制要允许或排除的实体状态编码。',
    example: ['DRAFT', 'PROCESSING'],
    expectedEffect: '权限 SQL 按限制模式只保留或排除这些状态的记录。'
  },
  'src/views/EntityDesign.vue:optionsText': {
    label: '旧内嵌选项',
    meaning: '只读展示历史字段内嵌选项，供迁移到系统代码表时核对。',
    configureWhen: '当前字段仍保存旧版内嵌选项时查看。',
    skipWhen: '新字段或已经迁移到系统代码表的字段无需使用。',
    example: 'draft:草稿',
    expectedEffect: '该控件已禁用，不会修改发布配置；新选项必须改用系统代码表。'
  },
  'src/views/EntityFormList.vue:initConfigType': {
    label: '初始化方式',
    meaning: '选择新建表单数据时从 API、实体、静态值或受控初始化器取得默认数据。',
    example: 'entity',
    expectedEffect: '打开新增表单时先执行对应初始化器，再把映射结果填入表单。'
  },
  'src/views/EntityFormList.vue:initConfigData.custom.name': {
    label: '注册初始化器名称',
    meaning: '选择平台已注册的受控表单初始化器，不允许在配置中直接填写和执行脚本。',
    example: 'changeRequestInitializer',
    expectedEffect: '打开新增表单时调用该初始化器取得初始数据；初始化器未注册时保存校验会提示不可用。'
  },
  'src/views/EntityFormDesignByEntity.vue:form.layoutType': {
    label: '表单布局',
    meaning: '设置当前表单的顶层布局类型。',
    example: 'GRID',
    expectedEffect: '预览和运行时按选择的布局组织根节点。'
  },
  'src/views/EntityFormDesignByEntity.vue:selectedField.fieldLabel': {
    label: '字段标签',
    meaning: '覆盖实体字段在当前表单中的显示标签，不改变实体字段名称。',
    example: '项目负责人',
    expectedEffect: '只有当前表单显示新标签，同一实体的其他表单不受影响。'
  },
  'src/views/EntityFormDesignByEntity.vue:selectedField.componentName': {
    label: '节点扩展组件',
    meaning: '选择一个已注册且兼容当前节点类型的表单节点扩展组件。',
    example: 'customer-risk-summary',
    expectedEffect: '发布快照锁定该扩展及版本，运行时用它替代内置节点渲染；清空后恢复内置节点。'
  },
  'src/views/EntityFormDesignByEntity.vue:selectedComponentConfig': {
    label: '组件属性',
    meaning: '编辑当前节点支持的结构化组件参数。',
    example: '{"clearable":true}',
    expectedEffect: '发布后运行时组件按受支持参数渲染，未知参数由组件策略忽略。'
  },
  'src/views/EntityFormDesignByEntity.vue:viewConfig.customComponentProps': {
    label: '自定义表单组件参数'
  },
  'src/components/form-designer/FormDesignerSettingsDrawer.vue:viewConfig.actionBar': {
    label: '表单按钮配置',
    meaning: '配置内置按钮覆盖和表单级自定义按钮。',
    example: '{"version":1,"customButtons":[]}',
    expectedEffect: '发布后新增、编辑、查看和审批弹窗由统一操作栏解析按钮。'
  },
  'src/components/form-designer/FormDesignerSettingsDrawer.vue:viewConfig.inputParameterSchema': {
    label: '子表单输入参数契约',
    meaning: '声明当前表单作为子表单使用时允许父表单传入的运行参数及类型约束。',
    configureWhen: '当前表单会被其他表单作为子表单引用，并且数据源、联动或事件需要读取父表单业务参数时配置。',
    skipWhen: '子表单只依赖自身字段和系统自动维护的父子关系外键时无需配置。',
    example: {
      type: 'object',
      required: ['projectId'],
      properties: {
        projectId: {
          type: 'string',
          title: '项目ID'
        }
      }
    },
    expectedEffect: '发布快照冻结参数 Schema；父表单只能映射已声明参数，提交时服务端重新计算并校验参数类型和必填项。'
  },
  'src/components/form-designer/FormInputParameterEditor.vue:row.name': {
    label: '参数中文名称',
    meaning: '设置子表单输入参数在设计器和校验提示中的中文名称。',
    example: '项目ID',
    expectedEffect: '父表单配置参数映射及提交校验报错时显示该中文名称，参数身份仍由编码决定。'
  },
  'src/components/form-designer/FormInputParameterEditor.vue:row.code': {
    label: '参数编码',
    meaning: '设置子表单运行时通过 params 读取参数的稳定编码。',
    example: 'projectId',
    expectedEffect: '子表单数据源、联动和事件可通过 params.projectId 读取父表单传入的可信值。'
  },
  'src/components/form-designer/FormInputParameterEditor.vue:row.type': {
    label: '参数类型',
    meaning: '声明输入参数必须符合的 JSON Schema 类型。',
    example: 'string',
    expectedEffect: '发布与提交时校验父表单映射结果；类型不匹配时给出中文错误并阻止继续处理。'
  },
  'src/components/form-designer/FormInputParameterEditor.vue:row.required': {
    label: '参数必填',
    meaning: '声明子表单运行时是否必须取得该输入参数。',
    example: true,
    expectedEffect: '必填参数没有映射值且没有默认值时阻止父表单发布或业务提交。'
  },
  'src/components/form-designer/FormInputParameterEditor.vue:row.defaultValue': {
    label: '参数默认值',
    meaning: '设置父表单没有传入有效值时使用的参数默认值。',
    example: 'DEFAULT_PROJECT',
    expectedEffect: '运行时参数缺失时使用该默认值，默认值仍需满足已声明的参数类型。'
  },
  'src/components/form-designer/FormInputParameterEditor.vue:row.description': {
    label: '参数说明',
    meaning: '说明输入参数的业务用途和父表单应传入的内容。',
    example: '当前业务所属项目的主键ID',
    expectedEffect: '父表单配置参数映射时展示该说明，帮助配置人员选择正确来源。'
  },
  'selectedField.subListShowSearch': {
    meaning: '控制子列表顶部是否显示查询条件区域。',
    expectedEffect: '开启后用户可按已发布列表的查询字段筛选嵌入记录；关闭后直接展示当前查询结果。'
  },
  'selectedField.subListShowPagination': {
    meaning: '控制子列表底部是否显示分页控件。',
    expectedEffect: '开启后用户可切换页码并按配置的分页大小加载记录；关闭后不展示分页操作。'
  },
  'selectedField.subListShowToolbar': {
    meaning: '控制子列表是否显示已发布列表的顶部工具栏动作。',
    expectedEffect: '开启后按权限和适用条件展示列表工具栏按钮；关闭后不渲染这些顶部动作。'
  },
  'selectedField.subListShowRowActions': {
    meaning: '控制子列表是否显示每条记录对应的行操作。',
    expectedEffect: '开启后逐行解析并展示有权限的操作按钮；关闭后不渲染行操作列。'
  },
  'selectedField.subListPageSize': {
    meaning: '设置子列表每页默认加载的记录数量。',
    example: 10,
    expectedEffect: '启用分页时按该数量请求和展示记录，并据此计算总页数。'
  },
  'selectedField.subListMaxHeight': {
    meaning: '设置子列表内容区域允许使用的最大高度，单位为像素。',
    example: 420,
    expectedEffect: '记录内容超过该高度时列表区域内部滚动，避免撑开父表单布局。'
  },
  'src/components/form-designer/FormNodeDataSettings.vue:selectedParameterContract': {
    label: '子表单参数传递',
    meaning: '把父记录字段、父记录ID、可信运行上下文或固定值映射为子表单运行参数和子字段初始值。',
    configureWhen: '子表单声明了输入参数，或新增子行需要从父表单带入业务字段时配置。',
    skipWhen: '子表单不需要父级业务参数且子字段由用户自行填写时无需配置；关系外键始终由系统自动维护。',
    example: {
      version: 1,
      parameterMapping: {
        projectId: 'parent.data.project_id'
      },
      fieldInitializationMapping: {
        source_dept_id: 'parent.data.dept_id'
      }
    },
    expectedEffect: '运行参数通过 params 使用且不落库；子字段只在空值时初始化，已有非空内容不会被父字段变化覆盖。'
  },
  'src/components/form-designer/FormNodeDataSettings.vue:selectedSubListParameterContract': {
    label: '子列表参数传递',
    meaning: '把父记录字段、父记录ID、可信运行上下文或固定值映射为嵌入列表的查询参数。',
    configureWhen: '嵌入列表需要按当前父记录或表单上下文限定查询范围时配置。',
    skipWhen: '嵌入列表查询不依赖父表单数据，或已发布列表自身条件足以确定数据范围时无需配置。',
    example: {
      version: 1,
      parameterMapping: {
        projectId: 'parent.data.project_id'
      }
    },
    expectedEffect: '运行时先解析父表单映射，再把可信参数传给已发布列表查询；映射缺失或类型不符时阻止错误查询。'
  },
  'src/components/FormButtonConfigPanel.vue:row.label': {
    label: '表单按钮名称',
    meaning: '设置当前表单自定义按钮向用户展示的文案。',
    example: '生成报告',
    expectedEffect: '按钮在适用模式和位置中显示“生成报告”，运行时身份仍由稳定编码决定。'
  },
  'src/components/FormButtonConfigPanel.vue:row.key': {
    label: '表单按钮稳定编码',
    meaning: '设置表单内唯一且发布后保持不变的按钮技术标识，用于事件绑定和版本差异。',
    example: 'generate_report',
    expectedEffect: '运行时以 ownerFormId:generate_report 识别按钮并解析 FORM_BUTTON_CLICK 事件。'
  },
  'src/components/FormButtonConfigPanel.vue:row.slotKey': {
    label: '动作插槽',
    meaning: '选择当前表单中已存在的 ACTION_SLOT 节点稳定键，仅在按钮位置为动作插槽时使用。',
    example: 'record_actions',
    expectedEffect: '按钮渲染在 record_actions 插槽；插槽不存在时发布校验会拒绝该引用。'
  },
  'src/components/ListButtonConfigPanel.vue:advancedButton.targetFormMode': {
    meaning: '选择行按钮打开目标表单时使用查看模式还是编辑模式。',
    example: 'VIEW',
    expectedEffect: '选择 VIEW 时以只读详情打开当前记录；选择 EDIT 时进入可提交修改的编辑表单。'
  },
  'src/views/EntityListConfigDesign.vue:selectedListTemplateId': {
    meaning: '选择一个已发布的列表列模板，用它初始化当前列的高级配置。',
    configureWhen: '新建列表列或希望复用既有列的数据源、查询和渲染配置时选择。',
    skipWhen: '当前列需要独立配置，或不希望复制任何模板内容时无需选择。',
    example: 'LIST_COLUMN_TEMPLATE_001',
    expectedEffect: '选择后把模板快照复制到当前列；保存后当前列独立维护，模板后续修改不会反向覆盖。'
  },
  'src/components/FormButtonConfigPanel.vue:advancedButton.confirm.message': {
    label: '按钮确认提示',
    meaning: '设置自定义按钮开启二次确认后，执行事件链前显示的确认文案。',
    example: '确认生成报告？',
    expectedEffect: '用户确认后才继续校验和执行事件；取消确认不会触发按钮事件。'
  },
  'src/components/LinkageConfigPanel.vue:config.visibilityEnabled': {
    label: '启用显示条件'
  },
  'src/components/LinkageConfigPanel.vue:config.disabledEnabled': {
    label: '启用禁用条件'
  },
  'src/components/LinkageConfigPanel.vue:config.requiredEnabled': {
    label: '启用必填条件'
  },
  'src/components/LinkageConfigPanel.vue:config.visibilityLogic': {
    label: '显示条件逻辑',
    meaning: '设置多条显示条件按全部满足还是任一满足计算。',
    example: 'AND',
    expectedEffect: '字段可见性按 AND 或 OR 汇总条件结果。'
  },
  'src/views/EntityListConfigDesign.vue:toolbarButtons': {
    label: '工具栏按钮',
    meaning: '维护列表顶部的新增、批量或自定义动作集合。',
    example: '[{"key":"create","enabled":true}]',
    expectedEffect: '运行时列表顶部按权限、条件和顺序展示这些动作。'
  },
  'src/views/EntityListConfigDesign.vue:rowActionButtons': {
    label: '行操作按钮',
    meaning: '维护每条记录右侧的查看、编辑和自定义动作集合。',
    example: '[{"key":"view","enabled":true}]',
    expectedEffect: '运行时逐行解析权限和可用条件后展示操作。'
  },
  'src/views/EntityListConfigDesign.vue:editingDataSourceConfig': {
    label: '字段数据源参数',
    meaning: '编辑列表字段绑定数据源的结构化参数。',
    example: '{"usage":"LIST_COLUMN"}',
    expectedEffect: '字段选项或展示值按绑定数据源及输入输出映射解析。'
  },
  'src/views/EntityListConfigDesign.vue:editingRenderConfig': {
    label: '字段渲染参数',
    meaning: '编辑列表单元格渲染组件的结构化参数。',
    example: '{"format":"YYYY-MM-DD"}',
    expectedEffect: '发布后对应列按渲染组件和参数展示。'
  },
  'src/views/EntityDesign.vue:selectedField.validateRules': {
    label: '字段校验规则'
  },
  'src/views/EntityDesign.vue:cond.includeSubDept': {
    label: '包含下级组织'
  },
  'src/components/FlowConditionGroupEditor.vue:group.logic': {
    label: '条件组逻辑',
    meaning: '设置当前条件组内的子条件全部满足或任一满足。',
    example: 'AND',
    expectedEffect: '条件表达式按所选逻辑组合，直接影响顺序流是否命中。'
  },
  'src/components/FlowConditionGroupEditor.vue:child.value': {
    label: '流程条件目标值',
    meaning: '设置顺序流条件要比较的目标值，字段为选项时从实体选项中选择。',
    example: 'APPROVED',
    expectedEffect: '流程变量或实体字段与 APPROVED 比较为真时，该子条件命中并参与连线选择。'
  },
  'src/components/NodeConfigPanel.vue:restForm.body': {
    label: '请求体'
  },
  'src/components/ui-config/EventBindingEditor.vue:step.inputRows': {
    label: '输入映射',
    meaning: '把表单、记录、用户或流程上下文映射为接口操作输入。',
    example: '[{"targetPath":"recordId","sourcePath":"record.id"}]',
    expectedEffect: '事件执行前按映射构造受管理接口的请求参数。'
  },
  'src/components/ui-config/EventBindingEditor.vue:step.outputRows': {
    label: '输出映射',
    meaning: '把接口操作结果映射回字段或事件结果。',
    example: '[{"targetPath":"formData.status","sourcePath":"data.status"}]',
    expectedEffect: '接口成功后按映射批量更新表单或返回结果。'
  },
  'src/components/ui-config/EventBindingEditor.vue:step.conditionOperator': {
    label: '步骤条件运算符'
  },
  'src/components/ui-config/EventBindingEditor.vue:step.conditionBoolean': {
    label: '步骤条件布尔值'
  },
  'src/components/ActionRuleGroupEditor.vue:child.relation': {
    label: '当前用户关系'
  },
  'src/components/EventConfigPanel.vue:addForm.name': {
    label: '旧自定义事件名称',
    meaning: '设置历史字段脚本事件的唯一名称，通常以 on 开头。',
    configureWhen: '仅维护受信任开发者创建的旧字段脚本事件，并且现有统一事件模型无法替代时配置。',
    skipWhen: '新增业务逻辑不应使用；优先使用结构化字段联动或统一事件绑定。',
    example: 'onSelect',
    expectedEffect: '字段运行时触发同名兼容事件时执行对应旧脚本；事件名称不匹配则不会触发。'
  },
  'src/components/EventConfigPanel.vue:addForm.label': {
    label: '旧自定义事件描述',
    meaning: '设置历史字段脚本事件在设计器中的可读说明。',
    configureWhen: '旧脚本事件名称不足以说明触发时机或用途时配置。',
    skipWhen: '事件名称已经清楚，或没有维护旧脚本事件时无需配置。',
    example: '选中数据后触发',
    expectedEffect: '字段事件配置页显示该说明，便于维护者识别；不改变事件触发条件。'
  },
  'src/components/ui-config/FormDataSourceCompatDialog.vue:binding.clientPrevalidate': {
    label: '浏览器预校验',
    meaning: '允许 BEFORE_SUBMIT 数据源在浏览器中提前校验，服务端仍执行最终校验。',
    example: true,
    expectedEffect: '同时声明无副作用后，提交前先在浏览器调用数据源并尽早提示错误。'
  },
  'src/components/ui-config/FormDataSourceCompatDialog.vue:binding.sideEffectFree': {
    label: '无副作用',
    meaning: '声明该提交前数据源不会写库、发消息或产生外部副作用。',
    example: true,
    expectedEffect: '只有与浏览器预校验同时开启时客户端才允许调用；虚假声明可能造成重复执行风险。'
  },
  'src/components/EventConfigPanel.vue:eventCodes[item.name]': {
    label: '旧字段事件脚本',
    meaning: '保存 onChange、onBlur、onFocus 或自定义事件的历史前端脚本。',
    configureWhen: '仅迁移或维护已经存在的受信任开发脚本时使用。',
    skipWhen: '普通业务配置和所有新增逻辑都不应使用，改用结构化联动或统一事件绑定。',
    example: 'field.value = value',
    expectedEffect: '兼容运行时在字段事件发生时执行脚本；该能力不应开放给不受信任配置人员。'
  }
})

const EVIDENCE_BY_AREA = Object.freeze({
  '实体字段与数据权限': 'src/shared/__tests__/entity-validation-rules.spec.js；workflow-entity EntityFieldValidationRuleServiceTest；workflow-app PermissionSqlBuilderTest / DataPermissionEngineTest',
  '列表设计': 'src/shared/__tests__/list-config-design.spec.js；workflow-app EntityListIncrementalConfigurationTest / EntityListConfigurationValidatorTest',
  '列表按钮': 'src/shared/__tests__/list-config-design.spec.js；src/shared/__tests__/form-actions.spec.js',
  '表单设计': 'src/shared/__tests__/form-node-property-schema.spec.js；src/shared/__tests__/form-node-drag.spec.js；workflow-entity EntityFormNodePropertyPolicyTest / EntityFormNodeServicePropertyPolicyTest',
  '表单设置': 'src/shared/__tests__/form-actions.spec.js；workflow-app EntityFormConfigurationValidatorTest / EntityFormRuntimeServiceTest',
  '表单节点数据绑定': 'src/shared/__tests__/form-node-property-schema.spec.js；workflow-entity EntityFormNodeServicePropertyPolicyTest',
  '表单级数据源': 'workflow-entity UiDataSourceProviderPolicyTest / UiDataSourceServiceRevisionTest',
  '表单按钮': 'src/shared/__tests__/form-actions.spec.js；workflow-entity EntityFormActionConfigPolicyTest',
  '表单字段联动': 'src/components/form-fields/composables/__tests__/useFormField.spec.js',
  '实体选择后回填': 'src/shared/__tests__/entity-selection-mapping.spec.js',
  '表单与列表发布': 'workflow-app UiConfigReleaseServiceTest',
  '实体数据版本': 'workflow-entity EntityMutationPipelineTest / EntityRecordVersionServiceTest / EntityChangeTargetServiceTest',
  '流程条件': 'src/utils/__tests__/flowConditionGroups.spec.js',
  '流程节点': 'src/shared/process-config/index.js；workflow-app ProcessBpmnPublishSanitizerTest / EntityFormResolveServiceTest / ProcessCcRuntimeServiceTest / ProcessCcEventListenerTest',
  '流程动作': 'workflow-app FlowActionEngineEventListenerTest / FlowActionExecutionProcessorTest',
  '事件绑定': 'src/components/ui-config/__tests__/interfaceServiceModel.spec.js；workflow-entity UiEventBindingServiceRevisionTest',
  '任务 SLA': 'workflow-app TaskSlaPolicyServiceTest',
  '工作日历': 'workflow-app WorkCalendarCalculatorTest'
})

const USAGE_CONTEXT_BY_AREA = Object.freeze({
  '实体基础与状态': [
    '创建或维护实体，且生命周期、流程绑定或状态定义与现有约定不同时',
    '实体沿用现有生命周期、流程绑定和状态定义时'
  ],
  '实体字段与数据权限': [
    '实体字段结构、编号、校验规则或不同用户的数据访问范围需要区别于默认约定时',
    '字段使用类型默认值且所有有权用户的数据范围一致时'
  ],
  '数据权限模拟（验证输入，不发布）': [
    '发布权限规则前，需要以某个用户身份核对实际可见数据范围时',
    '不做权限结果验证，或已有自动化证据覆盖当前规则时'
  ],
  '列表设计': [
    '当前实体需要独立调整查询条件、展示列、数据范围、排序或列表交互时',
    '实体使用平台生成的默认列表即可时'
  ],
  '列表按钮': [
    '列表需要补充或覆盖工具栏、批量操作或行级业务动作时',
    '平台内置列表按钮已经覆盖全部操作时'
  ],
  '按钮适用条件': [
    '同一按钮只允许在特定用户关系、流程状态、实体状态或字段值下使用时',
    '按钮通过权限校验后在所有记录上都可使用时'
  ],
  '表单定义与初始化': [
    '实体需要新增一张用途独立的表单，或新增页面需要预填业务数据时',
    '实体只使用已有默认表单且新增时不需要额外预填时'
  ],
  '表单设计': [
    '当前表单的布局、字段呈现或节点组件需要区别于实体和组件默认值时',
    '表单完全沿用实体字段和内置组件的默认呈现时'
  ],
  '表单设置': [
    '当前表单需要单独控制基础属性、运行行为、扩展参数或操作栏时',
    '当前表单沿用平台表单约定且没有表单级扩展时'
  ],
  '表单节点数据绑定': [
    '容器、子表单、重复器或动作插槽需要绑定特定实体、表单版本或数据路径时',
    '节点只展示静态内容，或使用当前表单的直接字段绑定时'
  ],
  '表单级数据源': [
    '表单初始化、字段选项、提交校验或数据转换需要调用受管理动态数据源时',
    '表单数据完全来自当前记录和静态选项时'
  ],
  '表单按钮': [
    '平台默认表单按钮不足以完成当前表单的业务操作或摆放要求时',
    '新增、编辑、查看和审批均可直接使用平台默认操作栏时'
  ],
  '表单字段联动': [
    '字段的可见、必填、禁用、取值或候选项需要随其他字段动态变化时',
    '字段状态和值不依赖表单中的其他字段时'
  ],
  '旧字段脚本事件（受限）': [
    '迁移或维护受信任开发者创建、且暂时无法由结构化事件替代的旧字段脚本时',
    '新增业务逻辑，或旧逻辑已迁移到结构化联动和统一事件时'
  ],
  '实体选择后回填': [
    '单选实体选中记录后，需要把关联实体的其他属性写入当前表单字段时',
    '选择后只保存引用实体 ID 和显示名称，不需要带出其他属性时'
  ],
  '实体数据版本': [
    '实体记录需要保留变更快照、差异、回滚目标或按场景控制版本行为时',
    '实体不需要记录级版本审计和回滚能力时'
  ],
  '数据版本模拟（验证输入，不发布）': [
    '保存版本场景前，需要用假设记录验证命中条件和变更目标时',
    '不做交互模拟，或自动化测试已覆盖当前版本规则时'
  ],
  '表单与列表发布': [
    '草稿配置需要形成正式快照，或兼容修复需要作用于允许的活动版本时',
    '仍在编辑草稿，尚不准备让运行时使用本次修改时'
  ],
  '流程定义与发布': [
    '创建流程、调整流程元数据，或把设计草稿发布为可启动版本时',
    '现有流程定义和发布版本无需变化时'
  ],
  '流程节点': [
    '当前 BPMN 节点的办理人、表单、审批、知会、服务调用、状态或 SLA 行为需要区别于节点默认值时',
    '节点使用类型默认行为即可，或当前节点类型不具备对应能力时'
  ],
  '流程条件': [
    '网关或顺序流需要依据流程变量、实体字段或上下文数据决定流向时',
    '该连线无条件通过，或由其他连线承担分支判断时'
  ],
  '流程动作': [
    '流程生命周期事件需要调用受控处理器更新业务数据、发消息或执行集成动作时',
    '流程仅使用引擎默认行为，不需要附加业务动作时'
  ],
  '流程动作扩展定义': [
    '需要注册或限制可供流程设计器选择的动作处理器时',
    '只使用平台现有动作处理器且无需调整可见范围时'
  ],
  '事件绑定': [
    '表单、列表或流程事件需要按顺序调用受管理接口，并处理条件、输入或输出映射时',
    '事件只执行平台默认处理，不需要外部接口步骤时'
  ],
  '任务 SLA': [
    '用户任务存在响应、完成、暂停、升级或重复提醒的时限要求时',
    '任务没有服务时限和超时提醒要求时'
  ],
  '工作日历': [
    'SLA、超时或期限计算需要遵循企业工作时段、节假日或组织差异时',
    '全部时限按自然时间计算，或系统默认日历已满足要求时'
  ]
})

function attrValue(node, name) {
  const prop = (node.props || []).find(item =>
    item.type === NodeTypes.ATTRIBUTE && item.name === name
  )
  return prop?.value?.content || ''
}

function hasAttribute(node, name) {
  return (node.props || []).some(item =>
    item.type === NodeTypes.ATTRIBUTE && item.name === name
  )
}

function directive(node, name) {
  return (node.props || []).find(item =>
    item.type === NodeTypes.DIRECTIVE && item.name === name
  )
}

function visibleText(node) {
  return (node.children || []).map(child => {
    if (child.type === NodeTypes.TEXT) return child.content.trim()
    if (child.type === NodeTypes.ELEMENT) return visibleText(child)
    return ''
  }).filter(Boolean).join(' ')
}

function descendantAttribute(node, name) {
  for (const child of node.children || []) {
    if (child.type !== NodeTypes.ELEMENT) continue
    const value = attrValue(child, name)
    if (value) return value
    const nested = descendantAttribute(child, name)
    if (nested) return nested
  }
  return ''
}

function contextLabel(node) {
  const labelSlot = (node.children || []).find(child =>
    child.type === NodeTypes.ELEMENT
      && child.tag === 'template'
      && directive(child, 'slot')?.arg?.content === 'label'
  ) || { children: [] }
  return attrValue(node, 'label')
    || visibleText(labelSlot)
    || descendantAttribute(labelSlot, 'label')
}

function modelControls(file) {
  const absolute = path.join(ROOT, file)
  const fileSource = readFileSync(absolute, 'utf8')
  const { descriptor } = parseSfc(fileSource, { filename: file })
  if (!descriptor.template) return []
  const scriptSource = [
    descriptor.script?.content || '',
    descriptor.scriptSetup?.content || ''
  ].join('\n')
  const requiredRuleProps = new Set(
    [...scriptSource.matchAll(
      /(?:^|\n)\s*([A-Za-z_$][\w$]*)\s*:\s*\[\s*\{\s*required\s*:\s*true/g
    )].map(match => match[1])
  )
  const ast = baseParse(descriptor.template.content)
  const controls = []

  function visit(node, context = {}) {
    if (node.type !== NodeTypes.ELEMENT) {
      for (const child of node.children || []) visit(child, context)
      return
    }
    const ownLabel = ['el-form-item', 'el-table-column'].includes(node.tag)
      ? contextLabel(node)
      : ''
    const ownProp = node.tag === 'el-form-item' ? attrValue(node, 'prop') : ''
    const nextContext = {
      ...context,
      ...(ownLabel ? { label: ownLabel } : {}),
      ...(ownProp ? { prop: ownProp } : {}),
      required: context.required
        || hasAttribute(node, 'required')
        || attrValue(node, 'required') === 'true'
        || requiredRuleProps.has(ownProp)
    }
    const model = directive(node, 'model')
    if (model?.exp?.content) {
      const options = []
      const collectOptions = item => {
        if (
          item.type === NodeTypes.ELEMENT
          && ['el-option', 'el-radio', 'el-radio-button'].includes(item.tag)
        ) {
          const value = attrValue(item, 'value')
          const label = attrValue(item, 'label') || visibleText(item)
          if (value || label) options.push({ value, label })
        }
        for (const child of item.children || []) collectOptions(child)
      }
      collectOptions(node)
      controls.push({
        binding: model.exp.content.trim(),
        component: node.tag,
        label: nextContext.label || attrValue(node, 'placeholder') || model.exp.content.trim(),
        placeholder: attrValue(node, 'placeholder'),
        type: attrValue(node, 'type'),
        required: Boolean(nextContext.required),
        options,
        line: (descriptor.template.loc.start.line || 1) + (node.loc.start.line || 1) - 1
      })
    }
    for (const child of node.children || []) visit(child, nextContext)
  }

  visit(ast)
  return controls
}

function terminalKey(binding) {
  return String(binding).split('.').at(-1).replace(/\W+/g, '')
}

function controlOverride(group, binding) {
  return CONTROL_OVERRIDES[`${group.file}:${binding}`]
    || CONTROL_OVERRIDES[binding]
    || {}
}

function inferredGuidance(control, binding, label) {
  const key = terminalKey(binding)
  const lower = key.toLowerCase()
  const optionExample = control.options.find(item => item.value && item.value !== 'false')?.value
  if (
    control.component === 'el-switch'
    || control.component === 'el-checkbox'
    || /^(is|has|allow|include|pause)/i.test(key)
    || /(enabled|readonly|required|hidden|collapsible|border|stripe|showindex|showinlist|isquery|defaultflag|link|async|editable)$/i.test(key)
  ) {
    return [
      `控制“${label}”是否启用。`,
      true,
      `开启后启用“${label}”对应能力；关闭后该能力不参与运行。`
    ]
  }
  if (/(mapping|parameters|params|config|headers|queryparams|body|text|json|rows)$/i.test(key)) {
    return [
      `配置“${label}”所需的结构化内容或映射规则。`,
      control.type === 'textarea' ? '{"key":"value"}' : '已配置结构',
      `保存并发布后，运行时解析“${label}”并用于数据转换、调用或展示。`
    ]
  }
  if (/(expression|formula|condition)$/i.test(key)) {
    return [
      `设置“${label}”的受控表达式或判断条件。`,
      '${amount > 10000}',
      `运行时计算表达式，并用结果决定“${label}”对应行为。`
    ]
  }
  if (/(code|key)$/i.test(key)) {
    return [
      `设置“${label}”的稳定编码，供发布版本和跨配置引用。`,
      lower.includes('status') ? 'PENDING' : 'stable_code',
      `保存后其他配置和运行时通过该编码识别“${label}”。`
    ]
  }
  if (/(ids|users|groups|roles|targetids|values|channels|timings|modes|sourcetypes|operationtypes|businessintents)$/i.test(key)) {
    return [
      `选择“${label}”对应的一组值。`,
      optionExample ? [optionExample] : ['VALUE'],
      `运行时按所选集合解析“${label}”，未选值不会参与匹配或执行。`
    ]
  }
  if (/(id|field|path)$/i.test(key)) {
    return [
      `选择或填写“${label}”引用的对象、字段或数据路径。`,
      optionExample || 'record_id',
      `运行时通过该引用定位“${label}”对应数据。`
    ]
  }
  if (/(type|mode|source|strategy|policy|timing|channel|operator|logic|direction|position|placement|phase|action|basis)$/i.test(key)) {
    return [
      `选择“${label}”采用的处理方式。`,
      optionExample || 'DEFAULT',
      `运行时按所选方式执行“${label}”，不同选项会改变处理路径。`
    ]
  }
  if (/(count|minutes|hours|size|length|width|precision|priority|order|index|span|offset|retries)$/i.test(key)) {
    return [
      `设置“${label}”的数值限制、顺序或容量。`,
      10,
      `运行时和校验逻辑使用该数值约束“${label}”。`
    ]
  }
  if (/(name|label|title|summary|documentation|subject|content|message|description)$/i.test(key)) {
    return [
      `设置“${label}”的显示文案或说明。`,
      control.placeholder
        ? control.placeholder.replace(/^(请输入|如[：:]?|例如[：:]?)/, '').trim()
        : '示例文案',
      `设计器、运行页面或通知内容会显示该“${label}”文案。`
    ]
  }
  if (/(value|defaultvalue)$/i.test(key)) {
    return [
      `设置“${label}”使用的业务值。`,
      optionExample || 'VALUE',
      `运行时把该值用于“${label}”的比较、初始化或参数传递。`
    ]
  }
  return [
    `设置“${label}”在当前配置中的业务参数。`,
    optionExample
      || (control.type === 'textarea' ? '示例文本' : '已配置值'),
    `保存并发布后，运行时在${label}相关场景读取并应用该参数。`
  ]
}

function humanizeBinding(binding) {
  return String(binding)
    .replaceAll(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replaceAll('.', ' / ')
}

function inferredExample(control, binding) {
  const guidance = KEY_GUIDANCE[terminalKey(binding)]
  if (guidance) return guidance[1]
  if (control.placeholder) {
    const placeholder = control.placeholder.replace(/^(请输入|请选择|如[：:]?|例如[：:]?)/, '').trim()
    if (placeholder && !placeholder.includes('{{')) return placeholder
  }
  const option = control.options.find(item => item.value && item.value !== 'false')
  if (option) return option.value
  if (control.component === 'el-switch') return true
  if (control.component === 'el-checkbox') return true
  if (control.component.includes('input-number')) return 10
  if (control.component.includes('date')) return '2026-08-01'
  if (control.type === 'textarea') return '{"key":"value"}'
  if (control.component.includes('checkbox-group')) return ['VALUE']
  if (control.component.includes('select')) return '已选择项'
  return '已配置值'
}

function narrative(control, group) {
  const key = terminalKey(control.binding)
  const guidance = KEY_GUIDANCE[key]
  const override = controlOverride(group, control.binding)
  const label = override.label || control.label || humanizeBinding(control.binding)
  const inferred = inferredGuidance(control, control.binding, label)
  const meaning = override.meaning || guidance?.[0] || inferred[0]
  const expectedEffect = override.expectedEffect || guidance?.[2] || inferred[2]
  const validationOnly = group.area.includes('不发布')
  const [usageContext, skipContext] = USAGE_CONTEXT_BY_AREA[group.area] || [
    `当前功能需要单独调整“${label}”时`,
    `当前功能沿用平台默认“${label}”时`
  ]
  const actionDescription = meaning.replace(/[。；]+$/, '')
  const defaultConfigureWhen = control.required
    ? `创建或维护${group.area}时必须配置“${label}”。`
    : `${usageContext}，${actionDescription}。`
  const defaultSkipWhen = control.required
    ? `该字段属于必填配置；只有不使用对应功能时才不涉及，已启用配置不能留空。`
    : `${skipContext}，且“${label}”不需要单独覆盖时无需配置。`
  return {
    label,
    meaning,
    configureWhen: override.configureWhen || (validationOnly
      ? `当需要在发布前验证“${label}”的匹配或转换结果时填写。`
      : defaultConfigureWhen),
    skipWhen: override.skipWhen || (validationOnly
      ? `该值只用于当前模拟，不进入草稿或发布版本；不做验证时无需填写。`
      : defaultSkipWhen),
    example: override.example ?? guidance?.[1] ?? inferred[1] ?? inferredExample(control, control.binding),
    expectedEffect
  }
}

export function buildConfigurationReference() {
  const entries = []
  for (const group of CONFIGURATION_SOURCES) {
    const controls = modelControls(group.file)
      .filter(control => group.patterns.some(pattern => pattern.test(control.binding)))
    const unique = new Map()
    for (const control of controls) {
      const key = `${group.file}:${control.binding}`
      if (!unique.has(key)) unique.set(key, control)
    }
    for (const control of unique.values()) {
      const details = narrative(control, group)
      entries.push({
        id: `${group.file.replaceAll(/[/.]/g, '-')}:${control.binding}`,
        domain: group.domain,
        area: group.area,
        label: details.label,
        location: configurationLocation({
          domain: group.domain,
          area: group.area,
          file: group.file,
          binding: control.binding
        }),
        binding: control.binding,
        component: control.component,
        source: `${group.file}:${control.line}`,
        sourceToken: control.binding,
        ...details,
        verification: EVIDENCE_BY_AREA[group.area]
          || `静态闭环：${group.file} 控件绑定、保存路径审计与前端构建`
      })
    }
  }
  return [...entries, ...STRUCTURED_CONFIGURATIONS].sort((a, b) =>
    a.domain.localeCompare(b.domain, 'zh-CN')
      || a.area.localeCompare(b.area, 'zh-CN')
      || a.source.localeCompare(b.source, 'zh-CN')
  )
}

export function configurationSourceCoverage() {
  const files = [...new Set(CONFIGURATION_SOURCES.map(group => group.file))]
  return files.map(file => {
    const groups = CONFIGURATION_SOURCES.filter(group => group.file === file)
    const all = modelControls(file)
    const included = all.filter(control =>
      groups.some(group => group.patterns.some(pattern => pattern.test(control.binding)))
    )
    const ignorePatterns = (IGNORED_UI_BINDINGS[file] || []).map(pattern => new RegExp(pattern))
    const ignored = all.filter(control =>
      ignorePatterns.some(pattern => pattern.test(control.binding))
    )
    const classified = new Set([...included, ...ignored].map(item => item.binding))
    return {
      file,
      all,
      included,
      ignored,
      unclassified: all.filter(item => !classified.has(item.binding)),
      uniqueIncluded: new Set(included.map(item => item.binding)).size
    }
  })
}
