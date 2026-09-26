package com.workflow.http.policy;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

/**
 * 封装{@code approved}接口端点的不可变数据；各分量供后续校验、传递或结果展示使用。
 *
 * @param uri {@code uri}，保存在对象中供后续校验、查询或展示
 * @param host 主机，保存在对象中供后续校验、查询或展示
 * @param addresses {@code addresses}，保存在对象中供后续校验、查询或展示
 */
public record ApprovedEndpoint(
        URI uri,
        String host,
        List<InetAddress> addresses) {
}
