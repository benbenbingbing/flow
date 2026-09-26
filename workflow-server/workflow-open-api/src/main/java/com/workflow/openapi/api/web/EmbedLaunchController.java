package com.workflow.openapi.api.web;

import com.workflow.contracts.embed.launch.model.EmbedApplicationActor;
import com.workflow.contracts.embed.launch.port.EmbedLaunchIssuePort;
import com.workflow.openapi.api.annotation.OpenIntegrationEndpoint;
import com.workflow.openapi.api.request.OpenEmbedLaunchRequest;
import com.workflow.openapi.api.response.OpenApiResponse;
import com.workflow.openapi.api.response.OpenEmbedLaunchResponse;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.web.CorrelationContext;
import com.workflow.openapi.application.security.OpenApplicationActorResolver;
import com.workflow.openapi.infrastructure.web.OpenRequestTrace;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Open API boundary for a host backend to issue a short-lived Embed launch.
 */
@RestController
@OpenIntegrationEndpoint
@AuthenticatedApi(objectAuthorization = true)
@ConditionalOnProperty(
        name = {
            "workflow.open-api.enabled",
            "workflow.embed.enabled"
        },
        havingValue = "true")
@RequestMapping("/api/open/v1")
@RequiredArgsConstructor
public class EmbedLaunchController {

    private final EmbedLaunchIssuePort launchIssuePort;
    private final OpenApplicationActorResolver actorResolver;

    /**
     * Issues a one-time launch without retaining its secret in the Open API layer.
     *
     * @param body 请求体，后续用于处理签发并传递处理结果
     * @param authentication 认证，作为 {@code actorResolver.resolve} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于处理签发
     * @return 处理后的签发结果，供调用方继续处理
     */
    @PostMapping("/embed-launches")
    public ResponseEntity<OpenApiResponse<OpenEmbedLaunchResponse>> issue(
            @Valid @RequestBody OpenEmbedLaunchRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        String traceId = OpenRequestTrace.get(request);
        var actor = actorResolver.resolve(authentication, traceId);
        var issued = launchIssuePort.issue(
                new EmbedApplicationActor(
                        actor.applicationId(),
                        actor.clientId(),
                        actor.traceId(),
                        CorrelationContext.requestId(request)),
                body.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(OpenApiResponse.success(
                        HttpStatus.CREATED.value(),
                        "created",
                        OpenEmbedLaunchResponse.from(issued),
                        traceId));
    }
}
