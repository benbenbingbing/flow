package com.workflow.openapi.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 封装撤销集成凭据的不可变数据；各分量供后续校验、传递或结果展示使用。
 *
 * @param expectedVersion 预期版本，保存在对象中供后续校验、查询或展示
 */
public record RevokeIntegrationCredentialRequest(
        @NotNull @PositiveOrZero Long expectedVersion) {
}
