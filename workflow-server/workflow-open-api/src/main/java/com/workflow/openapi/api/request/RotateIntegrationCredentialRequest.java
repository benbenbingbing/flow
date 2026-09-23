package com.workflow.openapi.api.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

/**
 * 封装轮换集成凭据的不可变数据；各分量供后续校验、传递或结果展示使用。
 *
 * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param expectedVersion 预期版本，保存在对象中供后续校验、查询或展示
 */
public record RotateIntegrationCredentialRequest(
        @Future Instant expiresAt,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
