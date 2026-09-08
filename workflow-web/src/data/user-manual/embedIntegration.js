const launchFieldColumns = [
  { key: 'field', label: '字段' },
  { key: 'owner', label: '取值方' },
  { key: 'rule', label: '要求' }
]

const eventColumns = [
  { key: 'event', label: '事件' },
  { key: 'handle', label: '嵌入方前端处理' }
]

const capabilityColumns = [
  { key: 'feature', label: '需要的功能' },
  { key: 'entry', label: '入口' },
  { key: 'capability', label: 'View / Grant Capability' }
]

const sdkExample = `import FlowEmbed from '@flow/embed-sdk'

let widget
let destroyPromise

export async function openFlow(
  container,
  entry = { mode: 'LIST' },
  formPresentation = 'seamless'
) {
  // 切换页面或重新打开前，必须先释放上一条 Flow Session。
  if (widget) await closeFlow()

  const channelId = crypto.randomUUID()
  const response = await fetch('/partner-api/flow-embed-launch', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ channelId, entry, formPresentation })
  })
  if (!response.ok) throw await response.json()

  const launch = await response.json()
  widget = FlowEmbed.mount({
    container,
    embedUrl: launch.embedUrl,
    launchId: launch.launchId,
    launchCode: launch.launchCode,
    channelId,
    targetOrigin: 'https://embed.flow.example.com',
    height: { mode: 'auto', min: 480, max: 1200 },
    onEvent(event) {
      if (event.type === 'form.saved') refreshHostData(event.payload.record)
      if (event.type === 'selection.changed') updateSelection(event.payload.selection)
      if (event.type === 'close.requested') {
        void closeFlow().catch(reportEmbedLifecycleFailure)
      }
      if (event.type === 'session.expired') {
        void closeFlow().then(showRelaunchAction).catch(reportEmbedLifecycleFailure)
      }
      if (event.type === 'error') showFlowError(event.payload)
    },
    onViolation(error) {
      reportProtocolViolation(error.errorCode)
    }
  })

  // SDK 已复制一次性 launchCode；宿主不再保留它。
  launch.launchCode = ''
}

export async function closeFlow() {
  if (destroyPromise) return destroyPromise
  if (!widget) return
  const current = widget
  const pending = current.destroy()
    .then(() => {
      if (widget === current) widget = undefined
    })
    .finally(() => {
      if (destroyPromise === pending) destroyPromise = undefined
    })
  destroyPromise = pending
  return pending
}

window.addEventListener('pagehide', () => {
  // 页面卸载不能可靠等待异步 Logout，只能做 best effort 清理。
  void closeFlow().catch(() => {})
}, { once: true })`

const tokenExample = `FLOW_API_BASE='https://api.flow.example.com'
CLIENT_ID='<Integration Application Client ID>'
CLIENT_SECRET='<仅保存在嵌入方后端的 Client Secret>'

curl --request POST "$FLOW_API_BASE/oauth2/token" \\
  --user "$CLIENT_ID:$CLIENT_SECRET" \\
  --header 'Content-Type: application/x-www-form-urlencoded' \\
  --data-urlencode 'grant_type=client_credentials' \\
  --data-urlencode 'scope=embed.launch'`

const assertionExample = `// JWT Header
{
  "alg": "RS256",
  "kid": "partner-signing-key-2026-09",
  "typ": "JWT"
}

// JWT Claims：由嵌入方后端签名，建议 60 秒内过期
{
  "iss": "https://id.partner.example",
  "sub": "employee-000123",
  "aud": "flow-embed-launch",
  "iat": 1788278400,
  "exp": 1788278460,
  "jti": "每次签发都不同"
}`

const backendExample = `@RestController
@RequiredArgsConstructor
class FlowEmbedController {
  private static final String VIEW_KEY = "supplier-work-orders";
  private static final String PARENT_ORIGIN = "https://portal.partner.example";
  private static final String EMBED_ORIGIN = "https://embed.flow.example.com";

  private final RestClient flowClient;
  private final FlowMachineTokenService machineTokenService;
  private final PartnerAssertionService assertionService;

  /** 为当前已登录的嵌入方用户创建一次性 Flow Launch。 */
  @PostMapping("/partner-api/flow-embed-launch")
  ResponseEntity<EmbedLaunch> createFlowEmbedLaunch(
      @AuthenticationPrincipal PartnerUser currentUser,
      @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
      @Valid @RequestBody HostLaunchRequest request) {
    var partnerUser = requireSignedInUser(currentUser);
    var channelId = validateUuid(request.channelId());
    var flowTraceId = normalizeTraceId(traceId);

    // 展示方式由客户端选择，但代理层只接受公开枚举；缺省时使用无缝铺满。
    var requestedPresentation = request.formPresentation();
    var formPresentation = requestedPresentation == null
        ? "seamless"
        : switch (requestedPresentation) {
          case "seamless", "dialog" -> requestedPresentation;
          default -> throw badRequest("unsupported formPresentation");
        };

    // LIST/CREATE 必须完全省略 recordId；VIEW 还要通过嵌入方业务授权。
    Map<String, Object> entry = switch (request.entry().mode()) {
      case "LIST", "CREATE" -> {
        if (request.entry().recordId() != null) {
          throw badRequest("LIST/CREATE must omit recordId");
        }
        yield Map.of("mode", request.entry().mode());
      }
      case "VIEW" -> {
        var recordId = requireRecordId(request.entry().recordId(), 64);
        requireRecordAccess(partnerUser, recordId);
        yield Map.of("mode", "VIEW", "recordId", recordId);
      }
      default -> throw badRequest("unsupported entry mode");
    };

    var accessToken = machineTokenService.getToken("embed.launch");
    var assertion = assertionService.signShortLivedJwt(
        partnerUser.immutableSubject(),
        "https://id.partner.example",
        "flow-embed-launch",
        Duration.ofSeconds(60));

    var launchRequest = new EmbedLaunchRequest(
        VIEW_KEY,
        PARENT_ORIGIN,
        channelId,
        new SignedJwtSubject("SIGNED_JWT", assertion),
        entry,
        buildAuthorizedContext(partnerUser),
        new LaunchUi("zh-CN", "light", formPresentation));

    var flowResponse = flowClient.post()
        .uri("/api/open/v1/embed-launches")
        .headers(headers -> headers.setBearerAuth(accessToken))
        .header("X-Trace-Id", flowTraceId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(launchRequest)
        .retrieve()
        .onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
          throw FlowLaunchException.from(response);
        })
        .toEntity(EmbedLaunchEnvelope.class);

    var envelope = flowResponse.getBody();
    if (flowResponse.getStatusCode() != HttpStatus.CREATED
        || envelope == null
        || envelope.code() != 201) {
      throw new IllegalStateException("invalid Flow Launch response");
    }

    // 防止错误环境或错误 Grant 返回的目标被带入当前宿主页面。
    var launch = validateLaunchResponse(
        envelope.data(), VIEW_KEY, "LIST", EMBED_ORIGIN);
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(launch);
  }
}`

const deploymentExample = `WORKFLOW_OPEN_API_ENABLED=true
WORKFLOW_OPEN_API_ISSUER=https://embed.flow.example.com
WORKFLOW_OPEN_API_AUDIENCE=flow-open-api
WORKFLOW_OPEN_API_KEY_ID=<Open API RSA key id>
WORKFLOW_OPEN_API_PRIVATE_KEY_LOCATION=file:<RSA 私钥路径>
WORKFLOW_OPEN_API_PUBLIC_KEY_LOCATION=file:<RSA 公钥路径>

WORKFLOW_EMBED_ENABLED=true
WORKFLOW_EMBED_PUBLIC_BASE_URL=https://embed.flow.example.com
WORKFLOW_EMBED_CONTEXT_KEY_BASE64=<独立的 32-byte AES key>
WORKFLOW_EMBED_CONTEXT_KEY_VERSION=<版本号>
WORKFLOW_EMBED_HMAC_KEY_BASE64=<独立的 HMAC key>
WORKFLOW_EMBED_HMAC_KEY_VERSION=<版本号>

# 将 Embed Origin 合并到现有 CORS 列表；这里不是宿主 Origin。
CORS_ALLOWED_ORIGINS=https://admin.flow.example.com,https://embed.flow.example.com`

export default {
  eyebrow: 'USER MANUAL · EMBED INTEGRATION',
  title: '嵌入集成用户手册',
  subtitle: '面向接入开发人员，只说明嵌入方前端、嵌入方后端和 Flow 系统各自需要完成的工作。',
  version: 'Flow Embed V1',
  updatedAt: '2026-09-02',
  intro: [
    {
      title: '实施顺序',
      type: 'info',
      text: '先完成 Flow 系统配置，再实现嵌入方后端 Launch 接口，最后由嵌入方前端接入 SDK。'
    },
    {
      title: '安全边界',
      type: 'warning',
      text: 'Client Secret、机器 Token、人员 JWT 私钥和人员断言只能存在于嵌入方后端；前端只接收一次性 Launch 结果。'
    }
  ],
  sections: [
    {
      id: 'embed-frontend',
      index: '01',
      title: '嵌入方前端需要做什么',
      summary: '调用自己的后端创建 Launch，再用固定版本 SDK 挂载和销毁 iframe。',
      topics: [
        {
          id: 'embed-frontend-mount',
          title: '创建并挂载',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '安装 SDK', text: '从 Flow 提供的私有制品库安装固定版本 @flow/embed-sdk；不要复制 SDK 源码，也不要手写 iframe 握手。' },
                { title: '生成 channelId', text: '每次挂载使用 crypto.randomUUID() 生成新的 channelId。' },
                { title: '请求自己的后端', text: '把 channelId、受控 entry 意图和可选 formPresentation 提交给嵌入方后端。LIST、CREATE 不传 recordId；VIEW 必须传 recordId。formPresentation 缺省为 seamless，需要保留模态效果时传 dialog；它也控制从嵌入列表内后续打开的表单。' },
                { title: '挂载 SDK', text: '把同一次 Launch 的 embedUrl、launchId、launchCode、channelId 传给 FlowEmbed.mount，并把 targetOrigin 固定为 Flow 提供的 Embed Origin。' }
              ]
            },
            {
              type: 'code',
              title: '前端示例',
              language: 'javascript',
              code: sdkExample
            }
          ]
        },
        {
          id: 'embed-frontend-lifecycle',
          title: '处理事件与生命周期',
          blocks: [
            {
              type: 'table',
              columns: eventColumns,
              rows: [
                { event: 'connected', handle: '仅表示安全通道握手完成；Flow 可能仍在兑换凭证或加载首屏。' },
                { event: 'initialized', handle: '结束加载态并启用宿主刷新等操作；可读取 viewKey、surfaceType 和 capabilities。' },
                { event: 'selection.changed', handle: '更新宿主选择结果；payload 只包含 Flow 配置允许回传的字段。' },
                { event: 'form.saved', handle: '刷新宿主数据；完整数据仍以后端权威数据为准。' },
                { event: 'close.requested', handle: '调用并等待 widget.destroy()，由宿主关闭嵌入区域。' },
                { event: 'session.expired', handle: '等待旧实例 destroy() 成功，再重新向嵌入方后端申请 Launch 并挂载；失败时保持回收未确认。' },
                { event: 'error', handle: '展示通用提示，并把 errorCode、traceId 交给 Flow 运维排查。' }
              ]
            },
            {
              type: 'checklist',
              items: [
                '重新打开、路由离开或组件卸载前，必须 await widget.destroy()；失败或超时时保留回收未确认状态并禁用重新打开，等待服务端超时或管理员确认回收。',
                'embedUrl 必须保持 Flow 返回值：使用 HTTPS，Origin 与 targetOrigin 完全相等，路径为 /embed/v1/launches/{launchId}，且不带 query 或 fragment。',
                '不要把 launchCode 写入 URL、localStorage、sessionStorage、Cookie、埋点或错误日志。',
                '不要直接创建 iframe，也不要使用 postMessage("*")；SDK 会校验 Origin、Window、Nonce 和 MessageChannel。',
                '不要从浏览器直接调用 /oauth2/token 或 /api/open/v1/embed-launches。'
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'embed-backend',
      index: '02',
      title: '嵌入方后端需要做什么',
      summary: '识别当前登录用户，取得机器 Token，签发人员断言并向 Flow 创建一次性 Launch。',
      topics: [
        {
          id: 'embed-backend-flow',
          title: '实现 Launch 接口',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '校验宿主请求', text: '从登录会话识别当前用户；校验 channelId、entry 和 formPresentation。展示方式只接受精确的小写 seamless/dialog，缺失或 null 默认 seamless；viewKey、parentOrigin、Context 及允许的入口必须由后端白名单确定。' },
                { title: '取得机器 Token', text: '用 Integration Application 的 Client ID / Client Secret 调用 /oauth2/token，Scope 使用 embed.launch。Token 可按 expires_in 在后端内存短期缓存。' },
                { title: '签发人员 JWT', text: '以稳定且不可回收的 external subject 作为 sub，使用与 Flow Provider 匹配的 iss、aud、alg 和 kid；设置短期 exp，并为每次断言生成唯一 jti。' },
                { title: '创建 Launch', text: '携带机器 Token 调用 POST /api/open/v1/embed-launches，校验响应目标和 Embed Origin 后返回当前浏览器，并设置 Cache-Control: no-store。' }
              ]
            },
            {
              type: 'code',
              title: '取得机器 Token',
              language: 'bash',
              code: tokenExample
            },
            {
              type: 'code',
              title: '人员 JWT 示例',
              language: 'json',
              code: assertionExample
            }
          ]
        },
        {
          id: 'embed-backend-launch',
          title: '组装 Launch 请求',
          blocks: [
            {
              type: 'table',
              columns: launchFieldColumns,
              rows: [
                { field: 'viewKey', owner: '嵌入方后端固定配置', rule: '不能接受浏览器任意指定；必须已配置对应 Grant。' },
                { field: 'parentOrigin', owner: '嵌入方后端固定配置', rule: '必须与真实宿主 Origin 和 Grant Allowed Origin 完全一致。' },
                { field: 'channelId', owner: '嵌入方前端生成', rule: '后端只校验格式并原样提交；必须与本次 SDK mount 使用同一值。' },
                { field: 'subject', owner: '嵌入方后端签发', rule: '默认使用 SIGNED_JWT；不得接受浏览器提交 Flow User ID。' },
                { field: 'entry', owner: '嵌入方后端白名单', rule: 'LIST/CREATE 不含 recordId；VIEW 的 recordId 必须经过业务授权。' },
                { field: 'context', owner: '嵌入方后端推导', rule: '只能提交 Flow 配置的 Context Schema 允许的字段。' },
                { field: 'ui', owner: '嵌入方后端校验', rule: 'locale、theme 和 formPresentation 必须按白名单校验；formPresentation 缺省为 seamless，可传 dialog 保留模态展示，并作用于列表内打开的表单；不影响权限。' }
              ]
            },
            {
              type: 'code',
              title: '嵌入方后端示例（Spring Boot 3 / Java 17）',
              language: 'java',
              code: backendExample
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '后端不得记录敏感值',
              text: '日志只记录 errorCode、traceId 和必要业务标识；不要记录 Client Secret、Access Token、人员断言、launchCode 或完整 external subject。'
            },
            {
              type: 'checklist',
              items: [
                '收到 201 后，校验 view.key、view.surfaceType 与后端白名单目标一致。',
                '校验 embedUrl 使用 HTTPS、Origin 等于后端固定 Embed Origin、路径绑定 launchId 且没有 query/fragment，再把 Launch 返回浏览器。',
                'HostLaunchRequest 必须开启 Bean Validation，并拒绝未知 JSON 字段；SIGNED_JWT subject 只序列化 type 和 assertion。',
                'RestClient 的 baseUrl、超时和重定向策略必须由后端固定配置；Flow URL 不能来自浏览器。',
                '对 401 重新获取机器 Token；对 403 检查 Grant、Provider、Binding 与 Flow 用户状态；对 429 按 Retry-After 退避。'
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'embed-platform-config',
      index: '03',
      title: 'Flow 系统需要怎么配置',
      summary: '启用独立 Embed 运行域名，并按顺序配置 Application、Provider、Binding、嵌入配置和 Grant。',
      topics: [
        {
          id: 'embed-prerequisites-environment',
          title: '部署配置',
          blocks: [
            {
              type: 'checklist',
              items: [
                '为 Embed Runtime 配置独立、受信任的 HTTPS Origin，例如 https://embed.flow.example.com；不要与管理后台 Origin 或宿主 Origin 混用。',
                '启用 Open API 和 Embed Runtime，分别配置 Open API RSA、Embed Context AES、Embed HMAC 三组独立密钥。',
                '执行 workflow-web 的 npm run build:embed，并把生成的 Embed 静态资源发布到专用 VHost。',
                '专用 VHost 只暴露 Embed Entry、静态资源、/api/embed/v1/** 控制面及运行时所需的受控原生 API。',
                'CORS 放行 Embed Origin；宿主 Origin 通过 Grant Allowed Origins 精确授权。'
              ]
            },
            {
              type: 'code',
              title: '关键环境变量示例',
              language: 'bash',
              code: deploymentExample
            }
          ]
        },
        {
          id: 'embed-platform-order',
          title: '管理端配置顺序',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '1. Integration Application', text: '在“系统管理 → 开放集成”创建嵌入方专用 Application，只授予 embed.launch；交付 Client ID 和一次性展示的 Client Secret，并记录内部 Application ID。测试、生产分别创建。' },
                { title: '2. Identity Provider', text: '在“系统管理 → 嵌入集成 → Identity Provider 与 Binding”创建 SIGNED_JWT Provider；配置 Subject Namespace、精确 Issuer、Audience=flow-embed-launch、允许算法和公开 JWKS。推荐 RS256 或 ES256，Flow 只保存公钥。' },
                { title: '3. Identity Binding', text: '按 Application + Provider + external subject 精确绑定到一个已启用 Flow 用户。该用户的角色、字段权限和 DataScope 决定 iframe 内实际可见与可操作内容。' },
                { title: '4. 嵌入配置', text: '在“配置与 Grant”创建稳定 viewKey，选择 LIST 或 FORM，再选择一个已有 ACTIVE 版本的实体列表或表单；配置入口、Returnable 和必要 Capability。有 Context 时定义 Schema，并用 FIXED_FILTER 或 FORCED_FORM_VALUE 绑定。首次有效保存会自动启用。' },
                { title: '5. Application Grant', text: '关联内部 Application ID、Provider 和嵌入配置；填写不含路径的精确 HTTPS Allowed Origin、Capability Ceiling、Session/并发/限流阈值。SIGNED_JWT 必须关闭 Trusted Subject Assertion，确认后将 Grant 设为 ACTIVE。' }
              ]
            },
            {
              type: 'table',
              title: '入口与 Capability 对应关系',
              columns: capabilityColumns,
              rows: [
                { feature: '打开列表', entry: 'LIST', capability: 'LIST_QUERY' },
                { feature: '直接新建', entry: 'CREATE', capability: 'RECORD_CREATE' },
                { feature: '直接查看记录', entry: 'VIEW + recordId', capability: 'RECORD_VIEW' },
                { feature: '向宿主回传列表选择', entry: 'LIST', capability: 'SELECTION_RETURN，并配置 Returnable 字段' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: 'V1 不支持编辑入口',
              text: 'V1 只支持 LIST、CREATE 和只读 VIEW；不要配置 EDIT 或 RECORD_UPDATE。列表、表单中的原生按钮仍按映射 Flow 用户权限执行。'
            },
            {
              type: 'callout',
              tone: 'info',
              title: '页面内容仍由 Flow 权限决定',
              text: 'Capability 只控制直接入口和跨 iframe Bridge，不会给映射用户增加权限；人员 JWT 中的角色或权限 Claim 同样不会授予 Flow 权限。列表列、表单字段、按钮、数据和 DataScope 均来自该 Flow 用户及目标最新 ACTIVE 版本；已打开 Session 固定启动时版本。'
            }
          ]
        },
        {
          id: 'embed-go-live-checklist',
          title: '联调确认',
          blocks: [
            {
              type: 'checklist',
              items: [
                '使用已绑定且启用的用户验证 LIST、CREATE 或 VIEW 正常打开，并与该用户在 Flow 原生页面中的权限和 DataScope 一致。',
                '验证未绑定用户、停用 Binding、停用 Grant、错误 Origin 和错误 recordId 均被拒绝。',
                '验证同一 launchCode 二次使用、过期使用或混用 channelId 均失败。',
                '验证 selection.changed 和 form.saved 只回传 Returnable 允许的字段。',
                '验证关闭、重新打开和 session.expired 场景会先销毁旧 Session，再申请新 Launch。',
                '通过 traceId 能关联后端和 Flow 日志，且日志中没有 Secret、Token、人员断言或 launchCode。'
              ]
            }
          ]
        }
      ]
    }
  ]
}
