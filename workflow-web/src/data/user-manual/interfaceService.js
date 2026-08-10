const fieldColumns = [
  { key: 'field', label: '配置项' },
  { key: 'meaning', label: '含义' },
  { key: 'when', label: '什么时候使用' },
  { key: 'how', label: '怎么配置' },
  { key: 'effect', label: '运行效果 / 注意事项' }
]

const optionColumns = [
  { key: 'option', label: '选项' },
  { key: 'meaning', label: '含义' },
  { key: 'when', label: '适用场景' },
  { key: 'notes', label: '限制 / 注意事项' }
]

const operationSchemaExample = `{
  "type": "object",
  "required": ["customerId"],
  "properties": {
    "customerId": { "type": "string" },
    "pageNum": { "type": "integer" },
    "pageSize": { "type": "integer" }
  }
}`

const pageResultSchemaExample = `{
  "type": "object",
  "required": ["records", "total"],
  "properties": {
    "records": {
      "type": "array",
      "items": { "type": "object" }
    },
    "total": { "type": "integer" },
    "pageNum": { "type": "integer" },
    "pageSize": { "type": "integer" }
  }
}`

const listResultExample = `{
  "records": [
    {
      "id": "1001",
      "name": "示例客户",
      "status": "ACTIVE"
    }
  ],
  "total": 1,
  "pageNum": 1,
  "pageSize": 20
}`

const connectorConfigExample = `{
  "connectorConfigId": "customer-center"
}`

const computeConfigExample = `{
  "inputs": ["firstName", "lastName"],
  "separator": " "
}`

export default {
  eyebrow: 'USER MANUAL · INTERFACE SERVICE',
  title: '接口服务用户手册',
  subtitle: '详细说明接口服务何时使用、如何创建、每个配置项的含义，以及如何通过事件绑定接入列表、表单、字段和按钮。',
  version: '统一接口服务与事件绑定基线',
  updatedAt: '2026-08-10',
  intro: [
    {
      title: '接口服务定义能力，事件绑定决定使用位置',
      type: 'info',
      text: '接口服务负责定义“调用什么能力、有哪些操作、输入输出是什么”；事件绑定负责定义“什么时候调用、参数从哪里来、结果写到哪里”。服务保存后不会自动改变任何页面，必须完成事件绑定并发布对应页面配置后才会在运行时生效。'
    },
    {
      title: '不要在页面里直接填写 URL 或代码',
      type: 'warning',
      text: '外部 HTTP 地址、认证和凭据由开放集成中的受控 Connector 管理。接口服务只选择已注册 Provider 或 Connector，并引用稳定编码；基础配置和操作配置禁止 sql、script、url、jdbcUrl、command、expression 等键。'
    },
    {
      title: '先调试，再绑定，再发布',
      type: 'success',
      text: '推荐先创建服务和操作，选择真实表单或列表上下文执行调试；确认 Schema、权限和返回结构正确后再配置事件绑定，最后发布表单或列表并使用真实角色验证。'
    }
  ],
  sections: [
    {
      id: 'interface-service-overview',
      index: '01',
      title: '什么时候使用',
      summary: '判断应该使用平台默认处理、接口服务，还是开放集成 Connector。',
      topics: [
        {
          id: 'interface-service-purpose',
          title: '接口服务解决什么问题',
          lead: '当列表、表单、字段或按钮需要复用平台内置能力、后端 Provider 或外部系统能力时，使用接口服务建立统一、可审计的调用入口。',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '列表改用自定义查询', meaning: '用一个 LIST/READ 操作替代平台默认实体分页查询。', when: '列表数据来自外部系统、聚合服务或特殊后端查询。', notes: '在“列表设置 → 查询实现 → 列表查询接口”直接选择服务和操作。' },
                { option: '表单加载外部详情', meaning: '打开编辑页时根据记录 ID 查询完整详情。', when: '平台实体只保存索引字段，详情由客户中心、ERP 等系统提供。', notes: '绑定 DETAIL_LOAD；返回结果需要映射为表单字段。' },
                { option: '保存前校验或转换', meaning: '平台保存前调用接口校验状态、额度或转换提交结构。', when: '业务校验不能仅靠字段必填和本地规则完成。', notes: '使用 BEFORE，失败策略通常选停止执行；保留平台默认保存。' },
                { option: '完全自定义保存', meaning: '由自定义 WRITE 操作替代平台实体新增或修改。', when: '主数据完全由外部系统维护，平台不能执行默认保存。', notes: '使用 REPLACE；接口需承担权限后的业务保存、幂等和标准结果返回。' },
                { option: '选择实体后回填', meaning: '选择客户、项目等记录后，把电话、负责人等值写入其他字段。', when: '表单需要联动带回选择记录的附加信息。', notes: '绑定 ENTITY_SELECTED；已有选择结果可只做映射，信息不足时先调用详情操作。' },
                { option: '按钮执行业务动作', meaning: '工具栏、行、表单或字段按钮触发接口操作。', when: '需要作废、同步、校验、生成文件、刷新列表等动作。', notes: '按钮只绑定事件，不填写前端函数名；写操作应选择 WRITE。' },
                { option: '静态选项、字典或上下文', meaning: '为字段提供固定选项、平台字典或当前用户上下文。', when: '下拉选项不需要自定义 Java 或 HTTP 调用。', notes: '分别使用平台静态数据、平台字典、运行时上下文。' }
              ]
            }
          ]
        },
        {
          id: 'interface-service-not-needed',
          title: '哪些情况不需要配置',
          blocks: [
            {
              type: 'bullets',
              items: [
                '普通实体列表查询、详情加载、新增、修改和删除已经由平台默认处理满足需求时，不需要增加接口服务。',
                '只需要调整列表显示列、查询字段、按钮名称或表单布局时，应在列表或表单设计器配置，不需要接口服务。',
                '只做实体选择字段之间的直接回填，并且选择结果已经包含所需值时，可以在事件步骤中只配置结果回填，不必调用接口。',
                '外部系统主动调用平台流程 API、接收 Webhook 或管理外部凭据时，应使用“开放集成”，不是直接新建接口服务。'
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '平台系统实体限制',
              text: '平台系统实体继续使用可信只读查询，不能配置 LIST_QUERY 查询接口、查询 Provider 或自定义列表组件，也不能通过通用接口服务执行新增、修改或删除。'
            }
          ]
        }
      ]
    },
    {
      id: 'interface-service-page',
      index: '02',
      title: '页面入口与权限',
      summary: '认识接口服务列表、筛选、状态和管理操作。',
      topics: [
        {
          id: 'interface-service-entry',
          title: '进入页面',
          lead: '从左侧菜单进入“接口服务”。页面分为“接口服务”和“事件绑定”两个页签。',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '打开接口服务页签', text: '先维护服务、操作、Schema、超时和缓存，并执行调试。' },
                { title: '打开事件绑定页签', text: '选择实体、配置层级和绑定位置，把已启用的服务操作放入业务事件执行链。' },
                { title: '返回页面设计器发布', text: '事件绑定保存的是草稿；发布对应表单或列表后，运行页面才读取新绑定。' }
              ]
            },
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: 'system:interface-service:list', meaning: '查看接口服务目录、操作和配置摘要。', when: '只读管理员、审计人员。', how: '通过角色菜单和功能权限授权。', effect: '可以查看页面与操作目录，不能保存或删除。' },
                { field: 'system:interface-service:update', meaning: '新增、编辑、启停和删除接口服务。', when: '平台配置管理员。', how: '与全局配置访问权限一起授予。', effect: '保存时执行作用域、Schema、Provider 和并发版本校验。' },
                { field: 'system:interface-service:test', meaning: '执行接口服务调试。', when: '需要联调 Provider、Connector 和映射的人员。', how: '只授予可信管理员；调试仍要求选择真实业务上下文。', effect: '按当前用户权限和数据范围执行，不是绕过权限的测试入口。' }
              ]
            }
          ]
        },
        {
          id: 'interface-service-list-fields',
          title: '列表页配置项与操作',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '搜索服务名称或编码', meaning: '在当前已加载服务中按名称、编码模糊过滤。', when: '服务较多时快速定位。', how: '输入关键词；清空恢复全部。', effect: '只改变页面展示，不改变服务。' },
                { field: '全部实现类型', meaning: '按服务实现类型过滤。', when: '只查看平台查询、Provider 或 Connector。', how: '选择一种类型或清空。', effect: '只改变页面展示。' },
                { field: '刷新', meaning: '重新加载服务、可用 Provider/Connector 目录和实体上下文。', when: '其他管理员刚修改服务，或后端注册能力发生变化。', how: '点击页面右上角刷新图标。', effect: '丢弃当前列表筛选以外的缓存数据，不修改服务。' },
                { field: '服务列', meaning: '显示服务名称和稳定服务编码。', when: '识别业务含义和技术引用。', how: '名称给人看，编码给配置和审计使用。', effect: '编码创建后不可在页面修改。' },
                { field: '实现列', meaning: '显示实现类型以及 Provider/Connector 编码。', when: '确认服务最终由谁执行。', how: '在编辑弹窗选择实现类型和受控连接。', effect: '类型变化会改变运行逻辑和所需基础配置。' },
                { field: '作用范围列', meaning: '显示服务可用于全局、实体、表单或列表。', when: '控制服务的配置边界。', how: '在编辑弹窗选择范围和范围对象。', effect: '运行时仍会继续校验页面发布版本、权限和数据范围。' },
                { field: '操作列（能力摘要）', meaning: '显示服务包含的查询或写操作。', when: '确认一个服务可复用哪些能力。', how: '在“服务操作”区域维护。', effect: '事件绑定最终选择的是“服务 + 操作”。' },
                { field: '策略列', meaning: '显示服务级超时和缓存时间。', when: '排查响应慢、结果未更新。', how: '在编辑弹窗配置。', effect: '操作没有独立策略时继承服务策略。' },
                { field: '状态列', meaning: '显示启用或停用。', when: '临时下线能力或上线新服务。', how: '编辑服务并切换“启用”。', effect: '停用服务不能被运行时执行。' },
                { field: '调试', meaning: '在真实表单或列表上下文中执行一个操作。', when: '保存后、绑定前联调。', how: '点击“调试”，选择操作和业务上下文。', effect: 'WRITE 操作可能修改数据，不能把调试当作无副作用预览。' },
                { field: '编辑', meaning: '修改服务属性、操作和 Schema。', when: '新增操作、调整连接或规则。', how: '点击“编辑”；提交时携带修订号。', effect: '多人同时修改时旧版本保存会失败，需要刷新后合并。' },
                { field: '删除', meaning: '删除服务定义。', when: '确认不再被草稿或后续配置引用。', how: '点击“删除”并确认。', effect: '历史已发布快照不被直接改写，但新执行链不应继续依赖已删除服务。' }
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'interface-service-editor',
      index: '03',
      title: '创建接口服务',
      summary: '逐项说明服务基本信息、实现类型、作用范围和执行策略。',
      topics: [
        {
          id: 'interface-service-basic-fields',
          title: '基本配置项',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '服务名称', meaning: '管理员看到的中文业务名称。', when: '所有服务必填。', how: '使用“客户服务”“项目查询服务”等可识别名称。', effect: '显示在服务列表和事件绑定选择器中，可以修改。' },
                { field: '服务编码', meaning: '服务的稳定技术标识。', when: '所有服务必填。', how: '建议使用小写英文、数字和短横线，如 customer-service；创建后页面锁定。', effect: '用于长期识别服务，不能依赖名称作为稳定引用。' },
                { field: '实现类型', meaning: '决定服务由平台内置逻辑、Java Provider、Connector、上下文或结构化计算执行。', when: '创建服务时必须按数据来源选择。', how: '参见“实现类型详解”。', effect: '类型选错会导致配置字段无效或运行结果不符合预期。' },
                { field: '受控连接', meaning: '后端已注册的 Provider 或 Connector 编码。', when: '实现类型为“平台注册能力”或“HTTP 受控连接”时必填。', how: '从下拉目录选择，不手工填写 URL。', effect: '运行时只允许调用目录中的受控实现。' },
                { field: '作用范围', meaning: '限制服务的业务配置边界。', when: '服务只应被某个实体、表单或列表使用时。', how: '选择全局、实体、表单或列表。', effect: '非全局必须指定范围对象；写操作还会校验目标实体是否匹配。' },
                { field: '范围对象', meaning: '实体 ID、表单 ID 或列表 ID。', when: '作用范围不是全局时必填。', how: '实体范围使用选择器；表单、列表范围填写对应配置 ID。', effect: '对象不存在、不是动态实体或无配置权限时保存失败。' },
                { field: '超时', meaning: '一次操作允许执行的最长时间。', when: '所有服务都应根据下游响应设置。', how: '100～30000 毫秒，默认 3000 毫秒。', effect: '超时后按失败策略处理；过长会占用请求线程，过短会造成误失败。' },
                { field: '缓存', meaning: '相同用户、输入、页面发布版本和数据范围下复用结果的时间。', when: '只读且短时间允许复用的数据。', how: '0～86400 秒；0 表示不缓存。', effect: '缓存键包含用户、租户、输入、上下文和数据范围，不跨权限共享；WRITE 操作建议保持 0。' },
                { field: '启用', meaning: '控制服务是否可执行。', when: '灰度上线、故障停用或配置尚未准备好。', how: '打开后可调试和运行；关闭后保存定义但拒绝执行。', effect: '停用不会自动删除事件绑定，恢复前相关步骤会失败。' }
              ]
            }
          ]
        },
        {
          id: 'interface-service-scope-options',
          title: '作用范围怎么选',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: 'GLOBAL 全局', meaning: '服务可以在不同实体、表单和列表中复用。', when: '平台字典、通用客户服务、通用通知等跨业务能力。', notes: '范围宽不代表跳过权限；运行时仍按当前页面和用户校验。' },
                { option: 'ENTITY 实体', meaning: '服务只属于一个动态业务实体。', when: '客户、项目、订单等实体专属查询或保存。', notes: '推荐用于实体写操作，避免被其他实体误绑定。' },
                { option: 'FORM 表单', meaning: '服务只为一个具体表单配置。', when: '某个特殊表单独有的初始化或提交校验。', notes: '复用性最低；同实体其他表单不能直接视为同一范围。' },
                { option: 'LIST 列表', meaning: '服务只为一个具体列表配置。', when: '某个列表独有的聚合查询、导出或行操作。', notes: '列表复制后需要重新确认范围和绑定。' }
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'interface-service-types',
      index: '04',
      title: '实现类型详解',
      summary: '说明六种实现类型什么时候使用、基础配置写什么、会返回什么。',
      topics: [
        {
          id: 'interface-service-type-reference',
          title: '六种实现类型',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '平台字典 DICTIONARY', meaning: '按字典编码返回 label、value、disabled、children。', when: '下拉、单选、多选需要平台字典。', notes: '基础配置填写 dictCode；不需要 Provider。' },
                { option: '平台静态数据 STATIC_OPTIONS', meaning: '直接返回配置中的固定 options。', when: '少量稳定选项，不值得创建字典。', notes: '基础配置填写 options 数组；变更需要修改服务配置。' },
                { option: '平台注册能力 REGISTERED_PROVIDER', meaning: '调用后端实现 UiDataSourceProvider 的受控 Java 能力。', when: '需要复杂查询、聚合或内部系统逻辑，且应在同一应用事务边界内受控执行。', notes: '必须选择受控连接；配置结构和输入输出由 Provider 契约决定。' },
                { option: 'HTTP 受控连接 INTEGRATION_CONNECTOR', meaning: '通过开放集成中已审核的 Connector 调用外部 HTTP JSON 服务。', when: '查询 ERP、CRM，或向外部系统创建、更新数据。', notes: '先在开放集成创建 Connector 和 Secret；接口服务不允许填写任意 URL 或凭据。' },
                { option: '运行时上下文 RUNTIME_CONTEXT', meaning: '返回当前事件上下文。', when: '需要把用户、记录、选择项或页面上下文映射到表单字段或后续步骤。', notes: '不需要基础配置；返回内容取决于触发事件和服务端提供的可信上下文。' },
                { option: '结构化计算 STRUCTURED_COMPUTE', meaning: '执行白名单内的简单计算，不运行表达式或脚本。', when: '取第一个非空值、拼接文本、求和或条件取值。', notes: '操作编码必须使用 COALESCE、CONCAT、SUM 或 IF_EQUALS；通过 config 指定输入路径和参数。' }
              ]
            }
          ]
        },
        {
          id: 'interface-service-type-config',
          title: '各类型基础配置示例',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: 'DICTIONARY.dictCode', meaning: '平台字典编码。', when: '返回某一字典的树形选项。', how: '基础配置填写 {"dictCode":"customer_status"}。', effect: '字典项状态会映射为 disabled，层级映射为 children。' },
                { field: 'STATIC_OPTIONS.options', meaning: '固定选项数组。', when: '选项少且不需要字典管理。', how: '基础配置填写 {"options":[{"label":"启用","value":"1"}]}。', effect: '运行时原样返回 options。' },
                { field: 'REGISTERED_PROVIDER 配置', meaning: 'Provider 自定义参数。', when: '所选 Provider 需要实体编码、查询模式或其他受控参数。', how: '按 Provider 开发文档填写基础配置和操作配置。', effect: '服务级配置先加载，操作配置覆盖同名键。' },
                { field: 'INTEGRATION_CONNECTOR.connectorConfigId', meaning: '开放集成 Connector 的稳定配置编码。', when: '通过受控 HTTP Connector 调用外部系统。', how: '基础配置只写 Connector 编码；事件选择的接口操作编码应与 Connector operation 一致。', effect: '平台生成幂等键并传入当前用户、页面、数据权限摘要。' },
                { field: 'STRUCTURED_COMPUTE.inputs', meaning: '从接口输入中读取值的点路径数组。', when: '计算只使用部分输入字段。', how: '例如 ["firstName","lastName"]；未配置时使用全部输入值。', effect: '路径不存在时得到 null。' },
                { field: 'STRUCTURED_COMPUTE.separator', meaning: 'CONCAT 文本拼接分隔符。', when: '拼接姓名、编码或展示文本。', how: '例如空格、短横线或逗号。', effect: 'null 按空字符串参与拼接。' },
                { field: 'STRUCTURED_COMPUTE.equals / then / else', meaning: 'IF_EQUALS 的比较值和两个返回分支。', when: '简单二选一映射。', how: '第一个 inputs 值与 equals 严格相等时返回 then，否则返回 else。', effect: '不支持任意表达式。' }
              ]
            },
            { type: 'code', title: 'HTTP Connector 基础配置', language: 'json', code: connectorConfigExample },
            { type: 'code', title: '结构化计算操作配置', language: 'json', code: computeConfigExample }
          ]
        }
      ]
    },
    {
      id: 'interface-service-operations',
      index: '05',
      title: '服务操作与 Schema',
      summary: '一个服务可以包含多个操作；操作是事件绑定真正执行的最小单元。',
      topics: [
        {
          id: 'interface-service-operation-fields',
          title: '服务操作配置项',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '增加操作', meaning: '在当前服务下新增一个能力。', when: '同一业务服务需要查询列表、加载详情、保存、校验等多个动作。', how: '点击“增加操作”，填写名称、编码和数据影响。', effect: '接口服务至少需要一个操作；操作按配置顺序展示。' },
                { field: '操作名称', meaning: '管理员看到的中文动作名称。', when: '每个操作必填。', how: '例如“查询客户列表”“获取客户详情”“校验客户状态”。', effect: '显示在事件绑定和调试操作选择器中。' },
                { field: '操作编码', meaning: '服务内唯一的稳定技术标识。', when: '每个操作必填。', how: '使用稳定英文编码，如 queryCustomers、getCustomer；Connector 和结构化计算需与下游操作编码一致。', effect: '同一服务不能重复；事件绑定按该编码执行。' },
                { field: '数据影响：只读查询 READ', meaning: '声明操作不应修改业务数据。', when: '列表、详情、字典、校验查询。', how: '选择“只读查询”。', effect: '列表查询简化入口只显示已启用服务中的 READ 操作。' },
                { field: '数据影响：修改数据 WRITE', meaning: '声明操作可能新增、修改、删除或触发外部副作用。', when: '保存、作废、同步、发送、生成任务。', how: '选择“修改数据”。', effect: '调试时也可能产生真实副作用；缓存应设为 0，并依赖幂等。' },
                { field: '删除操作', meaning: '从服务草稿移除操作。', when: '确认没有事件绑定继续引用该编码。', how: '点击操作标题右侧删除图标。', effect: '已引用的绑定在后续校验或执行时会提示操作不存在。' }
              ]
            }
          ]
        },
        {
          id: 'interface-service-advanced-config',
          title: '操作级和服务级高级配置',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '操作配置', meaning: '当前操作覆盖服务基础配置的 JSON 对象。', when: '同一服务的操作需要不同目标动作、查询模式或固定参数。', how: '填写合法 JSON 对象；同名键覆盖基础配置。', effect: '系统还会写入当前 operation 编码；禁止 URL、SQL、脚本、命令和表达式键。' },
                { field: '操作输入 Schema', meaning: '校验当前操作最终收到的 input。', when: '希望在调用 Provider/Connector 前拒绝缺字段或错类型。', how: '使用受支持的 JSON Schema 子集；空对象表示不校验。', effect: '校验失败不会执行接口，且不会被空结果失败策略吞掉。' },
                { field: '操作输出 Schema', meaning: '校验接口执行结果。', when: '事件映射依赖固定返回结构。', how: '描述对象、数组和字段类型；空对象表示不校验。', effect: '缓存命中和实际执行结果都会校验。' },
                { field: '服务级基础配置', meaning: '所有操作共享的 JSON 配置。', when: '多个操作共享实体、Connector、字典或固定参数。', how: '写公共键；差异放入操作配置。', effect: '操作配置覆盖同名服务配置。' },
                { field: '服务级输入 Schema', meaning: '服务基础定义中的输入契约。', when: '主要用于历史单操作服务兼容。', how: '新建多操作服务应在每个操作中明确配置输入 Schema。', effect: '多操作服务执行时使用操作级 Schema；操作级空对象表示该操作不校验输入。' },
                { field: '服务级输出 Schema', meaning: '服务基础定义中的输出契约。', when: '主要用于历史单操作服务兼容。', how: '新建多操作服务应在每个操作中明确配置输出 Schema。', effect: '多操作服务执行时使用操作级 Schema；操作级空对象表示该操作不校验输出。' }
              ]
            },
            {
              type: 'callout',
              tone: 'info',
              title: '当前支持的 Schema 子集',
              text: '支持 type、required、properties、items；type 支持 object、array、string、number、integer、boolean。当前不执行 additionalProperties、长度、正则、minimum、enum 等完整 JSON Schema 关键字，不要依赖这些关键字完成业务校验。'
            },
            {
              type: 'callout',
              tone: 'success',
              title: 'Provider 配置键会显示在编辑弹窗',
              text: '选择“平台注册能力”及具体 Provider 后，编辑弹窗会读取 Provider 的 configurationSchema，列出可写入基础配置或操作配置的参数、含义和默认值。公共参数放基础配置，只有单个操作不同的参数才放操作配置。'
            },
            { type: 'code', title: '输入 Schema 示例', language: 'json', code: operationSchemaExample },
            { type: 'code', title: '列表分页输出 Schema 示例', language: 'json', code: pageResultSchemaExample }
          ]
        }
      ]
    },
    {
      id: 'interface-service-debug',
      index: '06',
      title: '调试接口操作',
      summary: '使用真实业务上下文验证权限、数据范围、输入 Schema 和返回结构。',
      topics: [
        {
          id: 'interface-service-debug-fields',
          title: '调试窗口配置项',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '接口服务', meaning: '当前正在调试的服务，只读显示。', when: '从服务列表点击“调试”。', how: '需要切换服务时关闭弹窗并从另一行进入。', effect: '只执行当前服务中的操作。' },
                { field: '操作', meaning: '本次执行的具体操作。', when: '一个服务包含多个操作。', how: '选择操作名称和编码。', effect: '决定操作配置、Schema、READ/WRITE 语义和下游 operation。' },
                { field: '业务上下文', meaning: '选择按表单还是列表权限执行。', when: '所有调试都必选。', how: '根据最终绑定页面选择 FORM 或 LIST。', effect: '平台据此解析实体、发布配置、权限和数据范围。' },
                { field: '配置对象', meaning: '具体表单或列表 ID。', when: '所有调试都必选。', how: '选择最终计划使用该服务的对象。', effect: '不能使用不存在、无权或与服务范围不匹配的对象。' },
                { field: '事件用途', meaning: '模拟运行时事件编码。', when: '验证 LIST_LOAD、DETAIL_LOAD、DATA_UPDATE 等不同用途。', how: '选择与未来事件绑定一致的编码。', effect: '用途进入审计、缓存键和执行授权。' },
                { field: '输入参数', meaning: '传给操作的 JSON 对象。', when: '验证查询条件、记录 ID 或业务字段。', how: '填写合法 JSON；字段应符合输入 Schema。', effect: '非法 JSON 或 Schema 不匹配时不会调用接口。' },
                { field: '执行结果', meaning: '显示格式化后的返回值或错误信息。', when: '判断操作是否满足映射和分页要求。', how: '点击“执行调试”后查看。', effect: '结果只用于当前调试，不会自动保存为页面配置。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: 'WRITE 调试会产生真实副作用',
              text: '调试入口不是自动回滚的模拟器。对 WRITE 操作执行调试前，应使用测试数据、确认幂等键处理，并检查外部系统是否会真实新增、修改、通知或扣减。'
            }
          ]
        },
        {
          id: 'interface-service-debug-sequence',
          title: '推荐调试顺序',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '先测最小输入', text: '只传必填字段，确认 Provider 或 Connector 能被找到且权限通过。' },
                { title: '再测完整输入', text: '加入查询条件、页码、场景和上下文依赖字段，确认输入 Schema。' },
                { title: '核对返回结构', text: '列表操作确认 records、total、pageNum、pageSize；字段回填确认中文字段对应的数据路径。' },
                { title: '测试异常路径', text: '测试无权限、空结果、下游失败和超时，确认错误信息以及后续事件失败策略。' },
                { title: '最后再绑定', text: '记录服务编码、操作编码和结果路径，在事件绑定中按相同业务上下文配置。' }
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'interface-service-binding',
      index: '07',
      title: '事件绑定',
      summary: '决定接口何时执行、是否替代平台处理、输入从哪里来、结果写到哪里。',
      topics: [
        {
          id: 'interface-service-binding-scope',
          title: '先选择绑定对象',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '实体', meaning: '选择事件所属动态业务实体。', when: '配置实体默认、表单覆盖或列表覆盖前。', how: '使用实体选择器选择。', effect: '系统实体不会出现在可配置实体目录中。' },
                { field: '配置层级：实体默认', meaning: '为实体下普通表单和列表提供默认执行链。', when: '多个页面使用相同查询、详情或保存逻辑。', how: '选择 ENTITY；绑定位置固定为当前实体。', effect: '页面没有本级覆盖时继承。' },
                { field: '配置层级：表单覆盖', meaning: '只覆盖某一个表单。', when: '特殊表单需要不同详情、保存、字段或按钮逻辑。', how: '选择 FORM，再选择具体表单。', effect: '可绑定表单本身、字段或按钮。' },
                { field: '配置层级：列表覆盖', meaning: '只覆盖某一个列表。', when: '特殊列表需要不同查询、导出或按钮逻辑。', how: '选择 LIST，再选择具体列表。', effect: '可绑定列表本身或按钮。' },
                { field: '绑定位置：当前表单或列表', meaning: '事件属于页面主体。', when: '列表加载、详情、保存、导出、新增或删除。', how: '选择 OWNER。', effect: '事件使用当前页面配置 ID 解析。' },
                { field: '绑定位置：字段', meaning: '事件属于一个具体表单字段。', when: '字段变化、实体选择后、字段按钮点击。', how: '选择 FIELD，再按中文名称选择字段。', effect: '保存时使用稳定字段编码。' },
                { field: '绑定位置：按钮', meaning: '事件属于一个稳定按钮编码。', when: '工具栏、行或表单按钮需要接口动作。', how: '选择 BUTTON 并填写按钮稳定编码。', effect: '按钮编码必须与设计器中保存的按钮一致。' }
              ]
            }
          ]
        },
        {
          id: 'interface-service-events',
          title: '触发事件什么时候使用',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: 'LIST_LOAD / LIST_EXPORT', meaning: '列表加载事件 / 导出事件。', when: '加载或导出前后需要校验、补充参数、记录审计或触发联动。', notes: '自定义分页查询使用列表配置中的 LIST_QUERY 槽位，不通过 LIST_LOAD 保存查询数据源。' },
                { option: 'DETAIL_LOAD', meaning: '加载单条详情。', when: '编辑或查看前补充完整数据。', notes: 'REPLACE 时接口需返回可映射的完整记录。' },
                { option: 'DATA_CREATE / DATA_UPDATE', meaning: '新增 / 修改业务记录。', when: '对平台保存增加校验、同步，或完全自定义保存。', notes: 'WRITE 操作应具备幂等；REPLACE 后自定义接口承担主保存。' },
                { option: 'DATA_DELETE / DATA_BATCH_DELETE', meaning: '单条 / 批量删除。', when: '删除前校验、外部删除或删除后通知。', notes: '危险操作通常使用 STOP，避免部分成功。' },
                { option: 'FORM_OPEN / FORM_SAVE / FORM_RESET', meaning: '打开、保存、重置表单。', when: '初始化页面、保存编排或重置后联动。', notes: 'FORM_SAVE 最终会转入新增或修改事件；不要重复执行同一写操作。' },
                { option: 'FIELD_CHANGE / ENTITY_SELECTED', meaning: '字段变化 / 选择实体记录后。', when: '联动查询、计算、字段回填。', notes: '频繁变化字段应避免慢接口；选择结果已有数据时可只做映射。' },
                { option: 'FIELD_BUTTON_CLICK', meaning: '字段旁按钮点击。', when: '地址识别、编号生成、局部校验。', notes: '通过返回效果回填字段，不写前端函数名。' },
                { option: 'SUBFORM_LOAD / SUBFORM_SAVE', meaning: '加载 / 保存子表。', when: '子表数据来自特殊来源或保存前后需处理。', notes: '注意父子记录 ID、批量数据量和事务边界。' },
                { option: 'TOOLBAR_BUTTON_CLICK', meaning: '列表工具栏按钮点击。', when: '批量同步、导出任务、刷新或新增扩展动作。', notes: '可使用 selectedIds；无选中数据时条件应明确。' },
                { option: 'ROW_BUTTON_CLICK', meaning: '列表行按钮点击。', when: '查看、编辑以外的单条业务动作。', notes: '使用 recordId 和当前行数据。' },
                { option: 'FORM_BUTTON_CLICK', meaning: '表单自定义按钮点击。', when: '提交之外的校验、暂存、同步或生成动作。', notes: '按钮权限和适用条件仍先由页面运行时判断。' }
              ]
            }
          ]
        },
        {
          id: 'interface-service-binding-editor',
          title: '事件绑定配置项',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '触发事件', meaning: '本条执行链的系统触发点。', when: '新增绑定时必选。', how: '从当前层级和位置允许的事件中选择。', effect: '创建后不可直接改事件，需要删除后重建。' },
                { field: '继承方式：继承并追加 INHERIT', meaning: '保留上级自定义链，并追加当前层步骤。', when: '实体已有默认逻辑，特殊页面只增加额外校验或通知。', how: '选择“继承并追加”。', effect: '最终链由上级和本级共同组成。' },
                { field: '继承方式：替换上级 REPLACE', meaning: '当前层替换上级自定义链。', when: '特殊页面需要完全不同的自定义步骤。', how: '选择“替换上级”。', effect: '是否保留平台默认处理仍由步骤中的 REPLACE 决定。' },
                { field: '继承方式：禁用自定义 DISABLE', meaning: '忽略当前事件的自定义链，只保留平台默认处理。', when: '实体已有自定义默认，但某个页面必须回到平台行为。', how: '选择“禁用自定义”，无需步骤。', effect: '不是禁用整个业务事件。' },
                { field: '步骤名称', meaning: '执行链中给管理员看的步骤说明。', when: '建议每一步都填写。', how: '使用“校验客户状态”“同步 ERP”等动作名称。', effect: '显示在完整执行链和执行跟踪中。' },
                { field: '执行位置 BEFORE', meaning: '在平台默认处理或替代步骤前执行。', when: '校验、补充参数、转换输入。', how: '选择“前置”。', effect: 'STOP 失败会阻止后续平台处理。' },
                { field: '执行位置 REPLACE', meaning: '用本步骤替代平台默认处理。', when: '完全自定义查询、详情或保存。', how: '选择“替代平台处理”。', effect: '同一事件最多一个 REPLACE；配置后平台默认处理不执行。' },
                { field: '执行位置 AFTER', meaning: '在主处理完成后执行。', when: '通知、同步、刷新提示等后置动作。', how: '选择“后置”。', effect: '外部系统不能加入本地数据库事务，应依赖幂等和补偿。' },
                { field: '接口服务', meaning: '本步骤调用的服务。', when: '步骤需要执行接口。', how: '选择已启用服务；留空表示只做字段映射。', effect: '留空时必须配置结果回填，否则无法保存。' },
                { field: '接口操作', meaning: '服务中的具体 READ 或 WRITE 操作。', when: '选择接口服务后必填。', how: '从操作目录选择。', effect: '操作不存在或服务停用时执行失败。' },
                { field: '失败策略 STOP', meaning: '失败立即停止执行链。', when: '校验、主保存、删除等关键步骤。', how: '选择“停止执行”。', effect: '错误返回给当前业务操作。' },
                { field: '失败策略 CONTINUE', meaning: '记录失败后继续后续步骤。', when: '非关键通知或可降级辅助查询。', how: '选择“记录后继续”。', effect: '当前步骤结果为 null，不阻断后续处理。' },
                { field: '失败策略 EMPTY', meaning: '把当前步骤异常转换为空对象并继续。', when: '可接受无结果的辅助数据加载。', how: '选择“按空结果继续”。', effect: '后续映射必须能处理空值；关键权限、校验和写操作不建议使用。' },
                { field: '启用', meaning: '控制本条事件绑定是否参与解析。', when: '临时停用一条草稿绑定。', how: '关闭开关后保存。', effect: '不删除配置，但发布后的有效链不会使用停用绑定。' }
              ]
            }
          ]
        },
        {
          id: 'interface-service-mapping-condition',
          title: '输入参数映射、结果回填和条件',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '输入来源路径', meaning: '从事件状态读取值。', when: '接口参数不是完整原始 input，或参数名不同。', how: '例如 input.customerId、context.userId、selection.data.id、recordId、selectedIds。', effect: '路径不存在时得到 null。' },
                { field: '接口参数', meaning: '写入接口 input 的目标路径。', when: '把页面字段转换为接口契约。', how: '例如 customerId、filters.status。', effect: '当前输入映射按“目标路径 → 来源路径”保存；输入区应使用原值映射。' },
                { field: '结果来源路径', meaning: '从接口结果或事件状态读取值。', when: '回填表单、构造页面效果或转换分页结果。', how: '调用接口后使用 data.xxx 或 response.xxx；只做映射时可使用 input、selection、context。', effect: '数组可通过数字下标访问，例如 data.records.0.id。' },
                { field: '目标路径', meaning: '结果要写入的位置。', when: '字段回填或构造统一返回结构。', how: '表单字段使用 form.字段编码；列表分页可使用 records、total、pageNum、pageSize。', effect: '设计器显示中文字段名，保存后使用字段编码。' },
                { field: '值转换：原值', meaning: '保持来源值不变。', when: '类型已经匹配。', how: '选择“原值”。', effect: '默认选项。' },
                { field: '值转换：取第一项', meaning: '数组取第一个元素。', when: '多选结果回填单值字段。', how: '选择“取第一项”。', effect: '空数组得到 null。' },
                { field: '值转换：转数组', meaning: '把单值包装为数组。', when: '单值回填多选字段。', how: '选择“转数组”。', effect: '原值已经是集合时保持不变。' },
                { field: '值转换：文本拼接', meaning: '把集合元素拼成文本。', when: '多个名称回填到文本字段。', how: '选择“文本拼接”；当前编辑器使用逗号作为默认分隔符。', effect: 'null 元素按空字符串处理。' },
                { field: '覆盖策略：始终覆盖', meaning: '无论目标字段是否有值都写入。', when: '选择记录后目标值必须与来源保持一致。', how: '选择“始终覆盖”。', effect: '可能覆盖用户已输入内容。' },
                { field: '覆盖策略：仅空值覆盖', meaning: '目标为空时才回填。', when: '希望保留用户手工输入。', how: '选择“仅空值覆盖”。', effect: '目标已有值时保持不变。' },
                { field: '覆盖策略：覆盖前确认', meaning: '目标已有值时由页面询问是否覆盖。', when: '自动值重要，但用户可能已经修改。', how: '选择“覆盖前确认”。', effect: '需要运行页面支持确认交互。' },
                { field: '执行条件路径', meaning: '从事件状态读取待判断值。', when: '步骤只在特定状态或有选择数据时执行。', how: '例如 input.status、selectedIds、context.scene。', effect: '条件不满足时步骤标记为跳过。' },
                { field: '等于 / 不等于', meaning: '与配置值做严格相等或不等判断。', when: '状态、类型、固定值判断。', how: '输入比较值。', effect: '字符串 "1" 与数字 1 不相等。' },
                { field: '存在', meaning: '判断路径结果是否为 null。', when: '记录 ID、选择结果或上下文可选。', how: '选择是或否。', effect: '空字符串仍视为存在。' },
                { field: '为真', meaning: '按布尔、非零数字、非空集合和非空文本判断。', when: '开关、选中列表或非空文本条件。', how: '选择是或否。', effect: '空集合、0、空文本和 null 为假。' }
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'interface-service-recipes',
      index: '08',
      title: '常用场景怎么配置',
      summary: '按可直接验证的步骤配置列表数据源、实体回填、保存校验和按钮动作。',
      topics: [
        {
          id: 'interface-service-list-source',
          title: '场景一：修改列表数据源',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '创建查询服务', text: '新建接口服务，选择合适实现类型，增加一个 READ 操作并调试返回分页结构。' },
                { title: '打开列表设计器', text: '进入“列表设置 → 查询实现 → 列表查询接口”。' },
                { title: '选择服务和操作', text: '选择已启用、作用域匹配的 LIST/READ 操作；运行时统一提供 filters、sorts、currentRow、selectedRows、records、pageNum、pageSize 和 scene。' },
                { title: '确认分页契约', text: '操作输出 Schema 必须声明对象结构和 records 数组；接口结果应返回 records、total、pageNum、pageSize。' },
                { title: '保存并发布', text: '保存列表设置，再发布生效；未发布修改不影响实际运行列表。' },
                { title: '验证分页和条件', text: '分别测试第一页、第二页、修改每页条数和至少两个查询条件，确认预览与实际列表一致。' }
              ]
            },
            { type: 'code', title: '推荐列表返回结构', language: 'json', code: listResultExample },
            {
              type: 'callout',
              tone: 'info',
              title: '标准返回字段',
              text: '列表查询接口统一返回 records、total、pageNum、pageSize。分页结果仍会经过运行时标准化，但新接口应直接遵守标准契约。'
            },
            {
              type: 'callout',
              tone: 'info',
              title: '查询与事件分离',
              text: 'LIST_QUERY 只保存在列表查询配置槽位；LIST_LOAD 等事件仍在事件绑定中维护。两者可以调用同一接口服务的不同操作，但不会互相覆盖配置。'
            }
          ]
        },
        {
          id: 'interface-service-selection-backfill',
          title: '场景二：实体选择后回填字段',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '确认选择结果', text: '先检查实体选择列表是否已经返回电话、负责人等目标值。' },
                { title: '选择绑定位置', text: '事件绑定选择表单覆盖、字段、目标实体引用字段。' },
                { title: '选择 ENTITY_SELECTED', text: '新增“选择实体后”事件绑定。' },
                { title: '已有值时只做映射', text: '步骤不选接口服务，从 selection.data.phone 映射到 form.contactPhone。' },
                { title: '信息不足时调用详情', text: '增加客户服务的 READ 详情操作，把 selection.data.id 映射为 customerId，再从 data.phone 回填表单。' },
                { title: '选择覆盖策略', text: '系统带回值通常始终覆盖；保留手工输入时用仅空值覆盖；敏感字段用覆盖前确认。' },
                { title: '发布表单验证', text: '验证选择、清空、重新选择、多选和目标字段已有值等路径。' }
              ]
            }
          ]
        },
        {
          id: 'interface-service-save-chain',
          title: '场景三：保存前校验，保存后同步',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '配置校验操作', text: '在业务服务增加 READ 或无副作用 WRITE 校验操作，输入 Schema 声明必填业务字段。' },
                { title: '新增 FORM_SAVE 或 DATA_UPDATE 绑定', text: '使用 BEFORE，失败策略 STOP，把 form/input 字段映射给校验接口。' },
                { title: '保留平台保存', text: '不要增加 REPLACE，完整链应显示“校验 → 平台默认处理”。' },
                { title: '增加后置同步', text: '再增加 AFTER WRITE 操作同步 ERP，按业务重要性选择 STOP 或 CONTINUE，并确保外部接口幂等。' },
                { title: '需要完全自定义时才替代', text: '只有平台不能保存该数据时才配置 REPLACE；此时自定义接口承担主保存和标准结果返回。' },
                { title: '发布并验证失败路径', text: '确认校验失败不保存、平台保存成功、外部同步失败的提示和补偿方式符合预期。' }
              ]
            }
          ]
        },
        {
          id: 'interface-service-button-action',
          title: '场景四：按钮调用接口',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '先创建按钮', text: '在列表工具栏、操作列或表单按钮设计器中创建按钮并保存稳定编码。' },
                { title: '选择按钮绑定位置', text: '在事件绑定选择列表或表单层级、按钮位置，并填写相同按钮编码。' },
                { title: '选择点击事件', text: '工具栏用 TOOLBAR_BUTTON_CLICK，行按钮用 ROW_BUTTON_CLICK，表单按钮用 FORM_BUTTON_CLICK。' },
                { title: '映射业务参数', text: '工具栏批量操作使用 selectedIds，行按钮使用 recordId，表单按钮使用 input 或 form 数据。' },
                { title: '配置页面效果', text: '接口可返回 message 和 effects，触发提示、刷新列表、回填表单、关闭弹窗、打开系统路由或下载任务。' },
                { title: '验证权限与适用条件', text: '接口绑定不会取代按钮权限码和适用条件；使用不同角色和不同记录状态验证。' }
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'interface-service-publish',
      index: '09',
      title: '发布、安全与排错',
      summary: '理解保存和发布的边界，并按现象快速定位配置问题。',
      topics: [
        {
          id: 'interface-service-effective-rules',
          title: '什么时候真正生效',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '保存接口服务', meaning: '更新服务目录和操作定义。', when: '服务创建或编辑完成。', notes: '不会自动绑定到页面；停用会立即导致后续执行拒绝。' },
                { option: '保存事件绑定', meaning: '更新实体、表单或列表的绑定草稿。', when: '执行链编辑完成。', notes: '提示“发布页面配置后生效”，实际运行仍读取当前激活发布版本。' },
                { option: '保存列表查询接口', meaning: '更新列表的 queryDataSourceId 和 queryOperationCode。', when: '通过列表设置直接修改查询数据源。', notes: '服务 ID 和操作编码同时保存到列表查询配置槽位。' },
                { option: '发布表单或列表', meaning: '生成并激活运行快照。', when: '调试、映射、权限和影响预检通过。', notes: '发布后实际页面才使用新链；未发布修改只在设计草稿中存在。' },
                { option: '删除或停用服务', meaning: '停止服务后续执行。', when: '下线、故障隔离或安全处置。', notes: '不会自动清理绑定；应先检索引用并准备替代服务。' }
              ]
            }
          ]
        },
        {
          id: 'interface-service-troubleshooting',
          title: '常见问题',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '保存后页面没有变化', meaning: '只保存了服务或绑定，没有发布表单/列表。', when: '设计器能看到配置，运行页仍使用旧逻辑。', notes: '保存对应页面全部草稿并“发布生效”，再刷新运行页。' },
                { option: '列表仍走平台查询', meaning: '列表没有保存 queryDataSourceId 和 queryOperationCode，或发布版本仍是旧配置。', when: '自定义服务没有执行日志。', notes: '检查列表查询配置和当前激活发布版本。' },
                { option: '清空列表查询接口后的行为', meaning: '清空列表查询服务和操作。', when: '特殊列表不再需要独立数据源。', notes: '系统恢复平台默认实体分页查询；事件绑定不受影响。' },
                { option: '列表有数据但分页不对', meaning: '接口返回的 total/pageNum/pageSize 不正确。', when: '只有第一页、总数为 0 或翻页重复。', notes: '检查操作输出 Schema 和标准分页四字段。' },
                { option: '操作下拉没有目标操作', meaning: '服务停用、操作不是 READ、操作未保存或服务范围不匹配。', when: '配置列表查询接口或事件步骤。', notes: '刷新接口服务页面，确认操作编码、数据影响和启用状态。' },
                { option: '输入 Schema 校验失败', meaning: '映射后的 input 缺字段或类型不符。', when: '接口尚未真正执行即报错。', notes: '用调试窗口查看输入，注意 integer 与字符串、数组与单值区别。' },
                { option: '输出 Schema 校验失败', meaning: 'Provider/Connector 返回结构与声明不符。', when: '下游成功但平台报告输出错误。', notes: '修正接口返回或 Schema；不要用空 Schema 长期掩盖不稳定契约。' },
                { option: '数据一直不更新', meaning: 'READ 服务配置了缓存。', when: '短时间重复查询返回旧值。', notes: '检查缓存秒数；写操作必须为 0，需要强实时的查询也应设为 0。' },
                { option: '接口执行超时', meaning: '下游超过服务超时值。', when: '调试或运行提示数据源执行超时。', notes: '先查下游性能，再合理提高超时；不要把 30000 毫秒当作常规值。' },
                { option: '配置版本冲突', meaning: '其他管理员已经保存了更新修订号。', when: '编辑或删除时提示刷新重试。', notes: '刷新页面，对比最新内容并重新合并，不要连续盲点保存。' },
                { option: 'Provider/Connector 未注册', meaning: '当前部署没有对应后端 Bean 或 Connector。', when: '目录中不存在或执行时报未注册。', notes: '确认模块已部署、能力开关开启、编码完全一致。' },
                { option: '作用域不匹配或无权限', meaning: '服务范围、页面对象、目标实体或当前角色不一致。', when: '调试能选但执行被拒绝，或写操作报范围不匹配。', notes: '优先使用真实目标页面调试，并检查实体/表单/列表范围。' }
              ]
            }
          ]
        },
        {
          id: 'interface-service-go-live',
          title: '上线检查清单',
          blocks: [
            {
              type: 'checklist',
              items: [
                '服务名称清晰，服务编码和操作编码稳定且无重复。',
                '实现类型与真实数据来源一致，Provider/Connector 已在目标环境注册。',
                '作用范围采用满足复用需要的最小范围，写操作优先限定到实体。',
                'READ 与 WRITE 标记正确，WRITE 操作缓存为 0，并验证幂等。',
                '输入和输出 Schema 覆盖必填字段与关键类型，错误输入已验证。',
                '超时符合下游性能，缓存符合业务实时性，不依赖超长超时掩盖性能问题。',
                '调试使用了最终表单或列表上下文，并验证当前用户的数据权限。',
                '事件继承方式、BEFORE/REPLACE/AFTER 顺序和失败策略符合业务目标。',
                '字段映射使用中文选择后保存的稳定字段编码，数组和单值转换正确。',
                '列表操作返回标准分页结构，查询条件、翻页和每页条数均已验证。',
                '按钮权限码、适用条件和事件绑定同时生效，不把接口绑定当作权限替代。',
                '表单或列表已经发布生效，并使用真实角色验证成功、空结果、失败和超时路径。',
                '外部调用可以通过审计、traceId、幂等键和下游日志定位。',
                '已准备停用、重试、补偿和回滚方案，删除服务前已检查全部引用。'
              ]
            }
          ]
        }
      ]
    }
  ]
}
