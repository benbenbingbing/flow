package com.workflow.http.policy;

import com.workflow.http.config.WorkflowHttpProperties;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** 为传输测试提供可控 DNS；解析器注入入口仍只在生产策略包内可见。 */
public final class HttpPolicyTestSupport {
    private HttpPolicyTestSupport() {}

    /** 在保留全部地址校验规则的前提下替换 DNS，供传输测试验证解析次数和超时。 */
    public static RestEndpointPolicy withResolver(
            WorkflowHttpProperties properties, AddressResolver resolver) {
        return new RestEndpointPolicy(properties, resolver::resolve);
    }

    @FunctionalInterface
    public interface AddressResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
