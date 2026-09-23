package com.workflow.embed.management.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Launch/Session 撤销请求契约。 */
public final class EmbedOperationsRequests {

    /**
     * 初始化嵌入式操作集合{@code requests}，保存构造参数供后续方法使用。
     */
    private EmbedOperationsRequests() {
    }

    /**
     * 封装撤销的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param reason 原因，保存在对象中供后续校验、查询或展示
     */
    public record RevokeRequest(
            @Size(max = 128)
            @Pattern(regexp = "[A-Za-z0-9._:-]+")
            String reason) {
    }

    /**
     * 封装批量操作撤销的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param reason 原因，保存在对象中供后续校验、查询或展示
     * @param cursor 游标，保存在对象中供后续校验、查询或展示
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     */
    public record BulkRevokeRequest(
            @Size(max = 128)
            @Pattern(regexp = "[A-Za-z0-9._:-]+")
            String reason,
            @Size(max = 256)
            @Pattern(regexp = "[A-Za-z0-9_-]+")
            String cursor,
            @Min(1) @Max(200) Integer limit) {
    }
}
