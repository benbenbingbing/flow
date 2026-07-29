package com.workflow.openapi.api.web;

import com.workflow.openapi.api.OpenIntegrationEndpoint;
import com.workflow.openapi.api.request.OpenCorrelateMessageRequest;
import com.workflow.openapi.api.request.OpenStartProcessRequest;
import com.workflow.openapi.api.response.OpenApiResponse;
import com.workflow.openapi.api.response.OpenMessageCorrelationView;
import com.workflow.openapi.api.response.OpenPage;
import com.workflow.openapi.api.response.OpenProcessDefinitionView;
import com.workflow.openapi.api.response.OpenProcessInstanceView;
import com.workflow.openapi.api.response.OpenTaskSummaryView;
import com.workflow.openapi.application.OpenProcessService;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.openapi.security.OpenApplicationActorResolver;
import com.workflow.openapi.web.OpenRequestTrace;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@OpenIntegrationEndpoint
@AuthenticatedApi(objectAuthorization = true)
@ConditionalOnProperty(
        name = "workflow.open-api.enabled",
        havingValue = "true")
@RequestMapping("/api/open/v1")
@RequiredArgsConstructor
public class OpenProcessController {

    private final OpenProcessService service;
    private final OpenApplicationActorResolver actorResolver;

    @GetMapping("/process-definitions")
    public OpenApiResponse<OpenPage<OpenProcessDefinitionView>>
            listDefinitions(
                    @RequestParam(required = false) String cursor,
                    @RequestParam(required = false) Integer limit,
                    Authentication authentication,
                    HttpServletRequest request) {
        String traceId = OpenRequestTrace.get(request);
        return OpenApiResponse.success(
                200,
                "success",
                service.listDefinitions(
                        actorResolver.resolve(
                                authentication,
                                traceId),
                        cursor,
                        limit),
                traceId);
    }

    @PostMapping("/process-instances")
    public ResponseEntity<OpenApiResponse<OpenProcessInstanceView>>
            start(
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
                    @Valid @RequestBody OpenStartProcessRequest body,
                    Authentication authentication,
                    HttpServletRequest request) {
        String traceId = OpenRequestTrace.get(request);
        var result = service.start(
                actorResolver.resolve(authentication, traceId),
                idempotencyKey,
                body);
        return operationResponse(result, traceId);
    }

    @GetMapping("/process-instances/{processInstanceId}")
    public OpenApiResponse<OpenProcessInstanceView> get(
            @PathVariable String processInstanceId,
            Authentication authentication,
            HttpServletRequest request) {
        String traceId = OpenRequestTrace.get(request);
        return OpenApiResponse.success(
                200,
                "success",
                service.get(
                        actorResolver.resolve(
                                authentication,
                                traceId),
                        processInstanceId),
                traceId);
    }

    @GetMapping("/process-instances/{processInstanceId}/tasks")
    public OpenApiResponse<OpenPage<OpenTaskSummaryView>> listTasks(
            @PathVariable String processInstanceId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            Authentication authentication,
            HttpServletRequest request) {
        String traceId = OpenRequestTrace.get(request);
        return OpenApiResponse.success(
                200,
                "success",
                service.listTasks(
                        actorResolver.resolve(
                                authentication,
                                traceId),
                        processInstanceId,
                        cursor,
                        limit),
                traceId);
    }

    @PostMapping(
            "/process-instances/{processInstanceId}"
                    + "/messages/{messageKey}")
    public ResponseEntity<OpenApiResponse<OpenMessageCorrelationView>>
            correlate(
                    @PathVariable String processInstanceId,
                    @PathVariable String messageKey,
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
                    @Valid @RequestBody
                    OpenCorrelateMessageRequest body,
                    Authentication authentication,
                    HttpServletRequest request) {
        String traceId = OpenRequestTrace.get(request);
        var result = service.correlate(
                actorResolver.resolve(authentication, traceId),
                processInstanceId,
                messageKey,
                idempotencyKey,
                body);
        return operationResponse(result, traceId);
    }

    private <T> ResponseEntity<OpenApiResponse<T>> operationResponse(
            OpenProcessService.OperationResult<T> result,
            String traceId) {
        ResponseEntity.BodyBuilder response =
                ResponseEntity.status(result.status());
        if (result.replay()) {
            response.header("Idempotent-Replay", "true");
        }
        return response
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(OpenApiResponse.success(
                        result.status(),
                        result.status() == 201
                                ? "created"
                                : (result.status() == 202
                                ? "accepted"
                                : "success"),
                        result.data(),
                        traceId));
    }
}
