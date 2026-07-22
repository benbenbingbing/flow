const fieldColumns = [
  { key: 'field', label: '配置项' },
  { key: 'meaning', label: '含义' },
  { key: 'defaultLimit', label: '默认值 / 限制' },
  { key: 'effect', label: '运行效果' },
  { key: 'publish', label: '发布注意事项' }
]

const optionColumns = [
  { key: 'option', label: '选项 / 类型' },
  { key: 'meaning', label: '运行语义' },
  { key: 'notes', label: '配置与发布注意事项' }
]

export default {
  eyebrow: 'USER MANUAL · PROCESS',
  title: '流程管理用户手册',
  subtitle: '覆盖流程列表、BPMN 设计器全部元素、办理人与多实例、任务配置、条件组、节点表单、审批项、实体状态、流程动作、事务失败策略、发布与版本管理。',
  version: '当前 UI 配置基线',
  updatedAt: '2026-07-16',
  intro: [
    {
      title: '两层保存',
      type: 'warning',
      text: '右侧节点面板的“保存”把配置写入设计器内存中的 BPMN；顶部“保存流程”才把完整 XML 落库。离开页面前必须执行顶部保存。'
    },
    {
      title: '发布才进入运行时',
      type: 'success',
      text: '流程发布会校验动作、清理兼容属性、同步节点表单/审批/状态映射、部署到 Flowable、复制版本流程动作并生成版本快照；草稿修改不会自动影响已运行定义。'
    }
  ],
  sections: [
    {
      id: 'process-list',
      index: '01',
      title: '流程列表与生命周期',
      summary: '创建流程、查询状态、发布、禁用、删除并管理版本。',
      topics: [
        {
          id: 'process-list-search',
          title: '查询、状态与分页',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '流程名称', meaning: '按名称或流程标识模糊搜索。', defaultLimit: '默认空；回车或“查询”执行。', effect: '返回匹配流程并回到第 1 页。', publish: '无发布影响。' },
                { field: '状态', meaning: '筛选 DRAFT、PUBLISHED、DISABLED。', defaultLimit: '默认全部。', effect: '区分草稿、可运行和停用流程。', publish: 'PUBLISHED 状态下仍可保存草稿修改，但要再次发布才形成新版本。' },
                { field: '分类', meaning: '按流程分类精确筛选。', defaultLimit: '默认空。', effect: '用于按业务域管理流程。', publish: '分类可改，不影响 processKey。' },
                { field: '分页', meaning: '控制当前页与每页数量。', defaultLimit: '默认 10；可选 10/20/50/100。', effect: '服务端分页。', publish: '无发布影响。' }
              ]
            },
            {
              type: 'bullets',
              items: [
                '列表显示流程名称、流程标识、分类、当前版本、状态和更新时间。',
                '版本为 0 或空表示从未发布；发布后显示 v1、v2 等。',
                '“重置”清空名称、状态、分类并回到第 1 页。'
              ]
            }
          ]
        },
        {
          id: 'process-create',
          title: '新建与编辑流程',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '流程名称', meaning: '业务显示名。', defaultLimit: '必填。', effect: '显示在列表、设计器、部署名和版本详情。', publish: '可改名，建议在版本说明记录。' },
                { field: '流程标识', meaning: '流程稳定技术键。', defaultLimit: '必填；必须以字母开头，只能包含字母、数字、下划线；编辑时禁改。', effect: '发布时写入 BPMN process id，并作为 Flowable process definition key。', publish: '上线后绝不能通过数据库直接修改，否则实体绑定、实例启动、版本和迁移资产会失联。' },
                { field: '分类', meaning: '业务分类。', defaultLimit: '可选。', effect: '列表筛选和治理分组。', publish: '保持跨环境一致便于运维。' },
                { field: '描述', meaning: '说明流程范围、触发方式和边界。', defaultLimit: '可选，多行。', effect: '帮助管理员识别流程。', publish: '建议注明绑定实体、发起角色和结束结果。' }
              ]
            },
            {
              type: 'callout',
              tone: 'info',
              title: '初始状态',
              text: '新流程版本为 0、状态为 DRAFT。创建后必须进入“设计”完成 BPMN，并点击顶部“保存流程”后才能发布。'
            }
          ]
        },
        {
          id: 'process-list-actions',
          title: '流程列表操作',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '设计', meaning: '打开 BPMN 设计器，编辑图形、节点配置、连线条件、表单和动作。', notes: '节点面板保存后还要点击顶部“保存流程”。' },
                { option: '编辑', meaning: '修改名称、分类和描述；流程标识不可改。', notes: '已发布流程也允许保存新的 BPMN 草稿，状态仍显示 PUBLISHED，需再次发布。' },
                { option: '版本', meaning: '查看历史版本、只读流程图和版本流程动作。', notes: '删除版本会逻辑删除版本动作和版本记录，不会回滚现有运行实例。' },
                { option: '发布', meaning: '草稿或禁用流程创建新版本并部署。', notes: '每次发布版本号 +1；动作和 BPMN 必须通过校验。' },
                { option: '禁用', meaning: '将 PUBLISHED 或 DRAFT 流程状态置为 DISABLED。', notes: '用于阻止新使用；已有实例的处理策略需结合运行时验证。' },
                { option: '删除', meaning: '逻辑删除流程并置为 DISABLED。', notes: '先解除实体绑定并确认无实例、菜单和迁移依赖。' }
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'process-designer',
      index: '02',
      title: '设计器基础操作',
      summary: '掌握画布工具、撤销重做、XML、选择元素和两层保存。',
      topics: [
        {
          id: 'process-designer-toolbar',
          title: '顶部工具栏',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '撤销 / 重做', meaning: '撤销或重做 bpmn-js 命令栈中的图形与属性修改。支持 Ctrl/Cmd+Z、Ctrl/Cmd+Y、Ctrl/Cmd+Shift+Z。', notes: '接口保存、动作独立保存等服务端操作不一定进入同一命令栈。' },
                { option: '全局动作', meaning: '配置作用于整个流程实例的流程动作；徽标显示动作数。', notes: '动作保存后仍需发布流程才进入版本。' },
                { option: '查看 XML', meaning: '全屏只读显示格式化 BPMN XML，可复制。', notes: '用于排查属性和版本，不提供直接编辑；敏感接口参数复制后注意保密。' },
                { option: '保存流程', meaning: '获取完整 XML，确保 isExecutable=true 和 flowable 命名空间，然后更新流程配置。', notes: '这是节点和连线配置真正落库的唯一顶部操作。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '离开页面前',
              text: '右侧每个 Tab 的“保存”只更新当前设计器模型并触发未保存标记，不会单独提交数据库。完成任何节点、条件、表单、审批、状态或高级配置后，都要再次点击顶部“保存流程”。'
            }
          ]
        },
        {
          id: 'process-designer-palette',
          title: '画布工具与创建元素',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '手型工具', meaning: '拖动画布视口。', notes: '用于大流程导航，不修改模型。' },
                { option: '套索工具', meaning: '框选多个元素。', notes: '批量移动时避免破坏连线可读性。' },
                { option: '空间工具', meaning: '在画布中创建或移除空间。', notes: '适合插入节点后重新排版。' },
                { option: '全局连接工具', meaning: '从一个元素连接到另一个元素。', notes: '根据元素类型生成顺序流、消息流或关联。' },
                { option: '开始事件', meaning: '创建无类型开始事件。', notes: '可通过更改类型切换为消息、定时、条件或信号开始事件。' },
                { option: '中间事件', meaning: '创建中间抛出事件。', notes: '可更改为消息、定时、条件、链接、补偿、信号、升级等捕获/抛出事件。' },
                { option: '结束事件', meaning: '创建无类型结束事件。', notes: '可更改为消息、升级、错误、取消、补偿、信号、终止结束事件。' },
                { option: '网关', meaning: '创建排他网关。', notes: '可更改为并行、包容、复杂、基于事件网关。' },
                { option: '用户任务', meaning: '当前平台把默认“任务”入口替换为用户任务。', notes: '其他任务类型通过“更改类型”切换。' },
                { option: '展开子流程', meaning: '创建含开始事件的展开子流程。', notes: '用于局部封装；当前专用右侧配置以可选择元素支持为准。' },
                { option: '数据对象 / 数据存储', meaning: '表达流程数据输入、输出和持久存储。', notes: '主要用于建模说明，平台没有额外业务字段配置。' },
                { option: '泳池 / 参与者', meaning: '表达组织或系统参与边界。', notes: '跨泳池通常使用消息流；不直接等同系统用户组。' },
                { option: '组', meaning: '视觉分组，不改变执行语义。', notes: '用于提高大图可读性。' }
              ]
            }
          ]
        },
        {
          id: 'process-designer-selection',
          title: '元素选择与右侧配置范围',
          blocks: [
            {
              type: 'bullets',
              items: [
                '当前画布明确开放右侧配置的元素：用户、服务、发送、接收、手动、业务规则、脚本任务；开始、结束事件；排他、并行、包容、基于事件网关；顺序流。',
                '调用活动和子流程的专用表单已在节点面板中定义，可通过任务“更改类型”后配置；如果重新点击后未出现专用 Tab，应先保持当前选中状态完成配置并保存 XML，或由管理员通过版本 XML 复核。',
                '中间/边界事件、复杂网关、事务、泳池、组、数据对象、数据存储等当前主要提供 BPMN 建模能力，没有平台专用业务配置 Tab。',
                '点击动作数量徽标会选中对应节点或连线并打开配置面板。',
                '所有元素都有 BPMN ID；平台业务绑定、动作和版本都依赖稳定 ID，发布后不要无意义删除重建节点。'
              ]
            }
          ]
        },
        {
          id: 'process-basic-config',
          title: '所有可配置元素的基本信息',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '节点名称', meaning: '图上显示的业务名称。', defaultLimit: '可选但强烈建议填写；不同节点有示例占位。', effect: '显示在流程图、待办、动作上下文和版本节点。', publish: '同一流程内建议语义唯一，例如“部门经理审批”。' },
                { field: '节点 ID', meaning: 'BPMN 元素技术标识。', defaultLimit: '只读，由设计器生成。', effect: '用于节点表单、审批配置、状态映射、动作绑定和日志。', publish: '发布后删除重建会产生新 ID，历史配置不会自动迁移。' },
                { field: '说明文档', meaning: 'BPMN documentation。', defaultLimit: '可选多行。', effect: '写入 XML，供设计审计。', publish: '建议记录进入条件、处理要求、输出和异常路径。' }
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'process-node-types',
      index: '03',
      title: '节点类型与运行语义',
      summary: '覆盖设计器可创建或更改出的任务、事件、网关、子流程和建模元素。',
      topics: [
        {
          id: 'process-task-types',
          title: '任务与活动类型',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '用户任务 UserTask', meaning: '生成人工待办，支持办理人、候选人、会签/串行多实例、节点表单、审批项和高级配置。', notes: '人工审批首选；必须明确人员解析和表单权限。' },
                { option: '服务任务 ServiceTask', meaning: '自动执行 Java 类、表达式、Spring Bean 或 REST 请求。', notes: '外部调用优先考虑流程动作的提交后模式，避免长事务。' },
                { option: '发送任务 SendTask', meaning: '按邮件、短信、站内信渠道发送消息。', notes: '当前配置保存为平台扩展；必须确认运行处理器已接入。' },
                { option: '接收任务 ReceiveTask', meaning: '暂停流程，等待外部消息继续。', notes: '消息名称必须与外部系统约定；超时策略要有运维监控。' },
                { option: '手动任务 ManualTask', meaning: '记录系统外线下工作，不生成待办。', notes: '只用于建模和记录，不能替代需要系统确认的用户任务。' },
                { option: '业务规则任务 BusinessRuleTask', meaning: '执行 DMN 决策表并映射结果。', notes: '目标环境必须部署同 key 决策表。' },
                { option: '脚本任务 ScriptTask', meaning: '执行 JavaScript、Groovy 或 Python 脚本处理轻量逻辑。', notes: '脚本引擎支持受运行环境限制；复杂业务应使用服务任务或动作处理器。' },
                { option: '调用活动 CallActivity', meaning: '调用独立 BPMN 子流程或 CMMN 案例，支持入参、出参和业务 key。', notes: '当前子流程候选列表为界面内置示例，正式使用前必须确认目标定义真实存在。' },
                { option: '子流程 SubProcess / 事务 Transaction / 事件子流程', meaning: '在一个流程内封装局部步骤，事务和事件子流程具有更特殊的 BPMN 语义。', notes: '平台无完整专用属性面板时，需要通过 XML 和 Flowable 兼容性验证。' }
              ]
            }
          ]
        },
        {
          id: 'process-event-types',
          title: '事件类型',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '无类型开始事件', meaning: '流程由平台 API 或业务入口直接启动。', notes: '可绑定一个或多个实体表单，并配置流程/节点动作。' },
                { option: '消息 / 定时 / 条件 / 信号开始事件', meaning: '由消息、时间、条件或广播信号触发实例。', notes: '当前平台没有专用事件参数表单，必须确认 XML 中事件定义完整并与 Flowable 部署能力一致。' },
                { option: '中间捕获事件', meaning: '等待消息、定时器、条件、链接或信号。', notes: '会暂停或路由执行；事件名称、时间表达式需在 XML 中有效。' },
                { option: '中间抛出事件', meaning: '发送消息、升级、链接、补偿或信号。', notes: '确认接收方和作用域，避免无消费者事件。' },
                { option: '边界事件', meaning: '附着任务/活动，支持消息、定时、升级、条件、错误、取消、信号、补偿，以及部分非中断类型。', notes: '中断型会取消宿主活动，非中断型保留宿主活动；当前无专用平台配置。' },
                { option: '普通结束事件', meaning: '结束当前执行路径；所有路径完成后流程正常完成。', notes: '可配置节点动作，流程正常完成还会触发全局完成动作。' },
                { option: '消息 / 升级 / 错误 / 取消 / 补偿 / 信号结束事件', meaning: '以相应 BPMN 事件结束或传播。', notes: '错误、取消、补偿只在正确作用域有意义。' },
                { option: '终止结束事件', meaning: '终止作用域内所有活动。', notes: '会触发流程终止语义和 PROCESS_TERMINATED 动作，谨慎使用。' }
              ]
            }
          ]
        },
        {
          id: 'process-gateway-types',
          title: '网关与连线',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '排他网关 Exclusive', meaning: '按顺序选择一个满足条件的分支；可配置一个默认流。', notes: '每条业务分支在顺序流上配置条件，最后一条兜底设默认流。' },
                { option: '并行网关 Parallel', meaning: '拆分时同时进入全部分支，汇聚时等待所有到达分支。', notes: '通常不应在出线上配置互斥条件。' },
                { option: '包容网关 Inclusive', meaning: '进入所有满足条件的分支，汇聚时等待实际激活分支。', notes: '条件设计错误容易造成等待或遗漏。' },
                { option: '复杂网关 Complex', meaning: '表达复杂激活规则。', notes: '当前无专用平台规则配置，除非有明确 Flowable 方案，不建议使用。' },
                { option: '基于事件网关 Event-based', meaning: '由后续第一个发生的事件决定路径。', notes: '后续通常连接接收任务或捕获事件，而不是普通条件表达式。' },
                { option: '顺序流 SequenceFlow', meaning: '同一流程内控制执行顺序，可配置条件、默认流、实体状态和连线动作。', notes: '连线 ID 被动作和状态映射引用，发布后保持稳定。' },
                { option: '消息流 MessageFlow', meaning: '连接不同参与者之间的消息交互。', notes: '不用于同一流程内部顺序控制。' },
                { option: '关联 Association', meaning: '连接注释、数据对象等建模元素。', notes: '没有执行语义。' }
              ]
            }
          ]
        },
        {
          id: 'process-artifacts',
          title: '泳道、数据与分组元素',
          blocks: [
            {
              type: 'bullets',
              items: [
                '泳池/参与者表示外部组织、系统或独立流程参与方；不是系统角色或用户组配置。',
                '泳道可用于责任区分，但当前办理人仍由用户任务“执行人”配置决定。',
                '数据对象表示流程中使用或产生的数据，数据存储表示持久数据源；当前平台实体绑定不由这些图形自动建立。',
                '组只改变视觉分组，不改变顺序、事务或人员。',
                '建模元素必须服务于可读性；发布前确保流程主路径仍由可执行节点和顺序流完整连接。'
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'process-assignee',
      index: '04',
      title: '办理人、候选人与多实例',
      summary: '配置固定人员、组、角色、表达式、接口动态和会签/串行处理。',
      topics: [
        {
          id: 'process-assignee-methods',
          title: '普通用户任务指定方式',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '固定人员 user', meaning: '选择一个执行人，也可多选候选人。执行人直接拥有任务，候选人可认领。', notes: '默认指定方式；保存使用 username。一个任务同时配置执行人和候选人时要明确认领规则。' },
                { option: '用户组 group', meaning: '多选用户组，组内成员可处理任务。', notes: '保存 groupCode；成员变更会影响后续新任务。' },
                { option: '角色 role', meaning: '多选角色，拥有角色的用户可处理任务。', notes: '在 candidateGroups 中使用 ROLE_ 前缀区分；角色编码保持稳定。' },
                { option: '表达式 expression', meaning: '执行人、候选人、候选组由流程变量或表达式决定。', notes: '示例 ${submitUser}、${initiator}、${deptManagers}；返回值必须符合 Flowable 用户/组格式。' },
                { option: '接口动态 interface', meaning: '通过 Spring Bean 或 REST 接口解析办理人。', notes: '必须配置接口名称；Spring 可填方法名，REST 可选 GET/POST；请求参数支持流程变量；返回映射指定流程变量名。' }
              ]
            },
            {
              type: 'table',
              title: '接口动态字段',
              columns: fieldColumns,
              rows: [
                { field: '接口类型', meaning: 'spring 或 rest。', defaultLimit: '新建内存默认 spring；历史配置加载可能按兼容值回显。', effect: '决定调用本地 Bean 还是 HTTP。', publish: '目标环境必须存在 Bean 或可访问接口。' },
                { field: '接口名称', meaning: 'Bean 名称或完整 URL。', defaultLimit: '必填。', effect: '运行时定位人员解析器。', publish: 'URL 不应写环境固定域名，优先使用环境配置。' },
                { field: '方法名', meaning: 'Spring Bean 方法。', defaultLimit: '示例 selectAssignee。', effect: '返回用户 ID/用户名或集合。', publish: '签名和返回类型必须与运行时解析器一致。' },
                { field: '请求方式', meaning: 'REST GET/POST。', defaultLimit: '界面默认 POST。', effect: '决定参数发送方式。', publish: '敏感参数禁止放 GET 查询串。' },
                { field: '请求参数', meaning: 'JSON 文本，支持流程变量。', defaultLimit: '可选。', effect: '传给接口的上下文。', publish: '必须是合法 JSON，禁止传递无必要敏感数据。' },
                { field: '返回映射', meaning: '结果写入的变量名或结构路径。', defaultLimit: '界面默认 assignee。', effect: '后续人员绑定读取该结果。', publish: '与接口返回契约一致。' }
              ]
            }
          ]
        },
        {
          id: 'process-candidates',
          title: '执行人和候选人的区别',
          blocks: [
            {
              type: 'table',
              columns: [
                { key: 'role', label: '配置' },
                { key: 'runtime', label: '运行效果' },
                { key: 'recommendation', label: '使用建议' }
              ],
              rows: [
                { role: '执行人 assignee', runtime: '任务创建后直接归属一个用户，进入其待办。', recommendation: '责任人明确且无需认领时使用。' },
                { role: '候选人 candidateUsers', runtime: '多个用户都可见任务；在工作台点击“认领”后写入当前办理人，才能审批、转办或加签。', recommendation: '小范围共享待办时使用；并发认领只有第一个用户成功，其他用户收到冲突提示。' },
                { role: '候选组 candidateGroups', runtime: '组或角色成员可见任务；认领后任务从共享池转为个人待办。', recommendation: '按组织角色分配时使用；避免组过大导致所有人看到大量待办。' },
                { role: '接口动态', runtime: '运行时根据业务数据解析一个或多个处理人。', recommendation: '组织规则复杂或需要外部主数据时使用，并准备空结果兜底。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '空办理人',
              text: '任何动态方式都要设计“无人员返回”的处理。发布前使用最小数据、缺失部门、停用用户和接口超时等场景验证，避免流程进入无人可办状态。'
            }
          ]
        },
        {
          id: 'process-multi-instance',
          title: '多实例（会签 / 串行）',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '启用多实例', meaning: '为人员集合中的每个成员创建任务实例。', defaultLimit: '默认关闭。', effect: '开启后普通执行人/候选人配置不再作为 BPMN 直接办理人。', publish: '人员集合为空会导致运行异常或直接跳过，必须测试。' },
                { field: '执行方式', meaning: 'parallel 并行或 sequential 串行。', defaultLimit: '默认 parallel。', effect: '并行同时生成任务；串行按集合顺序逐个生成。UI 将串行标注为“或签”，但是否任一人通过取决于完成条件。', publish: '不要把“串行”误当成自动一票通过。' },
                { field: '集合来源', meaning: 'variable 流程变量或 interface 接口动态。', defaultLimit: '默认 variable。', effect: '决定人员集合如何生成。', publish: '接口方式必须返回可解析用户列表。' },
                { field: '会签人员 / 用户组 / 角色', meaning: '流程变量来源时直接选择用户、组、角色。', defaultLimit: '可多选。', effect: '平台汇总用户名、组成员和角色成员，写入系统集合变量。', publish: '去重、停用用户和组织变化需验证。' },
                { field: '集合变量', meaning: '多实例集合表达式。', defaultLimit: '系统固定 ${_wfMultiInstanceUsers_}，界面只读。', effect: '发布时作为 loop collection。', publish: '不要在 XML 外部覆盖同名变量为错误类型。' },
                { field: '接口配置', meaning: '接口来源时填写人员集合解析器。', defaultLimit: '示例 approverSelector.getApprovers。', effect: '运行时返回用户 ID 列表。', publish: '目标环境必须注册并具备超时/异常处理。' },
                { field: '元素变量', meaning: '当前实例成员变量名。', defaultLimit: '默认 assignee。', effect: '每个子任务 assignee 写为 ${元素变量}。', publish: '与流程中其他变量避免重名。' },
                { field: '完成条件', meaning: '满足后提前结束多实例。', defaultLimit: '默认空，表示全部实例完成；示例 ${nrOfCompletedInstances >= nrOfInstances * 0.5}。', effect: '可实现半数、任一、全票等策略。', publish: '使用 Flowable 多实例变量；提前结束会终止剩余实例，业务需确认。' }
              ]
            },
            {
              type: 'callout',
              tone: 'info',
              title: '多实例保存语义',
              text: '启用后平台清空 candidateUsers/candidateGroups，并把 BPMN assignee 设置为元素变量表达式；原人员配置和多实例人员明细保存在扩展属性中用于回显和运行准备。关闭多实例时会尝试恢复原候选配置。'
            }
          ]
        }
      ]
    },
    {
      id: 'process-task-config',
      index: '05',
      title: '各任务配置',
      summary: '逐项说明服务、发送、接收、手动、规则、脚本和调用活动。',
      topics: [
        {
          id: 'process-service-task',
          title: '服务任务',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: 'Java 类 class', meaning: '填写实现 Flowable JavaDelegate 的完整类名。', notes: '示例 com.workflow.delegate.DemoJavaDelegate；类必须在服务端 classpath。' },
                { option: '表达式 expression', meaning: '执行表达式，例如 ${demoExpressionService.execute(execution)}。', notes: 'Bean 和方法必须可解析，表达式可直接影响流程变量。' },
                { option: 'Spring Bean delegateExpression', meaning: '填写 Bean 表达式，例如 ${demoServiceTask}。', notes: 'Bean 通常实现 JavaDelegate。' },
                { option: 'REST 接口 rest', meaning: '由平台扩展执行 HTTP 请求。', notes: '适合外部系统，但同步事务中网络抖动可能阻塞流程。' }
              ]
            },
            {
              type: 'table',
              title: 'REST 配置',
              columns: fieldColumns,
              rows: [
                { field: '请求方式', meaning: 'GET、POST、PUT、DELETE。', defaultLimit: '默认 POST。', effect: '决定 HTTP 方法。', publish: 'DELETE/PUT 必须保证幂等与权限。' },
                { field: '请求 URL', meaning: '完整接口地址，支持 ${变量}。', defaultLimit: '必填。', effect: '运行时替换变量并调用。', publish: '使用环境配置，不要把开发地址发布到生产。' },
                { field: 'Content-Type', meaning: '请求内容类型。', defaultLimit: '默认 application/json；还支持表单、multipart、text/xml。', effect: '决定请求编码。', publish: '与 body 格式一致。' },
                { field: '请求头', meaning: 'JSON Headers，支持流程变量。', defaultLimit: '可选。', effect: '传递鉴权和追踪信息。', publish: '禁止把长期密钥直接写进 BPMN。' },
                { field: '请求体', meaning: '非 GET 的 JSON 或表单内容。', defaultLimit: '可选；支持 ${variable}。', effect: '发送业务数据。', publish: '必须是目标 Content-Type 可解析格式。' },
                { field: '查询参数', meaning: 'JSON 查询参数。', defaultLimit: '可选。', effect: '拼接 URL query。', publish: '避免敏感信息。' },
                { field: '超时时间', meaning: 'HTTP 超时秒数。', defaultLimit: '1–300，默认 30。', effect: '超过时间进入错误处理。', publish: '事务内超时会占用数据库事务。' },
                { field: '重试次数', meaning: '同步请求重试。', defaultLimit: '0–5，默认 0。', effect: '失败后重复调用。', publish: '非幂等接口不要自动重试。' },
                { field: '错误处理', meaning: 'throw、continue、ignore。', defaultLimit: '默认 throw。', effect: '抛异常终止流程、记录后继续或忽略。', publish: 'continue/ignore 必须有补偿和告警。' },
                { field: '结果映射', meaning: '响应路径 → 流程变量名 JSON。', defaultLimit: '可选。', effect: '把响应写入流程变量。', publish: '路径不存在时准备默认值。' },
                { field: '结果变量', meaning: '非 REST 实现的返回值变量。', defaultLimit: '可选。', effect: '保存委托执行结果。', publish: '变量名保持稳定，避免覆盖关键变量。' }
              ]
            }
          ]
        },
        {
          id: 'process-send-receive',
          title: '发送任务与接收任务',
          blocks: [
            {
              type: 'table',
              title: '发送任务',
              columns: fieldColumns,
              rows: [
                { field: '发送渠道', meaning: '邮件、短信、站内信，可多选。', defaultLimit: '默认 email。', effect: '按所选渠道发送。', publish: '目标环境必须配置渠道服务。' },
                { field: '接收人', meaning: '具体地址或 ${变量}。', defaultLimit: '可选但实际发送应填写。', effect: '解析消息接收目标。', publish: '多渠道接收人格式可能不同。' },
                { field: '消息标题', meaning: '标题模板。', defaultLimit: '可选。', effect: '支持业务标题。', publish: '避免泄露敏感字段。' },
                { field: '消息内容', meaning: '正文模板，支持 ${processName} 等变量。', defaultLimit: '可选。', effect: '运行时替换变量。', publish: '缺失变量要有可读兜底。' },
                { field: '消息模板', meaning: '流程提交、审批通过、审批拒绝预置模板 key。', defaultLimit: '可清空。', effect: '由消息服务选择模板。', publish: '模板必须在目标环境存在。' }
              ]
            },
            {
              type: 'table',
              title: '接收任务',
              columns: fieldColumns,
              rows: [
                { field: '消息名称', meaning: '外部系统触发流程继续的消息标识。', defaultLimit: '示例 paymentCallback。', effect: '流程停在节点等待同名消息。', publish: '外部系统必须携带正确实例关联信息。' },
                { field: '超时设置', meaning: '是否配置等待超时。', defaultLimit: '默认关闭。', effect: '开启后达到时间执行超时处理。', publish: '当前配置属于平台扩展，确认运行监听器已实现。' },
                { field: '超时时间 / 单位', meaning: '等待长度。', defaultLimit: '数值最小 1；单位 MINUTE、HOUR、DAY，默认 30 MINUTE。', effect: '决定超时点。', publish: '长时间等待关注定时作业和部署迁移。' },
                { field: '超时处理', meaning: 'error 抛异常或 continue 继续。', defaultLimit: '默认 error。', effect: '决定未收到消息时流程行为。', publish: 'continue 必须有业务补偿或状态标记。' }
              ]
            }
          ]
        },
        {
          id: 'process-manual-rule',
          title: '手动任务与业务规则任务',
          blocks: [
            {
              type: 'table',
              title: '手动任务',
              columns: fieldColumns,
              rows: [
                { field: '任务描述', meaning: '线下工作说明。', defaultLimit: '可选。', effect: '记录在 BPMN 扩展。', publish: '不生成系统待办。' },
                { field: '完成条件', meaning: '线下工作完成标准。', defaultLimit: '可选。', effect: '供流程说明使用。', publish: '系统不会自动验证。' },
                { field: '负责人', meaning: '线下负责人文本。', defaultLimit: '可选。', effect: '仅记录，不派单。', publish: '需要真实待办时改用用户任务。' },
                { field: '预计工时', meaning: '预计小时数。', defaultLimit: '最小 0，精度 1 位，默认 0。', effect: '用于说明或统计扩展。', publish: '当前不自动驱动 SLA。' }
              ]
            },
            {
              type: 'table',
              title: '业务规则任务',
              columns: fieldColumns,
              rows: [
                { field: '决策表 Key', meaning: 'DMN 定义键。', defaultLimit: '必填于实际运行。', effect: '调用对应决策表。', publish: '目标环境先部署同 key DMN。' },
                { field: '输入变量', meaning: '传入决策表的 JSON 映射。', defaultLimit: '可选；支持 ${变量}。', effect: '形成决策输入。', publish: '字段类型和名称与 DMN 输入一致。' },
                { field: '结果变量', meaning: '保存决策结果的流程变量。', defaultLimit: '可选。', effect: '后续网关和任务可读取。', publish: '变量名稳定。' },
                { field: '映射结果', meaning: '是否把决策结果映射到流程变量。', defaultLimit: '默认开启。', effect: '关闭时只保留整体结果。', publish: '后续条件依赖单项结果时必须开启。' }
              ]
            }
          ]
        },
        {
          id: 'process-script-task',
          title: '脚本任务',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '脚本类型', meaning: 'JavaScript、Groovy、Python。', defaultLimit: '默认 JavaScript；切换类型会自动替换为对应示例代码。', effect: '决定脚本引擎。', publish: '目标 JVM 必须支持对应引擎；Python 支持通常有限。' },
                { field: '脚本内容', meaning: '执行的代码。', defaultLimit: '可插入示例；JavaScript 提示避免 var，Groovy 支持 Elvis，Python 注意缩进。', effect: '读取 execution 变量并计算。', publish: '禁止网络、文件或高耗时逻辑；复杂逻辑改用服务。' },
                { field: '测试执行', meaning: '使用 price=100、qty=2 的测试变量调用服务端脚本测试。', defaultLimit: '脚本不能为空。', effect: '显示返回值、结果变量和流程变量。', publish: '测试环境通过不代表生产引擎和数据完全一致。' },
                { field: '结果变量', meaning: '保存脚本结果。', defaultLimit: '可选。', effect: '后续节点可读取。', publish: 'JavaScript 最后一行表达式、Groovy 返回值、Python 赋值行为需按提示验证。' },
                { field: '自动存储', meaning: '把脚本变量写入流程上下文。', defaultLimit: '默认关闭。', effect: '脚本内部变量成为流程变量。', publish: '可能污染变量命名空间，只在明确需要时开启。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '脚本安全',
              text: '脚本属于高风险可执行配置。生产发布前应代码评审、限制可用引擎和 API、验证超时与异常处理，并避免在脚本中处理凭据、文件和任意外部命令。'
            }
          ]
        },
        {
          id: 'process-call-activity',
          title: '调用活动',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '子流程 Key', meaning: '被调用定义的技术键。', defaultLimit: '界面候选为 seal_process、payment_process、contract_subprocess 示例。', effect: '运行时启动子流程或案例。', publish: '必须替换为真实存在且已发布的目标 key。' },
                { field: '调用方式', meaning: 'BPMN 子流程或 CMMN 案例。', defaultLimit: '默认 bpmn。', effect: '决定调用引擎类型。', publish: '当前系统主要围绕 BPMN，CMMN 需单独验证依赖。' },
                { field: '输入参数', meaning: '父变量 → 子流程变量 JSON。', defaultLimit: '可选。', effect: '启动子流程时传值。', publish: '避免传递无关大对象。' },
                { field: '输出参数', meaning: '子流程结果 → 父变量 JSON。', defaultLimit: '可选。', effect: '子流程完成后回写。', publish: '路径和变量名必须存在。' },
                { field: '业务 Key', meaning: '子流程业务标识。', defaultLimit: '可选。', effect: '用于跨流程关联。', publish: '建议与父实体或流程实例建立可追踪关系。' }
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'process-conditions',
      index: '06',
      title: '条件组与网关分支',
      summary: '在顺序流上配置无条件、表达式或默认流，并使用可视化嵌套条件组。',
      topics: [
        {
          id: 'process-condition-types',
          title: '条件类型',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '无条件', meaning: '连线在到达来源节点后始终可执行。', notes: '排他网关多条无条件线可能产生不可预期路由。' },
                { option: '表达式', meaning: '使用可视化条件组生成 ${...} 表达式。', notes: '至少配置一个完整条件；空属性、操作符或值不会生成有效表达式。' },
                { option: '默认流', meaning: '当其他条件均不满足时执行。', notes: '一个排他网关只能有一条默认流；建议最后一个分支设为默认。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '旧表达式兼容',
              text: '如果现有表达式无法转换为可视化条件组，页面会保留原表达式且不自动覆盖。只有点击“清空并改用条件组”后才重建；发布前必须确认旧表达式仍符合当前变量和审批值。'
            }
          ]
        },
        {
          id: 'process-condition-groups',
          title: '条件组编辑器',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '组逻辑', meaning: 'AND 全部满足或 OR 任一满足。', defaultLimit: '根组默认 AND。', effect: '决定子条件连接符。', publish: '复杂表达式应通过分组明确优先级。' },
                { field: '嵌套组', meaning: '条件组内继续添加子组。', defaultLimit: '最大深度 4。', effect: '生成括号表达式。', publish: '过深规则难维护，建议拆分网关。' },
                { field: '属性', meaning: '审批结果 approved 或绑定实体字段。', defaultLimit: '字段下拉使用运行时字段名称/标识；审批结果读取来源用户任务审批选项。', effect: '决定表达式左值。', publish: '实体字段改名或编码变化后重新检查表达式。' },
                { field: '操作符', meaning: '==、!=、>、<、>=、<=、contains。', defaultLimit: '默认 ==。', effect: '按字段类型格式化值。', publish: 'contains 适合集合/字符串；数值字段输入必须可转数字。' },
                { field: '值', meaning: '选择或输入比较值。', defaultLimit: '选择字段使用选项 value；布尔使用 true/false；审批结果使用来源节点配置的 value。', effect: '生成引号、数字或布尔常量。', publish: '显示文案变化不影响 value；value 变化会改变分支。' },
                { field: '完整表达式', meaning: '只读显示最终 ${...}。', defaultLimit: '自动生成。', effect: '实际写入 BPMN conditionExpression。', publish: '保存前人工复核括号和变量。' }
              ]
            }
          ]
        },
        {
          id: 'process-condition-approved',
          title: '审批结果与分支',
          blocks: [
            {
              type: 'bullets',
              items: [
                '顺序流会读取来源用户任务的审批选项，把 option.value 作为 approved 的可选值。',
                '默认审批项 value 为 approve 和 reject；条件应比较 value，不比较“通过/驳回”显示文本。',
                '兼容旧表达式时，approved == true 会迁移为 approve，approved == false 会迁移为 reject。',
                '自定义“退回修改、转交、终止”等审批值时，所有出线条件都要覆盖，并保留一条默认流防止无路可走。',
                '同一审批项需要显示备注或备注必填时，在来源用户任务的“审批配置”中设置。'
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'process-forms-approval',
      index: '07',
      title: '节点表单、只读与审批项',
      summary: '开始事件和用户任务绑定实体表单或自定义表单，用户任务配置审批意见与按钮。',
      topics: [
        {
          id: 'process-node-forms',
          title: '节点表单来源',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '实体表单 entity', meaning: '从流程绑定实体的表单中选择一个或多个。', notes: '流程未绑定实体时不可选；第一个表单作为主表单加载字段。' },
                { option: '自定义表单 custom', meaning: '填写外部表单 Key。', notes: '目标运行页面必须能解析该 Key。' },
                { option: '无表单 none', meaning: '节点不展示业务表单。', notes: '开始事件无表单时要确保流程仍有业务数据来源。' }
              ]
            },
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '所属实体', meaning: '显示当前流程绑定实体。', defaultLimit: '只读；未绑定时显示警告。', effect: '决定可选实体表单和实体字段条件。', publish: '先在实体管理绑定流程。' },
                { field: '选择表单', meaning: '多选一个或多个实体表单。', defaultLimit: '可筛选；默认无显式配置时尝试实体默认表单。', effect: '运行节点展示多个表单，首个为主表单。', publish: '表单必须启用、字段已保存且目标环境存在。' },
                { field: '只读模式', meaning: '节点只能查看表单，不能编辑。', defaultLimit: '默认关闭。', effect: '写入 entityFormReadonly。', publish: '还会叠加表单字段的 approve/view 模式权限。' },
                { field: '表单 Key', meaning: '自定义表单技术标识。', defaultLimit: '示例 leave_apply_form。', effect: '运行时打开外部表单。', publish: 'Key 在目标环境必须注册。' }
              ]
            },
            {
              type: 'callout',
              tone: 'info',
              title: '自动默认表单',
              text: '流程绑定实体、节点没有表单配置且实体存在默认表单时，设计器会自动把默认表单 ID、只读 false 和实体编码写入 BPMN。更换默认表单不会自动更新已显式保存的节点。'
            }
          ]
        },
        {
          id: 'process-approval-options',
          title: '审批意见与审批选项',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '启用审批意见', meaning: '是否显示审批操作和备注区域。', defaultLimit: '默认开启。', effect: '关闭后节点不使用自定义审批项配置。', publish: '审批节点通常保持开启。' },
                { field: '审批意见名称', meaning: '备注输入区标签。', defaultLimit: '默认“审批意见”。', effect: '可改为审批备注、处理说明等。', publish: '与业务术语一致。' },
                { field: '选项名称 label', meaning: '用户看到的按钮文字。', defaultLimit: '默认通过、驳回。', effect: '显示为审批按钮。', publish: '可以改文案而不改 value。' },
                { field: '选项值 value', meaning: '流程变量和分支判断使用的稳定值。', defaultLimit: '默认 approve、reject；新增项默认空。', effect: '提交后写入审批结果，连线条件读取。', publish: '发布后不要随意修改已使用 value。' },
                { field: '样式 type', meaning: 'primary、success、warning、danger。', defaultLimit: '新增默认 primary；驳回默认 danger。', effect: '改变按钮颜色。', publish: '危险操作使用 danger。' },
                { field: '显示备注', meaning: '该操作是否显示备注输入。', defaultLimit: '默认开启。', effect: '允许用户填写意见。', publish: '不显示备注时 remarkRequired 无意义。' },
                { field: '备注必填', meaning: '选择该操作时必须填写备注。', defaultLimit: '默认关闭。', effect: '阻止空备注提交。', publish: '驳回、终止等建议必填。' },
                { field: '删除 / 添加选项', meaning: '维护审批动作集合。', defaultLimit: '至少保留 1 个；删除需确认。', effect: '改变运行时按钮和 approved 可选值。', publish: '同步维护所有出线条件、通知和流程动作。' }
              ]
            }
          ]
        },
        {
          id: 'process-readonly-matrix',
          title: '只读与字段模式叠加',
          blocks: [
            {
              type: 'table',
              columns: [
                { key: 'layer', label: '控制层' },
                { key: 'scope', label: '作用范围' },
                { key: 'result', label: '运行结果' }
              ],
              rows: [
                { layer: '节点只读模式', scope: '当前开始事件或用户任务选择的所有实体表单。', result: '开启后整张节点表单不可编辑。' },
                { layer: '表单字段 isReadonly', scope: '该字段在所有模式。', result: '字段显示但不可编辑。' },
                { layer: '表单运行模式 editable', scope: 'create/edit/approve/view 之一。', result: '对应模式关闭后字段不可编辑。' },
                { layer: '表单运行模式 visible', scope: 'create/edit/approve/view 之一。', result: '对应模式关闭后字段隐藏。' },
                { layer: '联动 disabledRule / visibilityRule', scope: '根据当前数据动态变化。', result: '运行中进一步禁用或隐藏。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '叠加原则',
              text: '任一层要求只读，最终即不可编辑；任一层要求隐藏，最终即不可见。敏感字段要关闭 visible，而不是只开启只读。'
            }
          ]
        }
      ]
    },
    {
      id: 'process-status-actions',
      index: '08',
      title: '实体状态与流程动作',
      summary: '在连线上更新实体状态，并在流程、节点和连线时机执行扩展动作。',
      topics: [
        {
          id: 'process-entity-status',
          title: '顺序流实体状态',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '来源节点 / 目标节点', meaning: '当前连线两端。', defaultLimit: '只读。', effect: '帮助确认状态更新发生在哪条路径。', publish: '节点重建后连线 ID 和映射会变化。' },
                { field: '实体状态', meaning: '流程经过连线时写入实体 status。', defaultLimit: '从绑定实体状态中选择并可清空；界面按 NEW、PROCESSING、COMPLETED、TERMINATED 分组。', effect: '任务完成选择该连线后更新业务记录状态。', publish: 'WITHDRAWN 通常由撤回运行逻辑维护，不在当前连线分组中显示。' },
                { field: '状态名称', meaning: '所选状态显示名。', defaultLimit: '只读。', effect: '帮助核对编码。', publish: '状态改名不改变编码。' },
                { field: '条件表达式', meaning: '当前连线的网关条件。', defaultLimit: '有条件时只读回显。', effect: '说明状态只在该条件分支执行。', publish: '条件与状态必须成对复核。' },
                { field: '说明', meaning: '状态变更说明。', defaultLimit: '可选。', effect: '保存到状态映射。', publish: '建议描述业务动作和后续允许操作。' }
              ]
            },
            {
              type: 'bullets',
              items: [
                '状态配置依赖流程已绑定实体并已维护状态列表。',
                '保存状态配置会同时更新 BPMN 扩展属性和后端状态映射；顶部仍需保存流程 XML。',
                '发布时服务端会从 BPMN 再同步状态映射，最终以发布 XML 为准。'
              ]
            }
          ]
        },
        {
          id: 'process-flow-action-scope',
          title: '流程动作作用域与时机',
          blocks: [
            {
              type: 'table',
              columns: [
                { key: 'scope', label: '作用域' },
                { key: 'timings', label: '内置时机' },
                { key: 'context', label: '主要上下文与用途' }
              ],
              rows: [
                { scope: 'PROCESS 全局', timings: 'PROCESS_STARTED', context: '流程变量、实体数据；初始化变量和业务关联。默认事务内 + 回滚。' },
                { scope: 'PROCESS 全局', timings: 'PROCESS_COMPLETED', context: '历史变量、实体数据，当前任务为空；归档、完成通知。默认提交后 + 重试。' },
                { scope: 'PROCESS 全局', timings: 'PROCESS_WITHDRAWN', context: '历史变量、撤回原因、实体数据；撤回通知和清理。默认提交后 + 重试。' },
                { scope: 'PROCESS 全局', timings: 'PROCESS_TERMINATED', context: '历史变量、终止原因、实体数据；异常清理。默认提交后 + 重试。' },
                { scope: 'NODE 节点', timings: 'NODE_ENTERED', context: '节点、执行实例、流程变量；进入节点时准备数据。默认事务内 + 回滚。' },
                { scope: 'NODE 节点', timings: 'NODE_COMPLETED', context: '节点完成、路由计算前；写变量可影响后续条件。默认事务内 + 回滚。' },
                { scope: 'NODE 用户任务', timings: 'TASK_CREATED', context: '任务 ID、名称、办理人和变量；通知下一办理人。默认提交后 + 重试。' },
                { scope: 'NODE 用户任务', timings: 'TASK_ASSIGNED', context: '任务和新办理人；认领、转办通知。默认提交后 + 重试。' },
                { scope: 'NODE 用户任务', timings: 'TASK_COMPLETING', context: '任务、审批动作、审批人和变量；审批前校验、核心写入。默认事务内 + 回滚。' },
                { scope: 'SEQUENCE_FLOW 连线', timings: 'TRANSITION_TAKEN', context: '来源、目标和变量；分支选中后、进入目标前处理审批结果和状态。默认事务内 + 回滚。' }
              ]
            },
            {
              type: 'callout',
              tone: 'info',
              title: '自定义时机',
              text: '后端可注册额外 FlowActionTriggerProvider。界面会按作用域和 BPMN 类型动态加载，并复用版本隔离、执行方式、失败策略和执行日志。'
            }
          ]
        },
        {
          id: 'process-flow-action-fields',
          title: '流程动作字段',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '快捷模板', meaning: '审批前校验、通知下一办理人、审批结果同步、流程完成通知、撤回清理。', defaultLimit: '可选。', effect: '快速带出名称、时机、方式和失败策略。', publish: '仍需选择实际处理器和参数。' },
                { field: '动作名称', meaning: '动作业务名称。', defaultLimit: '必填。', effect: '用于列表、版本和执行日志。', publish: '写清时机和目标系统。' },
                { field: '执行时机', meaning: '由当前作用域与节点类型动态提供。', defaultLimit: '新增默认第一个可用时机。', effect: '决定何时触发。', publish: '改变时机会改变可用上下文。' },
                { field: '执行方式', meaning: 'IN_TRANSACTION 或 AFTER_COMMIT。', defaultLimit: '随时机默认；处理器可限制支持方式。', effect: '决定是否与流程事务绑定。', publish: '外部通知优先 AFTER_COMMIT，强一致写入使用 IN_TRANSACTION。' },
                { field: '失败策略', meaning: '事务内 ROLLBACK/CONTINUE；提交后 RETRY/IGNORE。', defaultLimit: '随执行方式自动切换默认。', effect: '决定失败时流程、重试和日志行为。', publish: '详见事务失败策略。' },
                { field: '最大重试', meaning: 'AFTER_COMMIT + RETRY 的最大次数。', defaultLimit: '0–20，默认 5；指数退避，最长等待约 6 小时，超限进入死信。', effect: '控制异步重试。', publish: '处理器必须幂等。' },
                { field: '处理器', meaning: '从动作处理器目录选择；界面显示可配置中文名称，Bean 名只作为技术标识。', defaultLimit: '必填；仅显示 GLOBAL 或明确包含当前绑定实体的启用动作。', effect: '执行具体业务逻辑，并阻止其他实体误用不属于自己的动作。', publish: '目标环境必须存在相同 Bean，并配置兼容的中文目录、可见范围、时机和执行方式。' },
                { field: '描述', meaning: '动作说明。', defaultLimit: '可选。', effect: '帮助审计。', publish: '说明输入、输出、幂等和失败补偿。' },
                { field: '参数配置', meaning: '名称、类型和值。', defaultLimit: '类型为 string、number、boolean、variable、expression。', effect: '序列化为 paramsJson 传给处理器。', publish: '变量/表达式会自动补 ${...}；参数名不可重复。' },
                { field: '是否启用', meaning: '动作开关。', defaultLimit: '默认开启。', effect: '禁用动作不触发。', publish: '只有启用动作在发布前校验。' },
                { field: '排序', meaning: '同一绑定下动作执行顺序。', defaultLimit: '通过上下按钮调整。', effect: '按 sortOrder 执行。', publish: '动作有依赖时写清顺序；避免依赖未提交的异步结果。' }
              ]
            }
          ]
        },
        {
          id: 'process-flow-action-handler-catalog',
          title: '动作处理器中文目录与实体可见范围',
          lead: '超级管理员可在流程动作面板点击“处理器目录”，统一维护技术 Bean 的业务中文名称和使用边界。',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '处理器', meaning: 'Spring 容器中的 FlowActionHandler Bean 名和实现类。', defaultLimit: '只读；Bean 未注册时标记为不可用。', effect: '作为执行器定位实际代码的稳定技术键。', publish: '跨环境必须保持 Bean 名稳定，不能只迁移中文名称。' },
                { field: '中文名称', meaning: '流程设计器下拉框展示的业务名称。', defaultLimit: '必填，例如“发送待办通知”“同步审批结果”。', effect: '避免配置人员直接面对英文 Bean 名。', publish: '名称可调整，不影响已发布动作的 handlerName 快照。' },
                { field: '用途说明', meaning: '说明动作做什么、适合哪些时机、输入输出和风险。', defaultLimit: '可选但建议填写。', effect: '选择处理器后显示在字段下方，帮助避免误配。', publish: '外部调用应注明幂等、超时和补偿要求。' },
                { field: '可见范围', meaning: 'GLOBAL 表示所有流程可选；ENTITY 表示仅指定实体绑定的流程可选。', defaultLimit: '未配置处理器默认不可选；ENTITY 必须选择实体。', effect: '从后端过滤处理器目录，前端不能通过伪造 entityCode 绕过。', publish: '流程保存时后端会按 processConfigId 再次校验。' },
                { field: '指定实体', meaning: 'ENTITY 范围允许使用该动作的实体编码集合。', defaultLimit: '至少一项；支持多选。', effect: '当前流程绑定实体不在集合中时，下拉不显示且保存被拒绝。', publish: '实体编码必须存在；实体改绑流程后应重新检查动作。' },
                { field: '启用', meaning: '是否允许新流程配置选择此处理器。', defaultLimit: '未配置目录默认关闭。', effect: '关闭后从选择列表移除，但不会删除历史执行记录。', publish: '禁用已有草稿动作后，重新保存或发布会被后端阻止。' },
                { field: '保存', meaning: '保存当前处理器的中文目录配置。', defaultLimit: '仅 super_admin 可调用，后端同样强制校验。', effect: '立即影响流程设计器处理器选项。', publish: '配置属于目标环境运行能力，不导出密钥或外部 URL。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '不要混淆两种作用域',
              text: '动作目录的 GLOBAL/ENTITY 控制“哪些实体能选择某个处理器”；动作实例的 PROCESS/NODE/SEQUENCE_FLOW 控制“该动作绑定在流程、节点还是连线”。两者独立，不能互相替代。'
            }
          ]
        },
        {
          id: 'process-flow-action-log',
          title: '动作执行记录与手工重试',
          lead: '流程进度、实体审批和数据详情中的“流程动作”区域可按流程实例查看执行记录。',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '状态', meaning: 'PENDING 待执行、RUNNING 执行中、SUCCESS 成功、FAILED 等待重试、DEAD 失败/死信。', defaultLimit: '由执行器维护。', effect: '帮助判断动作是否完成或需要人工处理。', publish: '状态属于运行实例，不随草稿动作修改而改变。' },
                { field: '重试', meaning: '显示 retryCount / maxRetries。', defaultLimit: '由动作 retryConfig 决定。', effect: '可判断剩余自动重试机会。', publish: '达到上限后进入 DEAD。' },
                { field: '耗时 / 触发时间', meaning: '记录动作开始、结束、耗时和创建时间。', defaultLimit: '自动记录。', effect: '用于性能和延迟排查。', publish: '长耗时事务内动作应改为提交后。' },
                { field: '展开详情', meaning: '查看动作、处理器、作用域、元素、时机、任务/执行 ID、幂等键、参数、结果、上下文、执行轨迹和异常。', defaultLimit: '执行上下文、参数、结果和异常仅超级管理员可看，接口会脱敏。', effect: '用于定位变量解析、处理器和外部接口问题。', publish: '详情可能包含业务敏感信息，禁止截图外传。' },
                { field: '手工重试', meaning: 'FAILED 或 DEAD 状态可重新加入执行队列。', defaultLimit: '需二次确认。', effect: '再次执行同一动作。', publish: '重试前必须确认外部接口和处理器幂等，避免重复通知、扣款或写入。' },
                { field: '刷新', meaning: '重新加载当前流程实例记录。', defaultLimit: '手动触发。', effect: '查看最新重试和结果。', publish: '无配置影响。' }
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'process-transactions',
      index: '09',
      title: '事务、失败策略与高级配置',
      summary: '理解流程动作的事务边界，以及节点异步、跳过表达式和自动跳过。',
      topics: [
        {
          id: 'process-action-transaction',
          title: '事务内与提交后执行',
          blocks: [
            {
              type: 'table',
              columns: [
                { key: 'mode', label: '执行方式' },
                { key: 'behavior', label: '事务与失败行为' },
                { key: 'scenes', label: '推荐场景' }
              ],
              rows: [
                { mode: 'IN_TRANSACTION 事务内', behavior: '动作与流程保存、表单提交、任务流转处于同一数据库事务。ROLLBACK 失败时整次操作回滚；CONTINUE 记录失败后继续提交。', scenes: '审批前强校验、必须一致的核心业务写入、影响后续路由的变量计算。' },
                { mode: 'AFTER_COMMIT 提交后', behavior: '主事务先写 PENDING 执行记录，提交后异步执行。RETRY 失败自动重试并最终死信；IGNORE 记录失败后结束。', scenes: '通知、外部系统同步、耗时接口、允许最终一致的处理。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '外部调用与幂等',
              text: '提交后动作可能重复执行，处理器必须使用 idempotencyKey 或业务唯一键去重。事务内调用外部接口会延长数据库事务并放大网络抖动影响，界面会对疑似 http/notify/message/sync 处理器给出风险提示。'
            }
          ]
        },
        {
          id: 'process-failure-policies',
          title: '四种失败策略',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: 'ROLLBACK', meaning: '仅事务内；处理器失败抛异常，流程操作和同事务业务写入回滚。', notes: '适合必须成功的校验和核心数据；错误信息要可读。' },
                { option: 'CONTINUE', meaning: '仅事务内；记录错误后继续流程事务。', notes: '主流程成功但动作失败，必须有日志、告警或补偿。' },
                { option: 'RETRY', meaning: '仅提交后；失败按重试配置自动重试，超过次数进入死信。', notes: '处理器必须幂等，外部系统应支持重复请求去重。' },
                { option: 'IGNORE', meaning: '仅提交后；失败记录后不再重试。', notes: '只用于真正非关键、可丢失的动作。' }
              ]
            },
            {
              type: 'bullets',
              items: [
                '事务内动作只允许 ROLLBACK 或 CONTINUE；提交后动作只允许 RETRY 或 IGNORE，发布校验会拒绝错误组合。',
                '切换执行方式时界面自动把失败策略重置为该方式默认值。',
                '结束类动作中 currentTask 为空，应使用历史变量或实体数据。',
                'NODE_COMPLETED 发生在路由计算前，动作修改变量会改变后续条件。'
              ]
            }
          ]
        },
        {
          id: 'process-advanced',
          title: '节点高级配置',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '异步执行', meaning: '开启 Flowable 异步能力。', defaultLimit: '默认关闭。', effect: '开启后可选择异步前和异步后。', publish: '依赖异步作业执行器和数据库作业表正常运行。' },
                { field: '异步前', meaning: '在进入当前任务/网关前创建异步边界。', defaultLimit: '异步开启后可选。', effect: '前序事务提交后由作业继续。', publish: '变量和事务边界会变化。' },
                { field: '异步后', meaning: '完成当前任务/网关后创建异步边界。', defaultLimit: '异步开启后可选。', effect: '当前节点提交后由作业继续后续路径。', publish: '后续状态和动作可能延迟。' },
                { field: '跳过表达式', meaning: '满足表达式时跳过节点。', defaultLimit: '默认空；示例 ${skip}。', effect: '由 Flowable skip expression 语义处理。', publish: '必须同时确认引擎启用跳过表达式的条件。' },
                { field: '自动跳过', meaning: '平台扩展 skipNode。', defaultLimit: '默认关闭。', effect: '运行到节点时由 WorkflowAutoSkipService 直接流转。', publish: '会绕过人工办理或自动任务逻辑；只用于明确无需处理的节点。' }
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'process-publish-version',
      index: '10',
      title: '发布、版本与迁移',
      summary: '每次发布创建新 Flowable 部署、不可变历史版本和配置迁移资产。',
      topics: [
        {
          id: 'process-publish-dialog',
          title: '发布字段',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '流程名称', meaning: '当前发布对象。', defaultLimit: '只读。', effect: '确认避免发错流程。', publish: '发布前再次核对实体绑定。' },
                { field: '当前版本 / 新版本', meaning: '当前版本和即将发布版本。', defaultLimit: '新版本 = 当前版本 +1。', effect: '每次发布生成新历史版本。', publish: '不能覆盖旧版本。' },
                { field: '版本说明', meaning: '本次 BPMN、节点、表单、动作变化说明。', defaultLimit: '可选，建议必填。', effect: '写入版本历史。', publish: '包含需求号、关键节点、条件、人员和兼容策略。' },
                { field: '待导出清单', meaning: '发布成功后生成迁移资产。', defaultLimit: '默认开启。', effect: '出现在系统管理 / 配置迁移。', publish: '与配套实体使用同一迁移批次。' },
                { field: '迁移标记', meaning: '发布批次标识。', defaultLimit: '加入待导出时必填；自动生成建议值。', effect: '用于打包流程及依赖。', publish: '跨环境发布时保持唯一、可追踪。' }
              ]
            }
          ]
        },
        {
          id: 'process-publish-pipeline',
          title: '发布管线与校验',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '检查 BPMN XML', text: '流程必须已经保存且 XML 非空。' },
                { title: '校验启用动作', text: '检查作用域、时机、处理器、执行方式、失败策略和参数组合。' },
                { title: '同步状态映射', text: '从 BPMN 连线配置同步实体状态映射。' },
                { title: '清理和兼容 XML', text: '补 flowable 命名空间、使用 processKey、修复人员/多实例/脚本/跳过属性和旧 approved 表达式。' },
                { title: '准备动作与节点绑定', text: '清理历史动作监听器，同步节点表单和审批配置。' },
                { title: '部署 Flowable', text: '以 processKey.bpmn20.xml 和“流程名称 - v版本”创建新部署。' },
                { title: '记录版本', text: '保存 BPMN、节点表单快照、发布人、部署 ID，并复制草稿流程动作到版本。' },
                { title: '生成迁移资产', text: '按发布说明、待导出开关和迁移标记记录不可变快照。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '发布前必须保存',
              text: '发布读取数据库中的 bpmnXml。右侧节点面板和画布中未点击顶部“保存流程”的修改不会进入发布版本。'
            }
          ]
        },
        {
          id: 'process-version-history',
          title: '版本历史',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '版本列表', meaning: '显示版本号、说明、发布时间和动作数量。', notes: '用于审计每次部署。' },
                { option: '查看版本详情', meaning: '只读查看版本号、名称、标识、说明、发布人、时间和 BPMN 图。', notes: '历史 XML 不受后续草稿修改影响。' },
                { option: '查看版本流程动作', meaning: '显示动作顺序、名称、位置、时机、执行方式、接口和启用状态。', notes: '运行中的版本使用发布时复制的动作，不读取最新草稿动作。' },
                { option: '删除版本', meaning: '逻辑删除版本记录及其版本动作。', notes: '不可恢复；不会删除 Flowable 已部署定义，也不会停止运行实例。生产环境通常不应删除审计版本。' }
              ]
            }
          ]
        },
        {
          id: 'process-disable-delete',
          title: '禁用、删除与运行实例',
          blocks: [
            {
              type: 'bullets',
              items: [
                '禁用只把配置状态设为 DISABLED，用于阻止后续入口使用；已有 Flowable 部署和运行实例需要单独治理。',
                '删除是逻辑删除流程配置并禁用，不会自动清除部署、历史版本、业务实体数据或实例。',
                '实体绑定流程时只允许一个实体独占绑定；删除流程前先解除实体关系。',
                '修改已发布流程并保存后，状态仍可能显示 PUBLISHED，但运行定义仍是上次版本；只有再次发布才生效。',
                '重大变更优先新版本兼容运行实例，不要依赖删除旧版本。'
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'process-release-checklist',
      index: '11',
      title: '发布验收与排障',
      summary: '使用配置、运行、事务和版本四个维度完成上线前检查。',
      topics: [
        {
          id: 'process-release-checklist-items',
          title: '上线检查清单',
          blocks: [
            {
              type: 'checklist',
              items: [
                '流程名称、processKey、分类和绑定实体正确，BPMN 主路径完整。',
                '所有节点名称清晰，节点 ID 未无意义重建，说明文档覆盖复杂节点。',
                '用户任务办理人、候选人和动态接口在空结果、停用人员场景有兜底。',
                '多实例集合、元素变量、完成条件和并行/串行语义已用真实人员验证。',
                '所有服务、发送、接收、规则、脚本和子流程目标在目标环境存在。',
                '排他/包容网关条件覆盖所有情况，默认流唯一，approved 使用审批项 value。',
                '节点表单已保存、启用、模式权限正确；只读与隐藏叠加符合预期。',
                '审批选项 label/value、备注显示与必填、所有出线条件保持一致。',
                '每条状态连线选择正确实体状态，撤回和终止状态有独立运行策略。',
                '流程动作处理器、参数、时机、执行方式、失败策略、幂等和重试均通过校验。',
                '节点面板保存后已点击顶部“保存流程”，查看 XML 能看到最新配置。',
                '版本说明、迁移标记、依赖实体和配置包批次一致。'
              ]
            }
          ]
        },
        {
          id: 'process-troubleshooting',
          title: '常见问题定位',
          blocks: [
            {
              type: 'table',
              columns: [
                { key: 'symptom', label: '现象' },
                { key: 'checks', label: '优先检查' }
              ],
              rows: [
                { symptom: '发布后节点配置未生效', checks: '是否只点了节点 Tab 保存而未点顶部保存流程；查看 XML 是否含最新扩展属性；发布版本时间是否最新。' },
                { symptom: '任务无人办理', checks: '固定用户是否停用；组/角色是否有成员；表达式变量是否存在；接口返回是否为空或格式错误；多实例集合是否为空。' },
                { symptom: '网关无路可走', checks: '条件组完整表达式；字段类型和值；approved value；是否有且仅有一条默认流。' },
                { symptom: '表单为空或不可编辑', checks: '流程是否绑定实体；表单是否启用和保存字段；节点来源是否 entity；节点只读、字段只读、模式 editable、联动 disabled 是否叠加。' },
                { symptom: '实体状态未变化', checks: '是否配置在实际被选中的连线上；状态编码是否存在；流程发布时是否同步；绑定实体和流程实例业务键是否正确。' },
                { symptom: '动作未执行', checks: '动作是否启用并已发布；作用域和 elementId 是否匹配；时机是否发生；处理器是否注册；查看执行日志、重试和死信。' },
                { symptom: '流程卡在自动节点', checks: '服务/脚本/规则异常；REST 超时和错误策略；异步作业执行器；接收任务消息或超时；事务动作回滚。' },
                { symptom: '版本图与草稿不同', checks: '版本详情显示的是发布快照；草稿必须重新发布才进入新版本。' }
              ]
            }
          ]
        }
      ]
    }
  ]
}
