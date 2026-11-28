// 这些前缀由表单数据源执行器组装。页面取值说明和问号共用同一份清单。
export const FORM_DATA_SOURCE_INPUT_SOURCES = Object.freeze([
  { prefix: 'data', description: '当前表单字段值，子表里取当前行', examples: ['data.country_code'] },
  { prefix: 'params', description: '当前表单声明并接收的输入参数', examples: ['params.regionType'] },
  { prefix: 'ctx', description: '当前表单入口提供的上下文参数', examples: ['ctx.currentUser.userId', 'ctx.currentDate'] },
  { prefix: 'context', description: '当前页面的运行信息', examples: ['context.mode', 'context.formId', 'context.entityId'] },
  { prefix: 'input', description: '执行映射前平台组装的接口请求参数', examples: ['input.fieldCode', 'input.recordId', 'input.formData'] },
  { prefix: 'parent', description: '父表单记录，子表、明细等提供父记录的场景可用', examples: ['parent.recordId', 'parent.data.country_code'] },
  { prefix: 'row', description: '当前子表行及其状态', examples: ['row.index', 'row.isNew', 'row.data.country_code'] },
  { prefix: 'relation', description: '当前子表或关联的关系配置', examples: ['relation.relationType', 'relation.childRefFieldCode'] }
])

export const CASCADER_INPUT_MAPPING_NOTE = '级联请求会自动携带 input.cascader，其中 parentValue 是父节点值，path 是父节点路径，level 是层级（从 0 开始），loadMode 是加载方式。接口通过这些参数加载节点时，无需另填映射。'

const HELP_ENTRIES = {
  'entityList.selectionReturnMappings': {
    title: '返回映射怎么用',
    shape: 'array',
    summary:
      '数组中的每一项表示 sourceField 到 targetField。平台会逐条处理选中记录，并把映射结果写入 row.selectionData。',
    example: [
      { sourceField: 'id', targetField: 'customerId' },
      { sourceField: 'data.customer_name', targetField: 'customerName' }
    ],
    result:
      '可读取 row.selectionData.customerId 和 row.selectionData.customerName。',
    notes: [
      '标准字段可直接写 id、code、name；自定义字段建议写 data.<字段编码>。',
      'targetField 支持点号路径。留空或 [] 表示不生成 selectionData 映射。',
      '单选和多选都会逐条应用；这里只改变返回数据，不会自动回填表单。'
    ]
  },
  'entityList.statusLabelMap': {
    title: '文本映射怎么用',
    shape: 'object',
    summary:
      '键为单元格原始值，值为页面显示文本。原始值按字符串精确匹配，大小写不会自动转换。',
    example: {
      DRAFT: '草稿',
      APPROVED: '已通过'
    },
    result: '值 DRAFT 显示为“草稿”，值 APPROVED 显示为“已通过”。'
  },
  'entityList.statusColorMap': {
    title: '颜色映射怎么用',
    shape: 'object',
    summary:
      '键为转成小写后的状态值，值为 Element Plus 标签类型。',
    example: {
      draft: 'info',
      approved: 'success',
      rejected: 'danger'
    },
    result: 'approved 使用成功色，rejected 使用危险色。',
    notes: ['建议值使用 success、warning、danger、info。']
  },
  'entityForm.cascaderOptions': {
    title: '级联选项怎么用',
    shape: 'array',
    summary:
      '数组中的每个节点必须包含 value 和 label；有下级时通过 children 继续嵌套同样结构。',
    example: [
      {
        value: 'china',
        label: '中国',
        children: [
          { value: 'beijing', label: '北京' }
        ]
      }
    ],
    result: '级联选择器先显示“中国”，展开后显示“北京”，选中后保存 ["china", "beijing"]。',
    notes: [
      '静态模式使用此数组；接口模式在“级联选择配置”选择级联数据接口。',
      '全量接口返回完整树；逐级接口只返回直接下级，每项必须有 leaf 布尔值。',
      'value 使用非空字符串或有限数字，编码类型与已保存路径保持一致；disabled=true 表示不可选择。',
      'PC 和移动端均保存单条完整路径；未配置时不填充示例地区。'
    ]
  },
  'entityForm.dataSourceInputMapping': {
    title: '数据源输入映射怎么用',
    shape: 'object',
    summary:
      '左边填写接口接收的参数名，右边填写取值路径。参数名可以用点号表示嵌套位置。',
    example: {
      'filters.ownerId': 'data.owner_id',
      'context.scene': { literal: 'FORM' }
    },
    result:
      '数据源收到 input.filters.ownerId 和 input.context.scene。',
    notes: [
      ...FORM_DATA_SOURCE_INPUT_SOURCES.map(({ description, examples }) => `${description}。示例 ${examples.join('、')}`),
      '固定值用 {"literal": 值}。留空或填 {} 时发送平台组装的默认入参。'
    ]
  },
  'entityForm.dataSourceOutputMapping': {
    title: '数据源输出映射怎么用',
    shape: 'object',
    summary:
      '键是运行时结果的目标路径，值是数据源返回对象中的来源路径；data 表示解包后的响应数据，response 表示完整响应。',
    example: {
      assigneeName: 'data.user.name',
      remoteCode: 'response.code'
    },
    result: '生成包含 assigneeName 和 remoteCode 的结果对象。',
    notes: ['留空或 {} 时直接使用数据源原始返回值。']
  },
  'process.assigneeExtraParams': {
    title: '办理人接口 extraParams',
    shape: 'object',
    summary:
      '该对象原样传给当前选择的人员解析器，用于补充平台固定上下文之外的业务参数。',
    example: {
      level: 2,
      includePartTime: false
    },
    result: '人员解析器可从 extraParams.level 等路径读取参数。',
    notes: ['只填写当前人员接口声明并支持的参数。']
  },
  'process.sequenceConditionParams': {
    title: '业务条件接口参数怎么填',
    shape: 'object',
    summary:
      '填写所选 Java 条件接口需要的业务参数，平台原样传给 request.extraParams()。无额外参数时填 {}；只填参数对象本身，不要再套 providerCode 或 extraParams。',
    example: {
      nodeIds: ['Task_Finance', 'Task_Legal'],
      expectedAction: 'approve'
    },
    result:
      '接口通过 request.extraParams().get("nodeIds") 取得节点列表，通过 request.extraParams().get("expectedAction") 取得期望结果，再结合本实例的审批记录自行判断并返回 true 或 false。',
    notes: [
      '示例键名不是平台内置规则，必须由所选接口实现读取；只填写该接口支持的键，必填项和类型可在 validate(extraParams) 中校验。',
      'nodeIds 如用于定位审批节点，应填节点“常用 → 基本配置”中的节点 ID，不填节点名称；接口需自行处理会签汇总和退回重审轮次。',
      '流程实例 ID、连线、起止节点、实体及变量上下文由平台自动传入，不必在这里重复配置。',
      '此处不执行 ${变量名} 替换或表达式；动态值请在接口中通过 request.variables().get("变量名") 读取。',
      '使用标准 JSON：键名和文本加双引号，布尔值写 true/false，节点列表使用数组；不支持注释和末尾逗号。'
    ]
  },
  'process.restHeaders': {
    title: 'REST 请求头怎么用',
    shape: 'object',
    summary:
      '键为 Header 名称，值会转成字符串并替换 ${流程变量} 后发送。',
    example: {
      'X-Business-Ref': '${businessRef}',
      'X-Client-Type': 'workflow'
    },
    result: '发送 X-Business-Ref 和 X-Client-Type 请求头。',
    notes: [
      '安全策略禁止 Authorization、Cookie、Host、Content-Length 以及名称中包含 token、secret、api-key、credential、password 的 Header。',
      'Content-Type、幂等键和流程追踪 Header 由平台自动补充。'
    ]
  },
  'process.restBody': {
    title: 'REST JSON 请求体怎么用',
    shape: 'object-or-array',
    summary:
      '仅支持 application/json。对象或数组中的字符串可使用 ${流程变量}，运行时替换后作为请求体发送。',
    example: {
      businessId: '${entityDataId}',
      approved: true
    },
    result: '流程变量替换后发送一个 JSON 对象。',
    notes: ['GET 请求不发送 Body。']
  },
  'process.restQueryParams': {
    title: 'REST 查询参数怎么用',
    shape: 'object',
    summary:
      '键为 URL 参数名，值会转成字符串、替换 ${流程变量} 并进行 URL 编码。',
    example: {
      businessNo: '${businessNo}',
      pageSize: 20
    },
    result: '请求 URL 追加 businessNo 和 pageSize。'
  },
  'process.restResultMapping': {
    title: 'REST 结果映射怎么用',
    shape: 'object',
    summary:
      '键是响应 JSON 的点号路径，值是要写入的流程变量名。',
    example: {
      'data.id': 'remoteId',
      status: 'remoteStatus'
    },
    result: '响应 data.id 写入 remoteId，status 写入 remoteStatus。'
  },
  'process.dmnInputVariables': {
    title: 'DMN 输入变量怎么用',
    shape: 'object',
    summary:
      '键是决策表输入变量名，值可以是常量或精确的 ${流程变量名} 引用。',
    example: {
      amount: '${expenseAmount}',
      region: 'CN',
      urgent: true
    },
    result: 'DMN 收到 amount、region、urgent 三个输入变量。',
    notes: [
      '输入框留空时，运行时把全部流程变量传给决策表；填写 {} 时不传任何输入变量。'
    ]
  },
  'process.callInputParameters': {
    title: '子流程输入参数怎么用',
    shape: 'object',
    summary:
      '键是子流程目标变量名，值是父流程来源变量名或 ${表达式}。',
    example: {
      childAmount: '${amount}',
      applicantId: 'starter'
    },
    result: '父流程 amount 传给子流程 childAmount，starter 传给 applicantId。'
  },
  'process.callOutputParameters': {
    title: '子流程输出参数怎么用',
    shape: 'object',
    summary:
      '键是父流程目标变量名，值是子流程来源变量名或 ${表达式}。',
    example: {
      parentResult: '${subProcessResult}'
    },
    result: '子流程 subProcessResult 回写父流程 parentResult。'
  },
  'process.ccExtraParams': {
    title: '知会人员接口 extraParams',
    shape: 'object',
    summary:
      '该对象原样传给当前知会规则选择的人员解析器。',
    example: {
      level: 2,
      includeDisabled: false
    },
    result: '解析器按参数返回知会收件人。',
    notes: ['只填写当前人员接口声明并支持的参数。']
  },
  'process.actionParams': {
    title: '流程动作参数怎么用',
    shape: 'object',
    summary:
      '页面会把参数行组装成 JSON 对象并传给 FlowActionContext.extraParams；类型化处理器还可直接映射为参数类。',
    example: {
      noticeType: 'APPROVAL',
      retryable: true,
      amount: '${amount}'
    },
    result:
      '静态文本、数字和布尔值保持原类型；${amount} 在执行时替换为同名流程变量值。',
    notes: ['流程变量只支持精确的 ${变量名}，不执行运算、方法调用或任意表达式。']
  }
}

export const JSON_CONFIG_HELP = Object.freeze(HELP_ENTRIES)

export function getJsonConfigHelp(helpKey, overrides = null) {
  const base = JSON_CONFIG_HELP[helpKey]
  if (!base && !overrides) return null
  return {
    ...(base || {}),
    ...(overrides || {})
  }
}

export function getJsonShapeLabel(shape) {
  return {
    object: 'JSON 对象',
    array: 'JSON 数组',
    'object-or-array': 'JSON 对象或数组'
  }[shape] || 'JSON'
}

export function buildSchemaJsonHelp(item = {}) {
  if (item.helpKey && JSON_CONFIG_HELP[item.helpKey]) return null
  const shape = item.jsonShape || 'object-or-array'
  const fallbackExample = shape === 'array' ? [] : {}
  return {
    title: `${item.label || item.key || '扩展参数'}怎么用`,
    shape,
    summary:
      item.description
      || '该字段由扩展 Schema 声明。平台会校验 JSON 语法和已声明的数据形态，具体键名由对应扩展解释。',
    example: item.example ?? fallbackExample,
    notes: [
      '自定义扩展应在 Schema 中提供 description、jsonShape、example 或 helpKey，避免配置含义不明确。'
    ]
  }
}
