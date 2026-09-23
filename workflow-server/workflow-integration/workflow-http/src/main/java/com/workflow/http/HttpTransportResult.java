package com.workflow.http;

/**
 * 封装HTTP传输的不可变数据；各分量供后续校验、传递或结果展示使用。
 *
 * @param statusCode 状态编码，后续用于处理HTTP传输结果时定位或关联目标
 * @param body 请求体，后续用于处理HTTP传输结果并传递处理结果
 * @param retryAfter 重试之后，保存在对象中供后续校验、查询或展示
 * @param responseTruncated 响应{@code truncated}，保存在对象中供后续校验、查询或展示
 */
public record HttpTransportResult(
        int statusCode,
        String body,
        String retryAfter,
        boolean responseTruncated) {
}
