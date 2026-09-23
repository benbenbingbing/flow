package com.workflow.openapi.security;

import com.workflow.openapi.api.error.OpenApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * 封装打开应用操作人解析器相关能力和状态；供同一业务流程的后续处理使用。
 */
@Component
public class OpenApplicationActorResolver {

    /**
     * 解析打开应用操作人解析器；输出作为后续校验或处理的输入。
     *
     * @param authentication 认证，供本方法解析打开应用操作人解析器时使用
     * @param traceId 追踪ID，后续用于解析打开应用操作人解析器时定位或关联目标
     * @return 解析后的打开应用操作人解析器结果，供调用方继续处理
     */
    public ResolvedApplicationActor resolve(
            Authentication authentication,
            String traceId) {
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new OpenApiException(
                    401,
                    "INVALID_ACCESS_TOKEN",
                    "Access token is invalid");
        }
        String applicationId = token.getToken()
                .getClaimAsString("application_id");
        String clientId = token.getToken().getSubject();
        if (applicationId == null
                || applicationId.isBlank()
                || clientId == null
                || clientId.isBlank()) {
            throw new OpenApiException(
                    401,
                    "INVALID_ACCESS_TOKEN",
                    "Access token is invalid");
        }
        return new ResolvedApplicationActor(
                applicationId,
                clientId,
                traceId);
    }

    /**
     * 通过机器令牌解析出的最小应用身份。
     *
     * @param applicationId 应用ID，后续用于处理已解析应用操作人时定位或关联目标
     * @param clientId 客户端ID，后续用于处理已解析应用操作人时定位或关联目标
     * @param traceId 追踪ID，后续用于处理已解析应用操作人时定位或关联目标
     */
    public record ResolvedApplicationActor(
            String applicationId,
            String clientId,
            String traceId) {
    }
}
