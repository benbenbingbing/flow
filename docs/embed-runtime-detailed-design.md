# Flow 第三方 iframe 嵌入运行时详细设计

> 状态：V1 应用代码、维护任务、OpenAPI/SDK 漂移门禁、专用部署清单与 Chrome 跨域 E2E 已收口；待本机 MySQL 隔离迁移验证、跨浏览器/安全验收、生产实际部署与接入方联合评审
> 文档版本：1.0
> 日期：2026-08-28
> 适用范围：第三方平台通过 iframe 嵌入 Flow 已发布的实体列表、实体表单及受控相关操作
> 实现状态：当前分支已实现 LIST/FORM 只读运行态与 RECORD_CREATE；RECORD_UPDATE、
> ACTION_EXECUTE、PROCESS_START 在 V1 发布与运行时均保持关闭。生产上线门槛见第 17.6、22、24.5 节

## 0. 方案摘要

推荐建设“iframe Embed Runtime”，而不是把现有后台列表/表单 URL 直接公开。

| 关键问题 | 设计结论 |
| --- | --- |
| 第三方应用如何认证 | 第三方后端复用现有 OAuth Client Credentials，新增 `embed.launch` Scope |
| iframe 中的人如何对应 Flow 用户 | 第三方短期签名人员断言，经精确 Identity Binding 映射到已存在的 `flowUserId` |
| iframe 如何持续鉴权 | 一次性 Launch 兑换为短期、受限、仅内存的 Embed Session Token |
| Flow 权限如何生效 | 每次 Runtime 请求恢复真实 `UserContext`，继续执行现有功能权限、数据权限和行级动作 |
| 如何限制暴露范围 | Embed View Release + Application Grant 固定实体、列表/表单 Release、字段、动作、Origin 和 Context |
| 可复用什么 | OAuth、应用、限流、审计、用户目录、列表/表单/实体运行态、数据权限及主要展示组件 |
| 必须新开发什么 | Embed 管理模型、身份绑定、Launch/Session、安全链、Facade、外部 DTO、Shell、SDK、动态 CSP |
| 当前 V1 | 已实现列表/选择/详情、只读表单、CREATE 联动只读重算和新建保存；编辑、动作、流程启动明确关闭 |

第三方浏览器永远不获得 Client Secret、机器 Token 或普通 Flow 用户 Token，也不能自行传入
`flowUserId/entityCode/listKey/formId/releaseId` 改变访问目标。

## 1. 背景与目标

Flow 的实体、表单、列表、流程、数据权限和 UI 发布能力已经具备较成熟的内部运行态。
新的接入需求是：第三方业务平台在自己的页面中，通过 iframe 嵌入 Flow 的某个实体列表、
新建表单或查看表单，并允许用户执行该嵌入资源明确授权的 V1 操作。编辑和通用动作属于后续
版本，不是当前接入契约。

该需求不是“公开匿名链接”，也不是“第三方前端直接调用任意实体 CRUD”。第三方页面中的
实际操作人需要对应到一个 Flow 用户，最终仍由 Flow 的功能权限、数据权限、发布配置和动作
规则进行授权。

### 1.1 建设目标

1. 为第三方平台提供稳定、版本化、可撤销的 iframe 嵌入协议。
2. 将第三方应用身份、第三方人员身份、Flow 用户身份明确分离。
3. 第三方用户精确映射到 Flow 用户，复用现有 `UserContext`、实体权限和数据范围。
4. 第三方只能访问管理员预先发布并授权的列表、表单、字段和动作。
5. iframe 不依赖第三方 Cookie，不复用普通 Flow 登录 JWT，不跳转 Flow 登录页。
6. 复用现有已发布表单、列表和实体运行态，避免重新实现业务规则。
7. 接口具备稳定错误码、审计、限流、幂等、重放防护和版本兼容策略。
8. 为后续 SDK、Webhook、附件、审批和 Headless API 扩展保留边界。

### 1.2 V1 已实现范围与关闭边界

当前分支已经交付 LIST/FORM 读取与 CREATE 写入链路。本文保留 UPDATE/ACTION/PROCESS 的目标
接口契约用于后续设计，但它们不是 V1 可用接口，发布校验、Bootstrap/Schema 投影和运行时均
必须 Fail Closed。

| 能力 | 当前 V1 状态 | 说明 |
| --- | --- | --- |
| 列表 Schema 与查询 | 已实现，可配置开放 | 仅已发布列表，分页、查询字段和固定过滤由服务端约束 |
| 记录选择并回传宿主 | 已实现，可配置开放 | 仅返回配置允许的字段，默认只返回记录 ID |
| 新建表单与保存 | 已实现，可配置开放 | 使用已发布表单、强制上下文字段和写请求幂等键 |
| 查看记录与只读表单 | 已实现，可配置开放 | 经过 Flow 数据范围与行级查看权限 |
| 编辑记录 | V1 强制关闭 | 尚无通用 `recordVersion` 乐观锁能力，不注册 PATCH 运行时路由 |
| 标准/自定义动作 | V1 强制关闭 | 尚无强类型 Embed Action Registry，不注册动作运行时路由 |
| 启动流程 | V1 强制关闭 | `PROCESS_START` 发布校验拒绝，不允许 `saveAndStart` |
| 删除、批量删除、导出 | 默认关闭 | 高风险能力，后续单独评审 |
| 审批、附件、关联内容 | 后续阶段 | 需要独立授权、对象校验和安全验收 |
| 匿名外部人员 | 不在 V1 | V1 要求映射到已存在且启用的 Flow 用户 |

### 1.3 非目标

1. 不把现有 `/entity-list/:entityCode/:listKey` 直接作为外部契约。
2. 不把 `/api/entity-lists/**`、`/api/entity-data/**` 或设计态接口改成公共接口。
3. 不把 OAuth Client Secret、普通 Flow Access Token 或长期 Embed Token 放入 iframe URL。
4. 不接受第三方传入的 `flowUserId`、权限码、SQL、数据范围表达式、`eventCode`、
   `operationCode`、`formId` 或 `releaseId` 作为可信授权依据。
5. 不允许合作方动态加载任意远程 JavaScript 到 Flow 页面。
6. V1 不实现外部人员即时创建 Flow 账号，也不使用共享服务账号代表所有外部用户。

## 2. 核心设计决策

### 2.1 iframe 是 V1 的运行载体

V1 使用独立 Embed Shell 承载 Flow 自己的列表和表单渲染器，第三方通过一个轻量 SDK
创建 iframe 并处理 `postMessage`。这样可以隔离 CSS、JavaScript、路由和依赖版本，且
Flow 发布新表单或列表配置时，不需要第三方重新打包前端。

直接输出 Vue 组件库或 Headless Schema API 可以作为后续能力，但不作为首期交付。

### 2.2 应用认证与人员认证分离

- OAuth 2.0 Client Credentials 只证明“哪个第三方应用在调用”。
- 用户签名断言只证明“第三方当前登录人员是谁”。
- Flow 身份绑定决定“这个外部人员对应哪个 Flow 用户”。
- Flow 用户权限和数据范围决定“该人员在 Flow 中本来能做什么”。
- Embed View 和 Application Grant 进一步决定“本次嵌入最多允许做什么”。

Client Credentials 不能单独证明当前人员身份。iframe、query 参数或宿主 JavaScript
声明的 `flowUserId` 不可信。

### 2.3 使用独立的受限 Embed Session

Embed Session 采用 256-bit 随机不透明 Bearer Token，数据库只保存 Token 哈希。该 Token：

- 仅由 `/api/embed/v1/**` 的专用认证过滤器接受；
- 不能访问普通 `/api/**` 管理或运行态接口；
- 绑定应用、Embed View Release、Flow 用户、外部主体、父页面 Origin、目标 UI 发布版本、
  上下文快照和能力快照；
- 仅保存在 iframe 内存，不写入 Cookie、`localStorage` 或 `sessionStorage`；
- 默认空闲 5 分钟、绝对 30 分钟，可按应用在安全范围内配置；
- 支持立即撤销，应用、授权、身份绑定或 Embed View 停用后可使会话失效。

不直接复用普通 `AuthSessionService.createSession`，因为当前普通 JWT 没有独立 Audience、
Token 类型、Embed View、Origin 和动作能力绑定，发给 iframe 后会扩大接口访问面。

### 2.4 专用 Facade，不直接开放现有 Controller

浏览器只访问 `/api/embed/v1/**`。Facade 从 Embed Session 中恢复真实的实体、列表、表单、
发布版本和上下文，再调用现有应用服务。浏览器请求中不接受任意目标配置 ID。

专用 Facade 同时执行两层授权：

```text
Embed 应用/视图/动作白名单授权
                 ∩
Flow 用户现有功能权限、数据权限、行级动作和发布规则
```

任何一层拒绝，操作即拒绝。

### 2.5 运行时固定到不可变发布快照

Embed View 自身采用草稿和发布版本。每次 Launch 必须绑定一个不可变 Embed View Release。
该 Release 可配置：

- `PINNED`：固定到指定表单/列表 UI Release；
- `FOLLOW_ACTIVE`：Launch 时解析当前 ACTIVE UI Release，但创建会话后仍固定到该版本。

浏览器不能提交 `releaseId` 或 `releaseVersion` 来切换目标版本。已有会话不会因为后台新发布
配置而在操作中途漂移。

### 2.6 外部上下文只能缩小访问范围

第三方可在 Launch 时传入业务上下文，例如 `supplierId`、`projectId`。上下文必须通过
Embed View 发布的 JSON Schema 校验，并由服务端映射为：

- 服务端固定过滤；
- 表单默认值；
- 服务端强制字段；
- 允许回传宿主的业务引用。

上下文不能覆盖 Flow 数据权限，不能作为 SQL、表达式或内部配置 ID 使用。运行时不允许
宿主通过 `postMessage` 任意变更安全上下文；需要变更时必须重新 Launch。

### 2.7 外部契约独立版本化

以下内容共同构成 Embed V1 外部契约：

- `/api/open/v1/embed-launches`；
- `/api/embed/v1/**`；
- iframe 页面 `/embed/v1/launches/{launchId}`；
- `flow-embed/1` postMessage 协议；
- 计划新增的 `docs/api/embed-v1.yaml`；
- SDK 的 V1 公共类型和事件。

同一主版本只能增加可选字段、能力或事件，不能删除字段、改变既有字段含义、改变认证方式
或复用既有错误码表达不同语义。破坏性变更使用 `/v2` 和 `flow-embed/2`。

## 3. 当前能力与差距分析

### 3.1 可复用的现有能力

| 能力 | 现有实现 | 复用结论 |
| --- | --- | --- |
| 第三方应用、Client ID/Secret、启停和轮换 | `workflow-open-api` 的 `IntegrationApplicationService` | 直接作为 Embed Application 的机器身份根对象 |
| OAuth Client Credentials | `OpenIntegrationSecurityConfiguration` | 直接用于服务端 Launch API，不用于 iframe 运行态 |
| 应用 Scope、来源网段、限流、并发租约 | `IntegrationScopeMapper`、`OpenApiApplicationPolicyFilter` 等 | 扩展 `embed.launch` Scope 后复用 |
| 外部响应 Envelope、Trace、1 MiB 请求保护 | `OpenApiResponse`、`OpenApiRequestGuardFilter` | 外部 Launch 与 Embed API 统一复用其风格 |
| 凭据生成与哈希 | `IntegrationSecretGenerator`、`IntegrationSecretHasher` | 复用安全随机数和哈希设计，Token 不存明文 |
| 系统审计 | `SystemAuditPort`、`SystemAuditEvent` | 复用，模块归类为 `INTEGRATION` |
| Flow 用户目录 | `IdentityDirectoryPort`、`SysUserService` | 精确解析和校验映射后的 Flow 用户 |
| 当前用户上下文 | `UserContext` | Embed 过滤器建立并在请求后清理 |
| 列表 Schema/查询 | `EntityListRuntimeService`、`EntityListPublishedRuntimeService` | 通过新跨模块端口和 Adapter 复用 |
| 表单解析 | `EntityFormResolveService`、`EntityFormRuntimeAdapter` | 通过 Embed Facade 复用 |
| 已发布表单提交 | `PublishedFormSubmissionService` | 复用表单规则、默认值与提交校验 |
| 实体详情和 CRUD | `EntityDataActionService` | 复用权限、数据范围、行级动作与审计 |
| 功能/行/按钮能力 | `EntityActionCapabilityService` | 作为 Flow 用户授权层继续执行 |
| 数据范围 | `DataPermissionEngine`、`EntityDataDynamicService` | 继续以映射后的 Flow 用户执行 |
| 列表前端渲染 | `EntityDataList.vue` 及查询区、表格组件 | 抽取展示层、替换内部 API 客户端后复用 |
| 表单字段、联动、动作条 | `EntityDataFormFields.vue`、`FormPreviewLinkage.vue`、`FormActionBar.vue` | 抽取运行时内容并注入 Embed Client 后复用 |

### 3.2 当前不能直接用于第三方 iframe 的部分

1. 当前实体列表路由挂在后台 `Layout` 下，会显示侧栏、菜单和后台导航。
2. 前端路由守卫在判断 public route 前无条件调用 `restoreAuthSession()`。
3. 全局请求客户端使用普通用户 Token、`withCredentials: true` 和 `/api/auth/refresh`。
4. Refresh Cookie 默认 `SameSite=Lax`，跨站 iframe 不能可靠使用。
5. 当前 Nginx 对全站设置 `frame-ancestors 'none'`，所有外部 iframe 均被禁止。
6. 当前内部列表/数据/表单接口接受 `entityCode`、`listKey`、`formId`、Release 等内部参数，
   暴露面过宽，缺少应用和 Embed View 绑定。
7. `UiEventRuntimeController`、`UiInterfaceOperationRuntimeController` 是内部泛化执行入口，
   不能允许外部浏览器指定任意事件或接口操作。
8. 当前动态实体更新没有通用乐观版本条件。若直接开放外部编辑，可能发生覆盖更新。
9. 当前 `ExternalIdentityResolver` 请求模型偏开放流程场景，缺少 Application、Issuer、Subject
   和已验证断言语义，不能直接作为完整 Embed 身份链路。

### 3.3 相关现有代码

- `workflow-web/src/router/index.js`
- `workflow-web/src/shared/request/index.js`
- `workflow-web/nginx.conf`
- `workflow-web/src/views/entity/EntityDataList.vue`
- `workflow-web/src/views/entity/components/EntityDataFormDialog.vue`
- `workflow-server/workflow-app/src/main/java/com/workflow/config/CorsConfig.java`
- `workflow-server/workflow-admin/src/main/java/com/workflow/admin/auth/infrastructure/AuthInterceptor.java`
- `workflow-server/workflow-admin/src/main/java/com/workflow/admin/authorization/infrastructure/EndpointAuthorizationInterceptor.java`
- `workflow-server/workflow-open-api/src/main/java/com/workflow/openapi/security/OpenIntegrationSecurityConfiguration.java`
- `workflow-server/workflow-entity/src/main/java/com/workflow/entity/list/api/web/EntityListRuntimeController.java`
- `workflow-server/workflow-entity/src/main/java/com/workflow/entity/data/api/web/EntityDataController.java`
- `workflow-server/workflow-process/src/main/java/com/workflow/process/form/api/web/EntityFormResolveController.java`

## 4. 总体架构

```mermaid
flowchart LR
    HostUser["第三方当前用户"] --> HostWeb["第三方页面"]
    HostWeb --> HostBackend["第三方后端"]
    HostBackend -->|"Client Credentials"| OAuth["Flow OAuth /oauth2/token"]
    HostBackend -->|"机器 Token + 用户断言"| Launch["/api/open/v1/embed-launches"]
    Launch --> Identity["外部主体校验与 Flow 用户映射"]
    Launch --> ViewRelease["Embed View Release 与 UI Release 解析"]
    Launch --> Ticket["一次性 Launch"]
    HostWeb -->|"iframe + postMessage"| Shell["Embed Shell"]
    Shell -->|"一次性兑换"| Exchange["/api/embed/v1/launches/{launchId}/exchange"]
    Exchange --> Session["Embed Session"]
    Shell -->|"短期 Bearer"| Facade["/api/embed/v1/runtime/**"]
    Facade --> Auth["EmbedSessionFilter + UserContext + EmbedContext"]
    Auth --> Ports["Embed Runtime Contracts"]
    Ports --> ListRuntime["现有列表运行态"]
    Ports --> FormRuntime["现有表单运行态"]
    Ports --> DataRuntime["现有实体与动作运行态"]
    Shell -->|"flow-embed/1"| HostWeb
```

### 4.1 模块边界

建议新增 `workflow-embed` Maven 模块，仍保持现有模块化单体部署，不新增独立微服务。

```text
workflow-open-api
  └─ 复用机器 OAuth、应用策略；新增窄的 Embed Launch Controller

workflow-embed（新增）
  ├─ Embed View/Release/Grant 管理
  ├─ 外部身份提供方与身份绑定
  ├─ Launch 与 Session 生命周期
  ├─ Embed 认证过滤器和运行时 Facade
  ├─ EmbedContext、限流、幂等和审计
  └─ 管理端 API

workflow-contracts（新增契约）
  ├─ EmbedLaunchIssuePort
  ├─ EmbedListRuntimePort
  ├─ EmbedFormRuntimePort
  ├─ EmbedRecordRuntimePort
  └─ ExternalSubjectResolver / EmbedActor

workflow-entity / workflow-process
  └─ 实现 Adapter，内部调用现有 Runtime/Application Service

workflow-admin
  └─ 用户目录、UserContext、权限与审计基础

workflow-web
  ├─ 管理端 Embed 配置页面
  ├─ 独立 Embed Shell
  ├─ Embed List/Form Runtime
  └─ framework-agnostic iframe SDK
```

`workflow-open-api` 不直接依赖 `workflow-embed` 实现。建议在 `workflow-contracts` 定义
`EmbedLaunchIssuePort`，由 `workflow-embed` 实现，Open API Controller 只调用契约端口。
`workflow-embed` 也不得直接访问实体或流程 Mapper。

### 4.2 三类接口边界

| 接口组 | 调用方 | 认证 | 用途 |
| --- | --- | --- | --- |
| 管理端 `/api/embed-management/v1/**` | Flow 管理员浏览器 | 普通 Flow 用户 JWT + 权限码 | 配置、发布、授权、身份绑定、撤销会话 |
| Launch `/api/open/v1/embed-launches` | 第三方后端 | OAuth Client Credentials 机器 Token | 验证应用和人员、创建一次性 Launch |
| Runtime `/api/embed/v1/**` | Flow 域名内的 iframe | 一次性 code 或 Embed Session Token | 兑换会话、加载 Schema、查询、详情、提交和动作 |

第三方宿主页面不直接跨域调用 Runtime API。iframe 由 Flow Embed 域名加载，并同源访问
`/api/embed/v1/**`，因此不需要为合作方页面开放泛化 CORS。

## 5. 领域模型与术语

### 5.1 Integration Application

现有 `integration_application`。表示一个第三方系统及其机器身份、Client Credentials、
Scope、来源网段、限流和并发策略。Embed 不重新创建一套 Client ID/Secret。

### 5.2 Embed View

管理员可编辑的嵌入资源草稿。第三方通过稳定 `viewKey` 引用，而不是直接引用实体编码、
列表 Key 或表单 ID。

一个 View 可以是：

- `LIST`：以列表为入口，允许查询、选择以及受控打开表单；
- `FORM`：以表单为入口，V1 只允许已发布的 CREATE 或 VIEW 模式。

### 5.3 Embed View Release

Embed View 的不可变发布快照，包含目标实体、表单/列表、允许模式、动作、字段策略、上下文
Schema、上下文绑定、返回字段、UI 配置和 Release 策略。Launch 必须绑定具体 Release。

### 5.4 Application Grant

表示某个 Integration Application 获准使用某个 Embed View。Grant 包含应用级覆盖策略：

- 精确允许的父页面 Origin；
- 能力上限；
- 外部身份提供方；
- 是否允许可信应用直接声明外部用户 ID；
- 有效期、状态和并发/会话限制。

### 5.5 External Identity Provider

第三方人员断言的信任配置，包括 Issuer、Audience、JWKS/公钥、允许算法、时钟偏差和身份
Namespace。V1 支持：

- `SIGNED_JWT`：推荐，Flow 验证第三方短期签名 JWT；
- `TRUSTED_EXTERNAL_ID`：仅对明确授权的内部可信应用开放。

`OIDC_ID_TOKEN` 预留到后续阶段，因为标准 ID Token 的 Audience、寿命、Nonce 和重放语义
与本方案的 60 秒一次性人员断言不同，不能直接按相同规则验收。

### 5.6 External Identity Binding

精确映射：

```text
(applicationId, issuer/namespace, externalSubject) -> flowUserId
```

禁止按姓名、手机号或可修改用户名模糊匹配。V1 不自动创建 Flow 用户。

### 5.7 Embed Launch

一次性、短期启动凭据，默认 60 秒有效。绑定应用、View Release、Flow 用户、外部主体、
父页面 Origin、上下文快照和 UI Release。Launch code 仅返回一次，数据库只保存哈希。

状态：`ISSUED -> CONSUMED`，或 `ISSUED -> EXPIRED/REVOKED`。并发兑换只能有一个成功。

### 5.8 Embed Session

iframe 运行时会话。状态为 `ACTIVE`、`LOGGED_OUT`、`REVOKED` 或 `EXPIRED`。Session 保存：

- Application、Grant、View、View Release；
- Flow 用户及外部主体；
- 父页面 Origin 和 postMessage channel；
- 上下文和能力快照；
- 表单/列表 UI Release 快照；
- Token 哈希、空闲和绝对过期时间；
- 撤销版本与审计字段。

### 5.9 能力枚举

V1 定义以下稳定外部能力枚举；“可发布”仍表示管理员必须在 View 和 Grant 中显式开放，
不是匿名默认授权：

| 能力 | 含义 | 当前 V1 发布状态 |
| --- | --- | --- |
| `LIST_QUERY` | 加载列表 Schema 和分页查询 | 已实现，可由 View/Grant 显式发布 |
| `SELECTION_RETURN` | 将已授权选择结果返回宿主 | 已实现，可由 View/Grant 显式发布 |
| `RECORD_VIEW` | 查看记录详情和只读表单 | 已实现，可由 View/Grant 显式发布 |
| `RECORD_CREATE` | 新建并保存记录 | 已实现，可由 View/Grant 显式发布 |
| `RECORD_UPDATE` | 编辑记录 | 强制关闭，需先建设通用乐观锁 |
| `ACTION_EXECUTE` | 执行发布动作白名单 | 强制关闭，需先建设强类型 Registry |
| `PROCESS_START` | 保存并发起流程 | 强制关闭，需单独完成流程动作契约 |
| `RECORD_DELETE` | 删除单条记录 | V1 强制关闭 |
| `BATCH_DELETE` | 批量删除 | V1 强制关闭 |
| `EXPORT` | 导出 | V1 强制关闭 |
| `FILE_UPLOAD` / `FILE_DOWNLOAD` | 附件操作 | V1 强制关闭 |

配置能力只是上限，不替代 Flow 用户权限。

## 6. 身份映射与鉴权详细设计

### 6.1 身份链路

```mermaid
sequenceDiagram
    participant U as 第三方用户
    participant H as 第三方后端
    participant O as Flow OAuth/Open API
    participant I as Flow 身份映射
    participant W as Embed Shell
    participant E as Embed Runtime API
    participant R as 实体/表单运行态

    U->>H: 已在第三方系统登录
    H->>O: Client Credentials 获取机器 Token
    H->>H: 签发短期用户断言 JWT
    H->>O: 创建 Embed Launch
    O->>I: 验签 issuer/audience/sub/jti
    I->>I: 精确映射到 flowUserId
    I-->>O: 已启用的 Flow 用户
    O-->>H: embedUrl + 一次性 launchCode
    H-->>U: 创建 iframe
    W->>E: 使用 launchCode 原子兑换
    E-->>W: Embed Session Token + Bootstrap
    W->>E: Bearer Embed Token 请求
    E->>E: 校验 Session，建立 EmbedContext
    E->>E: UserContext.setCurrentUser(flowUserId)
    E->>R: 调用现有运行态服务
    R-->>E: 数据/Schema/动作结果
    E->>E: finally 清理上下文
    E-->>W: 受控响应
```

### 6.2 第三方用户断言

推荐断言为最长 60 秒的签名 JWT，至少包含：

```json
{
  "iss": "https://id.partner.example",
  "sub": "user-10086",
  "aud": "flow-embed-launch",
  "iat": 1787719200,
  "exp": 1787719260,
  "jti": "814d23d7-7c90-4ed0-a6c0-c3bc0f18d38d"
}
```

校验规则：

1. `iss` 必须与 Grant 绑定的 Identity Provider 完全一致。
2. `aud` 必须包含 `flow-embed-launch`，不能接受 Flow 普通 API Audience；该规则针对
   V1 `SIGNED_JWT` 人员断言。
3. `sub` 必填、长度不超过 128，作为外部稳定不可变人员标识。
4. `exp - iat` 最大 60 秒；默认允许 30 秒时钟偏差。
5. `jti` 必填，并在有效窗口内禁止重放。
6. 只允许管理员登记的非对称签名算法，默认 `RS256` 或 `ES256`；拒绝 `none` 和动态算法降级。
7. 根据登记公钥或受控 JWKS 地址验签，不接受请求携带的任意 `jku`、`x5u`。
8. 断言只证明身份，不接受第三方断言中的角色、权限或数据范围作为 Flow 授权。

`TRUSTED_EXTERNAL_ID` 模式不携带签名断言，但只有 Grant 明确设置
`trustedSubjectAssertion=true` 时允许。此模式意味着该 Integration Application 可以代表其
Namespace 下任意已绑定用户，应配合最小来源网段、强审计和更低限流。

### 6.3 Flow 用户映射算法

1. 取得经过验证的 `applicationId + issuer/namespace + externalSubject`。
2. 精确查询 ACTIVE Identity Binding。
3. 找不到映射时返回 `EXTERNAL_IDENTITY_NOT_MAPPED`，不按用户名猜测。
4. 使用 `IdentityDirectoryPort`/用户服务加载 `flowUserId`。
5. 检查用户存在、未禁用、未删除；密码重置限制是否阻止 Embed 由安全策略配置，默认阻止。
6. 将映射版本和 Flow 用户 ID 固定到 Launch 和 Session。
7. 每个 Runtime 请求重新检查用户有效状态；角色和数据权限不写入长期 Token，继续从 Flow
   当前权限服务计算，使权限变更在短缓存窗口内生效。

当前 `ExternalIdentityResolver` 可复用“Namespace 精确解析”思想，但其请求结构是流程场景。
建议新增通用 `ExternalSubjectResolver`，避免把 Embed Application、Issuer 和 Subject 塞入
流程专用字段。

### 6.4 Embed Session 认证过滤器

新增 `EmbedSessionAuthenticationFilter`，仅匹配 `/api/embed/v1/runtime/**` 和 Session 管理接口：

1. 读取 `Authorization: Bearer <opaqueToken>`。
2. 对 256-bit 随机 Token 计算 SHA-256 后查询 `embed_session`，明文 Token 从不落库。
3. Runtime、查询和 Heartbeat 只接受 ACTIVE 且未超过 idle/absolute expiry 的 Session。
   `DELETE /session` 使用同一 Token 摘要但走受限 Logout 分支：只允许 ACTIVE 执行终止，
   LOGGED_OUT 仅返回幂等 204，不建立业务 UserContext；其他终态按对应过期/撤销错误返回。
4. 检查 Application、Grant、View、Identity Provider、Identity Binding 和 Flow 用户仍有效，
   且各安全版本与 Session 快照一致。
5. 检查请求目标属于 Session 绑定 View，拒绝跨 View、跨应用访问。
6. 创建 `EmbedContext`，包含 application/view/release/origin/externalSubject/context/capabilities。
7. 调用 `UserContext.setCurrentUser(flowUserId, username, sessionId)`。
8. 执行后续 Facade 和现有运行态服务。
9. 在 `finally` 中清理 `UserContext`、`EmbedContextHolder` 和日志 MDC。
10. 最多每 30 秒更新一次 `last_seen_at`，避免每个查询都写数据库。

需要在 `CorsConfig` 中把 `/api/embed/v1/**` 从普通 `AuthInterceptor` 和普通
`EndpointAuthorizationInterceptor` 的默认链路排除，改由专用过滤器和 `@EmbedApi` 策略处理。
不能简单标注 `@PublicApi`。

### 6.5 权限决策顺序

每个 Runtime 操作按以下顺序 Fail Closed：

1. Session 有效；
2. Application/Grant/View/Identity Provider/Identity Binding/Flow 用户有效；
3. View Release 允许该能力和 entry mode；
4. 请求字段、查询字段、返回字段和 actionKey 在发布白名单；
5. 上下文固定条件已应用且不可覆盖；
6. Flow 用户拥有实体标准权限；
7. Flow 数据范围允许访问目标行；
8. 已发布列表按钮/表单动作允许；
9. 记录状态允许操作；
10. 写请求幂等和乐观锁通过。

记录不存在和无权访问统一返回 404 `EMBED_RESOURCE_NOT_FOUND`，避免 IDOR 枚举。

## 7. Embed View 配置与发布模型

### 7.1 推荐配置示例

```json
{
  "viewKey": "supplier-work-orders",
  "name": "供应商工单",
  "surfaceType": "LIST",
  "target": {
    "entityCode": "work_order",
    "listKey": "supplier_open",
    "defaultFormId": "form-work-order-external"
  },
  "entryModes": ["LIST", "CREATE", "VIEW"],
  "releasePolicy": {
    "strategy": "PINNED",
    "listReleaseId": "list-release-12",
    "formReleaseId": "form-release-8"
  },
  "capabilities": [
    "LIST_QUERY",
    "SELECTION_RETURN",
    "RECORD_VIEW",
    "RECORD_CREATE"
  ],
  "fieldPolicy": {
    "visible": ["id", "code", "title", "status", "amount"],
    "queryable": ["code", "status"],
    "writable": ["title", "amount", "description"],
    "returnable": ["id", "code", "title"]
  },
  "queryPolicy": {
    "allowTotal": false,
    "maxPageSize": 100
  },
  "actionPolicy": {
    "allowed": ["view", "create", "save"],
    "requireConfirmation": []
  },
  "contextSchema": {
    "type": "object",
    "additionalProperties": false,
    "required": ["supplierId"],
    "properties": {
      "supplierId": {
        "type": "string",
        "minLength": 1,
        "maxLength": 64
      }
    }
  },
  "contextBindings": [
    {
      "source": "supplierId",
      "target": "supplier_id",
      "usage": "FIXED_FILTER"
    },
    {
      "source": "supplierId",
      "target": "supplier_id",
      "usage": "FORCED_FORM_VALUE"
    }
  ],
  "ui": {
    "showSearch": true,
    "showPagination": true,
    "showToolbar": true,
    "pageSize": 20,
    "heightMode": "AUTO"
  }
}
```

### 7.2 发布校验

发布 Embed View 前必须校验：

1. View Key 全局唯一且发布后不可变。
2. 实体存在且状态为已发布。
3. List/Form 均存在已发布 Release，且属于目标实体。
4. 字段白名单均存在于已发布快照；Writable 必须是表单可写字段。
5. Returnable 是 Visible 的子集，并排除敏感字段。
6. Action Key 存在于发布配置且进入强类型 Registry；发布器能完整生成第 11.4 节
   `ExternalActionDescriptor`，`transport/recordMode/selectionMode` 组合合法，Data/Input Schema
   默认 `additionalProperties=false`。未显式选择或无法安全映射的动作禁止发布。
7. Context Schema 大小不超过 16 KiB、深度和属性数受限，禁止远程 `$ref`；V1 不支持
   `contextSchema.pattern`，配置中出现该关键字时以 `SCHEMA_PATTERN_NOT_SUPPORTED` 拒绝
   校验或发布，历史/篡改快照在 Launch 时同样 Fail Closed。字符串约束使用 `enum`、
   `minLength`、`maxLength` 等受控关键字。
8. Context Binding 目标字段存在，固定过滤和强制字段不能被客户端覆盖。
9. FOLLOW_ACTIVE 的兼容性影响经过预检；敏感场景建议 PINNED。
10. V1 禁止 Embed View 使用全部自定义组件；后续开放时只能来自 Flow 内部可信注册表。
11. V1 不开放动态表单数据源或 Lookup Provider：发布快照中存在任意表单/字段数据源绑定、
    自定义组件或事件绑定时按不可信资源拒绝；`REFERENCE`、`MULTI_REFERENCE`、`LOOKUP`、
    `MULTI_LOOKUP`、`USER`、`DEPT`、`ROLE`、`GROUP` 表单字段整体以
    `UNTRUSTED_COMPONENT` 阻断发布；字段
    `validationRules` 出现任意 `pattern` 同样阻断。当前 options 路由只分页不可变 Form Release
    中超过 100 项的静态选项，Lookup 路由保持 Deferred/Fail Closed；禁止回退到任意 HTTP、SQL、
    内部 Service/Operation 或 UI Event。

### 7.3 Application Grant

Grant 至少配置：

```json
{
  "applicationId": "integration-app-1",
  "viewId": "embed-view-1",
  "allowedOrigins": ["https://portal.partner.example"],
  "identityProviderId": "partner-idp-1",
  "capabilityCeiling": [
    "LIST_QUERY",
    "SELECTION_RETURN",
    "RECORD_VIEW",
    "RECORD_CREATE"
  ],
  "trustedSubjectAssertion": false,
  "maxActiveSessionsPerUser": 3,
  "expiresAt": null,
  "status": "ACTIVE"
}
```

Origin 必须是规范化的 `scheme://host[:port]`，生产只允许 HTTPS，不允许 `*`、路径、用户名、
fragment、通配子域或混合大小写绕过。

### 7.4 配置生命周期

```text
View: DRAFT -> ACTIVE <-> DISABLED -> RETIRED
View Release: PUBLISHED（不可变）
Grant: ACTIVE <-> DISABLED -> REVOKED；到期后按 EXPIRED 处理
Identity Provider: ACTIVE <-> DISABLED -> REVOKED
Identity Binding: ACTIVE <-> DISABLED -> REVOKED
```

View 草稿修改不影响现有 Launch/Session；发布新 Release 后只影响新的 Launch。禁用 View、Grant、
应用或身份绑定默认立即阻止新 Launch，并使现有 Session 在下一个请求失效。

## 8. 接口通用约定

### 8.1 路径与版本

- 管理端：`/api/embed-management/v1/**`，内部契约，不对第三方承诺。
- 服务端 Launch：`/api/open/v1/embed-launches`，属于 Open API V1。
- 浏览器运行时：`/api/embed/v1/**`，属于 Embed Runtime V1。
- iframe 页面：`https://embed.flow.example.com/embed/v1/launches/{launchId}`。
- postMessage：`protocol = flow-embed/1`。

### 8.2 内容类型与编码

- JSON 接口：`application/json; charset=UTF-8`。
- OAuth Token：现有 `application/x-www-form-urlencoded`。
- 时间：UTC ISO-8601，例如 `2026-08-26T08:30:00Z`。
- ID：字符串，不向外承诺数据库格式或递增规律。
- 客户端必须忽略未知响应字段和未知非关键事件。

### 8.3 请求头

| Header | 使用范围 | 要求 |
| --- | --- | --- |
| `Authorization: Bearer ...` | Launch / Runtime | Launch 使用机器 Token；Runtime 使用 Embed Token |
| `X-Trace-Id` | 全部外部 API | 可选，1–64 位 `[A-Za-z0-9._-]`；非法时服务端重新生成 |
| `X-Request-Id` | 全部外部 API | 可选，同样限制为 1–64 位；响应总是返回 |
| `Idempotency-Key` | Runtime 写操作 | 必填，1–128 个非空可打印 ASCII 字符 |
| `X-Flow-Embed-Protocol` | Exchange、Bootstrap 必填；SDK 对 Runtime 统一发送 | 固定 `1`；Exchange 或 Bootstrap 缺失/不为 `1` 时返回 400 |

关键响应头：

| Header | 场景 | 语义 |
| --- | --- | --- |
| `X-Trace-Id/X-Request-Id` | 全部 API | 返回服务端最终采用的关联 ID |
| `Idempotent-Replay: true` | 写请求命中成功回执 | 本次没有再次执行业务，Body 已按当前权限投影 |
| `Retry-After: <seconds>` | 409 处理中或 429/503 | 非负整数秒，客户端保留原幂等键并退避 |
| `Cache-Control: no-store` | 所有动态/凭据响应 | 中间层和浏览器不得缓存 |

V1 的记录详情和创建响应固定 `recordVersion=null` 且不返回 ETag；未来更新接口使用的
`If-Match/ETag` 契约只见第 11.9、16.4 节，不属于当前请求头或响应头。

### 8.4 响应 Envelope

所有 JSON 外部响应使用真实 HTTP 状态，并采用现有 Open API 风格：

```json
{
  "code": 200,
  "message": "success",
  "errorCode": null,
  "data": {},
  "traceId": "trace-20260826-001"
}
```

错误示例：

```json
{
  "code": 403,
  "message": "Embed action is not allowed",
  "errorCode": "EMBED_OPERATION_NOT_ALLOWED",
  "data": null,
  "traceId": "trace-20260826-001"
}
```

客户端只能基于 HTTP 状态和 `errorCode` 编写逻辑，不依赖 `message` 文案。

### 8.5 缓存与敏感响应

Launch、Exchange、Bootstrap、Session 和所有包含用户/表单数据的响应设置：

```http
Cache-Control: no-store
Pragma: no-cache
Referrer-Policy: no-referrer
```

静态 hash JS/CSS 资源可长缓存。Token、Launch code、用户断言和完整 Context 不得写入日志、
审计详情、指标标签或浏览器错误上报。

### 8.6 请求大小与分页

- 默认请求体最大 1 MiB，Launch Context 单独限制 16 KiB。
- 列表 `pageNum` 默认 1；`pageSize` 默认取 View 配置并受 Bootstrap/Schema 的 `maxPageSize` 限制。
- 列表过滤项最多 32 个，`IN` 值最多 100 项，字符串过滤值最大 2,048 字符。
- 表单字段数量和嵌套深度沿用已发布表单校验，并额外限制总 JSON 大小。

### 8.7 幂等约定

- Launch 与 OAuth Token 一样属于短期凭据签发，不使用业务幂等响应缓存。调用方超时可重新
  创建一个新 Launch，旧 Launch 在 60 秒后过期；不得把 launchCode 写入现有明文
  `integration_idempotency_record.response_body`。
- 当前 V1 的记录创建必须携带 `Idempotency-Key`；更新和动作尚未注册，未来启用时沿用该规则。
- 沿用现有存储唯一范围 `Application + Operation + Idempotency-Key`。请求哈希使用稳定的
  `actorScopeDigest + viewKey + target + operation + canonicalBody`。RECORD_CREATE 的 canonical
  target 固定为稳定业务坐标 `entityCode + formId`，明确排除 Session ID、Launch ID、View/Form/List
  Release ID、Trace、Token 和时间戳；这样 iframe 丢失 Session 或发布版本变化后仍可在当前授权下
  用原键安全查询首次最小回执。
- 同一稳定 Actor/View/Operation/Key 使用相同请求体时返回首次业务结果；请求体不同，或同一
  Key 被其他 Actor/View 使用时返回 409 `EMBED_IDEMPOTENCY_KEY_REUSED`。
- 重放前仍要校验新 Session、Flow 用户、View 授权及当前对象可见性，且按当前 Output Policy
  重新投影；授权已撤销时拒绝重放，绝不能泄漏旧响应。
- 幂等记录不得持久化完整敏感表单正文；V1 只保存 `receiptId/outcomeCode` 固定 Envelope。
  未来确需保存敏感回执时必须新增专用密文字段和版本化契约，不能把它塞进明文
  `response_body`。
- 创建或流程启动在客户端超时时不得盲目换新幂等键重试。

### 8.8 Release 和安全参数不由浏览器指定

浏览器 Runtime 请求不得包含并影响以下可信参数：

```text
applicationId, flowUserId, entityCode, listKey, formId,
listReleaseId, formReleaseId, releaseVersion, fixedFilter,
allowedActions, dataScope, eventCode, serviceId, operationCode
```

这些参数一律从 Embed Session 和 View Release 恢复。若响应中为渲染需要返回某些标识，
服务端后续仍必须忽略客户端回传值并以 Session 快照为准。

## 9. 管理端接口设计

管理端接口供 Flow 管理员配置“哪些资源可以被哪些应用、以什么身份、从哪些页面嵌入”。
它们继续使用普通 Flow 登录会话、现有统一响应结构和 Endpoint 权限拦截器，不开放给
第三方服务器或 iframe。

### 9.1 管理端权限码

| 权限码 | 用途 |
| --- | --- |
| `system:embed:view` | 查看 View、Release、Grant 和运行状态 |
| `system:embed:manage` | 创建和修改 View、Grant、Origin |
| `system:embed:publish` | 校验并发布不可变 View Release |
| `system:embed:identity-manage` | 管理 Identity Provider 和身份绑定 |
| `system:embed:session-revoke` | 查询并撤销 Launch/Session |

发布、身份映射、Origin 变更和会话撤销属于高风险管理操作，除接口权限外还应进入现有系统
审计；是否增加二次确认由管理端产品策略决定。

### 9.2 Embed View 接口

| 方法与路径 | 权限 | 说明 |
| --- | --- | --- |
| `GET /api/embed-management/v1/views` | `system:embed:view` | 分页查询 View |
| `POST /api/embed-management/v1/views` | `system:embed:manage` | 新建 View 和初始草稿 |
| `GET /api/embed-management/v1/views/{viewId}` | `system:embed:view` | 查看 View 摘要 |
| `GET /api/embed-management/v1/views/{viewId}/draft` | `system:embed:view` | 获取当前草稿 |
| `PATCH /api/embed-management/v1/views/{viewId}/draft` | `system:embed:manage` | 乐观锁更新草稿 |
| `POST /api/embed-management/v1/views/{viewId}/validate` | `system:embed:manage` | 发布前校验，不产生 Release |
| `POST /api/embed-management/v1/views/{viewId}/publish` | `system:embed:publish` | 发布不可变 Release |
| `POST /api/embed-management/v1/views/{viewId}/status` | `system:embed:manage` | 启用、禁用或退役 |
| `GET /api/embed-management/v1/views/{viewId}/releases` | `system:embed:view` | 查询发布历史 |
| `GET /api/embed-management/v1/views/{viewId}/releases/{revision}` | `system:embed:view` | 查看指定快照 |

分页查询参数：

| 参数 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `keyword` | string | 否 | 匹配 `viewKey` 或名称，最大 100 字符 |
| `status` | string | 否 | `DRAFT/ACTIVE/DISABLED/RETIRED` |
| `surfaceType` | string | 否 | `LIST/FORM` |
| `applicationId` | string | 否 | 查询已授权给某应用的 View |
| `pageNum` | integer | 否 | 默认 1 |
| `pageSize` | integer | 否 | 默认 20，最大 100 |

新建请求：

```json
{
  "viewKey": "supplier-work-orders",
  "name": "供应商工单",
  "surfaceType": "LIST",
  "description": "供供应商门户查询和新建工单"
}
```

返回 `201 Created`，`data` 至少包含：

```json
{
  "id": "ev_01K...",
  "viewKey": "supplier-work-orders",
  "name": "供应商工单",
  "surfaceType": "LIST",
  "status": "DRAFT",
  "draftRevision": 1,
  "publishedRevision": null,
  "version": 1
}
```

草稿更新请求：

```json
{
  "expectedVersion": 1,
  "draft": {
    "target": {
      "entityCode": "work_order",
      "listKey": "supplier_open",
      "defaultFormId": "form-work-order-external"
    },
    "entryModes": ["LIST", "CREATE", "VIEW"],
    "releasePolicy": {
      "strategy": "PINNED",
      "listReleaseId": "list-release-12",
      "formReleaseId": "form-release-8"
    },
    "capabilities": [
      "LIST_QUERY",
      "SELECTION_RETURN",
      "RECORD_VIEW",
      "RECORD_CREATE"
    ],
    "fieldPolicy": {
      "visible": ["id", "code", "title", "status"],
      "queryable": ["code", "status"],
      "writable": ["title", "description"],
      "returnable": ["id", "code", "title"]
    },
    "queryPolicy": {
      "allowTotal": false,
      "maxPageSize": 100
    },
    "actionPolicy": {
      "allowed": ["view", "create", "save"]
    },
    "contextSchema": {},
    "contextBindings": [],
    "ui": {
      "showSearch": true,
      "showPagination": true,
      "showToolbar": true,
      "pageSize": 20,
      "heightMode": "AUTO"
    }
  }
}
```

`expectedVersion` 与 View 当前 `version` 不一致时返回 409
`EMBED_CONFIGURATION_VERSION_CONFLICT`，响应返回当前版本号，但不自动覆盖。

校验接口请求：

```json
{
  "expectedVersion": 2
}
```

成功时：

```json
{
  "data": {
    "valid": true,
    "resolved": {
      "entityCode": "work_order",
      "listReleaseId": "list-release-12",
      "listReleaseVersion": 12,
      "formReleaseId": "form-release-8",
      "formReleaseVersion": 8
    },
    "warnings": []
  }
}
```

校验失败返回 422 `EMBED_VIEW_VALIDATION_FAILED`，`data.violations` 最多返回 100 项：

```json
{
  "code": 422,
  "message": "Embed view validation failed",
  "errorCode": "EMBED_VIEW_VALIDATION_FAILED",
  "data": {
    "violations": [
      {
        "path": "fieldPolicy.writable[0]",
        "code": "FIELD_NOT_WRITABLE",
        "message": "字段不在已发布表单的可写字段中"
      }
    ]
  },
  "traceId": "trace-..."
}
```

发布请求：

```json
{
  "expectedVersion": 2,
  "releaseNote": "供应商门户首发"
}
```

发布事务必须重新执行完整校验，而不是信任先前 `validate` 的结果。返回：

```json
{
  "data": {
    "viewId": "ev_01K...",
    "revision": 1,
    "releaseId": "evr_01K...",
    "configHash": "64-char-lowercase-sha256",
    "publishedAt": "2026-08-26T08:00:00Z"
  }
}
```

状态更新请求：

```json
{
  "expectedVersion": 3,
  "status": "DISABLED",
  "reason": "合作方维护"
}
```

`DISABLED` 默认阻止新 Launch，并使相关 ACTIVE Session 在下次请求时失效；`RETIRED` 为终态，
不得恢复。接口返回受影响的活动会话数量。

### 9.3 Application Grant 接口

| 方法与路径 | 权限 | 说明 |
| --- | --- | --- |
| `GET /api/embed-management/v1/views/{viewId}/grants` | `system:embed:view` | 查询 View 的应用授权 |
| `PUT /api/embed-management/v1/views/{viewId}/grants/{applicationId}` | `system:embed:manage` | 创建或更新授权 |
| `POST /api/embed-management/v1/views/{viewId}/grants/{applicationId}/status` | `system:embed:manage` | 启用或禁用 |
| `POST /api/embed-management/v1/views/{viewId}/grants/{applicationId}/revoke` | `system:embed:manage` | 永久撤销 |

Upsert 请求：

```json
{
  "expectedVersion": null,
  "status": "ACTIVE",
  "identityProviderId": "eidp_01K...",
  "trustedSubjectAssertion": false,
  "allowedOrigins": [
    "https://portal.partner.example"
  ],
  "capabilityCeiling": [
    "LIST_QUERY",
    "SELECTION_RETURN",
    "RECORD_VIEW",
    "RECORD_CREATE"
  ],
  "revisionMode": "FOLLOW_ACTIVE",
  "pinnedRevision": null,
  "maxActiveSessionsPerUser": 3,
  "maxSessionSeconds": 1800,
  "launchLimitPerMinute": 60,
  "runtimeLimitPerMinute": 120,
  "maxConcurrency": 20,
  "expiresAt": null
}
```

规则：

1. `expectedVersion=null` 仅可用于首次创建；已有 Grant 时必须传当前版本。
2. `allowedOrigins` 至少一个、最多 20 个，只接受精确 Origin。
3. Grant 的 Capability 必须是 View Release Capability 的子集。
4. `revisionMode` 只允许 `FOLLOW_ACTIVE/PINNED`；为 `PINNED` 时
   `pinnedRevision` 必填且必须是已发布快照。
5. 所有配额只能收窄全局安全上限，不能通过 Grant 关闭平台硬限制。
6. `REVOKED` 为终态；需要重新合作时创建新的 Grant，而不是恢复旧授权。

### 9.4 Identity Provider 接口

| 方法与路径 | 权限 | 说明 |
| --- | --- | --- |
| `GET /api/embed-management/v1/identity-providers` | `system:embed:identity-manage` | 分页查询身份提供方 |
| `POST /api/embed-management/v1/identity-providers` | `system:embed:identity-manage` | 创建 |
| `GET /api/embed-management/v1/identity-providers/{providerId}` | `system:embed:identity-manage` | 查看脱敏配置 |
| `PATCH /api/embed-management/v1/identity-providers/{providerId}` | `system:embed:identity-manage` | 乐观锁更新 |
| `POST /api/embed-management/v1/identity-providers/{providerId}/status` | `system:embed:identity-manage` | 启用或禁用 |
| `POST /api/embed-management/v1/identity-providers/{providerId}/revoke` | `system:embed:identity-manage` | 永久撤销 |
| `POST /api/embed-management/v1/identity-providers/{providerId}/rotate-key` | `system:embed:identity-manage` | 轮换静态公钥 |

创建 `SIGNED_JWT` Provider 示例：

```json
{
  "name": "合作方 A 登录中心",
  "type": "SIGNED_JWT",
  "issuer": "https://id.partner.example",
  "audiences": ["flow-embed-launch"],
  "subjectNamespace": "partner-a",
  "algorithms": ["RS256"],
  "jwksMode": "STATIC_JWK_SET",
  "jwks": {
    "keys": [
      {
        "kty": "RSA",
        "kid": "partner-a-2026-08",
        "use": "sig",
        "alg": "RS256",
        "n": "...",
        "e": "AQAB"
      }
    ]
  },
  "clockSkewSeconds": 30,
  "maxAssertionLifetimeSeconds": 60
}
```

如使用远程 JWKS，只允许管理员配置的 HTTPS 地址，必须设置连接/读取超时、响应大小上限、
DNS/私网访问策略和缓存；不得读取断言 Header 中任意 `jku` 或 `x5u`。管理端响应不回显私钥
或历史密钥明文。

### 9.5 外部身份绑定接口

| 方法与路径 | 权限 | 说明 |
| --- | --- | --- |
| `GET /api/embed-management/v1/identity-bindings` | `system:embed:identity-manage` | 按应用、Provider、Flow 用户、状态查询；只返回 Subject Hint |
| `POST /api/embed-management/v1/identity-bindings/lookup` | `system:embed:identity-manage` | Body 中提交外部 Subject 做精确脱敏查询 |
| `POST /api/embed-management/v1/identity-bindings` | `system:embed:identity-manage` | 创建精确绑定 |
| `POST /api/embed-management/v1/identity-bindings/{bindingId}/status` | `system:embed:identity-manage` | 启用或禁用 |
| `POST /api/embed-management/v1/identity-bindings/{bindingId}/revoke` | `system:embed:identity-manage` | 永久撤销 |

创建请求：

```json
{
  "applicationId": "integration-app-1",
  "identityProviderId": "eidp_01K...",
  "externalSubject": "user-10086",
  "flowUserId": "2070000000000000001",
  "effectiveAt": "2026-08-26T08:00:00Z",
  "expiresAt": null,
  "remark": "供应商门户账号绑定"
}
```

服务端从 Provider 取得 Issuer/Namespace，不接受请求另行覆盖。创建时校验 Flow 用户存在且启用；
唯一键为 `applicationId + identityProviderId + externalSubject`。返回中外部 Subject 默认脱敏，
审计日志只记录 HMAC 指纹。原始 Subject 不得放 GET Query、URL 或访问日志；`lookup` 请求体同样
走敏感字段日志擦除。

精确查询请求：

```json
{
  "applicationId": "integration-app-1",
  "identityProviderId": "eidp_01K...",
  "externalSubject": "user-10086"
}
```

命中时只返回 `bindingId/subjectHint/flowUserId/status/effectiveAt/expiresAt`，未命中返回空结果，
不回显原始 Subject。

### 9.6 Launch 和 Session 运维接口

| 方法与路径 | 权限 | 说明 |
| --- | --- | --- |
| `GET /api/embed-management/v1/launches` | `system:embed:view` | 按应用、View、状态和时间查询 Launch 摘要 |
| `POST /api/embed-management/v1/launches/{launchId}/revoke` | `system:embed:session-revoke` | 撤销尚未消费的 Launch |
| `GET /api/embed-management/v1/sessions` | `system:embed:view` | 查询活动/历史 Session |
| `POST /api/embed-management/v1/sessions/{sessionId}/revoke` | `system:embed:session-revoke` | 撤销单个 Session |
| `POST /api/embed-management/v1/views/{viewId}/sessions/revoke` | `system:embed:session-revoke` | 撤销 View 下所有活动 Session |
| `POST /api/embed-management/v1/applications/{applicationId}/sessions/revoke` | `system:embed:session-revoke` | 撤销应用下所有活动 Session |

查询接口不得返回 Token 哈希、Launch code 哈希、完整 Context 或原始外部 Subject。撤销请求：

```json
{
  "reason": "SECURITY_INCIDENT",
  "remark": "合作方密钥疑似泄露"
}
```

撤销成功返回受影响数量；对已经终止的对象重复撤销按成功处理，并写“重复撤销”审计。

## 10. 第三方服务端对接接口

### 10.1 获取机器 Access Token（复用现有）

第三方后端继续调用现有 OAuth 2.0 Client Credentials：

```http
POST /oauth2/token
Authorization: Basic base64(client_id:client_secret)
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&scope=embed.launch
```

响应沿用 OAuth 标准格式：

```json
{
  "access_token": "machine-access-token",
  "token_type": "Bearer",
  "expires_in": 900,
  "scope": "embed.launch"
}
```

需要在现有 `IntegrationScope` 增加 `EMBED_LAUNCH("embed.launch")`，并在 Open API 安全链把
Launch 路由映射到 `SCOPE_embed.launch`。Client Secret 和机器 Token 只能保存在第三方后端，
不得下发给浏览器。

### 10.2 创建 Embed Launch（新增）

```http
POST /api/open/v1/embed-launches
Authorization: Bearer <machine-access-token>
Content-Type: application/json
X-Trace-Id: partner-20260826-001
```

请求：

```json
{
  "viewKey": "supplier-work-orders",
  "parentOrigin": "https://portal.partner.example",
  "channelId": "66f82f09-89ec-4a5a-b81b-f54f02d22262",
  "subject": {
    "type": "SIGNED_JWT",
    "assertion": "eyJhbGciOiJSUzI1NiIsImtpZCI6Ii4uLiJ9..."
  },
  "entry": {
    "mode": "LIST"
  },
  "context": {
    "supplierId": "S-10086"
  },
  "ui": {
    "locale": "zh-CN",
    "theme": "light"
  }
}
```

请求字段：

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `viewKey` | string | 是 | 1–100，必须已发布并授权给当前应用 |
| `parentOrigin` | string | 是 | 精确 `scheme://host[:port]`，必须命中 Grant |
| `channelId` | UUID/string | 是 | 16–128 个安全字符，由宿主每次启动随机生成 |
| `subject.type` | enum | 是 | V1 为 `SIGNED_JWT/TRUSTED_EXTERNAL_ID`；OIDC 值留待后续版本 |
| `subject.assertion` | string | 条件必填 | V1 短期签名 JWT，最大 16 KiB |
| `subject.namespace` | string | 可信 ID 模式必填 | 必须在 Grant 允许范围 |
| `subject.externalUserId` | string | 可信 ID 模式必填 | 1–128，不得直接传 Flow 用户 ID |
| `entry.mode` | enum | 是 | V1 仅 `LIST/CREATE/VIEW`，且必须是 Release 允许入口；`EDIT` 直接拒绝 |
| `entry.recordId` | string | VIEW 必填 | 最长 64；只作为候选记录，Runtime 读取时仍需 Flow 数据范围和行级查看权限校验 |
| `context` | object | 否 | 符合发布 JSON Schema，最大 16 KiB、最多 32 个属性 |
| `ui.locale` | string | 否 | Release 允许的语言，默认 `zh-CN` |
| `ui.theme` | string | 否 | `light/dark/system`，只影响展示 |

`TRUSTED_EXTERNAL_ID` 请求示例：

```json
{
  "subject": {
    "type": "TRUSTED_EXTERNAL_ID",
    "namespace": "erp-prod",
    "externalUserId": "user-10086"
  }
}
```

此模式只在 Grant 明确授权时接受；它代表接入应用可声明该 Namespace 中任意已绑定主体，风险
高于签名断言。

成功返回 `201 Created`：

```json
{
  "code": 201,
  "message": "created",
  "errorCode": null,
  "data": {
    "launchId": "lch_01K...",
    "embedUrl": "https://embed.flow.example.com/embed/v1/launches/lch_01K...",
    "launchCode": "base64url-256-bit-one-time-secret",
    "expiresAt": "2026-08-26T08:31:00Z",
    "view": {
      "key": "supplier-work-orders",
      "surfaceType": "LIST",
      "revision": 7
    },
    "protocolVersion": "flow-embed/1"
  },
  "traceId": "partner-20260826-001"
}
```

`launchCode` 只在该响应中返回一次，数据库只保存 SHA-256。推荐由第三方后端通过
自己的已认证接口把 `embedUrl + launchId + launchCode + expiresAt` 返回当前浏览器，再由 SDK
通过严格 Origin 的 MessageChannel 传给 iframe。无官方 SDK、但宿主手工实现完整握手协议时，
可以把 code 放在 URL fragment；页面读取后立即 `history.replaceState` 清除，禁止放在 query
参数。

### 10.3 Launch 服务端校验顺序

服务端按以下顺序执行，任何失败都不签发凭据：

1. 机器 Token 有效且包含 `embed.launch`；
2. Integration Application 为 ACTIVE，来源 CIDR、应用限流和并发租约通过；
3. View、Release 和 Application Grant 有效且未过期；
4. `parentOrigin` 规范化后精确命中 Grant；
5. 用户断言的 Issuer、Audience、算法、签名、有效期、`jti` 防重放通过；
6. 外部主体精确绑定到一个已启用 Flow 用户；
7. `entry.mode` 只属于 `LIST/CREATE/VIEW` 且被 Release 发布；CREATE/LIST 禁止 `recordId`，
   VIEW 必须携带候选 `recordId`；
8. Context、Locale 和 Theme 符合 Release；VIEW 的记录存在性和数据范围在 Runtime 读取时统一
   校验并以 404 防枚举，Launch 不建立第二套记录查询旁路；
9. 计算 Release 与 Grant 的 Capability 交集，并再次剔除 V1 禁用能力；
10. 事务写入 Launch、审计事件和一次性 `launchCodeDigest`。

Launch 创建不使用普通业务幂等缓存。调用超时后第三方重新创建即可，旧 Launch 最长 60 秒
过期；重试必须签发新的人员断言和 `jti`，不能重放旧断言。这样避免为了返回同一 secret 而
保存 launchCode 明文。

## 11. iframe 页面与 Runtime API

```http
GET /embed/v1/launches/{launchId}
```

入口页本身不消费 Launch，也不返回任何业务数据。服务端/Nginx 根据 Launch 中已校验的
`parentOrigin` 生成精确 CSP，例如：

```http
Content-Security-Policy: default-src 'self'; frame-ancestors https://portal.partner.example; object-src 'none'; base-uri 'none'
Cache-Control: no-store
Referrer-Policy: no-referrer
X-Content-Type-Options: nosniff
```

入口 HTML 只内嵌经过 JSON 安全序列化的
`launchId + expectedParentOrigin + channelId + protocolVersion`，供 Shell 在收到任何
`postMessage` 前校验父页面；不内嵌用户、Context、权限、code 或 Token。响应使用 CSP Nonce
时，Nonce 每次生成且不写入日志。

未知、过期或撤销的 `launchId` 返回通用不可嵌入错误页。生产建议使用独立
`embed.flow.example.com`，管理后台继续保持 `frame-ancestors 'none'`。

### 11.2 兑换 Embed Session

```http
POST /api/embed/v1/launches/{launchId}/exchange
Content-Type: application/json
X-Flow-Embed-Protocol: 1
```

请求：

```json
{
  "launchCode": "base64url-256-bit-one-time-secret",
  "channelId": "66f82f09-89ec-4a5a-b81b-f54f02d22262",
  "parentOrigin": "https://portal.partner.example",
  "parentNonce": "base64url-random",
  "childNonce": "base64url-random",
  "sdkVersion": "1.0.0"
}
```

该路由不使用机器 Token，也不进入普通 Flow 用户认证链；它只接受未消费、未过期且所有绑定
均匹配的 Launch。消费必须使用带状态、过期时间和 code 哈希条件的单条原子更新，两个并发
兑换恰好一个成功。

`childNonce` 由 iframe 生成并在 `ready` 中发出，`init` 必须原样回显；`parentNonce` 由
宿主生成，iframe 通过已转移的 MessagePort 回显确认。服务端校验两个 Nonce 的格式和长度并
将摘要固定到 Session，用于关联和阻止通道混用；服务端无法仅凭请求体独立证明浏览器
`event.origin`，Origin 的真实性由入口 CSP 和 Shell 对 Window `event.origin/source` 的校验
共同保证。Launch code 才是 Exchange 的一次性认证凭据。

成功返回：

```json
{
  "data": {
    "sessionId": "ems_01K...",
    "accessToken": "opaque-embed-session-token",
    "tokenType": "Bearer",
    "expiresAt": "2026-08-26T09:00:00Z",
    "idleExpiresAt": "2026-08-26T08:35:00Z",
    "heartbeatAfterSeconds": 60,
    "bootstrapUrl": "/api/embed/v1/runtime/bootstrap",
    "protocolVersion": "flow-embed/1"
  }
}
```

不提供 Refresh Token。Token 仅保存于 iframe JavaScript 内存。兑换响应丢失或页面刷新后，
Launch 已消费，宿主必须通过自己的后端创建新 Launch。

### 11.3 Bootstrap

```http
GET /api/embed/v1/runtime/bootstrap
Authorization: Bearer <embed-session-token>
X-Flow-Embed-Protocol: 1
```

响应：

```json
{
  "data": {
    "session": {
      "id": "ems_01K...",
      "expiresAt": "2026-08-26T09:00:00Z",
      "idleExpiresAt": "2026-08-26T08:35:00Z"
    },
    "actor": {
      "displayName": "张三"
    },
    "view": {
      "key": "supplier-work-orders",
      "name": "供应商工单",
      "surfaceType": "LIST",
      "revision": 7,
      "entryMode": "LIST"
    },
    "capabilities": [
      "LIST_QUERY",
      "SELECTION_RETURN",
      "RECORD_VIEW",
      "RECORD_CREATE"
    ],
    "ui": {
      "locale": "zh-CN",
      "theme": "light",
      "showSearch": true,
      "showPagination": true,
      "showToolbar": true,
      "pageSize": 20,
      "heightMode": "AUTO"
    },
    "limits": {
      "maxPageSize": 100,
      "maxPayloadBytes": 1048576,
      "maxSelectionSize": 100
    }
  }
}
```

`actor` 只返回渲染所需最小信息。不得返回 Application Secret、内部权限码、固定过滤表达式、
数据范围、Provider/JWK、Token 哈希、发布解析 Token 或未公开字段。

### 11.4 获取外部投影 Schema

```http
GET /api/embed/v1/runtime/schema
Authorization: Bearer <embed-session-token>
```

列表响应示例：

```json
{
  "data": {
    "view": {
      "key": "supplier-work-orders",
      "surfaceType": "LIST",
      "revision": 7
    },
    "entity": {
      "code": "work_order",
      "name": "工单"
    },
    "list": {
      "selection": {
        "mode": "SINGLE",
        "valueField": "id",
        "returnableFields": ["code"]
      },
      "pagination": {
        "allowTotal": false,
        "maxPageSize": 100
      },
      "columns": [
        {
          "code": "code",
          "label": "工单号",
          "type": "TEXT",
          "width": 180,
          "sortable": false
        },
        {
          "code": "status",
          "label": "状态",
          "type": "SELECT",
          "width": 120,
          "sortable": false,
          "options": [
            {"label": "处理中", "value": "PROCESSING"}
          ]
        }
      ],
      "filters": [
        {
          "code": "code",
          "label": "工单号",
          "type": "TEXT",
          "operator": "CONTAINS"
        },
        {
          "code": "status",
          "label": "状态",
          "type": "SELECT",
          "operator": "EQ",
          "options": [
            {"label": "处理中", "value": "PROCESSING"}
          ]
        }
      ]
    },
    "form": null,
    "actions": [
      {
        "key": "view",
        "label": "查看",
        "placement": "ROW",
        "kind": "NAVIGATION",
        "transport": "LOCAL_FORM",
        "recordMode": "CURRENT",
        "selectionMode": "NONE",
        "dataSchema": null,
        "requiresRecordVersion": false,
        "inputSchema": null,
        "idempotencyRequired": false
      },
      {
        "key": "create",
        "label": "新建",
        "placement": "TOOLBAR",
        "kind": "NAVIGATION",
        "transport": "LOCAL_FORM",
        "recordMode": "NONE",
        "selectionMode": "NONE",
        "dataSchema": null,
        "requiresRecordVersion": false,
        "inputSchema": null,
        "idempotencyRequired": false
      }
    ]
  }
}
```

该 DTO 是新建的 External Projection，不能直接序列化现有 `EntityListSchemaDTO`。投影层必须
移除权限码、固定过滤器、内部 Provider、运行时上下文 Token、接口操作配置、脚本、未审核
组件和未发布动作。

`list.columns` 是 iframe 内的展示白名单，`list.selection.returnableFields` 是
`selection.changed` 发给宿主系统的值字段白名单，两者不能混用。运行时只返回
`fieldPolicy.returnable ∩ 实际发布展示列`；记录 `id` 作为选择对象的独立字段返回，
不需要进入 `returnableFields`。前端必须从已规范化的行对象再次按该白名单投影，禁止把
整行 `values` 直接发送给宿主。

所有列表、表单和详情响应使用同一个 `ExternalActionDescriptor`，字段含义如下：

| 字段 | 允许值/含义 |
| --- | --- |
| `key/label/placement` | 稳定动作 Key、展示文案、`TOOLBAR/ROW/FORM` |
| `kind` | `NAVIGATION/MUTATION/SELECTION` |
| `transport` | V1 列表只投影 `LOCAL_FORM`，CREATE 表单只投影 `RECORD_CREATE`；`LOCAL_SELECT` 由列表选择协议处理，`RECORD_UPDATE/ACTION_API` 为后续保留值且当前不会下发 |
| `recordMode` | `NONE/CURRENT/SELECTION`，决定是否携带当前记录或选择集 |
| `selectionMode` | `NONE/SINGLE/MULTIPLE` |
| `dataSchema` | 动作可接受的记录字段 Schema；无记录正文时为 null |
| `inputSchema` | 动作额外输入 Schema；无额外输入时为 null |
| `requiresRecordVersion` | 是否必须携带 `expectedRecordVersion` |
| `idempotencyRequired` | 是否必须携带 `Idempotency-Key` |
| `enabled/disabledReason` | 当前表单级可用状态；行级状态仍在每条记录的 `actions` Capability Map 中返回 |

前端只按封闭 `transport` 选路由，不执行服务端下发 URL：V1 的 `LOCAL_FORM` 只在 iframe 内
完成 LIST→CREATE/VIEW→BACK 导航，`LOCAL_SELECT` 只走已投影的 `selection.changed`，
`RECORD_CREATE` 只调用固定记录创建接口。`RECORD_UPDATE/ACTION_API` 当前不会由 Schema 返回，
相应 HTTP 路由也未注册。未知枚举 Fail Closed，不能回退到内部 Event、Provider 或任意 URL。

### 11.5 列表查询

```http
POST /api/embed/v1/runtime/list/query
Authorization: Bearer <embed-session-token>
Content-Type: application/json
```

请求：

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": [
    {"field": "code", "value": "WO-2026"},
    {"field": "status", "value": "PROCESSING"}
  ]
}
```

V1 不允许客户端传 `scene`、`releaseId`、`context`、`fixedFilters` 或任意排序表达式。排序
使用已发布列表默认规则；如后续开放排序，只能基于 Schema 显式声明的字段和方向扩展。

`filters` 是无操作符的强类型外部 DTO。`EQ/CONTAINS/GT/GTE/LT/LTE` 使用
`{"field": ..., "value": ...}`，`IN` 使用 `values`，`BETWEEN` 使用同时包含
`start/end` 的 `range`。客户端不得提交 `operator`，也不得提交 Entity 内部的
`field_op/field_start/field_end` 键；服务端只按不可变发布 Schema 中该字段的 operator
映射为内部条件。未发布字段、重复字段、值形状与发布 operator 不一致时统一拒绝请求。

服务端按以下顺序组合条件：

```text
Embed View 固定条件
  AND Launch Context 绑定条件
  AND Flow 用户数据范围
  AND 已发布列表规则
  AND 客户端允许的查询字段
```

客户端过滤条件只能进一步缩小结果，不能覆盖前四类条件。返回：

```json
{
  "data": {
    "items": [
      {
        "id": "2080000000000000001",
        "recordVersion": null,
        "values": {
          "code": "WO-20260826-001",
          "title": "设备维修",
          "status": "PROCESSING"
        },
        "meta": {
          "updatedAt": "2026-08-26T08:20:00Z"
        },
        "actions": {
          "view": {
            "visible": true,
            "enabled": true,
            "reason": null
          }
        }
      }
    ],
    "hasMore": false,
    "pageNum": 1,
    "pageSize": 20
  }
}
```

只有 `fieldPolicy.visible` 字段可进入 `values`；行级 Action Capability 由现有能力服务计算后
再与 Release 白名单求交。默认只返回 `hasMore`；只有 View 显式设置
`queryPolicy.allowTotal=true` 时才额外返回 `total`，以避免敏感总量侧信道。

### 11.6 解析表单

```http
GET /api/embed/v1/runtime/form?mode=CREATE
GET /api/embed/v1/runtime/form?mode=VIEW&recordId=2080000000000000001
Authorization: Bearer <embed-session-token>
```

V1 `mode` 只接受 `CREATE/VIEW`：CREATE 禁止 `recordId`，VIEW 必须有 `recordId`。FORM 入口
必须与 Launch 固定模式一致；LIST 入口可以通过当前 External Schema 的 `LOCAL_FORM` 动作在
iframe 内进入 CREATE 或 VIEW。`recordId` 只能来自 Launch 固定记录或当前已投影列表行，并仍
通过固定 List Release、Flow 数据范围和行级查看能力校验；浏览器不能提交 form/entity/release。

CREATE 响应示例：

```json
{
  "code": 200,
  "message": "ok",
  "errorCode": null,
  "data": {
    "mode": "CREATE",
    "record": null,
    "form": {
      "title": "新建工单",
      "layout": {"type": "GRID"},
      "fields": [
        {
          "code": "category",
          "label": "工单类型",
          "type": "SELECT",
          "required": true,
          "readOnly": false,
          "hidden": false,
          "defaultValue": null,
          "validation": {},
          "options": [{"label": "设备维修", "value": "EQUIPMENT_REPAIR", "disabled": false}],
          "optionSource": null,
          "lookupSource": null,
          "layout": {"span": 24},
          "fieldState": {"visible": true, "writable": true}
        }
      ],
      "returnableFields": ["category"],
      "actions": [
        {
          "key": "save",
          "label": "保存",
          "placement": "FORM",
          "kind": "MUTATION",
          "transport": "RECORD_CREATE",
          "recordMode": "NONE",
          "selectionMode": "NONE",
          "dataSchema": {
            "type": "object",
            "properties": {
              "category": {"type": "string", "maxLength": 64}
            },
            "additionalProperties": false
          },
          "inputSchema": null,
          "enabled": true,
          "disabledReason": null,
          "requiresRecordVersion": false,
          "idempotencyRequired": true
        }
      ]
    }
  },
  "traceId": "trace-..."
}
```

表单投影复用已发布表单解析和联动结果，但不返回设计态配置、内部组件实现名、任意脚本、
任意远程数据源配置或完整事件执行信息。CREATE 只可能返回内建 `save`，其
`transport=RECORD_CREATE`、`requiresRecordVersion=false`、`idempotencyRequired=true`；VIEW
不返回保存动作。任意字段 `validationRules.pattern` 在 Embed View 发布时阻断，历史或篡改快照
在 Runtime 再次 Fail Closed；V1 仅执行受控长度、数值、格式和枚举校验。

#### 11.6.1 CREATE 联动只读重算

CREATE 表单的可见、可写、必填等状态可能依赖当前草稿。iframe 使用下面的专用只读接口把部分
草稿交给服务端重算，不能在浏览器自行解释内部联动表达式：

```http
POST /api/embed/v1/runtime/form/evaluations
Authorization: Bearer <embed-session-token>
Content-Type: application/json
```

```json
{
  "data": {
    "status": "CLOSED"
  }
}
```

请求顶层严格只允许 `data`，最多 100 个字段；禁止携带 entity、form、release、record、Flow 用户、
Context 或任意 Provider 坐标。接口只允许 `CREATE + RECORD_CREATE`，`data` 可以是部分草稿，但键
必须属于固定 Embed Release 的 `fieldPolicy.writable`，值只能使用当前字段类型允许的安全
scalar/array 形状。服务端从 Session 恢复固定 entity/form/release/context，并在联动求值前以后端
`FORCED_FORM_VALUE` 覆盖同名浏览器草稿。

本接口不执行 required 或最终提交校验，不申请幂等 Claim，不写业务数据、不触发流程/动作，也不
新增宿主 postMessage。成功以现有 `FormResult` 包络返回重算后的 CREATE 表单，`mode=CREATE`、
`record=null`；最终保存仍必须调用第 11.8 节创建接口，由服务端基于最终有效值完整重算和校验。
请求形状或字段非法返回 400 `INVALID_REQUEST`，入口/能力不允许返回 403
`EMBED_OPERATION_NOT_ALLOWED`，固定快照损坏或运行依赖失败返回 503
`EMBED_RUNTIME_UNAVAILABLE`。

#### 11.6.2 大型静态选项分页

当前 options 路由不是通用动态数据源代理。它只分页同一不可变 Form Release 内超过 100 项的
静态选项：

```http
POST /api/embed/v1/runtime/form/fields/{fieldCode}/options/query
Authorization: Bearer <embed-session-token>
Content-Type: application/json
```

```json
{
  "mode": "CREATE",
  "keyword": "维修",
  "dependencies": {},
  "pageNum": 1,
  "pageSize": 50
}
```

`mode` 必填且只允许 CREATE/VIEW；VIEW 必须同时提供当前导航记录的精确 `recordId`，CREATE
禁止 `recordId`。这些值只描述 LIST Session 当前的本地表单状态，Facade 仍从 Session 恢复固定
form/entity/release，并重新校验 entryModes、Capability、记录与 DataScope。当前静态实现的
`dependencies` 必须为空。任何数据源绑定、`provider/service/operation/url`、HTTP/SQL 连接器或
UI Event 在发布和运行时均拒绝，不允许回退到内部接口。

响应：

```json
{
  "data": {
    "items": [
      {
        "label": "设备维修",
        "value": "EQUIPMENT_REPAIR",
        "disabled": false
      }
    ],
    "hasMore": false,
    "pageNum": 1,
    "pageSize": 50
  }
}
```

#### 11.6.3 引用字段候选查询

引用候选 HTTP 路由和封闭 DTO 已注册，以固定未来契约，但 **V1 不提供可用的 Lookup 查询能力**：

```http
POST /api/embed/v1/runtime/form/fields/{fieldCode}/lookups/query
Authorization: Bearer <embed-session-token>
Content-Type: application/json
```

```json
{
  "mode": "VIEW",
  "recordId": "2080000000000000001",
  "keyword": "三号",
  "filters": {},
  "pageNum": 1,
  "pageSize": 20
}
```

`mode/recordId` 规则与 options 完全相同，且仍不是目标坐标。受支持的发布链会把
`REFERENCE/MULTI_REFERENCE/LOOKUP/MULTI_LOOKUP/USER/DEPT/ROLE/GROUP` 表单字段整体标记为
`UNTRUSTED_COMPONENT` 并阻断发布，表单 Schema 只会返回 `lookupSource=null`，不会发布可用的
非空 Lookup Source；当前实体适配器也没有固定候选 List Release 和 Lookup External Projection。
直接查询不可用字段时返回 403
`EMBED_OPERATION_NOT_ALLOWED`；历史或绕过发布器的可写 Lookup 快照在表单解析阶段以 503
`EMBED_RUNTIME_UNAVAILABLE` Fail Closed。本 OpenAPI 不声明 200 成功响应；未来只有在强类型
只读 Registry、固定候选 Release、数据范围和输出投影全部实现后才能扩展。

### 11.7 获取记录详情

```http
GET /api/embed/v1/runtime/records/{recordId}
Authorization: Bearer <embed-session-token>
```

返回当前 View 可见字段、字段状态和可执行动作。记录不存在与当前 Flow 用户无权访问统一返回
404 `EMBED_RESOURCE_NOT_FOUND`。

当前 V1 动态实体尚无通用 `record_version`，因此 `recordVersion=null` 且不返回 ETag。未来只有
第 16.4 节全链路完成后，非空版本才配套返回 `ETag: "rv-<version>"`。当前 Entity 表单 VIEW
适配器没有对外动作来源，因而 `actions` 固定为空对象；不能根据内部表单按钮自行推导动作。

```json
{
  "data": {
    "record": {
      "id": "2080000000000000001",
      "recordVersion": null,
      "values": {
        "code": "WO-20260826-001",
        "title": "设备维修",
        "status": "PROCESSING"
      },
      "meta": {
        "createdAt": "2026-08-25T08:20:00Z",
        "updatedAt": "2026-08-26T08:20:00Z"
      }
    },
    "fieldStates": {
      "title": {
        "visible": true,
        "readOnly": true,
        "required": true
      }
    },
    "actions": {}
  }
}
```

### 11.8 创建记录

**当前状态：V1 已实现。** 该接口是 V1 唯一开放的记录写入口；是否可调用仍取决于
View Release、Grant、Flow 用户权限、表单字段策略和幂等校验的交集。

```http
POST /api/embed/v1/runtime/records
Authorization: Bearer <embed-session-token>
Idempotency-Key: partner-order-create-20260826-001
Content-Type: application/json
```

请求：

```json
{
  "data": {
    "title": "设备维修",
    "description": "三号生产线设备异常"
  },
  "clientMutationId": "host-request-10001"
}
```

浏览器只能提交 `fieldPolicy.writable` 中的字段。服务端忽略或拒绝未知字段，并在进入现有表单
提交服务前注入 Launch Context 对应的强制字段。浏览器不能传实体、表单、Release、创建人、
组织、权限、流程定义或内部运行时 Token。

唯一性复用现有已发布表单提交语义，只保证**当前入口中适用且未忽略的已发布表单唯一规则**。
没有唯一规则、规则被忽略或条件对当前数据不适用的其他写入口不共享该约束，因此不能把该机制
表述为实体字段全局唯一；底层数据库或业务状态冲突仍统一映射为 409
`EMBED_RECORD_CONFLICT`。

成功返回 `201 Created`：

```http
Location: /api/embed/v1/runtime/records/2080000000000000001
```

```json
{
  "data": {
    "receiptId": "eor_01K...",
    "record": {
      "id": "2080000000000000001",
      "recordVersion": null,
      "values": {
        "code": "WO-20260826-001",
        "title": "设备维修"
      },
      "meta": {
        "createdAt": "2026-08-26T08:30:00Z"
      }
    },
    "effects": [],
    "clientMutationId": "host-request-10001"
  }
}
```

V1 的 `recordVersion` 固定为 `null` 且响应不返回 ETag，第三方也不能传入版本。未来
`record_version`/ETag 契约只见第 11.9、16.4 节，不能作为当前接入依据。

创建并发起流程不通过布尔参数绕过授权；V1 没有 `saveAndStart` 或通用动作接口。后续版本只有
在强类型动作契约独立实现并重新发布 Capability 后才能开放。

### 11.9 更新记录

> **后续目标契约，V1 不可用。** 当前版本不注册该 PATCH 路由，发布校验拒绝
> `RECORD_UPDATE`，Bootstrap/Schema 也不会投影编辑能力。以下请求/响应保留用于
> `record_version` 全链路完成后的兼容设计，不能作为当前接入依据。

```http
PATCH /api/embed/v1/runtime/records/{recordId}
Authorization: Bearer <embed-session-token>
Idempotency-Key: partner-order-update-2080000000000000001-v17
If-Match: "rv-17"
Content-Type: application/json
```

请求：

```json
{
  "expectedRecordVersion": 17,
  "data": {
    "description": "更换零件后复测"
  },
  "clientMutationId": "host-request-10002"
}
```

Header 和 Body 版本必须一致。更新 SQL 必须形如：

```sql
UPDATE <dynamic_entity_table>
   SET ..., record_version = record_version + 1
 WHERE id = ?
   AND record_version = ?;
```

更新 0 行时重新检查可见性：不可见/不存在返回 404，可见但版本已变化返回 409
`RECORD_VERSION_CONFLICT`。不能用 `update_time` 代替乐观锁。

成功返回 `200 OK` 和新 ETag：

```http
ETag: "rv-18"
```

```json
{
  "data": {
    "receiptId": "eor_01K...",
    "record": {
      "id": "2080000000000000001",
      "recordVersion": 18,
      "values": {
        "description": "更换零件后复测"
      }
    },
    "effects": [],
    "clientMutationId": "host-request-10002"
  }
}
```

版本冲突响应只对仍可见的记录返回当前版本：

```json
{
  "code": 409,
  "message": "Record has been changed",
  "errorCode": "RECORD_VERSION_CONFLICT",
  "data": {
    "currentRecordVersion": 18
  },
  "traceId": "trace-..."
}
```

当前动态实体表没有通用 `record_version`，因此完成第 16.4 节的乐观锁建设前：

- View 发布校验不得允许 `RECORD_UPDATE`；
- Bootstrap/Schema 不返回更新能力；
- 当前版本不注册更新路由；未来注册后，在能力未开放时返回 403 `EMBED_OPERATION_NOT_ALLOWED`；
- 不应先上线一个可能覆盖他人数据的临时实现。

### 11.10 执行动作

> **后续目标契约，V1 不可用。** 当前版本不注册 Action 路由，发布校验同时拒绝
> `ACTION_EXECUTE` 和 `PROCESS_START`；因此 `submit`、`saveAndStart` 等动作均不能由
> Embed V1 调用。以下内容只定义未来启用前必须满足的接口边界。

```http
POST /api/embed/v1/runtime/actions/{actionKey}
Authorization: Bearer <embed-session-token>
Idempotency-Key: partner-order-submit-2080000000000000001-v17
Content-Type: application/json
```

请求：

```json
{
  "recordId": "2080000000000000001",
  "expectedRecordVersion": 17,
  "input": {
    "comment": "确认提交"
  },
  "confirmation": {
    "confirmed": true
  },
  "clientMutationId": "host-request-10003"
}
```

规则：

1. `actionKey` 必须来自当前 View Release，且对应 Descriptor 的 `transport=ACTION_API`；不能
   接受 `eventCode/serviceId/operationCode`。
2. `recordId`、`data`、`selection` 和 `input` 是否允许由动作的
   `recordMode/selectionMode/dataSchema/inputSchema` 决定；未声明的 Payload 字段必须拒绝，
   不能静默忽略。
3. 列表批量动作逐条执行数据范围校验，V1 建议选择上限 100。
4. 需要记录版本的动作必须传 `expectedRecordVersion`；通用乐观锁未完成前不发布此类动作。
5. `view/select` 是 iframe 内交互，不调用动作 API；普通 `save` 只走
   `POST/PATCH /runtime/records`，避免两套保存契约。
6. V1 动作 API 只映射显式强类型 Handler，例如 `saveAndStart/submit`；`saveAndStart` 可按
   发布 Schema 接受 `data`，并在一个事务中保存和启动流程。
7. 任意脚本、任意连接器、导出、删除和批量删除默认拒绝。

同步成功：

```json
{
  "data": {
    "receiptId": "eor_01K...",
    "action": {
      "key": "submit",
      "status": "COMPLETED"
    },
    "record": null,
    "changedRecords": [
      {
        "recordId": "2080000000000000001"
      }
    ],
    "effects": [],
    "clientMutationId": "host-request-10003"
  }
}
```

V1 不开放异步动作，不返回 `202/asyncOperation`；动作必须在受控超时内同步完成。后续只有在
增加受 Session/View 授权的 Operation Status API、持久化状态机和签名 Webhook 后，才能开放
异步动作。postMessage 只用于当前页面交互提示，不是可靠结果通知。

### 11.11 心跳、查询会话与退出

```http
GET /api/embed/v1/session
Authorization: Bearer <embed-session-token>
```

返回会话状态和到期时间，不返回 Token。

```http
POST /api/embed/v1/session/heartbeat
Authorization: Bearer <embed-session-token>
Content-Type: application/json

{
  "visible": true,
  "clientTime": "2026-08-26T08:31:00Z"
}
```

响应：

```json
{
  "data": {
    "status": "ACTIVE",
    "idleExpiresAt": "2026-08-26T08:36:00Z",
    "absoluteExpiresAt": "2026-08-26T09:00:00Z",
    "nextHeartbeatAfterSeconds": 60
  }
}
```

心跳只能把 idle expiry 延长到 absolute expiry 以内；后台不可见时 SDK 应暂停高频心跳。
`clientTime` 和 `visible` 仅用于诊断/节流，服务端到期判断只使用服务端时钟，不能信任客户端
时间。

```http
DELETE /api/embed/v1/session
Authorization: Bearer <embed-session-token>
```

成功和已退出都返回 `204 No Content`。为了支持网络重试，专用 Logout Guard 只允许该 Token
命中 ACTIVE 或 LOGGED_OUT Session：ACTIVE 原子终止并释放 slot，LOGGED_OUT 只做无副作用
重放；它不能调用任何 Runtime 业务服务。Token 对其他接口随即永久失效；关闭 iframe 时 SDK
尽力发送，但服务端仍以超时清理为最终兜底。

## 12. iframe SDK 与 postMessage 协议

### 12.1 SDK 职责

建议发布无框架依赖的 `@flow/embed-sdk`，React/Vue 包装层只做生命周期适配。SDK 负责：

- 创建和销毁 iframe；
- 以精确 Origin 和 MessageChannel 传递一次性 Launch code；
- 协议版本、channel、nonce、source 和消息大小校验；
- 自动高度、主题、语言和宿主事件；
- Session 过期时触发宿主重新 Launch；
- 不保存机器 Token、普通 Flow Token 或 Embed Token。

### 12.2 宿主对接示例

第三方后端先封装自己的业务接口：

```http
POST /partner-api/work-orders/flow-embed-launch
Cookie: <第三方自己的登录会话>

{
  "entry": {"mode": "LIST"},
  "channelId": "66f82f09-89ec-4a5a-b81b-f54f02d22262"
}
```

该后端从自己的登录会话确定外部 Subject，签名断言并调用 Flow Launch API。第三方浏览器
不得自己提交“我要代表哪个用户”给一个无校验的代理接口；`viewKey` 建议由该业务路由固定，
`supplierId/projectId` 等安全 Context 必须由后端根据登录会话和业务授权派生，不能原样信任
浏览器值。该代理接口必须使用第三方现有登录鉴权与 CSRF 防护，并返回
`Cache-Control: no-store`。

宿主前端：

```javascript
const channelId = crypto.randomUUID()

const launch = await fetch('/partner-api/work-orders/flow-embed-launch', {
  method: 'POST',
  credentials: 'same-origin',
  headers: {'Content-Type': 'application/json'},
  body: JSON.stringify({
    entry: {mode: 'LIST'},
    channelId
  })
}).then(response => response.json())

const widget = FlowEmbed.mount({
  container: document.querySelector('#flow-work-orders'),
  embedUrl: launch.embedUrl,
  launchId: launch.launchId,
  launchCode: launch.launchCode,
  channelId,
  targetOrigin: 'https://embed.flow.example.com',
  height: {mode: 'auto', min: 480, max: 1200},
  onEvent(event) {
    if (event.type === 'selection.changed') {
      updateSelectedWorkOrder(event.payload.selection)
    }
    if (event.type === 'session.expired') {
      showReopenButton()
    }
  }
})

// 页面卸载或组件销毁时调用。
widget.destroy()
```

`launchCode` 在传输给 iframe 后立即从宿主 SDK 内存中清除。SDK 不写入浏览器存储和分析日志。
SDK 必须校验 `embedUrl` 为 HTTPS、Origin 与显式 `targetOrigin` 完全一致且 Path 属于
`/embed/v1/launches/`；不能直接信任合作方接口返回的任意 URL。

### 12.3 握手时序

```mermaid
sequenceDiagram
    participant H as 宿主 SDK
    participant F as Flow iframe
    participant A as Embed API

    H->>F: 加载 embedUrl（不含 secret）
    F-->>H: ready(channelId, childNonce)
    H->>H: 校验 event.source、event.origin、channelId
    H->>F: init(launchId, launchCode, childNonce, parentNonce) + MessagePort
    F->>F: 校验 source、Origin、channelId、childNonce
    F-->>H: MessagePort init.ack(childNonce, parentNonce)
    F->>A: exchange(code, channel, nonces)
    A-->>F: Embed Session Token
    F->>A: bootstrap + schema
    A-->>F: 受控运行时配置
    F-->>H: initialized(view, capabilities)
```

iframe 的 `ready` 只携带非敏感信息。父页面必须以
`targetOrigin=https://embed.flow.example.com` 发送，不得使用 `*`。iframe 只接受
`event.source === window.parent` 且 `event.origin === launch.parentOrigin` 的初始化消息。

### 12.4 消息 Envelope

```json
{
  "protocol": "flow-embed/1",
  "type": "selection.changed",
  "launchId": "lch_01K...",
  "channelId": "66f82f09-89ec-4a5a-b81b-f54f02d22262",
  "messageId": "base64url-random-message-id",
  "requestId": null,
  "timestamp": "2026-08-26T08:32:00Z",
  "childNonce": "base64url-child-nonce",
  "parentNonce": "base64url-parent-nonce",
  "payload": {}
}
```

要求：

- 单条消息默认最大 64 KiB；
- `messageId` 每个 channel 唯一，用于去重和诊断；
- 命令使用 `requestId`，iframe 以 `ack` 或 `error` 回应；
- 未知可选事件可忽略，未知命令必须返回 `UNSUPPORTED_MESSAGE_TYPE`；
- Payload 必须经过 JSON Schema 校验，禁止原型污染字段；
- 选择和保存事件按 View `outputPolicy/fieldPolicy.returnable` 再投影。

Window 握手消息必须校验 `event.origin` 和 `event.source`。MessagePort 建立后，Port 消息
本身没有可靠的 `origin/source` 字段，因此不再做不存在的 Origin 判断，而是校验已绑定的
Port、`protocol/channelId/childNonce/parentNonce/messageId` 和 Payload Schema。

### 12.5 iframe 发给宿主的事件

| type | 当前状态 | 何时触发 | 核心 Payload |
| --- | --- | --- | --- |
| `ready` | 已实现，Window 握手 | Shell 已加载、尚未兑换 | `launchId, channelId, childNonce, supportedVersions` |
| `init.ack` | 已实现，MessagePort 握手 | iframe 接受唯一 Port | `launchId, channelId, childNonce, parentNonce` |
| `ack` | 已实现 | 接受宿主白名单命令 | `command` |
| `initialized` | 已实现 | Session 与当前 Surface 就绪 | `viewKey, surfaceType, capabilities` |
| `resize` | 已实现 | 内容高度变化 | `height`，SDK 仍执行 min/max 限制 |
| `selection.changed` | 已实现 | 列表选择变化 | `selection[{id, values}]`，`values` 仅含 Schema 的 `selection.returnableFields` |
| `form.saved` | 已实现，仅 RECORD_CREATE | 创建保存完成 | `receiptId, record{id, values}, clientMutationId` |
| `session.expired` | 已实现 | 会话过期或撤销 | `reason, relaunchRequired=true` |
| `error` | 已实现 | 可展示/不可恢复错误 | `errorCode, message, traceId, recoverable` |
| `navigation.request` | **Deferred** | SDK 只有预留 Schema，当前 iframe Bridge 不发送 | 预留 `target, recordId` |
| `action.started/action.completed` | **Deferred** | 当前没有 Action Runtime 来源 | 仅 SDK 预留 Schema |
| `close.requested` | **Deferred** | 当前 iframe Bridge 不发送 | 仅 SDK 预留 Schema |

`values` 只包含 Returnable 字段；postMessage 不是可靠业务消息总线，浏览器关闭时事件可能丢失。
当前 LIST→CREATE/VIEW→BACK 是 iframe 内部状态导航，不向宿主发送 `navigation.request`，也不新增
可接受 form/entity/release/URL 的导航接口。SDK 预留的目标枚举也只允许
`LIST/CREATE/VIEW/BACK`，V1 不包含 EDIT；未来启用事件时仍不得自动修改宿主 URL、跳转顶层窗口
或打开任意 URL。

### 12.6 宿主发给 iframe 的命令

| type | 用途 | 说明 |
| --- | --- | --- |
| `init` | 传递 Launch code 并建立 MessagePort | 每个 Launch 仅一次 |
| `refresh` | 重新加载当前列表/记录 | 不改变 View 和安全 Context |
| `set-theme` | 切换 `light/dark/system` | 仅展示 |
| `set-locale` | 切换 Release 支持语言 | 仅展示 |
| `focus` | 聚焦 iframe 首个可交互元素 | 无业务副作用 |
| `destroy` | 主动退出并销毁 | iframe 尽力注销 Session |

V1 不提供 `set-context`、`set-user`、`set-record-id` 或 `execute-arbitrary-action`。改变用户、
记录入口或业务 Context 必须创建新的 Launch。

### 12.7 无 SDK 的手工协议兼容

不使用官方 SDK 时，第三方仍必须自行实现第 12.3–12.6 节的完整 Window/MessagePort 协议；
一个只有 `<iframe>` 标签、没有宿主 JavaScript 的页面不能完成安全握手。

手工实现时可以将一次性 code 放到 URL fragment：

```text
https://embed.flow.example.com/embed/v1/launches/lch_01K...#code=...
```

Embed Shell 在任何网络请求前读取 fragment，立即清除地址栏中的 secret，随后仍执行严格
Origin/nonce 握手；宿主仍需响应 `ready` 并证明自己是登记 Origin。此方式不提供官方 SDK 的
类型定义、命令确认和自动重启能力，只建议用于 PoC；正式对接优先使用 SDK + MessageChannel。

## 13. 全流程对接时序

### 13.1 管理配置与发布

```mermaid
sequenceDiagram
    participant A as Flow 管理员
    participant M as Embed 管理 API
    participant L as 列表/表单发布服务
    participant D as 数据库
    participant S as 系统审计

    A->>M: 创建 Embed View 草稿
    M->>D: 保存 draft_config + lock_version
    A->>M: 配置 Provider、身份绑定、Grant、Origin
    M->>D: 保存安全配置
    A->>M: validate(expectedVersion)
    M->>L: 解析实体、列表、表单及 FOLLOW_ACTIVE/PINNED Release
    L-->>M: 发布快照与字段/动作能力
    M-->>A: violations / warnings
    A->>M: publish(expectedVersion)
    M->>L: 事务内重新解析和校验
    M->>D: 写 immutable Embed View Release
    M->>S: 记录发布审计
    M-->>A: releaseId + revision + configHash
```

配置顺序建议：

1. 创建/确认现有 Integration Application，并授予 `embed.launch` Scope；
2. 创建 Identity Provider；
3. 批量或逐个建立外部人员到 Flow 用户的精确绑定；
4. 创建 Embed View，选择已发布列表和表单；
5. 配置字段、动作、Context、返回字段和 UI 策略；
6. 校验并发布 View Release；
7. 给 Application 配置 Grant 和精确 Origin；
8. 使用测试用户完成联调，通过后启用生产 Grant。

### 13.2 用户启动与 Flow 鉴权

```mermaid
sequenceDiagram
    participant U as 第三方用户
    participant P as 第三方页面
    participant B as 第三方后端
    participant O as Flow OAuth/Open API
    participant I as Embed 身份服务
    participant F as Flow iframe
    participant R as Embed Runtime
    participant E as 现有实体/表单服务

    U->>P: 已登录第三方平台
    P->>B: 请求打开 viewKey + 业务 Context
    B->>B: 从自己的会话确定 externalSubject
    B->>B: 签发 60 秒用户断言
    B->>O: Client Credentials 获取机器 Token
    B->>O: POST embed-launches
    O->>I: 校验应用、Grant、Origin、断言和防重放
    I->>I: 精确映射 externalSubject -> flowUserId
    I->>E: 以该 Flow 用户预校验入口数据权限
    O-->>B: embedUrl + 一次性 launchCode
    B-->>P: 返回本次 Launch
    P->>F: 创建 iframe，严格 Origin 握手
    P->>F: MessageChannel 传递一次性 code
    F->>R: exchange
    R-->>F: 受限 Embed Session Token
    F->>R: bootstrap/schema/query
    R->>R: 恢复 EmbedContext
    R->>R: 设置 UserContext(flowUserId)
    R->>E: 调用现有运行态
    E-->>R: 按 Flow 用户权限和数据范围返回
    R->>R: 再执行 View 字段/动作投影
    R-->>F: 外部 DTO
```

这回答了“iframe 启动后如何对应 Flow 用户”：映射在第三方后端创建 Launch 时已经完成，Session
中固定 `flowUserId`；每个 Runtime 请求由专用过滤器恢复该用户的 `UserContext`，因此现有
Flow 权限、组织、数据范围和行级动作继续生效。宿主前端没有选择或切换 Flow 用户的能力。

### 13.3 列表进入表单

1. iframe 调用 Bootstrap 和 Schema。
2. 列表查询由服务端注入 View 固定过滤、Context 和 Flow 数据范围。
3. 用户点击 Schema 中 `transport=LOCAL_FORM` 的“查看”或“新建”时，前端只在本地选择
   `VIEW + 已投影行 recordId` 或 `CREATE`；V1 没有 EDIT。
4. Facade 以 Session 中的 `RECORD_VIEW/RECORD_CREATE`、entryModes 和固定 List Release 复验；
   VIEW 同时执行当前 Flow 用户的数据范围与行级查看能力，不存在/无权统一 404。
5. Facade 用 Session 固定的 `formReleaseId/formReleaseVersion` 调用表单解析服务，浏览器不传这些坐标。
6. 表单字段状态由已发布表单联动规则、Flow 权限和 View 字段策略求交。
7. `BACK` 恢复先前列表查询、分页和选择状态并保持同一 Session；不会发送宿主导航事件，切换到
   View 未发布的其他实体或表单会被拒绝。

### 13.4 创建，以及后续更新/动作

当前 V1 只执行下图中的创建路径（`POST /runtime/records`）。图中的更新、动作和
`recordVersion` 分支是后续目标事务模型；在 V1 发布与运行时均关闭。

```mermaid
sequenceDiagram
    participant F as iframe
    participant R as Embed Facade
    participant I as 幂等服务
    participant P as 权限/表单/数据服务
    participant D as 动态实体表
    participant A as 审计 Outbox

    F->>R: 写请求 + Idempotency-Key
    R->>R: 校验 Session、Capability、字段和动作
    R->>I: claim(application + actor + view + operation + key)
    alt 首次获得执行权
        R->>P: 以 Flow UserContext 校验表单和数据范围
        P->>D: 事务写入（更新含 recordVersion）
        R->>A: required 审计事件
        R->>I: 同业务事务用 fencing token 完成并保存最小响应
        R-->>F: 201/200 + receipt
    else 相同请求已成功
        I-->>R: receiptId + 首次 outcome
        R->>R: 重验当前权限并重新投影
        R-->>F: 业务结果回执/当前投影 + Idempotent-Replay=true
    else 相同 Key 不同请求
        R-->>F: 409 EMBED_IDEMPOTENCY_KEY_REUSED
    else 仍在处理
        R-->>F: 409 EMBED_REQUEST_IN_PROGRESS + Retry-After
    end
```

### 13.5 会话过期和重新打开

- Heartbeat 只能续空闲时间，不能突破绝对到期时间。
- Token 过期、Session 被撤销、用户被禁用或安全配置变化后，Runtime 返回 401/403 和
  `relaunchRequired=true`。
- iframe 发送 `session.expired`，清空内存 Token，展示“请重新打开”状态。
- 宿主不能刷新 Token，必须调用自己的后端重新创建 Launch；新 Launch 会重新做用户映射和
  全部权限计算。
- 页面刷新等同于丢失 Session Token，也必须重新 Launch。这是刻意的安全约束。

### 13.6 禁用与紧急撤销

| 操作 | 新 Launch | 已签发未兑换 Launch | 已有 Session |
| --- | --- | --- | --- |
| 禁用 Application | 拒绝 | 兑换拒绝 | 下次请求拒绝 |
| 禁用/撤销 Grant | 拒绝 | 兑换拒绝 | 下次请求拒绝 |
| 禁用/退役 View | 拒绝 | 兑换拒绝 | 下次请求拒绝 |
| 禁用 Identity Provider/Binding | 拒绝 | 兑换拒绝 | 下次请求拒绝 |
| 禁用/删除 Flow 用户 | 拒绝 | 兑换拒绝 | 下次请求拒绝 |
| 撤销单 Session | 不影响 | 不影响 | 该 Session 立即拒绝 |
| 发布新 Release | 使用新版本 | 保持原快照 | 保持原快照 |

认证过滤器每次请求检查 Session 及关联安全对象的状态/版本，因此安全停用不依赖 Token 自然
过期。可对只读高频查询使用最长 5 秒本地缓存，但停用事件必须跨 Pod 主动失效缓存；写请求
始终读取最新状态。
停用/撤销事务提交后还应投递 Session Sweep 事件，小批量把受影响 ACTIVE Session 转为
REVOKED 并通过第 14.9.1 节的统一原语释放 Counter；Sweep 延迟不影响认证拒绝，只负责状态和
配额收敛。

## 14. 数据模型与持久化设计

### 14.1 通用约定

- 主键沿用现有 64 字符以内统一 ID；授权不能依赖 ID 难猜，也不向外承诺格式或递增规律。
- 新增的外部稳定 Key、摘要和非 FK 字符串标识使用二进制排序规则，Origin、Issuer、Subject
  先规范化再计算摘要。所有 FK 子列必须与父列的类型、字符集和 Collation 完全一致；尤其
  `flow_user_id` 引用当前 `sys_user.id` 时使用其 `utf8mb4_0900_ai_ci`，不能套用二进制规则，
  否则 MySQL 会拒绝创建外键。
- 所有时间字段使用 `datetime(6)`，应用层统一按 UTC 处理。
- JSON 列必须有 `JSON_VALID`、长度和业务 Schema 三层校验。
- 密钥、Token、Launch code、用户断言不存明文。
- Context 如业务上必须持久化，使用应用层 AEAD 加密并绑定
  `applicationId + launchId/sessionId` 作为 AAD；密文保存加密密钥版本，查询/缓存只使用带
  独立密钥版本的 HMAC 摘要。
- 外部 Subject、来源 IP 等低熵或隐私字段使用服务端密钥 HMAC-SHA-256 等值索引，并保存
  摘要密钥版本；只保留最小脱敏 Hint，不保存原文。
- Launch code、Embed Session Token 和握手 Nonce 均为至少 256-bit CSPRNG 随机值，存储
  SHA-256 即可抵抗离线枚举，不引入无必要的 HMAC 密钥轮换状态。

`context_ciphertext` 使用版本化 AEAD Envelope，建议格式为：

```json
{
  "alg": "A256GCM",
  "kid": "embed-context-2026-08",
  "nonce": "<96-bit random base64url>",
  "ciphertext": "<base64url>",
  "tag": "<128-bit base64url>"
}
```

同一密钥下 Nonce 绝不复用；列上的 `context_cipher_key_version` 必须与 Envelope `kid` 对应。
Launch AAD 固定为 `embed-launch-v1|applicationId|launchId`，Session AAD 固定为
`embed-session-v1|applicationId|sessionId`。

### 14.2 复用现有表

| 现有表 | 复用方式 | 需要调整 |
| --- | --- | --- |
| `integration_application` | 第三方机器身份和状态根对象 | 增加 Scope 枚举，不改表结构 |
| `integration_application_scope` | 保存 `embed.launch` | 无新结构 |
| `integration_rate_limit_bucket` | Launch/Exchange/Runtime 限流 | 增加 Embed namespace |
| `integration_api_request_lease` | 写操作跨 Pod 并发租约 | 增加 Embed operation |
| `integration_idempotency_record` | 写操作 claim、重放和 fencing | 保持 Application/Operation/Key 唯一范围；请求哈希加入稳定 Actor/View，排除 Session/Release；响应只保存最小回执 |
| 现有系统审计/Outbox 表 | 安全和业务审计 | 增加 Embed 事件类型 |
| `sys_user` 及权限相关表 | Flow 用户、角色、组织、权限 | 只读复用，不复制用户权限快照 |

### 14.3 `embed_view`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | varchar(64) PK | View ID |
| `view_key` | varchar(100) | 外部稳定 Key，全局唯一，发布后不可改 |
| `name` | varchar(128) | 展示名称 |
| `description` | varchar(500) | 说明 |
| `surface_type` | varchar(16) | `LIST/FORM` |
| `status` | varchar(16) | `DRAFT/ACTIVE/DISABLED/RETIRED` |
| `draft_config_json` | longtext | 当前草稿完整配置 |
| `draft_revision` | bigint | 草稿递增版本 |
| `published_release_id` | varchar(64) null | 当前发布 Release |
| `lock_version` | bigint | 管理操作乐观锁 |
| `security_version` | bigint | 启停/退役等使既有 Session 失效的安全版本 |
| `create_by/create_time/update_by/update_time` | 现有审计类型 | 审计字段 |

索引与约束：

- `uk_embed_view_key(view_key)`；
- `idx_embed_view_status(status, update_time)`；
- `draft_config_json` 必须是合法 JSON 且不超过建议 256 KiB；
- `lock_version > 0`；
- `RETIRED` 不能回退到其他状态。

### 14.4 `embed_view_release`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | varchar(64) PK | Release ID |
| `view_id` | varchar(64) FK | 所属 View |
| `revision` | bigint | View 内递增发布号 |
| `surface_type` | varchar(16) | 快照目标类型 |
| `entity_code` | varchar(100) | 固定实体 |
| `list_key` | varchar(100) null | LIST 目标 |
| `default_form_id` | varchar(64) null | 列表相关默认表单 |
| `list_release_id/version` | varchar/bigint null | 固定列表发布快照 |
| `form_release_id/version` | varchar/bigint null | 固定表单发布快照 |
| `entry_modes_json` | longtext | 允许入口 |
| `capabilities_json` | longtext | 能力上限 |
| `field_policy_json` | longtext | 字段可见、查询、写入、回传策略 |
| `action_policy_json` | longtext | 动作白名单和输入 Schema |
| `context_schema_json` | longtext | Launch Context Schema |
| `context_bindings_json` | longtext | 固定过滤/强制字段映射 |
| `ui_config_json` | longtext | 受控 UI 设置 |
| `config_json` | longtext | Canonical 完整快照 |
| `config_hash` | char(64) | Canonical JSON SHA-256 |
| `release_note/published_by/published_at` | 审计类型 | 发布信息 |

`UNIQUE(view_id, revision)`；发布后除生命周期审计字段外不可更新。目标类型、List/Form 字段组合
用 CHECK 和应用层同时校验。

### 14.5 `embed_application_grant` 与 `embed_allowed_origin`

Grant 主要字段：

| 字段 | 说明 |
| --- | --- |
| `id` | Grant ID |
| `application_id` | FK `integration_application` |
| `view_id` | FK `embed_view` |
| `identity_provider_id` | 本 Grant 使用的人员身份来源 |
| `status` | `ACTIVE/DISABLED/REVOKED` |
| `trusted_subject_assertion` | 是否允许可信外部 ID |
| `revision_mode/pinned_revision` | `FOLLOW_ACTIVE/PINNED` 与固定版本 |
| `capability_ceiling_json` | 应用侧能力上限 |
| `max_active_sessions_per_user` | 单人会话数 |
| `max_session_seconds` | 绝对时长 |
| `launch_limit_per_minute/runtime_limit_per_minute/max_concurrency` | 配额 |
| `expires_at` | 授权到期 |
| `lock_version` | 乐观锁 |
| `security_version` | Origin、Provider、Capability、状态等安全变更版本 |
| 审计字段 | 创建、更新、撤销人员和时间 |

唯一键：`UNIQUE(application_id, view_id)`；状态和到期建立组合索引。

Origin 单独规范化保存：

```text
embed_allowed_origin(
  grant_id varchar(64) NOT NULL,
  origin varchar(255) COLLATE utf8mb4_bin NOT NULL,
  create_time datetime(6) NOT NULL,
  PRIMARY KEY(grant_id, origin)
)
```

只保存 Origin，不保存路径。`https://example.com` 与 `https://example.com:443` 在规范化后必须
得到同一结果；生产默认拒绝 HTTP、Wildcard 和 Public Suffix 级授权。

### 14.6 `embed_identity_provider`

主要字段：

| 字段 | 说明 |
| --- | --- |
| `id/name/type/status` | Provider 标识、名称、类型和状态 |
| `issuer` | 精确 Issuer |
| `subject_namespace` | 映射 Namespace |
| `audiences_json` | 允许 Audience |
| `algorithms_json` | 允许的非对称算法 |
| `jwks_mode` | `STATIC_JWK_SET/REMOTE_JWKS` |
| `jwks_json/jwks_url` | 二选一的受控验签材料 |
| `clock_skew_seconds` | 时钟偏差 |
| `max_assertion_lifetime_seconds` | 最大断言寿命 |
| `key_version/lock_version` | 外部验签材料版本和管理乐观锁 |
| `security_version` | Provider 状态/信任配置变化时递增 |
| 审计字段 | 创建、更新、禁用信息 |

Issuer 建唯一约束时需结合租户模型；如果同一 Issuer 可服务多个 Integration Application，
Provider 可复用，但 Grant 必须显式引用。

### 14.7 `embed_external_identity_binding` 与断言防重放

绑定表：

| 字段 | 说明 |
| --- | --- |
| `id` | Binding ID |
| `application_id` | 应用隔离边界 |
| `identity_provider_id` | Provider |
| `subject_digest` | 规范化 Subject 的 HMAC-SHA-256 |
| `subject_digest_key_version` | Subject 摘要密钥版本 |
| `subject_hint` | 最多保留首尾少量字符的脱敏 Hint |
| `flow_user_id` | 精确 Flow 用户 |
| `status` | `ACTIVE/DISABLED/REVOKED` |
| `binding_version` | 安全变更版本 |
| `effective_at/expires_at` | 生效窗口 |
| 审计字段 | 创建、更新、撤销信息 |

唯一键：
`UNIQUE(application_id, identity_provider_id, subject_digest)`。同一外部主体在一个应用/Provider
中只能映射一个 Flow 用户。查询时用当前及仍处于迁移窗口的旧密钥分别计算摘要；新增前先查
全部接受版本，轮换任务锁定 Binding 后原地改写摘要和 `subject_digest_key_version`，避免把
同一 Subject 插成两条映射。

新增 `embed_assertion_replay` 保存
`provider_id + jti_digest + expires_at`，其中 `jti_digest` 为
`SHA-256(normalizedIssuer + providerId + jti)`；主键或唯一键阻止同一个断言被并发重放。
清理任务在 `expires_at + clockSkew` 后删除；原始 `jti` 不落库。

### 14.8 `embed_launch`

| 字段 | 说明 |
| --- | --- |
| `id` | Launch ID |
| `application_id/grant_id/view_id/view_release_id` | 固定授权和发布快照 |
| `identity_provider_id/provider_security_version` | Provider 与签发时安全版本 |
| `application_version/grant_security_version/view_security_version` | 签发时撤销版本 |
| `flow_user_id/identity_binding_id/binding_version` | 固定 Flow 用户映射 |
| `subject_digest/subject_digest_key_version` | 审计关联，不保存原 Subject |
| `parent_origin/channel_id` | iframe 和消息通道绑定 |
| `entry_mode/record_id` | 固定入口 |
| `context_ciphertext/context_cipher_key_version` | AEAD 加密 Context 及密钥版本 |
| `context_digest/context_digest_key_version` | Canonical Context 的 HMAC 摘要及密钥版本 |
| `ui_locale/ui_theme` | 受控展示参数 |
| `launch_code_digest` | 一次性高熵 code 的 SHA-256，唯一 |
| `status` | `ISSUED/CONSUMED/EXPIRED/REVOKED` |
| `expires_at/consumed_at/consumed_session_id/revoked_at` | 生命周期 |
| `trace_id/request_id` | 安全审计关联 |
| `source_ip_digest/source_ip_digest_key_version` | 来源 IP 的 HMAC 摘要及密钥版本 |
| `user_agent_digest/user_agent_digest_key_version` | User-Agent 的 HMAC 摘要及密钥版本 |
| `create_time` | 创建时间 |

索引：

- `UNIQUE(launch_code_digest)`；
- `UNIQUE(consumed_session_id)`；
- `idx_embed_launch_expiry(status, expires_at)`；
- `idx_embed_launch_application_view(application_id, view_id, create_time)`。

`consumed_session_id` 只是便于审计和排障的反向指针，**不建立 FK**；否则兑换事务在 Session
尚未插入时先更新 Launch 会违反外键。真正的关系约束放在
`embed_session.launch_id -> embed_launch.id ON DELETE RESTRICT`。

### 14.9 `embed_session`

| 字段 | 说明 |
| --- | --- |
| `id` | Session ID |
| `session_token_digest` | 256-bit Token 的 SHA-256，唯一 |
| `launch_id` | 一个 Launch 只能创建一个 Session |
| `application_id/grant_id/view_id/view_release_id` | 安全快照 |
| `identity_provider_id/provider_security_version` | Provider 安全快照 |
| `flow_user_id/identity_binding_id/binding_version` | Actor 快照 |
| `parent_origin/channel_id` | 父页面与消息通道 |
| `entry_mode/record_id` | 固定入口与候选记录；Runtime 不接受切换目标 |
| `parent_nonce_digest/child_nonce_digest` | 256-bit 握手 Nonce 的 SHA-256，用于通道关联 |
| `context_ciphertext/context_cipher_key_version` | 运行时可信 Context 的 AEAD 密文及密钥版本 |
| `context_digest/context_digest_key_version` | Canonical Context 的 HMAC 摘要及密钥版本 |
| `ui_locale/ui_theme` | 会话展示参数，不影响授权 |
| `capability_snapshot_json` | 会话能力上限，不替代实时 Flow 权限 |
| `application_version/grant_security_version/view_security_version` | 快速撤销版本 |
| `status` | `ACTIVE/LOGGED_OUT/EXPIRED/REVOKED` |
| `slot_released` | 活跃会话计数是否已释放，保证终止幂等 |
| `slot_released_at` | 首次释放活跃会话计数的时间 |
| `issued_at/last_seen_at/idle_expires_at/absolute_expires_at` | 超时 |
| `revoked_at/revoke_reason` | 撤销审计 |
| `source_ip_digest/source_ip_digest_key_version` | 来源 IP 风险分析摘要及密钥版本 |
| `user_agent_digest/user_agent_digest_key_version` | User-Agent 风险分析摘要及密钥版本 |

索引：

- `UNIQUE(session_token_digest)`；
- `UNIQUE(launch_id)`；
- `idx_embed_session_expiry(status, idle_expires_at, absolute_expires_at)`；
- `idx_embed_session_application(application_id, status)`；
- `idx_embed_session_view(view_id, status)`；
- `idx_embed_session_user(flow_user_id, status)`。

`embed_session.launch_id` 同时 `NOT NULL + UNIQUE + FK ... ON DELETE RESTRICT`；删除顺序必须先
处理终态 Session，再处理对应 Launch，不允许级联删除安全审计链。
`slot_released` 使用 `tinyint NOT NULL DEFAULT 0`，并以 CHECK 保证 ACTIVE 时为 0、值为 0 时
`slot_released_at IS NULL`、值为 1 时 `slot_released_at IS NOT NULL`。

`lock_version` 只解决管理端并发编辑，`security_version` 用于撤销，二者不能混用。View 发布
新 Release 不递增 `view_security_version`，因此旧 Session 仍固定原快照；View 启停/退役、
Grant 的 Origin/Provider/Capability/状态变化、Provider 信任配置变化和 Binding 映射变化必须
递增相应安全版本。Integration Application V1 可使用现有 `version`，这意味着任何应用配置
变更都会使既有 Embed Session 失效，行为保守但明确。

#### 14.9.1 `embed_session_counter`

`max_active_sessions_per_user` 不能靠“先 count 再 insert”实现，否则并发 Exchange 会超额。
新增计数行：

```text
embed_session_counter(
  grant_id varchar(64) NOT NULL,
  flow_user_id varchar(64) NOT NULL,
  active_count int NOT NULL,
  lock_version bigint NOT NULL,
  update_time datetime(6) NOT NULL,
  PRIMARY KEY(grant_id, flow_user_id),
  CHECK(active_count >= 0)
)
```

`grant_id` 和 `flow_user_id` 分别使用 `ON DELETE RESTRICT` 外键；授权/用户退役先终止 Session、
释放 slot，再按平台归档策略处理主体，不能级联吞掉计数审计。

Exchange 事务先 `INSERT IGNORE` 初始化，再 `SELECT ... FOR UPDATE`，校验
`active_count < maxActiveSessionsPerUser` 后原子加一，并插入 `slot_released=0` 的 Session。
达到上限时回滚兑换事务并返回 429 `EMBED_SESSION_LIMIT_EXCEEDED`，Launch 保持 ISSUED，调用方
可在其剩余 TTL 内关闭旧 Session 后再次兑换同一 Launch。
Logout/Revoke/Expire 先只读取得 Grant/User，再在事务中按 `Counter -> Session` 的统一顺序
锁定；随后用 `WHERE slot_released=0` 条件更新 Session 为
`status=<LOGGED_OUT/REVOKED/EXPIRED>, slot_released=1, slot_released_at=now()`，仅当受影响
行数为 1 时执行
`active_count=active_count-1`。若异常漂移导致计数已为 0，则不减为负数，终止 Session 并写
required 漂移审计/告警。重复 Logout、并发撤销和过期扫描均不得重复扣减。定时对账任务
按未释放的 ACTIVE Session 双向只读扫描并报告极端故障漂移；自动改写计数可能与在线
Exchange/Termination 竞争，因此 V1 不自动修数，由运维确认后通过受控操作修复。现有
`integration_api_request_lease` 仍只负责
应用级请求并发，不承担 Grant/User 活跃会话数。
应用/View/Grant 批量撤销也必须小批量调用同一个条件释放原语，不能只批量改 Session 状态而
遗漏 Counter。

#### 14.9.2 `embed_operation_receipt` 与幂等表映射

每个成功写请求在同一业务事务中创建一个最小业务回执，作为 stale reclaim 下的第二道唯一
屏障，并满足现有 `integration_idempotency_record` 成功状态的非空约束：

| 字段 | 说明 |
| --- | --- |
| `id` | 对外不透明 `receiptId` |
| `idempotency_record_id` | 本次 Claim 的内部记录 ID，唯一；不建立跨保留期 FK |
| `application_id/operation` | 应用和操作范围 |
| `actor_scope_digest/view_key` | 稳定 Actor/View 绑定 |
| `target_type/target_id` | 记录、流程或动作目标；可空目标使用回执 ID |
| `outcome_code/record_version` | 最小结果和版本 |
| `result_summary_json` | 只含允许回传的资源 ID、版本和状态，最大 8 KiB |
| `create_time` | 创建时间 |

`UNIQUE(idempotency_record_id)`；旧 Worker 在新 Worker 已接管后即使继续运行，也会在插入回执
或完成 fencing 时冲突，整个同库业务事务必须回滚。该回执不能替代下游外部副作用的 Outbox/
消费者幂等。

完成现有幂等记录时固定写：

```text
resource_type   = EMBED_OPERATION_RECEIPT
resource_id     = <receiptId>
response_status = 首次 2xx 状态
```

`response_body` 使用固定且不含业务字段的 `EmbedIdempotencyReplayEnvelope`，UTF-8 最大 8 KiB，
天然满足现表的 `JSON_VALID` 和服务层 65,535 字节硬上限：

```json
{
  "schema": "embed-idempotency-replay-v1",
  "receiptId": "eor_01K...",
  "outcomeCode": "RECORD_CREATED"
}
```

重放时先做当前授权校验，再按 `receiptId` 读取最小回执并按当前 Output Policy 查询/投影资源；
V1 不在 `response_body` 保存表单值，也不需要在该明文 JSON 内嵌密文。无法安全重建的动作只能
返回回执中的非敏感 outcome/资源 ID，不能退回首次敏感正文。

### 14.10 状态机

```mermaid
stateDiagram-v2
    state "Embed View" as V {
        [*] --> DRAFT
        DRAFT --> ACTIVE: publish
        ACTIVE --> DISABLED: disable
        DISABLED --> ACTIVE: enable
        DRAFT --> RETIRED: retire
        ACTIVE --> RETIRED: retire
        DISABLED --> RETIRED: retire
    }
    state "Launch" as L {
        [*] --> ISSUED
        ISSUED --> CONSUMED: atomic exchange
        ISSUED --> EXPIRED: ttl
        ISSUED --> REVOKED: revoke
    }
    state "Session" as S {
        [*] --> ACTIVE
        ACTIVE --> LOGGED_OUT: logout
        ACTIVE --> EXPIRED: idle/absolute timeout
        ACTIVE --> REVOKED: security revoke
    }
```

Grant、Identity Provider 和 Binding 的 `REVOKED`，以及 View 的 `RETIRED` 都是终态。

### 14.11 原子兑换事务

兑换事务建议按以下顺序：

1. 在事务外计算 `launchCodeDigest` 并读取候选 Launch，错误 code 不锁定大量安全配置；
2. 开启事务，按全系统统一顺序锁定并重新读取
   `Application -> View -> Grant -> Provider -> Binding -> Flow User ->
   Session Counter -> Launch`；
3. 在锁内重新检查状态、到期和全部 `security_version`；初始化并锁定 Session Counter，校验
   上限后把 `active_count` 原子加一；
4. 创建预分配 `sessionId` 和随机 Session Token；
5. 执行条件更新：

```sql
UPDATE embed_launch
   SET status = 'CONSUMED',
       consumed_at = ?,
       consumed_session_id = ?
 WHERE id = ?
   AND status = 'ISSUED'
   AND expires_at > ?
   AND launch_code_digest = ?
   AND channel_id = ?
   AND parent_origin = ?;
```

6. 受影响行数必须等于 1，否则统一失败；
7. 用 Launch AAD 解密并验证 Context，再以新随机 Nonce 和 Session AAD 重新加密；插入
   `slot_released=0` 的 `embed_session`，写 Session Token/Nonce 摘要、Context Envelope 和
   所有安全版本；禁止把 Launch 密文原样复制到 Session；
8. 写 required 审计 Outbox；
9. 提交后才把明文 Token 返回调用方。

Token 生成后若事务失败立即丢弃。不得先标记 Launch 已消费、后异步创建 Session。
Exchange 与管理员禁用操作必须采用相同锁顺序：先提交者定义线性化结果；如果 Exchange 刚好
先提交，随后禁用会使新 Session 在第一次 Runtime 请求即失效。

### 14.12 保留与清理

建议默认：

| 数据 | 在线保留 | 清理方式 |
| --- | --- | --- |
| Assertion replay | 到 `exp + skew` 后 1 天 | 小批量定时删除 |
| 未消费 Launch | 到期后 7 天 | 先标记 EXPIRED，再批量清理 |
| 已消费 Launch 安全摘要 | 至少保留到关联 Session 删除后 | 按审计策略脱敏归档，再按 FK 顺序清理 |
| Session | 终止后 90 天 | 只留审计关联，密文 Context 提前擦除 |
| Idempotency | 复用现有默认 7 天 | 不删除仍在 PROCESSING 的新 Claim |
| Operation Receipt | 不短于对应 Idempotency 记录 | 先清理 Idempotency，再清理无引用回执 |
| 系统审计 | 沿用平台审计保留策略 | 不由 Embed 清理任务直接删除 |

清理必须按索引小批量执行、可重入，并避免在多 Pod 上重复抢占；复用现有调度锁或任务框架。
对已消费数据先擦除终态 Session 的 Context，再删除满足保留期的 Session，下一批才删除已无
Session 引用的 Launch；`consumed_session_id` 因无 FK 可保留为脱敏审计指针。集成测试必须覆盖
FK 方向、兑换插入顺序以及 Session/Launch 分批清理顺序。

## 15. 安全设计

### 15.1 信任边界

| 输入来源 | 信任程度 | 处理 |
| --- | --- | --- |
| Flow 管理员发布配置 | 受权限控制，但仍需校验 | 发布时解析并生成不可变快照 |
| 第三方机器 Token | 只证明应用 | 不能推导人员身份或 Flow 权限 |
| 已验签人员断言 | 只证明外部 Subject | 必须再查 Identity Binding |
| 第三方后端 Context | 有条件可信 | 仅限 Schema 字段，只能收窄范围 |
| 宿主浏览器/postMessage | 不可信 | Origin、source、nonce、channel、Schema 全校验 |
| iframe Runtime 请求 | 不可信 | 目标和能力全部从 Session 恢复 |
| Flow 已发布列表/表单 | 业务可信输入 | 对外仍需字段、动作和组件投影 |

最终授权必须同时满足：

```text
有效应用
AND 有效 Grant/Origin/Provider/Binding
AND 有效 Flow 用户
AND 有效 Embed View Release/Capability
AND Flow 功能权限
AND Flow 数据范围与行级能力
AND 记录状态/表单规则
AND 请求幂等与并发条件
```

### 15.2 Token 与凭据

- Client Secret 和机器 Token 只存在第三方服务器。
- 人员断言最长 60 秒，`jti` 一次性；禁止把 Flow 角色放进断言并直接授权。
- Launch code 使用至少 256-bit CSPRNG，60 秒、一次性、只存 SHA-256 摘要。
- Embed Token 使用独立随机值、独立认证 Filter、独立 Audience 语义，绝不签成普通用户 JWT。
- Embed Token 只在 iframe 内存；不使用 Cookie，因此不依赖第三方 Cookie 和 `SameSite=None`。
- URL query、日志、Referer、错误报告和埋点中不得包含任何 Token/断言。
- Token/code 摘要比较使用常量时间。Subject、Context、IP、User-Agent 的 HMAC 密钥和 Context
  的 AEAD 密钥来自受管 Secret/KMS，每条记录保存对应 `keyVersion`。
- 密钥轮换采用“先发布新读取密钥环，再切换写主密钥，后台重算摘要/重加密，最后移除旧密钥”
  的顺序；迁移期间读路径同时接受当前与旧版本，审计轮换进度，确认无旧版本记录后才销毁旧密钥。

### 15.3 CSP、Origin、CORS 与点击劫持

1. 管理后台继续 `frame-ancestors 'none'`，只允许 Embed 入口响应被授权 Origin 嵌入。
2. 每个 Launch 固定一个精确父 Origin，入口响应只生成该一个 `frame-ancestors` Source。
   CSP 会检查全部祖先，因此 V1 只支持由该 Origin 直接嵌入，不支持再套一层不同 Origin。
3. `postMessage` 的 `targetOrigin`、`event.origin` 和 `event.source` 全部精确匹配。
4. 不使用 `X-Frame-Options: ALLOW-FROM`，因为现代浏览器支持不一致；以 CSP 为准。
5. iframe 与 Runtime API 推荐同源，不为合作方 Origin 直接开放 Runtime CORS。
6. 若静态资源使用 CDN，CSP 的 `script-src/style-src/font-src/img-src/connect-src` 必须显式列举，
   禁止为方便使用宽泛 `*`。
7. Embed 页面不能在顶层窗口中静默运行敏感动作；检测 `window.top===window.self` 时展示安全
   错误或只允许经产品批准的顶层调试模式。

### 15.4 XSS 与组件扩展

- 表单富文本按现有可信 Sanitizer 再过滤；普通文本默认转义。
- V1 不允许运行发布配置中的任意 JavaScript、`eval`、动态模块 URL 或合作方远程组件。
- V1 强制拒绝全部自定义表单、列表、节点、单元格和关联内容组件；现有注册表尚无 Embed
  安全标记。后续若开放，必须新增跨注册表的 `embedSafe=true` 元数据、输入/输出审计和
  运行时强制校验。
- 外部 Schema 只包含声明式数据，不暴露内部 Provider、模板表达式或服务调用配置。
- postMessage Payload 反序列化后拒绝 `__proto__`、`constructor`、`prototype` 等危险键。

### 15.5 数据越权与侧信道

- 任何 `recordId` 都重新执行实体归属、固定 Context、Flow 数据范围和行级能力校验。
- 无权限与不存在统一 404，不返回“记录存在但无权限”。
- 列表数量、字典选项和校验错误也必须经过字段策略；敏感场景可关闭 `total`。
- 批量选择逐条授权，不能仅校验第一条或依赖前端曾经显示过。
- 错误响应不返回 SQL、表名、权限码、内部类名、堆栈或被拒绝字段当前值。

### 15.6 SSRF、附件和外部调用

远程 JWKS 是受控出站网络访问，必须防 SSRF。V1 不开放任意 URL 数据源、附件上传下载或
通用连接器。如果后续开放附件，必须增加独立 File Capability、对象级签名 URL、文件类型/
大小/病毒扫描、下载审计和 Session/View/Record 绑定，不能直接复用内部永久文件 URL。

### 15.7 威胁与控制摘要

| 威胁 | 关键控制 |
| --- | --- |
| Launch URL 被转发 | 60 秒一次性 code、绑定 Origin/channel/用户、原子消费 |
| 合作方伪造 Flow 用户 | 签名断言 + 精确 Binding，拒绝客户端 `flowUserId` |
| Embed Token 横向调用普通 API | 独立不透明 Token和专用安全链 |
| 更换实体/表单/Release 参数 | Runtime 不接收这些可信参数 |
| IDOR | 每次按 Flow 用户和 View Context 重做行级校验 |
| 点击劫持 | 每个 Launch 精确 `frame-ancestors` |
| postMessage 劫持 | exact Origin/source/channel/nonces/MessagePort |
| 重复提交 | 应用范围幂等键、请求摘要、fencing token |
| 并发覆盖 | V1 不开放更新；后续 UPDATE/ACTION 必须先完成通用 `record_version` 乐观锁 |
| 配置停用后 Token 仍可用 | 每请求检查安全对象状态/版本并跨 Pod 失效缓存 |
| 日志泄密 | 字段级脱敏、Token/Assertion/Context 禁记 |

## 16. 后端实现设计

### 16.1 模块与依赖调整

建议依赖方向：

```text
workflow-contracts
  ↑           ↑                 ↑
open-api   workflow-embed   entity/process adapters
  \           |                 /
             workflow-app（组装）
```

箭头指向被依赖的内层。`workflow-embed` 是独立边界上下文；它不导入 Open API Controller、
Entity/Process Mapper、Spring MVC Request 或前端 DTO，外部模型通过 Anti-Corruption Adapter
转换为 Embed 自己的 Value Object/Command。

具体调整：

1. 新增 `workflow-embed`，由 `workflow-app` 引入。
2. `workflow-contracts` 增加 Embed Launch、Actor、列表、表单、记录和共享集成策略端口。
3. `workflow-open-api` 增加 Launch Controller，只依赖 `EmbedLaunchIssuePort`。
4. 现有 Open API 限流、租约、幂等能力通过 Contracts Port 暴露给 Embed，避免 Embed 依赖
   Open API Controller 或 Mapper。
5. `workflow-entity`、`workflow-process` 实现窄 Adapter；Adapter 调用现有应用服务，不让
   `workflow-embed` 直接访问它们的 Mapper。
6. Embed 的 Application Service 只依赖 Contracts Port；数据库 Mapper、JWKS HTTP Client、
   KMS 和时钟均为外层 Adapter，由 `workflow-app` 组装。
7. Controller 只做 HTTP 校验、Command 转换和 Envelope 映射；权限求交、状态机、幂等协调
   不放进 Controller。
8. 使用 Maven 依赖检查/ArchUnit 固化上述方向，禁止 `workflow-embed` 反向依赖
   `workflow-open-api`、`workflow-entity` 或 `workflow-process` 的基础设施包。

下面是边界契约的逻辑形态。当前 V1 只实现列表读取、表单解析、记录详情和记录创建；
`update/execute` 属于第 16.4 节完成后的后续契约，不是当前可注入或可调用能力：

```java
public interface EmbedLaunchIssuePort {
    EmbedLaunchIssued issue(EmbedApplicationActor application,
                            EmbedLaunchCommand command);
}

public interface EmbedListRuntimePort {
    EmbedListSchema resolveSchema(EmbedRuntimeActor actor,
                                  EmbedRuntimeTarget target);
    EmbedPage query(EmbedRuntimeActor actor,
                    EmbedRuntimeTarget target,
                    EmbedListQuery query);
}

public interface EmbedFormRuntimePort {
    EmbedFormView resolve(EmbedRuntimeActor actor,
                          EmbedRuntimeTarget target,
                          EmbedFormCommand command);
}

public interface EmbedRecordRuntimePort {
    EmbedRecordView detail(EmbedRuntimeActor actor,
                           EmbedRuntimeTarget target,
                           String recordId);
    EmbedMutationResult create(EmbedRuntimeActor actor,
                               EmbedRuntimeTarget target,
                               EmbedCreateCommand command);

    // Deferred：V1 不注册对应 HTTP 路由，也不发布所需 Capability。
    EmbedMutationResult update(EmbedRuntimeActor actor,
                               EmbedRuntimeTarget target,
                               EmbedUpdateCommand command);
    EmbedActionResult execute(EmbedRuntimeActor actor,
                              EmbedRuntimeTarget target,
                              EmbedActionCommand command);
}
```

Contracts 中只使用稳定业务类型，不依赖 Web Request、MyBatis Record 或内部 DTO。
Launch、Exchange、授权求交和重放 Use Case 必须可用 In-Memory Port 在不启动 Spring、数据库
或网络的普通单元测试中运行；真实 Mapper/JWKS/KMS 和实体/流程 Adapter 另做契约与集成测试。

### 16.2 主要后端组件

| 组件 | 所属模块 | 职责 |
| --- | --- | --- |
| `EmbedLaunchController` | open-api | 机器 Token 路由、DTO 和 Envelope |
| `EmbedLaunchService` | embed | Grant、Origin、断言、用户映射、Release 解析、签发 |
| `ExternalSubjectVerificationService` | embed | V1 JWT/可信 ID 校验与防重放；OIDC 为后续扩展 |
| `ExternalIdentityBindingService` | embed | 精确映射 Flow 用户 |
| `EmbedViewAdministrationService` | embed | 草稿、校验、发布、状态 |
| `EmbedGrantAdministrationService` | embed | 应用授权和 Origin |
| `EmbedLaunchExchangeController` | embed | 一次性交换 |
| `EmbedSessionService` | embed | Token、超时、撤销、心跳 |
| `EmbedSessionAuthenticationFilter` | embed | Runtime 认证、上下文建立/清理 |
| `EmbedRuntimeFacade` | embed | 能力求交、调用 Adapter、外部 DTO 投影 |
| `EmbedActionRegistry` | embed | **Deferred**；后续把 actionKey 映射到强类型、审核过的 Handler，V1 不存在 Action API |
| `EmbedDataSourceProviderRegistry` | embed/entity adapter | **Deferred**；V1 只有大型不可变静态 Options 分页，动态数据源和 Lookup Provider 均 Fail Closed |
| `EmbedResponseProjector` | embed | 字段/动作/错误脱敏 |
| `EmbedIdempotencyCoordinator` | embed | 对接现有 claim/fencing 能力 |
| `EmbedOperationReceiptService` | embed | 同事务最小业务回执和 stale Worker 第二道去重 |
| `EmbedAuditService` | embed | required/best-effort 审计映射 |
| `EmbedCleanupJob` | embed | 过期和密文擦除 |

### 16.3 Web 安全链和上下文

当前普通 `AuthInterceptor` 只跳过少量认证接口，`@PublicApi` 本身不足以建立 Embed 认证。
需要明确调整：

1. `/api/open/v1/embed-launches` 继续进入现有 `@Order(2)` OAuth Resource Server 安全链，
   并在 `anyRequest().authenticated()` 之前增加精确
   `POST + SCOPE_embed.launch` Matcher，不能让任意机器 Scope 调用。
2. 新增优先于现有 `@Order(1000)` catch-all `permitAll` 的专用 Spring
   `SecurityFilterChain`（建议 `@Order(3)`、`securityMatcher("/api/embed/**")`）。
3. `/api/embed/v1/launches/*/exchange` 从普通 `AuthInterceptor` 和
   `EndpointAuthorizationInterceptor` 排除，但必须进入 `EmbedLaunchExchangeGuard`。
4. `/api/embed/v1/runtime/**`、`/api/embed/v1/session` 和 `/api/embed/v1/session/**`
   从普通用户认证/Endpoint 权限
   拦截器排除，只进入专用 FilterChain 中的 `EmbedSessionAuthenticationFilter`；该 Filter
   在 Spring MVC 之前建立 Authentication。
5. 管理端 `/api/embed-management/v1/**` 不排除，继续使用普通 Flow JWT 和权限码。
6. 增加 `@EmbedApi` 或等效 Marker，并用集成测试/ArchUnit 保证 Runtime Controller 不会
   意外落到无认证链。

专用链必须 `STATELESS`、关闭 Request Cache，仅对精确 Exchange/Runtime 路径忽略 CSRF，并
在列举允许的 Matcher 后 `anyRequest().denyAll()`。集成测试需同时在
`workflow.open-api.enabled=true/false` 两种配置下证明 Embed 路径都不会落入 catch-all
`permitAll`。

Runtime 过滤器伪代码：

```java
try {
    EmbedSessionPrincipal principal = sessionAuthenticator.authenticate(token);
    EmbedContextHolder.set(principal.embedContext());
    UserContext.setCurrentUser(principal.flowUser());
    mdc.put("embedSessionId", principal.sessionId());
    filterChain.doFilter(request, response);
} finally {
    mdc.remove("embedSessionId");
    EmbedContextHolder.clear();
    UserContext.clear();
}
```

核心原则：

- `UserContext` 用真实已启用 Flow 用户建立，不使用共享服务账号；
- `EmbedContext` 保存 View/Release/Context/Capability 等额外约束；
- Facade 和 Adapter 同时要求两个 Context，不允许只有 UserContext 就调用 Embed 端口；
- 异步线程不能依赖 ThreadLocal 自动传播，必须显式传递不可变 `EmbedRuntimeActor`；
- finally 清理必须有并发测试，防止线程池用户串号。

### 16.4 动态实体 `record_version` 建设

这是开放编辑和有状态动作的硬前置，不只是 Embed Controller 的一个字段。

需要修改的通用链路：

1. 新建动态实体表 DDL 自动包含：

   `record_version BIGINT NOT NULL DEFAULT 0`。

   同时把 `record_version/recordVersion` 加入系统保留字段，禁止业务实体定义同名字段。

2. 复用现有 `workflow_schema_change` 队列和 `SchemaChangeWorker` 对所有既有动态实体表执行
   受控 Expand；沿用其 `active_hash` 去重、owner/lease/fencing、attempt、nextAttempt 和
   lastError，不另造一个弱化任务表：
   - 识别缺列；
   - 小批次/在线 DDL 补列；
   - 校验默认值和非空；
   - 每个目标表单独入队，以任务的 APPLIED 状态作为 DDL checkpoint；提交端随后查询
     information_schema 复核列定义，失败则记录并重试核验而不是重复盲改。
3. 动态实体读取 DTO 返回 `recordVersion`。
4. 版本参数必须贯穿
   `If-Match/External DTO -> EmbedUpdateCommand -> EntityMutationCommand/Port ->
   Dynamic SQL`；当前 `EntityDataActionService` 和下游 Command 没有该字段，需要修改
   Contract，不能只在 Embed Controller 表面校验。
5. 所有更新入口统一采用
   `WHERE id = ? AND record_version = ?`，成功时原子 `+1`。
6. EntityDataAction、表单保存、有状态动作和内部编辑页面同步传递版本，不能只给 Embed 使用。
7. 旧客户端兼容期可在内部接口暂时允许缺版本，但 Embed 永远要求；最终 Contract 阶段再收紧
   内部接口。
8. 更新 0 行要先按同一数据权限重新读取，区分“不可见/不存在”和“版本冲突”，且不得泄漏
   越权记录存在性。

上线顺序：

```text
Expand 迁移/Schema Worker
-> 所有表补列并核验
-> 部署兼容读写代码
-> 内部前端开始传版本
-> 启用 Embed RECORD_UPDATE 发布校验
-> 后续版本收紧所有写入口
```

### 16.5 运行态 Adapter 与复用边界

列表 Adapter：

- 新增仅供服务端 Embed Adapter 调用的 `EmbedReleaseResolver`，基于不可变
  `embed_view_release` 解析固定 Entity/List/Form Release；当前普通解析器在没有签名上下文
  Token 时只允许 ACTIVE Release，不能通过伪造普通 UI Token 来加载 PINNED 版本；
- 用 Resolver 得到的固定目标调用
  `EntityListPublishedRuntimeService/EntityListRuntimeService` 的受信内部入口；
- 服务端构造 `scene=EMBEDDED`、固定 Filter 和 Context；
- 不把多个条件简单 `Map.putAll`。新增 `EmbedFilterPlan`/谓词 AST，按
  `View Fixed AND Context AND Data Scope AND Published List AND Client Filter` 求交；同字段
  等值冲突返回 match-none，范围取交集，不支持安全求交的组合直接拒绝；
- 把允许的外部 Filter 编译为参数化查询，不透传 Release/Context Token；
- 当前列表 `LIST_LOAD` 通用 UI Event 和动态 Provider 在 Embed V1 中全部禁用；后续若建设
  Provider Allowlist，也只能执行强类型只读来源，不能依靠响应投影弥补已经发生的任意内部 Event；
- 调用后用 `EmbedResponseProjector` 只输出公开列和行级动作。

表单 Adapter：

- 使用同一 `EmbedReleaseResolver` 固定 Form Release，再调用
  `EntityFormResolveService`、`EntityFormRuntimeAdapter` 的受信内部入口；
- 使用 `PublishedFormSubmissionService` 完成默认值、校验和保存；
- 拒绝设计态表单和动态传入 Form ID；
- V1 发布和运行时均拒绝自定义 UI Event，不存在“白名单动作间接触发任意 Event”的回退路径。

当前实现通过受信内部入口解析 Session 固定的 PINNED Release；解析器或精确版本不可用时必须
503 Fail Closed，不允许降级为 `FOLLOW_ACTIVE`，也不允许把固定 Release ID 直接塞给现有普通接口。

记录/动作 Adapter：

- 调用 `EntityDataActionService`、`EntityActionCapabilityService` 和 `DataPermissionEngine`；
- 每次详情、保存、动作都重新检查 Flow 权限和行级能力；
- `EntityActionCapabilityService` 只提供能力判断，不是通用动作执行器；`EmbedActionRegistry`
  必须为每个允许动作显式实现事务和输入 Schema；
- 不直接公开 `EntityDataController`、`UiEventRuntimeController` 或
  `UiInterfaceOperationRuntimeController`。

### 16.6 幂等、事务与审计一致性

Embed 写请求复用现有 `OpenIdempotencyService` 的状态机、120 秒 stale reclaim 和 fencing
思想，但通过 Contracts Port/共享组件调用。

Canonical request hash 至少包含：

```text
applicationId
不可逆且跨 Session 稳定的 actorScopeDigest
viewKey
operation
stable target（CREATE 为 entityCode + formId；recordId/actionKey 仅后续操作使用）
canonical request body
```

当前 V1 的 `EMBED_RECORD_CREATE` 将上式 target 具体冻结为
`targetType=ENTITY_FORM + {entityCode, formId}`。`formReleaseId/listReleaseId/viewReleaseId` 都不进入
target 或 Hash，Session/Launch 也不进入；Release 变化后的安全性由重放前重新授权和按当前
Output Policy 投影保证，而不是靠把短期发布坐标混入幂等身份。

`actorScopeDigest` 使用域分离的
`SHA-256("embed-actor-v1" + applicationId + providerId + bindingId + flowUserId)` 生成；输入都是
Flow 内部稳定 ID，不含外部 Subject/用户名等 PII，也不受 Session 或本地 HMAC 密钥轮换影响。
Session ID、Launch ID、View Release ID、Trace ID、Token 和时间戳都不得进入请求哈希；否则
页面刷新/重新 Launch 后无法用原键查询首次结果。Release 变化不允许再次执行业务，而是在
当前 Release 仍授权该操作时返回并重新投影首次最小回执。
`EmbedIdempotencyCoordinator` 把上述稳定范围与已通过 External Schema 校验的 Command 包装后
交给现有 Canonical JSON 实现：对象 Key 排序、数组顺序保留、缺失与 null 保持不同；Header、
服务端默认值和展示 Locale 不进入 Body Hash。`clientMutationId` 属于调用方业务关联字段并进入
Hash，所以网络重试时也必须保持不变。

`Idempotency-Key` 在 V1 按 Application + Operation 全局唯一处理；SDK/第三方应使用 UUID 或
包含外部业务请求号的全局键。相同 Key 被另一 Actor/View 使用时必须 409，绝不能重放前一
主体结果。重放请求先完成当前 Session、用户、Grant、View、对象可见性和 Output Policy 校验；
失去权限时返回授权错误，而不是返回历史敏感数据。
Operation 使用服务端封闭枚举 `EMBED_RECORD_CREATE/EMBED_RECORD_UPDATE/
EMBED_ACTION_EXECUTE`，不能把浏览器传值直接作为数据库 operation；具体 `actionKey` 放在
Target/Hash 中。

现有 `integration_idempotency_record.response_body` 是明文 JSON。Embed 只能保存满足
第 14.9.2 节的固定 Replay Envelope，并把 `resource_type/resource_id` 指向事务内创建的
`embed_operation_receipt`；不得保存表单值。未来若确需保存不可重建的敏感响应，必须新增带
AEAD key version 的专用密文字段和迁移，不能把密文结构临时塞入既有明文契约。Launch/Exchange
绝不能使用该响应缓存。

写事务边界建议：

1. 用现有 `REQUIRES_NEW` 语义 claim 幂等记录；
2. 开启业务事务，校验实时权限和记录版本；
3. 写实体/流程数据；
4. 同事务写 required 审计 Outbox；
5. 同事务插入 `embed_operation_receipt`，由 `UNIQUE(idempotency_record_id)` 提供业务效果
   第二道去重；
6. 在同一个业务事务内调用现有 `MANDATORY completeInBusinessTransaction`，使用当前
   fencing token 完成幂等记录；
7. 一起提交业务数据、Operation Receipt、审计 Outbox 和幂等成功状态；
8. 返回外部投影。

`completeInBusinessTransaction` 失败必须让业务事务整体回滚；业务异常回滚后，再以
`REQUIRES_NEW` 标记 `FAILED_RETRYABLE`；该更新也必须携带当前 fencing token，已被新 Worker
接管时只记冲突审计、不得覆盖新状态。这样不会出现“业务已提交但幂等仍为 PROCESSING”的
窗口。需要沿用现有 fencing 规则，旧 Worker 不得覆盖新 Claim 的结果；旧 Worker 的 complete
因 fencing token 失效而更新 0 行时必须抛错并回滚同一数据库事务。

V1 允许的写动作，其业务数据、业务回执、审计 Outbox 和幂等状态必须位于同一数据库事务；
禁止在事务提交前直接调用不可回滚的 HTTP、消息或文件副作用。需要外部副作用的后续动作必须
先以 Outbox/业务 Effect Receipt 落库，并用同一稳定业务键在消费者侧再次去重，不能只依赖
API 层幂等记录。可复用现有 `EntityMutationReceiptService` 的语义，但回执正文仍需遵循最小化
和加密规则。

### 16.7 缓存和撤销

- View Release 是不可变对象，可按 Release ID 长缓存。
- Provider JWKS 按 `kid` 缓存，未知 `kid` 触发一次受控刷新，防止刷新风暴。
- **后续能力**若为动态选项/引用候选增加缓存，Key 必须包含
  `flowUserId + viewReleaseId + contextDigest + fieldCode + dependencyHash`，不得跨用户共享未授权
  结果；V1 的静态 Options 直接读取不可变 Form Release，Lookup 不执行。
- Session Token 摘要查找可缓存极短时间，但 Session 状态、到期和安全版本必须一起缓存。
- Application/Grant/View/Provider/Binding/用户停用通过现有事件或新增本地 Cache Evict Topic 跨 Pod
  失效；没有可靠广播时最大缓存 TTL 不超过 5 秒。
- 写请求和高风险动作不使用可能过期的授权缓存。

### 16.8 定时任务

| 任务 | 周期建议 | 行为 |
| --- | --- | --- |
| Assertion Replay 清理 | 5 分钟 | 删除过安全窗口的数据 |
| Launch 过期标记 | 1 分钟 | `ISSUED -> EXPIRED` |
| Session 过期标记 | 1 分钟 | idle/absolute 到期，并按 `slot_released` 原子释放 Counter |
| Session Counter 对账 | 10 分钟 | 双向只读扫描未释放 ACTIVE Session，发现漂移后聚合告警，不自动改写在线计数 |
| Context 密文擦除 | 每小时 | 达保留期后擦除 |
| Idempotency 清理 | 复用现有周期 | 保留处理中记录和 fencing 语义 |
| Operation Receipt 清理 | Idempotency 清理之后 | 只删已无对应幂等重放窗口的回执 |
| 摘要/密文 Key 迁移 | 轮换期间持续 | 小批量重算/重加密，统计旧 keyVersion 余量 |
| 指标聚合 | 1–5 分钟 | 按应用/View 聚合，不带 Subject/Token |

当前 V1 已实现 Assertion Replay 清理、Launch 过期标记、Session 过期回收、终态 Context
擦除、Counter 双向只读对账、无幂等记录引用的 Operation Receipt 清理，以及按 FK 顺序清理
终态 Session/Launch。所有扫描均使用有界批次和索引，每个数据库步骤以独立小事务执行；
摘要/密文 Key 在线迁移与更丰富的指标聚合仍属于后续运维增强。

### 16.9 OpenAPI 契约产物

当前已新增 `docs/api/embed-v1.yaml`（OpenAPI 3.1），包含：

- OAuth/Launch、Entry HTML、Exchange、Session、Bootstrap、List Schema/Query、Form、CREATE 联动重算、
  静态 Options、Fail-Closed Lookup、Record View/Create；
- Bearer Security Scheme 的机器 Token 与 Embed Token 区分；
- 所有 DTO、枚举、长度、格式、状态码、错误码和示例；
- `Idempotency-Key`、`Retry-After`、`Idempotent-Replay`，以及 V1 固定
  `recordVersion=null`、不返回 ETag 的边界；
- `flow-embed/1` 已实现握手/事件，以及 `navigation.request` 等 SDK 预留事件的 Deferred 标记；
- 未注册的 Update/Action 路由不伪造进 `paths`；已注册的 Lookup 查询路由保留在 `paths` 但不声明
  200，动态 Option/Lookup Provider 只在扩展元数据中标为 Deferred/Fail Closed；
- 兼容性说明和 deprecation 规则。

CI 已执行 OpenAPI 解析、Breaking Change、Controller/DTO/响应实例与关键 Header 契约检查；
SDK 公共类型由本文件的封闭协议 Schema 确定性生成，`test:embed` 会校验类型产物、运行时
协议常量、Capability、命令和事件没有漂移。

## 17. 前端与部署实现设计

### 17.1 独立 Embed 路由

建议新增顶层路由：

```javascript
{
  path: '/embed/v1/launches/:launchId',
  component: () => import('@/embed/EmbedShell.vue'),
  meta: {
    public: true,
    publicEntry: true,
    embedRuntime: true,
    skipSessionRestore: true,
    hideGlobalLayout: true
  }
}
```

该路由不挂后台 `Layout`。当前路由守卫在 public 判断前无条件
`restoreAuthSession()`，且后续只识别现有 `meta.public`。因此不能只加一个新 Meta：

1. 推荐提供独立 `embed-main.ts + embed-router.ts` 入口，完全不执行普通用户 Store 恢复、
   后台扩展注册和后台路由守卫；
2. 如首期仍共用主 Bundle，路由必须同时设置 `public: true`，并在守卫第一条分支识别
   `embedRuntime` 后直接放行并 `return`，再执行任何 `restoreAuthSession`/登录跳转逻辑；
3. 增加 E2E，确认无普通 Flow Cookie/Token 时入口绝不访问 `/auth/refresh`、不跳 `/login`。

### 17.2 独立请求客户端

新增 `embedRequest`，与现有 `shared/request` 分离：

| 行为 | 普通客户端 | Embed 客户端 |
| --- | --- | --- |
| Token | 普通 Flow JWT | 内存 Embed Token |
| Cookie | `withCredentials=true` | 固定 `fetch(..., {credentials: 'omit'})` |
| 401 | 调 `/auth/refresh` 或去登录 | 清空内存并通知宿主重新 Launch |
| Base URL | 后台 API | `/api/embed/v1` |
| 可信参数 | 内部页面提供 | 只来自 Bootstrap/Session |
| 存储 | 现有 Session 策略 | 禁止 local/session storage |

Embed 客户端不得用 Axios 默认同源行为代替 `credentials: 'omit'`。Embed 组件不得直接 import
普通 `request`；建议通过 ESLint Boundary/路径 Alias 和单元测试约束。服务端 Embed Filter
忽略 Cookie 和普通 Flow JWT，Embed 响应不得设置登录/Refresh Cookie。

### 17.3 组件拆分与复用

建议目录：

```text
workflow-web/src/embed/
  EmbedShell.vue
  EmbedRuntimeHost.vue
  api/embedRequest.ts
  api/runtimeApi.ts
  bridge/embedBridge.ts
  session/embedSessionStore.ts
  runtime/EmbedListRuntime.vue
  runtime/EmbedFormRuntime.vue
  runtime/EmbedErrorState.vue
  projection/embedSchemaTypes.ts

workflow-web/packages/embed-sdk/
  src/index.ts
  src/bridge.ts
  src/types.ts
```

复用策略：

| 现有前端能力 | 处理 |
| --- | --- |
| `EntityDataList.vue` 的 embedded 布局和展示开关 | 抽取展示状态/容器逻辑，不原样复用其 API 和路由依赖 |
| `EntityDataSearchForm.vue` | 抽取纯展示能力，通过 `ExternalSchemaAdapter` 映射字段 |
| `EntityDataTable.vue`、选择 composable | 只复用安全的纯展示/选择部分；不得直接接 External DTO |
| `EntityDataFormFields.vue` | 复用字段渲染，注入 Embed data source/runtime |
| `FormPreviewLinkage.vue` | 复用已审核声明式联动 |
| `FormActionBar.vue` | 复用展示，点击只调用 Embed actionKey |
| `EntityDataFormDialog.vue` | 不直接复用；它绑定 Element Dialog 和内部 API，应抽取 Form Content |
| 普通路由/菜单/权限 Store | 不使用 |
| 通用 UI Event/远程组件 | V1 不使用 |

现有查询组件使用 `fieldCode/fieldName/fieldType/queryType`，External Schema 使用
`code/label/type/operator`；现有表格读取扁平字段或 `row.data`，External Record 使用
`row.values`。因此必须新增：

- `ExternalSchemaAdapter`：只做明确枚举的字段/列/查询类型映射；
- `ExternalRecordAdapter`：把已投影 `values` 转为展示 ViewModel，不补回任何未公开字段；
- `EmbedActionAdapter`：只保留 External DTO 中已授权的 `actionKey`；
- `EmbedCreateEvaluationRuntime`：只调用第 11.6.1 节的 CREATE 只读重算接口，使用取消/序号避免
  旧响应覆盖新草稿，不在前端执行内部联动表达式；
- `EmbedDataSourceRuntime`：V1 只调用第 11.6.2 节的大型静态 Options Facade，必须注入且禁止
  回退到内部 `serviceId/operationCode` 或普通字典接口；第 11.6.3 节 Lookup 在受支持发布链不可达；
- 或新建更小的 `EmbedDataTable`，避免继承现有表格中的自定义动作、关联内容、子列表入口。

这不是把 External DTO 原样传给现有组件的薄包装。Adapter 必须有“未知类型 Fail Closed”和
敏感字段不出现的单元测试。

### 17.4 页面状态机

```text
LOADING_ENTRY
-> WAITING_HANDSHAKE
-> EXCHANGING
-> BOOTSTRAPPING
-> READY
-> SESSION_EXPIRED / FATAL_ERROR / DESTROYED
```

页面不得把 Exchange 失败渲染成普通登录页。错误态至少区分：

- 启动凭证已失效：请宿主重新打开；
- 当前账号未绑定/已禁用：联系管理员；
- 当前页面来源未授权：拒绝嵌入；
- 资源已停用：联系 Flow 管理员；
- 临时错误：允许在同一有效 Session 下重试只读请求。

### 17.5 高度、焦点和无障碍

- `ResizeObserver` 合并节流后发送 `resize`，建议最大 10 次/秒。
- SDK 对高度执行 min/max，避免恶意或异常页面撑开宿主。
- iframe 必须设置可配置 `title`；加载和错误状态具有 ARIA 提示。
- 宿主触发 `focus` 时聚焦第一个有效控件；关闭后焦点回到打开按钮。
- 弹窗、下拉和日期组件需验证在 iframe 内的 teleport 容器，不得挂到宿主 DOM。

### 17.6 域名和 Nginx/网关

推荐：

```text
admin.flow.example.com   管理后台，frame-ancestors 'none'
embed.flow.example.com   Embed Shell + 同源 /api/embed/v1 反向代理
api.flow.example.com     可选的服务端 Open API/OAuth
```

当前 `workflow-web/nginx.conf` 的全局 `frame-ancestors 'none'` 不能直接沿用到 Embed 页面。
因为每个 Launch 的父 Origin 不同，入口 HTML 最适合由后端/边缘 Handler 动态返回 CSP；静态
JS/CSS 仍由 Nginx 长缓存。不得为了省事把全站改成 `frame-ancestors *`。Embed 必须使用独立
VHost/Ingress，入口路由不得继承或追加管理站点的 `frame-ancestors 'none'`；浏览器会同时执行
多份 CSP，任一 `none` 都会使授权嵌入失败。集成测试应断言入口响应恰有一个有效 CSP Header。

当前 Helm Ingress 如只支持单 Host，需要扩展为独立 Embed Host、TLS Secret 和 Entry/API
路由；这是上线前置工作，不是运行时配置可以规避的问题。

**当前交付状态：**应用侧动态 Entry CSP、Embed Shell 和 Runtime 路由已经实现；生产环境的
独立 `embed.flow.example.com` VHost/Ingress、TLS、同源 API 反向代理及 CSP 实际响应尚需由
部署环境完成并验证。专用 Embed Origin 是生产上线的硬门槛，在该门槛完成前必须保持
`workflow.embed.enabled=false`，不能把管理站点 Host 当作生产 Embed Host 临时替代。

如果 Embed Shell 与 Runtime API 不同源，只允许固定
`https://embed.flow.example.com` 调用 Runtime CORS，绝不把合作方 Origin 加入 Runtime CORS。
优先通过网关同源反代，减少 CORS 和 Cookie 混淆。

### 17.7 SDK 发布

- 包名：`@flow/embed-sdk`；
- 语义版本遵循协议兼容规则；
- 同时提供 ESM 和类型声明，是否提供 UMD 由接入方需求决定；
- SDK 只接受 `embedUrl` 指向管理员允许的 Flow Embed Origin，默认拒绝其他域名；
- 发布包附带 SRI Hash/固定 CDN 版本时，不使用“latest”漂移；
- SDK 单元测试覆盖消息伪造、重复消息、销毁后消息、超大 Payload 和版本降级。

### 17.8 浏览器与 iframe 默认策略

暂定最低支持矩阵：Chrome/Edge 109、Firefox 115 ESR、Safari 16.4；最终以项目正式浏览器
矩阵为准。必须支持 Web Crypto、MessageChannel、ResizeObserver 和 CSP `frame-ancestors`。
`crypto.randomUUID` 不可用时，用 `crypto.getRandomValues` 的经过测试实现生成 UUID，禁止
回退到 `Math.random`。

SDK 默认创建：

```html
<iframe
  title="供应商工单"
  sandbox="allow-scripts allow-forms allow-same-origin"
  referrerpolicy="no-referrer"
  src="https://embed.flow.example.com/embed/v1/launches/lch_..."
></iframe>
```

默认不授予 `allow-popups`、`allow-top-navigation`、`allow-downloads`、摄像头、麦克风、
定位或剪贴板权限；后续附件等能力单独评审。宿主 CSP 还必须包含
`frame-src https://embed.flow.example.com`（或等效 `child-src`），否则宿主自己会阻止 iframe。

## 18. 接口清单与复用/开发矩阵

### 18.1 对外接口总表

| 接口 | 状态 | 认证 | 主要实现 |
| --- | --- | --- | --- |
| `POST /oauth2/token` | 现有复用 | Client Credentials | 现有 OAuth |
| `POST /api/open/v1/embed-launches` | V1 已实现 | Machine Bearer + `embed.launch` | Open API Controller + Embed Launch Port |
| `GET /embed/v1/launches/{launchId}` | V1 已实现 | Launch ID + 动态 CSP | Embed Entry Handler |
| `POST /api/embed/v1/launches/{launchId}/exchange` | V1 已实现 | 一次性 Launch code | Exchange Service |
| `GET /api/embed/v1/session` | V1 已实现 | Embed Bearer | 非秘密 Session 状态 |
| `POST /api/embed/v1/session/heartbeat` | V1 已实现 | Embed Bearer | 仅按服务端时间延长 idle 到期 |
| `DELETE /api/embed/v1/session` | V1 已实现 | Embed Bearer | 幂等退出和 Session Slot 释放 |
| `GET /api/embed/v1/runtime/bootstrap` | V1 已实现 | Embed Bearer | Runtime Facade |
| `GET /api/embed/v1/runtime/schema` | V1 已实现（仅 LIST） | Embed Bearer | 列表 External Projection |
| `POST /api/embed/v1/runtime/list/query` | V1 已实现 | Embed Bearer | 复用列表查询服务 |
| `GET /api/embed/v1/runtime/form` | V1 已实现 | Embed Bearer | 复用表单解析服务 |
| `POST /api/embed/v1/runtime/form/evaluations` | V1 已实现（仅 CREATE） | Embed Bearer | 部分草稿联动只读重算；不做最终校验、不写数据 |
| `POST /api/embed/v1/runtime/form/fields/{fieldCode}/options/query` | V1 已实现（仅大型静态选项） | Embed Bearer | 分页不可变 Form Release 中超过 100 项的静态 options；动态绑定 Fail Closed |
| `POST /api/embed/v1/runtime/form/fields/{fieldCode}/lookups/query` | 路由/DTO 已注册，V1 Deferred/Fail Closed | Embed Bearer | 引用类表单字段发布阻断；无固定候选 List Release/Projection，不声明 200 |
| `GET /api/embed/v1/runtime/records/{recordId}` | V1 已实现 | Embed Bearer | 复用实体详情/权限 |
| `POST /api/embed/v1/runtime/records` | V1 已实现 | Embed Bearer + 幂等 | 复用已发布表单提交 |
| `PATCH /api/embed/v1/runtime/records/{recordId}` | V1 未注册、强制关闭 | 不适用 | 待通用 `record_version` 全链路完成后实现 |
| `POST /api/embed/v1/runtime/actions/{actionKey}` | V1 未注册、强制关闭 | 不适用 | 待强类型 Registry 与动作事务契约完成后实现 |
| `GET /api/embed/v1/session` | V1 已实现 | Embed Bearer | Session Service |
| `POST /api/embed/v1/session/heartbeat` | V1 已实现 | Embed Bearer | Session Service |
| `DELETE /api/embed/v1/session` | V1 已实现 | Embed Bearer | Session Service |

V1 不提供独立 `PROCESS_START` 路由，也不允许通过创建请求布尔参数绕过关闭边界。管理端
`/api/embed-management/v1/**` 已实现，并继续复用普通 Flow 用户鉴权、权限码、统一响应和审计。

### 18.2 后端能力矩阵

| 能力 | 复用现有 | 需要扩展 | 全新开发 |
| --- | --- | --- | --- |
| 第三方应用和 Client Secret | `integration_application`、凭据轮换 | 新增 `embed.launch` Scope | 无 |
| 机器 OAuth | Spring Authorization Server 配置 | Launch 路由 Scope 映射 | 无 |
| 应用 CIDR/限流/并发 | 现有 Policy Filter、Bucket、Lease | 增加 Embed namespace 和 Contracts Port | Exchange/Session 细分策略 |
| 用户目录 | `IdentityDirectoryPort`、SysUser | 校验 Embed 使用限制 | 无 |
| 外部人员映射 | 现有 Resolver 思想 | 抽象通用 `ExternalSubjectResolver` | V1 Provider、Binding、JWT 校验、防重放；OIDC 后续建设 |
| 普通用户上下文 | `UserContext` | 支持安全建立/清理和显式 Actor | `EmbedContext` |
| 列表发布和查询 | `EntityListRuntimeService` 等 | 新 Adapter、`EMBEDDED` 固定场景 | 外部 Schema/结果投影 |
| 表单发布和解析 | `EntityFormResolveService` 等 | 新 Adapter、固定 Release | Embed Form Facade |
| 表单提交 | `PublishedFormSubmissionService` | 字段强制值、幂等协调 | 外部写 DTO |
| 实体权限/数据范围 | `EntityDataActionService`、`DataPermissionEngine` | Actor-aware Adapter | Capability 求交 |
| 行级动作 | `EntityActionCapabilityService` 只复用授权判断 | `exposeToEmbed`/白名单语义 | 强类型 `EmbedActionRegistry`、事务 Handler 和外部结果投影 |
| 审计 | `SystemAuditPort`、Outbox | 新事件类型和字段映射 | Embed 安全审计编排 |
| 幂等 | `integration_idempotency_record`、fencing | Actor/View 请求哈希、敏感响应处理 | Embed Coordinator |
| 更新并发 | 暂无可靠通用能力 | 动态 DDL/读写链路 | `record_version` 全链路 |
| Session | 普通 Session 不适用 | 无 | Launch、Embed Session、专用 Filter |
| 配置发布 | 可参考现有 UI Release 模式 | 无 | Embed View/Release/Grant 管理 |

### 18.3 前端能力矩阵

| 能力 | 复用现有 | 需要扩展/抽取 | 全新开发 |
| --- | --- | --- | --- |
| 列表展示 | `EntityDataList.vue` 内部展示逻辑 | API Adapter、状态与布局拆分 | `EmbedListRuntime.vue` |
| 搜索与表格 | 只复用安全的纯展示/选择逻辑 | `ExternalSchemaAdapter/ExternalRecordAdapter` | 必要时新建 `EmbedDataTable`，不是薄包装 |
| 表单字段 | `EntityDataFormFields.vue` | 数据源和运行态注入 | `EmbedFormRuntime.vue` |
| 联动 | `FormPreviewLinkage.vue` | 仅允许审核过的声明式联动 | Embed 安全适配 |
| 动作条 | `FormActionBar.vue` | 只发 actionKey | Action Bridge |
| 表单容器 | Dialog 不能直接复用 | 抽取 Form Content | iframe 全页容器 |
| 请求/认证 | 普通 Request 不适用 | 无 | `embedRequest`、内存 Session Store |
| 路由 | Vue Router 基础可复用 | 无 | 优先独立 `embed-main/router` 与 Embed Shell |
| 宿主通信 | 无 | 无 | SDK、MessageChannel、协议类型 |
| CSP/部署 | Nginx 基础可复用 | 分域、动态入口 Header | Embed Entry Handler |
| 管理配置 UI | 现有表单组件/权限框架 | 复用通用页面框架 | View/Grant/Identity 管理页面 |

### 18.4 明确禁止“直接复用”的入口

以下做法虽然开发量小，但会破坏安全边界，不能采用：

1. 给现有实体 Controller 加 `@PublicApi`；
2. 把普通 Flow Access Token 交给第三方浏览器；
3. 用 `AuthSessionService.createSession` 签发不带 Embed Scope 的普通会话；
4. 用 OAuth Client Credentials Token 直接调用实体 CRUD；
5. iframe 依赖普通 Refresh Cookie；
6. 宿主传 `flowUserId/entityCode/formId/listKey/releaseId`；
7. 对 iframe 暴露通用 UI Event、接口操作或任意自定义脚本；
8. 把管理后台全站 CSP 改成允许任意站点嵌入。

## 19. 错误码契约

### 19.1 错误码表

| HTTP | `errorCode` | 场景 | 客户端处理 |
| --- | --- | --- | --- |
| 400 | `INVALID_REQUEST` | Header、字段、JSON 或格式非法 | 修正请求 |
| 400 | `EMBED_CONTEXT_INVALID` | Context 不符合发布 Schema | 修正业务 Context |
| 401 | `INVALID_ACCESS_TOKEN` | 机器 Token 无效 | 后端重新取 Token |
| 401 | `EMBED_LAUNCH_INVALID` | code 错误、已消费或绑定不匹配 | 重新 Launch |
| 401 | `EMBED_SESSION_INVALID` | Embed Token 无效 | 清空并重新 Launch |
| 401 | `EMBED_SESSION_EXPIRED` | idle/absolute 到期 | 重新 Launch |
| 403 | `SOURCE_ADDRESS_NOT_ALLOWED` | Launch 来源 CIDR 拒绝 | 检查接入配置 |
| 403 | `INSUFFICIENT_SCOPE` | 机器 Token 缺少 `embed.launch` | 重新授权应用 Scope |
| 403 | `EMBED_VIEW_NOT_GRANTED` | 应用未获授权 | 联系管理员 |
| 403 | `EMBED_ORIGIN_NOT_ALLOWED` | 父 Origin 未授权 | 修正 Grant/Origin |
| 403 | `EMBED_IDENTITY_ASSERTION_INVALID` | 断言验签/Claim 失败 | 修正 IdP/断言 |
| 403 | `EMBED_IDENTITY_ASSERTION_REPLAYED` | `jti` 已使用 | 签发新断言 |
| 403 | `EXTERNAL_IDENTITY_NOT_MAPPED` | 外部人员未绑定 | 建立绑定 |
| 403 | `FLOW_USER_DISABLED` | Flow 用户不可用 | 启用/更换用户 |
| 403 | `EMBED_VIEW_DISABLED` | App/Grant/View/Binding 停用 | 联系管理员 |
| 403 | `EMBED_SESSION_REVOKED` | Session 被撤销 | 视原因决定是否重开 |
| 403 | `EMBED_ACCESS_DENIED` | Embed 安全链拒绝未匹配的访问 | 不尝试切换到内部接口 |
| 403 | `EMBED_OPERATION_NOT_ALLOWED` | Capability/动作/入口未开放 | 不展示该操作 |
| 404 | `EMBED_RESOURCE_NOT_FOUND` | 不存在或无行级权限 | 按不存在处理 |
| 409 | `EMBED_CONFIGURATION_VERSION_CONFLICT` | 管理配置乐观锁冲突 | 重新加载后编辑 |
| 409 | `EMBED_IDEMPOTENCY_KEY_REUSED` | 同 Key 不同请求/Actor/View | 使用正确业务请求号 |
| 409 | `EMBED_REQUEST_IN_PROGRESS` | 相同写请求仍在处理 | 按 `Retry-After` 查询/重试 |
| 409 | `EMBED_RECORD_CONFLICT` | 创建时唯一约束或业务状态冲突 | 刷新数据并修正提交内容 |
| 410 | `EMBED_LAUNCH_EXPIRED` | 已确认的 Launch 已过期 | 重新 Launch |
| 413 | `PAYLOAD_TOO_LARGE` | 超过请求或消息上限 | 缩小数据 |
| 422 | `FORM_VALIDATION_FAILED` | 表单业务/字段校验失败 | 展示 `violations` |
| 422 | `EMBED_VIEW_VALIDATION_FAILED` | 管理端 View 发布校验失败 | 修复 `violations` 后重试 |
| 429 | `RATE_LIMIT_EXCEEDED` | 超过配额 | 按 `Retry-After` 退避 |
| 429 | `EMBED_SESSION_LIMIT_EXCEEDED` | Grant/User 活跃会话已达上限 | 关闭旧页面或等待到期后重开 |
| 503 | `EMBED_RUNTIME_UNAVAILABLE` | 依赖或运行态暂不可用 | 保持幂等键重试 |
| 503 | `INTEGRATION_TEMPORARILY_UNAVAILABLE` | Open API Launch 的应用策略/租约暂不可用 | 后端退避重试 |

为防枚举，Exchange 对“code 错误、已消费、channel 不匹配”等默认统一返回
`EMBED_LAUNCH_INVALID`。只有客户端提供了正确 code 且服务端能安全确认仅为过期时，才可返回
`EMBED_LAUNCH_EXPIRED`。

V1 列表过滤字段/值/形状不合法统一使用 `INVALID_REQUEST`；V1 没有 Action API，也不会公开
`EMBED_ACTION_INPUT_INVALID`、`RECORD_VERSION_CONFLICT` 或 `ACTION_PRECONDITION_FAILED`。这些
后续能力的错误码必须在对应接口真正注册时重新冻结，不能由客户端提前依赖。

### 19.1.1 现有异常到 Embed 契约的映射

OAuth/Open API 安全链在进入 Launch Controller 前产生的稳定错误继续保留原码；Runtime Facade
则统一映射成 Embed 命名空间，禁止把内部类名或权限码透出：

| 来源 | 现有错误/异常 | 对外接口 | Embed 对外错误 |
| --- | --- | --- | --- |
| OAuth Scope | `INSUFFICIENT_SCOPE` | Launch | 原样 `INSUFFICIENT_SCOPE` |
| Open 应用策略/Lease | `INTEGRATION_TEMPORARILY_UNAVAILABLE` | Launch | 原样保留 |
| `OpenIdempotencyService` | `IDEMPOTENCY_KEY_REUSED` | Runtime 写 | `EMBED_IDEMPOTENCY_KEY_REUSED` |
| 幂等处理中 | `REQUEST_IN_PROGRESS` | Runtime 写 | `EMBED_REQUEST_IN_PROGRESS` |
| Embed 下游临时故障 | `INTEGRATION_TEMPORARILY_UNAVAILABLE` | Runtime | `EMBED_RUNTIME_UNAVAILABLE` |
| 实体/数据权限拒绝或对象不存在 | 内部权限/NotFound 异常 | Runtime | 统一 `EMBED_RESOURCE_NOT_FOUND` |
| 表单字段校验 | 内部 Validation 异常 | Runtime | `FORM_VALIDATION_FAILED` + 公开字段 violations |

映射发生在 Controller Advice/Facade 边界，HTTP 状态、`errorCode`、`traceId` 和 `Retry-After`
必须进入契约测试；不得依赖异常 message 文本做判断。

### 19.2 校验错误结构

```json
{
  "code": 422,
  "message": "Form validation failed",
  "errorCode": "FORM_VALIDATION_FAILED",
  "data": {
    "violations": [
      {
        "path": "data.title",
        "code": "REQUIRED",
        "message": "标题不能为空"
      }
    ],
    "relaunchRequired": false
  },
  "traceId": "trace-..."
}
```

`violations` 最多 20 项；Path 只能指向客户端本来可见/可写字段。内部异常统一映射，详细堆栈
仅进入受控服务端日志。

### 19.3 重试规则

| 请求 | 是否可自动重试 | 条件 |
| --- | --- | --- |
| OAuth Token | 是 | 第三方后端按 OAuth 错误处理 |
| Launch | 可重新创建 | 不重放旧响应，旧 Launch 自动过期 |
| Exchange | 否 | 响应丢失时重新 Launch |
| Bootstrap/Schema/Query/Detail | 是 | 同一 Session 有效，指数退避 |
| Create/Update/Action | 是 | 必须使用完全相同 Idempotency-Key 和请求体 |
| Heartbeat | 是 | 逻辑幂等 |
| Logout | 是 | 已退出仍视为成功 |

## 20. 审计、指标、限流与运维

### 20.1 必须审计的事件

| 类别 | 事件 |
| --- | --- |
| 管理 | View 创建/修改/校验/发布/启停/退役 |
| 授权 | Grant、Origin、Provider、Binding 创建/变更/撤销 |
| Launch | 创建、拒绝、兑换成功/失败、重复消费、过期、撤销 |
| Session | 建立、心跳异常、过期、退出、撤销 |
| 读取 | 详情读取必须逐条；列表查询可按策略逐请求或分钟聚合 |
| 写入 | 创建、更新、动作、版本冲突、幂等重放和失败 |
| 安全 | Origin 拒绝、断言失败/重放、越权、限流、Token 误用 |

审计建议字段：

```text
operationId, traceId, requestId,
applicationId, viewId, viewReleaseId, sessionId,
actorDigest, flowUserId（仅受控审计域）,
targetType, targetId, actionKey,
result, errorCode, durationMs,
sourcePointer = EMBED_LAUNCH / EMBED_SESSION
```

`before/after` 只记录状态、字段名、数量和必要摘要，不记录 Token、断言、原始 Subject、完整
Context 或完整表单正文。

### 20.2 默认限流建议

| Bucket | 默认 |
| --- | --- |
| `embed-launch-app:{applicationId}` | 60/min |
| `embed-launch-address:{ipDigest}` | 30/min |
| `embed-exchange-launch:{launchId}` | 10/min |
| `embed-exchange-address:{ipDigest}` | 120/min |
| `embed-runtime-session:{sessionId}` | 120/min |
| `embed-write-session:{sessionId}` | 30/min |
| `embed-heartbeat-session:{sessionId}` | 12/min |

最终有效配额取平台硬上限、Application 配额和 Grant 配额中的最小值。机器 Launch 继续校验
来源 CIDR；浏览器 Runtime 不使用合作方服务器 CIDR 作为授权，因为最终请求来自用户浏览器。

### 20.3 指标

建议 Micrometer 指标：

| 指标 | 类型 | 标签 |
| --- | --- | --- |
| `flow_embed_launch_total` | Counter | application、view、result |
| `flow_embed_exchange_total` | Counter | view、result |
| `flow_embed_active_sessions` | Gauge | application、view |
| `flow_embed_runtime_requests_total` | Counter | operation、result |
| `flow_embed_runtime_duration_seconds` | Histogram | operation |
| `flow_embed_permission_denied_total` | Counter | stage、errorCode |
| `flow_embed_idempotent_replay_total` | Counter | operation |
| `flow_embed_record_conflict_total` | Counter | entity（需限制基数） |
| `flow_embed_session_expired_total` | Counter | reason |

禁止把 sessionId、recordId、externalSubject、Token、traceId 作为指标标签。Application/View 数量
较大时也应采用受控 ID 或 Top-N 聚合，避免标签爆炸。

### 20.4 建议 SLO 与告警

以下为上线评审的初始目标，不是现状承诺：

- Launch/Exchange 月可用性 99.9%；
- Launch P95 小于 500 ms（不含首次远程 JWKS 冷启动）；
- Exchange P95 小于 300 ms；
- Bootstrap/Schema P95 小于 500 ms；
- 列表/表单业务耗时沿用相应运行态 SLO；
- 同一 Launch 多次成功兑换必须为 0；
- 未授权 Origin 成功加载业务数据必须为 0；
- 幂等写产生重复业务结果必须为 0。

告警至少覆盖：Exchange 失败率突增、断言重放突增、某应用 403/429 激增、Session Store/数据库
不可用、幂等 PROCESSING 堆积、清理任务滞后、审计 Outbox 积压和 CSP 配置错误。

### 20.5 运维查询与应急处置

支持按 `traceId/applicationId/viewId/sessionId/recordId` 查询关联日志和审计。应急流程：

1. 发现单用户问题：撤销 Binding/Session；
2. 发现单 View 配置问题：禁用 View 或回切 Grant 的 pinned revision；
3. 发现单合作方泄露：禁用 Application、轮换 Client Secret、撤销全部 Session；
4. 发现 Embed 全局风险：Feature Flag 禁止新 Launch，并在网关关闭 Runtime 写操作；
5. 保留必要审计，擦除 Token/Context 密文，不执行破坏性数据库回滚。

## 21. 实施状态与后续开发计划

当前分支已经完成原阶段 1 与阶段 2 的应用代码范围，即 LIST/FORM 读取、CREATE、管理端、
Launch/Session、安全链、Shell 和 SDK。这里保留阶段划分用于说明依赖与后续边界；代码完成
不等于可生产上线，独立 Embed Origin、隔离 MySQL 迁移、浏览器 E2E、安全与联合验收仍是门槛。

### 21.1 阶段 0：设计基线已完成，生产评审待签字

交付物：

1. 确认独立 Embed 域名、网关拓扑和生产 Origin；
2. 确认 V1 只支持已存在 Flow 用户；
3. 确认默认身份模式为 `SIGNED_JWT`，可信外部 ID 仅作例外；
4. 确认首期 Capability：建议只读列表、选择、详情、新建；
5. 确认 `total` 默认是否返回，建议默认关闭、View 显式开启；
6. 评审本文和已生成的 `docs/api/embed-v1.yaml` 接口级契约；
7. 完成安全威胁建模和数据分级；
8. 实施前重新确认 Flyway 最大版本号。

退出条件：架构、安全、前端、后端、DBA、运维和至少一个接入方共同签字。

### 21.2 阶段 1：只读列表 MVP（代码已实现）

后端：

- Embed View/Release/Grant/Origin/Provider/Binding 管理能力；
- `embed.launch` Scope 与 Launch API；
- JWT 验签、防重放、Flow 用户映射；
- Launch/Session 表、原子 Exchange 和专用认证 Filter；
- Bootstrap、外部 List Schema、Query、Detail；
- Capability/字段投影、审计、限流、清理任务；
- 管理端最小 API 和会话撤销。

前端/部署：

- 独立 Embed Route/Shell 和 `embedRequest`；
- 列表展示、搜索、分页、单选/多选回传；
- SDK、MessageChannel、自动高度和错误态；
- 独立域名/同源 API 代理、动态 CSP；
- 管理端最小 View/Grant/Binding 页面。

阶段 1 的原始边界关闭 Create、Update、流程启动、自定义动作、附件、导出和删除；当前分支已
继续完成下一阶段的 Create，因此当前 V1 是“LIST/FORM 读取 + CREATE”，其余能力仍关闭。

### 21.3 阶段 2：表单查看与新建（代码已实现）

- Embed Form External Schema；
- 表单 Content 抽取和已发布字段渲染；
- 详情/只读表单；
- CREATE 部分草稿联动只读重算，服务端强制 Context 覆盖且不提前执行最终提交校验；
- 新建表单、服务端强制 Context 字段；
- 写请求幂等、fencing、required 审计；
- 同事务 Operation Receipt 与固定 Replay Envelope；
- `form.saved` 事件；`action.completed` 只保留协议 Schema，当前没有动作运行时来源；
- 受审计的创建保存链路（仅 `POST /runtime/records`，不是通用 Action API）。

`saveAndStart` 未纳入当前 V1，不能以创建参数或未注册的动作接口调用。

### 21.4 阶段 3：编辑与有状态动作（未实施，V1 强制关闭）

前置：第 16.4 节 `record_version` 已对全部动态实体表和内部写入口完成 Expand、核验和兼容。

- 编辑表单、`If-Match`、版本冲突 UX；
- 更新接口；
- 审核过的 `submit/saveAndStart` 等动作；
- 批量动作逐条权限校验；
- 多 Pod 并发和故障注入测试；
- 开启 `RECORD_UPDATE/ACTION_EXECUTE/PROCESS_START` 发布校验。

### 21.5 阶段 4：可选扩展（未实施）

- 签名 Webhook 的 Embed 业务事件；
- 附件上传下载；
- 审批任务和关联内容；
- OIDC Discovery/更丰富的企业 SSO；
- Headless Runtime API；
- 非 Flow 用户的受限 `EmbedActor`/JIT Provisioning；
- 更细粒度异步动作查询和撤销。

以上每项都扩大安全与权限模型，不应默认包含在 V1。

### 21.6 工作包和依赖

| 工作包 | 主要依赖 | 可并行 | 当前状态 |
| --- | --- | --- | --- |
| DB 表和 Mapper | 契约冻结、迁移版本 | 后端骨架 | 已实现；V062 已解决，仍待第 22.3 节本机独立空库全量验收 |
| View/Grant 管理 | DB | Identity、前端 Shell | 已实现 |
| Identity/Assertion | Provider/Binding 表 | View 管理 | 已实现 |
| Launch/Exchange/Session | Identity、Grant | Embed Shell | 已实现 |
| Runtime Security Chain | Session | 外部 DTO 设计 | 已实现 |
| List Adapter/Projection | Contracts、现有列表服务 | SDK | 已实现 |
| Form Adapter/Projection | List 基础可复用 | 管理 UI | 已实现，含 CREATE 联动只读重算 |
| Options / Lookup | 固定 Form Release、只读 Provider 边界 | Form Adapter | Options 仅大型静态分页；动态数据源与 Lookup 执行 Deferred/Fail Closed |
| Record Create | 表单提交、幂等、回执 | Form Adapter | 已实现 |
| Record Version | Schema Worker、全部写入口 | LIST/FORM/CREATE | 未实施，阶段 3 前置 |
| SDK/Bridge | 消息契约 | 后端 Launch | 已实现 |
| 动态 CSP/应用入口 | 网关和入口 Handler | SDK | 已实现 |
| 生产专用域名/VHost | TLS、Ingress、网关 | 应用验收 | 8081 专用 VHost/Helm 清单已实现；生产启用与 DNS/TLS 实验待验收 |
| 审计/限流/指标 | Contracts、现有基础设施 | 各业务 API | 运行时审计、分级限流与低基数基础指标已实现；Histogram/Gauge 属后续增强 |
| OpenAPI/接入示例 | 接口契约 | 实现过程持续 | OpenAPI 已与 Controller/DTO/响应实例收口，并确定性生成 SDK 类型 |

## 22. 测试与验收设计

### 22.1 单元测试

身份和凭据：

- Origin 规范化、默认端口、大小写、Unicode Host、路径/query/fragment 拒绝；
- JWT Issuer/Audience/算法/签名/`iat/exp/jti`；
- 未知 `kid` 的受控 JWKS 刷新；
- 同一 `jti` 并发验证只成功一次；
- Subject HMAC 映射的应用/Provider 隔离；
- Flow 用户不存在、禁用、删除和 Binding 过期。

配置：

- View 草稿乐观锁；
- Entity/List/Form 归属和发布状态；
- 字段 Visible/Queryable/Writable/Returnable 子集；
- Context Schema 禁止远程 `$ref`、深度/大小上限；
- Context Schema 出现 `pattern` 时以 `SCHEMA_PATTERN_NOT_SUPPORTED` 拒绝；已发布表单字段的
  `validationRules.pattern` 也按不可信资源阻断，历史/篡改快照运行时同样 Fail Closed；
- `REFERENCE/MULTI_REFERENCE/LOOKUP/MULTI_LOOKUP/USER/DEPT/ROLE/GROUP` 表单字段以
  `UNTRUSTED_COMPONENT` 阻断发布；历史或绕过发布器的可写 Lookup 快照运行时返回 503；
- Action 白名单和 Capability 子集；
- Immutable Release Canonical JSON/Hash 稳定。

Session：

- Code/Token 只存摘要；
- Launch 原子状态机；
- idle/absolute timeout；
- 两个并发 Exchange 不能突破 Grant/User 活跃会话上限；
- Logout/Revoke/Expire 并发或重复执行时，`slot_released` 只让计数扣减一次；
- Application/Grant/View/Provider/Binding 版本变化；
- finally 清理 UserContext/EmbedContext；
- Token 不能被普通安全链接受。

运行态：

- List Filter 只接受最多 32 项的 discriminated array；每项严格为 `value`、`values` 或 `range`
  三种形状之一，浏览器提交 `operator/_op/_start/_end` 或重复字段均拒绝；
- 固定 Context 不能被客户端覆盖；
- CREATE 联动重算只接受 fixed writable 的部分草稿，forced Context 覆盖同名值；不执行 required、
  不写记录、不申请幂等 Claim，旧异步响应不能覆盖较新的草稿；
- 外部 DTO 不含权限码、Provider、内部 Token、脚本；
- Row Action 是 View 与 Flow Capability 的交集；
- 详情 404 防枚举；
- 幂等 Hash 包含稳定 Actor/View/Target，明确排除 Session/Launch/Release；
- 重新 Launch 后使用同一 Key 能查询首次结果，其他 Actor/View 不能重放；
- HMAC/AEAD 密钥轮换期间新旧版本均可读，迁移完成后旧版本可安全下线；
- `[阶段 3]` `recordVersion` 更新和冲突；不纳入当前 V1 通过门槛。

### 22.2 后端集成测试

1. 机器 Token 能调用 Launch，普通用户 Token、Embed Token 均不能。
2. Embed Token 只能访问 `/api/embed/v1/**`，不能访问 `/api/open/**`、
   `/api/entity-data/**`、`/api/entity-lists/**` 或管理端。
3. `/api/embed/v1/**` 必须命中专用 Filter，不能因 `@PublicApi` 或拦截器顺序裸奔。
4. View、Grant、Origin、Binding、用户任一无效都拒绝 Launch/Runtime。
5. 两个事务/两个 Pod 同时兑换一个 Launch，恰好一个成功。
6. 同一 Grant/User 并发兑换到上限时不超额；并发 Logout/Revoke/Expire 只释放一次计数。
7. 两个 Pod 同时以相同幂等键创建，业务数据恰好一条。
8. iframe 重新 Launch 后用相同幂等键只得到首次最小回执，不重复创建；其他 Actor/View 使用
   该键得到 409。
9. 旧 fencing token 不能完成新 Worker 已重新 Claim 的请求，且旧 Worker 的业务事务回滚。
10. Application/Grant/View 禁用后，既有 Session 下次读写立即失败。
11. 篡改 Entity/List/Form/Release/Context/Action 参数不能改变 Session 目标。
12. CREATE 联动重算在部分草稿、隐藏字段变化和 forced Context 覆盖下返回当前 FormResult，且
    数据库、幂等记录、审计 Outbox 均无写入。
13. 写业务事务失败时，业务数据、审计 Outbox 和幂等状态符合原子回滚/重试规则。
14. 含非事务性外部副作用且未实现 Outbox/Effect Receipt 的动作在 V1 发布或执行时被拒绝。

### 22.3 数据库和迁移测试

本项目开发环境使用 **macOS 本机 MySQL，不使用且禁止为本需求启动 Docker MySQL**。迁移验证
只能指向专门创建、可清空的独立测试库（例如 `workflow_embed_test_embed_v1`），不得指向现有开发、
业务或共享数据库。创建/删除操作也只能作用于已显式核对库名的该测试库。

V062 的空库迁移问题已经处理完成，**不再是 Embed 阻塞项**。对本机既有 `workflow` 库的只读
核对已确认 Flyway V062–V065 为 `success`，V066–V068 尚未在该库执行；该业务库只用于确认现状，
不能充当 Embed 空库验收目标。隔离迁移测试已经尝试，但现有 `workflow` 与
`workflow_schema` 账号只获准访问既有业务库，不能创建 `workflow_embed_test_*`。

因此当前尚未完成的是一次真实的本机 MySQL 全量执行验收：应由 DBA/本机管理员预建独立空库，
并对 schema 账号授予该库所需 DDL 权限；另一种选择是提供有效且具备建库/授权能力的专用账号，
再按以下清单执行。该执行前置不允许通过借用既有数据库、启动 Docker 或执行 `flyway repair`
绕过；现有单元/集成测试也不能替代真实空库验收。

DBA 预建空库时使用显式复用模式；测试启动前会校验库名必须匹配
`workflow_embed_test_*`，并确认库内不存在表、视图、存储过程、触发器或事件。测试结束只清理
本次测试对象并保留空数据库，不要求应用账号拥有 `CREATE/DROP DATABASE`：

```bash
EMBED_LOCAL_MYSQL_TEST=true \
EMBED_LOCAL_MYSQL_REUSE_EMPTY_DATABASE=true \
EMBED_LOCAL_MYSQL_DATABASE=workflow_embed_test_embed_v1 \
DB_HOST=127.0.0.1 DB_PORT=3306 \
DB_USERNAME='<test-user>' DB_PASSWORD='<test-password>' \
mvn -f workflow-server/pom.xml -pl workflow-db-migrator \
  -Dtest=EmbedRuntimeLocalMySqlMigrationTest test
```

- 从空库按 V001 到最新依次迁移并启动；
- 现有历史迁移 checksum 不变；
- 所有新 JSON CHECK、唯一键、FK、状态 CHECK 生效；
- 每个 FK 子列与父列的类型/字符集/Collation 完全一致，特别覆盖 `flow_user_id -> sys_user.id`；
- Launch 并发消费、Session 唯一性和 `embed_session_counter` 非负约束；
- 并发兑换/注销/撤销/过期扫描后，Counter 与未释放 ACTIVE Session 数一致；
- `embed_operation_receipt.idempotency_record_id` 唯一，同键 stale Worker 的业务事务只有一个提交；
- Operation Receipt/Idempotency 与 Session/Launch 都按文档定义的无环 FK 和保留顺序清理；
- 清理任务使用索引且不大事务锁表；
- Schema Worker 对“无版本列/已有版本列/失败重试/并发任务”均幂等；
- 动态实体大表补列在预期数据库版本上做在线 DDL 演练；
- 备份恢复后 Token 摘要和加密 Context 可按密钥版本读取。

### 22.4 浏览器 E2E

当前已用真实 Chrome 和三个临时 HTTPS Origin 自动化覆盖下述核心链路，CI 在
缺少 Chrome 时会强制失败而不是 skip。Edge、Firefox 和 Safari 仍属生产前的跨浏览器
验收矩阵：

1. 授权 Origin 成功嵌入；未授权 Origin 被 CSP 拦截。
2. 全程禁用第三方 Cookie 仍可启动、查询和提交。
3. 父/子 Origin、Window Source、Nonce 任一错误时忽略消息。
4. Launch code 不出现在 Network URL、Referer、History、Storage 和前端日志。
5. 列表加载、搜索、分页、选择和自动高度。
6. 列表打开只读/新建表单；CREATE 草稿变化经服务端只读重算后字段状态正确，快速连续输入时
   旧响应不会覆盖新草稿，最终保存仍执行完整校验。
7. 断网重试创建只产生一个业务结果。
8. `[阶段 3 未来验收，不纳入当前 V1]` 两个窗口编辑同一记录，后提交窗口收到 409 并正确刷新提示。
9. Session 到期、被撤销、Flow 用户禁用后，页面提示重新打开而非跳登录。
10. 键盘操作、焦点返回、ARIA、缩放和窄屏布局满足前端验收标准。

### 22.5 安全测试

- Token confusion、JWT 算法降级、伪造 `jku/x5u`；
- Launch/Assertion/Idempotency 重放；
- IDOR、字段越权、Mass Assignment；
- Context/Filter 注入、SQL 注入和 JSON 深度攻击；
- postMessage Origin/Source 欺骗和 Prototype Pollution；
- XSS（文本、富文本、字典 Label、后端错误文案）；
- CSP 绕过、点击劫持、顶层导航；
- SSRF（远程 JWKS）；
- 限流绕过、批量 ID 放大、慢请求和大请求；
- 日志、审计、APM、前端埋点中的敏感信息泄漏。

### 22.6 性能与容量

基准场景至少包括：

- 100 个并发 Session，每个 Session 60 次/分钟只读；
- 同一应用的 Launch 峰值和 JWKS 冷/热缓存；
- 10/50/100 条选择动作的逐行鉴权；
- 多 Pod Session 查找、限流、幂等争抢；
- 复杂列表数据范围和表单联动；
- 过期数据清理与在线业务并发。

测试报告要分离 Embed Facade 开销和现有实体运行态耗时，避免把已有慢查询误归因于 iframe。

### 22.7 业务验收用例

| Given | When | Then |
| --- | --- | --- |
| 外部用户已绑定且有 Flow 数据权限 | 打开列表 | 只看到自己范围内记录 |
| 外部用户已绑定但无实体查看权限 | 打开列表 | 返回明确无操作权限，不泄漏 Schema/数据 |
| Application 只授权 View A | 请求 View B Launch | 403 `EMBED_VIEW_NOT_GRANTED` |
| Context 固定 `supplierId=S1` | 浏览器尝试查询 S2 | 条件不能覆盖，S2 数据不可见 |
| View 只回传 ID/Code | 选择记录 | postMessage 不含金额等字段 |
| 相同创建请求发生网络重试 | 使用同一幂等键 | 只创建一次并重放同一业务回执，字段按当前权限重新投影 |
| `[阶段 3 未来验收，不纳入当前 V1]` 记录版本已变化 | 提交旧版本 | 409，不覆盖最新数据 |
| 管理员禁用 Binding | 已打开页面继续查询 | Session 拒绝并要求重新打开 |

## 23. Flyway、发布与回滚

### 23.1 当前迁移核查

2026-08-28 实施前再次核查结果：

```text
main / HEAD 已跟踪的最高迁移：V065
当前工作区已有且尚未合并的用户迁移：V066__entity_form_unique_claim.sql
本 Embed 实现新增：V067、V068
当前工作区并行新增的非 Embed 迁移：V069、V070
本 Embed 用户手册新增：V071
当前工作区已占用的最高迁移版本：V071
```

因此实施时：

1. V001–V065 视为不可变，禁止修改、删除或重命名；
2. V066 属于用户已有且尚未合并的实体表单唯一性纵切，Embed 不占用其版本；本轮安全
   收口在合并前将其 value gate 契约修正为 `ENTITY:{entityCode}:{fieldCode}`，使同时进入
   唯一性校验的同实体字段请求使用同一门闩，claim 命名空间保持表单/快照级。该门闩只协调
   当前入口中适用且未忽略的已发布表单规则，不把无规则、忽略或条件不适用的其他写入口升级为
   实体字段全局唯一约束；
3. Embed 使用连续且当前不冲突的 V067、V068；V069、V070 是并行的组织岗位功能迁移，不属于
   Embed 迁移清单，Embed 不修改、不删除、不重命名这些文件；
4. 当前工作区最大版本已占用至 V071，后续 Embed 或 `record_version` 迁移不得低于 V072；真正
   创建文件前仍必须重新扫描生产迁移目录，并使用高于届时最大版本的版本号；
5. 不修改 `V001__business_schema.sql`，不使用 `flyway repair` 掩盖历史变化。

### 23.2 实际迁移拆分

当前 Embed 实现使用：

| 文件 | 内容 |
| --- | --- |
| `V067__embed_views_identity_and_grants.sql` | View、Release、Provider、Binding、Grant、Origin、Assertion Replay、摘要/密文密钥版本 |
| `V068__embed_launch_sessions_and_receipts.sql` | Launch、Session、`slot_released/slot_released_at`、Session Counter、Operation Receipt、明确 FK 方向、索引和状态 CHECK；若运行态配额实现需要，也只允许增加向后兼容的 Lease 范围字段 |

`record_version` 不能只在 Flyway 中列举少量动态表。新建动态表 DDL、Schema Worker、既有表
Expand 和所有写 SQL 必须一起建设。任务持久化明确复用 V009/V012 已建立的
`workflow_schema_change`（已有 `status/attempt/owner_id/lease_token/lease_until/next_attempt_at/
last_error/active_hash`），不再新建一套任务表；其静态元数据/保留字段若需 Flyway 变更，使用
后续新版本迁移，实际动态表 DDL 仍进入该 fenced 队列。

V062 的新库迁移问题已由项目侧处理，不再作为 Embed 验收阻塞项。对本机既有 `workflow` 库
只读核对的结果是 V062–V065 已成功、V066–V068 尚未执行；不得在该库补跑 Embed 迁移来代替隔离
验收。Embed 迁移契约测试会直接加载生产 `classpath:db/migration`，从空库完整执行当前全部迁移
（截至本次核查为 V001–V071），并明确断言 V062–V071 均成功进入 Flyway History；其中 V069、
V070 仅作为完整生产迁移链的一部分执行；V071 验证用户手册菜单。测试不使用独立高版本目录、
`ignoreMigrationPatterns`、`repair`，也不修改任何已合并历史迁移。

当前 `.env` root 凭据认证失败，`workflow_schema` 又只获准访问 `workflow.*`，因此必须先由 DBA
预建符合 `workflow_embed_test_*` 命名规则的空库并对 schema 账号授权，或提供有效且具备建库
权限的账号；禁止为规避权限问题启动 Docker 或复用现有业务库。

### 23.3 发布配置

建议新增：

```yaml
workflow:
  embed:
    enabled: false
    public-base-url: https://embed.flow.example.com
    entry-asset-path: /embed-assets/embed-main.js
    entry-style-path: /embed-assets/embed-main.css
    launch-ttl-seconds: 60
    session-idle-seconds: 300
    session-absolute-seconds: 1800
    max-page-size: 100
    max-payload-bytes: 1048576
    max-selection-size: 100
    crypto:
      context-key-base64: ${WORKFLOW_EMBED_CONTEXT_KEY_BASE64}
      context-key-version: embed-context-v1
      hmac-key-base64: ${WORKFLOW_EMBED_HMAC_KEY_BASE64}
      hmac-key-version: embed-hmac-v1
```

敏感摘要/加密密钥不写入 YAML，使用现有 Secret/KMS 注入并带 `keyVersion`。

### 23.4 部署顺序

1. 备份并验证迁移；执行 Expand 类型数据库变更。
2. 部署后端模块和安全链，`workflow.embed.enabled=false`。
3. 部署 Embed 静态资源、独立域名、动态 CSP 和网关路由。
4. 部署管理端和 SDK。
5. 创建测试 Provider/Binding/View/Grant，保持生产 Grant 禁用。
6. 在预生产完成安全、E2E、多 Pod 和容量验收。
7. 只对白名单 Application/View 打开 Feature Flag/Grant。
8. 观察错误率、审计、限流和 Session 指标后逐步扩大。
9. 阶段 3 另行执行 recordVersion Expand/Contract，不与只读 MVP 强绑一次发布。

### 23.5 回滚策略

- 功能回滚：关闭新 Launch Feature Flag，禁用 Grant/View，撤销活动 Session；
- 网关回滚：保留管理后台 CSP `none`，停止 Embed 入口和 Runtime 路由；
- 应用回滚：回滚到前一兼容版本，但数据库 Expand 表/列保留；
- 配置回滚：Grant 可 Pin 回上一 View Release；已发布 Release 不修改；
- 数据库不做破坏性 Down Migration，不删除新表/列，不执行 `flyway repair`；
- 已发生的业务创建/动作按业务补偿处理，不能靠数据库回滚抹除审计。

### 23.6 本设计任务的迁移变更

当前实现分支的迁移变化：

```text
新增迁移文件：
  V066__entity_form_unique_claim.sql（用户已有未合并文件；本轮更新其 gate 契约）
  V067__embed_views_identity_and_grants.sql
  V068__embed_launch_sessions_and_receipts.sql
  V071__embed_integration_manual_menu.sql
修改已跟踪迁移文件：无
删除迁移文件：无
```

V066 不属于 Embed 表结构，但它参与当前完整迁移链和实体写入安全验收；由于尚未合并，可在不
破坏历史 checksum 的前提下修正。若任何共享环境已执行旧内容，必须停止修改 V066，并改用新的
更高版本迁移及非滚动升级方案。

V069、V070 是共享工作区中并行开发的非 Embed 迁移，故不列入上面的 Embed 结构迁移清单；
V071 只增加用户手册菜单及调整手册排序。后续 Embed/`record_version` 迁移不得占用 V071 或更低版本。

## 24. 第三方接入手册与上线检查

### 24.1 接入前提

第三方必须具备可信后端。纯静态前端无法安全保存 Client Secret、签发人员断言或创建 Launch，
不支持“只给一个永久 URL 就直接打开”的生产模式。

Flow 管理员需提供：

- OAuth `client_id/client_secret`（Secret 只展示一次）；
- OAuth Token URL 和 Launch API Base URL；
- 授权 `viewKey`；
- Embed Origin `https://embed.flow.example.com`；
- SDK 固定版本或下载地址；
- 人员断言 Issuer/Audience/JWK 约定；
- 测试/生产允许的 Parent Origin；
- 错误码、限流和支持渠道。

第三方需提供：

- 测试和生产 Parent Origin；
- 后端出口 IP/CIDR；
- 稳定不可变的 External Subject 规则；
- JWT Issuer、公钥/JWKS、算法和 `kid` 轮换流程；
- External Subject 与 Flow 用户的初始绑定清单；
- Context 字段定义和数据分级；
- 峰值 Launch、并发 Session 和查询量。

### 24.2 标准接入步骤

1. 管理员创建 Integration Application，交换 Client Secret。
2. 双方登记 JWT Provider、公钥和生产 Origin。
3. 管理员建立或导入 Identity Binding。
4. 管理员发布 Embed View 并授权 Application。
5. 第三方后端接入 OAuth 和 Launch API。
6. 第三方前端接入 SDK，不直接调用 Flow Runtime。
7. 用正向用户、无权限用户、未绑定用户和禁用用户联调。
8. 验证列表查询、选择回传、记录查看、记录创建以及各场景的回传字段。
9. 完成重放、并发、过期、撤销和未授权 Origin 测试。
10. 双方确认审计 Trace 和应急联系人后上线。

### 24.3 服务端调用示例

取得机器 Token：

```bash
curl --request POST 'https://api.flow.example.com/oauth2/token' \
  --user '<client_id>:<client_secret>' \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=client_credentials' \
  --data-urlencode 'scope=embed.launch'
```

创建 Launch：

```bash
curl --request POST 'https://api.flow.example.com/api/open/v1/embed-launches' \
  --header 'Authorization: Bearer <machine_access_token>' \
  --header 'Content-Type: application/json' \
  --header 'X-Trace-Id: partner-demo-001' \
  --data '{
    "viewKey": "supplier-work-orders",
    "parentOrigin": "https://portal.partner.example",
    "channelId": "66f82f09-89ec-4a5a-b81b-f54f02d22262",
    "subject": {
      "type": "SIGNED_JWT",
      "assertion": "<short_lived_signed_user_assertion>"
    },
    "entry": {
      "mode": "LIST"
    },
    "context": {
      "supplierId": "S-10086"
    },
    "ui": {
      "locale": "zh-CN",
      "theme": "light"
    }
  }'
```

示例中的 Secret、Token 和断言只能由第三方后端从 Secret Manager/内存读取，不能写入代码仓库、
前端包或普通日志。

### 24.4 联调排查顺序

| 现象 | 优先检查 |
| --- | --- |
| OAuth 401 | Client 状态、Secret、Scope、Token URL |
| Launch 403 | Application CIDR、Grant、Origin、Provider、Binding、Flow 用户 |
| iframe 浏览器拒绝加载 | Entry 响应 CSP `frame-ancestors` 和实际 Parent Origin |
| iframe 跳到登录 | Route 是否跳过 `restoreAuthSession`、是否误用普通 Request |
| Exchange 401 | 60 秒 TTL、一次性消费、channel/nonces、code 是否被日志/刷新消耗 |
| 列表为空 | View 固定 Context、Flow 数据范围、列表发布规则；不要先假定接口故障 |
| 操作按钮缺失 | View Capability、Grant Ceiling、Flow 权限、行级能力四层交集 |
| 创建重复 | Idempotency-Key 是否稳定、第三方是否超时后换 Key |
| 编辑配置或请求被拒绝 | 属于 V1 预期边界：发布阻断 `RECORD_UPDATE`，Launch/Form 只接受 CREATE/VIEW，PATCH 路由未注册 |
| Session 突然失效 | 用户/Binding/Grant/View/Application 状态和绝对到期 |

所有问题使用响应 `traceId` 关联，不要求第三方提供任何 Token 明文。

### 24.5 上线检查表

- [ ] 生产 Application 使用独立 Client，不与测试共用；
- [ ] 已部署并验证专用 Embed Origin/VHost/Ingress、TLS、同源 Runtime 代理和动态 CSP；未完成不得上线；
- [ ] `embed.launch` 是该 Client 的最小必要 Scope；
- [ ] 生产 Origin 精确且全部使用 HTTPS；
- [ ] 默认使用签名用户断言，`aud/iss/kid` 正确；
- [ ] 所有外部人员已精确绑定到已启用 Flow 用户；
- [ ] View 固定字段、四项 V1 Capability、Context、Returnable 策略已评审；
- [ ] 生产首发使用 `PINNED` UI Release；
- [ ] 删除、批量删除、导出、附件和任意脚本均关闭；
- [ ] 未完成 recordVersion 时 `RECORD_UPDATE` 关闭；
- [ ] CSP、postMessage、Token Storage、无第三方 Cookie E2E 通过；
- [ ] 限流、并发、Session 时长符合容量评估；
- [ ] 审计可按 Trace/Application/View/Session/Record 查询且无敏感泄漏；
- [ ] 应急禁用、Secret 轮换和 Session 撤销演练完成；
- [ ] SDK 和 API 主版本固定，不使用漂移的 latest 地址。

### 24.6 待产品/架构确认项与建议默认值

| 决策项 | 建议默认 |
| --- | --- |
| V1 身份模式 | `SIGNED_JWT` + 已有 Flow 用户 |
| Embed 域名 | 独立 `embed.flow.example.com` |
| Launch 传递 | SDK + MessageChannel；fragment 仅兼容 |
| Launch TTL | 60 秒 |
| Session | idle 5 分钟、absolute 30 分钟、不可刷新 |
| UI Release | 首批 `PINNED` |
| 列表总数 | 默认不返回，View 显式开启 |
| 首期能力 | 列表、选择、详情、新建 |
| 编辑 | recordVersion 全链路完成后开放 |
| 动作 | V1 关闭；后续版本仅逐个审核的内建 actionKey |
| Session 过期 | 宿主重新 Launch，不跳 Flow 登录 |
| 非 Flow 外部人员 | V1 不支持 |
| 可靠业务回调 | 后续建设签名 Webhook；V1 不把 postMessage 当可靠回调 |

## 25. 结论

推荐方案不是给现有后台页面加一个“免登录 URL”，而是建设一条受限的 Embed Runtime：

1. 第三方后端用现有 Client Credentials 证明应用；
2. 短期签名人员断言精确映射到一个 Flow 用户；
3. 一次性 Launch 建立不依赖 Cookie 的 iframe Session；
4. 每个请求恢复真实 Flow `UserContext`，继续执行现有权限与数据范围；
5. Embed View/Grant 再收窄到指定列表、表单、字段、V1 Capability、Origin 和 Context；
6. 专用 Facade 和外部 DTO 复用成熟运行态，同时隔离内部 Controller 和任意执行入口；
7. 通过 CSP、postMessage、短 Token、幂等、审计和撤销形成 V1 安全闭环；乐观锁属于后续
   UPDATE/ACTION 阶段前置。

当前分支已经实现 LIST/FORM 读取与 RECORD_CREATE 的前后端全链路；RECORD_UPDATE、
ACTION_EXECUTE、PROCESS_START 明确关闭，本文相关章节只是后续目标契约。专用
Embed VHost/Helm 清单和 Chrome 跨域 E2E 已实现；进入生产前还必须完成 macOS 本机 MySQL
独立空测试库迁移验证、生产 DNS/TLS/Ingress 实际启用、Edge/Firefox/Safari 兼容性、
安全验收和接入方联合验收。该边界既复用平台成熟运行态，也避免把普通用户会话、
内部动态 API 或尚无并发保护的写操作暴露给第三方。
