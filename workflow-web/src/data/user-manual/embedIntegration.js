const permissionColumns = [
  { key: 'permission', label: '权限码' },
  { key: 'purpose', label: '用途' },
  { key: 'role', label: '建议授予对象' }
]

const entryModeColumns = [
  { key: 'mode', label: '入口模式' },
  { key: 'parameter', label: '入口参数' },
  { key: 'usage', label: '用户看到的内容' }
]

const capabilityColumns = [
  { key: 'capability', label: 'V1 Capability' },
  { key: 'purpose', label: '能力' },
  { key: 'condition', label: '生效条件' }
]

const fieldPolicyColumns = [
  { key: 'policy', label: '字段策略' },
  { key: 'purpose', label: '控制范围' },
  { key: 'rule', label: '配置原则' }
]

const launchFieldColumns = [
  { key: 'field', label: '字段' },
  { key: 'source', label: '应由谁确定' },
  { key: 'rule', label: '规则' }
]

const eventColumns = [
  { key: 'event', label: 'SDK 事件' },
  { key: 'payload', label: '关键 Payload' },
  { key: 'hostAction', label: '宿主建议处理' }
]

const commandColumns = [
  { key: 'command', label: 'SDK 命令' },
  { key: 'method', label: '调用方式' },
  { key: 'purpose', label: '用途' }
]

const errorColumns = [
  { key: 'symptom', label: '现象' },
  { key: 'check', label: '优先检查' },
  { key: 'action', label: '处理方式' }
]

const tokenExample = `FLOW_API_BASE='https://api.flow.example.com'
CLIENT_ID='<Integration Application Client ID>'
CLIENT_SECRET='<只在第三方后端保存的 Client Secret>'

curl --request POST "$FLOW_API_BASE/oauth2/token" \\
  --user "$CLIENT_ID:$CLIENT_SECRET" \\
  --header 'Content-Type: application/x-www-form-urlencoded' \\
  --data-urlencode 'grant_type=client_credentials' \\
  --data-urlencode 'scope=embed.launch'`

const assertionExample = `// JWT Header（示意）
{
  "alg": "RS256",
  "kid": "partner-signing-key-2026-08",
  "typ": "JWT"
}

// JWT Claims（示意；必须由第三方后端签名）
{
  "iss": "https://id.partner.example",
  "sub": "employee-000123",
  "aud": "flow-embed-launch",
  "iat": 1787884800,
  "exp": 1787884860,
  "jti": "每次断言唯一且不可重用"
}`

const launchExample = `ACCESS_TOKEN='<oauth2/token 返回的 access_token>'
USER_ASSERTION='<第三方后端刚签发的短期人员 JWT>'

curl --request POST \\
  'https://api.flow.example.com/api/open/v1/embed-launches' \\
  --header "Authorization: Bearer $ACCESS_TOKEN" \\
  --header 'Content-Type: application/json' \\
  --header 'X-Trace-Id: partner-work-order-20260828-001' \\
  --header 'X-Request-Id: partner-launch-20260828-001' \\
  --data "{
    \"viewKey\": \"supplier-work-orders\",
    \"parentOrigin\": \"https://portal.partner.example\",
    \"channelId\": \"66f82f09-89ec-4a5a-b81b-f54f02d22262\",
    \"subject\": {
      \"type\": \"SIGNED_JWT\",
      \"assertion\": \"$USER_ASSERTION\"
    },
    \"entry\": { \"mode\": \"LIST\" },
    \"context\": { \"supplierId\": \"S-10086\" },
    \"ui\": { \"locale\": \"zh-CN\", \"theme\": \"light\" }
  }"`

const partnerBackendExample = `// 该接口运行在第三方后端。浏览器只提交 channelId 和受控入口意图。
export async function createWorkOrderEmbedLaunch(req, res) {
  const partnerUser = requireSignedInPartnerUser(req)
  const channelId = validateChannelId(req.body.channelId)
  const entry = allowEntry(req.body.entry, ['LIST', 'CREATE', 'VIEW'])

  const accessToken = await getFlowMachineToken({ scope: 'embed.launch' })
  const assertion = await signShortLivedUserAssertion({
    issuer: 'https://id.partner.example',
    subject: partnerUser.immutableSubject,
    audience: 'flow-embed-launch',
    expiresInSeconds: 60
  })

  const response = await fetch(
    'https://api.flow.example.com/api/open/v1/embed-launches',
    {
      method: 'POST',
      headers: {
        Authorization: 'Bearer ' + accessToken,
        'Content-Type': 'application/json',
        'X-Trace-Id': req.traceId
      },
      body: JSON.stringify({
        viewKey: 'supplier-work-orders',
        parentOrigin: 'https://portal.partner.example',
        channelId,
        subject: { type: 'SIGNED_JWT', assertion },
        entry,
        context: await loadAuthorizedContext(partnerUser),
        ui: { locale: 'zh-CN', theme: 'light' }
      })
    }
  )

  const envelope = await response.json()
  if (!response.ok) return res.status(response.status).json(envelope)
  res.setHeader('Cache-Control', 'no-store')
  return res.status(201).json(envelope.data)
}`

const sdkExample = `import FlowEmbed from '@flow/embed-sdk'

const channelId = crypto.randomUUID()
const launch = await fetch('/partner-api/work-orders/flow-embed-launch', {
  method: 'POST',
  credentials: 'same-origin',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    channelId,
    entry: { mode: 'LIST' }
  })
}).then(async response => {
  if (!response.ok) throw await response.json()
  return response.json()
})

const widget = FlowEmbed.mount({
  container: document.querySelector('#flow-work-orders'),
  embedUrl: launch.embedUrl,
  launchId: launch.launchId,
  launchCode: launch.launchCode,
  channelId,
  targetOrigin: 'https://embed.flow.example.com',
  height: { mode: 'auto', min: 480, max: 1200 },
  onEvent(event) {
    if (event.type === 'selection.changed') {
      updateSelectedRows(event.payload.selection)
    }
    if (event.type === 'form.saved') {
      refreshPartnerPage(event.payload.record)
    }
    if (event.type === 'session.expired') {
      remountWithANewLaunch()
    }
  },
  onViolation(error) {
    reportProtocolViolation(error.errorCode)
  }
})

// 页面或前端组件卸载时必须销毁。
window.addEventListener('pagehide', () => widget.destroy(), { once: true })`

const viewEntryExample = `// LIST 和 CREATE 不携带 recordId
{ "mode": "LIST" }
{ "mode": "CREATE" }

// VIEW 的 recordId 必须由第三方后端授权后确定
{ "mode": "VIEW", "recordId": "wo_20260828_0001" }`

export default {
  eyebrow: 'USER MANUAL · EMBED INTEGRATION',
  title: '嵌入集成用户手册',
  subtitle: '面向 Flow 管理员、第三方后端开发和宿主前端开发，说明如何把受控列表与表单安全嵌入外部系统，并让外部登录人员精确对应到 Flow 用户。',
  version: 'Flow Embed V1',
  updatedAt: '2026-08-28',
  intro: [
    {
      title: '嵌入集成不是永久免登录 URL',
      type: 'warning',
      text: '每次打开都由第三方可信后端创建一次性 Launch，浏览器再通过 SDK 建立短期 Embed Session。Client Secret、机器 Token、人员断言和 Flow 用户 Token 都不能交给浏览器。'
    },
    {
      title: 'V1 已开放的能力边界',
      type: 'info',
      text: '当前只开放列表查询、选择回传、记录查看和记录新建。RECORD_UPDATE、ACTION_EXECUTE、PROCESS_START、删除、导出和文件操作均为关闭状态。'
    }
  ],
  sections: [
    {
      id: 'embed-overview',
      index: '01',
      title: '认识嵌入集成',
      summary: '先理解第三方系统、Flow 管理面、Embed Runtime 和 Flow 用户权限之间的关系。',
      topics: [
        {
          id: 'embed-overview-purpose',
          title: '什么时候使用',
          lead: '外部系统希望在自己的页面内直接展示 Flow 的某个已发布列表或表单，同时继续使用 Flow 的字段权限、数据范围和表单规则时，使用 Embed Runtime。',
          blocks: [
            {
              type: 'bullets',
              items: [
                '适合：供应商门户查看工单、业务中台选择 Flow 记录、外部工作台新建或查看业务记录。',
                '不适合：服务器间批处理、流程消息回调或后台同步；这些场景使用开放 API 或 Webhook。',
                '不支持把普通后台页面、内部实体 API 或普通用户 Bearer Token 直接暴露给第三方。',
                '宿主页面保持自己的登录态；iframe 不跳转 Flow 登录页，也不依赖第三方 Cookie。'
              ]
            }
          ]
        },
        {
          id: 'embed-overview-flow',
          title: '一次打开经历什么',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '宿主请求', text: '已登录的第三方浏览器向自己的后端申请本次 Launch，并生成唯一 channelId。' },
                { title: '应用鉴权', text: '第三方后端以 OAuth Client Credentials 和 embed.launch Scope 向 Flow 证明应用身份。' },
                { title: '人员映射', text: '后端提交短期 SIGNED_JWT；Flow 根据 Provider 和精确 Binding 把 external subject 映射到一个已启用 Flow 用户。' },
                { title: '签发 Launch', text: 'Flow 固定 View、Release、Origin、Context、能力和用户，返回一次性 launchCode 与 embedUrl。' },
                { title: 'SDK 握手', text: '宿主 SDK 创建 iframe，经严格 Origin、Window、Nonce 与 MessageChannel 握手传递 launchCode。' },
                { title: '受限运行', text: '每个运行请求恢复真实 Flow UserContext，再取 Flow 权限与数据范围、View 策略和 Grant 上限的交集。' }
              ]
            },
            {
              type: 'callout',
              tone: 'success',
              title: '用户对应关系',
              text: '第三方用户不是临时匿名用户。只有已建立 ACTIVE Binding、且目标 Flow 用户仍存在并启用时才能 Launch；用户权限、字段权限和数据范围仍以该 Flow 用户为准。'
            }
          ]
        },
        {
          id: 'embed-overview-boundary',
          title: '入口与能力边界',
          blocks: [
            {
              type: 'table',
              title: '入口模式',
              columns: entryModeColumns,
              rows: [
                { mode: 'LIST', parameter: '只需 mode', usage: '打开已发布列表；可在 iframe 内进入允许的 VIEW / CREATE，再返回列表。' },
                { mode: 'CREATE', parameter: '只需 mode', usage: '直接打开新建表单；提交成功后通过 form.saved 回传受控字段。' },
                { mode: 'VIEW', parameter: 'mode + recordId', usage: '直接查看一条记录；recordId 必须在第三方后端授权后确定。' }
              ]
            },
            {
              type: 'table',
              title: 'V1 Capability',
              columns: capabilityColumns,
              rows: [
                { capability: 'LIST_QUERY', purpose: '加载、分页和筛选列表', condition: 'LIST View、已发布列表、Flow 查询权限与 Grant 均允许。' },
                { capability: 'SELECTION_RETURN', purpose: '把选中记录回传宿主', condition: 'View 返回策略与字段投影允许；宿主不能要求额外字段。' },
                { capability: 'RECORD_VIEW', purpose: '查看记录表单', condition: '表单已发布、记录在 Flow 数据范围内且行级能力允许。' },
                { capability: 'RECORD_CREATE', purpose: '新建记录', condition: 'CREATE 入口、表单与字段可写策略、Flow 创建权限均允许。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '明确关闭',
              text: 'RECORD_UPDATE、ACTION_EXECUTE、PROCESS_START、RECORD_DELETE、BATCH_DELETE、EXPORT、FILE_UPLOAD 和 FILE_DOWNLOAD 在 V1 中不能通过配置绕过。'
            }
          ]
        }
      ]
    },
    {
      id: 'embed-prerequisites',
      index: '02',
      title: '接入前准备',
      summary: '确认独立 Embed 域名、管理权限、应用凭据和双方提供的信息。',
      topics: [
        {
          id: 'embed-prerequisites-environment',
          title: '部署与域名前提',
          blocks: [
            {
              type: 'checklist',
              items: [
                'Embed 功能已由运维启用，并使用独立 HTTPS Origin，例如 https://embed.flow.example.com。',
                '管理后台/API Origin 与 Embed Origin 分离；专用 VHost 只暴露 Entry、Embed 静态资源和 /api/embed/v1/**。',
                '宿主生产 Origin 已确定为精确 scheme://host[:port]，不含路径、Query、Fragment 或通配符。',
                '第三方拥有可信后端，可安全保存 Client Secret、签名私钥并识别已登录用户。',
                '双方时间同步，能够生成短期 JWT，并有 Trace ID 与应急联系人。'
              ]
            },
            {
              type: 'callout',
              tone: 'danger',
              title: '纯静态前端不能生产接入',
              text: '浏览器不能保存 Client Secret，也不能自行决定 Flow 用户、viewKey、Origin、Context 或 recordId。缺少第三方可信后端时，请先补齐后端接入层。'
            }
          ]
        },
        {
          id: 'embed-prerequisites-permissions',
          title: 'Flow 管理权限',
          blocks: [
            {
              type: 'table',
              columns: permissionColumns,
              rows: [
                { permission: 'system:integration:view', purpose: '查看 Integration Application 与内部 Application ID', role: '接入负责人' },
                { permission: 'system:integration:manage', purpose: '创建应用、设置 embed.launch 与访问策略', role: '开放集成管理员' },
                { permission: 'system:integration:secret-rotate', purpose: '签发、轮换或吊销 Client Secret', role: '凭据管理员' },
                { permission: 'system:embed:view', purpose: '查看 View、Grant、Release 和 Launch / Session', role: '接入负责人、运维只读人员' },
                { permission: 'system:embed:manage', purpose: '创建 View、保存草稿、维护 Grant', role: '平台配置管理员' },
                { permission: 'system:embed:publish', purpose: '校验并发布不可变 View Release', role: '发布管理员，建议与日常编辑分离' },
                { permission: 'system:embed:identity-manage', purpose: '维护 Provider、公开 JWKS 和用户 Binding', role: '身份管理员' },
                { permission: 'system:embed:session-revoke', purpose: '撤销 Launch / Session', role: '安全运维人员' }
              ]
            },
            {
              type: 'paragraph',
              text: 'Integration Application 仍在“系统管理 → 开放集成”中创建；Embed View、Grant、Provider、Binding 和运行运维在“系统管理 → 嵌入集成”中完成。前端菜单可见性不替代服务端权限校验。'
            }
          ]
        },
        {
          id: 'embed-prerequisites-handover',
          title: '双方需要交换的信息',
          blocks: [
            {
              type: 'table',
              columns: [
                { key: 'party', label: '提供方' },
                { key: 'items', label: '信息' },
                { key: 'sensitive', label: '敏感处理' }
              ],
              rows: [
                { party: 'Flow 管理员', items: 'Client ID、Token URL、API Base URL、viewKey、Embed Origin、SDK 固定版本', sensitive: 'Client Secret 仅创建时展示一次，走 Secret Manager 交付。' },
                { party: '第三方', items: '测试/生产 Origin、Issuer、稳定 external subject 规则、公开 JWKS/JWKS URL、峰值与 Context 定义', sensitive: '只向 Flow 提供公钥；签名私钥永不离开第三方。' },
                { party: '业务管理员', items: 'external subject → Flow User ID 绑定清单、字段返回范围、数据范围与验收用户', sensitive: '按最小必要原则审批，不在普通日志记录 subject。' }
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'embed-platform-config',
      index: '03',
      title: '平台端配置 View 与 Grant',
      summary: '按依赖顺序创建机器应用、人员信任与映射，再发布 Embed View，并授权精确宿主 Origin。',
      topics: [
        {
          id: 'embed-platform-order',
          title: '必须遵循的配置顺序',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: 'Integration Application', text: '先创建应用并取得内部 Application ID、Client ID 与 Client Secret。' },
                { title: 'Identity Provider', text: '创建并启用 SIGNED_JWT Provider，配置 Issuer、Audience、算法与公开 JWKS。' },
                { title: 'Identity Binding', text: '把该 Application + Provider 下的 external subject 精确绑定到 Flow User ID。' },
                { title: 'View Release', text: '创建 View，保存草稿、通过校验并首次发布；DRAFT 首次发布后会变为 ACTIVE。' },
                { title: 'Application Grant', text: '最后创建引用已发布 View 和既有 Provider 的 Grant，配置 Origin、能力与限额后设为 ACTIVE。' },
                { title: '联合联调', text: '第三方后端创建 Launch，宿主 SDK 挂载，并完成正向、拒绝、过期与撤销场景。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '不能提前创建 Grant',
              text: 'Grant 必须引用已存在的 Identity Provider，且 View 已经拥有 Published Release。若 View 处于 DISABLED，重新发布不会自动启用，必须按变更流程显式恢复状态。'
            }
          ]
        },
        {
          id: 'embed-platform-application',
          title: '创建 Integration Application',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '进入开放集成', text: '打开“系统管理 → 开放集成”，创建第三方系统专用 Application；测试与生产不要共用。' },
                { title: '配置最小 Scope', text: '只授予 embed.launch；不要为了嵌入额外开放流程或其他 Open API Scope。' },
                { title: '保存凭据', text: '复制 Client ID 与只展示一次的 Client Secret，立即存入第三方 Secret Manager。' },
                { title: '记录 Application ID', text: '应用详情会显示并可复制内部 Application ID；后续 Grant 与 Binding 使用它，OAuth 使用 Client ID。不要混淆。' }
              ]
            }
          ]
        },
        {
          id: 'embed-platform-view',
          title: '创建、校验并发布 Embed View',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '创建 View', text: '进入“系统管理 → 嵌入集成 → View 与 Grant”，填写稳定 View Key、名称与 Surface（LIST 或 FORM）。View Key 是外部契约，发布后避免改变含义。' },
                { title: '绑定目标', text: '填写 Entity Code；LIST 再填写 List Key，可选 Default Form ID；FORM 必须填写 Form ID。所有目标都必须已有发布版本。' },
                { title: '选择入口', text: 'LIST 可启用 LIST / CREATE / VIEW；FORM 只允许 CREATE / VIEW。只启用实际需要的入口。' },
                { title: '选择 Release 策略', text: '首批生产建议 PINNED 并填写 List/Form Release ID；FOLLOW_ACTIVE 会跟随兼容的当前发布版。' },
                { title: '收窄能力和字段', text: '勾选四项 V1 Capability 的必要子集，并分别配置 Visible、Queryable、Writable、Returnable；Returnable 必须是 Visible 的安全子集。' },
                { title: '配置 Context', text: 'contextSchema 必须是封闭 JSON Object（即使没有属性也要显式配置），V1 禁止 pattern/$ref；contextBindings 只允许 FIXED_FILTER 或 FORCED_FORM_VALUE。' },
                { title: '发布', text: '依次“保存草稿 → 校验已保存版本 → 发布”。发布会生成不可变 Release、Revision 和 SHA-256 配置摘要。' }
              ]
            },
            {
              type: 'table',
              title: '字段策略',
              columns: fieldPolicyColumns,
              rows: [
                { policy: 'Visible', purpose: 'iframe 可以展示的字段', rule: '只列业务必须字段；内部字段、敏感字段和脚本组件不得进入。' },
                { policy: 'Queryable', purpose: '列表允许筛选的字段', rule: '必须是已发布列表声明可查询字段，操作符由发布配置决定。' },
                { policy: 'Writable', purpose: 'CREATE 可以提交的字段', rule: '仅对表单可写字段开放；固定 Context 字段不能被浏览器覆盖。' },
                { policy: 'Returnable', purpose: 'selection.changed / form.saved 可回传宿主的字段', rule: '这是跨系统数据边界，应比 Visible 更小；未列字段不会回传。' }
              ]
            },
            {
              type: 'paragraph',
              text: '入口与能力必须成对：LIST 需要 LIST_QUERY，CREATE 需要 RECORD_CREATE，VIEW 需要 RECORD_VIEW，SELECTION_RETURN 还必须同时具备 LIST_QUERY；查看或新建还要求已发布的 Form Release。'
            }
          ]
        },
        {
          id: 'embed-platform-grant',
          title: '创建 Application Grant',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '关联对象', text: '在 View 详情的 Application Grants 中填写内部 Application ID 和 Identity Provider ID。每个 View + Application 只有一份精确 Grant。' },
                { title: '精确 Origin', text: 'Allowed Origins 每行一个 HTTPS Origin，例如 https://portal.partner.example；禁止 *、路径、Query、Fragment 和凭据。' },
                { title: '设置 Release', text: 'Grant 的 PINNED 填 Embed View 的 Revision，不是草稿中的 List/Form Release ID；生产首发建议 PINNED。' },
                { title: '设置 Capability Ceiling', text: 'Grant 能力上限必须等于或小于 View 能力；最终能力还会与 Flow 用户权限和运行态行能力取交集。' },
                { title: '设置保护阈值', text: '配置每用户最大活跃 Session、Session 最长秒数、最大并发、Launch/分钟、Runtime/分钟和可选到期时间。' },
                { title: '启用 Grant', text: '确认 Provider、Binding、Origin、Release 和限额均正确后设为 ACTIVE。撤销用于永久终止，不作为临时停用。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: 'Trusted Subject 开关',
              text: 'SIGNED_JWT Provider 必须关闭“Trusted Subject Assertion”；只有经单独风险批准的 TRUSTED_EXTERNAL_ID Provider 才能启用。默认始终使用 SIGNED_JWT。'
            }
          ]
        }
      ]
    },
    {
      id: 'embed-identity',
      index: '04',
      title: '把外部人员映射为 Flow 用户',
      summary: 'Provider 验证人员断言来源，Binding 决定该 external subject 对应哪个 Flow 用户。',
      topics: [
        {
          id: 'embed-identity-provider',
          title: '配置 SIGNED_JWT Provider',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '新建 Provider', text: '进入“Identity Provider 与 Binding → Identity Providers”，Type 选择 SIGNED_JWT。' },
                { title: '固定信任域', text: '填写不可混用的 Subject Namespace、精确 Issuer，并在 Audiences 中包含 flow-embed-launch。' },
                { title: '选择算法', text: 'V1 仅使用 RSA/EC 公钥及 RS256/384/512、PS256/384/512、ES256/384/512；推荐 RS256 或 ES256。' },
                { title: '配置 JWKS', text: 'STATIC_JWK_SET 只粘贴公开 JWK；REMOTE_JWKS 使用受控 HTTPS URL。每把公钥必须有唯一 kid，严禁 d、p、q、k 等私钥参数。' },
                { title: '设置时钟窗口', text: '根据双方时钟同步质量设置 Clock Skew 和 Assertion 最长有效期；生产建议断言约 60 秒。' },
                { title: '状态与轮换', text: 'Provider 创建后即 ACTIVE；没有内建“测试断言”按钮，未准备好时应立即停用。轮换静态公钥会增加 keyVersion 并使旧会话失效。' }
              ]
            },
            {
              type: 'code',
              title: '短期人员断言 Claims 示例',
              language: 'json',
              code: assertionExample
            },
            {
              type: 'callout',
              tone: 'danger',
              title: '不要把普通登录 Token 当人员断言',
              text: '断言必须专用于 Flow Embed，aud 必须包含 flow-embed-launch，且每次使用唯一 jti。不要重用第三方普通业务 JWT 或 Flow 用户 JWT。'
            },
            {
              type: 'paragraph',
              text: 'Header 的 kid 必须唯一命中登记公钥；RSA 至少 2048 位，EC 曲线须匹配算法；typ 可省略，存在时只能是 JWT。JWT 内角色、组织或权限 Claim 不会授予任何 Flow 权限。'
            }
          ]
        },
        {
          id: 'embed-identity-binding',
          title: '建立精确 Binding',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '准备用户', text: '先在 Flow 中创建或确认目标用户，确保状态启用，并配置正确角色、字段权限和数据范围。' },
                { title: '新建 Binding', text: '进入 External Identity Bindings，填写 Application ID、Identity Provider、External Subject 和 Flow User ID。' },
                { title: '设置有效期', text: '按需填写生效与过期时间。迁移、离职或账号停用时应先停用或撤销 Binding。' },
                { title: '精确查找验证', text: '使用“按外部 Subject 精确查找”确认映射。管理端不会长期回显 external subject 原文。' }
              ]
            },
            {
              type: 'paragraph',
              text: 'SIGNED_JWT 的 sub 必须稳定、不可回收并与 Binding 的 External Subject 一致。Flow 会 trim、做 Unicode NFC 规范化后摘要匹配；不要使用邮箱、显示名等可能变化或复用的值。'
            },
            {
              type: 'callout',
              tone: 'info',
              title: '映射成功仍不等于有数据权限',
              text: 'Launch 后所有列表和记录访问仍执行该 Flow 用户的菜单/动作权限、字段权限和 DataScope。列表为空可能是正确的数据范围结果。'
            }
          ]
        }
      ]
    },
    {
      id: 'embed-backend',
      index: '05',
      title: '第三方后端创建 Launch',
      summary: '机器 Token、人员断言和 Launch 都必须在可信后端完成，浏览器只接收本次挂载所需的一次性结果。',
      topics: [
        {
          id: 'embed-backend-token',
          title: '取得机器 Access Token',
          blocks: [
            {
              type: 'code',
              title: 'OAuth Client Credentials',
              language: 'bash',
              code: tokenExample
            },
            {
              type: 'bullets',
              items: [
                '只在第三方后端调用 /oauth2/token；Client Secret 不进入前端构建产物、URL、日志或监控标签。',
                '申请 Scope 必须包含且通常只包含 embed.launch。',
                'Token 按 expires_in 在后端内存短期缓存；401 时重新取 Token，不把失败凭据返回浏览器。'
              ]
            }
          ]
        },
        {
          id: 'embed-backend-launch',
          title: '签发人员断言并调用 Launch API',
          blocks: [
            {
              type: 'table',
              title: 'Launch 请求字段归属',
              columns: launchFieldColumns,
              rows: [
                { field: 'viewKey', source: '第三方后端固定配置', rule: '不能接受浏览器任意指定；必须命中 Application Grant。' },
                { field: 'parentOrigin', source: '第三方后端固定配置', rule: '必须与真实宿主 Origin、Grant Allowed Origin 完全相等。' },
                { field: 'channelId', source: '宿主浏览器安全随机生成', rule: '本次挂载唯一；后端验证格式后原样提交，并与 SDK mount 使用同一值。' },
                { field: 'subject', source: '第三方后端', rule: '根据当前已登录用户签发短期 SIGNED_JWT；不能接收浏览器自报 Flow User ID。' },
                { field: 'entry', source: '后端白名单校验', rule: '只允许 View 已发布入口；VIEW 的 recordId 必须先做第三方业务授权。' },
                { field: 'context', source: '第三方后端业务权限', rule: '仅提交发布 Context Schema 允许的值，通常由租户、供应商或组织关系推导。' },
                { field: 'ui', source: '后端允许的宿主偏好', rule: 'locale 与 light/dark/system；不影响权限。' }
              ]
            },
            {
              type: 'code',
              title: '入口对象',
              language: 'json',
              code: viewEntryExample
            },
            {
              type: 'code',
              title: '创建一次性 Launch',
              language: 'bash',
              code: launchExample
            },
            {
              type: 'paragraph',
              text: '成功返回 HTTP 201，响应 envelope.data 包含 launchId、embedUrl、launchCode、expiresAt、view 和 protocolVersion。launchCode 只返回一次且有效期很短，必须立即交给当前宿主页面。'
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '请求对象是封闭契约',
              text: '未知字段会被拒绝；LIST/CREATE 必须完全省略 recordId，不能发送 null。SIGNED_JWT 也不能附带 namespace/externalUserId，即使值为 null。context 最多 32 个顶层属性。'
            }
          ]
        },
        {
          id: 'embed-backend-proxy',
          title: '推荐的宿主后端接口',
          lead: '宿主前端只调用自己的同源接口；该接口从登录会话推导人员和 Context，再代表应用访问 Flow。',
          blocks: [
            {
              type: 'code',
              title: '第三方后端伪代码',
              language: 'javascript',
              code: partnerBackendExample
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '响应必须 no-store',
              text: '第三方后端不得缓存或记录 Launch 响应正文。返回浏览器后应尽快清除服务端临时引用；浏览器同样不能写入 localStorage、sessionStorage、Cookie、URL 或分析日志。'
            }
          ]
        }
      ]
    },
    {
      id: 'embed-frontend',
      index: '06',
      title: '宿主前端挂载 SDK',
      summary: '使用 @flow/embed-sdk 创建 iframe、完成严格握手，并消费受校验的事件。',
      topics: [
        {
          id: 'embed-frontend-package',
          title: '取得固定版本 SDK',
          blocks: [
            { type: 'paragraph', text: '当前仓库尚未配置公共 npm Registry 或 CDN。发布负责人应从 packages/flow-embed-sdk 生成带版本的 tgz 并进入私有制品库；接入方安装经校验的固定版本后，示例中的 @flow/embed-sdk import 才可用。' },
            { type: 'code', title: '生成与安装版本化制品', language: 'bash', code: "cd workflow-web/packages/flow-embed-sdk\nnpm pack\n\n# 接入项目从已审批的私有制品地址或 tgz 安装\nnpm install ./flow-embed-sdk-1.0.0.tgz" }
          ]
        },
        {
          id: 'embed-frontend-mount',
          title: '安装与挂载',
          blocks: [
            {
              type: 'code',
              title: 'FlowEmbed.mount 示例',
              language: 'javascript',
              code: sdkExample
            },
            {
              type: 'checklist',
              items: [
                'embedUrl 必须是 HTTPS，Origin 与 targetOrigin 完全相等。',
                'embedUrl path 必须精确为 /embed/v1/launches/{launchId}，且不能带 query 或 fragment。',
                'launchId、launchCode、channelId 必须来自同一次 Launch；不要重用或混配。',
                '容器卸载、路由离开或 Session 过期时调用 destroy()。',
                '单独写 <iframe src="embedUrl"> 无法传递 launchCode；必须使用固定版本 SDK。SDK 使用 allow-forms allow-scripts allow-same-origin sandbox，禁止自行 postMessage("*")。'
              ]
            }
          ]
        },
        {
          id: 'embed-frontend-events',
          title: '处理运行事件',
          blocks: [
            {
              type: 'table',
              columns: eventColumns,
              rows: [
                { event: 'connected（SDK 本地事件）', payload: '无 payload；顶层有 launchId、channelId', hostAction: '表示严格握手已建立；业务命令应从此时开始发送。' },
                { event: 'ack', payload: 'command；顶层 requestId 关联命令', hostAction: '确认 refresh / 主题 / 语言 / focus / destroy 命令已被 iframe 接收。' },
                { event: 'initialized', payload: 'viewKey、surfaceType、capabilities', hostAction: '解除宿主加载态；按实际能力决定外围提示。' },
                { event: 'resize', payload: 'height', hostAction: 'auto 高度模式由 SDK 自动限制在配置的 min/max。' },
                { event: 'selection.changed', payload: 'selection[{ id, values }]', hostAction: '更新宿主选择；values 仅含发布策略允许回传的字段。' },
                { event: 'form.saved', payload: 'receiptId、record、clientMutationId', hostAction: '刷新宿主数据或提示成功；record.values 仅含 Returnable 字段。' },
                { event: 'error', payload: 'errorCode、message、traceId、recoverable', hostAction: '展示可理解提示，用 traceId 联系 Flow 运维；不要展示 Token。' },
                { event: 'session.expired', payload: 'reason、relaunchRequired', hostAction: 'destroy 旧实例，向第三方后端申请新 Launch 后重新 mount。' }
              ]
            },
            {
              type: 'callout',
              tone: 'info',
              title: 'postMessage 不是可靠业务回调',
              text: 'selection.changed 与 form.saved 适合驱动当前页面交互，不应替代后端对账、消息投递或可靠 Webhook。宿主刷新后应以自己的后端或 Flow 权威数据为准。'
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '不要依赖预留事件',
              text: 'navigation.request、action.started、action.completed 和 close.requested 仅在 SDK 类型中预留，当前 V1 iframe 不发送；LIST / CREATE / VIEW / BACK 导航在 iframe 内部完成。'
            }
          ]
        },
        {
          id: 'embed-frontend-commands',
          title: '可用命令与会话处理',
          blocks: [
            {
              type: 'table',
              columns: commandColumns,
              rows: [
                { command: 'refresh', method: 'widget.refresh()', purpose: '重新加载当前列表或表单运行数据。' },
                { command: 'set-theme', method: "widget.setTheme('dark')", purpose: '切换 light / dark / system 主题。' },
                { command: 'set-locale', method: "widget.setLocale('zh-CN')", purpose: '切换允许的界面语言。' },
                { command: 'focus', method: 'widget.focus()', purpose: '把键盘焦点移入 iframe。' },
                { command: 'destroy', method: 'widget.destroy()', purpose: '清理端口、iframe、计时器和事件监听。' }
              ]
            },
            {
              type: 'paragraph',
              text: '业务命令必须在 connected 后发送，否则会得到 FLOW_EMBED_NOT_CONNECTED；命令返回 requestId，随后以相同 requestId 收到 ack 或 error。改变用户、View、Context 或入口记录时必须重新 Launch。'
            }
          ]
        }
      ]
    },
    {
      id: 'embed-user-operation',
      index: '07',
      title: '用户如何操作列表和表单',
      summary: '宿主只接收受控结果，实际查询、查看与新建仍由 iframe 内的 Flow Runtime 完成。',
      topics: [
        {
          id: 'embed-user-list',
          title: '列表、筛选与选择回传',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '加载列表', text: 'LIST_QUERY 生效后，iframe 按已发布列、固定 Context 和 Flow DataScope 查询。宿主不能传内部 SQL 或任意筛选操作符。' },
                { title: '使用筛选', text: '只显示 Queryable 字段；操作符和数据类型由发布列表定义，用户输入只提供值。' },
                { title: '选择记录', text: 'SELECTION_RETURN 生效时，选择变化触发 selection.changed；宿主只收到允许回传的 id 与 values。' },
                { title: '打开详情', text: 'RECORD_VIEW 生效且当前行允许时，可在 iframe 内进入 VIEW；返回列表时保留当前分页、筛选和选择状态。' }
              ]
            }
          ]
        },
        {
          id: 'embed-user-create',
          title: '新建表单与保存回执',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '打开新建', text: '可从 CREATE 入口直接打开，或从 LIST 内部进入。只有 RECORD_CREATE 与表单创建权限同时满足时可提交。' },
                { title: '填写字段', text: '只渲染安全白名单字段类型；Writable 字段可编辑，固定 Context 和只读字段不能覆盖。联动评估不产生写入。' },
                { title: '服务端校验', text: 'Flow 执行发布表单约束、必填、类型、枚举、唯一性和实体创建权限；失败时保留表单并返回封闭错误。' },
                { title: '幂等保存', text: '同一次提交使用稳定 Idempotency-Key；超时重试不会重复创建。成功后返回 receiptId 并触发 form.saved。' },
                { title: '宿主刷新', text: '宿主用 form.saved 更新页面；若要读取完整记录，仍通过授权的数据接口或新 Launch，不依赖事件中未返回字段。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '当前不支持编辑',
              text: 'VIEW 始终只读。即使旧配置中出现 EDIT 或 RECORD_UPDATE，V1 发布与运行时也会拒绝；不要把“查看表单”误认为可修改记录。'
            }
          ]
        }
      ]
    },
    {
      id: 'embed-troubleshooting',
      index: '08',
      title: '联调排查与安全要求',
      summary: '按调用阶段定位问题，并始终用 traceId 关联，不收集敏感凭据。',
      topics: [
        {
          id: 'embed-troubleshooting-errors',
          title: '常见问题排查',
          blocks: [
            {
              type: 'table',
              columns: errorColumns,
              rows: [
                { symptom: 'HTTP 400 / 413', check: '未知/null 字段、Context Schema、请求体大小', action: '修正参数或发布配置；PAYLOAD_TOO_LARGE 不应原样重试。' },
                { symptom: 'OAuth 401', check: 'Client 状态、Client Secret、Token URL、embed.launch Scope', action: '在第三方后端修正凭据；不要把 Secret 发给浏览器排查。' },
                { symptom: 'Launch 403', check: 'Application、Grant、Origin、Provider、Binding、Flow 用户状态', action: '按 traceId 查审计，确认 external subject 与 Application 的精确 Binding。' },
                { symptom: 'Launch 429', check: 'Launch/IP 限流与 Retry-After', action: '按 Retry-After 退避；禁止立即循环重试。' },
                { symptom: 'iframe 被浏览器拒绝', check: '专用 Embed 域名、TLS、CSP frame-ancestors 与 parentOrigin', action: '确认实际宿主 Origin 与 Allowed Origin 字节级一致。' },
                { symptom: 'iframe 跳转登录', check: '是否误用了普通后台 URL 或普通 Request 客户端', action: '只使用 launch.embedUrl 和官方 SDK；Embed 不走 Flow 登录。' },
                { symptom: '握手 / Exchange 401 或 410', check: 'Launch TTL、是否已消费、launch/channel/Origin/Nonce 是否混配', action: '废弃旧 Launch，申请一组新的 launchId/launchCode/channelId。' },
                { symptom: '列表为空', check: '固定 Context、Flow DataScope、列表发布规则', action: '用同一 Flow 用户核对数据范围，不要先扩大 View 字段或权限。' },
                { symptom: '记录 404', check: '资源不存在或该 Flow 用户无权访问', action: '两种情况故意不区分；不要通过重试探测记录是否存在。' },
                { symptom: '按钮或字段缺失', check: 'View、Grant Ceiling、Flow 权限、行级能力、字段策略的交集', action: '找出最先拒绝的一层；不要把 Capability 当作授予 Flow 权限。' },
                { symptom: '创建 409', check: '唯一性冲突或 Idempotency-Key 被不同请求复用', action: '同一请求保持相同 Key；业务内容改变必须使用新 Key。' },
                { symptom: '创建 422', check: 'FORM_VALIDATION_FAILED 的 data.violations', action: '提示并修正表单字段，不需要重新 Launch。' },
                { symptom: 'Runtime 503', check: '发布资源或集成依赖暂不可用', action: '有限退避；若 Exchange 结果不确定则废弃旧 Launch。' },
                { symptom: 'session.expired', check: '绝对到期、闲置、用户/Binding/Grant/View 状态或人工撤销', action: '销毁旧 widget，由后端重新 Launch；不要跳 Flow 登录。' }
              ]
            }
          ]
        },
        {
          id: 'embed-troubleshooting-security',
          title: '必须遵守的安全边界',
          blocks: [
            {
              type: 'checklist',
              items: [
                'Client Secret、机器 Access Token、签名私钥、人员断言和 Flow 用户 Token 仅存在于可信后端。',
                'launchCode 不进入 URL、Referrer、浏览器存储、错误上报、埋点、访问日志或截图。',
                '宿主使用精确 targetOrigin；禁止 postMessage 的 *，禁止接受来源不匹配的事件。',
                '第三方浏览器不得提交 flowUserId、entityCode、listKey、formId、releaseId、Provider ID 或任意 Runtime URL。',
                'External Subject 使用不可变且不可回收的标识；Binding 变更、用户停用和公钥轮换都有审批与审计。',
                'Returnable 字段按最小必要原则评审，特别检查个人信息、内部状态和租户隔离字段。',
                'error 只记录 errorCode、traceId 和必要上下文；不得记录 Token、断言、launchCode 或完整 subject。'
              ]
            }
          ]
        },
        {
          id: 'embed-troubleshooting-operations',
          title: 'Launch / Session 运维',
          blocks: [
            {
              type: 'paragraph',
              text: '在“系统管理 → 嵌入集成 → Launch / Session 运维”中按时间、Application 或 View 查询安全投影；可撤销单条 Launch、单条 Session，或按 View / Application 做有界批量撤销。'
            },
            {
              type: 'bullets',
              items: [
                'Secret 泄漏：立即停用或轮换 Application 凭据，撤销相关 Grant/Session，并检查审计。',
                '签名密钥泄漏：撤销 Provider 或轮换公开 JWKS，撤销受影响 Session，再更换第三方私钥。',
                '人员离职或错误映射：停用/撤销 Binding，并撤销该用户的活跃 Session。',
                'View 配置问题：先停用 Grant 阻断新 Launch；修正、校验、发布新 Revision 后再恢复。'
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'embed-go-live',
      index: '09',
      title: '联调与上线检查清单',
      summary: '用正向、拒绝和失效场景共同证明接入边界，而不是只验证一次成功打开。',
      topics: [
        {
          id: 'embed-go-live-test',
          title: '联合验收场景',
          blocks: [
            {
              type: 'checklist',
              items: [
                '已绑定且启用的用户可以打开，权限、字段和 DataScope 与预期一致。',
                '未绑定、Binding 停用、Flow 用户停用、Grant 停用时 Launch 均被拒绝。',
                '错误 Origin、通配 Origin、HTTP Origin 和错误 targetOrigin 均不能完成挂载。',
                '同一 launchCode 二次使用、过期后使用、混用 channelId 均失败且不泄漏原因细节。',
                'LIST 分页/筛选、selection.changed 字段投影、VIEW 数据范围均正确。',
                'CREATE 成功、校验失败、唯一性冲突、超时幂等重试与 form.saved 投影均正确。',
                'Session 到期与人工撤销触发 session.expired，宿主能申请新 Launch 并干净重挂载。',
                '错误与审计可用 traceId 关联，日志中不存在 Secret、Token、断言、launchCode 和完整 subject。'
              ]
            }
          ]
        },
        {
          id: 'embed-go-live-checklist',
          title: '上线检查清单',
          blocks: [
            {
              type: 'checklist',
              items: [
                '测试与生产使用不同 Application、Client Secret、Provider、Binding 和 Allowed Origin。',
                '专用 Embed Origin/VHost/Ingress、TLS、同源 Runtime 代理和动态 frame-ancestors 已在真实环境验证。',
                'Application 仅有 embed.launch；Client Secret 与签名私钥已进入 Secret Manager 并配置轮换责任人。',
                'SIGNED_JWT 的 issuer、aud=flow-embed-launch、算法、kid、短期 exp 与 jti 防重放均已验证。',
                '每个 external subject 都精确映射到正确且启用的 Flow 用户，离职/变更流程明确。',
                '生产首发使用 PINNED Release；View、Grant Ceiling、Context 和 Returnable 字段已经业务与安全评审。',
                'RECORD_UPDATE、ACTION_EXECUTE、PROCESS_START、删除、导出和文件操作保持关闭。',
                'Launch、Runtime、并发与 Session 时长阈值经过容量验证，429 退避策略已实现。',
                '宿主 SDK 与 API 主版本固定；真实 Chrome 及目标 Edge/Firefox/Safari 兼容性已验收。',
                '应急停用、Secret/JWKS 轮换、Binding 失效、批量 Session 撤销和 traceId 排查已演练。'
              ]
            },
            {
              type: 'callout',
              tone: 'success',
              title: '完成标准',
              text: '平台配置、用户映射、第三方后端、宿主 SDK、安全拒绝场景和运维撤销全部验收后，才把生产 Grant 设为 ACTIVE 并开放真实用户流量。'
            }
          ]
        }
      ]
    }
  ]
}
