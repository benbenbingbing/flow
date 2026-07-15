# 流程动作时机扩展

## 自定义处理器

实现 `FlowActionHandler` 并注册为 Spring Bean。已有实现无需修改；可按需覆盖：

- `supportedTriggerTimings()`：限制允许配置的触发时机。
- `supportedExecutionModes()`：限制事务内或提交后执行。
- `recommendedExecutionMode()`：设计器选择处理器时自动带出的推荐方式。

提交后处理器应使用 `FlowActionContext#getIdempotencyKey()` 调用外部系统。

## 自定义时机

实现 `FlowActionTriggerProvider`，返回自定义 `FlowActionTimingOptionDTO`。时机编码需要全局唯一，并声明：

- 适用作用域 `PROCESS / NODE / SEQUENCE_FLOW`
- 是否仅允许用户任务
- 默认执行方式和失败策略
- 配置人员可以使用的上下文说明

业务代码通过注入 `FlowActionDispatcher` 调用 `dispatchCustom(...)`。自定义时机仍会经过发布版本解析、处理器校验、执行排序、事务策略、Outbox、重试和执行日志。

## 执行语义

- `IN_TRANSACTION + ROLLBACK`：处理器异常回滚当前流程操作。
- `IN_TRANSACTION + CONTINUE`：记录失败后继续流程。
- `AFTER_COMMIT + RETRY`：主事务提交后执行，失败按指数退避重试。
- `AFTER_COMMIT + IGNORE`：主事务提交后执行，失败记录为终态。

流程结束类动作不能假定运行时流程或当前任务仍存在，应使用上下文变量快照、历史流程数据和实体数据。
