package com.workflow.admin.auth.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolves a stable login client address without trusting spoofed headers.
 */
@Component
public class ClientAddressResolver {

    private final boolean trustForwardedHeaders;

    /**
     * 初始化客户端地址解析器，保存构造参数供后续方法使用。
     *
     * @param trustForwardedHeaders {@code trust}{@code forwarded}{@code headers}依赖，保存到当前对象供后续业务方法调用
     */
    public ClientAddressResolver(
            @Value(
                    "${workflow.security.trust-forwarded-headers:false}")
            boolean trustForwardedHeaders) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    /**
     * 解析客户端地址解析器；输出作为后续校验或处理的输入。
     *
     * @param request 本次请求，后续经校验后用于解析客户端地址解析器
     * @return 解析后的客户端地址解析器文本，供调用方比较或展示
     */
    public String resolve(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String forwarded =
                    request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                String first = forwarded.split(",", 2)[0].trim();
                String normalized = normalizeLiteral(first);
                if (normalized != null) {
                    return normalized;
                }
            }
        }
        String normalized =
                normalizeLiteral(request.getRemoteAddr());
        return normalized == null
                ? "unknown"
                : normalized;
    }

    /**
     * 规范化字面值；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化字面值的原始输入，结果供调用方继续使用
     * @return 规范化后的字面值文本，供调用方比较或展示
     */
    private String normalizeLiteral(String value) {
        if (!StringUtils.hasText(value)
                || value.length() > 45
                || !value.matches("[0-9A-Fa-f:.]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(value)
                    .getHostAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }
}
