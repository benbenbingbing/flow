# ADR-0005：外部工作流场景采用可配置、可发布的版本契约

## 状态

已接受。

## 背景

Flow 需要被多个外部业务系统复用。外部系统不应复制 Flow 的流程 Key、流程版本、
变量字段、身份字段和事件规则；这些内容也不能通过某个客户的业务名称固化在 Flow
代码中。流程定义发布后仍可能继续演进，因此一次运行必须能证明它使用了哪一个场景
配置和哪一个已发布流程 revision。

## 决策

1. 以 `integration_application` 为授权边界，在其下配置唯一的 `scenarioKey`。
2. 场景草稿保存到不可变的 `integration_workflow_scenario_revision`。发布只切换
   应用场景的 `published_revision` 指针；已发布 revision 永不原地修改。
3. 场景 revision 固定流程 Key、可选的 Flowable 流程版本、输入 JSON Schema、
   结果映射、身份映射和版本化事件白名单。映射只允许声明式字段路径，禁止脚本、
   SpEL、任意 SQL 和任意 JavaScript。
4. 启动请求优先使用 `scenarioKey`。服务端校验应用授权和输入，生成 binding revision，
   固定业务引用、subjectVersion、身份命名空间、输入快照和 SHA-256。未指定场景时，
   现有 `processKey` 方式继续可用。
5. 查询只返回场景允许的结果字段；`status` 是 Flow 生命周期，`outcomeCode` 是
   业务决定，两者不能互相推断。`COMPLETED` 不代表任何业务批准结果。
6. 外部事件使用 CloudEvents V1 和事务 Outbox。投递采用数据库 lease/fencing、
   指数退避、死信和人工重投；接收端故障不得回滚或阻塞 Flow 核心事务。
7. OAuth2 Client Credentials、应用级 grant、最小 scope、CORS 白名单、Webhook
   HMAC keyId/时间窗/重放防护和 Secret 注入是部署约束，不由具体接入系统定制。

## 备选方案

- 为每个外部系统编写 Provider：会把客户领域语义带入 Flow，并造成编译和发布耦合，
  不采用。
- 让调用方直接提交任意流程 Key 和变量：无法保证授权、版本稳定性和数据最小化，
  不采用。
- 覆盖当前场景配置：会重新解释历史运行，破坏审计和重放， 不采用。

## 影响与迁移

- 新增 revision 表和 binding 快照列，V020 将既有活动场景转换为已发布 revision。
- 管理端必须先保存草稿、通过校验后发布；停用只阻止新启动，历史实例继续使用快照。
- 生产部署必须使用共享数据库和 Outbox，不得使用 Pod 本地内存承载幂等或投递状态。
- Open API V1 的旧 processKey 启动、查询、任务和消息接口保持兼容。

## 验收

- OpenAPI 兼容性检查在普通 push、pull request 和手动触发中执行。
- 场景 revision、binding 快照、幂等、取消、事件和身份失败均有自动化测试。
- 独立 reference external system 在本地双 Pod k3s 中完成 start/query/cancel/Webhook 闭环。
