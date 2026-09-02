# Flow Embed 本地第三方系统演示

这个示例用于直观看到“别的系统嵌入 Flow 列表或表单”的真实效果，不是 Mock 页面：

- `https://localhost:3443` 模拟第三方业务系统，提供宿主页和同源后端接口；
- `https://localhost:8443` 是隔离的 Flow Embed Origin，只开放 Entry、`embed-assets`、
  Flow 上传文件只读路径、Embed 控制面 API，以及携带短期 opaque Session 的 Flow 原生
  数据面 API；
- Flow Server 继续使用本机 `http://127.0.0.1:8080`；
- 第三方 Node 后端使用 Client Credentials 获取机器 Token，签发人员 JWT，再创建一次性
  Launch；
- 浏览器使用仓库里的 `@flow/embed-sdk` 挂载 iframe、完成 MessageChannel 握手并展示事件。

示例只使用 Node 内置模块，不需要安装 npm 依赖，不使用 Docker，也不会修改 macOS
Keychain。Client Secret、机器 Token、人员 JWT、私钥和 Launch Code 都不会写入日志或前端
Storage。

## 一、默认的 LIST + FORM 本地示例

Demo 默认提供两个受控目标：`req-list / ZDWREQ / list001` 列表和
`zdwreq-form-demo` 表单。内部 ID 都必须从当前环境创建后复制，下面不会提供一个看似可用、
实际不存在的 Grant ID：

| 对象 | 示例值 |
| --- | --- |
| Application | 当前环境创建的“嵌入联调示例”；Scope 仅 `embed.launch` |
| Provider | 当前环境创建的 SIGNED_JWT Provider；Issuer `https://id.embed-demo.local` |
| 外部人员 | `sub=demo-lisi-001`，绑定到当前环境中已启用且具备目标权限的 Flow 用户 |
| 列表嵌入配置 | `req-list` / `LIST`；目标区域只选择实体 `ZDWREQ` 和列表 `list001` |
| 表单嵌入配置 | `zdwreq-form-demo` / `FORM`；目标区域选择需要演示的 ACTIVE 表单 |
| Grant | 当前环境分别为两个配置创建；Allowed Origin 均为 `https://localhost:3443` |
| 宿主 Origin | `https://localhost:3443` |
| Embed Origin | `https://localhost:8443` |

示例 iframe 直接运行 Flow 原生 `EntityDataList` 页面，而不是根据 Embed 参数重画列、字段或
按钮。列表的列、筛选、渲染器、数据源、工具栏、行按钮，以及按钮打开的表单、日期、下拉、
富文本和弹框都来自 Flow 原生运行时。嵌入配置第一次有效保存即启用，不需要发布；每个新
Launch 解析目标最新 ACTIVE，已有 Session 固定启动时版本。换句话说，平台内建控件来自目标最新 ACTIVE 发布版，
而不是 Embed 自己维护的兼容实现。

> ？后续新增组件要改这个示例或 Embed 吗？不需要。新组件只要进入 Flow 原生 Registry、
> 激活并接好自身权限/API，新的 Launch 会自动使用它；Embed 不维护组件或 datasource 白名单。

默认从以下已忽略文件读取秘密，不复制、不改写文件内容：

```text
.codex-artifacts/embed-demo/oauth-credentials.json
.codex-artifacts/embed-demo/assertion-private.pem
```

## 二、Flow Server 前置条件

真实挂载要求 Open API 和 Embed Runtime 都已启用。Flow Server 启动环境至少要满足：

```bash
WORKFLOW_OPEN_API_ENABLED=true
WORKFLOW_OPEN_API_ISSUER=https://localhost:8443
WORKFLOW_OPEN_API_AUDIENCE=flow-open-api
WORKFLOW_OPEN_API_KEY_ID=<当前 Open API RSA key id>
WORKFLOW_OPEN_API_PRIVATE_KEY_LOCATION=file:<Open API RSA 私钥绝对路径>
WORKFLOW_OPEN_API_PUBLIC_KEY_LOCATION=file:<Open API RSA 公钥绝对路径>

WORKFLOW_EMBED_ENABLED=true
WORKFLOW_EMBED_PUBLIC_BASE_URL=https://localhost:8443
WORKFLOW_EMBED_CONTEXT_KEY_BASE64=<独立的 32-byte AES key，Base64 编码>
WORKFLOW_EMBED_CONTEXT_KEY_VERSION=embed-context-demo-v1
WORKFLOW_EMBED_HMAC_KEY_BASE64=<现有 Embed HMAC key，Base64 编码>
WORKFLOW_EMBED_HMAC_KEY_VERSION=embed-hmac-demo-v1

# 8443 是 iframe 内 Flow 原生页面的请求 Origin；3443 只配置在 View Grant 中。
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000,https://localhost:8443
```

这些值应放在本地已忽略的 `.env` 或进程环境里，不要提交。四类材料用途不同，不能混用：
Open API RSA、人员断言 RSA、Embed Context AES 和 Embed Subject HMAC。

然后在 Flow 管理端确认：

1. Application、Provider、Binding、Grant 均为 `ACTIVE`；`req-list` 和 `zdwreq-form-demo`
   均已第一次有效保存，目标列表与表单存在 ACTIVE 版本；
2. 当前环境为两个嵌入配置创建的 Grant，其 Origin 均精确等于 `https://localhost:3443`；
3. 部署和密钥就绪后，将该 Grant 从 `DISABLED` 改为 `ACTIVE`；
4. 重启 Flow Server，使上面的启用开关和密钥配置生效。

不要把 Embed Origin 改成 `http://localhost:8080`，也不要把 3443 和 8443 合并为同一个
Origin。Flow Server 可以继续走本机 HTTP；浏览器边界必须通过两个 HTTPS Origin 展示。

## 三、准备并启动

先构建独立 Embed 资源：

```bash
cd workflow-web
npm run build:embed
cd ../examples/embed-local-demo
```

准备自签名 localhost 证书。证书和私钥只写到已忽略的 `.runtime/`，不会导入 Keychain：

```bash
npm run setup
```

检查 SDK、构建产物、OAuth 凭据文件、人员断言私钥、JWT 签名和文件权限；输出只包含非敏感
状态：

```bash
npm run check
```

启动同时支持 LIST 与 FORM 的两个 HTTPS 服务：

```bash
npm start
```

打开 [https://localhost:3443](https://localhost:3443)，先选择“需求列表”或“需求表单”，再选择
该目标允许的入口并点击“创建 Launch 并打开”。页面顶部和导航属于模拟第三方系统，白色运行区
来自独立的 Flow Embed Origin。

“需求列表”只提供 LIST；“需求表单”提供 CREATE 和 VIEW，其中 VIEW 才需要填写 Flow
`recordId`。切换目标重新打开时，Demo 会先等待旧 Session 安全注销，再为所选目标创建新
Launch。浏览器只提交 `targetKey=list/form`，真实 `viewKey` 由 Demo 后端白名单映射。

本地证书未加入系统信任库时，浏览器第一次会显示证书警告。只在本地开发中确认该
localhost 证书即可。如果 iframe 因 8443 的证书尚未确认而空白，可单独打开
[https://localhost:8443/embed-assets/embed-main.css](https://localhost:8443/embed-assets/embed-main.css)，
确认后返回 3443 重新创建 Launch。不要用关闭 TLS 校验的环境变量规避证书验证。

## 四、实际对接流程

1. 浏览器请求第三方同源接口 `POST https://localhost:3443/partner-api/embed-launch`，只传
   受控 `targetKey`、`mode`、可选 `recordId` 和主题；
2. 3443 Node 后端从本地安全文件读取 OAuth 凭据，调用
   `POST http://127.0.0.1:8080/oauth2/token`，Scope 固定为 `embed.launch`；
3. Node 后端用人员断言私钥签发 60 秒 RS256 JWT，固定
   `iss/aud/sub/kid`，每次生成新的 `jti`；
4. Node 后端把 `list/form` 映射为环境变量中的固定 View Key，再调用
   `POST /api/open/v1/embed-launches`；`parentOrigin` 固定为 3443，Context 固定为空对象；
5. 浏览器拿到一次性 Launch 后调用 `FlowEmbed.mount()`；SDK 创建 8443 iframe，通过
   `ready/init/init.ack` 严格握手，把 Launch Code 只交给对应 iframe；
6. iframe 通过 8443 同源代理兑换短期 Session：Exchange、Bootstrap、LIST 与 Session 生命周期
   调用 `/api/embed/v1/**`，字段、按钮、数据源、文件和流程交互调用同一套 Flow 原生
   `/api/**`；两类请求都携带不透明 Embed Bearer 和 `X-Flow-Embed-Protocol: 1`。服务端先建立
   mapped Flow 用户身份，普通数据面继续经过原有 EndpointAuthorization、对象权限和 DataScope；
   携带历史 Release/目标坐标的端点再叠加签名绑定校验。中央 Embed 框架不维护组件、URL 或事件码
   清单，所以后续新增组件复用 Flow 原生 API 时不需要修改 Embed；
7. `initialized`、`form.saved`、`close.requested`、`error`、`session.expired` 等经过 SDK 校验后显示在宿主事件
   日志中；
8. Flow 已发布表单的“关闭/取消”会发出 `close.requested`；宿主收到后与手工关闭、
   重新打开共用同一条 `await widget.destroy()` 链，等 iframe 完成服务端 Session
   Logout 并返回 ACK 后才移除容器。注销失败时不会继续创建新 Launch，也不会通过提高
   Grant 的活动会话上限掩盖问题。

浏览器无法覆盖 `viewKey`、人员、`parentOrigin`、`context` 或 `channelId`，也不能提交目标
白名单以外的 `targetKey`。CREATE/LIST 必须完全省略 `recordId`；VIEW 必须携带第三方后端已
授权的合法 `recordId`。

## 五、可选配置

可通过环境变量覆盖本地默认值：

| 环境变量 | 默认值 | 用途 |
| --- | --- | --- |
| `FLOW_DEMO_FLOW_BASE_URL` | `http://127.0.0.1:8080` | Flow Server Origin |
| `FLOW_DEMO_HOST_ORIGIN` | `https://localhost:3443` | 第三方宿主精确 Origin |
| `FLOW_DEMO_EMBED_ORIGIN` | `https://localhost:8443` | 独立 Embed Origin |
| `FLOW_DEMO_LIST_VIEW_KEY` | `req-list` | LIST 目标固定 View Key |
| `FLOW_DEMO_FORM_VIEW_KEY` | `zdwreq-form-demo` | FORM 目标固定 View Key |
| `FLOW_DEMO_VIEW_KEY` | 空 | 兼容旧单目标启动；设置后切换为单目标模式 |
| `FLOW_DEMO_ALLOWED_ENTRY_MODES` | 空 | 与旧 `FLOW_DEMO_VIEW_KEY` 配套，兼容旧单目标入口模式 |
| `FLOW_DEMO_OAUTH_CREDENTIALS_FILE` | `.codex-artifacts/embed-demo/oauth-credentials.json` | OAuth 凭据 JSON |
| `FLOW_DEMO_ASSERTION_PRIVATE_KEY_FILE` | `.codex-artifacts/embed-demo/assertion-private.pem` | 人员断言 RSA 私钥 |
| `FLOW_DEMO_ASSERTION_ISSUER` | `https://id.embed-demo.local` | Provider issuer |
| `FLOW_DEMO_ASSERTION_AUDIENCE` | `flow-embed-launch` | Provider audience |
| `FLOW_DEMO_ASSERTION_SUBJECT` | `demo-lisi-001` | Binding external subject |
| `FLOW_DEMO_ASSERTION_KEY_ID` | `embed-demo-rs256-20260828` | STATIC_JWK_SET 中的 kid |
| `FLOW_DEMO_TLS_CERT_FILE` / `FLOW_DEMO_TLS_KEY_FILE` | `.runtime/localhost-*` | 自带证书时成对配置 |
| `FLOW_DEMO_FLOW_CA_FILE` | 空 | Flow Server 自身使用 HTTPS 时提供可信 CA |

也可以用 `FLOW_DEMO_CLIENT_ID` 与 `FLOW_DEMO_CLIENT_SECRET` 从进程环境注入凭据；不要把值写入
shell 历史、命令行参数、源码或截图。私钥和凭据文件必须是 `0600` 权限。

## 六、常见问题

| 现象 | 检查项 |
| --- | --- |
| OAuth 返回 404 | `WORKFLOW_OPEN_API_ENABLED=true` 是否已随 Flow Server 重启生效 |
| Launch 返回 403 | Application、Provider、Binding、View、Grant 是否启用；Grant Origin 是否精确为 3443 |
| `FLOW_DEMO_EMBED_ORIGIN_MISMATCH` | `WORKFLOW_EMBED_PUBLIC_BASE_URL` 是否精确为 `https://localhost:8443` |
| JWT/身份校验失败 | issuer、audience、kid、STATIC_JWK_SET、公私钥与 `demo-lisi-001` Binding 是否一致；本机时间是否准确 |
| iframe Entry 404 | Launch 是否已过期、消费、撤销或被重复使用；重新创建 Launch |
| iframe 空白或证书错误 | 分别确认 3443 与 8443 的 localhost 自签名证书后重试 |
| 重新打开提示活动 Session 达上限 | 确认宿主使用当前 SDK 并且在新 Launch 前 `await widget.destroy()`；不要直接删除 iframe |
| LIST 按钮或数据与 Flow 不同 | 确认比较的是同一映射用户、同一新 Launch、目标最新 ACTIVE、相同记录状态和 DataScope；不要通过 Capability、字段投影或组件白名单修补 |
| 提示缺少 `embed-main.js/css` | 在 `workflow-web` 执行 `npm run build:embed` |
| `chmod 600` 提示 | 收紧 OAuth 凭据、人员断言私钥或 TLS 私钥权限后重试 |

## 七、验证

示例自身契约测试不访问数据库或真实 Flow 服务：

```bash
cd examples/embed-local-demo
npm test
npm run check
```

`npm test` 验证双 Origin、LIST/FORM 目标白名单、封闭的浏览器启动参数、入口形状、RS256 JWT
签名及敏感信息不落浏览器 Storage/日志。`npm run check` 再验证当前机器上的真实资源、秘密
文件权限和密钥可用性。
