# 第三方 Embed 后端接入 Demo

本包 `com.workflow.biz.project.embed.demo` 演示**第三方将 Flow 嵌入自己的页面时，其后端需要实现的逻辑**。
参考实现使用 Java 17 / Spring Boot 3，仅通过 Flow 公开 HTTP API 通信，不调用 Flow 内部服务或数据库。

所有示例 Bean 仅在 `partner-embed-demo` Profile 下注册，默认不启用。
本包没有独立 Maven 模块或启动类；代码随 `biz-project` 编译，供第三方复制使用。
不要在 Flow 正常运行环境启用示例登录配置。

## 接入链路与代码入口

```text
第三方前端：已登录的宿主会话 + SDK channelId
  -> POST /partner-api/flow-embed-launch
     -> 从认证上下文识别用户，校验入口及记录权限
     -> Client Credentials 调用 Flow /oauth2/token，后端内存缓存机器 Token
     -> 宿主私钥签发 60 秒 RS256 人员 JWT，每次生成唯一 jti
     -> Bearer Token 调用 Flow /api/open/v1/embed-launches
     -> 校验 View、Embed Origin、Launch 路径和有效期
  <- 201 + Cache-Control: no-store + 一次性 Launch
第三方前端：使用 @flow/embed-sdk 挂载 iframe
```

| 类 | 第三方后端职责 |
| --- | --- |
| `FlowEmbedController` | 登录态 Launch 接口、CSRF Token 接口、公开 JWKS 接口 |
| `FlowEmbedService` | 固定目标和 Context、入口及记录授权、机器 401 的一次重试、Launch 返回校验 |
| `FlowMachineTokenService` | Client Credentials、Token 缓存、并发刷新与精确失效 |
| `PartnerAssertionService` | 读取 RSA 私钥、签发人员断言、只导出公钥 |
| `EmbedDemoProperties` | 后端授权配置、HTTPS Origin 校验及凭据脱敏 |
| `EmbedDemoSecurity` | 示例单用户登录，保留 CSRF，支持表单登录和 Basic 联调 |
| `EmbedDemoExceptionHandler` | 脱敏错误响应；保留 traceId、429 的 Retry-After |

## Flow 侧前置配置

Flow 需启用 Open API、Embed Runtime，并部署独立的 HTTPS Embed Origin 及静态资源。
在管理端依次准备：

1. **Integration Application**：取得第三方专用 Client ID / Client Secret。
2. **Identity Provider**：类型 `SIGNED_JWT`，Issuer 与宿主配置完全一致，Audience 包含
   `flow-embed-launch`，算法为 `RS256`，配置独立 Subject Namespace 和宿主公开 JWKS。
3. **Identity Binding**：将 Application + Provider + 宿主不可回收的 external subject 精确绑定到启用的 Flow 用户。
4. **嵌入配置**：创建稳定 viewKey，选择 `LIST` 或 `FORM`、已发布目标、入口、Capability 和 Returnable。
5. **Application Grant**：关联上述配置，Allowed Origin 精确匹配第三方 HTTPS Origin，设为 ACTIVE；
   `SIGNED_JWT` 接入关闭 Trusted Subject Assertion。

Demo 不自动创建这些配置。映射用户的角色、权限和 DataScope 决定 iframe 内可见及可操作的内容。
公开协议在仓库 `docs/api/embed-v1.yaml`；完整 HTTPS 宿主页及 iframe 部署示例在
`workflow-frontend/examples/embed-local-demo/README.md`。

## 复制到第三方应用并启动

1. 复制本包 Java 源码，按第三方工程需要调整包名。
2. 第三方 Spring Boot 工程添加 `spring-boot-starter-web`、`spring-boot-starter-validation`、
   `spring-boot-starter-security` 和 `spring-security-oauth2-jose`。
   使用项目兼容的 Spring Boot BOM 管理版本；仓库 `biz-project/pom.xml` 中有对应依赖。
3. 将本目录的 `application-partner-embed-demo.yml` **复制到第三方工程的 `src/main/resources/`**。
   此处文件只作配置样例，不会被 Flow 自动加载。
4. 第三方启动类扫描本包，例如：

```java
@SpringBootApplication(scanBasePackageClasses = EmbedDemoConfiguration.class)
public class PartnerApplication {
    public static void main(String[] args) {
        SpringApplication.run(PartnerApplication.class, args);
    }
}
```

先在第三方工程工作目录生成宿主签名密钥，使用 PKCS#8 PEM，至少 RSA 2048 bit：

```bash
umask 077
mkdir -p secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out secrets/partner-private.pem
```

私钥持久保存并限制权限，将 `secrets/` 排除在第三方版本控制之外。
该密钥属于第三方人员断言，不要复用 Flow Open API 签名密钥、Context AES 或 HMAC 密钥。

设置实际配置后，使用第三方工程自己的启动命令：

```bash
export SPRING_PROFILES_ACTIVE='partner-embed-demo'
export FLOW_API_BASE_URL='http://127.0.0.1:8080'
export FLOW_CLIENT_ID='<Flow 分配的 Client ID>'
export FLOW_CLIENT_SECRET='<Flow 分配的 Client Secret>'
export FLOW_EMBED_ORIGIN='https://embed.flow.example.com'
export PARTNER_ORIGIN='https://portal.partner.example'
export FLOW_VIEW_KEY='<已配置的 LIST viewKey>'
export FLOW_SURFACE_TYPE='LIST'
export FLOW_ENTRY_MODES='LIST'
export PARTNER_ASSERTION_ISSUER='https://id.partner.example'
export PARTNER_ASSERTION_KEY_ID='partner-demo-rsa-1'
export PARTNER_PRIVATE_KEY="$PWD/secrets/partner-private.pem"
export DEMO_USERNAME='demo'
export DEMO_PASSWORD='<设置联调密码>'
export DEMO_EXTERNAL_SUBJECT='employee-000123'

mvn spring-boot:run
```

变量中的 Secret、密码应从受限配置或密钥管理服务注入，不提交仓库。
示例默认监听 `127.0.0.1:18081`。第三方 HTTPS 宿主将 `/partner-api/**`、`/login`、`/logout`
代理到此服务，保持登录 Cookie 和 CSRF 同源；本包提供后端逻辑，TLS 和宿主页由宿主部署负责。

Origin 不带尾斜杠、路径、Query 或 Fragment，Embed 与宿主 Origin 必须不同。
API 根地址仅允许回环 HTTP 联调，其他地址使用 HTTPS；私有 CA 加入 JVM truststore，不关闭 TLS 校验。
出站连接/请求超时为 3/5 秒，不跟随重定向。

启动后导出公开 JWKS（无需登录），粘贴到 Flow Provider 的 `STATIC_JWK_SET`：

```bash
curl --fail http://127.0.0.1:18081/.well-known/jwks.json > secrets/partner-public-jwks.json
```

只将公钥交给 Flow，`kid` 与 `PARTNER_ASSERTION_KEY_ID` 一致。
使用 `REMOTE_JWKS` 时提供满足 Flow 网络策略的正式 HTTPS 地址；回环地址仅用于取出静态配置。

## 宿主接口与前端调用

`POST /partner-api/flow-embed-launch` 要求登录及 CSRF，返回原始 Launch 数据（不再包 `data`）：

```json
{
  "channelId": "66f82f09-89ec-4a5a-b81b-f54f02d22262",
  "entry": { "mode": "LIST" },
  "formPresentation": "seamless"
}
```

`formPresentation` 缺省或 null 时为 `seamless`，也支持精确小写 `dialog`。
接口拒绝未知字段，不接受浏览器指定 View、Origin、人员身份、Flow User ID 或 Context。
宿主登录后，前端调用示例：

```javascript
import FlowEmbed from '@flow/embed-sdk'

async function createWidget(container) {
  const csrfResponse = await fetch('/partner-api/csrf', { credentials: 'same-origin' })
  if (!csrfResponse.ok) throw new Error('请先登录宿主系统')
  const csrf = await csrfResponse.json()
  const channelId = crypto.randomUUID()
  const response = await fetch('/partner-api/flow-embed-launch', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: JSON.stringify({ channelId, entry: { mode: 'LIST' } })
  })
  if (!response.ok) {
    const error = await response.json()
    throw new Error(`${error.errorCode} (${error.traceId || ''})`)
  }
  const launch = await response.json()
  const widget = FlowEmbed.mount({
    container,
    embedUrl: launch.embedUrl,
    launchId: launch.launchId,
    launchCode: launch.launchCode,
    channelId,
    targetOrigin: 'https://embed.flow.example.com', // 与后端固定 Embed Origin 一致
    height: { mode: 'auto', min: 480, max: 1200 }
  })
  launch.launchCode = ''
  return widget
}
```

SDK 位于仓库 `workflow-frontend/workflow-web/packages/flow-embed-sdk`。
关闭、重新打开、`close.requested`、`session.expired` 时必须先 `await widget.destroy()`，确认释放旧 Session 后再创建新实例。
销毁失败时保留回收未确认状态并禁用再次打开。Launch Code 不放入 URL、Storage、Cookie、埋点或日志；
机器 Token、人员 JWT 和私钥不返回前端。

Flow 机器 401 刷新 Token 后以新 jti 重试一次；Flow 403 检查 Grant/Provider/Binding/用户状态；
429 按 Retry-After 退避，不自动重复签发。其他上游错误映射为 502，不回显上游原文。

## FORM、VIEW、Context 与真实用户

直接新建/查看表单时，将后端和 Flow View/Grant 同步配置为：

```bash
export FLOW_VIEW_KEY='<已配置的 FORM viewKey>'
export FLOW_SURFACE_TYPE='FORM'
export FLOW_ENTRY_MODES='CREATE,VIEW'
export DEMO_ALLOWED_RECORD_IDS='<当前宿主用户可查看的 recordId，多个用逗号分隔>'
```

入口分别为 `{"mode":"CREATE"}` 和 `{"mode":"VIEW","recordId":"..."}`。
LIST/CREATE 完全省略 recordId（null 也拒绝），VIEW 同时通过宿主记录授权及 Flow 权限；V1 不支持 EDIT。
默认 VIEW 白名单为空。

Context 配置在后端 `partner.embed-demo.host.context`，Flow 侧须先发布匹配的 Context Schema 和绑定。
例如 supplierId 从当前登录用户的业务归属推导，不能转发浏览器传值。

真实接入时替换以下示例逻辑：

- `EmbedDemoSecurity`：沿用第三方 SSO/登录会话和 CSRF 防护。
- `host.username -> externalSubject`：从认证用户取得稳定且不可回收的人员标识，用于 Flow Binding。
- 固定记录白名单、Context 和单一 View：替换为按当前用户/租户查询的业务授权；多个目标由后端白名单选择。
- 私钥文件：按第三方密钥管理方式加载。轮换时先向 Flow 发布新公钥和 kid，再切换签名私钥，旧公钥覆盖在途断言。

## 仓库验证

在 `workflow-server` 目录执行：

```bash
mvn -pl biz-project -am test -Dtest='EmbedDemo*Test' -Dsurefire.failIfNoSpecifiedTests=false
```

测试使用临时回环 HTTP 服务及 RSA 密钥，验证真实请求、签名、公钥、登录/CSRF、浏览器越权参数、
记录授权、401 重试、429、错误 Launch 目标和默认不注册示例 Bean；不连接真实 Flow。
真实联调需完成 Flow 配置并通过宿主 SDK 打开 iframe，确认映射用户权限与数据范围。

本次示例不新增、修改或删除数据库迁移文件。
