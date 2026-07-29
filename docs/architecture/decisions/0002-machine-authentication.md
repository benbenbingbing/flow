# ADR-0002：机器身份采用 OAuth 2.0 Client Credentials

- 状态：已接受
- 日期：2026-07-29
- 决策范围：开放集成 V1 机器认证

## 背景

当前用户 JWT 由交互式用户名和密码登录签发，语义是平台用户会话。复用该令牌会混淆
机器和人员身份，难以提供独立 Scope、凭据轮换、Audience 和审计。

## 决策

1. 机器认证采用 OAuth 2.0 Client Credentials，使用维护中的标准实现，不自定义令牌协议。
2. 机器令牌与用户 JWT 使用不同的签名密钥、Audience 和安全过滤链。
3. 机器令牌的 Audience 固定为 `flow-open-api`，默认有效期 10 分钟，禁止 Refresh Token。
4. 接入应用按 Scope 和允许的流程定义授权；Scope 不能替代资源归属校验。
5. 客户端凭据使用至少 256-bit 随机值，创建和轮换时只返回一次，数据库仅保存
   Argon2id 哈希。
6. 开放 API 拒绝用户 JWT；内部 API 拒绝机器令牌。
7. 签名密钥使用独立非对称密钥，经部署 Secret 注入并支持 `kid` 重叠轮换。

V1 默认由 Flow 提供 Client Credentials 端点。后续接入企业 IdP 时，开放 API 继续作为
OAuth 2.0 Resource Server，通过配置切换受信发行方，不改变业务 Scope 和资源授权模型。

## 安全边界

- 应用停用后立即停止签发新令牌。
- 已签发令牌的紧急吊销窗口受 10 分钟有效期限制。
- 管理员操作使用现有用户权限和审计，不能使用机器令牌管理接入应用。
- 身份失败对外使用统一错误，不能用于枚举 `client_id`。

## 影响

- 运行实现需要成熟的 OAuth 2.0 Authorization Server 和 Resource Server 组件。
- 当前 `JwtUtil` 继续服务用户会话，不承担机器令牌职责。
- k3s 多 Pod 必须共享同一组版本化签名公私钥。
