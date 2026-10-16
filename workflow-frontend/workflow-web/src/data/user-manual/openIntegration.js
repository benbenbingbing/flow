const fieldColumns = [
  { key: 'field', label: '配置项' },
  { key: 'meaning', label: '含义' },
  { key: 'rule', label: '配置规则' },
  { key: 'effect', label: '运行效果 / 注意事项' }
]

const optionColumns = [
  { key: 'option', label: '状态 / 操作' },
  { key: 'meaning', label: '含义' },
  { key: 'usage', label: '使用建议' }
]

const tokenExample = `FLOW_API_BASE='https://flow.example.com'
CLIENT_ID='<创建应用时获得的 Client ID>'
CLIENT_SECRET='<仅显示一次的 Client Secret>'

curl --request POST "$FLOW_API_BASE/oauth2/token" \\
  --user "$CLIENT_ID:$CLIENT_SECRET" \\
  --header 'Content-Type: application/x-www-form-urlencoded' \\
  --data-urlencode 'grant_type=client_credentials'`

const embedLaunchExample = `ACCESS_TOKEN='<oauth2/token 返回的 access_token>'

curl --request POST "$FLOW_API_BASE/api/open/v1/embed-launches" \\
  --header "Authorization: Bearer $ACCESS_TOKEN" \\
  --header 'Content-Type: application/json' \\
  --header 'X-Trace-Id: partner-embed-20260910-001' \\
  --data '{
    "viewKey": "customer-overview",
    "parentOrigin": "https://portal.example.com",
    "channelId": "channel-20260910-0001",
    "subject": {
      "type": "SIGNED_JWT",
      "assertion": "<short-lived-person-assertion>"
    },
    "entry": { "mode": "LIST" },
    "context": {},
    "ui": {
      "locale": "zh-CN",
      "theme": "light",
      "formPresentation": "seamless"
    }
  }'`

export default {
  eyebrow: 'USER MANUAL · OPEN INTEGRATION',
  title: '开放集成用户手册',
  subtitle: '说明接入应用的创建、状态管理、Client Credentials 认证、应用凭据轮换，以及 Embed Launch 的后端调用方式。',
  version: '接入应用精简版',
  updatedAt: '2026-09-10',
  intro: [
    {
      title: '一个应用对应一个机器调用方',
      type: 'info',
      text: '接入应用保存外部系统的机器身份、责任组织、来源 CIDR、请求上限和并发上限。创建完成后获得 Client ID 与一次性展示的 Client Secret。'
    },
    {
      title: 'Client Secret 只显示一次',
      type: 'warning',
      text: '创建或轮换凭据后，应立即把 Client Secret 存入企业密钥管理系统。页面后续只显示凭据尾部提示，无法恢复原值。'
    },
    {
      title: '机器凭据只能放在服务端',
      type: 'success',
      text: 'Client ID 与 Client Secret 用于调用方后端获取短期访问令牌。不要把 Client Secret、令牌请求或 Embed Launch 请求放进浏览器代码。'
    }
  ],
  sections: [
    {
      id: 'integration-overview',
      index: '01',
      title: '开放集成解决什么问题',
      summary: '为 Embed 宿主后端提供可停用、可审计、可轮换的机器身份。',
      topics: [
        {
          id: 'integration-current-boundary',
          title: '当前能力边界',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '接入应用', meaning: '标识一个独立的机器调用方，并保存限流、并发和来源网段约束。', usage: '测试与生产环境分别创建；不同外部系统不要共用。' },
                { option: 'Client Credentials', meaning: '调用方后端使用 Client ID 与 Client Secret 换取短期访问令牌。', usage: '在服务端缓存令牌，并在过期前安全刷新。' },
                { option: 'Embed Launch', meaning: '调用方后端使用访问令牌签发一次性嵌入启动信息。', usage: '具体页面、人员映射与来源限制继续在“嵌入集成”中配置。' }
              ]
            },
            {
              type: 'callout',
              tone: 'info',
              title: '页面只管理应用与应用凭据',
              text: '开放集成页面负责应用列表、应用状态和凭据生命周期。Embed View、Identity Provider、Identity Binding 与 Application Grant 在“嵌入集成”页面维护。'
            }
          ]
        }
      ]
    },
    {
      id: 'integration-application',
      index: '02',
      title: '应用列表与创建',
      summary: '创建独立应用，并用稳定标识定位调用方。',
      topics: [
        {
          id: 'integration-application-list',
          title: '列表和详情怎么看',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '应用名称', meaning: '管理员识别系统、环境和用途的名称。', rule: '最多 128 字符，建议包含系统与环境。', effect: '可按名称搜索。' },
                { field: 'Application ID', meaning: '平台内部稳定应用标识。', rule: '由平台生成。', effect: '用于 Embed Application Grant 等平台配置。' },
                { field: 'Client ID', meaning: '机器认证用户名。', rule: '由平台生成。', effect: '与 Client Secret 配对调用 /oauth2/token。' },
                { field: '状态', meaning: 'ACTIVE、DISABLED 或 REVOKED。', rule: '只有 ACTIVE 应用可以获取新令牌。', effect: '停用用于临时隔离；吊销表示应用不可恢复。' },
                { field: '最近使用', meaning: '当前活跃凭据最近一次成功认证时间。', rule: '从未使用时显示“从未”。', effect: '用于识别闲置应用或切换是否完成。' },
                { field: '版本', meaning: '应用配置的乐观锁版本。', rule: '状态与凭据操作携带 expectedVersion。', effect: '版本冲突时刷新后再确认操作。' }
              ]
            }
          ]
        },
        {
          id: 'integration-application-create',
          title: '新建应用',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '应用名称', meaning: '描述机器调用方。', rule: '必填。', effect: '建议使用“系统-环境-用途”格式。' },
                { field: '责任组织', meaning: '记录应用的业务或技术负责人归属。', rule: '可选，最多 64 字符。', effect: '方便审计与故障联络。' },
                { field: '说明', meaning: '记录应用用途和负责人信息。', rule: '可选，最多 500 字符。', effect: '不要写入任何密钥。' },
                { field: '每分钟请求上限', meaning: '应用级请求速率限制。', rule: '1～10000。', effect: '超过限制时请求被拒绝，应采用有上限的退避。' },
                { field: '并发上限', meaning: '应用允许同时处理的请求数量。', rule: '1～1000。', effect: '根据调用方吞吐和平台容量设置。' },
                { field: '来源 CIDR', meaning: '允许访问的调用方出口网段。', rule: '每行一个 CIDR；留空表示不限制。', effect: '生产建议配置固定出口，并验证代理转发链。' }
              ]
            },
            {
              type: 'steps',
              items: [
                { title: '填写基本信息', text: '确认系统、环境、责任组织、限流和来源 CIDR。' },
                { title: '创建并签发凭据', text: '提交后立即保存 Application ID、Client ID 和 Client Secret。' },
                { title: '完成后端接入', text: '仅在调用方后端配置凭据，再验证令牌与 Embed Launch。' }
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'integration-status-credential',
      index: '03',
      title: '状态与凭据生命周期',
      summary: '使用停用、启用、轮换和吊销完成日常变更与应急隔离。',
      topics: [
        {
          id: 'integration-status',
          title: '应用状态',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: 'ACTIVE', meaning: '应用有效，且拥有有效凭据时可以获取新令牌。', usage: '正常运行状态。' },
                { option: 'DISABLED', meaning: '临时阻止新令牌和新的接入请求。', usage: '故障隔离、计划维护或切换验证时使用；问题解决后可重新启用。' },
                { option: 'REVOKED', meaning: '应用被永久吊销。', usage: '确认不再使用或发生严重泄漏时采用；后续应创建替代应用。' }
              ]
            }
          ]
        },
        {
          id: 'integration-credential',
          title: '凭据轮换与吊销',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '准备变更窗口', text: '确认调用方可以立即更新 Client Secret，并准备失败处置和负责人；旧凭据不可回退。' },
                { title: '轮换凭据', text: '平台签发新的 Client Secret，旧凭据立即失效；关闭弹窗前保存新值。' },
                { title: '更新调用方', text: '在服务端密钥管理系统中替换凭据，重新获取令牌并执行最小验证。' },
                { title: '确认最近使用', text: '刷新应用详情，确认新凭据已经成功使用。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '吊销不会自动补发',
              text: '吊销当前凭据后，应用无法继续获取令牌。需要恢复时由有权限的管理员执行轮换，签发一组新凭据。'
            }
          ]
        }
      ]
    },
    {
      id: 'integration-client-credentials',
      index: '04',
      title: 'Client Credentials 认证',
      summary: '调用方后端用应用凭据获取短期访问令牌。',
      topics: [
        {
          id: 'integration-token-request',
          title: '获取机器令牌',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '地址', meaning: '令牌端点。', rule: 'POST /oauth2/token。', effect: '使用 application/x-www-form-urlencoded。' },
                { field: '客户端认证', meaning: '用 Client ID 与 Client Secret 证明应用身份。', rule: '使用 HTTP Basic。', effect: '凭据只保存在调用方后端。' },
                { field: 'grant_type', meaning: 'OAuth 授权类型。', rule: '固定为 client_credentials。', effect: '不要提交浏览器用户信息。' },
                { field: 'access_token', meaning: '短期机器访问令牌。', rule: '按 expires_in 缓存并刷新。', effect: '不得写入日志、URL、浏览器存储或前端代码。' }
              ]
            },
            { type: 'code', title: '获取令牌', language: 'bash', code: tokenExample },
            {
              type: 'callout',
              tone: 'info',
              title: '共享短期缓存',
              text: '同一调用方实例应复用尚未过期的令牌，避免每次请求都访问令牌端点。刷新失败时采用短暂且有上限的退避。'
            }
          ]
        }
      ]
    },
    {
      id: 'integration-embed-launch',
      index: '05',
      title: 'Embed Launch',
      summary: '使用机器令牌为受控宿主页面签发一次性启动信息。',
      topics: [
        {
          id: 'integration-embed-request',
          title: '调用启动接口',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '完成嵌入配置', text: '先在“嵌入集成”配置 View、Identity Provider、Identity Binding 与 Application Grant。' },
                { title: '识别当前人员', text: '调用方后端从已登录会话确定人员，并生成符合 Provider 配置的短期断言。' },
                { title: '获取机器令牌', text: '调用 /oauth2/token，复用仍有效的后端令牌缓存。' },
                { title: '创建 Launch', text: '调用 POST /api/open/v1/embed-launches，使用服务端确定的 viewKey、parentOrigin、channelId、人员与入口。' },
                { title: '返回浏览器', text: '只把当前请求需要的 embedUrl 与 launchCode 返回宿主前端，并设置 Cache-Control: no-store。' }
              ]
            },
            { type: 'code', title: '创建 Embed Launch', language: 'bash', code: embedLaunchExample },
            {
              type: 'callout',
              tone: 'warning',
              title: '一次性且短时有效',
              text: 'launchCode 不应写入日志、分析参数或持久化存储。浏览器收到后应立即交给 Embed SDK，并限制消息 targetOrigin。'
            }
          ]
        }
      ]
    },
    {
      id: 'integration-operations',
      index: '06',
      title: '排错与上线检查',
      summary: '围绕应用状态、凭据、来源网络和 Embed 配置定位问题。',
      topics: [
        {
          id: 'integration-troubleshooting',
          title: '常见问题',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: 'invalid_client', meaning: 'Client ID 或 Client Secret 错误，凭据已失效，或者应用不可用。', usage: '核对应用状态、凭据尾部提示和最近轮换时间。' },
                { option: '来源地址被拒绝', meaning: '调用方出口地址不在应用配置的 CIDR 内。', usage: '确认实际出口 IP、反向代理信任配置和 CIDR。' },
                { option: '请求被限流', meaning: '超过每分钟请求上限或并发上限。', usage: '复用令牌，限制并发，并使用带抖动的有上限退避。' },
                { option: 'Embed Launch 被拒绝', meaning: '应用、凭据、View、Grant、人员映射、Origin 或入口配置不可用。', usage: '先使用“嵌入集成”的接入检查逐项确认。' },
                { option: '配置版本冲突', meaning: '另一管理员已经更新应用。', usage: '刷新详情，核对状态和版本后重新执行操作。' }
              ]
            }
          ]
        },
        {
          id: 'integration-go-live',
          title: '上线检查清单',
          blocks: [
            {
              type: 'checklist',
              items: [
                '测试与生产使用不同接入应用和不同凭据。',
                '应用名称、责任组织和说明能够定位系统、环境与负责人。',
                '生产来源 CIDR 已配置，并用真实出口和代理链验证。',
                '每分钟请求上限和并发上限与容量评估一致。',
                'Client Secret 只保存在服务端密钥管理系统，日志和浏览器均不可见。',
                '令牌在服务端短期缓存，刷新失败采用有上限退避。',
                'Embed View、Application Grant、身份提供方、人员映射和 Origin 均已验证。',
                '停用、轮换与吊销操作具备负责人、审计记录和恢复步骤。',
                '已验证应用停用、凭据失效、来源拒绝、限流和人员映射失败路径。'
              ]
            }
          ]
        }
      ]
    }
  ]
}
