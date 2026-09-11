package com.workflow.openapi.security;

import com.workflow.openapi.api.error.OpenApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class OpenApplicationActorResolver {

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

    /** 通过机器令牌解析出的最小应用身份。 */
    public record ResolvedApplicationActor(
            String applicationId,
            String clientId,
            String traceId) {
    }
}
