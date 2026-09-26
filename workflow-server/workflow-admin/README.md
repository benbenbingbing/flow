# workflow-admin

管理模块负责认证、用户、角色、组织、菜单、字典、扩展治理和统一系统操作审计。

## 审计边界

- `com.workflow.admin.audit.api.web/request/response`：只读查询、导出接口及其请求和响应。
- `com.workflow.admin.audit.application`：写入、查询、Outbox 消费和保留期清理。
- `com.workflow.admin.audit.domain`：审计语义与 Outbox 载荷模型。
- `com.workflow.admin.audit.infrastructure.aop/web/config`：切面、HTTP 元数据和事务装配。
- `com.workflow.admin.audit.infrastructure.persistence.mapper/record`：日志持久化；`SystemOperationLog` 是表映射记录。
- `com.workflow.admin.audit.infrastructure`：审计差异计算与载荷脱敏。
- `com.workflow.contracts.audit`：供其他模块使用的审计注解、事件和端口。

系统审计记录“谁在什么时候对什么执行了什么操作”；流程操作日志、动作执行日志和数据权限审计仍由对应领域模块维护。
普通成功操作在业务事务提交后写入 Outbox，并在入队失败时有限重试和发布技术监控事件；标记为必需的高风险操作在当前事务写入 Outbox，写入失败会回滚业务。

## 其他能力的分包规则

认证、身份、授权、组织、字典、设置和扩展治理按能力聚合；HTTP 类型使用 `api.web/request/response/error`，应用服务使用 `application`，数据库映射使用 `infrastructure.persistence.mapper/record`。
认证拦截器放在 `auth.infrastructure.web`，JWT 实现放在 `auth.infrastructure.security`，配置属性放在 `auth.infrastructure.config`。只创建实际需要的分层，不为目录对称增加空包。
