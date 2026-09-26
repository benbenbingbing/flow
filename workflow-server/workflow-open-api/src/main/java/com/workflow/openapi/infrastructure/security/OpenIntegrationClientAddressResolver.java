package com.workflow.openapi.infrastructure.security;

import com.workflow.openapi.infrastructure.config.OpenIntegrationProperties;

import com.workflow.openapi.network.IpNetwork;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 封装打开集成客户端地址解析器相关能力和状态；供同一业务流程的后续处理使用。
 */
@Component
@ConditionalOnProperty(
        name = "workflow.open-api.enabled",
        havingValue = "true")
public class OpenIntegrationClientAddressResolver {

    private static final int MAXIMUM_FORWARDED_HEADER_LENGTH = 512;
    private static final int MAXIMUM_FORWARDED_ADDRESSES = 16;

    private final OpenIntegrationProperties properties;
    private final List<IpNetwork> trustedProxies;

    /**
     * 初始化打开集成客户端地址解析器，保存构造参数供后续方法使用。
     *
     * @param properties 属性集合依赖，保存到当前对象供后续业务方法调用
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    public OpenIntegrationClientAddressResolver(
            OpenIntegrationProperties properties) {
        this.properties = properties;
        if (!properties.isTrustForwardedHeaders()) {
            this.trustedProxies = List.of();
            return;
        }
        if (properties.getTrustedProxyCidrs().isEmpty()) {
            throw new IllegalStateException(
                    "信任转发头时必须配置可信代理 CIDR");
        }
        try {
            this.trustedProxies = properties.getTrustedProxyCidrs()
                    .stream()
                    .map(IpNetwork::parse)
                    .toList();
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "可信代理 CIDR 格式不正确",
                    exception);
        }
    }

    /**
     * 解析打开集成客户端地址解析器；输出作为后续校验或处理的输入。
     *
     * @param request 本次请求，后续经校验后用于解析打开集成客户端地址解析器
     * @return 解析后的打开集成客户端地址解析器文本，供调用方比较或展示
     */
    public String resolve(HttpServletRequest request) {
        String remoteAddress = normalize(request.getRemoteAddr());
        if (!properties.isTrustForwardedHeaders()
                || remoteAddress == null
                || !isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remoteAddress;
        }
        if (forwarded.length() > MAXIMUM_FORWARDED_HEADER_LENGTH) {
            return null;
        }
        String[] values = forwarded.split(",", -1);
        if (values.length > MAXIMUM_FORWARDED_ADDRESSES) {
            return null;
        }
        List<String> addresses = new ArrayList<>(values.length);
        for (String value : values) {
            String normalized = normalize(value.trim());
            if (normalized == null) {
                return null;
            }
            addresses.add(normalized);
        }
        for (int index = addresses.size() - 1; index >= 0; index--) {
            String address = addresses.get(index);
            if (!isTrustedProxy(address)) {
                return address;
            }
        }
        return addresses.get(0);
    }

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param value 待规范化打开集成客户端地址解析器的原始输入，结果供调用方继续使用
     * @return 规范化后的打开集成客户端地址解析器文本，供调用方比较或展示
     */
    private String normalize(String value) {
        try {
            return IpNetwork.parseAddress(value).getHostAddress();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 判断是否可信{@code proxy}；判断结果决定调用方的后续分支。
     *
     * @param address 地址，供本方法判断是否可信{@code proxy}时使用
     * @return 可信{@code proxy}条件成立时为 true，否则为 false
     */
    private boolean isTrustedProxy(String address) {
        return trustedProxies.stream()
                .anyMatch(network -> network.contains(address));
    }
}
