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
  'entityList.fixedFilters': {
    title: '固定条件怎么用',
    shape: 'object',
    summary:
      '键为实体字段编码，值为固定查询值。运行时会在用户查询条件之后合并该对象，因此同名固定条件不能被页面输入覆盖。',
    example: {
      status: 'APPROVED'
    },
    result: '该列表只查询 status=APPROVED 的记录。',
    notes: [
      '这是服务端可信条件，保存后需发布列表配置才会生效。',
      '需要运算符时使用字段对应的 _op 配置；不确定时优先使用等值条件。'
    ]
  },
  'entityList.contextBinding': {
    title: '上下文绑定怎么用',
    shape: 'object',
    summary:
      '该对象会随已发布列表 Schema 返回，供自定义列表组件或查询扩展解释。平台默认动态查询不会仅凭这里的键自动生成关联条件。',
    example: {
      parentField: 'project_id'
    },
    result:
      '只有自定义组件或 Provider 明确读取 parentField 时才会产生对应效果。',
    notes: [
      '默认可信关联过滤来自调用方 context.relationKey 和已注册的 EntityListContextResolver。',
      '不要在这里保存用户可篡改的值或密钥；未接入扩展时请保持 {}。'
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
    result: '级联选择器先显示“中国”，展开后显示“北京”。'
  },
  'entityForm.init.apiQuery': {
    title: 'API Query 参数怎么用',
    shape: 'object',
    summary:
      '对象会作为请求的 Query 参数。字符串值支持从初始化上下文读取 {{路径}} 模板。',
    example: {
      projectId: '{{routeQuery.projectId}}',
      pageSize: 20
    },
    result: 'routeQuery.projectId 会替换后随请求 URL 发送。'
  },
  'entityForm.init.apiBody': {
    title: 'API 请求体怎么用',
    shape: 'object',
    summary:
      '对象会作为请求体发送，嵌套对象和数组中的字符串同样支持 {{路径}} 模板。',
    example: {
      ownerId: '{{userStore.userInfo.id}}',
      status: 'DRAFT'
    },
    result: '模板替换后的对象作为 data 传给 API。'
  },
  'entityForm.init.apiMapping': {
    title: 'API 字段映射怎么用',
    shape: 'object',
    summary:
      '键是要回填的表单字段编码，值是 API 响应中的来源路径。若配置了响应路径，来源路径从截取后的对象开始计算。',
    example: {
      projectName: 'name',
      projectCode: 'code'
    },
    result: '响应中的 name 和 code 分别回填 projectName、projectCode。'
  },
  'entityForm.init.entityFilters': {
    title: '实体过滤参数怎么用',
    shape: 'object',
    summary:
      '对象会作为目标实体列表查询参数，用于取得初始化记录；默认取查询结果的第一条。',
    example: {
      status: 'APPROVED',
      project_id: '{{routeQuery.projectId}}'
    },
    result: '查询满足条件的实体记录，再按“取第几条”选择来源记录。'
  },
  'entityForm.init.entityMapping': {
    title: '实体字段映射怎么用',
    shape: 'object',
    summary:
      '键是要回填的当前表单字段编码，值是来源实体记录中的字段路径。',
    example: {
      projectName: 'name',
      ownerName: 'data.owner_name'
    },
    result: '来源记录的 name 和 data.owner_name 回填到当前表单。'
  },
  'entityForm.init.staticValues': {
    title: '静态值怎么用',
    shape: 'object',
    summary:
      '对象会直接作为表单初始化值返回，键必须使用当前表单绑定的字段编码。',
    example: {
      status: 'DRAFT',
      request_type: 'CHANGE'
    },
    result: '新建表单时直接带出 status 和 request_type。'
  },
  'entityForm.init.customParams': {
    title: '自定义初始化参数怎么用',
    shape: 'object',
    summary:
      '对象保存在 custom.params 中，注册的自定义初始化器自行读取并解释这些参数。',
    example: {
      scene: 'project-change',
      includeHistory: false
    },
    result: '初始化器通过 config.params.scene 等路径读取参数。',
    notes: ['参数名称和类型必须与所选初始化器的约定一致。']
  },
  'entityForm.dataSourceInputMapping': {
    title: '数据源输入映射怎么用',
    shape: 'object',
    summary:
      '键是发给数据源的目标路径，值是 data、context 或 input 中的来源路径；常量使用 {"literal": 值}。',
    example: {
      'filters.ownerId': 'data.owner_id',
      'context.scene': { literal: 'FORM' }
    },
    result:
      '数据源收到 input.filters.ownerId 和 input.context.scene。',
    notes: ['留空或 {} 时使用平台组装的原始输入对象。']
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
