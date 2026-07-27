# workflow-admin

管理模块负责认证、用户、角色、组织、菜单、字典、扩展治理和统一系统操作审计。

## 审计边界

- `com.workflow.admin.audit.api`：只读查询和导出接口。
- `com.workflow.admin.audit.application`：写入、查询、Outbox 消费和保留期清理。
- `com.workflow.admin.audit.domain`：审计日志与 Outbox 数据模型。
- `com.workflow.admin.audit.infrastructure`：AOP、HTTP 元数据、脱敏和 Mapper。
- `com.workflow.contracts.audit`：供其他模块使用的审计注解、事件和端口。

系统审计记录“谁在什么时候对什么执行了什么操作”；流程操作日志、动作执行日志和数据权限审计仍由对应领域模块维护。
普通成功操作在业务事务提交后写入 Outbox，并在入队失败时有限重试和发布技术监控事件；标记为必需的高风险操作在当前事务写入 Outbox，写入失败会回滚业务。
