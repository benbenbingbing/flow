package com.workflow.http.policy;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 定义主机地址解析器的调用契约；实现层按此提供能力，调用方无需依赖具体实现。
 */
@FunctionalInterface
interface HostAddressResolver {

    /**
     * 解析主机地址解析器；输出作为后续校验或处理的输入。
     *
     * @param host 主机，供本方法解析主机地址解析器时使用
     * @return 解析后的主机地址解析器结果，供调用方继续处理
     * @throws UnknownHostException 操作失败时向调用方传递
     */
    InetAddress[] resolve(String host) throws UnknownHostException;
}
