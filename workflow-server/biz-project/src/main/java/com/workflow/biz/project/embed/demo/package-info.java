/**
 * 第三方后端嵌入 Flow 的完整参考实现。
 *
 * <p>从宿主登录态取得人员身份，通过 Client Credentials 和 SIGNED_JWT 创建一次性 Launch。
 * 示例只依赖通用 Spring/JWT 库及 Flow 公开 HTTP 契约，不调用平台内部服务。
 * 所有示例 Bean 仅在 partner-embed-demo Profile 下注册；常规 biz-project 不启用该 Profile。
 * 第三方复制后须将单用户认证、记录白名单和 Context 替换为自己的业务授权。</p>
 */
package com.workflow.biz.project.embed.demo;
