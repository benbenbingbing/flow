package com.workflow.openapi.api.response;

import java.time.Instant;

/**
 * 封装已签发集成凭据视图的不可变数据；各分量供后续校验、传递或结果展示使用。
 *
 * @param application 应用，保存在对象中供后续校验、查询或展示
 * @param clientSecret 客户端密钥，保存在对象中供后续校验、查询或展示
 * @param credentialExpiresAt 凭据过期时间，后续用于判断有效期或展示该事件的发生时间
 */
public record IssuedIntegrationCredentialView(
        IntegrationApplicationView application,
        String clientSecret,
        Instant credentialExpiresAt) {
}
