# 后端源码归属与分包约定

业务模块先按能力分组，再按实际职责分层；技术库按技术职责分组。包名表达用途，不按类数量机械拆包，也不为目录对称创建空层。

## 业务能力内部

| 包 | 内容 |
| --- | --- |
| `api.web` | HTTP Controller |
| `api.request/response/error` | HTTP 入参、出参、协议错误与异常处理 |
| `application` | 用例编排与应用服务 |
| `application.model/context/validation` | 内部结果、执行上下文、应用校验；按需使用 |
| `application.port` | 应用定义的外部能力接口 |
| `domain` | 业务规则、值对象和配置语义 |
| `infrastructure.persistence.mapper/record/provider` | 数据访问、表映射和 SQL 生成 |
| `infrastructure.flowable/web/security/config` | 引擎、Web、安全框架与配置装配 |

包内辅助类可以与其调用者同包；拆包不能成为扩大可见性或绕开封装的理由。Java 库中的 `api` 表示公开调用契约，不等同于 HTTP Controller。

分包保持既有 HTTP 契约。嵌入管理的身份提供方更新、视图草稿更新两个接口继续使用已发布的 PATCH 方法；架构规则只对这两个精确方法签名保留例外，其他接口继续遵循 GET/POST 约定，OpenAPI 契约测试检查实际请求方法和路径。

## 模块分工

| 模块 | 关键归属 |
| --- | --- |
| `workflow-admin` | 审计、认证和设置沿用统一的接口与持久化分层 |
| `workflow-entity` | `mutation` 负责变更编排和回执，`version` 负责快照与版本规则；表单专用异常归属 `form` |
| `workflow-process` | Flowable 适配进入各能力的 `infrastructure.flowable`；REST 服务任务委托归属流程模块 |
| `workflow-embed` | 运行侧和 `management` 均使用 `api.web/request/response/error` 与 `application.port` |
| `workflow-core` | 仅共享技术能力；JDBC 与 MyBatis 代码分别位于 `database.jdbc/mybatis` |
| `workflow-open-api` | 应用安全服务、访问策略、框架适配、配置装配分别归类 |
| `workflow-storage` | 存储接口在 `application.port`，内部文件描述在 `application.model`，厂商实现仍在基础设施层 |
| `workflow-migration` | 配置资产选择与导入导出在应用层，归档和密码实现归基础设施层 |
| `workflow-http` | `api/policy/transport/config`；不依赖 Flowable |
| `workflow-outbox` | 对外 Java 接口保留在 `api`，调度装配进入 `infrastructure.config` |
| `workflow-db-migrator` | 工具代码使用 `com.workflow.dbmigrator`；历史 Flyway Java 迁移继续位于 `db.migration` |
| `workflow-devtools` | 示例能力位于 `demo`，包含代码生成占位示例 |
| `workflow-app` | 启动、装配、宿主适配、全局 Web 处理与观测；保留跨模块集成及架构测试 |

`workflow-notification.action.SendNotificationHandler` 当前只记录通知日志，尚不具备真实发送能力。历史迁移与现有流程资产已注册 `sendNotificationHandler`，因此保留该运行时 Bean；不通过将它移入仅测试依赖的 devtools 使现有动作失效。真实通知渠道接入或示例退役需要单独处理业务配置兼容。

## 测试与迁移

单模块单元测试随源码放在所属 Maven 模块，测试 package 与被测职责保持对应。需要跨模块装配的测试留在 `workflow-app`。包内可见的测试注入入口可由测试辅助类桥接，无需扩大生产 API。

迁移目录和历史 `V*__*.sql` 不参与包整理。`ModulePackageLayoutTest` 检查模块命名空间与物理目录，显式允许 db-migrator 中固定位置的 Flyway Java 迁移。
