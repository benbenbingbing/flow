# ADR-0007：开放集成收缩为应用与 Embed 鉴权

- 状态：已接受
- 日期：2026-09-10
- 取代：ADR-0001、ADR-0003、ADR-0004、ADR-0005

## 背景

开放集成 V1 同时引入公共流程 API、流程授权与输入契约、外部流程场景、Webhook、
Connector 和 Secret 管理。现阶段没有真实接入方或生产流量证明这些能力解决了已确认
问题；仓库中的项目管理系统、客户端示例和验收脚本均为参考系统，不是用户采用证据。
这些能力却形成了独立的数据模型、管理界面、后台 Worker、网络安全边界、密钥轮换、
告警和兼容性承诺，维护成本已超过当前产品收益。

Embed 集成已经存在明确运行边界，仍需要应用身份、Client Credentials、来源地址策略、
限流、并发租约和幂等保护，因此不能整体删除集成应用安全底座。

## 决策

开放集成管理页收缩为应用列表与机器凭据管理，只服务 Embed/OAuth：

- 保留 `integration_application`、`integration_application_credential`、
  `integration_rate_limit_bucket`、`integration_api_request_lease` 和
  `integration_idempotency_record`；
- OAuth 不再签发或校验业务 Scope；凭有效应用凭据认证后，只允许访问唯一保留的
  Embed launch 边界；
- 删除公共流程 API、流程授权与输入契约、流程绑定、外部流程场景、Webhook、
  Connector 和集成 Secret；
- 接口服务目录不再接受 `INTEGRATION_CONNECTOR` 数据源类型；
- 删除相应示例、公开契约、部署开关、告警与运维手册。

数据库收缩由 `V084__remove_retired_open_integration_features.sql` 完成。该迁移是破坏性
contract migration，只能在旧代码和 Worker 完全退出后执行；历史业务数据的恢复依赖
迁移前备份。

## 重新引入门槛

不为假设场景预留产品入口。只有同时满足以下条件时，才重新评审某项能力：

1. 至少一个具名接入方有无法通过 Embed 或现有内部 API 满足的真实业务目标；
2. 有明确调用量、延迟、可靠性、合规和负责人要求；
3. 已验证直接调用流程能力或使用接入方现有集成平台不能更简单地解决问题；
4. 接入方接受版本契约、密钥轮换、故障恢复和去重责任；
5. 先以最小纵向切片验证价值，再决定是否增加场景编排、Webhook 或 Connector 平台。

## 影响

产品和运维面显著缩小，当前用户不再看到无法解释用途的配置入口。代价是未来若出现
真实开放流程需求，需要基于当时证据重新设计契约和迁移，而不是恢复已删除的旧模型。
