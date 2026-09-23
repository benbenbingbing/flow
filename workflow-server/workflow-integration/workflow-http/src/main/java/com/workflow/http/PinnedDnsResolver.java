package com.workflow.http;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import org.apache.hc.client5.http.DnsResolver;

/**
 * 封装固定{@code dns}解析器相关能力和状态；供同一业务流程的后续处理使用。
 */
final class PinnedDnsResolver implements DnsResolver {

    private final String host;
    private final InetAddress[] addresses;

    /**
     * 初始化固定{@code dns}解析器，保存构造参数供后续方法使用。
     *
     * @param approved {@code approved}，保存在对象中供后续校验、查询或展示
     */
    PinnedDnsResolver(ApprovedEndpoint approved) {
        this.host = approved.host().toLowerCase(Locale.ROOT);
        this.addresses = approved.addresses()
                .toArray(InetAddress[]::new);
    }

    /**
     * 解析固定{@code dns}解析器；输出作为后续校验或处理的输入。
     *
     * @param requestedHost 请求主机，作为 {@code requireApprovedHost} 的输入影响后续处理
     * @return 解析后的固定{@code dns}解析器结果，供调用方继续处理
     * @throws UnknownHostException 操作失败时向调用方传递
     */
    @Override
    public InetAddress[] resolve(String requestedHost)
            throws UnknownHostException {
        requireApprovedHost(requestedHost);
        return addresses.clone();
    }

    /**
     * 解析规范{@code hostname}；输出作为后续校验或处理的输入。
     *
     * @param requestedHost 请求主机，作为 {@code requireApprovedHost} 的输入影响后续处理
     * @return 解析后的规范{@code hostname}文本，供调用方比较或展示
     * @throws UnknownHostException 操作失败时向调用方传递
     */
    @Override
    public String resolveCanonicalHostname(String requestedHost)
            throws UnknownHostException {
        requireApprovedHost(requestedHost);
        return host;
    }

    /**
     * 校验并获取{@code approved}主机；不满足约束时阻止后续处理。
     *
     * @param requestedHost 请求主机，供本方法校验并获取{@code approved}主机时使用
     * @throws UnknownHostException 操作失败时向调用方传递
     */
    private void requireApprovedHost(String requestedHost)
            throws UnknownHostException {
        if (requestedHost == null
                || !host.equals(
                        requestedHost.toLowerCase(Locale.ROOT))) {
            throw new UnknownHostException(
                    "HTTP 请求拒绝解析未审批主机");
        }
    }
}
