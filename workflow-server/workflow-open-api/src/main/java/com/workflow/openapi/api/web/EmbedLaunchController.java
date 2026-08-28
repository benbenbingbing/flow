package com.workflow.openapi.api.web;

import com.workflow.contracts.embed.EmbedApplicationActor;
import com.workflow.contracts.embed.EmbedLaunchIssuePort;
import com.workflow.openapi.api.OpenIntegrationEndpoint;
import com.workflow.openapi.api.request.OpenEmbedLaunchRequest;
import com.workflow.openapi.api.response.OpenApiResponse;
import com.workflow.openapi.api.response.OpenEmbedLaunchResponse;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.web.CorrelationContext;
import com.workflow.openapi.security.OpenApplicationActorResolver;
import com.workflow.openapi.web.OpenRequestTrace;
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
