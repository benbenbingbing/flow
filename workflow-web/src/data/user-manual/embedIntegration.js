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

const runtimeBoundaryColumns = [
  { key: 'policy', label: '边界' },
  { key: 'purpose', label: '控制范围' },
  { key: 'rule', label: '准确含义' }
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
    if (event.type === 'close.requested') {
      // Flow 原生表单的关闭/取消由宿主完成销毁与 Session Logout。
      void widget.destroy().catch(reportEmbedLifecycleFailure)
    }
    if (event.type === 'session.expired') {
      void closeAndRelaunch().catch(reportEmbedLifecycleFailure)
    }
  },
  onViolation(error) {
    reportProtocolViolation(error.errorCode)
  }
})

async function closeAndRelaunch() {
  // destroy 失败或超时时 await 会抛错，因而不会继续签发新 Launch。
  await widget.destroy()
  return remountWithANewLaunch()
}

// pagehide 不会等待异步 Logout，这里只能做 best effort 清理。
window.addEventListener('pagehide', () => {
  void widget.destroy().catch(() => {})
}, { once: true })`

const viewEntryExample = `// LIST 和 CREATE 不携带 recordId
{ "mode": "LIST" }
{ "mode": "CREATE" }

// VIEW 的 recordId 必须由第三方后端授权后确定
{ "mode": "VIEW", "recordId": "wo_20260828_0001" }`

const localListLaunchBody = `{
  "viewKey": "req-list",
  "parentOrigin": "https://localhost:3443",
  "channelId": "<本次挂载生成的 channelId>",
  "subject": {
    "type": "SIGNED_JWT",
    "assertion": "<第三方后端签发，sub=demo-lisi-001>"
  },
  "entry": { "mode": "LIST" },
  "context": {},
  "ui": { "locale": "zh-CN", "theme": "light" }
}`

const localEmbedDemoCommands = `# 1. 启动 Flow（.env 中 START_LOCAL_MYSQL=false，不会启动 Docker）
./start.sh start

# 2. 启动第三方宿主与独立 Embed HTTPS Origin；固定使用手册中的 LIST 示例
cd examples/embed-local-demo
npm run setup
npm run check
FLOW_DEMO_VIEW_KEY=req-list FLOW_DEMO_ALLOWED_ENTRY_MODES=LIST npm start

# 3. 浏览器访问
# https://localhost:3443`

export default {
  eyebrow: 'USER MANUAL · EMBED INTEGRATION',
  title: '嵌入集成用户手册',
  subtitle: '面向 Flow 管理员、第三方后端开发和宿主前端开发，说明如何把受控列表与表单安全嵌入外部系统，并让外部登录人员精确对应到 Flow 用户。',
  version: 'Flow Embed V1',
  updatedAt: '2026-09-01',
  intro: [
    {
      title: 'LIST 与 FORM 都直接运行 Flow 原生页面',
      type: 'success',
      text: '第三方只保存稳定 viewKey 并使用 Flow 返回的 embedUrl。iframe 直接挂载 Flow 自己的列表或表单代码，不根据 Embed 参数重画字段、列或按钮；日期、下拉、富文本、弹窗、自定义组件、数据源和后续新增组件均复用同一套 Flow Runtime。'
    },
    {
      title: '嵌入集成不是永久免登录 URL',
      type: 'warning',
      text: '每次打开都由第三方可信后端创建一次性 Launch，浏览器再通过 SDK 建立短期 Embed Session。Client Secret、机器 Token、人员断言和 Flow 用户 Token 都不能交给浏览器。'
    },
    {
      title: 'V1 已开放的能力边界',
      type: 'info',
      text: 'View 与 Grant 的 Capability 只决定允许从哪个入口打开，以及哪些事件或命令可以跨 iframe Bridge；它不改写原生页面的字段、列、按钮和 API 权限。页内所有操作继续按映射 Flow 用户的现有权限、对象权限、记录状态和 DataScope 执行。'
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
          lead: '外部系统希望在自己的页面内直接展示 Flow 的某个 ACTIVE 列表或表单，同时继续使用 Flow 原有的页面代码、字段权限、数据范围和业务规则时，使用 Embed Runtime。',
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
          id: 'embed-overview-native-form-help',
          title: '？“嵌入 Flow 列表或表单”到底是什么意思',
          lead: '它表示在受限 iframe Session 中直接挂载 Flow 自己的列表或表单页面，不是 Embed 收到 Schema 后再拼一张相似页面。',
          blocks: [
            {
              type: 'table',
              title: '容易误解的概念',
              columns: [
                { key: 'term', label: '概念' },
                { key: 'meaning', label: '准确含义' },
                { key: 'notMeaning', label: '不代表' }
              ],
              rows: [
                { term: '稳定 URL', meaning: 'URL 规则固定为 /embed/v1/launches/{launchId}；每次打开仍由后端签发一次性 Launch。', notMeaning: '永久保存某个 launchId 或免登录公开链接。' },
                { term: '原生页面', meaning: 'LIST 与 FORM 都运行 Flow 原页面、完整 Registry、原生 API 和权限链。', notMeaning: '只复用几个组件，或维护 Embed 专用列/控件白名单。' },
                { term: '稳定目标', meaning: '管理员在页面选择实体后，再选择该实体的列表或表单；系统只保存这些资源的稳定标识。', notMeaning: '要求管理员或第三方手填 entityCode、listKey、formId、Release 或 API 地址。' },
                { term: '最新 ACTIVE 解析', meaning: '每次新 Launch 自动取得目标表单或列表当前 ACTIVE 发布版；已打开 Session 固定启动时版本。', notMeaning: '用户填写过程中热切换表单，或让第三方传版本号。' },
                { term: '映射用户', meaning: '每个原生请求均按 Binding 对应的 Flow 用户检查按钮、字段、对象权限、记录状态和 DataScope。', notMeaning: '第三方 Application 或 Capability 自动获得管理员权限。' },
                { term: 'Capability', meaning: '只控制 LIST / CREATE / VIEW 入口，以及允许跨域传递的 Bridge 事件或命令。', notMeaning: '隐藏原生按钮、替代 Flow 权限，或给原生 API 额外授权。' },
                { term: 'Returnable', meaning: '只限制 form.saved / selection.changed 能跨 iframe 发给宿主的值。', notMeaning: '裁剪或重写 iframe 中的原生表单字段。' }
              ]
            },
            {
              type: 'callout',
              tone: 'info',
              title: '？以后新增组件还要改 Embed 吗',
              text: '不需要为列表列、字段类型、数据源、弹框或按钮修改 Embed 框架。新组件只要在 Flow 原生页面中完成注册、激活和权限接入，下一次 Launch 就随最新 ACTIVE 版本加载；只有新增跨系统能力或改变宿主 Bridge 协议时，才需要升级 Embed。'
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
                { title: '签发 Launch', text: 'Flow 从管理端保存的稳定资源引用解析最新 ACTIVE，并把目标运行版本、Origin、Context、入口/Bridge 边界和映射用户固定到 Session，返回一次性 launchCode 与 embedUrl。' },
                { title: 'SDK 握手', text: '宿主 SDK 创建 iframe，经严格 Origin、Window、Nonce 与 MessageChannel 握手传递 launchCode。' },
                { title: '受限运行', text: '每个页内请求恢复真实 Flow UserContext，使用与 Flow 原系统相同的权限、对象权限、业务状态和 DataScope；View/Grant 只守住入口与跨域 Bridge。' }
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
                { mode: 'LIST', parameter: '只需 mode', usage: '打开目标列表最新 ACTIVE 版本；页内按钮和弹框完全按映射用户在 Flow 中的实际结果显示。' },
                { mode: 'CREATE', parameter: '只需 mode', usage: '直接打开新建表单；提交成功后通过 form.saved 回传受控字段。' },
                { mode: 'VIEW', parameter: 'mode + recordId', usage: '直接查看一条记录；recordId 必须在第三方后端授权后确定。' }
              ]
            },
            {
              type: 'table',
              title: 'V1 Capability',
              columns: capabilityColumns,
              rows: [
                { capability: 'LIST_QUERY', purpose: '允许 LIST 入口和列表运行通道', condition: 'View 与 Grant 均开放；列表中实际可见数据仍只由映射用户权限和 DataScope 决定。' },
                { capability: 'SELECTION_RETURN', purpose: '允许 selection.changed 跨域回传', condition: 'View、Grant 和 Returnable 均允许；宿主不能要求额外字段。' },
                { capability: 'RECORD_VIEW', purpose: '允许宿主使用 VIEW 直接入口', condition: 'View 与 Grant 均开放；LIST 页内原生导航、记录可见性和按钮仍由 Flow 原权限链决定。' },
                { capability: 'RECORD_CREATE', purpose: '允许宿主使用 CREATE 直接入口', condition: 'View 与 Grant 均开放；LIST 页内原生新建按钮及能否保存仍由 Flow 原权限链决定。' },
                { capability: 'ACTION_EXECUTE', purpose: '跨域 Bridge 动作能力的保留标识', condition: '不控制 iframe 内原生按钮；V1 未开放宿主任意调用原生动作的通用命令。' }
              ]
            },
            {
              type: 'callout',
              tone: 'info',
              title: '？为什么不需要用 ACTION_EXECUTE 打开页内按钮',
              text: 'iframe 内的保存、保存并发起、列表工具栏、行按钮、自定义按钮和弹框，本来就是 Flow 原生页面的一部分，必须与映射用户在 Flow 内看到的结果一致。ACTION_EXECUTE 不能用来隐藏或放大这些按钮；只有未来显式开放“宿主跨域发起动作”时，它才用于 Bridge 边界。'
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
                '管理后台/API Origin 与 Embed Origin 分离；专用 VHost 只暴露 Entry、Embed 静态资源、/api/embed/v1/** 控制面，以及显式声明 Capability 与 TargetBinding 的 Flow 原生运行时端点。',
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
                { permission: 'system:embed:view', purpose: '查看嵌入配置、Grant 和 Launch / Session', role: '接入负责人、运维只读人员' },
                { permission: 'system:embed:manage', purpose: '创建、保存嵌入配置并维护 Grant', role: '平台配置管理员' },
                { permission: 'system:embed:identity-manage', purpose: '维护 Provider、公开 JWKS 和用户 Binding', role: '身份管理员' },
                { permission: 'system:embed:session-revoke', purpose: '撤销 Launch / Session', role: '安全运维人员' }
              ]
            },
            {
              type: 'paragraph',
              text: 'Integration Application 仍在“系统管理 → 开放集成”中创建；嵌入配置、Grant、Provider、Binding 和运行运维在“系统管理 → 嵌入集成”中完成。前端菜单可见性不替代服务端权限校验。'
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
      summary: '按依赖顺序创建机器应用、人员信任与映射，再保存嵌入配置，并授权精确宿主 Origin。',
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
                { title: '嵌入配置', text: '选择类型，搜索选择目标实体，再从该实体下选择一个 ACTIVE 列表或表单并保存；不填写编码、ID 或版本。首次有效保存自动启用。' },
                { title: 'Application Grant', text: '最后创建引用该配置和既有 Provider 的 Grant，配置 Origin、能力与限额后设为 ACTIVE。' },
                { title: '联合联调', text: '第三方后端创建 Launch，宿主 SDK 挂载，并完成正向、拒绝、过期与撤销场景。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '不能提前创建 Grant',
              text: 'Grant 必须引用已存在的 Identity Provider 和有效嵌入配置。若配置处于 DISABLED，保存配置不会自动启用，必须按变更流程显式恢复状态。'
            }
          ]
        },
        {
          id: 'embed-platform-complete-example',
          title: '完整例子：把“需求列表”嵌入第三方系统',
          lead: '下面使用稳定示例 req-list / ZDWREQ / list001。管理员在 LIST 目标区域只选择“实体 → 列表”，系统内部保存稳定资源引用；第三方只使用稳定 View Key。Flow 激活新字段、新组件或新布局后，下一次 Launch 自动运行最新 ACTIVE 版本的原生代码。',
          blocks: [
            {
              type: 'callout',
              tone: 'warning',
              title: '？为什么示例不提供 Application、Provider 或 Grant ID',
              text: '这些 ID 只在创建后由当前环境生成，复制其他环境的值必然无效。请从当前页面复制真实 Application ID、Provider ID 和 Flow User ID；目标实体与列表必须用级联选择器选择，不能手填 Entity Code、List Key、Release 或虚构 Grant ID。'
            },
            {
              type: 'table',
              title: '本机示例配置值',
              columns: [
                { key: 'object', label: '配置对象' },
                { key: 'field', label: '字段' },
                { key: 'value', label: '示例值' },
                { key: 'explanation', label: '说明' }
              ],
              rows: [
                { object: 'Integration Application', field: '名称 / Scope / Application ID', value: '嵌入联调示例 / embed.launch / 从当前环境复制', explanation: '创建独立应用，不复用已有业务 Client；Application ID 与 OAuth Client ID 不是同一个值。' },
                { object: 'Identity Provider', field: 'Provider ID / Issuer / Audience', value: '从当前环境复制 / https://id.embed-demo.local / flow-embed-launch', explanation: '类型 SIGNED_JWT、算法 RS256；只登记公开 JWK，私钥保留在第三方后端。' },
                { object: 'Identity Binding', field: 'External Subject / Flow 用户', value: 'demo-lisi-001 / 当前环境中已启用且有权限的用户', explanation: '运行时使用这个映射用户自己的权限与 DataScope；不要照抄示例用户 ID。' },
                { object: '嵌入配置', field: '配置 Key / 类型', value: 'req-list / LIST', explanation: '第一次有效保存即启用，不存在 Embed 发布步骤；后续目标 ACTIVE 变化不要求第三方修改配置。' },
                { object: '目标列表', field: '实体 / 列表', value: 'ZDWREQ / list001', explanation: '在级联选择器中按名称选择；LIST 目标不填写默认表单、编码、ID 或版本。每次新 Launch 取最新 ACTIVE，已打开 Session 固定启动时版本。' },
                { object: '入口与能力', field: 'Entry Modes / Capabilities', value: 'LIST / LIST_QUERY', explanation: '这里只开放宿主 LIST 直接入口；原生新增、查看、编辑及其他按钮完全按映射用户在 Flow 中的权限、记录状态和 DataScope 显示与执行。' },
                { object: '跨域返回', field: 'Returnable', value: '按需选择，默认空', explanation: '只限制 selection.changed 离开 iframe 的值，不改变 iframe 内列、筛选、按钮或表单。' },
                { object: 'Application Grant', field: 'Origin / Ceiling', value: 'https://localhost:3443 / LIST_QUERY', explanation: 'Grant ID 由当前环境创建后生成，无需在示例中预填；Ceiling 只能收窄入口与 Bridge，不能改变映射用户的页内权限。' },
                { object: '保护阈值', field: 'Session / 限流 / 并发', value: '每用户 1 个、1800 秒、Launch 30/分钟、Runtime 120/分钟、并发 5', explanation: '本机联调的保守值；若提示活跃 Session 已达上限，请先在 Launch / Session 运维中撤销旧会话。' }
              ]
            },
            {
              type: 'callout',
              tone: 'info',
              title: '目标不能在高级 JSON 中改写',
              text: '实体、列表或表单由上方级联选择器管理；高级 JSON 只保留 Context、UI 和跨域返回等边界配置。保存时会清理旧的字段投影和按钮覆盖，避免形成第二套列表或表单。'
            },
            {
              type: 'steps',
              items: [
                { title: '先配置身份', text: '创建 SIGNED_JWT Provider，再创建 demo-lisi-001 → lisi 的 Binding；JWT sub 必须与 demo-lisi-001 完全一致。' },
                { title: '保存嵌入配置', text: '创建 LIST 类型配置，在目标区域只按名称选择实体 ZDWREQ 和列表 list001 后直接保存；服务端同步校验，第一次有效保存即启用，不存在单独的嵌入发布步骤。' },
                { title: '最后创建 Grant', text: '填写内部 Application ID、Provider ID、精确 HTTPS Origin；SIGNED_JWT 必须关闭 Trusted Subject Assertion，部署完成前状态保持 DISABLED。' },
                { title: '先测 LIST', text: '首轮 Launch 使用 entry.mode=LIST。进入后直接核对 list001 的列、筛选、数据源、按钮和弹框是否与同一映射用户在 Flow 原生页面中一致。' }
              ]
            },
            {
              type: 'code',
              title: '本示例的 Launch Body',
              language: 'json',
              code: localListLaunchBody
            },
            {
              type: 'callout',
              tone: 'info',
              title: '配置完成不等于运行入口已开放',
              text: '真正联调前还要准备四类彼此独立的材料：Open API RSA 密钥对、Embed Context AES 密钥、Embed HMAC 密钥，以及第三方人员 JWT 私钥与 Flow 保存的公开 JWKS；同时启用 Open API 与 Embed Runtime，配置 Public Base URL、专用 HTTPS Embed Origin 和静态资源 VHost。未完成这些部署前，Grant 应保持 DISABLED。'
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '为什么后台预览和嵌入效果不同？',
              text: 'LIST 与 FORM 的 iframe 都使用同一套 Flow 表单运行时和列表运行时；若内容或按钮不同，通常是后台预览了尚未激活的版本、旧 Session 固定了启动时版本，或映射用户的权限/DataScope/记录状态不同。它不应由 Embed 控件兼容造成；出现组件、弹框、数据源或按钮行为不同应按缺陷处理。'
            },
            {
              type: 'steps',
              items: [
                { title: '先核对目标 ACTIVE 版本', text: '配置变化不会影响已打开页面。先在 Flow 设计器激活目标资源的新版本；下一次新 Launch 会自动取得它，无需重新保存嵌入配置。嵌入配置本身没有发布步骤。' },
                { title: '再核对页面来源', text: 'LIST 与 FORM 都不维护 Visible、Queryable、Writable、控件参数或列渲染参数：字段、列、顺序、布局、数据源、联动、弹层和组件全部来自原生 ACTIVE 页面。Returnable 只控制跨 iframe 回传值。' },
                { title: '确认映射用户', text: '字段是否可写、记录是否可见以及保存是否允许，继续使用 Binding 映射的 Flow 用户实时权限和 DataScope；Application 或外部 subject 不能放大该用户权限。' },
                { title: '确认操作栏来源', text: 'LIST 与 FORM 的工具栏、行按钮、关闭、重置、保存、保存并发起流程，以及名称、样式、顺序、显隐和禁用原因，全部来自启动时固定的 Flow 原生页面；Embed 不用 actionPolicy 或 ACTION_EXECUTE 重写按钮。' },
                { title: '确认 Session 边界', text: '新 Launch 自动选择目标最新 ACTIVE 发布版；已存在 Session 固定启动时版本，不会在填写过程中切换。' },
                { title: '让修改真正生效', text: '激活 Flow 列表或表单新版本后，关闭旧 widget 并等待 Logout 完成，再创建新 Launch。第三方的 viewKey、Launch Body、SDK 挂载方式和 Embed URL 规则都不需要修改。' },
                { title: '验证原生组件', text: '列表渲染器、数据源、日期、时间、单选/多选下拉、级联、富文本、确认框和自定义扩展都应与同一 Flow 用户在原生运行页一致；新增组件只需进入 Flow 原生 Registry，不需要给 Embed 增加映射。' }
              ]
            },
            {
              type: 'callout',
              tone: 'info',
              title: '？日期、下拉和确认弹窗会不会变样',
              text: '不会走 native input 或 Embed 自制弹层。iframe 内运行 Flow 原生 Element Plus/扩展组件，弹层挂载在 iframe 自己的 document.body 中，因此日期面板、下拉 Popper、校验提示和确认对话框的样式、键盘行为与 Flow 原生运行页一致。'
            }
          ]
        },
        {
          id: 'embed-platform-local-preview',
          title: '本机查看“别的系统嵌入”效果',
          lead: '仓库内置了一个真实第三方系统模拟页：蓝色门户属于外部系统，白色 iframe 运行区来自独立 Flow Embed Origin；它会走真实 OAuth、人员 JWT、Launch、Exchange、用户映射和 Form Runtime，不是静态 Mock。',
          blocks: [
            {
              type: 'code',
              title: '启动本机演示',
              language: 'bash',
              code: localEmbedDemoCommands
            },
            {
              type: 'steps',
              items: [
                { title: '先确认 Embed 证书', text: '首次使用自签名证书时，先打开 https://localhost:8443/embed-assets/embed-main.css，在浏览器高级选项中仅对本机 localhost 继续访问。' },
                { title: '再打开第三方门户', text: '访问 https://localhost:3443；若再次出现证书提示，同样只对本机 localhost 确认。页面顶部蓝色区域就是模拟的外部系统。' },
                { title: '创建真实 Launch', text: '入口选择“列表（LIST）”，点击“创建 Launch 并打开”。状态应依次变为“等待安全握手”“安全通道已连接”“Flow 已加载”。' },
                { title: '观察边界', text: '白色区域是 Flow 原生 list001 页面：列、筛选、按钮和数据源应与同一用户在 Flow 中一致；从原生按钮打开表单后，可继续核对日期面板、下拉 Popper、富文本和确认框。下方日志不会显示 Client Secret、JWT、Token 或 Launch Code。' },
                { title: '结束联调', text: '点击“关闭嵌入”并等待安全注销完成；正常关闭会自动释放活跃 Session 名额。只有关闭失败时，才按页面 traceId 进入“嵌入集成 → Launch / Session 运维”检查或撤销 Session。停止演示服务可在 npm start 终端按 Ctrl+C。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '证书确认仅用于本机演示',
              text: '生产环境必须使用受信任 CA 签发的 HTTPS 证书，不能让用户绕过证书警告，也不能关闭 TLS 校验。宿主 Origin https://localhost:3443 与 Embed Origin https://localhost:8443 必须保持隔离。'
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
          title: '创建并保存嵌入配置',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '创建配置', text: '进入“系统管理 → 嵌入集成 → 配置与 Grant”，填写稳定配置 Key、名称与类型（LIST 或 FORM）。配置 Key 是外部契约。' },
                { title: '选择实体', text: '在实体选择器中按名称搜索并选择 ACTIVE 实体；页面显示业务名称，系统内部保存稳定实体标识，不能手填实体编码。' },
                { title: '选择原生页面', text: 'LIST 类型从所选实体下选择一个 ACTIVE 列表；FORM 类型选择一个 ACTIVE 表单。LIST 不需要再配置默认表单，列表按钮引用哪个表单由 Flow 原生列表配置自行解析。' },
                { title: '选择入口', text: 'LIST 可启用 LIST / CREATE / VIEW；FORM 只允许 CREATE / VIEW。只启用实际需要的入口。' },
                { title: '确认版本行为', text: '每次新 Launch 自动取得目标资源最新 ACTIVE 发布版；已打开 Session 固定启动时版本，第三方请求不携带版本号。' },
                { title: '确认页面来源', text: 'LIST 与 FORM 都直接挂载 Flow 原生页面，不存在 EXPLICIT 字段投影或 Embed 专用渲染器；从 LIST 打开的表单、弹框和下拉同样走 Flow 原生代码。' },
                { title: '确认按钮与 API', text: '列表工具栏、行按钮和表单操作栏都按映射 Flow 用户的现有权限、对象权限、流程/记录状态和 DataScope 显示并调用原生 API；View/Grant Capability 不参与按钮塑形。' },
                { title: '配置 Context', text: 'contextSchema 必须是封闭 JSON Object（即使没有属性也要显式配置），V1 禁止 pattern/$ref；contextBindings 只允许 FIXED_FILTER 或 FORCED_FORM_VALUE。' },
                { title: '保存配置', text: '服务端在保存时同步校验。首次有效保存自动启用；已启用配置保存后供下一次 Launch 使用，停用配置保存后仍保持停用。' }
              ]
            },
            {
              type: 'table',
              title: '原生运行与跨域边界',
              columns: runtimeBoundaryColumns,
              rows: [
                { policy: 'Flow 原生页', purpose: 'iframe 内全部可见内容和交互', rule: 'LIST/FORM 都来自启动时固定的 ACTIVE 版本；内部兼容值 FLOW_PUBLISHED 不需要管理员配置，不能用字段参数覆盖原生页面。' },
                { policy: '映射用户', purpose: '页内字段、数据、按钮和请求权限', rule: '完全执行该 Flow 用户已有的菜单/动作权限、对象权限、字段权限、记录状态和 DataScope；Application、View、Grant 不放大这些权限。' },
                { policy: 'Capability', purpose: '入口与跨域 Bridge', rule: '只决定 LIST/CREATE/VIEW 是否可进入，以及宿主能接收或发送哪些 Bridge 消息；不决定原生按钮是否显示或可执行。' },
                { policy: 'Returnable', purpose: 'selection.changed / form.saved 可回传宿主的字段', rule: '只控制离开 iframe 的业务值，不隐藏原生列/字段，也不改变页内查询、编辑、保存或按钮行为。' },
                { policy: 'Context', purpose: '宿主后端提供的固定业务上下文', rule: '只能按已声明 Schema 与 Binding 注入固定筛选或表单值；不能传目标资源、运行 API、权限或任意查询表达式。' }
              ]
            },
            {
              type: 'paragraph',
              text: '宿主直接入口与 Capability 必须成对：LIST 使用 LIST_QUERY，CREATE 使用 RECORD_CREATE，VIEW 使用 RECORD_VIEW，selection.changed 还需要 SELECTION_RETURN。它们只守住直接入口与 Bridge；LIST/FORM 页内原生按钮和内部导航（包括自定义按钮）不要求额外 Capability，而是始终按映射 Flow 用户在 Flow 原系统中的权限结果显示和执行。'
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
                { title: '确认版本行为', text: 'Grant 不选择资源版本；每次新 Launch 自动使用目标最新 ACTIVE 发布版，已打开 Session 不漂移。' },
                { title: '设置 Capability Ceiling', text: 'Grant 能力上限必须等于或小于 View 能力，用于收窄入口和跨域 Bridge；它不增减 iframe 内 Flow 原生页面的按钮或 API 权限。' },
                { title: '设置保护阈值', text: '配置每用户最大活跃 Session、Session 最长秒数、最大并发、Launch/分钟、Runtime/分钟和可选到期时间。' },
                { title: '启用 Grant', text: '确认 Provider、Binding、Origin、能力和限额均正确后设为 ACTIVE。撤销用于永久终止，不作为临时停用。' }
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
                { field: 'entry', source: '后端白名单校验', rule: '只允许嵌入配置已开放的入口；VIEW 的 recordId 必须先做第三方业务授权。' },
                { field: 'context', source: '第三方后端业务权限', rule: '仅提交配置 Context Schema 允许的值，通常由租户、供应商或组织关系推导。' },
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
                '容器卸载、路由离开或 Session 过期时调用 destroy()；需要重开或切换入口时必须等待其 Promise 完成。',
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
                { event: 'ack', payload: 'command；顶层 requestId 关联命令', hostAction: '确认命令已完成；destroy 的 ACK 表示服务端 Session Logout 已完成，可以安全申请下一次 Launch。' },
                { event: 'initialized', payload: 'viewKey、surfaceType、capabilities', hostAction: '解除宿主加载态；按实际能力决定外围提示。' },
                { event: 'resize', payload: 'height', hostAction: 'auto 高度模式由 SDK 自动限制在配置的 min/max。' },
                { event: 'selection.changed', payload: 'selection[{ id, values }]', hostAction: '更新宿主选择；values 仅含配置允许回传的字段。' },
                { event: 'form.saved', payload: 'receiptId、record、clientMutationId', hostAction: '刷新宿主数据或提示成功；record.values 仅含 Returnable 字段。' },
                { event: 'close.requested', payload: 'reason', hostAction: 'Flow 原生表单的关闭/取消请求；立即 await widget.destroy()，确认 Session Logout 后再允许重新打开。' },
                { event: 'error', payload: 'errorCode、message、traceId、recoverable', hostAction: '展示可理解提示，用 traceId 联系 Flow 运维；不要展示 Token。' },
                { event: 'session.expired', payload: 'reason、relaunchRequired', hostAction: '先等待旧实例 destroy 完成，再向第三方后端申请新 Launch 并重新 mount。' }
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
              title: '区分已实现和预留事件',
              text: 'close.requested 已由直接 FORM 的原生关闭/取消按钮发送；navigation.request、action.started 和 action.completed 仍只在 SDK 类型中预留。LIST / CREATE / VIEW / BACK 导航继续在 iframe 内部完成。'
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
                { command: 'destroy', method: 'await widget.destroy()', purpose: '先完成服务端 Session Logout，收到关联 ACK 后再清理端口、iframe、计时器和事件监听。' }
              ]
            },
            {
              type: 'paragraph',
              text: '业务命令必须在 connected 后发送，否则会得到 FLOW_EMBED_NOT_CONNECTED；命令返回 requestId，随后以相同 requestId 收到 ack 或 error。改变用户、View、Context 或入口记录时必须重新 Launch。'
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '重开前必须等待 destroy',
              text: 'destroy() 是异步关闭协议。宿主的“重新打开”、路由切换和组件替换必须先 await widget.destroy()；Promise resolve 表示已收到关联 ACK，服务端 Logout 和活跃 Session 名额释放已经完成。若 Promise reject 或超时，不得继续申请或签发新 Launch，应保留错误提示并由运维按 traceId 检查 Session。pagehide/beforeunload 无法可靠等待异步请求，只能作为 best effort 兜底。'
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
                { title: '加载列表', text: 'LIST 入口开放后，iframe 直接挂载目标 Flow 原生列表，并按固定 Context、映射用户权限和 DataScope 查询。宿主不能传内部 SQL、数据源或任意筛选操作符。' },
                { title: '使用筛选与组件', text: '列、搜索区、操作符、渲染器、数据源、分页和自定义组件全部由启动时固定的 Flow 列表版本提供；Embed 不维护 Queryable、Visible 或组件白名单。' },
                { title: '选择记录', text: 'SELECTION_RETURN 生效时，选择变化触发 selection.changed；宿主只收到允许回传的 id 与 values。' },
                { title: '使用按钮与弹框', text: '工具栏、行按钮、日期/下拉面板、业务弹框及其 API 完全按映射 Flow 用户在原系统中的权限和状态运行；Embed Capability 不隐藏或新增按钮。' },
                { title: '打开详情', text: '映射用户在 Flow 中可见的列表按钮会按原生配置解析目标表单并在 iframe 内打开，不需要额外 Capability，也不需要在嵌入配置中绑定默认表单。返回列表时保留当前分页、筛选和选择状态。' }
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
                { title: '打开新建', text: '可由宿主使用 CREATE 直接入口打开，或由 Flow 原生 LIST 按钮打开。直接入口 Capability 只允许宿主进入；页内新建按钮、能否新建和保存完全由映射用户的 Flow 创建权限决定。' },
                { title: '填写字段', text: 'CREATE / VIEW 直接挂载固定 Flow 原生表单页面；Embed 不裁剪字段。原生请求携带短期 Embed Session，由服务端恢复映射用户后继续执行原字段权限、对象权限、DataScope、联动和 Context 强制值。' },
                { title: '操作原生控件', text: '日期/日期时间、Select/MultiSelect、Radio、Checkbox、Switch、富文本及自定义扩展直接运行 Flow 原组件和原数据源；Popper、日历、对话框、搜索、分页、快捷键及可访问性行为不由 Embed 重写。' },
                { title: '服务端校验', text: 'Flow 原生 API 执行 ACTIVE 表单约束、必填、类型、枚举、唯一性、实体权限和 DataScope；失败时保留表单并返回原生校验结果。' },
                { title: '使用原生操作栏', text: '按钮由 Flow 原生 form-actions/resolve 按映射用户、当前记录/流程状态和固定版本解析；名称、顺序、显隐、禁用原因和请求路径都与 Flow 原系统一致，不由 ACTION_EXECUTE 或 actionPolicy 改写。关闭会发出 close.requested，宿主 await widget.destroy()。' },
                { title: '幂等保存', text: '同一次提交使用稳定 Idempotency-Key；超时重试不会重复创建。save 与 saveAndStart 是不同幂等语义，同一 Key 不能互换。成功后返回 receiptId 并触发 form.saved。' },
                { title: '宿主刷新', text: '宿主用 form.saved 更新页面；若要读取完整记录，仍通过授权的数据接口或新 Launch，不依赖事件中未返回字段。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '当前不支持编辑',
              text: 'VIEW 入口始终只读。即使旧配置中出现 EDIT 或 RECORD_UPDATE，V1 配置校验与运行时也会拒绝；不要把“查看表单”误认为可修改记录。'
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
                { symptom: 'HTTP 400 / 413', check: '未知/null 字段、Context Schema、请求体大小', action: '修正参数或管理端配置；PAYLOAD_TOO_LARGE 不应原样重试。' },
                { symptom: 'OAuth 401', check: 'Client 状态、Client Secret、Token URL、embed.launch Scope', action: '在第三方后端修正凭据；不要把 Secret 发给浏览器排查。' },
                { symptom: 'Launch 403', check: 'Application、Grant、Origin、Provider、Binding、Flow 用户状态', action: '按 traceId 查审计，确认 external subject 与 Application 的精确 Binding。' },
                { symptom: 'Launch 429', check: 'Launch/IP 限流与 Retry-After', action: '按 Retry-After 退避；禁止立即循环重试。' },
                { symptom: 'iframe 被浏览器拒绝', check: '专用 Embed 域名、TLS、CSP frame-ancestors 与 parentOrigin', action: '确认实际宿主 Origin 与 Allowed Origin 字节级一致。' },
                { symptom: 'iframe 跳转登录', check: '是否误用了普通后台 URL 或普通 Request 客户端', action: '只使用 launch.embedUrl 和官方 SDK；Embed 不走 Flow 登录。' },
                { symptom: '握手 / Exchange 401 或 410', check: 'Launch TTL、是否已消费、launch/channel/Origin/Nonce 是否混配', action: '废弃旧 Launch，申请一组新的 launchId/launchCode/channelId。' },
                { symptom: '列表为空', check: '固定 Context、Flow DataScope、列表发布规则', action: '用同一 Flow 用户核对数据范围，不要先扩大 View 字段或权限。' },
                { symptom: '记录 404', check: '资源不存在或该 Flow 用户无权访问', action: '两种情况故意不区分；不要通过重试探测记录是否存在。' },
                { symptom: '按钮、字段或列表列缺失', check: '是否为同一 ACTIVE 版本、同一映射 Flow 用户、同一记录/流程状态，以及原生权限/DataScope 结果', action: '不要在 Embed 配置中补字段、列或按钮；LIST/FORM 都没有兼容白名单。先用同一用户打开 Flow 原生运行页对照。' },
                { symptom: '列表、日期/下拉、富文本或弹窗不同', check: '是否意外进入 Embed 旧投影渲染器，Network 是否走 Flow 原生列表/表单 API', action: '这是实现缺陷而非配置问题；LIST 与 FORM 都必须挂载原生组件树并调用原生 API。' },
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
                '嵌入配置问题：先停用 Grant 阻断新 Launch；修正并保存配置后再恢复。'
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
                'LIST 原生分页、筛选、渲染器、数据源和自定义组件均正确；selection.changed 只按 Returnable 回传宿主。',
                'CREATE 成功、校验失败、唯一性冲突、超时幂等重试与 form.saved 跨域返回边界均正确。',
                '在同一视口、同一 ACTIVE 版本、同一用户和同一数据下对照原生 Flow 与 iframe：列表列/搜索区、字段顺序/布局、日期与下拉 Popper、富文本、确认框、校验提示和按钮完全一致。',
                'LIST 与 FORM Network 都携带 Embed Session 调用 Flow 原生 API；宿主不能指定 entityCode、listKey、formId、Release 或 API URL。',
                '分别在 Flow 注册测试列表组件和测试字段组件后，不修改 Embed 代码即可在新 Launch 中显示，用作“未来组件零适配”架构门禁。',
                '使用两名权限不同的映射 Flow 用户验收同一页面：页内按钮、数据和请求结果应分别与两人在 Flow 原系统中的结果一致；增删 View/Grant 的 ACTION_EXECUTE 不得改变原生按钮。',
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
                '嵌入配置、Grant Ceiling、Context 和 Returnable 字段已经业务与安全评审；新 Launch 取目标最新 ACTIVE，已打开 Session 不漂移。',
                'View/Grant Capability 只开放必要入口与 Bridge；不能把 ACTION_EXECUTE 当作页内按钮权限，也不能用 Capability 绕过映射 Flow 用户权限。',
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
