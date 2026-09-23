package com.workflow.http;

import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * 封装HTTP传输的不可变数据；各分量供后续校验、传递或结果展示使用。
 *
 * @param method {@code method}，保存在对象中供后续校验、查询或展示
 * @param uri {@code uri}，保存在对象中供后续校验、查询或展示
 * @param headers {@code headers}，保存在对象中供后续校验、查询或展示
 * @param body 请求体，后续用于处理HTTP传输请求并传递处理结果
 * @param timeoutMillis {@code timeout}{@code millis}，保存在对象中供后续校验、查询或展示
 * @param allowedHosts 允许{@code hosts}，保存在对象中供后续校验、查询或展示
 * @param maxResponseBytes 最大响应字节，保存在对象中供后续校验、查询或展示
 * @param truncateOversizedResponse {@code truncate}{@code oversized}响应，保存在对象中供后续校验、查询或展示
 */
public record HttpTransportRequest(
        String method,
        URI uri,
        Map<String, String> headers,
        String body,
        int timeoutMillis,
        Set<String> allowedHosts,
        int maxResponseBytes,
        boolean truncateOversizedResponse) {

    /**
     * 初始化HTTP传输请求，保存构造参数供后续方法使用。
     *
     * @param method {@code method}，保存在对象中供后续校验、查询或展示
     * @param uri {@code uri}，保存在对象中供后续校验、查询或展示
     * @param headers {@code headers}，保存在对象中供后续校验、查询或展示
     * @param body 请求体，后续用于初始化HTTP传输并传递处理结果
     * @param timeoutMillis {@code timeout}{@code millis}，保存在对象中供后续校验、查询或展示
     * @param allowedHosts 允许{@code hosts}，保存在对象中供后续校验、查询或展示
     * @param maxResponseBytes 最大响应字节，保存在对象中供后续校验、查询或展示
     * @param truncateOversizedResponse {@code truncate}{@code oversized}响应，保存在对象中供后续校验、查询或展示
     */
    public HttpTransportRequest {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        allowedHosts = allowedHosts == null
                ? Set.of()
                : Set.copyOf(allowedHosts);
    }
}
