package com.workflow.openapi.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 封装更新集成状态的不可变数据；各分量供后续校验、传递或结果展示使用。
 *
 * @param status 状态标识，决定后续更新集成状态请求采用的处理分支
 * @param expectedVersion 预期版本，保存在对象中供后续校验、查询或展示
 */
public record UpdateIntegrationStatusRequest(
        @NotBlank String status,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
